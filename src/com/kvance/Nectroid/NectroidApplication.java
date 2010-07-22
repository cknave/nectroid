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

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;


public class NectroidApplication extends Application implements OnSharedPreferenceChangeListener
{
    private PlaylistManager mPlaylistManager;
    private StreamsManager mStreamsManager;
    private PlayerManager mPlayerManager;
    private OneLinerManager mOneLinerManager;
    private Scrobbler mScrobbler;


    @Override
    public void onCreate()
    {
        super.onCreate();
        Context appContext = getApplicationContext();
        mPlaylistManager = new PlaylistManager(appContext);
        mStreamsManager = new StreamsManager();
        mPlayerManager = new PlayerManager();

        mOneLinerManager = new OneLinerManager(appContext);
        mOneLinerManager.listenForPreferences();

        // Start or stop the scrobbler by user preference.
        mScrobbler = new Scrobbler(this);
        if(Prefs.getUseScrobbler(this)) {
            mScrobbler.start();
        }
        SharedPreferences p = getSharedPreferences(Prefs.PREFS_NAME, Context.MODE_PRIVATE);
        p.registerOnSharedPreferenceChangeListener(this);
    }


    @Override
    public void onLowMemory()
    {
        mPlaylistManager.onLowMemory();
        mStreamsManager.onLowMemory();
        mOneLinerManager.onLowMemory();
    }


    @Override
    public void onTerminate()
    {
        super.onTerminate();
        mOneLinerManager.unlistenForPreferences();
        if(mScrobbler.isActive()) {
            mScrobbler.stop();
        }
        SharedPreferences p = getSharedPreferences(Prefs.PREFS_NAME, Context.MODE_PRIVATE);
        p.registerOnSharedPreferenceChangeListener(this);
    }


    ///
    /// Getters
    ///

    public PlaylistManager getPlaylistManager() { return mPlaylistManager; }
    public StreamsManager getStreamsManager() { return mStreamsManager; }
    public PlayerManager getPlayerManager() { return mPlayerManager; }
    public OneLinerManager getOneLinerManager() { return mOneLinerManager; }


    ///
    /// Public interface
    ///

    /** Return true if any of our managers are loading something. */
    public boolean isLoadingAnything()
    {
        return mPlaylistManager.isUpdating() ||
            mStreamsManager.isUpdating() ||
            mOneLinerManager.isUpdating() ||
            mPlayerManager.getPlayerState() == PlayerService.State.LOADING;
    }


    ///
    /// Preference updates
    ///

    /** Start listening for changes in the oneliner refresh time preference. */
    private void listenForPreferences()
    {
        SharedPreferences p = getSharedPreferences(Prefs.PREFS_NAME, Context.MODE_PRIVATE);
        p.registerOnSharedPreferenceChangeListener(this);
    }

    /** Stop listening for preference changes. */
    private void unlistenForPreferences()
    {
        SharedPreferences p = getSharedPreferences(Prefs.PREFS_NAME, Context.MODE_PRIVATE);
        p.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key)
    {
        if(key.equals(Prefs.USE_SCROBBLER_KEY)) {
            // Start or stop the scrobbler as requested.
            boolean useScrobbler = prefs.getBoolean(key, Prefs.DEFAULT_USE_SCROBBLER);
            if(useScrobbler && !mScrobbler.isActive()) {
                mScrobbler.start();
            } else if(!useScrobbler && mScrobbler.isActive()) {
                mScrobbler.stop();
            }
        }
    }
}
