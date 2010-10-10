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
#include <netdb.h>
#include <netinet/in.h>
#include <sys/select.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <string.h>
#include <unistd.h>

#include "abort.h"
#include "logmacros.h"
#include "read.h"
#include "ringbuffer_jni.h"

/*
 * Forward declarations
 */

static int read_line(int sockfd, char *buffer, int max_size);
static int is_line_blank(char *line);


/*
 * Public interface
 */

/* Open a socket to this MP3Streamer's stream. */
JNIEXPORT jint JNICALL Java_com_kvance_Nectroid_MP3Streamer_openSocket
    (JNIEnv *env, jobject obj, jstring host, jint port)
{
    int error = 0;
    int sock = -1;
    const char *host_cstr = NULL;
    struct hostent *resolved_host = NULL;
    struct sockaddr_in host_addr;
    int rc = -1;

    /* Create the socket. */
    sock = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if(sock == -1) {
        LOGE("Failed to create socket: %s", strerror(errno));
        error = 1;
    }

    /* Decode the hostname string. */
    if(!error) {
        host_cstr = (*env)->GetStringUTFChars(env, host, NULL);
        if(host_cstr == NULL) {
            LOGE("Out of memory decoding host string");
            error = 1;
        }
    }

    /* Resolve the hostname. */
    if(!error) {
        resolved_host = gethostbyname(host_cstr);
        if(resolved_host == NULL) {
            LOGE("Failed to resolve host \"%s\": %d", host_cstr, h_errno);
            error = 1;
        }
    }

    /* Connect to the host! */
    if(!error) {
        host_addr.sin_family = AF_INET;
        host_addr.sin_port = htons(port);
        memcpy(&host_addr.sin_addr, resolved_host->h_addr, resolved_host->h_length);
        rc = connect(sock, (struct sockaddr *)&host_addr, sizeof(host_addr));
        if(rc == -1) {
            error = 1;
        }
    }

    /* Clean up. */
    if(host_cstr != NULL) {
        (*env)->ReleaseStringUTFChars(env, host, host_cstr);
    }
    if(error && sock != -1) {
        close(sock);
    }

    /* Return the socket, or an error code. */
    if(!error) {
        LOGI("Connected to streaming host");
        return sock;
    } else {
        return -1;
    }
}


/* Close this socket. */
JNIEXPORT void JNICALL Java_com_kvance_Nectroid_MP3Streamer_closeSocket
    (JNIEnv *env, jobject obj, jint sock)
{
    shutdown(sock, SHUT_RDWR);
    close(sock);
}


/* Block until there is data to read on this socket. */
JNIEXPORT jboolean JNICALL Java_com_kvance_Nectroid_MP3Streamer_waitForReadable
    (JNIEnv *env, jobject obj, jint sock)
{
    fd_set rfds;
    struct timeval timeout;
    int rc = 0;
    int error = 0;

    // Set timeout to 10 seconds.
    timeout.tv_sec = 10;
    timeout.tv_usec = 0;

    FD_ZERO(&rfds);
    FD_SET(sock, &rfds);
    rc = select(sock + 1, &rfds, NULL, NULL, &timeout);
    if(rc == -1) {
        LOGE("Error waiting to read MP3: %s", strerror(errno));
        error = 1;
    } else if(rc == 0) {
        LOGE("Timed out waiting to read MP3");
        error = 1;
    }

    return error ? JNI_TRUE : JNI_FALSE;
}


/* Read any amount of data into this MP3 ringbuffer. */
JNIEXPORT jboolean JNICALL Java_com_kvance_Nectroid_MP3Streamer_readIntoMP3Buffer
    (JNIEnv *env, jobject streamer, jint sock, jobject ringbuffer_obj)
{
    int error = 0;

    struct ringbuffer *rbuf = get_local_ringbuffer(env, ringbuffer_obj);
    if(!rbuf) {
        error = 1;
    }
    
    if(!error) {
        error = read_into_ringbuffer(sock, rbuf);
    }

    return error ? JNI_TRUE : JNI_FALSE;
}


/* Send the HTTP request. */
JNIEXPORT jboolean JNICALL Java_com_kvance_Nectroid_MP3Streamer_sendHttpRequest
    (JNIEnv *env, jobject obj, jstring path, jint sock)
{
    int error = 0;
    const char *path_cstr = NULL;
    char *req_buffer = NULL;
    const char *req_begin_part = "GET ";
    const char *req_end_part = " HTTP/1.0\r\n\r\n";
    ssize_t bytes_sent;
    char line_buffer[256];

    /* Decode the hostname string. */
    path_cstr = (*env)->GetStringUTFChars(env, path, NULL);
    if(path_cstr == NULL) {
        LOGE("Out of memory decoding path string");
        error = 1;
    }

    /* Allocate the HTTP request buffer. */
    if(!error) {
        req_buffer = malloc(strlen(req_begin_part) + strlen(path_cstr) + strlen(req_end_part));
        if(req_buffer == NULL) {
            LOGE("Out of memory allocating HTTP request buffer");
            error = 1;
        }
    }

    /* Build the HTTP request. */
    if(!error) {
        strcpy(req_buffer, req_begin_part);
        strcat(req_buffer, path_cstr);
        strcat(req_buffer, req_end_part);
    }

    /* Send the HTTP request. */
    if(!error) {
        bytes_sent = write(sock, req_buffer, strlen(req_buffer));
        if(bytes_sent != strlen(req_buffer)) {
            LOGE("Failed to send HTTP request: %s", strerror(errno));
            error = 1;
        }
    }

    if(!error) {
        LOGI("Sent HTTP request");
    }

    /* Read the response. */
    if(!error) {
        error = read_line(sock, line_buffer, sizeof(line_buffer));
    }

    /* Log the response. */
    if(!error) {
        LOGI("%s", line_buffer);
    }

    /* Check for a 200 (OK) response code. */
    if(!error) {
        if(strlen(line_buffer) < strlen("HTTP/1.0 200")) {
            LOGE("HTTP response too short");
            error = 1;
        }
        if(strncmp(line_buffer + strlen("HTTP/1.0 "), "200", strlen("200")) != 0) {
            LOGE("HTTP response not OK");
            error = 1;
        }
    }

    /* Read the rest of the headers, stopping at the blank line. */
    if(!error) {
        do {
            error = read_line(sock, line_buffer, sizeof(line_buffer));
            if(g_abort) {
                LOGI("Aborting HTTP read");
                error = 1;
            }
        } while(!error && !is_line_blank(line_buffer));
    }

    /* Clean up. */
    if(req_buffer != NULL) {
        free(req_buffer);
    }
    if(path_cstr != NULL) {
        (*env)->ReleaseStringUTFChars(env, path, path_cstr);
    }

    /* Return error boolean. */
    return error ? JNI_TRUE : JNI_FALSE;
}


/*
 * Utility functions
 */

/* Read a line one byte at a time, stopping when the buffer is full. */
static int read_line(int sockfd, char *buffer, int max_size)
{
    int i;
    int error = 0;

    /* Read bytes until we run out of buffer.  Leave room for the \0 at the end. */
    for(i = 0; i < (max_size - 1); i++) {
        size_t bytes_read = read(sockfd, buffer + i, 1);
        if(bytes_read != 1) {
            error = 1;
            break;
        }
        if(buffer[i] == '\n') {
            i++;
            break;
        }
    }

    /* Add null-termination. */
    if(!error) {
        buffer[i] = '\0';
    }

    return error;
}

/* Return 1 if this line is blank. */
static int is_line_blank(char *line)
{
    if(line[0] == '\n')
        return 1;
    if(line[0] == '\r' && line[1] == '\n')
        return 1;
    return 0;
}
