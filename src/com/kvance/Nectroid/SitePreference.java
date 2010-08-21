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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CursorAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;


public class SitePreference extends DialogPreference
{
    private Context mContext;
    private SQLiteDatabase mDB;
    private Cursor mCursor;
    private DialogInterface.OnClickListener mOnNewSiteClickListener;
    private int mIdColumnIdx;
    private int mClickedDialogEntryIndex;


    public SitePreference(Context context, AttributeSet attrs, int defStyle)
    {
        super(context, attrs, defStyle);
        init(context);
    }


    public SitePreference(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        init(context);
    }


    ///
    /// DialogPreference methods
    ///

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder)
    {
        super.onPrepareDialogBuilder(builder);

        // Show a list of site names.
        int position = getPositionOfSelectedSite();
        builder.setSingleChoiceItems(mCursor, position, DbOpenHelper.SITES_NAME_KEY,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // Dismiss the dialog, simulating a positive button click.
                        mClickedDialogEntryIndex = which;
                        SitePreference.this.onClick(dialog, DialogInterface.BUTTON_POSITIVE);
                        dialog.dismiss();
                    }
        });

        // Add the "New site" button.
        builder.setNeutralButton(R.string.new_site, mOnNewSiteClickListener);

        // Hide the "OK" button.
        builder.setPositiveButton(null, null);
    }


    // This (and the above OnClickListener) are based on ListPreference.
    @Override
    protected void onDialogClosed(boolean positiveResult)
    {
        super.onDialogClosed(positiveResult);
        if(positiveResult && mClickedDialogEntryIndex >= 0) {
            int id = getIdAtPosition(mClickedDialogEntryIndex);
            if(callChangeListener(new Integer(id))) {
                persistInt(id);
            }
        }
    }


    ///
    /// Public interface
    ///

    /** Return the site ID at this position in the list. */
    public int getIdAtPosition(int position)
    {
        mCursor.moveToPosition(position);
        int id = mCursor.getInt(mIdColumnIdx);
        return id;
    }


    /** Return the list position of the site with this ID.
     *
     * If no match is found, return -1.
     */
    public int getPositionOfId(int siteId)
    {
        int result = -1;

        // Iterate over every row until we find it.
        int numItems = mCursor.getCount();
        int position;
        for(position = 0; position < numItems; position++) {
            mCursor.moveToPosition(position);
            int id = mCursor.getInt(mIdColumnIdx);
            if(id == siteId) {
                result = position;
                break;
            }
        }

        return result;
    }


    /** Return the list position of the selected site.
     *
     * If no site is selected, return -1.
     */
    public int getPositionOfSelectedSite()
    {
        int position = -1;
        int selectedId = getPersistedInt(-1);
        if(selectedId != -1) {
            position = getPositionOfId(selectedId);
        }
        return position;
    }



    /** Refresh the list of sites. */
    public void refresh()
    {
        // Update the cursor.
        mCursor.requery();

        // Redraw the dialog if it's currently on the screen.
        AlertDialog dialog = (AlertDialog)getDialog();
        if(dialog != null) {
            ListView listView = dialog.getListView();
            if(listView != null) {
                // Notify new data.
                BaseAdapter adapter = (BaseAdapter)dialog.getListView().getAdapter();
                if(adapter != null) {
                    adapter.notifyDataSetChanged();
                }

                // Update the selection.
                int position = getPositionOfSelectedSite();
                listView.setItemChecked(position, true);
            }
        }
    }


    ///
    /// Getters
    ///

    public Cursor getCursor()
    {
        return mCursor;
    }

    public DialogInterface.OnClickListener getOnNewSiteClickListener()
    {
        return mOnNewSiteClickListener;
    }


    ///
    /// Setters
    ///

    public void setDb(SQLiteDatabase db)
    {
        // Close up any old database.
        if(mCursor != null) {
            mCursor.close();
        }
        if(mDB != null) {
            mDB.close();
        }

        mDB = db;
        DbDataHelper data = new DbDataHelper(mDB);
        mCursor = data.selectAllSites();
        mIdColumnIdx = mCursor.getColumnIndexOrThrow(DbOpenHelper.SITES_ID_KEY);
    }

    public void setOnNewSiteClickListener(DialogInterface.OnClickListener listener)
    {
        mOnNewSiteClickListener = listener;
    }


    ///
    /// Utility methods
    ///

    private void init(Context context)
    {
        mContext = context;
    }
}
