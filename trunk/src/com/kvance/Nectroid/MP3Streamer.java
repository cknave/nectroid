// This file is part of Nectroid.
//
// Nectroid is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// Nectroid is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with Nectroid.  If not, see <http://www.gnu.org/licenses/>.

package com.kvance.Nectroid;

import java.net.URL;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.util.Log;


class MP3Streamer extends Thread
{
    public interface BufferingListener {
        abstract void onMP3Buffering(boolean isBuffering);
    }
    public interface ErrorListener {
        abstract void onMP3Error();
    }

    private String mRemoteHost;
    private int mRemotePort;
    private String mRemotePath;
    private int mMP3BufferSize;
    private boolean mCancelled;

    private AudioTrack mAudioTrack;
    private short[] mPcmBuffer;

    private Handler mHandler;

    private BufferingListener mBufferingListener;
    private ErrorListener mErrorListener;

    private static final int PCM_BUFFER_SIZE = 44100 * 2 * 2 * 8/10; // bytes
    private static final String TAG = "MP3Streamer";


    MP3Streamer(URL streamUrl, int bitrate)
    {
        // Take apart the URL now.
        mRemoteHost = streamUrl.getHost();
        mRemotePort = streamUrl.getPort();
        if(mRemotePort == -1) {
            mRemotePort = streamUrl.getDefaultPort();
        }
        mRemotePath = streamUrl.getPath();

        mCancelled = false;
        mAudioTrack = null;
        mMP3BufferSize = bufferSizeForBitrate(bitrate);
        Log.d(TAG, String.format("Set %d byte buffer for %d kbps stream", mMP3BufferSize, bitrate));
        mHandler = new Handler();

        // PCM_BUFFER_SIZE is in bytes, so divide by 2 to get shorts.
        mPcmBuffer = new short[PCM_BUFFER_SIZE / 2];
    }


    ///
    /// Public interface
    ///

    /** Stop all streaming. */
    public void cancel()
    {
        mCancelled = true;
        mBufferingListener = null;
        mErrorListener = null;
        setAbortFlag(true);
    }


    public void setBufferingListener(BufferingListener listener)
    {
        mBufferingListener = listener;
    }

    public void setErrorListener(ErrorListener listener)
    {
        mErrorListener = listener;
    }


    ///
    /// Thread interface
    ///

    @Override
    public void start()
    {
        // Clear the abort flag before starting.
        setAbortFlag(false);
        super.start();
    }


    @Override
    public void run()
    {
        // Connect to the stream.
        int socket = openSocket(mRemoteHost, mRemotePort);
        if(socket == -1) {
            notifyError();
            return;
        }

        try {
            // Send the HTTP request.
            Log.i(TAG, "Sending HTTP request");
            if(!sendHttpRequest(mRemotePath, socket)) {
                if(!mCancelled) {
                    notifyError();
                }
                return;
            }

            // Stream until cancelled or error.
            if(!runStreamingLoop(socket, mMP3BufferSize, PCM_BUFFER_SIZE)) {
                if(!mCancelled) {
                    notifyError();
                }
            }
        } finally {
            // Clean up.
            closeSocket(socket);

            if(mAudioTrack != null) {
                mAudioTrack.stop();
                mAudioTrack.release();
                mAudioTrack = null;
            }
        }
    }


    ///
    /// Utility methods
    ///

    private void notifyBuffering(final boolean isBuffering)
    {
        if(mBufferingListener != null) {
            // Run the listener on its own thread.
            mHandler.post(new Runnable() {
                public void run() {
                    mBufferingListener.onMP3Buffering(isBuffering);
                }
            });
        }
    }


    private void notifyError()
    {
        if(mErrorListener != null) {
            // Run the listener on its own thread.
            mHandler.post(new Runnable() {
                public void run() {
                    mErrorListener.onMP3Error();
                }
            });
        }
    }


    /** Return the buffer size (in bytes) for this bitrate (in kilobits per second). */
    private int bufferSizeForBitrate(int bitrate)
    {
        // Clamp bitrate between 64 and 320
        if(bitrate < 64) {
            bitrate = 64;
        } else if(bitrate > 320) {
            bitrate = 320;
        }

        // Determined experimentally
        return bitrate * 1024 * 3/4;
    }


    /** The audio format has changed.  Update the AudioTrack.
     *
     * Return false if an error occurs.
     */
    private boolean onAudioFormatChanged(int sampleRate, int channels)
    {
        Log.i(TAG, "Audio format changed.");

        // Close the old track first.
        if(mAudioTrack != null) {
            Log.i(TAG, "Stopping old track.");
            mAudioTrack.flush();
            mAudioTrack.stop();
            mAudioTrack.release();
        }

        // Select the proper format constants.
        int streamType = AudioManager.STREAM_MUSIC;
        int channelConfig;
        if(channels == 2) {
            channelConfig = AudioFormat.CHANNEL_CONFIGURATION_STEREO;
        } else {
            channelConfig = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        }
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        int mode = AudioTrack.MODE_STREAM;

        // Create the new AudioTrack.
        Log.i(TAG, String.format("Creating new audio track: rate=%d, channels=%d", sampleRate,
                    channels));
        try {
            mAudioTrack = new AudioTrack(streamType, sampleRate, channelConfig, audioFormat,
                    PCM_BUFFER_SIZE, mode);
        } catch(IllegalArgumentException e) {
            Log.e(TAG, "Failed to create new audio track.", e);
            return false;
        }
        return true;
    }

    /** Start our AudioTrack playing.  Return 0 on success, 1 on error. */
    private int startPlaying()
    {
        if(mAudioTrack != null) {
            mAudioTrack.play();
            return 0;
        } else {
            Log.e(TAG, "Tried to play a null AudioTrack");
            return 1;
        }
    }

   
    ///
    /// Native interface
    ///

    /** Open a socket to this host and port, returning its fd. */
    private native int openSocket(String host, int port);

    /** Close this socket fd. */
    private native void closeSocket(int socket);

    /** Send the HTTP request to GET this path. */
    private native boolean sendHttpRequest(String path, int socket);

    /** Run the streaming loop. */
    private native boolean runStreamingLoop(int socket, int mp3BufSize, int pcmBufSize);

    /** Set the global abort flag. */
    private native void setAbortFlag(boolean abort);

    static {
        System.loadLibrary("mp3streamer");
    }
}
