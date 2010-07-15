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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

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
class Stream
{
    /** To appease the compiler, since passing around List<Stream> objects is UNSAFE!!!!1 */
    public static class List extends ArrayList<Stream> {};

    enum Type {
        UNKNOWN,
        MP3,
        OGG,
        AAC,
        SHOUTCAST,
    }

    private int mId;
    private URL mUrl;
    private String mName;
    private String mCountry;
    private int mBitrate;
    private Type mType;
    private String mTypeName;


    public Stream()
    {
        mName = new String("UNKNOWN");
        mCountry = new String("??");
        mType = Type.UNKNOWN;
    }


    public int getId() { return mId; }
    public URL getUrl() { return mUrl; }
    public String getName() { return mName; }
    public String getCountry() { return mCountry; }
    public int getBitrate() { return mBitrate; }
    public Type getType() { return mType; }
    public String getTypeName() { return mTypeName; }


    private static final String TAG = "NectroidStream";


    public static List listFromXml(String xmlData) throws SAXException
    {
        return (new Parser()).parse(xmlData);
    }


    /*
     * XML parsing stuff
     */

    public static class Parser
    {
        private Stream mNewStream;
        private List mAllStreams;

        public Parser()
        {
            mAllStreams = new List();
        }


        /** Parse value into an int, or return -1.
         *
         * @param value the string to parse as an integer
         * @param description a description of the string to print in an error message
         */
        public static int parseIntOrMinusOne(String value, String description, String tag)
        {
            int result = -1;
            if(value != null) {
                try {
                    result = Integer.parseInt(value);
                } catch(NumberFormatException e) {
                    Log.w(tag, String.format("Could not parse %s \"%s\" as integer", description,
                                value));
                }
            } else {
                Log.w(TAG, String.format("Missing %s", description));
            }
            return result;
        }

        public static int parseIntOrMinusOne(String value, String description)
        {
            return parseIntOrMinusOne(value, description, TAG);
        }


        private class StreamListener implements ElementListener
        {
            @Override
            public void start(Attributes attributes)
            {
                mNewStream = new Stream();
                String idString = attributes.getValue("id");
                if(idString != null) {
                    mNewStream.mId = parseIntOrMinusOne(idString, "stream id");
                }
            }

            @Override
            public void end()
            {
                mAllStreams.add(mNewStream);
            }
        }

        private class UrlListener implements EndTextElementListener
        {
            @Override
            public void end(String body)
            {
                try {
                    mNewStream.mUrl = new URL(body);
                } catch(MalformedURLException e) {
                    Log.w(TAG, String.format("Malformed stream URL \"%s\"", body));
                }
            }
        }

        private class NameListener implements EndTextElementListener
        {
            @Override
            public void end(String body)
            {
                mNewStream.mName = body;
            }
        }

        private class CountryListener implements EndTextElementListener
        {
            @Override
            public void end(String body)
            {
                mNewStream.mCountry = body;
            }
        }

        private class BitrateListener implements EndTextElementListener
        {
            @Override
            public void end(String body)
            {
                mNewStream.mBitrate = parseIntOrMinusOne(body, "stream bitrate");
            }
        }

        private class TypeListener implements TextElementListener
        {
            @Override
            public void start(Attributes attributes)
            {
                String typeCode = attributes.getValue("v");
                if(typeCode == null) {
                    Log.w(TAG, String.format("Stream %d missing type code", mNewStream.mId));
                    mNewStream.mType = Type.UNKNOWN;
                } else if(typeCode.equals("M")) {
                    mNewStream.mType = Type.MP3;
                } else if(typeCode.equals("O")) {
                    mNewStream.mType = Type.OGG;
                } else if(typeCode.equals("A")) {
                    mNewStream.mType = Type.AAC;
                } else if(typeCode.equals("S")) {
                    mNewStream.mType = Type.SHOUTCAST;
                } else {
                    Log.w(TAG, String.format("Unknown stream type code \"%s\"", typeCode));
                    mNewStream.mType = Type.UNKNOWN;
                }
            }

            @Override
            public void end(String body)
            {
                // No need to remember the type name for known types
                if(mNewStream.mType == Type.UNKNOWN) {
                    mNewStream.mTypeName = body;
                }
            }
        }


        public List parse(String xmlData) throws SAXException
        {
            RootElement root = new RootElement("streams");

            Element stream = root.getChild("stream");
            stream.setElementListener(new StreamListener());

            Element url = stream.getChild("url");
            url.setEndTextElementListener(new UrlListener());

            Element name = stream.getChild("name");
            name.setEndTextElementListener(new NameListener());

            Element country = stream.getChild("country");
            country.setEndTextElementListener(new CountryListener());

            Element type = stream.getChild("type");
            type.setTextElementListener(new TypeListener());

            Xml.parse(xmlData, root.getContentHandler());

            return mAllStreams;
        }
    }
}
