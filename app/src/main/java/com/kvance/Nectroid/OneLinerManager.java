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

import org.xml.sax.SAXException;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;


public class OneLinerManager extends AutoRefreshDocManager<OneLiner.List>
        implements SharedPreferences.OnSharedPreferenceChangeListener
{
    private OneLiner.List mOneLiners;
    private Date mTimestamp;
    private long mLastUpdateTime;

    private static final String TAG = "NectroidOneLinersManager";


    public OneLinerManager(Context applicationContext)
    {
        super(applicationContext);
        mLastUpdateTime = 0L;
    }


    /** Return the ONELINER DocId. */
    @Override
    public Cache.DocId getDocId()
    {
        return Cache.DocId.ONELINER;
    }


    /** A cached document was retrieved. */
    @Override
    public void onCachedCopyRetrieved(Context context)
    {
        // Retrieve the timestamp from the prefs.
        mTimestamp = Prefs.getOneLinerUpdateTime(context);
    }


    /** A new document was downloaded. */
    @Override
    public void onNewCopyRetrieved(FetchUrl.Result fetchResult, Context context)
    {
        // Save the timestamp to the prefs.
        mTimestamp = fetchResult.getTimestamp();
        Prefs.setOneLinerUpdateTime(mTimestamp, context);
        mLastUpdateTime = System.currentTimeMillis();
    }


    /** Parse an XML file into a OneLiner list. */
    @Override
    public OneLiner.List parseDocument(String xmlData, Context context)
    {
        try {
            mOneLiners = OneLiner.listFromXml(xmlData);
        } catch(SAXException e) {
            mOneLiners = null;
        }

        // Update the timestamp.
        if(mOneLiners != null) {
            mOneLiners.setTimestamp(mTimestamp);
        }

        return mOneLiners;
    }


    ///
    /// Public interface
    ///

    /** Return the current oneliners. */
    public OneLiner.List getOneLiners()
    {
        return mOneLiners;
    }


    /** Release whatever memory we can. */
    public void onLowMemory()
    {
        // We can dump the oneliners.
        mOneLiners = null;
    }


    /** Reset all data. */
    public void reset()
    {
        cancelUpdate();
        mOneLiners = null;
        mTimestamp = null;
        mLastUpdateTime = 0L;
    }

    
    /** Start listening for changes in the oneliner refresh time preference. */
    public void listenForPreferences()
    {
        SharedPreferences p = mContext.getSharedPreferences(Prefs.PREFS_NAME, Context.MODE_PRIVATE);
        p.registerOnSharedPreferenceChangeListener(this);
    }

    /** Stop listening for preference changes. */
    public void unlistenForPreferences()
    {
        SharedPreferences p = mContext.getSharedPreferences(Prefs.PREFS_NAME, Context.MODE_PRIVATE);
        p.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key)
    {
        if(key.equals(Prefs.ONELINER_REFRESH_PERIOD_KEY)) {
            // Reschedule the next update.
            if(!mAutoRefreshRequesters.isEmpty()) {
                scheduleNextRefresh(mContext);
            }
        }
    }


    ///
    /// Auto-refresh methods
    ///

    @Override
    protected void scheduleNextRefresh(Context context)
    {
        int refreshPeriod = Prefs.getOneLinerRefreshPeriod(context);
        long delay;

        if(mOneLiners == null) {
            delay = 0;

        } else if(refreshPeriod > 0) {
            // The next refresh should happen refreshPeriod seconds after the last update.
            long timestamp = Math.max(mLastUpdateTime, mOneLiners.getTimestamp().getTime());
            long nextUpdateTime = timestamp + (1000L * refreshPeriod);
            long now = System.currentTimeMillis();
            delay = nextUpdateTime - now;

            // If we're late, update immediately.
            if(delay < 0) {
                delay = 0;
            }

        } else {
            // Auto-refresh disabled.
            mHandler.removeCallbacks(autoUpdateOneLiners);
            return;
        }

        mHandler.removeCallbacks(autoUpdateOneLiners);
        mHandler.postDelayed(autoUpdateOneLiners, delay);
    }

    @Override
    protected void unscheduleNextRefresh(Context context)
    {
        mHandler.removeCallbacks(autoUpdateOneLiners);
    }

    private Runnable autoUpdateOneLiners = new Runnable() {
        public void run() {
            update(mContext, false);
        }
    };

    @Override
    protected boolean hasDocument()
    {
        return (mOneLiners != null);
    }
}
