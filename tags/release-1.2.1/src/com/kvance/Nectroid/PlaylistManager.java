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

import java.util.Date;
import java.util.HashSet;

import org.xml.sax.SAXException;

import android.content.Context;
import android.os.Handler;
import android.util.Log;


/** Manager responsible for updating a playlist.
 *
 * Register for playlist update events with addTaskListener().
 * Register for song update events with addSongListener().
 *
 * If your activity or service wants the playlist to be automatically refreshed before it gets
 * stale, call requestAutoUpdate().  Once you no longer need them, make sure to unregister your
 * request with unrequestAutoUpdate().
 */
public class PlaylistManager extends AutoRefreshDocManager<Playlist>
{
    public interface SongListener {
        abstract void onSongChanged(Playlist.EntryAndTimeLeft newSong);
    }


    private HashSet<SongListener> mSongListeners;

    private Date mTimeBase;
    private Playlist mPlaylist;
    private long mLastUpdateTime;

    // Milliseconds before we're considered "almost done"
    private static final long ALMOST_DONE_TIME = 25000;

    // Minimum time between auto-refreshes (in ms)
    private static final long MIN_AUTO_REFRESH_TIME = 30000;

    private static final String TAG = "Nectroid";


    public PlaylistManager(Context applicationContext)
    {
        super(applicationContext);
        mSongListeners = new HashSet<SongListener>();
        mLastUpdateTime = 0L;
    }


    /** Return the QUEUE DocId. */
    @Override
    public Cache.DocId getDocId()
    {
        return Cache.DocId.QUEUE;
    }


    /** A cached document was retrieved. */
    @Override
    public void onCachedCopyRetrieved(Context context)
    {
        // Retrieve the timestamp from the prefs.
        mTimeBase = Prefs.getPlaylistUpdateTime(context);
    }


    /** A new document was downloaded. */
    @Override
    public void onNewCopyRetrieved(FetchUrl.Result fetchResult, Context context)
    {
        // Save the timestamp to the prefs.
        mTimeBase = fetchResult.getTimestamp();
        Prefs.setPlaylistUpdateTime(mTimeBase, context);
        mLastUpdateTime = System.currentTimeMillis();
    }


    /** Parse an XML file into a Playlist object. */
    @Override
    public Playlist parseDocument(String xmlData, Context context)
    {
        try {
            mPlaylist = new Playlist(xmlData, mTimeBase);
            return mPlaylist;
        } catch(SAXException e) {
            return null;
        }
    }


    @Override
    public void onParserSuccess(Playlist result, Context context)
    {
        // Update whatever listeners we have.
        if(mSongListeners.size() > 0) {
            notifyNewSong.run();
        }

        super.onParserSuccess(result, context);
    }


    ///
    /// Public interface
    ///

    public void addSongListener(SongListener listener)
    {
        mSongListeners.add(listener);
        if(mSongListeners.size() == 1) {
            startSongUpdates();
        }
    }

    public void removeSongListener(SongListener listener)
    {
        mSongListeners.remove(listener);
        if(mSongListeners.size() == 0) {
            stopSongUpdates();
        }
    }


    public Playlist.EntryAndTimeLeft getCurrentSong()
    {
        if(mPlaylist == null) {
            return null;
        } else {
            return mPlaylist.atNow();
        }
    }


    public void onLowMemory()
    {
        // We can release the playlist if nothing's using it.
        if(mUpdateTask == null && mSongListeners.isEmpty()) {
            mPlaylist = null;
        }
    }


    /** Reset all data. */
    public void reset()
    {
        cancelUpdate();
        mTimeBase = null;
        mPlaylist = null;
        mLastUpdateTime = 0L;
    }


    ///
    /// Getters
    ///

    public Playlist getPlaylist() { return mPlaylist; }


    ///
    /// Song updates
    ///


    private void startSongUpdates()
    {
        if(mPlaylist != null) {
            Playlist.EntryAndTimeLeft ent = mPlaylist.atNow();
            if(ent != null) {
                scheduleSongUpdate(ent);
            }
        }
        // If we weren't able to schedule one due to an out-of-date playlist, it will be scheduled
        // the next time our UpdateTask finishes.
    }


    private void stopSongUpdates()
    {
        mHandler.removeCallbacks(notifyNewSong);
    }


    private void scheduleSongUpdate(Playlist.EntryAndTimeLeft ent)
    {
        // Remove any old callbacks before posting the new one.
        long delay = ent.getTimeLeft() * 1000L;
        mHandler.removeCallbacks(notifyNewSong);
        mHandler.postDelayed(notifyNewSong, delay);
    }


    private Runnable notifyNewSong = new Runnable() {
        public void run() {
            Playlist.EntryAndTimeLeft ent = mPlaylist.atNow();
            if(ent != null) {
                // Notify all listeners of the new song.
                for(SongListener listener : mSongListeners) {
                    listener.onSongChanged(ent);
                }

                // Schedule the next notification.
                scheduleSongUpdate(ent);
            }
        }
    };


    private void stopPlaylistAutoUpdate()
    {
        mHandler.removeCallbacks(autoUpdatePlaylist);
    }


    ///
    /// Auto-refresh methods
    ///

    @Override
    protected void scheduleNextRefresh(Context context)
    {
        // How much time has elapsed since the playlist's timebase?
        long now = System.currentTimeMillis();
        long timeElapsed = now - mPlaylist.getTimeBase().getTime();

        // Playlist length is in seconds, so multiply by 1000.
        long totalLength = mPlaylist.lengthInSeconds() * 1000L;
        long delay = totalLength - timeElapsed - ALMOST_DONE_TIME;
        delay = Math.max(delay, 0);    // An old queue may have finished in the past

        // Apply a minimum delay, so we're not constantly reloading when there's an empty queue.
        long nextUpdateTime = now + delay;
        long timeBetweenUpdates = nextUpdateTime - mLastUpdateTime;
        if(timeBetweenUpdates < MIN_AUTO_REFRESH_TIME) {
            delay = MIN_AUTO_REFRESH_TIME - timeBetweenUpdates;
        }

        // Remove any old auto-update callbacks before posting this one.
        mHandler.removeCallbacks(autoUpdatePlaylist);
        mHandler.postDelayed(autoUpdatePlaylist, delay);
    }


    @Override
    protected void unscheduleNextRefresh(Context context)
    {
        mHandler.removeCallbacks(autoUpdatePlaylist);
    }


    private Runnable autoUpdatePlaylist = new Runnable() {
        public void run() {
            update(mContext, false);
            // The next update will be scheduled after our UpdateTask completes.
        }
    };


    @Override
    protected boolean hasDocument()
    {
        return (mPlaylist != null);
    }
}
