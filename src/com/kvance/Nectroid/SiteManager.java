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

import java.util.HashSet;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;


class SiteManager implements SharedPreferences.OnSharedPreferenceChangeListener
{
    public interface SiteListener
    {
        public abstract void onSiteChanged(Site newSite);
    }

    private HashSet<SiteListener> mSiteListeners;
    private Context mContext;
    private Site mCurrentSite;

    private static final String TAG = "Nectroid";


    SiteManager(Context appContext)
    {
        mContext = appContext;
        mSiteListeners = new HashSet<SiteListener>();
        refreshCurrentSite();
    }


    ///
    /// Public interface
    ///

    /** Start listening for site changes. */
    public void start()
    {
        SharedPreferences p = mContext.getSharedPreferences(Prefs.PREFS_NAME, Context.MODE_PRIVATE);
        p.registerOnSharedPreferenceChangeListener(this);
    }

    /** Stop listening for site changes. */
    public void stop()
    {
        SharedPreferences p = mContext.getSharedPreferences(Prefs.PREFS_NAME, Context.MODE_PRIVATE);
        p.unregisterOnSharedPreferenceChangeListener(this);
    }


    /** Add a site changed listener. */
    public void addSiteListener(SiteListener listener)
    {
        mSiteListeners.add(listener);
    }

    /** Remove a site changed listener. */
    public void removeSiteListener(SiteListener listener)
    {
        mSiteListeners.remove(listener);
    }

    /** Refresh the current site, notifying all listeners of the change. */
    public void refreshCurrentSite()
    {
        // Clear the cache.
        Cache.clear(mContext);

        // Get the new site object.
        int siteId = Prefs.getSiteId(mContext);
        mCurrentSite = getSiteFromDb(siteId);

        // Notify all interested parties.
        notifyNewSite(mCurrentSite);
    }

    ///
    /// Getters
    ///

    public Site getCurrentSite() { return mCurrentSite; }


    ///
    /// Preferences events
    ///

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key)
    {
        if(key.equals(Prefs.SITE_ID_KEY)) {
        	refreshCurrentSite();
        }
    }


    ///
    /// Utility methods
    ///
   
    /** Notify all listeners that the site has changed to newSite. */
    private void notifyNewSite(Site newSite)
    {
        for(SiteListener listener : mSiteListeners) {
            listener.onSiteChanged(newSite);
        }
    }


    /** Read the site with this ID from the database. */
    private Site getSiteFromDb(int siteId)
    {
        SQLiteDatabase db = new DbOpenHelper(mContext).getReadableDatabase();
        Site retval = null;
        try {
            DbDataHelper data = new DbDataHelper(db);
            retval = data.getSite(siteId);
        } finally {
            db.close();
        }
        return retval;
    }
}
