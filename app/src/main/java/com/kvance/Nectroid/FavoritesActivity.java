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
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class FavoritesActivity extends ListActivity
        implements BackgroundTaskListener
{

    private FavoritesManager mFavoritesManager;

    private Favorites mFavorites;
    private FavoritesAdapter mListAdapter;

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
            throw new RuntimeException("Started FavoritesActivity with null data URI");
        }
        if(!dataUri.getScheme().equals("nectroid")) {
            throw new RuntimeException(String.format("Unknown URI scheme \"%s\" in %s",
                    dataUri.getScheme(), dataUri));
        }

        // Check which favorites we're supposed to display
        String favoritesName = dataUri.getSchemeSpecificPart();
        if(!favoritesName.equals("//favorites")) {
            throw new RuntimeException(String.format("Unknown favoritesName \"%s\" requested in %s",
                    favoritesName, dataUri));
        }
    }

    @Override
    public void onStart()
    {
        super.onStart();

        // Subscribe to playlist events.
        NectroidApplication app = (NectroidApplication)getApplication();
        mFavoritesManager = app.getFavoritesManager();
        mFavoritesManager.addTaskListener(this);

        // Set up the UI for whichever playlist was requested
        mFavorites = mFavoritesManager.getFavorites();
        String appName = getString(R.string.app_name);
        String listName = "Favorites";
        int emptyText = R.string.nofavs;
        mListAdapter = new FavoritesAdapter(mFavorites, this);
        setListAdapter(mListAdapter);

        setTitle(String.format("%s - %s", appName, listName));
        TextView emptyView = (TextView)findViewById(android.R.id.empty);
        emptyView.setText(emptyText);

        // Update the throbber to the current state.  (If we do this before we set the title, it
        // seems to be ignored.)
        setProgressBarIndeterminateVisibility(mFavoritesManager.isUpdating());
    }

    @Override
    public void onStop()
    {
        // Unsubscribe from updates.
        mFavoritesManager.removeTaskListener(this);

        super.onStop();
    }

    @Override
    public void onResume()
    {
        super.onResume();
        mFavoritesManager.requestAutoRefresh(this);
    }

    @Override
    public void onPause()
    {
        super.onPause();
        mFavoritesManager.unrequestAutoRefresh(this);
    }


    @Override
    protected void onListItemClick(ListView l, final View v, int position, long id)
    {
        // Find the base URL for the current site.
        NectroidApplication app = (NectroidApplication)getApplication();
        String baseUrl = app.getSiteManager().getCurrentSite().getBaseUrl();

        // Open the song link.
        Favorites.Entry song = (Favorites.Entry)mListAdapter.getItem(position);

        // Req the song is not locked
        if (song.isLocked()) {
            Toast.makeText(this, "The song already locked :(", Toast.LENGTH_LONG).show();
        } else {

            try {
                String url = baseUrl + FavoritesActivity.this.getString(R.string.url_song_queue);
                HttpClient client = app.getHttpClient();

                HttpPost post = new HttpPost(url);
                System.out.println("SONG QUEUE URL="+url);
                List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(1);
                nameValuePairs.add(new BasicNameValuePair(FavoritesActivity.this.getString(R.string.parameter_songid), Integer.toString(song.getId())));
                post.setEntity(new UrlEncodedFormEntity(nameValuePairs));

                HttpResponse response = client.execute(post);
                BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
                rd.close(); // I don't need to read the output

                FetchUrl.Result data = FetchUrl.get(new URL(baseUrl+FavoritesActivity.this.getString(R.string.url_song)+ song.getId()));
                String songinfo = data.getResponse();
                if (songinfo.contains("<locked>True</locked>")) {
                    song.mLocked = true;

                    v.setEnabled(false);
                    Toast.makeText(FavoritesActivity.this, "Requesting song: " + song.getTitle(), Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(FavoritesActivity.this, "Song already locked or too much songs queued :(", Toast.LENGTH_LONG).show();
                }
            } catch (final Exception e) {
                Toast.makeText(FavoritesActivity.this, "Requesting song failed with error=" + e, Toast.LENGTH_LONG).show();
            }
        }

    }


    /** Initialize the contents of the Activity's standard options menu. */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
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
                mFavoritesManager.cancelUpdate();
                mFavoritesManager.update(this, false);
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
        Transition.set(this, R.anim.slide_in_left, R.anim.slide_out_right);
    }


    ///
    /// Favorites event handlers
    ///

    public void onTaskStarted(Object manager)
    {
        setProgressBarIndeterminateVisibility(true);
    }

    public void onTaskFinished(Object manager, Object result)
    {
        //System.out.println("RESULT="+result);
        Favorites favorites = (Favorites)result;
        mFavorites = favorites;
        mListAdapter.setFavorites(mFavorites);
        setProgressBarIndeterminateVisibility(false);
    }

    public void onTaskCancelled(Object manager)
    {
        setProgressBarIndeterminateVisibility(false);
    }

    public void onTaskFailed(Object manager)
    {
        setProgressBarIndeterminateVisibility(false);
        Toast errorToast = Toast.makeText(this, R.string.favorites_failed, Toast.LENGTH_SHORT);
        errorToast.show();
    }
}
