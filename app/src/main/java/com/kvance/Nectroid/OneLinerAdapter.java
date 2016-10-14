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
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.text.style.TextAppearanceSpan;
import android.text.util.Linkify;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;


class OneLinerAdapter extends BaseAdapter
{
    protected List<OneLiner> mOneLiners;
    protected Context mContext;

    private TextAppearanceSpan mAuthorSpan;


    public OneLinerAdapter(List<OneLiner> oneLiners, Context context)
    {
        super();
        if(oneLiners == null) {
            mOneLiners = new ArrayList<OneLiner>();
        } else {
            mOneLiners = oneLiners;
        }
        mAuthorSpan = new TextAppearanceSpan(context, R.style.oneliner_author);
        mContext = context;
    }

    
    public void setOneLiners(List<OneLiner> oneLiners)
    {
        if(oneLiners == null) {
            mOneLiners = new ArrayList<OneLiner>();
        } else {
            mOneLiners = oneLiners;
        }
        notifyDataSetChanged();
    }


    ///
    /// Adapter methods
    ///

    @Override
    public int getCount()
    {
        return mOneLiners.size();
    }

    @Override
    public long getItemId(int position)
    {
        OneLiner oneLiner = mOneLiners.get(position);
        return oneLiner.hashCode();
    }

    @Override
    public Object getItem(int position)
    {
        return mOneLiners.get(position);
    }

    @Override
    public boolean hasStableIds()
    {
        return false;
    }

    @Override
    public boolean isEnabled(int position)
    {
        return false;
    }


    @Override
    public View getView(int position, View convertView, ViewGroup parent)
    {
        TextView view;
        if(convertView == null) {
            view = new TextView(mContext);
            view.setAutoLinkMask(Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES);
            view.setMovementMethod(LinkMovementMethod.getInstance());
            view.setFocusable(false);
        } else {
            view = (TextView)convertView;
        }

        OneLiner oneLiner = mOneLiners.get(position);
        SpannableStringBuilder text = new SpannableStringBuilder(oneLiner.getAuthor() + ": ");
        text.setSpan(mAuthorSpan, 0, text.length(), 0);
        text.append(oneLiner.getMessage());
        view.setText(text);

        return view;
    }
}
