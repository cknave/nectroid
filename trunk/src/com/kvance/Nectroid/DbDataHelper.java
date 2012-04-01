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

import java.util.ArrayList;
import java.util.List;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;


class DbDataHelper
{
    private SQLiteDatabase mDB;
    private SQLiteStatement mInsertStream;

    private static final String[] STREAM_COLUMNS = {
        DbOpenHelper.STREAMS_REMOTE_ID_KEY,
        DbOpenHelper.STREAMS_URL_KEY,
        DbOpenHelper.STREAMS_NAME_KEY,
        DbOpenHelper.STREAMS_COUNTRY_KEY,
        DbOpenHelper.STREAMS_BITRATE_KEY,
        DbOpenHelper.STREAMS_TYPE_CODE_KEY,
        DbOpenHelper.STREAMS_TYPE_NAME_KEY
    };

    private static final String TAG = "Nectroid";


    public DbDataHelper(SQLiteDatabase db)
    {
        mDB = db;
    }


    ///
    /// Site methods
    ///

    /** Delete the site with this ID from the database. */
    public void deleteSite(int siteId)
    {
        String siteIdString = String.valueOf(siteId);
        mDB.execSQL("DELETE FROM " + DbOpenHelper.SITES_TABLE_NAME + " WHERE " +
                DbOpenHelper.SITES_ID_KEY + " = " + siteIdString + ";");
    }


    /** Return a Site object for this id. */
    public Site getSite(int siteId)
    {
        Site site = null;

        final String[] cols = {
            DbOpenHelper.SITES_NAME_KEY,
            DbOpenHelper.SITES_URL_KEY,
            DbOpenHelper.SITES_COLOR_KEY
        };
        final String where = DbOpenHelper.SITES_ID_KEY + " = " + String.valueOf(siteId);
        Cursor c = mDB.query(DbOpenHelper.SITES_TABLE_NAME, cols, where, null, null, null, null);
        try {
            if(c.getCount() == 1) {
                c.moveToFirst();
                Integer id = new Integer((int)siteId);
                String name = c.getString(0);
                String baseUrl = c.getString(1);
                String colorString = c.getString(2);
                site = new Site(id, name, baseUrl, colorString);
            } else {
                Log.w(TAG, String.format("Could not find site with id %d", siteId));
            }
        } finally {
            c.close();
        }
        return site;
    }


    /** Return the number of sites in the database. */
    public int getSiteCount()
    {
        int count;
        Cursor cursor = mDB.rawQuery("SELECT COUNT(*) FROM " + DbOpenHelper.SITES_TABLE_NAME + ";",
                null);
        try {
            cursor.moveToFirst();
            count = cursor.getInt(0);
        } finally {
            cursor.close();
        }
        return count;
    }


    /** Add a site to the database.
     *
     * Returns the new site ID.
     */
    public int insertSite(Site site)
    {
        ContentValues values = contentValuesForSite(site);
        int id = (int)mDB.insert(DbOpenHelper.SITES_TABLE_NAME, null, values);
        return (int)id;
    }


    /** Get a cursor to all the sites in the database
     *
     * Remember to close() the returned cursor when you're done with it!
     */
    public Cursor selectAllSites()
    {
        // Query the database.
        Cursor cursor = mDB.rawQuery("SELECT * FROM " + DbOpenHelper.SITES_TABLE_NAME, null);
        return cursor;
    }


    /** Update this site's entry in the database. */
    public void updateSite(Site site)
    {
        ContentValues values = contentValuesForSite(site);
        String where = DbOpenHelper.SITES_ID_KEY + " = " + String.valueOf(site.getId());
        int rowsAffected = mDB.update(DbOpenHelper.SITES_TABLE_NAME, values, where, null);
        if(rowsAffected != 1) {
            throw new RuntimeException("Failed to update site %s" + String.valueOf(site.getId()));
        }
    }


    ///
    /// Stream methods
    ///

    /** Delete all streams from the site with this id. */
    public void deleteStreamsFromSite(int siteId)
    {
        final Long[] delArgs = { (long)siteId };
        mDB.execSQL("DELETE FROM " + DbOpenHelper.STREAMS_TABLE_NAME + " WHERE site_id = ?;",
                delArgs);
    }


    /** Get the user-selected stream ID for the site with this ID. */
    public Integer getPickedStreamForSite(int siteId)
    {
        Integer result = null;

        String columns[] = { DbOpenHelper.SELECTED_STREAM_STREAM_KEY };
        String where = DbOpenHelper.SELECTED_STREAM_SITE_KEY + " = " + String.valueOf(siteId);
        Cursor cursor = mDB.query(DbOpenHelper.SELECTED_STREAM_TABLE_NAME, columns,
                where, null, null, null, null);
        try {
            if(cursor.getCount() == 1) {
                cursor.moveToFirst();
                int intValue = cursor.getInt(0);
                result = new Integer(intValue);
            }
        } finally {
            cursor.close();
        }
        return result;
    }


    /** Add a stream to the site with this id. */
    public int insertStream(Stream stream, int siteId)
    {
        compileInsertStream();
        mInsertStream.bindLong  (1, siteId);
        mInsertStream.bindLong  (2, stream.getId());
        mInsertStream.bindString(3, stream.getUrl().toString());
        mInsertStream.bindString(4, stream.getName());
        mInsertStream.bindString(5, stream.getCountry());
        mInsertStream.bindLong  (6, stream.getBitrate());
        mInsertStream.bindLong  (7, stream.getType().ordinal());

        // typeName is probably null.
        String typeName = stream.getSavedTypeName();
        if(typeName == null) {
            mInsertStream.bindNull(8);
        } else {
            mInsertStream.bindString(8, typeName);
        }
        return (int)mInsertStream.executeInsert();
    }


    /** Get a cursor to all the streams for this site. 
     *
     * Remember to close() the returned cursor when you're done with it!
     */
    public Cursor selectAllStreams(int siteId)
    {
        // Query the database.
        String where = DbOpenHelper.STREAMS_SITE_KEY + " = " + String.valueOf(siteId);
        Cursor cursor = mDB.query(DbOpenHelper.STREAMS_TABLE_NAME, STREAM_COLUMNS,
                where, null, null, null, DbOpenHelper.STREAMS_REMOTE_ID_KEY);
        return cursor;
    }


    /** Get a cursor to the stream with this id.
     *
     * Remember to close() the returned cursor when you're done with it!
     */
    public Cursor selectStream(int streamId)
    {
        // Query the database.
        String where = DbOpenHelper.STREAMS_ID_KEY + " = " + String.valueOf(streamId);
        Cursor cursor = mDB.query(DbOpenHelper.STREAMS_TABLE_NAME, STREAM_COLUMNS, where, null,
                null, null, null);
        return cursor;
    }


    /** Get a cursor to the stream with the remote id for this site id.
     *
     * Remember to close() the returned cursor when you're done with it!
     */
    public Cursor selectStreamRemote(int siteId, int streamRemoteId, String[] columns)
    {
        // Query the database.
        String where = DbOpenHelper.STREAMS_REMOTE_ID_KEY + " = " + String.valueOf(streamRemoteId) +
            " AND " + DbOpenHelper.STREAMS_SITE_KEY + " = " + String.valueOf(siteId);
        Cursor cursor = mDB.query(DbOpenHelper.STREAMS_TABLE_NAME, columns, where, null,
                null, null, null);
        return cursor;
    }


    /** Set the user-selected local stream ID for the site with this ID. */
    public void setLocalStreamForSite(int siteId, int streamLocalId)
    {
        // Try updating the record first.
        ContentValues values = new ContentValues();
        values.put(DbOpenHelper.SELECTED_STREAM_STREAM_KEY, (long)streamLocalId);
        String siteIdString = String.valueOf(siteId);
        String where = DbOpenHelper.SELECTED_STREAM_SITE_KEY + " = " + String.valueOf(siteId);
        int rowsAffected = mDB.update(DbOpenHelper.SELECTED_STREAM_TABLE_NAME, values, where, null);

        // If there was no record for this site, insert it.
        if(rowsAffected != 1) {
            String streamIdString = String.valueOf(streamLocalId);
            mDB.execSQL("INSERT INTO " + DbOpenHelper.SELECTED_STREAM_TABLE_NAME + " (" +
                    DbOpenHelper.SELECTED_STREAM_SITE_KEY + ", " +
                    DbOpenHelper.SELECTED_STREAM_STREAM_KEY + ") VALUES (" +
                    siteIdString + ", " +
                    streamIdString + ");");
        }
    }


    /** Set the user-selected remote stream ID for the site with this ID. */
    public void setRemoteStreamForSite(int siteId, int streamRemoteId)
    {
        Log.d(TAG, String.format("Picking stream %d for site %d", streamRemoteId, siteId));

        // Find the local ID for the stream.
        String[] columns = { DbOpenHelper.STREAMS_ID_KEY };
        Cursor cursor = selectStreamRemote(siteId, streamRemoteId, columns);
        int streamLocalId;
        try {
            if(cursor.getCount() != 1) {
                throw new RuntimeException(String.format("Tried to pick nonexistent stream %d on " +
                            "site %d", streamRemoteId, siteId));
            }
            cursor.moveToFirst();
            streamLocalId = cursor.getInt(0);
        } finally {
            cursor.close();
        }

        // Now we can update the record.
        setLocalStreamForSite(siteId, streamLocalId);
    }


    ///
    /// Picked stream methods
    ///

    /** Delete the user-selected stream for the site with this ID. */
    public void deletePickedStreamForSite(int siteId)
    {
        Log.d(TAG, String.format("Deleting picked stream for site %d", siteId));

        String siteIdString = String.valueOf(siteId);
        mDB.execSQL("DELETE FROM " + DbOpenHelper.SELECTED_STREAM_TABLE_NAME + " WHERE " +
                DbOpenHelper.SELECTED_STREAM_SITE_KEY + " = " + siteIdString + ";");
    }


    ///
    /// Utility methods
    ///

    private void compileInsertStream()
    {
        if(mInsertStream == null) {
            mInsertStream = mDB.compileStatement("INSERT INTO " + DbOpenHelper.STREAMS_TABLE_NAME +
                    "(" +
                    DbOpenHelper.STREAMS_SITE_KEY + ", " +
                    DbOpenHelper.STREAMS_REMOTE_ID_KEY + ", " +
                    DbOpenHelper.STREAMS_URL_KEY + ", " +
                    DbOpenHelper.STREAMS_NAME_KEY + ", " +
                    DbOpenHelper.STREAMS_COUNTRY_KEY + ", " +
                    DbOpenHelper.STREAMS_BITRATE_KEY + ", " +
                    DbOpenHelper.STREAMS_TYPE_CODE_KEY + ", " +
                    DbOpenHelper.STREAMS_TYPE_NAME_KEY + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?);");
        }
    }


    /** Return a ContentValues mapping values in this site to its database columns. */
    private ContentValues contentValuesForSite(Site site)
    {
        ContentValues values = new ContentValues();
        values.put(DbOpenHelper.SITES_NAME_KEY, site.getName());
        values.put(DbOpenHelper.SITES_URL_KEY, site.getBaseUrl());
        String colorString = String.format("#%06X", site.getColor());
        values.put(DbOpenHelper.SITES_COLOR_KEY, colorString);
        return values;
    }
}
