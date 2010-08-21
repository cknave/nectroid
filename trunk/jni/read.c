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

#include <errno.h>
#include <unistd.h>

#include "logmacros.h"
#include "read.h"

/* Read in a loop until at_least bytes have been read or at_most have been read. 
 * Return the number of bytes read, or -1 for error. */
int read_loop(int fd, unsigned char *buffer, int at_least, int at_most, int *abort_flag)
{
    int total_bytes_read = 0;
    int error = 0;

    /* Keep reading until we get at_least bytes. */
    while(!error && (total_bytes_read < at_least)) {
        int bytes_to_read;
        int num_bytes_read;

        if(abort_flag && (*abort_flag)) {
            LOGI("Aborted read");
            error = 1;
            break;
        }

        bytes_to_read = at_most - total_bytes_read;
        num_bytes_read = read(fd, buffer, bytes_to_read);

        if(num_bytes_read < 0) {
            LOGE("Error while reading MP3: %s", strerror(errno));
            error = 1;
        } else if(num_bytes_read == 0) {
            LOGE("EOF while reading MP3");
            error = 1;
        } else {
            total_bytes_read += num_bytes_read;
            buffer += num_bytes_read;
        }
    }

    if(error) {
        return -1;
    } else {
        return total_bytes_read;
    }
}


/* Read exactly length bytes into buffer, calling read() multiple times if necessary. */
int read_fully(int fd, unsigned char *buffer, int length, int *abort_flag)
{
    int total = read_loop(fd, buffer, length, length, abort_flag);
    if(total == -1)
        return 1;
    else
        return 0;
}
