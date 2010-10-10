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
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.webkit.WebView;
import android.widget.TextView;


public class AboutActivity extends Activity
{
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.about);

        ((NectroidApplication)getApplication()).updateWindowBackground(getWindow());

        // Fill in title.
        String version;
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(),
                    PackageManager.GET_META_DATA);
            version = pInfo.versionName;
        } catch(PackageManager.NameNotFoundException e) {
            version = "";
        }
        String aboutText = getString(R.string.app_name) + " " + version;
        TextView textView = (TextView)findViewById(R.id.about_title);
        textView.setText(aboutText);

        // Set up WebView widget.
        WebView webView = (WebView)findViewById(R.id.about_webview);
        webView.setBackgroundColor(0);
        webView.loadUrl("file:///android_asset/about.html");
    }
}
