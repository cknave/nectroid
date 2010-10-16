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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;


public class PlayerService extends ForegroundService
    implements PlaylistManager.SongListener, MediaPlayer.OnErrorListener,
               MediaPlayer.OnPreparedListener, MP3Streamer.BufferingListener,
               MP3Streamer.ErrorListener
{
    public enum State {
        STOPPED,
        LOADING,
        PLAYING
    };

    public static final String ACTION_PLAY = "com.kvance.Nectroid.PLAY";
    private static final String TAG = "NectroidPlayer";
    private static final int PLAYING_ID = 1;


    private Notification mNotification;
    private CharSequence mNotifyTitle;
    private PendingIntent mNotifyIntent;
    private WifiManager.WifiLock mWifiLock;

    private PlaylistManager mPlaylistManager;
    private PlayerManager mPlayerManager;

    /** MediaPlayer used for Android 1.6+ */
    private MediaPlayer mMP;

    /** MP3Streamer used for Android 1.5 */
    private MP3Streamer mMP3Streamer;


    ///
    /// Service event handlers
    ///

    @Override
    public void onCreate()
    {
        super.onCreate();

        // Build our notification (to be identified by PLAYING_ID).
        mNotification = new Notification(R.drawable.play_status, null, 0);
        mNotification.flags = Notification.FLAG_ONGOING_EVENT;
        mNotifyTitle = getString(R.string.nectroid_streaming);
        Intent nectroidIntent = new Intent(this, NectroidActivity.class);
        mNotifyIntent = PendingIntent.getActivity(this, 0, nectroidIntent, 0);

        // Tell the player manager we exist.
        NectroidApplication app = (NectroidApplication)this.getApplication();
        mPlayerManager = app.getPlayerManager();
        mPlayerManager.setPlayer(this);

        // Get the current song for our notification.
        mPlaylistManager = app.getPlaylistManager();
        Playlist.EntryAndTimeLeft ent = mPlaylistManager.getCurrentSong();
        if(ent != null) {
            updateSongInfo(ent.getEntry());
        } else {
            updateSongInfo(null);
        }
            
        // Register for song updates.
        mPlaylistManager.addSongListener(this);
        mPlaylistManager.requestAutoRefresh(this);

        // Keep any running wi-fi active.
        Context ctx = getApplicationContext();
        if(isWifiConnected(ctx)) {
            WifiManager wiman = (WifiManager)ctx.getSystemService(Context.WIFI_SERVICE);
            mWifiLock = wiman.createWifiLock(WifiManager.WIFI_MODE_FULL, TAG);
            mWifiLock.acquire();
        }
    }


    @Override
    public void onDestroy()
    {
        Log.d(TAG, "destroy PlayerService");
        
        // Release any existing wifi lock.
        if(mWifiLock != null) {
            mWifiLock.release();
            mWifiLock = null;
        }

        // Tell the player manager we're going away.
        mPlayerManager.setPlayer(null);

        // Unsubscribe from song updates.
        mPlaylistManager.removeSongListener(this);
        mPlaylistManager.unrequestAutoRefresh(this);

        // Clean up the player.
        if(mMP != null) {
            // MediaPlayer.release() blocks for a few seconds, so run it in another thread.
            new AsyncTask<Void, Void, Void>() {
                public Void doInBackground(Void... args) {
                    mMP.stop();
                    mMP.release();
                    return null;
                }
            }.execute();
        }

        // Clean up the other player.
        if(mMP3Streamer != null) {
            mMP3Streamer.cancel();
        }

        // Continue with parent's destruction.
        super.onDestroy();

        // Make sure our notification is gone.
        mNM.cancel(PLAYING_ID);
    }


    ///
    /// PlayerManager event handlers
    ///

    @Override
    public void onSongChanged(Playlist.EntryAndTimeLeft newSong)
    {
        updateSongInfo(newSong.getEntry());
    }


    ///
    /// MediaPlayer event handlers
    ///

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra)
    {
        // Register the error, and stop the service.
        Toast.makeText(this, R.string.player_error, Toast.LENGTH_SHORT).show();
        stopSelf();
        return true;
    }

    @Override
    public void onPrepared(MediaPlayer mp)
    {
        // Start playing immediately.
        mPlayerManager.setPlayerState(State.PLAYING);
        mp.start();
    }


    ///
    /// MP3Streamer event handlers
    ///

    @Override
    public void onMP3Buffering(boolean isBuffering)
    {
        if(isBuffering) {
            mPlayerManager.setPlayerState(State.LOADING);
        } else {
            mPlayerManager.setPlayerState(State.PLAYING);
        }
    }

    @Override
    public void onMP3Error()
    {
        // Call through to MediaPlayer's onError with bogus parameters.
        onError(null, 0, 0);
    }


    ///
    /// ForegroundService methods
    ///

    protected int getNotificationId()
    {
        return PLAYING_ID;
    }

    protected Notification getNotification()
    {
        return mNotification;
    }

    protected void handleCommand(Intent intent)
    {
        if(intent == null) {
            Log.w(TAG, "Ignoring null intent");
            stopSelf();

        } else if(intent.getAction() == null) {
            Log.w(TAG, "Ignoring intent with null action");
            stopSelf();

        } else if(!intent.getAction().equals(ACTION_PLAY)) {
            Log.w(TAG, String.format("Ignoring invalid action %s", intent.getAction()));
            stopSelf();

        } else {
            // Valid intent.  Extract the bitrate and start playing.
            int bitrate = intent.getIntExtra(StreamsActivity.EXTRA_BITRATE, 192);
            startPlaying(intent.getData(), bitrate);
        }
    }


    ///
    /// Utility methods
    ///

    private void startPlaying(Uri stream, int bitrate)
    {
        boolean error = false;
        if(Prefs.getUseSWDecoder(this)) {
            Log.i(TAG, "Using software MP3 decoding instead of MediaPlayer.");
            URL streamUrl = null;
            try {
                streamUrl = new URL(stream.toString());
            } catch(MalformedURLException e) {
                error = false;
            }
            if(!error) {
                mMP3Streamer = new MP3Streamer(this, streamUrl, bitrate);
                mMP3Streamer.setErrorListener(this);
                mMP3Streamer.setBufferingListener(this);
                mMP3Streamer.start();
            }

        } else {
            // Use the normal MediaPlayer for streaming.
            mMP = new MediaPlayer();
            mMP.setAudioStreamType(android.media.AudioManager.STREAM_MUSIC);
            try {
                mMP.setDataSource(stream.toString());
            } catch(IOException e) {
                error = true;
            }

            if(!error) {
                mMP.setOnPreparedListener(this);
                mMP.setOnErrorListener(this);
                mMP.prepareAsync();
                startForegroundCompat(PLAYING_ID, mNotification);
                mPlayerManager.setPlayerState(State.LOADING);
            }
        }

        if(error) {
            // Call our own error handler with bogus parameters.
            onError(null, 0, 0);
        }
    }


    private void updateSongInfo(Playlist.Entry song)
    {
        CharSequence info;

        // No song?  Just leave an empty string I guess...
        if(song == null) {
            info = "";
        } else {
            // Build the info string.
            StringBuilder sb = new StringBuilder();
            sb.append('\u201c');
            sb.append(song.getTitle());
            sb.append('\u201d');
            java.util.List<Playlist.IdString> artists = song.getArtists();
            int numArtists = artists.size();
            if(numArtists > 0) {
                sb.append(" by ");
                sb.append(song.getArtistString());
            }
            info = sb.toString();
        }

        // Update our notification.
        Context ctx = getApplicationContext();
        mNotification.setLatestEventInfo(ctx, mNotifyTitle, info, mNotifyIntent);
        mNM.notify(PLAYING_ID, mNotification);
    }


    private static boolean isWifiConnected(Context context)
    {
        ConnectivityManager conman = (ConnectivityManager)context.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = conman.getActiveNetworkInfo();

        return (netInfo != null) && (netInfo.getType() == ConnectivityManager.TYPE_WIFI);
    }
}
