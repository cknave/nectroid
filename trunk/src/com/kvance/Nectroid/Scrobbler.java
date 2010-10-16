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

import android.content.Context;
import android.content.Intent;
import android.util.Log;


/** Audioscrobbler support for PlayerService. */
class Scrobbler implements PlayerManager.StateListener, PlaylistManager.SongListener
{
    private PlayerManager mPlayerManager;
    private PlaylistManager mPlaylistManager;
    private Context mContext;
    private boolean mActive;

    private static final String TAG = "Nectroid";


    public Scrobbler(NectroidApplication app)
    {
        mPlayerManager = app.getPlayerManager();
        mPlaylistManager = app.getPlaylistManager();
        mContext = app.getApplicationContext();
        mActive = false;
    }


    ///
    /// Main interface
    ///

    public void start()
    {
        if(mActive == true) {
            Log.w(TAG, "Tried to start the scrobbler when it was already running.");

        } else {
            // Listen for song changes while the player is playing.
            if(mPlayerManager.getPlayerState() == PlayerService.State.PLAYING) {
                mPlaylistManager.addSongListener(this);
                Playlist.EntryAndTimeLeft currentSong = mPlaylistManager.getCurrentSong();
                if(currentSong != null) {
                    notifySong(currentSong);
                }
            }
            // Listen for changes in the player state.
            mPlayerManager.addStateListener(this);

            mActive = true;
        }
    }


    public void stop()
    {
        if(mActive == false) {
            Log.w(TAG, "Tried to stop the scrobbler, but it wasn't running.");

        } else {
            // If we're still listening for song changes, stop.
            if(mPlayerManager.getPlayerState() == PlayerService.State.PLAYING) {
                mPlaylistManager.removeSongListener(this);
                notifyStopped();
            }
            // Stop listening to the player state.
            mPlayerManager.removeStateListener(this);

            mActive = false;
        }
    }


    public boolean isActive()
    {
        return mActive;
    }


    ///
    /// Event listeners
    ///

    /** The player state has changed.
     *
     * If the player is running, listen for changes in the current song.
     */
    public void onStateChanged(PlayerService.State newState)
    {
        if(newState == PlayerService.State.PLAYING) {
            mPlaylistManager.addSongListener(this);
            Playlist.EntryAndTimeLeft currentSong = mPlaylistManager.getCurrentSong();
            if(currentSong != null) {
                notifySong(currentSong);
            }
        } else if(newState == PlayerService.State.STOPPED) {
            mPlaylistManager.removeSongListener(this);
            notifyStopped();
        }
    }


    /** The song has changed.
     *
     * Send an update about it.
     */
    public void onSongChanged(Playlist.EntryAndTimeLeft newSong)
    {
        notifySong(newSong);
    }


    ///
    /// Utility methods
    ///

    private void notifySong(Playlist.EntryAndTimeLeft ent)
    {
        Playlist.Entry song = ent.getEntry();

        // Convert seconds to milliseconds
        long timeLeft = ent.getTimeLeft() * 1000;
        long duration = song.getLength() * 1000;
        long position = duration - timeLeft;

        Intent i = new Intent("fm.last.android.metachanged");
        i.putExtra("artist", song.getArtistString());
        i.putExtra("track", song.getTitle());
        i.putExtra("duration", duration);
        i.putExtra("position", position);
        mContext.sendBroadcast(i);
    }

    private void notifyStopped()
    {
        Intent i = new Intent("fm.last.android.playbackcomplete");
        mContext.sendBroadcast(i);
    }
}
