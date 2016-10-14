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

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;


class DbOpenHelper extends SQLiteOpenHelper
{
    private static final int DB_VERSION = 3;
    private static final String DB_NAME = "nectroid";

    // A site (e.g. nectarine, cvgm.net)
    public static final String SITES_TABLE_NAME = "sites";
    public static final String SITES_ID_KEY = "_id";
    public static final String SITES_NAME_KEY = "name";
    public static final String SITES_URL_KEY = "base_url";
    public static final String SITES_COLOR_KEY = "color";
    private static final String SITES_TABLE_CREATE = "CREATE TABLE " + SITES_TABLE_NAME + " (" +
        SITES_ID_KEY + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        SITES_NAME_KEY + " TEXT, " +
        SITES_URL_KEY + " TEXT," +
        SITES_COLOR_KEY + " TEXT);";


    // Streams for a site
    public static final String STREAMS_TABLE_NAME = "streams";
    public static final String STREAMS_ID_KEY = "_id";
    public static final String STREAMS_SITE_KEY = "site_id"; // References a "sites" id
    public static final String STREAMS_REMOTE_ID_KEY = "remote_id";
    public static final String STREAMS_URL_KEY = "url";
    public static final String STREAMS_NAME_KEY = "name";
    public static final String STREAMS_COUNTRY_KEY = "country";
    public static final String STREAMS_BITRATE_KEY = "bitrate";
    public static final String STREAMS_TYPE_CODE_KEY = "type_code";
    public static final String STREAMS_TYPE_NAME_KEY = "type_name";
    private static final String STREAMS_TABLE_CREATE = "CREATE TABLE " + STREAMS_TABLE_NAME + " (" +
        STREAMS_ID_KEY + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        STREAMS_SITE_KEY + " INTEGER, " +
        STREAMS_REMOTE_ID_KEY + " INTEGER, " +
        STREAMS_URL_KEY + " TEXT, " +
        STREAMS_NAME_KEY + " TEXT, " +
        STREAMS_COUNTRY_KEY + " TEXT, " +
        STREAMS_BITRATE_KEY + " INTEGER, " +
        STREAMS_TYPE_CODE_KEY + " INTEGER, " +
        STREAMS_TYPE_NAME_KEY + " TEXT);";


    // Stream selections
    public static final String SELECTED_STREAM_TABLE_NAME = "selected_stream";
    public static final String SELECTED_STREAM_SITE_KEY = "site_id"; // References a sites id
    public static final String SELECTED_STREAM_STREAM_KEY = "stream_id"; // References a streams id
    private static final String SELECTED_STREAM_TABLE_CREATE = "CREATE TABLE " +
        SELECTED_STREAM_TABLE_NAME + " (" +
        SELECTED_STREAM_SITE_KEY + " INTEGER, " +
        SELECTED_STREAM_STREAM_KEY + " INTEGER);";


    private Context mContext;

    private static final String TAG = "Nectroid";


    DbOpenHelper(Context context)
    {
        super(context, DB_NAME, null, DB_VERSION);
        mContext = context;
    }


    @Override
    public void onCreate(SQLiteDatabase db)
    {
        // Create the tables.
        db.execSQL(SITES_TABLE_CREATE);
        db.execSQL(STREAMS_TABLE_CREATE);
        db.execSQL(SELECTED_STREAM_TABLE_CREATE);

        // Get the default sites array.
        String[] defaultSites = mContext.getResources().getStringArray(R.array.default_sites);
        if(defaultSites.length % 3 != 0) {
            throw new RuntimeException(String.format("Default sites array is length %d; it " +
                        "should be a multiple of 3", defaultSites.length));
        }

        // Add the first default site, noting its ID.
        if(defaultSites.length > 0) {
            ContentValues firstSite = new ContentValues();
            firstSite.put(SITES_NAME_KEY, defaultSites[0]);
            firstSite.put(SITES_URL_KEY, defaultSites[1]);
            firstSite.put(SITES_COLOR_KEY, defaultSites[2]);
            long firstSiteId = db.insert(SITES_TABLE_NAME, null, firstSite);

            // Save the ID of the first site to the preferences.
            Prefs.setSiteId(mContext, (int)firstSiteId);
        }

        // Add the rest of the default sites.
        String[] nextSite = new String[3];
        for(int i = 3; i < defaultSites.length; i += 3) {
            nextSite[0] = defaultSites[i];
            nextSite[1] = defaultSites[i+1];
            nextSite[2] = defaultSites[i+2];
            db.execSQL("INSERT INTO " + SITES_TABLE_NAME + " (" +
                    SITES_NAME_KEY + ", " +
                    SITES_URL_KEY + ", " +
                    SITES_COLOR_KEY + ") VALUES (?, ?, ?);",
                    nextSite);
        }
    }


    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
    {
        for(int version = oldVersion; version < newVersion; version++) {
        	switch(version) {
        		case 1: {
	                // Delete all streams from version 1.  When they were parsed from XML, the
	                // bitrate was ignored, and saved as 0.
	                Log.i(TAG, "Upgrading from version with bad streams; deleting them");
	                db.execSQL("DELETE FROM " + STREAMS_TABLE_NAME + ";");
	
	                // Clear the stream selection as well.
	                db.execSQL("DELETE FROM " + SELECTED_STREAM_TABLE_NAME + ";");
	                Prefs.clearStream(mContext);
	                
	                break;
        		}
        		case 2: {
        			Log.i(TAG, "Upgrading from version with http Nectarine link; changing to https.");
        			String[] urlReplace = {
        					"https://www.scenemusic.net/demovibes/",
        					"http://www.scenemusic.net/demovibes/"
        			};
        			db.execSQL("UPDATE " + SITES_TABLE_NAME + " SET " + SITES_URL_KEY + " = ? WHERE " + SITES_URL_KEY + " = ?;",
        					urlReplace);
        			break;
        			
        		}
            }
        }
    }
}
