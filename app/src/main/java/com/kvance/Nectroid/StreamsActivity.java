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

import android.app.Activity;
import android.app.ListActivity;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;


public class StreamsActivity extends ListActivity implements BackgroundTaskListener
{
    private StreamsManager mStreamsManager;
    private List<Stream> mStreams;
    private StreamsAdapter mListAdapter;
    private TextView mEmptyTextView;

    private static final String TAG = "Nectroid";

    public static final String EXTRA_ID = "com.kvance.Nectroid.Stream.id";
    public static final String EXTRA_BITRATE = "com.kvance.Nectroid.Stream.bitrate";


    ///
    /// Activity event handlers
    ///

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.inset_list);

        ((NectroidApplication)getApplication()).updateWindowBackground(getWindow());

        getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        // Set the empty text
        mEmptyTextView = (TextView)findViewById(android.R.id.empty);
        mEmptyTextView.setText(R.string.no_streams);
    }

    @Override
    public void onStart()
    {
        super.onStart();

        // Subscribe to streams events.
        NectroidApplication app = (NectroidApplication)getApplication();
        mStreamsManager = app.getStreamsManager();
        mStreamsManager.addTaskListener(this);

        // Make sure we get a streams list now or in the near future.
        SQLiteDatabase db = new DbOpenHelper(this).getReadableDatabase();
        try {
            mStreams = Stream.listFromDB(db, Prefs.getSiteId(this));
        } finally {
            db.close();
        }
        if(mStreams == null || mStreams.size() == 0) {
            mEmptyTextView.setText(R.string.loading_streams);
            mStreamsManager.update(this, true);
        }

        // Set up the list GUI.
        mListAdapter = new StreamsAdapter(mStreams, this);
        setListAdapter(mListAdapter);

        // Update the throbber to the current state.  (If we do this before we set the title, it
        // seems to be ignored.)
        setProgressBarIndeterminateVisibility(mStreamsManager.isUpdating());
    }


    @Override
    protected void onListItemClick(ListView l, View v, int position, long id)
    {
        // Activities return URIs, not URLs.
        Stream stream = mStreams.get(position);
        Uri streamUri = Uri.parse(stream.getUrl().toString());
        
        Intent result = new Intent();
        result.setData(streamUri);
        result.putExtra(EXTRA_ID, (int)id);
        result.putExtra(EXTRA_BITRATE, stream.getBitrate());
        setResult(Activity.RESULT_OK, result);
        finish();
    }


    /** Initialize the contents of the Activity's options menu. */
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.streams_options, menu);
        return true;
    }


    /** Called whenever an item in the Activity's options menu is selected. */
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch(item.getItemId()) {
            case R.id.refresh_item:
                // Refresh the streams.
                mStreamsManager.cancelUpdate();
                mStreamsManager.update(this, false);
                return true;

            default:
                return false;
        }
    }


    ///
    /// Streams update event handlers
    ///

    public void onTaskStarted(Object manager)
    {
        setProgressBarIndeterminateVisibility(true);
    }

    public void onTaskFinished(Object manager, Object result)
    {
        mStreams = (Stream.List)result;
        mListAdapter.setStreams(mStreams);
        setProgressBarIndeterminateVisibility(false);
        mEmptyTextView.setText(R.string.no_streams);
    }

    public void onTaskCancelled(Object manager)
    {
        setProgressBarIndeterminateVisibility(false);
        mEmptyTextView.setText(R.string.no_streams);
    }

    public void onTaskFailed(Object manager)
    {
        setProgressBarIndeterminateVisibility(false);
        Toast errorToast = Toast.makeText(this, R.string.streams_failed, Toast.LENGTH_SHORT);
        errorToast.show();
        mEmptyTextView.setText(R.string.no_streams);
    }
}
