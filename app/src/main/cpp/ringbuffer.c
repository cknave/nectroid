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
#include <stdlib.h>
#include <string.h>

#include "logmacros.h"
#include "ringbuffer.h"


/* Create a new ringbuffer.  Returns NULL on failure. */
struct ringbuffer *ringbuffer_create(int size)
{
    int error = 0;
    struct ringbuffer *rb = NULL;

    /* Allocate the structure. */
    rb = malloc(sizeof(struct ringbuffer));
    if(rb == NULL) {
        LOGE("Out of memory allocating ringbuffer structure");
        error = 1;
    }

    /* Allocate the data buffer. */
    if(!error) {
        rb->buffer = malloc(size);
        if(rb->buffer == NULL) {
            LOGE("Out of memory allocating ringbuffer data buffer");
            error = 1;
        }
    }

    /* Fill in the rest of the fields. */
    if(!error) {
        rb->end = rb->buffer + size;
        rb->read = rb->buffer;
        rb->write = rb->buffer;
    }

    /* Clean up on failure. */
    if(error && (rb != NULL)) {
        free(rb);
        rb = NULL;
    }
    return rb;
}

/* Free the ringbuffer data and its structure. */
void ringbuffer_destroy(struct ringbuffer *rb)
{
    free(rb->buffer);
    free(rb);
}


/* Return 1 if the buffer is empty, or 0 if not empty. */
int ringbuffer_empty(struct ringbuffer *rb)
{
    /* It's empty when the read and write pointers are the same. */
    if(rb->read == rb->write) {
        return 1;
    } else {
        return 0;
    }
}


/* Return 1 if the buffer is full, or 0 if not full. */
int ringbuffer_full(struct ringbuffer *rb)
{
    /* It's full when the write pointer is 1 element before the read pointer, equivalently if the
     * write pointer is at the end and the read pointer is at the beginning (since the buffer is
     * circular). */
    if(rb->write == (rb->read - 1)) {
        return 1;
    } else if((rb->write == (rb->end - 1) && (rb->read == rb->buffer))) {
        return 1;
    } else {
        return 0;
    }
}


/* Return the number of bytes of data written to this ring buffer. */
int ringbuffer_length(struct ringbuffer *rb)
{
    if(rb->read == rb->write) {
        return 0;
    } else if(rb->read < rb->write) {
        return rb->write - rb->read;
    } else {
        return (rb->end - rb->read) + (rb->write - rb->buffer);
    }
}


/* Make sure there is a contiguous buffer of at least n bytes after the read pointer.  The buffer
 * may be reorganized to make this true.
 *
 * Return 1 on error, 0 on success. */
int ringbuffer_require_contiguous_read(struct ringbuffer *rb, int n)
{
    int error = 0;

    /* If the region exceeds the end of the buffer, shift the read pointer back to the start. */
    if((rb->read + n) >= rb->end) {
        error = ringbuffer_realign(rb);
    }

    return error;
}


/* Reorganize the ring buffer such that the read pointer is at the start of memory.
 *
 * Return 1 on error, 0 on success. */
int ringbuffer_realign(struct ringbuffer *rb)
{
    int error = 0;
    if(rb->read == rb->buffer) {
        /* Already aligned; do nothing. */

    } else if(rb->write > rb->read) {
        /* The buffer does not wrap; simply move it back to the beginning.
         *
         * 1. [.......xxxxx...] 
         *     b      r    w  e    (b=buffer, w=write, r=read, e=end)
         *
         * 2. [xxxxx..........]
         *     b    w         e
         *     r
         */
        int length = rb->write - rb->read;
        memmove(rb->buffer, rb->read, length);

        /* Update the pointers. */
        rb->read = rb->buffer;
        rb->write = rb->buffer + length;

    } else {
        /* The buffer wraps around.
         *
         * Copy suffix to a temporary buffer, move prefix to the beginning, and append suffix:
         *
         *    suffix      prefix
         * 1. [ooooo.......xxx] 
         *     b    w      r  e    (b=buffer, w=write, r=read, e=end)
         *
         *  prefix suffix
         * 2. [xxxooooo.......]
         *     b       w      e
         *     r
         */

        /* Copy the suffix to a temporary buffer. */
        int suffix_length = rb->write - rb->buffer;
        void *suffix = malloc(suffix_length);
        if(suffix == NULL) {
            LOGE("Out of memory allocating temporary suffix buffer");
            error = 1;

        } else {
            memcpy(suffix, rb->buffer, suffix_length);
        }

        /* Copy the prefix to the beginning, and append the suffix. */
        if(!error) {
            int prefix_length = rb->end - rb->read;
            unsigned char *suffix_dest = rb->buffer + prefix_length;
            memmove(rb->buffer, rb->read, prefix_length);
            memcpy(suffix_dest, suffix, suffix_length);

            /* Update the pointers. */
            rb->read = rb->buffer;
            rb->write = suffix_dest + suffix_length;

            /* Clean up. */
            free(suffix);
        }
    }

    return error;
}


/* Return the number of contiguous bytes available to read from. */
int ringbuffer_available_contiguous_read(struct ringbuffer *rb)
{
    int result = 0;
    if(rb->write > rb->read) {
        /* Write pointer is after the read pointer; the region extends to the write pointer. */
        result = rb->write - rb->read;
    } else {
        /* Write pointer is before the read pointer; the region extends to the buffer end. */
        result = rb->end - rb->read;
    }

    return result;
}


/* Return the number of contiguous bytes available to write to. */
int ringbuffer_available_contiguous_write(struct ringbuffer *rb)
{
    int result = 0;
    if(rb->write >= rb->read) {
        /* Write pointer is after the read pointer; the region extends to the end of the buffer.
         * If the read pointer is still at the beginning, the region extends 1 byte less to prevent
         * overflow. */
        result = rb->end - rb->write;
        if(rb->read == rb->buffer) {
            result--;
        }
    } else {
        /* Write pointer is before the read pointer; the region extends to 1 byte before the read
         * pointer. */
        result = rb->read - rb->buffer - 1;
    }

    return result;
}
