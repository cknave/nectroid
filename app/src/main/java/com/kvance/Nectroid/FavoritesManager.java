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

import org.xml.sax.SAXException;

import java.util.Date;
import java.util.HashSet;


/** Manager responsible for updating a Favorites.
 *
 * Register for Favorites update events with addTaskListener().
 * Register for song update events with addSongListener().
 *
 * If your activity or service wants the Favorites to be automatically refreshed before it gets
 * stale, call requestAutoUpdate().  Once you no longer need them, make sure to unregister your
 * request with unrequestAutoUpdate().
 */
public class FavoritesManager extends AutoRefreshDocManager<Favorites>
{
    private Favorites mFavorites;

    // Milliseconds before we're considered "almost done"
    private static final long ALMOST_DONE_TIME = 25000;

    // Minimum time between auto-refreshes (in ms)
    private static final long MIN_AUTO_REFRESH_TIME = 30000;

    private static final String TAG = "Nectroid";


    public FavoritesManager(Context applicationContext)
    {
        super(applicationContext);
    }

    /** Return the QUEUE DocId. */
    @Override
    public Cache.DocId getDocId()
    {
        return Cache.DocId.FAVORITES;
    }

    /** Parse an XML file into a Favorites object. */
    @Override
    public Favorites parseDocument(String xmlData, Context context)
    {
        //System.out.println("PARSE DOCUMENT="+xmlData);
        try {
            mFavorites = new Favorites(xmlData);
            //System.out.println("nb of favs: "+mFavorites.getFavorites().size());
            return mFavorites;
        } catch(SAXException e) {
            return null;
        }
    }


    @Override
    public void onParserSuccess(Favorites result, Context context)
    {
        super.onParserSuccess(result, context);
    }


    ///
    /// Public interface
    ///

    public void onLowMemory()
    {
        // We can release the Favorites if nothing's using it.
        if(mUpdateTask == null) {
            mFavorites = null;
        }
    }


    /** Reset all data. */
    public void reset() {
        cancelUpdate();
    }


    ///
    /// Getters
    ///

    public Favorites getFavorites() { return mFavorites; }

    ///
    /// Auto-refresh methods
    ///

    @Override
    protected void scheduleNextRefresh(Context context)
    {
        long delay = 1000L*60L;

        // Remove any old auto-update callbacks before posting this one.
        mHandler.removeCallbacks(autoUpdateFavorites);
        mHandler.postDelayed(autoUpdateFavorites, delay);
    }

    @Override
    protected void unscheduleNextRefresh(Context context)
    {
        mHandler.removeCallbacks(autoUpdateFavorites);
    }

    private Runnable autoUpdateFavorites = new Runnable() {
        public void run() {
            update(mContext, false);
            // The next update will be scheduled after our UpdateTask completes.
        }
    };

    @Override
    protected boolean hasDocument()
    {
        return (mFavorites != null);
    }
}
