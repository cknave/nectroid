LOCAL_PATH := $(call my-dir)
LIBMAD := libmad-0.15.1b

# Build libmad as a static library.
include $(CLEAR_VARS)

LOCAL_MODULE    := libmad
LOCAL_CFLAGS    := -DASO_INTERLEAVE1 -DASO_IMDCT -DFPM_ARM -Wall
LOCAL_SRC_FILES := $(LIBMAD)/version.c $(LIBMAD)/fixed.c $(LIBMAD)/bit.c $(LIBMAD)/timer.c \
                   $(LIBMAD)/stream.c $(LIBMAD)/frame.c $(LIBMAD)/synth.c $(LIBMAD)/decoder.c \
                   $(LIBMAD)/layer12.c $(LIBMAD)/layer3.c $(LIBMAD)/huffman.c \
                   $(LIBMAD)/imdct_l_arm.S
LOCAL_ARM_MODE  := arm

include $(BUILD_STATIC_LIBRARY)


# Build libmp3streamer as a shared library.
include $(CLEAR_VARS)

LOCAL_MODULE    := libmp3streamer
LOCAL_CFLAGS    := -I$(LIBMAD) -Wall
LOCAL_SRC_FILES := abort.c http.c read.c ringbuffer.c ringbuffer_jni.c streamer.c

LOCAL_STATIC_LIBRARIES := libmad
LOCAL_LDLIBS := -llog

include $(BUILD_SHARED_LIBRARY)
