/* This file is part of Nectroid.
 *
 * Nectroid is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Nectroid is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Nectroid.  If not, see <http://www.gnu.org/licenses/>.
 */
#include <jni.h>

#include <stdlib.h>

#include "logmacros.h"
#include "ringbuffer.h"
#include "ringbuffer_jni.h"


/*
 * Public interface
 */

JNIEXPORT jlong JNICALL Java_com_kvance_Nectroid_RingBuffer_initRB
    (JNIEnv *env, jobject obj, jint size)
{
    struct ringbuffer *rb = ringbuffer_create(size);
    return (long)rb;
}


JNIEXPORT void JNICALL Java_com_kvance_Nectroid_RingBuffer_cleanupRB
    (JNIEnv *env, jobject obj, jlong pointer)
{
    unsigned int uptr = (unsigned int)pointer;
    struct ringbuffer *rb = (struct ringbuffer *)uptr;
    ringbuffer_destroy(rb);
}


JNIEXPORT jboolean JNICALL Java_com_kvance_Nectroid_RingBuffer_isFull
    (JNIEnv *env, jobject obj)
{
    jboolean result = JNI_FALSE;

    struct ringbuffer *rb = get_local_ringbuffer(env, obj);
    if(rb == NULL) {
        LOGE("Tried to call isFull() on NULL ringbuffer");
    } else {
        result = ringbuffer_full(rb) ? JNI_TRUE : JNI_FALSE;
    }

    return result;
}

JNIEXPORT jint JNICALL Java_com_kvance_Nectroid_RingBuffer_length
    (JNIEnv *env, jobject obj)
{
    int result = 0;

    struct ringbuffer *rb = get_local_ringbuffer(env, obj);
    if(rb == NULL) {
        LOGE("Tried to call length() on NULL ringbuffer");
    } else {
        result = ringbuffer_length(rb);
    }

    return result;
}


/*
 * Utility functions
 */

struct ringbuffer *get_local_ringbuffer(JNIEnv *env, jobject obj)
{
    struct ringbuffer *result = NULL;
    jclass cls = (*env)->GetObjectClass(env, obj);
    jfieldID fid = (*env)->GetFieldID(env, cls, "mRBPointer", "J");
    if(fid == NULL) {
        LOGE("Failed to get RingBuffer.mRBPointer's field ID");
    } else {
        unsigned int ptr = (unsigned int)(*env)->GetLongField(env, obj, fid);
        result = (struct ringbuffer *)ptr;
    }
    return result;
}
