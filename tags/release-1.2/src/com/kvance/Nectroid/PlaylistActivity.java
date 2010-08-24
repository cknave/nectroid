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

import android.app.ListActivity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;


public class PlaylistActivity extends ListActivity
    implements BackgroundTaskListener, PlaylistManager.SongListener
{
    /** Which playlist to display */
    enum WhichPlaylist {
        HISTORY,
        QUEUE
    };

    private WhichPlaylist mDisplaying;
    private PlaylistManager mPlaylistManager;

    private Playlist mPlaylist;
    private PlaylistAdapter mListAdapter;

    private static final String TAG = "Nectroid";


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

        // Check for a data URI
        Uri dataUri = getIntent().getData();
        if(dataUri == null) {
            throw new RuntimeException("Started PlaylistActivity with null data URI");
        }
        if(!dataUri.getScheme().equals("nectroid")) {
            throw new RuntimeException(String.format("Unknown URI scheme \"%s\" in %s",
                        dataUri.getScheme(), dataUri));
        }

        // Check which playlist we're supposed to display
        String playlistName = dataUri.getSchemeSpecificPart();
        if(playlistName.equals("//history")) {
            mDisplaying = WhichPlaylist.HISTORY;
        } else if(playlistName.equals("//queue")) {
            mDisplaying = WhichPlaylist.QUEUE;
        } else {
            throw new RuntimeException(String.format("Unknown playlist \"%s\" requested in %s",
                        playlistName, dataUri));
        }
    }

    @Override
    public void onStart()
    {
        super.onStart();

        // Subscribe to playlist events.
        NectroidApplication app = (NectroidApplication)getApplication();
        mPlaylistManager = app.getPlaylistManager();
        mPlaylistManager.addTaskListener(this);
        mPlaylistManager.addSongListener(this);

        // Set up the UI for whichever playlist was requested
        mPlaylist = mPlaylistManager.getPlaylist();
        String appName = getString(R.string.app_name);
        String listName;
        int emptyText;
        if(mDisplaying == WhichPlaylist.HISTORY) {
            mListAdapter = new PlaylistHistoryAdapter(mPlaylist, this);
            listName = getString(R.string.history);
            emptyText = R.string.history_empty;
        } else {
            mListAdapter = new PlaylistQueueAdapter(mPlaylist, this);
            listName = getString(R.string.queue);
            emptyText = R.string.queue_empty;
        }
        setListAdapter(mListAdapter);
        updatePlaylistPosition();
        setTitle(String.format("%s - %s", appName, listName));
        TextView emptyView = (TextView)findViewById(android.R.id.empty);
        emptyView.setText(emptyText);

        // Update the throbber to the current state.  (If we do this before we set the title, it
        // seems to be ignored.)
        setProgressBarIndeterminateVisibility(mPlaylistManager.isUpdating());
    }

    @Override
    public void onStop()
    {
        // Unsubscribe from updates.
        mPlaylistManager.removeTaskListener(this);
        mPlaylistManager.removeSongListener(this);

        super.onStop();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        mPlaylistManager.requestAutoRefresh(this);
    }

    @Override
    public void onPause()
    {
        super.onPause();
        mPlaylistManager.unrequestAutoRefresh(this);
    }


    @Override
    protected void onListItemClick(ListView l, View v, int position, long id)
    {
        // Find the base URL for the current site.
        NectroidApplication app = (NectroidApplication)getApplication();
        String baseUrl = app.getSiteManager().getCurrentSite().getBaseUrl();

        // Open the song link.
        Playlist.Entry song = (Playlist.Entry)mListAdapter.getItem(position);
        String songLink = baseUrl + song.songLink(this);
        Uri link = Uri.parse(songLink);
        Intent intent = new Intent(Intent.ACTION_VIEW, link);
        startActivity(intent);
    }


    /** Initialize the contents of the Activity's standard options menu. */
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_options, menu);
        return true;
    }


    /** Called whenever an item in the Activity's options menu is selected. */
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch(item.getItemId()) {
            case R.id.settings_item:
                // Show the settings activity.
                Intent settingsActivity = new Intent(this, SettingsActivity.class);
                startActivity(settingsActivity);
                return true;

            case R.id.refresh_item:
                // Refresh the playlist.
                mPlaylistManager.cancelUpdate();
                mPlaylistManager.update(this, false);
                return true;

            default:
                return false;
        }
    }


    // Override in API level 5+
    public void onBackPressed()
    {
        // Use a different transition depending on which playlist we're showing.
        finish();
        if(mDisplaying == WhichPlaylist.HISTORY) {
            Transition.set(this, R.anim.slide_in_right, R.anim.slide_out_left);
        } else {
            Transition.set(this, R.anim.slide_in_left, R.anim.slide_out_right);
        }
    }


    ///
    /// Playlist event handlers
    ///

    public void onTaskStarted(Object manager)
    {
        setProgressBarIndeterminateVisibility(true);
    }

    public void onTaskFinished(Object manager, Object result)
    {
        Playlist playlist = (Playlist)result;
        mPlaylist = playlist;
        mListAdapter.setPlaylist(playlist);
        updatePlaylistPosition();
        setProgressBarIndeterminateVisibility(false);
    }

    public void onTaskCancelled(Object manager)
    {
        setProgressBarIndeterminateVisibility(false);
    }

    public void onTaskFailed(Object manager)
    {
        setProgressBarIndeterminateVisibility(false);
        Toast errorToast = Toast.makeText(this, R.string.playlist_failed, Toast.LENGTH_SHORT);
        errorToast.show();
    }

    public void onSongChanged(Playlist.EntryAndTimeLeft ent)
    {
        updatePlaylistPosition(ent);
    }


    ///
    /// Utilities
    ///

    /** Find our current position in the playlist and update the GUI. */
    private void updatePlaylistPosition(Playlist.EntryAndTimeLeft ent)
    {
        int queueOffset;

        java.util.List<Playlist.Entry> queue = mPlaylist.getQueue();
        if(ent == null) {
            // We passed the end of the queue!
            queueOffset = queue.size();
        } else {
            // Find the song after the current one.  That's our queue offset.
            Playlist.Entry currentEntry = ent.getEntry();

            if(mPlaylist.getCurrentEntry() == currentEntry) {
                queueOffset = 0;
            } else {
                queueOffset = queue.indexOf(currentEntry) + 1;
            }
        }

        mListAdapter.setQueueOffset(queueOffset);
    }

    private void updatePlaylistPosition()
    {
        if(mPlaylist != null) {
            updatePlaylistPosition(mPlaylist.atNow());
        }
    }
}
