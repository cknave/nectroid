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


class RingBuffer
{
    private long mRBPointer;

    RingBuffer(int size)
    {
        mRBPointer = initRB(size);
        if(mRBPointer == 0) {
            throw new RuntimeException();
        }
    }

    protected void finalize() throws Throwable
    {
        if(mRBPointer != 0) {
            cleanupRB(mRBPointer);
        }
    }


    ///
    /// Public interface
    ///

    public native boolean isFull();

    public native int length();


    ///
    /// Native interface
    ///

    /** Initialize a new native ringbuffer, and return a pointer to it. */
    private native long initRB(int size);

    /** Clean up the native resources of this RingBuffer. */
    private native void cleanupRB(long pointer);

    static {
        System.loadLibrary("mp3streamer");
    }
}
