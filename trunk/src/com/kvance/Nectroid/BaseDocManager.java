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
import android.os.AsyncTask;


public abstract class BaseDocManager
{
    protected HashSet<BackgroundTaskListener> mListeners;
    protected AsyncTask<Void, Void, Void> mUpdateTask;

    /** Override this to return a new task to do the document retrieval work */
    protected abstract AsyncTask<Void, Void, Void> newUpdateTask(Context context, boolean useCache);


    public BaseDocManager()
    {
        mListeners = new HashSet<BackgroundTaskListener>();
    }


    ///
    /// Control methods
    ///

    /** Update the document for this manager.
     *
     * If an update is already in progess, this call will be ignored.
     */
    public void update(Context context, boolean useCache)
    {
        if(!isUpdating()) {
            mUpdateTask = newUpdateTask(context, useCache);
            mUpdateTask.execute();
        }
    }

    /** Cancel an ongoing update.
     *
     * If no update is in progress, this call will be ignored.
     */
    public void cancelUpdate()
    {
        if(isUpdating()) {
            mUpdateTask.cancel(true);
            mUpdateTask = null;
            notifyCancelled();
        }
    }

    /** Return true if an update task is in progress. */
    public boolean isUpdating()
    {
        return (mUpdateTask != null);
    }


    ///
    /// Listener methods
    ///

    public void addTaskListener(BackgroundTaskListener listener)
    {
        mListeners.add(listener);
    }

    public void removeTaskListener(BackgroundTaskListener listener)
    {
        mListeners.remove(listener);
    }


    ///
    /// Notification methods
    ///

    protected void notifyStarted()
    {
        for(BackgroundTaskListener listener : mListeners) {
            listener.onTaskStarted(this);
        }
    }

    protected void notifyFinished(Object result)
    {
        for(BackgroundTaskListener listener : mListeners) {
            listener.onTaskFinished(this, result);
        }
    }

    protected void notifyCancelled()
    {
        for(BackgroundTaskListener listener : mListeners) {
            listener.onTaskCancelled(this);
        }
    }

    protected void notifyFailed()
    {
        for(BackgroundTaskListener listener : mListeners) {
            listener.onTaskFailed(this);
        }
    }
}
