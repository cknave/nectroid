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

import android.app.Activity;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.view.View;


public class SiteActivity extends Activity
{
    private Site mSite;
    private TextView mNameView;
    private TextView mUrlView;
    private TextView mColorView;

    private static final String DEFAULT_NAME = "Example Radio";
    private static final String DEFAULT_URL = "http://example.com/demovibes/";
    private static final String DEFAULT_COLOR = "#44cc88";


    ///
    /// Activity event handlers
    ///

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.site);

        ((NectroidApplication)getApplication()).updateWindowBackground(getWindow());

        // Create a new site object, or load an existing one depending on how we were started.
        Intent intent = getIntent();
        String action = intent.getAction();
        if(action.equals(Intent.ACTION_INSERT)) {
            mSite = new Site(null, DEFAULT_NAME, DEFAULT_URL, DEFAULT_COLOR);
            setTitle(R.string.new_site);
        } else if(action.equals(Intent.ACTION_EDIT)) {
            mSite = getSiteForUri(intent.getData());
            String title = getString(R.string.edit_site) + " - " + mSite.getName();
            setTitle(title);
        } else {
            throw new RuntimeException("Unknown action " + action);
        }

        // Get widget references
        mNameView = (TextView)findViewById(R.id.site_name);
        mUrlView = (TextView)findViewById(R.id.site_base_url);
        mColorView = (TextView)findViewById(R.id.site_color);

        // Fill text fields with our site values.
        mNameView.setText(mSite.getName());
        mUrlView.setText(mSite.getBaseUrl());
        String hexColor = String.format("#%06X", mSite.getColor() & 0xFFFFFF);
        mColorView.setText(hexColor);

        // Link buttons to events.
        Button okButton = (Button)findViewById(R.id.site_ok);
        okButton.setOnClickListener(onOkClicked);
        Button cancelButton = (Button)findViewById(R.id.site_cancel);
        cancelButton.setOnClickListener(onCancelClicked);
    }


    ///
    /// Button events
    ///

    private OnClickListener onOkClicked = new OnClickListener() {
        public void onClick(View v) {
            // Copy the results back to the site object.
            mSite.setName(mNameView.getText().toString());
            mSite.setBaseUrl(mUrlView.getText().toString());
            mSite.setColor(mColorView.getText().toString());

            // Save the changes.
            SQLiteDatabase db = new DbOpenHelper(SiteActivity.this).getWritableDatabase();
            int siteId;
            try {
                DbDataHelper data = new DbDataHelper(db);
                if(getIntent().getAction().equals(Intent.ACTION_INSERT)) {
                    // Create a new site.
                    siteId = data.insertSite(mSite);
                } else {
                    // Update an existing site.
                    data.updateSite(mSite);
                    siteId = mSite.getId();
                }
            } finally {
                db.close();
            }

            // Finish the activity, returning the new site id.
            Intent result = new Intent();
            String siteUriString = "nectroid://sites/" + String.valueOf(siteId);
            Uri siteUri = Uri.parse(siteUriString);
            result.setData(siteUri);
            setResult(Activity.RESULT_OK, result);
            finish();
        }
    };


    private OnClickListener onCancelClicked = new OnClickListener() {
        public void onClick(View v) {
            setResult(Activity.RESULT_CANCELED);
            finish();
        }
    };


    ///
    /// Utility methods
    ///


    /** Parse a nectroid://sites/n URI, returning the n part. */
    public static int parseSiteUri(Uri siteUri)
    {
        // Validate the URI.
        if(siteUri == null) {
            throw new RuntimeException("Started SiteActivity with null data URI");
        }
        if(!siteUri.getScheme().equals("nectroid")) {
            throw new RuntimeException(String.format("Unknown URI scheme \"%s\" in %s",
                        siteUri.getScheme(), siteUri));
        }
        final String SITES = "//sites/";
        String location = siteUri.getSchemeSpecificPart();
        if(!location.startsWith(SITES)) {
            throw new RuntimeException(String.format("Excepting a %s URI in %s", SITES, siteUri));
        }

        // Get the site ID from the URI.
        String idString = location.substring(SITES.length(), location.length());
        int siteId = Integer.valueOf(idString);
        return siteId;
    }


    private Site getSiteForUri(Uri siteUri)
    {
        int siteId = parseSiteUri(siteUri);

        // Fetch that site.
        Site site = null;
        SQLiteDatabase db = new DbOpenHelper(this).getReadableDatabase();
        try {
            DbDataHelper data = new DbDataHelper(db);
            site = data.getSite(siteId);
        } finally {
            db.close();
        }
        return site;
    }
}
