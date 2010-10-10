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

import android.graphics.Color;
import android.util.Log;


/** Simple data structure for site info. */
class Site
{
    private Integer mId;
    private String mName;
    private String mBaseUrl;
    private Integer mColor;

    private static final String TAG = "Nectroid";


    public Site(Integer id, String name, String baseUrl, Integer colorInt)
    {
        mId = id;
        mName = name;
        mBaseUrl = baseUrl;
        mColor = colorInt;
    }

    public Site(Integer id, String name, String baseUrl, String colorString)
    {
        this(id, name, baseUrl, (Integer)null);
        setColor(colorString);
    }

    
    ///
    /// Getters
    ///

    public Integer getId() { return mId; }
    public String getName() { return mName; }
    public String getBaseUrl() { return mBaseUrl; }
    public Integer getColor() { return mColor; }


    ///
    /// Setters
    ///

    public void setId(Integer id) { mId = id; }
    public void setName(String name) { mName = name; }
    public void setBaseUrl(String baseUrl) { mBaseUrl = baseUrl; }
    public void setColor(Integer colorInt) { mColor = colorInt; }

    public void setColor(String colorString)
    {
        // Null or empty colorString is a null color.
        if(colorString == null || colorString.length() == 0) {
            mColor = null;
            return;
        }

        // Parse the color string.
        Integer colorInt = null;
        try {
            colorInt = Color.parseColor(colorString);
        } catch(IllegalArgumentException e) {
            // Handled below
        } catch(StringIndexOutOfBoundsException e) {
            // Handled below
        }
        setColor(colorInt);

        // Error warning.
        if(colorInt == null) {
            Log.w(TAG, String.format("Unable to parse color \"%s\"", colorString));
        }
    }
}
