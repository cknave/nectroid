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

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.Context;


class DbOpenHelper extends SQLiteOpenHelper
{
    private static final int DB_VERSION = 1;
    private static final String DB_NAME = "nectroid";

    // A site (e.g. nectarine, cvgm.net)
    private static final String SITES_TABLE_NAME = "sites";
    private static final String SITES_ID_KEY = "id";
    private static final String SITES_NAME_KEY = "name";
    private static final String SITES_URL_KEY = "base_url";
    private static final String SITES_TABLE_CREATE = "CREATE TABLE " + SITES_TABLE_NAME + " (" +
        SITES_ID_KEY + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
        SITES_NAME_KEY + " TEXT, " +
        SITES_URL_KEY + " TEXT);";


    // Streams for a site
    private static final String STREAMS_TABLE_NAME = "streams";
    private static final String STREAMS_ID_KEY = "id";
    private static final String STREAMS_SITE_KEY = "site_id"; // References a "sites" id
    private static final String STREAMS_REMOTE_ID_KEY = "remote_id";
    private static final String STREAMS_URL_KEY = "url";
    private static final String STREAMS_NAME_KEY = "name";
    private static final String STREAMS_COUNTRY_KEY = "country";
    private static final String STREAMS_BITRATE_KEY = "bitrate";
    private static final String STREAMS_TYPE_CODE_KEY = "type_code";
    private static final String STREAMS_TYPE_NAME_KEY = "type_name";
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



    DbOpenHelper(Context context)
    {
        super(context, DB_NAME, null, DB_VERSION);
    }


    @Override
    public void onCreate(SQLiteDatabase db)
    {
        db.execSQL(SITES_TABLE_CREATE);
        db.execSQL(STREAMS_TABLE_CREATE);
    }


    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
    {
        throw new RuntimeException("Requested to upgrade a DB with no other version");
    }
}
