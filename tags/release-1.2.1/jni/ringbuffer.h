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
#ifndef RINGBUFFER_H
#define RINGBUFFER_H

struct ringbuffer {
    /* Data buffer */
    unsigned char *buffer;

    /* Buffer end (i.e. *(end - 1) is okay but *end is invalid) */
    unsigned char *end;

    /* Read pointer */
    unsigned char *read;

    /* Write pointer */
    unsigned char *write;
};


/* Create a new ringbuffer. */
struct ringbuffer *ringbuffer_create(int size);

/* Free the ringbuffer data and its structure. */
void ringbuffer_destroy(struct ringbuffer *rb);

/* Return 1 if the buffer is empty, or 0 if not empty. */
int ringbuffer_empty(struct ringbuffer *rb);

/* Return 1 if the buffer is full, or 0 if not full. */
int ringbuffer_full(struct ringbuffer *rb);

/* Return the number of bytes of data written to this ring buffer. */
int ringbuffer_length(struct ringbuffer *rb);

/* Make sure there is a contiguous buffer of at least n bytes after the read pointer.  The buffer
 * may be reorganized to make this true.
 *
 * Return 1 on error, 0 on success. */
int ringbuffer_require_contiguous_read(struct ringbuffer *rb, int n);

/* Reorganize the ring buffer such that the read pointer is at the start of memory.
 *
 * Return 1 on error, 0 on success. */
int ringbuffer_realign(struct ringbuffer *rb);

/* Return the number of contiguous bytes available to write to. */
int ringbuffer_available_contiguous_read(struct ringbuffer *rb);

/* Return the number of contiguous bytes available to write to. */
int ringbuffer_available_contiguous_write(struct ringbuffer *rb);

#endif
