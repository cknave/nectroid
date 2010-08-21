#!/bin/sh
# Run the configure script using droid-wrapper.
# kvance 2010-08-15
if ! which droid-gcc >/dev/null 2>/dev/null; then
    echo "Missing droid-gcc.  The droid-wrapper script is required."
    exit
fi

export DROID_ROOT="/opt/android-ndk/build"
export DROID_TARGET="android-3"
export CC="droid-gcc"
export LD="droid-ld"
./configure --host=arm-none-linux-gnueabi --enable-fpm=arm
