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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Date;


/** Retrieve data from a URL. */
class FetchUrl
{
    public static class Result
    {
        private String mResponse;
        private Date mTimestamp;

        public String getResponse() { return mResponse; }
        public Date getTimestamp() { return mTimestamp; }
    }

    public static Result get(URL url)
    {
        Result result = new Result();
        StringBuilder sb = new StringBuilder();
        boolean failed;

        try {
            InputStream inputStream = url.openStream();
            InputStreamReader instReader = new InputStreamReader(inputStream);
            BufferedReader reader = new BufferedReader(instReader, 8*1024);
            String line;

            result.mTimestamp = new Date();

            while((line = reader.readLine()) != null) {
                sb.append(line);
            }
            failed = false;
        } catch(IOException e) {
            failed = true;
        }

        if(failed == false) {
            result.mResponse = sb.toString();
        }

        return result;
    }
}
