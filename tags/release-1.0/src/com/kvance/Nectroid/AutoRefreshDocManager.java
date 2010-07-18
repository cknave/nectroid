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

import java.util.TreeSet;

import android.content.Context;
import android.os.Handler;
import android.util.Log;


/** A document manager that automatically refreshes its content.
 *
 * Overload the scheduleNextRefresh() method to schedule the next auto-refresh call.  This method
 * will be called as soon as any objects have requested auto-refresh.
 */
abstract class AutoRefreshDocManager<Result> extends CachedDocManager<Result>
{
    protected TreeSet<Integer> mAutoRefreshRequesters;
    protected Context mContext;
    protected Handler mHandler;

    public AutoRefreshDocManager(Context applicationContext)
    {
        super();
        mContext = applicationContext;
        mAutoRefreshRequesters = new TreeSet<Integer>();
        mHandler = new Handler();
    }


    ///
    /// Required methods
    ///

    /** Schedule the next update.
     *
     * At some point in the future after calling this method, a call to update(context, false)
     * should be made.
     */
    protected abstract void scheduleNextRefresh(Context context);


    /** Unschedule the next update.
     *
     * This should undo the upcoming refresh scheduled by scheduleNextRefresh().
     */
    protected abstract void unscheduleNextRefresh(Context context);


    /** Return true if this manager has a document. */
    protected abstract boolean hasDocument();


    ///
    /// Public auto-refresh interface
    ///

    public void requestAutoRefresh(Object requester)
    {
        mAutoRefreshRequesters.add(requester.hashCode());
        if(mAutoRefreshRequesters.size() == 1) {
            startAutoRefresh();
        }
    }

    public void unrequestAutoRefresh(Object requester)
    {
        mAutoRefreshRequesters.remove(requester.hashCode());
        if(mAutoRefreshRequesters.size() == 0) {
            stopAutoRefresh();
        }
    }


    ///
    /// Auto-refresh support
    ///

    @Override
    public void onParserSuccess(Result result, Context context)
    {
        super.onParserSuccess(result, context);

        // A new document has been loaded.  If we're doing auto-refresh, schedule the next one.
        if(mAutoRefreshRequesters.size() > 0) {
            // Start auto-updates once this task finishes.  If we didn't, then a possible
            // request to refresh would be ignored since the task is still technically
            // running.
            mHandler.post(new Runnable() {
                public void run() {
                    startAutoRefresh();
                }
            });
        }
    }


    ///
    /// Utility methods
    ///

    private void startAutoRefresh()
    {
        if(!hasDocument()) {
            // No playlist to update!  Fetch one; cached is fine.
            update(mContext, true);

        } else {
            // Playlist is still current.  Schedule the update for later.
            scheduleNextRefresh(mContext);
        }
    }

    private void stopAutoRefresh()
    {
        unscheduleNextRefresh(mContext);
    }
}
