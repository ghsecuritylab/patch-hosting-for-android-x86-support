ifeq ($(PV_OSCL_LIB), true)
LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
	src/oscl_shared_library.cpp \
	src/oscl_library_list.cpp



LOCAL_MODULE := liboscllib

LOCAL_CFLAGS :=   $(PV_CFLAGS)


LOCAL_C_INCLUDES := \
	$(PV_TOP)//oscl/oscl/oscllib/src \
	$(PV_TOP)//oscl/oscl/oscllib/src \
	$(PV_TOP)//oscl/oscl/oscllib/../osclio/src \
	$(PV_INCLUDES) 


LOCAL_COPY_HEADERS_TO := $(PV_COPY_HEADERS_TO)
LOCAL_COPY_HEADERS := \
	src/oscl_shared_lib_interface.h \
	src/oscl_shared_library.h \
	src/oscl_library_common.h \
	src/oscl_library_list.h

include $(BUILD_STATIC_LIBRARY)
endif  # PV_OSCL_LIB