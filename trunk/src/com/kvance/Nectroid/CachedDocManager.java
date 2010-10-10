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

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;


/** A document manager that uses the cache.
 *
 * Result is the class of parsed document we return, e.g. Playlist for Cache.DocId.QUEUE.
 */
abstract class CachedDocManager<Result> extends BaseDocManager
{
    ///
    /// Required methods
    ///

    /** Return the cache document ID for the document we manage.
     *
     * This method runs in the background thread.
     */
    public abstract Cache.DocId getDocId();


    /** Parse the document into its result object.
     *
     * If the parser fails, return null instead.
     *
     * This method runs in the background thread.
     */
    public abstract Result parseDocument(String xmlData, Context context);


    ///
    /// Optional methods
    ///

    /** Called when the document was retrieved from cache.
     *
     * This method runs in the background thread.
     */
    public void onCachedCopyRetrieved(Context context) {}


    /** Called when a new copy of the document was downloaded.
     *
     * This method runs in the background thread.
     */
    public void onNewCopyRetrieved(FetchUrl.Result fetchResult, Context context) {}


    /** Called when the parser successfully parsed the document.
     *
     * This method runs in the UI thread.
     */
    public void onParserSuccess(Result result, Context context) {}


    ///
    /// Base AsyncTask
    ///

    protected class UpdateTask extends AsyncTask<Void, Void, Void>
    {
        private Context mContext;
        private boolean mShouldUseCache;
        private Result mResult;


        public UpdateTask(Context context, boolean useCache)
        {
            mContext = context;
            mShouldUseCache = useCache;
        }

        
        @Override
        protected Void doInBackground(Void... args)
        {
            Cache.DocId docId = getDocId();
            String response = null;

            // If requested, try using the cache first.
            if(mShouldUseCache) {
                response = Cache.read(docId, mContext);
                onCachedCopyRetrieved(mContext);
            }

            // If there's no cached copy, or we don't want it, fetch it.
            if(response == null) {
                URL url = Cache.getUrlForDocId(docId, mContext);
                if(url != null) {
                    // In issue #7, a user got a null URL here.  How?!
                    FetchUrl.Result fetchResult = FetchUrl.get(url);
                    response = fetchResult.getResponse();
                    if(response != null && response.length() > 0) {
                        // Cache this version.
                        Cache.write(docId, response, mContext);
                        onNewCopyRetrieved(fetchResult, mContext);
                    }
                }
            }

            // Parse the result.
            if(response != null) {
                mResult = parseDocument(response, mContext);
            }
            return null;
        }


        @Override
        protected void onPreExecute()
        {
            notifyStarted();
        }

        
        @Override
        protected void onPostExecute(Void result)
        {
            if(mResult != null) {
                notifyFinished(mResult);
                onParserSuccess(mResult, mContext);
            } else {
                notifyFailed();
            }
            mUpdateTask = null;
        }
    }

    protected static final String TAG = "Nectroid";

    @Override
    protected AsyncTask<Void, Void, Void> newUpdateTask(Context context, boolean useCache)
    {
        return new UpdateTask(context, useCache);
    }
}
