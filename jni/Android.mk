LOCAL_PATH := $(call my-dir)
LIBMAD := libmad-0.15.1b

# Build libmad as a static library.
include $(CLEAR_VARS)

LOCAL_MODULE    := libmad
LOCAL_CFLAGS := -DASO_INTERLEAVE1 -DASO_IMDCT -DFPM_ARM
LOCAL_SRC_FILES := $(LIBMAD)/version.c $(LIBMAD)/fixed.c $(LIBMAD)/bit.c $(LIBMAD)/timer.c $(LIBMAD)/stream.c $(LIBMAD)/frame.c \
                   $(LIBMAD)/synth.c.arm $(LIBMAD)/decoder.c $(LIBMAD)/layer12.c.arm $(LIBMAD)/layer3.c.arm $(LIBMAD)/huffman.c \
		   $(LIBMAD)/imdct_l_arm.S

include $(BUILD_SHARED_LIBRARY)
