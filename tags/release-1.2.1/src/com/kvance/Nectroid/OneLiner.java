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

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import android.sax.Element;
import android.sax.ElementListener;
import android.sax.EndTextElementListener;
import android.sax.RootElement;
import android.sax.TextElementListener;
import android.util.Log;
import android.util.Xml;


/** A stream descriptor */
class OneLiner
{
    public static class List extends ArrayList<OneLiner>
    {
        private Date mTimestamp;

        public Date getTimestamp() { return mTimestamp; }
        public void setTimestamp(Date timestamp) { mTimestamp = timestamp; }
    }

    private Date mTime;
    private String mAuthor;
    private String mFlag;
    private String mMessage;

    private static final String TAG = "NectroidOneLiner";


    public OneLiner()
    {
        mTime = new Date();
        mAuthor = "UNKNOWN";
        mFlag = "??";
        mMessage = "";
    }


    public static OneLiner.List listFromXml(String xmlData) throws SAXException
    {
        return (new Parser()).parse(xmlData);
    }


    ///
    /// Getters
    ///

    public Date getTime() { return mTime; }
    public String getAuthor() { return mAuthor; }
    public String getFlag() { return mFlag; }
    public String getMessage() { return mMessage; }



    ///
    /// XML parser
    ///

    private static class Parser
    {
        private OneLiner mNewOneLiner;
        private OneLiner.List mAllOneLiners;
        private SimpleDateFormat dateFormat;

        public Parser()
        {
            mAllOneLiners = new OneLiner.List();
            dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        }


        private class EntryListener implements ElementListener
        {
            @Override
            public void start(Attributes attributes)
            {
                mNewOneLiner = new OneLiner();
                String timeString = attributes.getValue("time");
                if(timeString != null) {
                    try {
                        mNewOneLiner.mTime = dateFormat.parse(timeString);
                    } catch(ParseException e) {
                        Log.w(TAG, String.format("Unable to parse time \"%s\"", timeString));
                    }
                }
            }

            @Override
            public void end()
            {
                mAllOneLiners.add(mNewOneLiner);
            }
        }

        private class AuthorListener implements TextElementListener
        {
            @Override
            public void start(Attributes attributes)
            {
                String flagString = attributes.getValue("flag");
                if(flagString != null) {
                    mNewOneLiner.mFlag = flagString;
                }
            }

            @Override
            public void end(String body)
            {
                mNewOneLiner.mAuthor = body;
            }
        }

        private class MessageListener implements EndTextElementListener
        {
            @Override
            public void end(String body)
            {
                mNewOneLiner.mMessage = body;
            }
        }


        public OneLiner.List parse(String xmlData) throws SAXException
        {
            RootElement root = new RootElement("oneliner");

            Element entry = root.getChild("entry");
            entry.setElementListener(new EntryListener());

            Element author = entry.getChild("author");
            author.setTextElementListener(new AuthorListener());

            Element message = entry.getChild("message");
            message.setEndTextElementListener(new MessageListener());

            Xml.parse(xmlData, root.getContentHandler());

            return mAllOneLiners;
        }
    }
}
