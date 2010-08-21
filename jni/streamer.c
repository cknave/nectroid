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

#include <errno.h>
#include <string.h>
#include <unistd.h>

#include "abort.h"
#include "logmacros.h"
#include "mad.h"
#include "read.h"

/* Maximum java local references per loop */
#define N_REFS 16


/*
 * Forward declarations
 */

static enum mad_flow on_mad_input(void *data, struct mad_stream *stream);
static enum mad_flow on_mad_output(void *data, struct mad_header const *header,
        struct mad_pcm *pcm);
static enum mad_flow on_mad_error(void *data, struct mad_stream *stream, struct mad_frame *frame);

static int update_audio_format(JNIEnv *env, jobject streamer, int samplerate, int channels);
static int write_pcm_output(JNIEnv *env, jobject streamer, struct mad_pcm *pcm);
static int update_buffering_state(JNIEnv *env, jobject streamer, jboolean is_buffering);
static int start_playing(JNIEnv *env, jobject streamer);


/*
 * Data structures
 */

/* Local decoder state */
struct decoder_state {
    /* MP3 buffer */
    unsigned char *buffer;
    int buffer_size;

    /* Size of the PCM buffer (***NOT*** the MP3 buffer) in bytes */
    int pcm_buffer_size;

    /* Source file descriptor */
    int source_fd;

    /* Format of the last PCM chunk decoded */
    int last_samplerate;
    int last_channels;

    /* AudioTrack playing state.  Values:
     * -1: playing
     *  0: not playing
     * >0: number of bytes written to PCM buffer (not playing yet) */
    int playing_state;

    /* Java MP3Streamer instance */
    jobject streamer;
    JNIEnv *env;
};


/* Struct for returning the Java PCM buffer and its jobject pointer */
struct buffer_and_object {
    jshort *buffer;
    jobject object;
};


/*
 * Public interface
 */

JNIEXPORT jboolean JNICALL Java_com_kvance_Nectroid_MP3Streamer_runStreamingLoop
    (JNIEnv *env, jobject obj, jint sock, jint mp3_bufsize, jint pcm_bufsize)
{
    struct mad_decoder decoder;
    struct decoder_state state;
    int rc;
    int error = 0;

    /* Allocate the mp3 buffer, and initialize our local state. */
    state.buffer = malloc(mp3_bufsize);
    if(state.buffer == NULL) {
        error = 1;
    }

    /* Finish initializing state. */
    if(!error) {
        state.buffer_size = mp3_bufsize;
        state.pcm_buffer_size = pcm_bufsize;
        state.source_fd = sock;
        state.last_samplerate = 0;
        state.last_channels = 0;
        state.playing_state = 0;
        state.streamer = obj;
        state.env = env;
        mad_decoder_init(&decoder, &state, on_mad_input, NULL /* header */, NULL /* filter */,
                on_mad_output, on_mad_error, NULL /* message */);
    }

    /* Notify that we're going to fill the buffer. */
    if(!error) {
        error = update_buffering_state(env, obj, JNI_TRUE);
    }

    /* Completely fill the MP3 buffer. */
    if(!error) {
        LOGI("Filling buffer");
        error = read_fully(sock, state.buffer, mp3_bufsize, &g_abort);
    }

    /* Notify that we're no longer buffering. */
    if(!error) {
        error = update_buffering_state(env, obj, JNI_FALSE);
    }

    /* Start the decoder loop. */
    if(!error) {
        LOGI("Starting decoder loop");
        rc = mad_decoder_run(&decoder, MAD_DECODER_MODE_SYNC);
        if(rc != 0) {
            LOGE("Decoder failed");
            error = 1;
        }
        LOGI("Finished decoder loop");
    }

    /* Clean up. */
    if(state.buffer != NULL) {
        free(state.buffer);
        mad_decoder_finish(&decoder);
    }

    /* Return success boolean. */
    return error ? JNI_FALSE : JNI_TRUE;
}


JNIEXPORT void JNICALL Java_com_kvance_Nectroid_MP3Streamer_setAbortFlag
    (JNIEnv *env, jobject obj, jboolean abort)
{
    if(abort == JNI_TRUE) {
        g_abort = 1;
    } else {
        g_abort = 0;
    }
}



/*
 * libmad event handlers
 */

static enum mad_flow on_mad_input(void *data, struct mad_stream *stream)
{
    struct decoder_state *dsdata = data;
    unsigned char *frame_ptr = NULL;
    int offset = 0;
    int length = 0;
    int new_bytes = 0;
    int error = 0;

    /* Copy the last frame to the beginning of the buffer. */
    if(stream->next_frame != NULL) {
        offset = stream->bufend - stream->next_frame;
        memmove(dsdata->buffer, stream->next_frame, offset);
    }

    /* Refill the buffer. */
    new_bytes = read(dsdata->source_fd, dsdata->buffer + offset, dsdata->buffer_size - offset);
    if(new_bytes < 0) {
        LOGE("Error reading MP3: %s", strerror(errno));
        error = 1;
    } else if(new_bytes == 0) {
        LOGE("EOF reading MP3");
        error = 1;
    } else {
        length = offset + new_bytes;
    }
                
    /* Stream the data. */
    if(!error) {
        mad_stream_buffer(stream, dsdata->buffer, length);
    }

    return error ? MAD_FLOW_STOP : MAD_FLOW_CONTINUE;
}


static enum mad_flow on_mad_output(void *data, struct mad_header const *header,
        struct mad_pcm *pcm)
{
    struct decoder_state *dsdata = data;
    int frame_rc = 0;
    JNIEnv *env = dsdata->env;
    int pcm_bytes_written = 0;
    int error = 0;

    /* Start a new Java stack frame. */
    frame_rc = (*env)->PushLocalFrame(env, N_REFS);
    if(frame_rc < 0) {
        LOGE("Out of memory on new stack frame.");
        error = 1;
    }

    /* Check if we should abort. */
    if(g_abort) {
        LOGI("Aborting MP3 playback.");
        error = 1;
    }

    /* Check for a change in audio format. */
    if(!error) {
        if(pcm->samplerate != dsdata->last_samplerate || pcm->channels != dsdata->last_channels) {
            LOGI("Detected change in audio format");
            error = update_audio_format(env, dsdata->streamer, pcm->samplerate, pcm->channels);
            dsdata->last_samplerate = pcm->samplerate;
            dsdata->last_channels = pcm->channels;
        }
    }

    /* Send the PCM data to the OS. */
    if(!error) {
        pcm_bytes_written = write_pcm_output(env, dsdata->streamer, pcm);
        if(pcm_bytes_written == -1) {
            return error;
        }
    }

    /* If the track isn't playing yet, start it if there's enough data. */
    if(!error) {
        if(dsdata->playing_state >= 0) {
            dsdata->playing_state += pcm_bytes_written;
            if(dsdata->playing_state >= (dsdata->pcm_buffer_size * 85/100)) {
                LOGI("Filled PCM buffer to %d (threshold=%d, size=%d)", dsdata->playing_state, (dsdata->pcm_buffer_size * 85/100), dsdata->pcm_buffer_size);
                /* The buffer is at least 3/4 filled.  Start playing. */
                error = start_playing(env, dsdata->streamer);
                if(!error) {
                    dsdata->playing_state = -1;
                }
            }
        }
    }

    /* Exit the stack frame. */
    if(frame_rc >= 0) {
        (*env)->PopLocalFrame(env, NULL);
    }

    /* Stop decoding on error. */
    return error ? MAD_FLOW_STOP : MAD_FLOW_CONTINUE;
}


static enum mad_flow on_mad_error(void *data, struct mad_stream *stream, struct mad_frame *frame)
{
    struct decoder_state *dsdata = data;

    LOGE("decoding error 0x%04x (%s)", stream->error, mad_stream_errorstr(stream));

    return MAD_FLOW_CONTINUE;
}


/*
 * Utility functions
 */

/* Update the AudioTrack format on the Java side. */
static int update_audio_format(JNIEnv *env, jobject streamer, int samplerate, int channels)
{
    int error = 0;

    /* Find the onAudioFormatChanged method. */
    jclass cls = (*env)->GetObjectClass(env, streamer);
    jmethodID mid = (*env)->GetMethodID(env, cls, "onAudioFormatChanged", "(II)Z");
    if(mid == NULL) {
        LOGE("Could not find the onAudioFormatChanged() method");
        error = 1;
    }

    /* Call it. */
    if(!error) {
        jboolean result = (*env)->CallBooleanMethod(env, streamer, mid, samplerate, channels);
        if(!result) {
            error = 1;
        }
    }

    return error;
}

/* Get a pointer to the short[] array backing the mPcmBuffer of this MP3Streamer object. */
static int get_pcm_buffer(JNIEnv *env, jobject streamer, struct buffer_and_object *result)
{
    jclass cls = NULL;
    jfieldID fid = NULL;
    jshortArray array_obj = NULL;
    jshort *pcm_buffer = NULL;
    int error = 0;

    /* Get the PCM buffer field. */
    cls = (*env)->GetObjectClass(env, streamer);
    fid = (*env)->GetFieldID(env, cls, "mPcmBuffer", "[S");
    if(fid == NULL) {
        LOGE("Could not find the mPcmBuffer field");
        error = 1;
    }

    /* Get the PCM buffer array object. */
    if(!error) {
        array_obj = (*env)->GetObjectField(env, streamer, fid);
        if(array_obj == NULL) {
            LOGE("Could not get the pcm buffer array object");
            error = 1;
        }
    }

    /* Get the PCM buffer pointer. */
    if(!error) {
        pcm_buffer = (*env)->GetShortArrayElements(env, array_obj, NULL);
        if(pcm_buffer == NULL) {
            LOGE("Could not get the pcm buffer pointer");
            error = 1;
        }
    }

    /* Fill the result buffer. */
    if(!error) {
        result->buffer = pcm_buffer;
        result->object = array_obj;
    }

    return error;
}


/* Write the Java PCM buffer to the Java to the Java AudioTrack object. */
static int write_buffer_to_audiotrack(JNIEnv *env, jobject streamer, jobject buffer, int length)
{
    jclass streamer_cls = NULL;
    jfieldID fid = NULL;
    jobject audiotrack = NULL;
    jmethodID write_mid = NULL;
    int error = 0;

    /* Get the audio track field. */
    streamer_cls = (*env)->GetObjectClass(env, streamer);
    fid = (*env)->GetFieldID(env, streamer_cls, "mAudioTrack", "Landroid/media/AudioTrack;");
    if(fid == NULL) {
        LOGE("Could not find the mAudioTrack field");
        error = 1;
    }

    /* Get the audio track object. */
    if(!error) {
        audiotrack = (*env)->GetObjectField(env, streamer, fid);
        if(audiotrack == NULL) {
            LOGE("Could not get the audiotrack object");
            error = 1;
        }
    }

    /* Get the audiotrack's write() method. */
    if(!error) {
        jclass audiotrack_cls = (*env)->GetObjectClass(env, audiotrack);
        write_mid = (*env)->GetMethodID(env, audiotrack_cls, "write", "([SII)I");
        if(write_mid == NULL) {
            LOGE("Could not get the AudioTrack.write() method ID");
            error = 1;
        }
    }

    /* Call write() on our buffer. */
    if(!error) {
        int samples_written = (*env)->CallIntMethod(env, audiotrack, write_mid, buffer, 0, length);
        if(samples_written < 0) {
            LOGE("Error %d writing to audio buffer", samples_written);
            error = 1;
        } else if(samples_written != length) {
            LOGW("Only wrote %d / %d samples", samples_written, length);
        }
    }

    return error;
}


/*
 * The following utility routine performs simple rounding, clipping, and
 * scaling of MAD's high-resolution samples down to 16 bits. It does not
 * perform any dithering or noise shaping, which would be recommended to
 * obtain any exceptional audio quality. It is therefore not recommended to
 * use this routine if high-quality output is desired.
 *
 * (C) 2000-2004 Underbit Technologies, Inc.  See libmad for license.
 */
static inline
signed int scale(mad_fixed_t sample)
{
  /* round */
  sample += (1L << (MAD_F_FRACBITS - 16));

  /* clip */
  if (sample >= MAD_F_ONE)
    sample = MAD_F_ONE - 1;
  else if (sample < -MAD_F_ONE)
    sample = -MAD_F_ONE;

  /* quantize */
  return sample >> (MAD_F_FRACBITS + 1 - 16);
}


/* Write this pcm data to the AudioTrack on the Java side. */
static int write_pcm_output(JNIEnv *env, jobject streamer, struct mad_pcm *pcm)
{
    struct buffer_and_object bno;
    int error = 0;
    int result = -1;
    bno.buffer = NULL;

    /* Get a pointer to the Java-side PCM buffer and its object reference. */
    error = get_pcm_buffer(env, streamer, &bno);

    /* Output scaled PCM data to the pointer. */
    if(!error) {
        jshort *outp = bno.buffer;
        int nchannels = pcm->channels;
        int nsamples = pcm->length;
        mad_fixed_t *left_ch = pcm->samples[0];
        mad_fixed_t *right_ch = pcm->samples[1];

        // TODO: check array length
        while(nsamples--) {
            *(outp++) = scale(*left_ch++);
            if(nchannels == 2) {
                (*outp++) = scale(*right_ch++);
            }
        }
    }

    /* Release the buffer pointer. */
    if(bno.buffer != NULL) {
        (*env)->ReleaseShortArrayElements(env, bno.object, bno.buffer, 0);
    }

    /* Send the buffer to the AudioTrack. */
    if(!error) {
        int length = pcm->length * pcm->channels;
        error = write_buffer_to_audiotrack(env, streamer, bno.object, length);
    }

    /* On success, return the number of bytes written. */
    if(!error) {
        /* Number of samples * 2 bytes (16-bit) * number of channels */
        result = pcm->length * 2 * pcm->channels;
    }

    return result;
}


/* Update the buffering state on the Java side. */
static int update_buffering_state(JNIEnv *env, jobject streamer, jboolean is_buffering)
{
    int error = 0;

    /* Find the notifyBuffering method. */
    jclass cls = (*env)->GetObjectClass(env, streamer);
    jmethodID mid = (*env)->GetMethodID(env, cls, "notifyBuffering", "(Z)V");
    if(mid == NULL) {
        LOGE("Could not find the notifyBuffering() method");
        error = 1;
    }

    /* Call it. */
    if(!error) {
        (*env)->CallVoidMethod(env, streamer, mid, is_buffering);
    }

    return error;
}


/* Update the AudioTrack format on the Java side. */
static int start_playing(JNIEnv *env, jobject streamer)
{
    int error = 0;

    /* Find the startPlaying method. */
    jclass cls = (*env)->GetObjectClass(env, streamer);
    jmethodID mid = (*env)->GetMethodID(env, cls, "startPlaying", "()I");
    if(mid == NULL) {
        LOGE("Could not find the onAudioFormatChanged() method");
        error = 1;
    }

    /* Call it. */
    if(!error) {
        error = (*env)->CallIntMethod(env, streamer, mid);
    }

    return error;
}
