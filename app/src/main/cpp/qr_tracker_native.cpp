#include <jni.h>
#include <string>
#include <vector>
#include <thread>
#include <chrono>
#include <dlfcn.h>
#include <android/log.h>

// Android XR platform define must come BEFORE openxr headers so
// JavaVM and AAssetManager become available in openxr_platform.h
#define XR_USE_PLATFORM_ANDROID
#include <openxr/openxr.h>
#include <openxr/openxr_platform.h>

#define LOG_TAG "QRTrackerNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define CHK_XR(res) if (XR_FAILED(res)) { LOGE("OpenXR Call failed: %d at %s:%d", res, __FILE__, __LINE__); }

bool threadRunning = false;
bool trackingEnabled = true;

// Global references
JavaVM* gJvm = nullptr;
PFN_xrGetInstanceProcAddr pfn_xrGetInstanceProcAddr = nullptr;
PFN_xrCreateTrackableTrackerANDROID pfn_xrCreateTrackableTrackerANDROID = nullptr;
PFN_xrGetAllTrackablesANDROID pfn_xrGetAllTrackablesANDROID = nullptr;
PFN_xrGetTrackableQrCodeANDROID pfn_xrGetTrackableQrCodeANDROID = nullptr;
PFN_xrDestroyTrackableTrackerANDROID pfn_xrDestroyTrackableTrackerANDROID = nullptr;
PFN_xrCreateReferenceSpace pfn_xrCreateReferenceSpace = nullptr;
PFN_xrDestroySpace pfn_xrDestroySpace = nullptr;

XrInstance gInstance = XR_NULL_HANDLE;
XrSession gSession = XR_NULL_HANDLE;

bool LoadOpenXRExtensions(XrInstance instance) {
    if (pfn_xrCreateTrackableTrackerANDROID) {
        return true; // Already loaded
    }

    // CRITICAL: Do NOT use xrGetInstanceProcAddr from our linked Khronos loader.
    // Jetpack XR has already loaded its own OpenXR runtime into process space.
    // We must get xrGetInstanceProcAddr from THAT runtime via RTLD_DEFAULT.
    pfn_xrGetInstanceProcAddr = (PFN_xrGetInstanceProcAddr)dlsym(RTLD_DEFAULT, "xrGetInstanceProcAddr");
    if (!pfn_xrGetInstanceProcAddr) {
        // Fallback: try to open the Android XR runtime directly
        void* xrLib = dlopen("libopenxr_runtime_android.so", RTLD_NOW | RTLD_NOLOAD);
        if (!xrLib) xrLib = dlopen("libandroid_xr.so", RTLD_NOW | RTLD_NOLOAD);
        if (xrLib) {
            pfn_xrGetInstanceProcAddr = (PFN_xrGetInstanceProcAddr)dlsym(xrLib, "xrGetInstanceProcAddr");
        }
    }
    if (!pfn_xrGetInstanceProcAddr) {
        LOGE("xrGetInstanceProcAddr is null — Android XR runtime not in process space?");
        return false;
    }
    LOGI("xrGetInstanceProcAddr resolved: %p", pfn_xrGetInstanceProcAddr);

    auto loadProc = [&](const char* name, auto& pfn) -> bool {
        XrResult res = pfn_xrGetInstanceProcAddr(instance, name, (PFN_xrVoidFunction*)&pfn);
        if (XR_FAILED(res)) LOGE("Failed to load %s (XrResult=%d)", name, res);
        return XR_SUCCEEDED(res);
    };

    bool ok = true;
    ok &= loadProc("xrCreateTrackableTrackerANDROID", pfn_xrCreateTrackableTrackerANDROID);
    ok &= loadProc("xrGetAllTrackablesANDROID",       pfn_xrGetAllTrackablesANDROID);
    ok &= loadProc("xrGetTrackableQrCodeANDROID",     pfn_xrGetTrackableQrCodeANDROID);
    ok &= loadProc("xrDestroyTrackableTrackerANDROID",pfn_xrDestroyTrackableTrackerANDROID);
    ok &= loadProc("xrCreateReferenceSpace",          pfn_xrCreateReferenceSpace);
    ok &= loadProc("xrDestroySpace",                  pfn_xrDestroySpace);

    return ok;
}


void QrTrackingLoop(jobject listenerObj) {
    JNIEnv* env;
    gJvm->AttachCurrentThread(&env, NULL);

    XrSystemQrCodeTrackingPropertiesANDROID qrCodeProperty = {.type = XR_TYPE_SYSTEM_QR_CODE_TRACKING_PROPERTIES_ANDROID, .next = nullptr};
    XrTrackableQrCodeConfigurationANDROID configuration =
        {.type = XR_TYPE_TRACKABLE_QR_CODE_CONFIGURATION_ANDROID,
         .next = nullptr,
         .trackingMode = XR_QR_CODE_TRACKING_MODE_DYNAMIC_ANDROID,
         .qrCodeEdgeSize = 0.0f};

    XrTrackableTrackerCreateInfoANDROID createInfo =
        {.type = XR_TYPE_TRACKABLE_TRACKER_CREATE_INFO_ANDROID,
         .next = &configuration,
         .trackableType = XR_TRACKABLE_TYPE_QR_CODE_ANDROID};

    XrTrackableTrackerANDROID qrCodeTracker = XR_NULL_HANDLE;
    XrResult res = pfn_xrCreateTrackableTrackerANDROID(gSession, &createInfo, &qrCodeTracker);
    if (XR_FAILED(res)) {
        LOGE("Failed to create QR Code Tracker: %d", res);
        gJvm->DetachCurrentThread();
        return;
    }

    XrReferenceSpaceCreateInfo spaceCreateInfo = {
        .type = XR_TYPE_REFERENCE_SPACE_CREATE_INFO,
        .next = nullptr,
        .referenceSpaceType = XR_REFERENCE_SPACE_TYPE_LOCAL,
        .poseInReferenceSpace = {{0.0f, 0.0f, 0.0f, 1.0f}, {0.0f, 0.0f, 0.0f}}
    };
    XrSpace appSpace = XR_NULL_HANDLE;
    res = pfn_xrCreateReferenceSpace(gSession, &spaceCreateInfo, &appSpace);
    if (XR_FAILED(res)) {
        LOGE("Failed to create reference space: %d", res);
    }

    jclass listenerClass = env->GetObjectClass(listenerObj);
    jmethodID callbackMethod = env->GetMethodID(listenerClass, "onQrCodeScanned", "(Ljava/lang/String;)V");
    if (!callbackMethod) {
        LOGE("Could not find onQrCodeScanned method");
        gJvm->DetachCurrentThread();
        return;
    }

    LOGI("Started QR Tracking Loop in background thread...");

    while (threadRunning) {
        std::this_thread::sleep_for(std::chrono::milliseconds(200));
        
        if (!trackingEnabled) continue;

        uint32_t qrCodeSize = 0;
        std::vector<XrTrackableANDROID> trackables(10);
        res = pfn_xrGetAllTrackablesANDROID(qrCodeTracker, 10, &qrCodeSize, trackables.data());
        
        if (XR_SUCCEEDED(res) && qrCodeSize > 0) {
            for (uint32_t i = 0; i < qrCodeSize; i++) {
                XrTrackableQrCodeANDROID qrCode = { .type = XR_TYPE_TRACKABLE_QR_CODE_ANDROID };
                qrCode.bufferCountOutput = 0;
                XrTrackableGetInfoANDROID getInfo = {
                    .type = XR_TYPE_TRACKABLE_GET_INFO_ANDROID,
                    .next = nullptr,
                    .trackable = trackables[i],
                    .baseSpace = appSpace,
                    .time = 0 // In real app, time should be prediction time
                };
                
                if (XR_SUCCEEDED(pfn_xrGetTrackableQrCodeANDROID(qrCodeTracker, &getInfo, &qrCode)) && qrCode.bufferCountOutput > 0) {
                    std::vector<char> buffer(qrCode.bufferCountOutput);
                    qrCode.bufferCapacityInput = qrCode.bufferCountOutput;
                    qrCode.buffer = buffer.data();
                    
                    if (XR_SUCCEEDED(pfn_xrGetTrackableQrCodeANDROID(qrCodeTracker, &getInfo, &qrCode))) {
                        std::string qrStr(buffer.data());
                        LOGI("Tracked QR: %s", qrStr.c_str());
                        jstring jqrStr = env->NewStringUTF(qrStr.c_str());
                        env->CallVoidMethod(listenerObj, callbackMethod, jqrStr);
                        env->DeleteLocalRef(jqrStr);
                        
                        // Disable tracking temporarily until user triggers again to avoid UI spam in this mockup
                        trackingEnabled = false; 
                    }
                }
            }
        }
    }

    if (qrCodeTracker != XR_NULL_HANDLE) {
        pfn_xrDestroyTrackableTrackerANDROID(qrCodeTracker);
    }
    if (appSpace != XR_NULL_HANDLE) {
        pfn_xrDestroySpace(appSpace);
    }
    gJvm->DetachCurrentThread();
}

extern "C" JNIEXPORT void JNICALL
Java_com_sherlock_xr_MainActivity_toggleQrTrackerNative(JNIEnv* env, jobject thiz, jboolean enable) {
    trackingEnabled = enable;
    if (enable) {
        LOGI("User triggered Scan. Tracking enabled.");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_sherlock_xr_MainActivity_startQrTrackerNative(JNIEnv* env, jobject thiz, jobject nativeData) {
    LOGI("Bắt đầu khởi tạo OpenXR QR Tracker qua NDK...");
    env->GetJavaVM(&gJvm);

    // === Extract XrInstance and XrSession from androidx.xr.arcore.NativeData ===
    // Based on decompiled source:
    //   private final long nativeSessionPointer;   (primitive)
    //   private final Long nativeInstancePointer;  (nullable object)
    jclass nativeDataClass = env->GetObjectClass(nativeData);

    // Log the actual class name at runtime for debugging
    jclass classClass = env->GetObjectClass(nativeDataClass);
    jmethodID getNameMethod = env->GetMethodID(classClass, "getName", "()Ljava/lang/String;");
    jstring className = (jstring)env->CallObjectMethod(nativeDataClass, getNameMethod);
    const char* classNameStr = env->GetStringUTFChars(className, nullptr);
    LOGI("NativeData class: %s", classNameStr);
    env->ReleaseStringUTFChars(className, classNameStr);
    env->DeleteLocalRef(classClass);

    XrInstance instance = XR_NULL_HANDLE;
    XrSession  session  = XR_NULL_HANDLE;

    // Step 1: Extract nativeSessionPointer (long primitive)
    jmethodID getSessionMethod = env->GetMethodID(nativeDataClass, "getNativeSessionPointer", "()J");
    if (getSessionMethod) {
        session = (XrSession)env->CallLongMethod(nativeData, getSessionMethod);
        LOGI("Extracted XrSession via getter: 0x%llx", (unsigned long long)session);
    } else {
        env->ExceptionClear();
        jfieldID sessionField = env->GetFieldID(nativeDataClass, "nativeSessionPointer", "J");
        if (sessionField) {
            session = (XrSession)env->GetLongField(nativeData, sessionField);
            LOGI("Extracted XrSession via field: 0x%llx", (unsigned long long)session);
        } else {
            env->ExceptionClear();
            LOGE("Cannot find nativeSessionPointer");
        }
    }

    // Step 2: Extract nativeInstancePointer (nullable Long object)
    jmethodID getInstanceMethod = env->GetMethodID(nativeDataClass, "getNativeInstancePointer", "()Ljava/lang/Long;");
    if (getInstanceMethod) {
        jobject instanceObj = env->CallObjectMethod(nativeData, getInstanceMethod);
        if (instanceObj != nullptr) {
            jclass longClass = env->FindClass("java/lang/Long");
            jmethodID longValue = env->GetMethodID(longClass, "longValue", "()J");
            instance = (XrInstance)env->CallLongMethod(instanceObj, longValue);
            env->DeleteLocalRef(longClass);
            env->DeleteLocalRef(instanceObj);
            LOGI("Extracted XrInstance via getter: 0x%llx", (unsigned long long)instance);
        } else {
            LOGE("nativeInstancePointer returned null — extension may not be enabled in this XrInstance");
        }
    } else {
        env->ExceptionClear();
        LOGE("getNativeInstancePointer() not found");
    }

    if (session == XR_NULL_HANDLE) {
        LOGE("ERROR: XrSession is NULL — aborting.");
        return;
    }
    if (instance == XR_NULL_HANDLE) {
        LOGI("WARNING: XrInstance is NULL; will use XR_NULL_HANDLE with session-based lookup");
        // Per OpenXR spec, passing XR_NULL_HANDLE to xrGetInstanceProcAddr is
        // allowed for a subset of functions. Use the session's backing instance.
        instance = gInstance; // may still be null on first call
    }

    LOGI("Using XrInstance: 0x%llx, XrSession: 0x%llx",
         (unsigned long long)instance, (unsigned long long)session);

    // Save globals for the background thread
    gInstance = instance;
    gSession = session;

    if (!LoadOpenXRExtensions(instance)) {
        LOGE("Failed to load required OpenXR tracker extensions");
        return;
    }

    if (threadRunning) return;
    threadRunning = true;
    trackingEnabled = true;
    
    jobject listenerRef = env->NewGlobalRef(thiz);
    std::thread trackingThread([=]() {
        QrTrackingLoop(listenerRef);
        JNIEnv* localEnv;
        gJvm->AttachCurrentThread(&localEnv, NULL);
        localEnv->DeleteGlobalRef(listenerRef);
        gJvm->DetachCurrentThread();
    });
    trackingThread.detach();
}
