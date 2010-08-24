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

import android.content.Context;


class PlaylistQueueAdapter extends PlaylistAdapter
{
    List<Playlist.Entry> mQueue;


    public PlaylistQueueAdapter(Playlist playlist, Context context)
    {
        super(playlist, context);
        setPlaylist(playlist);
    }

    @Override
    public void setPlaylist(Playlist playlist)
    {
        super.setPlaylist(playlist);
        if(playlist != null) {
            mQueue = playlist.getQueue();
        }
    }


    ///
    /// Adapter methods
    ///

    @Override
    public int getCount()
    {
        if(mPlaylist == null) {
            return 0;
        } else {
            return mPlaylist.getQueue().size() - mQueueOffset;
        }
    }


    @Override
    public Object getItem(int position)
    {
        return mQueue.get(position + mQueueOffset);
    }
}
