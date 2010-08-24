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

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import android.view.Window;


public class NectroidApplication extends Application
    implements OnSharedPreferenceChangeListener, SiteManager.SiteListener
{
    private OneLinerManager mOneLinerManager;
    private PlayerManager mPlayerManager;
    private PlaylistManager mPlaylistManager;
    private Scrobbler mScrobbler;
    private SiteManager mSiteManager;
    private StreamsManager mStreamsManager;

    private BackgroundColorizer mBackgroundColorizer;

    private static final String TAG = "Nectroid";


    @Override
    public void onCreate()
    {
        super.onCreate();
        Context appContext = getApplicationContext();

        // Before doing anything, make sure the database has been (automatically) created.
        new DbOpenHelper(appContext).getReadableDatabase().close();

        // Update the cache with the current site before starting any other managers.
        mSiteManager = new SiteManager(appContext);
        mSiteManager.start();
        mSiteManager.addSiteListener(this);
        Cache.setSite(mSiteManager.getCurrentSite(), appContext);

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

        SharedPreferences prefs = getSharedPreferences(Prefs.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.registerOnSharedPreferenceChangeListener(this);

        // Set the background to the color of the current site.
        mBackgroundColorizer = new BackgroundColorizer(appContext);
        applySiteColor(mSiteManager.getCurrentSite());

        // Change the SW decoder preference based on OS version (if unset).
        updateSWDecoderPreference(prefs);
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
        mSiteManager.removeSiteListener(this);
        mOneLinerManager.unlistenForPreferences();
        if(mScrobbler.isActive()) {
            mScrobbler.stop();
        }
        mSiteManager.stop();
        SharedPreferences p = getSharedPreferences(Prefs.PREFS_NAME, Context.MODE_PRIVATE);
        p.registerOnSharedPreferenceChangeListener(this);
    }


    /** The user selected a stream on the current site. */
    public void onUserPickedStream(URL streamUrl, int streamId)
    {
        // Update the database.
        Site site = mSiteManager.getCurrentSite();
        Context appContext = getApplicationContext();
        SQLiteDatabase db = new DbOpenHelper(appContext).getWritableDatabase();
        try {
            DbDataHelper data = new DbDataHelper(db);
            data.setRemoteStreamForSite(site.getId(), streamId);
        } finally {
            db.close();
        }

        // Update the prefs.
        Prefs.setStream(streamUrl, streamId, appContext);
    }


    ///
    /// Getters
    ///

    public PlaylistManager getPlaylistManager() { return mPlaylistManager; }
    public StreamsManager getStreamsManager() { return mStreamsManager; }
    public PlayerManager getPlayerManager() { return mPlayerManager; }
    public OneLinerManager getOneLinerManager() { return mOneLinerManager; }
    public SiteManager getSiteManager() { return mSiteManager; }


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

    /** Update this window's background to the current background color. */
    public void updateWindowBackground(Window window)
    {
        window.setBackgroundDrawable(mBackgroundColorizer.getDrawable());
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


    ///
    /// Site updates
    ///

    @Override
    public void onSiteChanged(Site newSite)
    {
        // Stop the player.
        Context appContext = getApplicationContext();
        if(mPlayerManager.getPlayerState() != PlayerService.State.STOPPED) {
            Intent intent = new Intent(appContext, PlayerService.class);
            stopService(intent);
        }

        // Notify the cache (this will clear all cached documents).
        Cache.setSite(newSite, appContext);

        // Reset all document managers.
        mPlaylistManager.reset();
        mOneLinerManager.reset();
        mStreamsManager.reset();
        Prefs.clearOneLinerUpdateTime(appContext);

        // Update the prefs with the selected stream for this site.
        Stream stream = getSelectedStreamForSite(newSite);
        if(stream != null) {
            Prefs.setStream(stream.getUrl(), stream.getId(), appContext);
        } else {
            Prefs.clearStream(appContext);
        }

        // Update the background color.
        applySiteColor(newSite);
    }


    ///
    /// Utility methods
    ///

    private void applySiteColor(Site site)
    {
        Integer bgColor = site.getColor();
        if(bgColor != null) {
            mBackgroundColorizer.shiftBackgroundColorTo(bgColor);
        } else {
            Log.w(TAG, String.format("Site %d (\"%s\") has no BG color!", site.getId(),
                        site.getName()));
        }
    }


    private Stream getSelectedStreamForSite(Site site)
    {
        Stream stream = null;
        SQLiteDatabase db = new DbOpenHelper(getApplicationContext()).getReadableDatabase();
        try {
            // Check for selection.
            DbDataHelper data = new DbDataHelper(db);
            Integer streamId = data.getPickedStreamForSite(site.getId());

            if(streamId != null) {
                // There is a selection.  Read the specified stream.
                Cursor cursor = data.selectStream(streamId);
                try {
                    if(cursor.getCount() == 1) {
                        cursor.moveToFirst();
                        stream = Stream.fromCursor(cursor);
                    }
                } finally {
                    cursor.close();
                }
            }
        } finally {
            db.close();
        }
        return stream;
    }


    private void updateSWDecoderPreference(SharedPreferences prefs)
    {
        if(!prefs.contains(Prefs.USE_SW_DECODER_KEY)) {
            if(android.os.Build.VERSION.SDK.equals("3")) {
                Log.i(TAG, "Cupcake detected; enabling SW MP3 decoder");
                Prefs.setUseSWDecoder(this, true);
            }
        }
    }
}
