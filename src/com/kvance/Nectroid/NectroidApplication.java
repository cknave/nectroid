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
import android.app.Application;
import android.content.Context;


public class NectroidApplication extends Application
{
    private PlaylistManager mPlaylistManager;
    private StreamsManager mStreamsManager;
    private PlayerManager mPlayerManager;
    private OneLinerManager mOneLinerManager;


    @Override
    public void onCreate()
    {
        super.onCreate();
        Context appContext = getApplicationContext();
        mPlaylistManager = new PlaylistManager(appContext);
        mStreamsManager = new StreamsManager();
        mPlayerManager = new PlayerManager();
        mOneLinerManager = new OneLinerManager(appContext);
        mOneLinerManager.listenForPreferences();
    }


    @Override
    public void onLowMemory()
    {
        mPlaylistManager.onLowMemory();
        mStreamsManager.onLowMemory();
        mOneLinerManager.onLowMemory();
    }


    @Override
    public void onTerminate()
    {
        super.onTerminate();
        mOneLinerManager.unlistenForPreferences();
    }


    public PlaylistManager getPlaylistManager() { return mPlaylistManager; }
    public StreamsManager getStreamsManager() { return mStreamsManager; }
    public PlayerManager getPlayerManager() { return mPlayerManager; }
    public OneLinerManager getOneLinerManager() { return mOneLinerManager; }


    /** Return true if any of our managers are loading something. */
    public boolean isLoadingAnything()
    {
        return mPlaylistManager.isUpdating() ||
            mStreamsManager.isUpdating() ||
            mOneLinerManager.isUpdating() ||
            mPlayerManager.getPlayerState() == PlayerService.State.LOADING;
    }
}
