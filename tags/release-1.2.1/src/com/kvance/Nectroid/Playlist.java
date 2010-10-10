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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import android.content.Context;
import android.sax.Element;
import android.sax.EndElementListener;
import android.sax.EndTextElementListener;
import android.sax.RootElement;
import android.sax.StartElementListener;
import android.sax.TextElementListener;
import android.util.Log;
import android.util.Xml;


/** A playlist has a current song, a history, and an upcoming list. */
class Playlist
{
    /** A single song in a playlist */
    public class Entry
    {
        private int mId;
        private int mLength; // in seconds
        private String mTitle;
        private List<IdString> mArtists;
        private IdString mRequester;
        private Date mRequestTime;

        public Entry()
        {
            mTitle = new String("UNKNOWN");
            mArtists = new ArrayList<IdString>();
            mRequester = new IdString(0, "UNKNOWN");
            mRequestTime = new Date(0);
        }

        public String songLink(Context ctx)
        {
            String fmt = ctx.getString(R.string.url_fmt_song);
            String idString = String.valueOf(mId);
            return String.format(fmt, idString);
        }

        public String artistLink(IdString artist, Context ctx)
        {
            String fmt = ctx.getString(R.string.url_fmt_artist);
            String idString = String.valueOf(artist.mId);
            return String.format(fmt, idString);
        }

        public String requesterLink(Context ctx)
        {
            String fmt = ctx.getString(R.string.url_fmt_user);
            return String.format(fmt, mRequester.mString);
        }

        public int getId() { return mId; }
        public int getLength() { return mLength; }
        public String getTitle() { return mTitle; }
        public List<IdString> getArtists() { return mArtists; }
        public IdString getRequester() { return mRequester; }
        public Date getRequestTime() { return mRequestTime; }

        @Override
        public String toString()
        {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            StringBuilder sb = new StringBuilder();
            sb.append(String.format(
                        "<Entry title=\"%s\" id=%d length=%d requester=(%d, \"%s\") " +
                        "requestTime=\"%s\" artists=[",
                        mTitle, mId, mLength, mRequester.mId, mRequester.mString,
                        dateFormat.format(mRequestTime)));
            for(int i = 0; i < mArtists.size(); i++) {
                IdString artist = mArtists.get(i);
                sb.append(String.format("(%d, \"%s\")", artist.mId, artist.mString));
                if(i < (mArtists.size() - 1)) {
                    sb.append(", ");
                }
            }
            sb.append("]>");
            return sb.toString();
        }

        /** Return the name of the artist.
         *
         * If more than one artist was credited, "et al." is added.
         */
        public String getArtistString()
        {
            int numArtists = mArtists.size();
            if(numArtists == 0) {
                return "UNKNOWN";
            } else {
                String artist = mArtists.get(0).getString();
                if(numArtists == 1) {
                    return artist;
                } else {
                    return artist + ", et al.";
                }
            }
        }
    }

    public class IdString
    {
        private int mId;
        private String mString;

        public IdString(int id, String string)
        {
            mId = id;
            mString = string;
        }

        public int getId() { return mId; }
        public String getString() { return mString; }
    }

    /** A playlist entry, and the time left on that entry */
    public class EntryAndTimeLeft
    {
        private Entry entry;
        private int timeLeft; // in seconds

        public EntryAndTimeLeft(Entry entry, int timeLeft)
        {
            this.entry = entry;
            this.timeLeft = timeLeft;
        }

        public Entry getEntry() { return entry; }
        public int getTimeLeft() { return timeLeft; }
    };


    private Date mTimeBase; // when this playlist was retrieved
    private Entry mCurrentEntry; // the current entry, as of mTimeBase
    private int mTimeLeft; // time left in the current song, in seconds

    List<Entry> mQueue;
    List<Entry> mHistory;

    private static final String TAG = "NectroidPlaylist";


    public Playlist(Date timeBase)
    {
        mTimeBase = timeBase;
        mCurrentEntry = new Entry();
        mTimeLeft = 0;
        mQueue = new ArrayList<Entry>();
        mHistory = new ArrayList<Entry>();
    }

    public Playlist(String xmlData, Date timeBase) throws SAXException
    {
        this(timeBase);
        new Parser().parse(xmlData);
    }


    ///
    /// Public interface
    ///

    /** Return the total length of the queue, in seconds, including the currently playing song. */
    public int lengthInSeconds()
    {
        int result = mTimeLeft;
        for(Entry entry : mQueue) {
            if(entry.mLength > 0) {
                result += entry.mLength;
            }
        }
        return result;
    }


    /** Return the entry for the song at this time
     * @param time the time in seconds relative to the start of this playlist
     */
    public EntryAndTimeLeft atTime(int time)
    {
        EntryAndTimeLeft result = null;

        if(time < mTimeLeft) {
            // Still on the current song.
            result = new EntryAndTimeLeft(mCurrentEntry, mTimeLeft - time);
        } else {
            // Past the current song, walk the queue til we find it.
            time -= mTimeLeft;
            for(Entry entry : mQueue) {
                if(time < entry.mLength) {
                    result = new EntryAndTimeLeft(entry, entry.mLength - time);
                    break;
                }
                time -= entry.mLength;
            }
        }

        return result;
    }
    
    /** Return the entry for the song at the current system time. */
    public EntryAndTimeLeft atNow()
    {
        long timeInMillis = System.currentTimeMillis() - mTimeBase.getTime();
        int timeInSeconds = (int)(timeInMillis / 1000L);
        return atTime(timeInSeconds);
    }


    ///
    /// Getters
    ///

    public Date getTimeBase() { return mTimeBase; }
    public Entry getCurrentEntry() { return mCurrentEntry; }
    public int getTimeLeft() { return mTimeLeft; }
    public List<Entry> getQueue() { return mQueue; }
    public List<Entry> getHistory() { return mHistory; }


    ///
    /// XML parser
    ///

    private class Parser
    {
        private SimpleDateFormat dateFormat;

        private Entry mNewEntry;


        public Parser()
        {
            dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        }


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


        private class EntryStartListener implements StartElementListener
        {
            public void start(Attributes attributes)
            {
                mNewEntry = new Entry();
                String dateString = attributes.getValue("request_time");
                if(dateString != null) {
                    try {
                        mNewEntry.mRequestTime = dateFormat.parse(dateString);
                    } catch(ParseException e) {
                        Log.w(TAG, String.format("Cannot parse request_time=\"%s\"", dateString));
                    }
                }
            }
        }

        private class TimeLeftListener implements EndTextElementListener
        {
            public void end(String body)
            {
                mTimeLeft = parseIntOrMinusOne(body, "time left");
            }
        }

        private class ArtistTextListener implements TextElementListener
        {
            private int mId = -1;

            public void start(Attributes attributes)
            {
                String idString = attributes.getValue("id");
                mId = parseIntOrMinusOne(idString, "artist id");
            }

            public void end(String body)
            {
                IdString artist = new IdString(mId, body);
                mNewEntry.mArtists.add(artist);
            }
        }

        private class RequesterTextListener implements TextElementListener
        {
            private int mId = -1;

            public void start(Attributes attributes)
            {
                String idString = attributes.getValue("id");
                mId = parseIntOrMinusOne(idString, "requester id");
            }

            public void end(String body)
            {
                mNewEntry.mRequester = new IdString(mId, body);
            }
        }

        private class SongTextListener implements TextElementListener
        {
            private int mId = -1;
            private int mLength = -1;

            public void start(Attributes attributes)
            {
                String idString = attributes.getValue("id");
                mId = parseIntOrMinusOne(idString, "song id");
                String lengthString = attributes.getValue("length");
                mLength = parseSongLengthOrMinusOne(lengthString);
            }

            public void end(String body)
            {
                mNewEntry.mId = mId;
                mNewEntry.mLength = mLength;
                mNewEntry.mTitle = body;
            }

            private int parseSongLengthOrMinusOne(String value)
            {
                int result = -1;
                if(value != null) {
                    String[] minAndSec = value.split(":", 2);
                    if(minAndSec.length == 2) {
                        int minutes = parseIntOrMinusOne(minAndSec[0], "song minutes");
                        int seconds = parseIntOrMinusOne(minAndSec[1], "song secands");
                        if(minutes > -1 && seconds > -1) {
                            result = 60*minutes + seconds;
                        } else {
                            Log.w(TAG, String.format("Invalid song length \"%s\"", value));
                        }
                    } else {
                        Log.w(TAG, String.format("Song length \"%s\" missing colon", value));
                    }
                } else {
                    Log.w(TAG, "Missing song length");
                }
                return result;
            }
        }


        public void parse(String xmlData) throws SAXException
        {
            RootElement root = new RootElement("playlist");

            Element now = root.getChild("now");
            Element timeleft = now.getChild("timeleft");
            timeleft.setEndTextElementListener(new TimeLeftListener());

            // Deal with entries from all 3 kinds of parents: now, queue, history.
            Element nowEntry = now.getChild("entry");
            EntryStartListener entryStartListener = new EntryStartListener();
            nowEntry.setStartElementListener(entryStartListener);
            nowEntry.setEndElementListener(new EndElementListener() {
                public void end() {
                    mCurrentEntry = mNewEntry;
                }
            });

            Element queue = root.getChild("queue");
            Element queueEntry = queue.getChild("entry");
            queueEntry.setStartElementListener(entryStartListener);
            queueEntry.setEndElementListener(new EndElementListener() {
                public void end() {
                    mQueue.add(mNewEntry);
                }
            });

            Element history = root.getChild("history");
            Element historyEntry = history.getChild("entry");
            historyEntry.setStartElementListener(entryStartListener);
            historyEntry.setEndElementListener(new EndElementListener() {
                public void end() {
                    mHistory.add(mNewEntry);
                }
            });

            // Apply <entry> sub-element listeners to all 3 kinds of entries.
            ArtistTextListener artistTextListener = new ArtistTextListener();
            SongTextListener songTextListener = new SongTextListener();
            RequesterTextListener requesterTextListener = new RequesterTextListener();
            for(Element parent : new Element[] {nowEntry, queueEntry, historyEntry}) {
                Element artist = parent.getChild("artist");
                artist.setTextElementListener(artistTextListener);

                Element song = parent.getChild("song");
                song.setTextElementListener(songTextListener);

                Element requester = parent.getChild("requester");
                requester.setTextElementListener(requesterTextListener);
            }

            Xml.parse(xmlData, root.getContentHandler());
        }
    }
}
