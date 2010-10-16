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
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.method.MovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View.OnClickListener;
import android.view.View;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;


public class NectroidActivity extends Activity
    implements BackgroundTaskListener, PlaylistManager.SongListener, PlayerManager.StateListener
{
    // Widgets
    private TextView mCurrentlyPlayingView;
    private TextView mRequestedByView;
    private TextView mTimeLeftView;
    private ListView mOneLinerView;
    private ImageButton mPlayButton;

    // Managers
    private PlaylistManager mPlaylistManager;
    private PlayerManager mPlayerManager;
    private OneLinerManager mOneLinerManager;

    // Other fields
    private OneLinerAdapter mOneLinerAdapter;
    private boolean mPaused;
    private Handler mHandler;
    private int mTimeLeft;

    // When a new stream is selected, store the choice here until we verify it works.
    private class StreamChoice { URL stream; int id; }
    private StreamChoice mStreamChoice;


    // Activity request codes
    private static final int PICK_STREAM_REQUEST = 0;

    private static final String TAG = "Nectroid";
    

    ///
    /// Activity event handlers
    ///

    /** Called when the Activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.main);

        mHandler = new Handler();
        
        Window window = getWindow();
        getNectroidApp().updateWindowBackground(window);

        createWidgets();

        // Fix banding on gradients
        window.setFormat(android.graphics.PixelFormat.RGBA_8888);
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DITHER);
    }


    /** Called when the Activity is being displayed to the user. */
    @Override
    protected void onStart()
    {
        super.onStart();
        NectroidApplication app = getNectroidApp();

        // Update the throbber to the current state.
        mPlaylistManager = app.getPlaylistManager();
        setProgressBarIndeterminateVisibility(app.isLoadingAnything());

        // Subscribe to playlist events.
        mPlaylistManager.addTaskListener(this);
        mPlaylistManager.addSongListener(this);

        // Subscribe to player events, and check on the current player state.
        mPlayerManager = app.getPlayerManager();
        mPlayerManager.addStateListener(this);
        onStateChanged(mPlayerManager.getPlayerState());

        // Subscribe to oneliner events.
        mOneLinerManager = app.getOneLinerManager();
        mOneLinerManager.addTaskListener(this);

        // Update the GUI title bar.
        updateTitle();

        // Update the GUI with the current song.
        Playlist.EntryAndTimeLeft currentSong = mPlaylistManager.getCurrentSong();
        if(currentSong != null) {
            updatePlaylistUI(currentSong);
        } else {
            clearPlaylistUI();
        }

        // Update the GUI with the current oneliners.
        OneLiner.List oneLiners = mOneLinerManager.getOneLiners();
        if(oneLiners != null) {
            mOneLinerAdapter.setOneLiners(oneLiners);
        } else {
            clearOneLiners();
        }
    }


    /** Called when the Activity is no longer visible to the user. */
    @Override
    protected void onStop()
    {
        // Unsubscribe from various events.
        mPlaylistManager.removeTaskListener(this);
        mPlaylistManager.removeSongListener(this);
        mPlayerManager.removeStateListener(this);
        mOneLinerManager.removeTaskListener(this);

        super.onStop();
    }


    /** Called when the Activity is going into the background. */
    @Override
    protected void onPause()
    {
        super.onPause();
        mPaused = true;

        // Disable auto-refresh.
        mPlaylistManager.unrequestAutoRefresh(this);
        mOneLinerManager.unrequestAutoRefresh(this);
    }


    /** Called when the Activity is ready to start interacting with the user. */
    @Override
    protected void onResume()
    {
        super.onResume();
        mPaused = false;

        // (Re-)enable auto-refresh.
        mPlaylistManager.requestAutoRefresh(this);
        mOneLinerManager.requestAutoRefresh(this);

        // (Re-)start the 1-second timer
        startTimer();
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

            case R.id.about_item:
                // Show the about activity.
                Intent aboutActivity = new Intent(this, AboutActivity.class);
                startActivity(aboutActivity);
                return true;

            case R.id.refresh_item:
                // Refresh the playlist and the oneliners.
                mPlaylistManager.cancelUpdate();
                mPlaylistManager.update(this, false);
                mOneLinerManager.cancelUpdate();
                mOneLinerManager.update(this, false);
                return true;

            default:
                return false;
        }
    }


    /** Called when an Activity that this Activity launched exits. */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        switch(requestCode) {
            case PICK_STREAM_REQUEST:
                if(resultCode == Activity.RESULT_OK) {
                    int id = data.getIntExtra(StreamsActivity.EXTRA_ID, -1);
                    int bitrate = data.getIntExtra(StreamsActivity.EXTRA_BITRATE, 192);
                    onStreamPicked(data.getData(), id, bitrate);
                }
                break;

            default:
                Log.w(TAG, String.format("Unknown request code %d", requestCode));
                break;
        }
    }


    ///
    /// Background task event handlers
    ///

    @Override
    public void onTaskStarted(Object manager)
    {
        setProgressBarIndeterminateVisibility(true);
    }

    @Override
    public void onTaskFinished(Object manager, Object result)
    {
        // Determine what task was finished, and call the appropriate event handler.
        Class managerClass = manager.getClass();
        if(managerClass == PlaylistManager.class) {
            mHandler.post(onPlaylistUpdateTaskFinished);

        } else if(managerClass == OneLinerManager.class) {
            OneLiner.List oneLiners = (OneLiner.List)result;
            mHandler.post(new OnOneLinerUpdateTaskFinished(oneLiners));

        } else {
            throw new RuntimeException(String.format("Unknown task finished %s", managerClass));
        }
    }

    @Override
    public void onTaskCancelled(Object manager)
    {
        mHandler.post(new Runnable() {
            public void run() {
                setProgressBarIndeterminateVisibility(getNectroidApp().isLoadingAnything());
            }
        });
    }

    @Override
    public void onTaskFailed(Object manager)
    {
        int stringId;
        Class managerClass = manager.getClass();
        if(managerClass == PlaylistManager.class) {
            stringId = R.string.playlist_failed;
        } else if(managerClass == OneLinerManager.class) {
            stringId = R.string.oneliner_failed;
        } else {
            throw new RuntimeException(String.format("Unknown task failed %s", managerClass));
        }
        Toast.makeText(this, stringId, Toast.LENGTH_SHORT).show();


        mHandler.post(new Runnable() {
            public void run() {
                setProgressBarIndeterminateVisibility(getNectroidApp().isLoadingAnything());
            }
        });
    }


    private Runnable onPlaylistUpdateTaskFinished = new Runnable() {
        public void run() {
            setProgressBarIndeterminateVisibility(getNectroidApp().isLoadingAnything());
        }
    };


    private class OnOneLinerUpdateTaskFinished implements Runnable {
        OneLiner.List mOneLiners;

        public OnOneLinerUpdateTaskFinished(OneLiner.List oneLiners) {
            mOneLiners = oneLiners;
        }

        public void run() {
            mOneLinerAdapter.setOneLiners(mOneLiners);
            setProgressBarIndeterminateVisibility(getNectroidApp().isLoadingAnything());
        }
    }


    ///
    /// Other event handlers
    ///

    @Override
    public void onSongChanged(Playlist.EntryAndTimeLeft newSong)
    {
        updatePlaylistUI(newSong);
    }


    ///
    /// UI event handlers
    ///

    /** The play button was clicked */
    private OnClickListener onPlayButtonClicked = new OnClickListener() {
        public void onClick(View v)
        {
            // The play button can mean "play" or "stop" depending on the player state.
            if(mPlayerManager.getPlayerState() != PlayerService.State.STOPPED) {
                stopStream();
            } else {
                // Check if we have a URL to play.
                URL streamUrl = Prefs.getStreamUrl(NectroidActivity.this);
                if(streamUrl != null) {
                    int bitrate = getBitrateForStreamUrl(streamUrl);
                    Log.d(TAG, String.format("Play stream \"%s\" (%d kbps)", streamUrl, bitrate));
                    playStream(streamUrl, bitrate);
                } else {
                    // No stream picked yet.  Ask the user what they want.
                    Intent intent = new Intent(Intent.ACTION_PICK);
                    intent.setClass(NectroidActivity.this, StreamsActivity.class);
                    startActivityForResult(intent, PICK_STREAM_REQUEST);
                }
            }
        }
    };


    /** The "history" link was clicked */
    private OnClickListener onHistoryClicked = new OnClickListener() {
        public void onClick(View widget)
        {
            Activity us = NectroidActivity.this;
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("nectroid://history"));
            intent.setClass(us, PlaylistActivity.class);
            startActivity(intent);
            Transition.set(us, R.anim.slide_in_left, R.anim.slide_out_right);
        }
    };


    /** The "queue" link was clicked */
    private OnClickListener onQueueClicked = new OnClickListener() {
        public void onClick(View widget)
        {
            Activity us = NectroidActivity.this;
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("nectroid://queue"));
            intent.setClass(us, PlaylistActivity.class);
            startActivity(intent);
            Transition.set(us, R.anim.slide_in_right, R.anim.slide_out_left);
        }
    };


    /** One second has elapsed */
    final Runnable onTick = new Runnable() {
        public void run() {
            // Update the 1-second timer.
            if(mTimeLeft > 0) {
                mTimeLeft--;
                updateTimeLeft();
            }

            // Schedule the next tick.
            if(!mPaused) {
                mHandler.postDelayed(this, 1000);
            }
        }
    };

    /** The user selected a stream to play. */
    private void onStreamPicked(Uri streamUri, int id, int bitrate)
    {
        // Activities return URIs, but we need a URL.
        URL streamUrl = null;
        try {
            streamUrl = new URL(streamUri.toString());
        } catch(MalformedURLException e) {
            Toast.makeText(this, R.string.invalid_stream, Toast.LENGTH_SHORT).show();
        }

        if(streamUrl != null) {
            // Remember the choice.  Once we verify we can play the stream, save it to prefs.
            mStreamChoice = new StreamChoice();
            mStreamChoice.stream = streamUrl;
            mStreamChoice.id = id;
            playStream(streamUrl, bitrate);
        }
    }


    ///
    /// Player event handlers
    ///

    public void onStateChanged(PlayerService.State state)
    {
        switch(state) {
            case STOPPED:
                mPlayButton.setImageResource(R.drawable.play);
                break;

            case PLAYING:
                // If we selected a new stream, we can now remember the choice since we verified
                // that it's playable.
                if(mStreamChoice != null) {
                    getNectroidApp().onUserPickedStream(mStreamChoice.stream, mStreamChoice.id);
                    mStreamChoice = null;
                }
                // And continue on to case LOADING...

            case LOADING:
                mPlayButton.setImageResource(R.drawable.stop);
                break;
        }
        setProgressBarIndeterminateVisibility(getNectroidApp().isLoadingAnything());
    }


    ///
    /// Playlist commands
    ///

    /** Update the playlist part of the UI with this song. */
    private void updatePlaylistUI(Playlist.EntryAndTimeLeft ent)
    {
        mTimeLeft = ent.getTimeLeft();
        updateTimeLeft();
        updateCurrentlyPlaying(ent.getEntry());
        updateRequestedBy(ent.getEntry());
    }

    /** Clear the playlist views. */
    private void clearPlaylistUI()
    {
        mTimeLeft = -1;
        mTimeLeftView.setText("");
        mCurrentlyPlayingView.setText("");
        mRequestedByView.setText("");
    }


    ///
    /// Other commands
    ///

    /** Create our widget references after the activity has been created. */
    private void createWidgets()
    {
        // Get widget references
        mCurrentlyPlayingView = (TextView)findViewById(R.id.currently_playing);
        mRequestedByView = (TextView)findViewById(R.id.requested_by);
        mTimeLeftView = (TextView)findViewById(R.id.time_left);
        mOneLinerView = (ListView)findViewById(R.id.oneliner);
        mPlayButton = (ImageButton)findViewById(R.id.play_button);

        // Make sure hypertext is clickable in textviews that have it.
        MovementMethod lmm = LinkMovementMethod.getInstance();
        mCurrentlyPlayingView.setMovementMethod(lmm);
        mRequestedByView.setMovementMethod(lmm);

        // Set up play button.
        mPlayButton.setOnClickListener(onPlayButtonClicked);

        // Set up hypertext "buttons".
        TextView historyButton = (TextView)findViewById(R.id.history_button);
        historyButton.setMovementMethod(lmm);
        historyButton.setOnClickListener(onHistoryClicked);

        TextView queueButton = (TextView)findViewById(R.id.queue_button);
        queueButton.setMovementMethod(lmm);
        queueButton.setOnClickListener(onQueueClicked);

        // Set up oneliner view.
        mOneLinerAdapter = new OneLinerAdapter(null, this);
        mOneLinerView.setAdapter(mOneLinerAdapter);
        mOneLinerView.setItemsCanFocus(true);
    }


    /** Start the one-second timer. */
    private void startTimer()
    {
        // Remove any existing timer first, just in case.
        mHandler.removeCallbacks(onTick);
        mHandler.postDelayed(onTick, 1000);
    }


    /** Start the player service. */
    private void playStream(URL url, int bitrate)
    {
        Intent intent = new Intent(this, PlayerService.class);
        intent.setAction(PlayerService.ACTION_PLAY);
        intent.setData(Uri.parse(url.toString()));
        intent.putExtra(StreamsActivity.EXTRA_BITRATE, bitrate);
        startService(intent);
    }

    /** Stop the player service. */
    private void stopStream()
    {
        Intent intent = new Intent(this, PlayerService.class);
        boolean stopped = stopService(intent);
        Log.d(TAG, "stopService() returned " + String.valueOf(stopped));
    }


    /** Clear the OneLiners view. */
    private void clearOneLiners()
    {
        mOneLinerAdapter.setOneLiners(null);
    }

    ///
    /// Utilities
    ///

    private void updateCurrentlyPlaying(Playlist.Entry entry)
    {
        // Build the formatted string
        StringBuilder html = new StringBuilder();
        String baseUrl = getNectroidApp().getSiteManager().getCurrentSite().getBaseUrl();
        String songLink = baseUrl + entry.songLink(this);
        html.append(String.format("<b><a href=\"%s\">%s</a></b> by ", songLink, entry.getTitle()));
        java.util.List<Playlist.IdString> artists = entry.getArtists();
        int numArtists = artists.size();
        for(int i = 0; i < numArtists; i++) {
            Playlist.IdString artist = artists.get(i);
            String link = baseUrl + entry.artistLink(artist, this);
            html.append(String.format("<b><a href=\"%s\">%s</a></b>", link, artist.getString()));
            if(i < (numArtists - 1)) {
                html.append(", ");
            }
        }

        // Set the new text
        Spanned spannedText = Html.fromHtml(html.toString());
        mCurrentlyPlayingView.setText(spannedText, TextView.BufferType.SPANNABLE);
    }


    private void updateRequestedBy(Playlist.Entry entry)
    {
        // Build the formatted string
        String baseUrl = getNectroidApp().getSiteManager().getCurrentSite().getBaseUrl();
        String requesterLink = baseUrl + entry.requesterLink(this);
        String html = String.format("<a href=\"%s\">%s</a>", requesterLink,
                entry.getRequester().getString());

        // Set the new text
        Spanned spannedText = Html.fromHtml(html.toString());
        mRequestedByView.setText(spannedText, TextView.BufferType.SPANNABLE);
    }


    private void updateTimeLeft()
    {
        int time = Math.max(0, mTimeLeft);
        mTimeLeftView.setText(String.format("%d:%02d", time / 60, time % 60));
    }


    private void updateTitle()
    {
        String siteName = getNectroidApp().getSiteManager().getCurrentSite().getName();
        String appName = this.getString(R.string.app_name);
        setTitle(appName + " - " + siteName);
    }


    private int getBitrateForStreamUrl(URL streamUrl)
    {
        String urlString = streamUrl.toString();
        int bitrate = 192;

        // Get the list of streams.
        List<Stream> streams = null;
        SQLiteDatabase db = new DbOpenHelper(this).getReadableDatabase();
        try {
            streams = Stream.listFromDB(db, Prefs.getSiteId(this));
        } finally {
            db.close();
        }

        if(streams == null) {
            Log.w(TAG, "Couldn't open streams database; using unknown bitrate");
        } else {
            // Search the stream list for that URL.
            boolean found = false;
            for(Stream stream : streams) {
                if(stream.getUrl().toString().equals(urlString)) {
                    bitrate = stream.getBitrate();
                    found = true;
                    break;
                }
            }
            if(!found) {
                Log.w(TAG, String.format("Couldn't find bitrate for stream \"%s\"", urlString));
            }
        }

        return bitrate;
    }
        

    private NectroidApplication getNectroidApp()
    {
        return (NectroidApplication)getApplication();
    }
}
