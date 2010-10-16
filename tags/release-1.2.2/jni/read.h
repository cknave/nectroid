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
#ifndef READ_H
#define READ_H

#include "ringbuffer.h"


/* Read in a loop until at_least bytes have been read or at_most have been read. 
 * Return the number of bytes read, or -1 for error. */
int read_loop(int fd, unsigned char *buffer, int at_least, int at_most, int *abort_flag);


/* Read exactly length bytes into buffer, calling read() multiple times if necessary. */
int read_fully(int fd, unsigned char *buffer, int length, int *abort_flag);


/* Read whatever is available into this ringbuffer.
 *
 * Return 0 on success, 1 on error. */
int read_into_ringbuffer(int fd, struct ringbuffer *rb);

#endif
