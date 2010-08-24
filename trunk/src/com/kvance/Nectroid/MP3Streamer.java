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

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.PowerManager;
import android.util.Log;


class MP3Streamer
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
    private boolean mCancelled;

    private AudioTrack mAudioTrack;
    private short[] mPcmBuffer;
    private RingBuffer mMP3Buffer;
    private int mMP3BufferSize;

    private Handler mHandler;

    private BufferingListener mBufferingListener;
    private ErrorListener mErrorListener;

    private Thread mBufferingThread;
    private Thread mStreamingThread;

    private Context mContext;
    private PowerManager.WakeLock mWakeLock;

    private int mSocket;

    private static final int PCM_BUFFER_SIZE = 44100 * 2 * 2 * 8/10; // bytes
    private static final String TAG = "MP3Streamer";


    MP3Streamer(Context context, URL streamUrl, int bitrate)
    {
        // Take apart the URL now.
        mRemoteHost = streamUrl.getHost();
        mRemotePort = streamUrl.getPort();
        if(mRemotePort == -1) {
            mRemotePort = streamUrl.getDefaultPort();
        }
        mRemotePath = streamUrl.getPath();

        // Allocate the MP3 buffer.
        mMP3BufferSize = bufferSizeForBitrate(bitrate);
        Log.d(TAG, String.format("Set %d byte buffer for %d kbps stream", mMP3BufferSize, bitrate));
        mMP3Buffer = new RingBuffer(mMP3BufferSize);

        // Allocate the PCM buffer; PCM_BUFFER_SIZE is in bytes, so divide by 2 to get shorts.
        mPcmBuffer = new short[PCM_BUFFER_SIZE / 2];

        // Initialize other fields.
        mCancelled = false;
        mAudioTrack = null;
        mHandler = new Handler();
        mSocket = -1;
        mContext = context;
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

        // Shut down the socket now, in case the buffering thread is blocking on a read.
        if(mSocket != -1) {
            closeSocket(mSocket);
            mSocket = -1;
        }
    }


    public void setBufferingListener(BufferingListener listener)
    {
        mBufferingListener = listener;
    }

    public void setErrorListener(ErrorListener listener)
    {
        mErrorListener = listener;
    }


    /** Start streaming the MP3. */
    public void start()
    {
        // Clear the abort flag before starting.
        setAbortFlag(false);

        // Start a thread to fill the MP3 buffer.
        mBufferingThread = new Thread(bufferingLogic, "BufferingThread");
        mBufferingThread.start();

        // Start a thread to decode the MP3.
        mStreamingThread = new Thread(streamingLogic, "StreamingThread");
        mStreamingThread.start();
    }


    ///
    /// Buffering logic
    ///

    private Runnable bufferingLogic = new Runnable() {
        public void run() {
            final String TAG = "MP3-Buffer";
            boolean error = false;

            // Notify the start of buffering.
            notifyBuffering(true);

            // Connect to the stream.
            // Synchronize this to mMP3Buffer to keep the streaming thread asleep.
            synchronized(mMP3Buffer) {
                // Open the socket.
                mSocket = openSocket(mRemoteHost, mRemotePort);
                if(mSocket == -1) {
                    error = true;
                }
            }

            try {
                // Still acquiring mMP3Buffer to keep the streaming thread asleep.
                synchronized(mMP3Buffer) {
                    // Send the HTTP request.
                    if(!error && !mCancelled) {
                        Log.i(TAG, "Sending HTTP request");
                        error = sendHttpRequest(mRemotePath, mSocket);
                    }
                }

                // Read into the buffer until there's an error or it's quitting time.
                boolean bufferIsFull = false;
                while(!error && !mCancelled) {
                    if(bufferIsFull) {
                        // The buffer is full; let the streaming thread run.
                        Log.d(TAG, "Buffer is full; waiting");
                        try {
                            Thread.sleep(200, 0);
                        } catch(InterruptedException e) {
                            // Doesn't matter.
                        }
                        bufferIsFull = false;
                    } else {
                        error = waitForReadable(mSocket);
                    }

                    if(!error) {
                        // Wait until there's room in the buffer.
                        synchronized(mMP3Buffer) {
                            if(mMP3Buffer.isFull()) {
                                bufferIsFull = true;
                            } else {
                                error = readIntoMP3Buffer(mSocket, mMP3Buffer);
                            }
                        }
                    }
                }
            } finally {
                // Clean up.
                if(mSocket != -1) {
                    closeSocket(mSocket);
                    mSocket = -1;
                }
                if(error && !mCancelled) {
                    notifyError();
                }
                Log.i(TAG, "Buffering thread is terminating");
            }
        }
    };


    ///
    /// Streaming logic
    ///

    private Runnable streamingLogic = new Runnable() {
        public void run() {
            final String TAG = "MP3-Stream";
            boolean error = false;

            // Wait for the MP3 buffer to fill up to 75%.
            int targetLength = mMP3BufferSize * 75/100;
            while(!mCancelled) {
                try {
                    Thread.sleep(200, 0);
                } catch(InterruptedException e) {
                    // Doesn't matter...
                }
                synchronized(mMP3Buffer) {
                    if(mMP3Buffer.length() >= targetLength) {
                        break;
                    }
                }
            }

            // Notify that buffering is complete.
            if(!mCancelled) {
                notifyBuffering(false);
            }

            // Acquire a wake lock so the CPU runs fast enough to decode MP3s with the screen off.
            PowerManager pm = (PowerManager)mContext.getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Nectroid MP3 Player");
            mWakeLock.acquire();

            // Run the native MP3 decoding loop.
            Log.i(TAG, "Starting MP3 decoding");
            try {
                if(!mCancelled) {
                    error = runStreamingLoop(mMP3Buffer);
                }
            } finally {
                // Clean up.
                if(error && !mCancelled) {
                    notifyError();
                }
                if(mAudioTrack != null) {
                    mAudioTrack.stop();
                    mAudioTrack.release();
                    mAudioTrack = null;
                }
                mWakeLock.release();
                Log.i(TAG, "Streaming thread is terminating");
            }
        }
    };


    ///
    /// Utility methods
    ///

    private void notifyBuffering(final boolean isBuffering)
    {
        final BufferingListener listener = mBufferingListener;
        if(listener != null) {
            // Run the listener on its own thread.
            mHandler.post(new Runnable() {
                public void run() {
                    listener.onMP3Buffering(isBuffering);
                }
            });
        }
    }


    private void notifyError()
    {
        final ErrorListener listener = mErrorListener;
        if(listener != null) {
            // Run the listener on its own thread.
            mHandler.post(new Runnable() {
                public void run() {
                    listener.onMP3Error();
                }
            });
        }

        // Cancel any other running threads.
        cancel();
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
        return bitrate * 1024 * 1/2;
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

    /** Block until there is data to read on this socket. */
    private native boolean waitForReadable(int socket);

    /** Send the HTTP request to GET this path. */
    private native boolean sendHttpRequest(String path, int socket);

    /** Read any amount of data into this MP3 ringbuffer. */
    private native boolean readIntoMP3Buffer(int socket, RingBuffer mp3Buffer);

    /** Run the streaming loop. */
    private native boolean runStreamingLoop(RingBuffer mp3Buffer);

    /** Set the global abort flag. */
    private native void setAbortFlag(boolean abort);

    static {
        System.loadLibrary("mp3streamer");
    }
}
