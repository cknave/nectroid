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

import java.util.List;

import org.xml.sax.SAXException;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;


public class StreamsManager extends CachedDocManager<Stream.List>
{
    private Stream.List mStreams;

    private static final String TAG = "Nectroid";

    /** Return the STREAMS DocId. */
    @Override
    public Cache.DocId getDocId()
    {
        return Cache.DocId.STREAMS;
    }


    /** Parse an XML file into a Stream list. */
    @Override
    public Stream.List parseDocument(String xmlData, Context context)
    {
        try {
            mStreams = Stream.listFromXml(xmlData);
            return mStreams;
        } catch(SAXException e) {
            Log.w(TAG, "Failed to parse streams.", e);
            return null;
        }
    }


    /** Update the streams database with this new info. */
    @Override
    public void onParserSuccess(Stream.List result, Context context)
    {
        // Update the database with the new streams info.
        int siteId = Prefs.getSiteId(context);
        SQLiteDatabase db = new DbOpenHelper(context).getWritableDatabase();
        try {
            // Replace the old stream list.
            replaceStreamsForSite(result, siteId, db);
            // Update the selected stream to its new ID.
            updateStreamPickedInDatabase(siteId, db, context);
        } finally {
            db.close();
        }
    }


    ///
    /// Getters
    ///
    
    public List<Stream> getStreams() { return mStreams; }


    ///
    /// Public interface
    ///

    public void onLowMemory()
    {
        // We can dump the streams.
        mStreams = null;
    }

    /** Clear all data to before any streams were loaded. */
    public void reset()
    {
        cancelUpdate();
        mStreams = null;
    }


    ///
    /// Utility methods
    ///

    /** Replace the streams for some site in the database with this new list of streams. */
    private void replaceStreamsForSite(Stream.List streams, int siteId, SQLiteDatabase db)
    {
        // Delete the old streams.
        DbDataHelper data = new DbDataHelper(db);
        data.deleteStreamsFromSite(siteId);

        // Insert the new ones.
        for(Stream stream : streams) {
            data.insertStream(stream, siteId);
        }
    }

    /** Update the selected stream after replacing the stream list in the database. */
    private void updateStreamPickedInDatabase(int siteId, SQLiteDatabase db, Context context)
    {
        // Get the remote ID from the prefs.
        Integer remoteStreamId = Prefs.getStreamId(context);
        if(remoteStreamId == null) {
            // No stream picked; nothing to do.
            return;
        }

        // Look for that ID in our new list of streams.
        DbDataHelper data = new DbDataHelper(db);
        String[] columns = { DbOpenHelper.STREAMS_ID_KEY };
        Cursor cursor = data.selectStreamRemote(siteId, remoteStreamId, columns);
        Integer localStreamId = null;
        try {
            if(cursor.getCount() == 1) {
                cursor.moveToFirst();
                localStreamId = cursor.getInt(0);
            }
        } finally {
            cursor.close();
        }
        
        if(localStreamId != null) {
            // Update to the new local ID.
            Log.d(TAG, String.format("Updated stream (remote id=%d) to local id %d for site %d",
                        remoteStreamId, localStreamId.intValue(), siteId));
            data.setLocalStreamForSite(siteId, localStreamId);
        } else {
            // The stream no longer exists.  Clear the prefs and DB row.
            Log.w(TAG, String.format("Selected stream (remote id=%d) for site %d no longer exists!",
                        remoteStreamId, siteId));
            Prefs.clearStream(context);
            data.deletePickedStreamForSite(siteId);
        }
    }
}
