//
// VirtualApp Native Project
//

#ifndef FOUNDATION_PATH
#define FOUNDATION_PATH


#include <jni.h>
#include <dlfcn.h>
#include <stddef.h>
#include <fcntl.h>
#include <sys/system_properties.h>

#include <fb/include/fb/ALog.h>
#include <fb/include/fb/fbjni.h>
#include "Jni/Helper.h"

#include <stdlib.h>
#include <memory.h>

using namespace facebook::jni;

#define HOOK_METHODS_COUNT  10

enum METHODS {
    OPEN_DEX = 0, CAMERA_SETUP, AUDIO_NATIVE_CHECK_PERMISSION
};

void hookAndroidVM(JArrayClass<jobject> javaMethods,
                   jstring packageName, jboolean isArt, jint apiLevel, jint cameraMethodType);

void *getDvmOrArtSOHandle();

void hookMethod(JArrayClass<jobject> srcMethods, JArrayClass<jobject> destMethods);
void backupMethod(JArrayClass<jobject> srcMethods);
void callMethod(jobject viewRootImpl, jint index, jobject queuedInputEvent);

#endif //NDK_HOOK_NATIVE_H
