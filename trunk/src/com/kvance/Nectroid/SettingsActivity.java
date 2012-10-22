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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.preference.DialogPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.ListView;
import android.widget.Toast;


public class SettingsActivity extends PreferenceActivity
{
    private SitePreference mSitePreference;
    private SQLiteDatabase mDB;
    private int mSiteIdToDelete;

    private static final int PICK_STREAM_REQUEST = 0;
    private static final int NEW_SITE_REQUEST = 1;
    private static final int EDIT_SITE_REQUEST = 2;
    private static final String TAG = "Nectroid";


    ///
    /// Activity events
    ///

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        ((NectroidApplication)getApplication()).updateWindowBackground(getWindow());

        // Get a readable database handle.
        mDB = new DbOpenHelper(this).getReadableDatabase();

        // Use the correct prefs file
        getPreferenceManager().setSharedPreferencesName(Prefs.PREFS_NAME);

        addPreferencesFromResource(R.xml.settings);

        // Show the streams activity for the "select a stream" preference.
        Preference streamPreference = findPreference(Prefs.STREAM_URL_KEY);
        streamPreference.setOnPreferenceClickListener(onPickStreamClicked);

        // Wait until the "select a site" preference is clicked to register its context menu.
        mSitePreference = (SitePreference)findPreference(Prefs.SITE_ID_KEY);
        mSitePreference.setOnPreferenceClickListener(onPickSiteClicked);
        mSitePreference.setOnNewSiteClickListener(onNewSiteClicked);
        mSitePreference.setDb(mDB);
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();

        // Close up the database.
        mSitePreference.getCursor().close();
        mDB.close();
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        switch(requestCode) {
            case PICK_STREAM_REQUEST:
                if(resultCode == Activity.RESULT_OK) {
                    int id = data.getIntExtra(StreamsActivity.EXTRA_ID, -1);
                    onStreamPicked(data.getData(), id);
                }
                break;

            case NEW_SITE_REQUEST:
                if(resultCode == Activity.RESULT_OK) {
                    // Pick the site we just created.
                    int siteId = SiteActivity.parseSiteUri(data.getData());
                    Prefs.setSiteId(this, siteId);
                    mSitePreference.refresh();
                }
                break;

            case EDIT_SITE_REQUEST:
                if(resultCode == Activity.RESULT_OK) {
                	// If we edited the current site, force a reload by the site manager.
                    int siteId = SiteActivity.parseSiteUri(data.getData());
                    if(siteId == Prefs.getSiteId(this)) {
                    	((NectroidApplication)getApplication()).getSiteManager().refreshCurrentSite();
                    }
                    
                    // Refresh the site preference to show the changes.
                    mSitePreference.refresh();
                }
                break;

            default:
                Log.w(TAG, String.format("Unknown request code %d", requestCode));
                break;
        }
    }


    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo)
    {
        // Inflate the menu.
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.site_item_context, menu);

        // onContextItemSelected() seems to be broken if the menu item is in a dialog.  We use our
        // own OnMenuItemClickListeners instead.
        MenuItem editItem = menu.findItem(R.id.edit_item);
        editItem.setOnMenuItemClickListener(onSiteEditClicked);

        MenuItem deleteItem = menu.findItem(R.id.delete_item);
        deleteItem.setOnMenuItemClickListener(onSiteDeleteClicked);
    }


    ///
    /// Site context menu events
    ///

    private OnMenuItemClickListener onSiteEditClicked = new OnMenuItemClickListener() {
        public boolean onMenuItemClick(MenuItem item) {
            int siteId = getIdForSiteMenuItem(item);
            Uri siteUri = makeUriForSite(siteId);
            Intent intent = new Intent(Intent.ACTION_EDIT, siteUri);
            intent.setClass(SettingsActivity.this, SiteActivity.class);
            startActivityForResult(intent, EDIT_SITE_REQUEST);
            return true;
        }
    };


    private OnMenuItemClickListener onSiteDeleteClicked = new OnMenuItemClickListener() {
        public boolean onMenuItemClick(MenuItem item) {
            AlertDialog.Builder builder = new AlertDialog.Builder(SettingsActivity.this);
            DbDataHelper data = new DbDataHelper(mDB);
            if(data.getSiteCount() == 1) {
                // Don't let the user delete the last site.
                builder.setMessage(R.string.no_delete_last_site);
                builder.setPositiveButton(R.string.ok, onDialogCancel);

            } else {
                // Build confirmation message.
                mSiteIdToDelete = getIdForSiteMenuItem(item);
                Site site = data.getSite(mSiteIdToDelete);
                String name = site.getName();
                if(name == null || name.trim().length() == 0) {
                    name = getString(R.string.site);
                }
                String message = String.format(getString(R.string.confirm_delete), name);

                // Show confirmation dialog.
                builder.setMessage(message);
                builder.setPositiveButton(R.string.ok, onDeleteConfirmed);
                builder.setNegativeButton(R.string.cancel, onDialogCancel);
            }

            builder.create().show();
            return true;
        }
    };

            
    ///
    /// Other events
    ///

    /** The user confirmed the delete confirmation dialog. */
    private DialogInterface.OnClickListener onDeleteConfirmed =
        new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            SQLiteDatabase wdb = new DbOpenHelper(SettingsActivity.this).getWritableDatabase();
            try {
                // Execute the deletion.
                DbDataHelper wdata = new DbDataHelper(wdb);
                wdata.deleteSite(mSiteIdToDelete);

                // If we deleted the current site, select some other site.
                int currentSiteId = Prefs.getSiteId(SettingsActivity.this);
                if(mSiteIdToDelete == currentSiteId) {
                    pickAnySite();
                }
            } finally {
                wdb.close();
            }
            mSitePreference.refresh();
        }
    };


    /** Dialog cancel button was pressed. */
    private DialogInterface.OnClickListener onDialogCancel =
        new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            dialog.dismiss();
        }
    };


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


    /** Create a new site. */
    private DialogInterface.OnClickListener onNewSiteClicked =
        new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int which) {
            // Start up the settings activity.
            Intent intent = new Intent(Intent.ACTION_INSERT);
            intent.setClass(SettingsActivity.this, SiteActivity.class);
            startActivityForResult(intent, NEW_SITE_REQUEST);
        }
    };


    ///
    /// Utility methods
    ///


    /** Make some changes to the "select a site" dialog before it's displayed. */
    private Preference.OnPreferenceClickListener onPickSiteClicked =
        new Preference.OnPreferenceClickListener() {
        public boolean onPreferenceClick(Preference preference) {
            // Show the sites context menu for the "select a site" preference.
            AlertDialog dialog = getSiteDialog();
            ListView siteListView = dialog.getListView();
            registerForContextMenu(siteListView);
            return false;
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
            NectroidApplication app = (NectroidApplication)getApplication();
            app.onUserPickedStream(streamUrl, id);
        }
    }


    /** Return the "select a site" dialog. */
    private AlertDialog getSiteDialog()
    {
        DialogPreference sitePref = (DialogPreference)findPreference(Prefs.SITE_ID_KEY);
        return (AlertDialog)sitePref.getDialog();
    }


    /** Get the site ID this site context menu is for. */
    private int getIdForSiteMenuItem(MenuItem item)
    {
        AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo)item.getMenuInfo();
        int id = mSitePreference.getIdAtPosition(menuInfo.position);
        return id;
    }

    /** Make a nectroid:// site URI for the site with this ID. */
    private Uri makeUriForSite(int siteId)
    {
        return Uri.parse("nectroid://sites/" + String.valueOf(siteId));
    }


    /** Pick any valid site ID as the new current site. */
    private void pickAnySite()
    {
        // Find any site ID.
        int newId;
        DbDataHelper data = new DbDataHelper(mDB);
        Cursor cursor = data.selectAllSites();
        try {
            int idColumn = cursor.getColumnIndexOrThrow(DbOpenHelper.SITES_ID_KEY);
            cursor.moveToFirst();
            newId = cursor.getInt(idColumn);
        } finally {
            cursor.close();
        }

        // Pick that site.
        Prefs.setSiteId(SettingsActivity.this, newId);
    }

    private void updateBackground()
    {
    }
}
