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

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;


abstract class PlaylistAdapter extends BaseAdapter
{
    protected Playlist mPlaylist;
    protected Context mContext;
    protected int mQueueOffset;


    public PlaylistAdapter(Playlist playlist, Context context)
    {
        super();
        mPlaylist = playlist;
        mContext = context;
        mQueueOffset = 0;
    }

    
    /** Set how many songs into the playlist we currently are.
     *
     * If mPlaylist.getCurrentEntry() is playling, offset = 0
     * If mPlaylist.getQueue().get(0) is playling, offset = 1
     * and so on.
     */
    public void setQueueOffset(int newOffset)
    {
        mQueueOffset = newOffset;
        notifyDataSetChanged();
    }


    public void setPlaylist(Playlist playlist)
    {
        mPlaylist = playlist;
        mQueueOffset = 0;
        notifyDataSetChanged();
    }


    ///
    /// Adapter methods
    ///

    @Override
    public long getItemId(int position)
    {
        Playlist.Entry entry = (Playlist.Entry)getItem(position);
        return entry.getId();
    }


    @Override
    public boolean hasStableIds()
    {
        return true;
    }


    @Override
    public View getView(int position, View convertView, ViewGroup parent)
    {
        TextView view;
        if(convertView == null) {
            view = new TextView(mContext);
        } else {
            view = (TextView)convertView;
        }

        Playlist.Entry entry = (Playlist.Entry)getItem(position);
        String text = entry.getTitle();
        if(entry.getArtists().size() > 0)
            text += " by " + entry.getArtists().get(0).getString();
        if(entry.getArtists().size() > 1)
            text += ", et al.";
        view.setText(text);

        return view;
    }
}
