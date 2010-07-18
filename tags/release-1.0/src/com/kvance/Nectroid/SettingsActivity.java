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

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.util.Log;
import android.widget.Toast;


public class SettingsActivity extends PreferenceActivity
{
    private static final int PICK_STREAM_REQUEST = 0;
    private static final String TAG = "Nectroid";


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        // Use the correct prefs file
        getPreferenceManager().setSharedPreferencesName(Prefs.PREFS_NAME);

        addPreferencesFromResource(R.xml.settings);

        // Show the streams activity for the "select a stream" preference.
        Preference streamPref = (Preference)findPreference(Prefs.STREAM_URL_KEY);
        streamPref.setOnPreferenceClickListener(onPickStreamClicked);
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        switch(requestCode) {
            case PICK_STREAM_REQUEST:
                if(resultCode == android.app.Activity.RESULT_OK) {
                    int id = data.getIntExtra(StreamsActivity.EXTRA_ID, -1);
                    onStreamPicked(data.getData(), id);
                }
                break;

            default:
                Log.w(TAG, String.format("Unknown request code %d", requestCode));
                break;
        }
    }


    /** Start the stream picker activity. */
    private Preference.OnPreferenceClickListener onPickStreamClicked =
        new Preference.OnPreferenceClickListener() {
        public boolean onPreferenceClick(Preference preference) {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setClass(SettingsActivity.this, StreamsActivity.class);
            startActivityForResult(intent, PICK_STREAM_REQUEST);
            return true;
        }
    };


    /** The user selected a new default stream. */
    private void onStreamPicked(Uri streamUri, int id)
    {
        // Activities return URIs, but we need a URL.
        URL streamUrl = null;
        try {
            streamUrl = new URL(streamUri.toString());
        } catch(MalformedURLException e) {
            Toast.makeText(this, R.string.invalid_stream, Toast.LENGTH_SHORT).show();
        }

        if(streamUrl != null) {
            Prefs.setStreamUrlAndId(streamUrl, id, this);
        }
    }
}
