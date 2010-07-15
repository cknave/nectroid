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
            Log.d(TAG, "Parsed OK");
            return mStreams;
        } catch(SAXException e) {
            Log.w(TAG, "Failed to parse streams.", e);
            return null;
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
}
