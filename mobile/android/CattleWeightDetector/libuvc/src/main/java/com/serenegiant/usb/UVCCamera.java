/*
 * BRIDGE CLASS for Native Library Compatibility
 * 
 * Native library libUVCCamera.so expects class at package com.serenegiant.usb.UVCCamera
 * But actual implementation is at com.jiangdg.uvc.UVCCamera
 * 
 * This class exists ONLY to satisfy JNI class lookup!
 * It simply extends the real implementation.
 */
package com.serenegiant.usb;

/**
 * Bridge class that native library will find and load
 * Extends the actual implementation from jiangdg package
 */
public class UVCCamera extends com.jiangdg.uvc.UVCCamera {
    // No implementation needed - just inheritance
    // Native code will call methods from parent class
}
