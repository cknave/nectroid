Nectroid
========
Nectroid provides a way to access
[Nectarine Demoscene Radio](https://www.scenemusic.net/demovibes/) or [CVGM Radio](http://www.cvgm.net/demovibes) from Android
devices.  Current feature set:

 * Background music streaming
 * Login and logout (scenemusic, CVGM or Rock with Wolfenstein account needed)
 * Current song, OneLiner, History, Queue and Favorites views
 * Submit song from your favorites
 * Submit message to oneliner
 * AudioScrobbler support
 * Support for other Demovibes-powered sites like
   [CVGM](http://www.cvgm.net/demovibes/)
 * Software MP3 player for devices with old/broken versions of Android

Nectroid source code
(C) 2010, 2012 Kevin Vance <kvance@kvance.com>
(C) 2016 Decorde Matthieu <matthieu.decorde@gmail.com>

Distribute under the terms of the GNU GPL v3.


NOTE: ic\_menu\_refresh.png is Copyright (C) The Android Open Source
      Project.

NOTE: Nectroid uses libmad Copyright (C) 2000-2004 Underbit Technologies, Inc.
      distributed under the GNU GPL v2 or higher.


Nectroid requires the Android SDK and the NDK to build.


Build instructions:

    $ android update project -n Nectroid -p .

    $ cd jni
    $ ndk-build
    $ cd ..

    $ ant debug


Have fun!  
-- kvance
