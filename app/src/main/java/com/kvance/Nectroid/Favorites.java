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
import android.sax.Element;
import android.sax.EndElementListener;
import android.sax.EndTextElementListener;
import android.sax.RootElement;
import android.sax.StartElementListener;
import android.sax.TextElementListener;
import android.util.Log;
import android.util.Xml;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;


/** A playlist has a current song, a history, and an upcoming list. */
class Favorites
{
    /** A single song in a playlist */
    public class Entry {
        protected int mId = 0;
        protected String mTitle = new String("UNKNOWN");
        protected Boolean mLocked = false;

        public int getId() { return mId; }
        public String getTitle() { return mTitle; }
        public boolean isLocked() { return mLocked; }

        @Override
        public String toString() {
            return String.format(
                        "<Entry title=\"%s\" id=%d locked=\"%s\">",
                        mTitle, mId, mLocked.toString());
        }
    }

    List<Entry> mFavorites;

    private static final String TAG = "NectroidPlaylist";


    public Favorites() {
        mFavorites = new ArrayList<Entry>();
    }

    public Favorites(String xmlData) throws SAXException {
        this();
        new Parser().parse(xmlData);
    }

    ///
    /// Getters
    ///

    public List<Entry> getFavorites() { return mFavorites; }

    ///
    /// XML parser
    ///

    private class Parser {

        /** Parse value into an int, or return -1.
         *
         * @param value the string to parse as an integer
         * @param description a description of the string to print in an error message
         */
        private int parseIntOrMinusOne(String value, String description)
        {
            // Same code in Stream XML parser
            return Stream.Parser.parseIntOrMinusOne(value, description, TAG);
        }

        private class SongTextListener implements TextElementListener
        {
            private int mId = -1;
            private Boolean mLocked = false;

            public void start(Attributes attributes) {

                String idString = attributes.getValue("id");
                mId = parseIntOrMinusOne(idString, "song id");

                String locked = attributes.getValue("locked");
                mLocked = "True".equals(locked);
            }

            public void end(String body) {
                Entry mNewEntry = new Entry();
                mNewEntry.mId = mId;
                mNewEntry.mLocked = mLocked;
                mNewEntry.mTitle = body;
                if (!mNewEntry.mLocked)
                    mFavorites.add(mNewEntry);
            }
        }

        public void parse(String xmlData) throws SAXException
        {
            RootElement root = new RootElement("favorites");

            Element song = root.getChild("song");
            SongTextListener songTextListener = new SongTextListener();
            song.setTextElementListener(songTextListener);

            Xml.parse(xmlData, root.getContentHandler());
        }
    }
}
