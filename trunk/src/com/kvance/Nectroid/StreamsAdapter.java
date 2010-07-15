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

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;


class StreamsAdapter extends BaseAdapter
{
    protected List<Stream> mStreams;
    protected Context mContext;


    public StreamsAdapter(List<Stream> streams, Context context)
    {
        super();
        if(streams == null) {
            mStreams = new ArrayList<Stream>();
        } else {
            mStreams = streams;
        }
        mContext = context;
    }

    
    public void setStreams(List<Stream> streams)
    {
        if(streams == null) {
            mStreams = new ArrayList<Stream>();
        } else {
            mStreams = streams;
        }
        notifyDataSetChanged();
    }


    ///
    /// Adapter methods
    ///

    @Override
    public int getCount()
    {
        return mStreams.size();
    }

    @Override
    public long getItemId(int position)
    {
        Stream stream = mStreams.get(position);
        return stream.getId();
    }

    @Override
    public Object getItem(int position)
    {
        return mStreams.get(position);
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

        Stream stream = mStreams.get(position);
        view.setText(stream.getName());

        return view;
    }
}
