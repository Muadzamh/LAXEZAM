package com.capstone.cattleweight;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;

import com.jiangdg.usb.USBMonitor;
import com.jiangdg.uvc.UVCCamera;

import java.util.HashMap;

/**
 * UVC Camera Manager with AndroidUSBCamera Library
 * Uses jiangdongguo/AndroidUSBCamera (native expects com.serenegiant but we use bridge)
 */
public class UvcCameraManager {
    
    private static final String TAG = "UvcCameraManager";
    
    private final Context mContext;
    private USBMonitor mUSBMonitor;
    private UVCCamera mUVCCamera;
    private TextureView mTextureView;
    private Surface mPreviewSurface;
    private boolean mIsConnected = false;
    private boolean mIsPreviewing = false;
    private UvcCameraCallback mCallback;
    
    // Preview settings
    private static final int PREVIEW_WIDTH = 640;
    private static final int PREVIEW_HEIGHT = 480;
    
    public interface UvcCameraCallback {
        void onCameraConnected();
        void onCameraDisconnected();
        void onCameraError(String error);
        void onPreviewStarted();
    }
    
    public UvcCameraManager(Context context) {
        mContext = context.getApplicationContext();
    }
    
    /**
     * Initialize USB monitor and start scanning for UVC devices
     */
    public void initialize(UvcCameraCallback callback) {
        mCallback = callback;
        
        if (mUSBMonitor != null) {
            mUSBMonitor.unregister();
        }
        
        mUSBMonitor = new USBMonitor(mContext, new USBMonitor.OnDeviceConnectListener() {
            @Override
            public void onAttach(UsbDevice device) {
                Log.i(TAG, "USB device attached: " + device.getProductName());
                // Don't auto-request - wait for user to toggle switch
                if (mCallback != null) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        mCallback.onCameraConnected()
                    );
                }
            }
            
            @Override
            public void onDetach(UsbDevice device) {
                Log.i(TAG, "USB device detached: " + device.getProductName());
                if (mUVCCamera != null) {
                    closeCamera();
                }
                mIsConnected = false;
                if (mCallback != null) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        mCallback.onCameraDisconnected()
                    );
                }
            }
            
            @Override
            public void onConnect(UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock, boolean createNew) {
                Log.i(TAG, "USB device connected: " + device.getProductName());
                openCamera(device, ctrlBlock);
            }
            
            @Override
            public void onDisconnect(UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock) {
                Log.i(TAG, "USB device disconnected");
                closeCamera();
            }
            
            @Override
            public void onCancel(UsbDevice device) {
                Log.w(TAG, "USB permission cancelled for: " + device.getProductName());
                if (mCallback != null) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        mCallback.onCameraError("Permission denied for USB camera")
                    );
                }
            }
        });
        
        mUSBMonitor.register();
        
        // Check for already connected devices
        java.util.List<UsbDevice> devices = mUSBMonitor.getDeviceList();
        if (!devices.isEmpty()) {
            Log.i(TAG, "Found " + devices.size() + " UVC device(s)");
            // Don't auto-request - let user toggle switch first
            if (mCallback != null) {
                mCallback.onCameraConnected();
            }
        } else {
            Log.w(TAG, "No UVC devices found");
        }
    }
    
    /**
     * List all USB devices connected to the phone
     * This method shows how to get UsbManager and list all USB devices
     */
    public void listAllUsbDevices() {
        // Get UsbManager from system service
        UsbManager usbManager = (UsbManager) mContext.getSystemService(Context.USB_SERVICE);
        
        if (usbManager == null) {
            Log.e(TAG, "UsbManager is null - USB service not available");
            return;
        }
        
        // Get all USB devices
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        
        if (deviceList.isEmpty()) {
            Log.i(TAG, "No USB devices found");
            return;
        }
        
        Log.i(TAG, "========== USB DEVICES LIST ==========");
        Log.i(TAG, "Total USB devices connected: " + deviceList.size());
        
        int index = 1;
        for (UsbDevice device : deviceList.values()) {
            Log.i(TAG, "--- Device " + index + " ---");
            Log.i(TAG, "  Device Name: " + device.getDeviceName());
            Log.i(TAG, "  Product Name: " + device.getProductName());
            Log.i(TAG, "  Manufacturer: " + device.getManufacturerName());
            Log.i(TAG, "  Vendor ID: " + device.getVendorId() + " (0x" + Integer.toHexString(device.getVendorId()) + ")");
            Log.i(TAG, "  Product ID: " + device.getProductId() + " (0x" + Integer.toHexString(device.getProductId()) + ")");
            Log.i(TAG, "  Device ID: " + device.getDeviceId());
            Log.i(TAG, "  Device Class: " + device.getDeviceClass() + " (" + getDeviceClassName(device.getDeviceClass()) + ")");
            Log.i(TAG, "  Subclass: " + device.getDeviceSubclass());
            Log.i(TAG, "  Protocol: " + device.getDeviceProtocol());
            Log.i(TAG, "  Interface Count: " + device.getInterfaceCount());
            
            // Log interface details for combo devices
            if (device.getInterfaceCount() > 0) {
                for (int i = 0; i < device.getInterfaceCount(); i++) {
                    android.hardware.usb.UsbInterface iface = device.getInterface(i);
                    Log.i(TAG, "    Interface " + i + ":");
                    Log.i(TAG, "      Class: " + iface.getInterfaceClass() + " (" + getInterfaceClassName(iface.getInterfaceClass()) + ")");
                    Log.i(TAG, "      Subclass: " + iface.getInterfaceSubclass());
                    Log.i(TAG, "      Protocol: " + iface.getInterfaceProtocol());
                }
            }
            index++;
        }
        Log.i(TAG, "======================================");
    }
    
    private String getDeviceClassName(int classCode) {
        switch (classCode) {
            case 0: return "Device (Interface defined)";
            case 2: return "Communications";
            case 9: return "Hub";
            case 14: return "Video";
            case 255: return "Vendor Specific";
            default: return "Unknown";
        }
    }
    
    private String getInterfaceClassName(int classCode) {
        switch (classCode) {
            case 0: return "Undefined";
            case 2: return "Communications";
            case 10: return "CDC-Data";
            case 14: return "Video (UVC)";
            case 255: return "Vendor Specific";
            default: return "Unknown (" + classCode + ")";
        }
    }
    
    /**
     * Set preview TextureView
     */
    public void setPreviewTexture(TextureView textureView) {
        mTextureView = textureView;
        
        if (mTextureView != null) {
            mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                    Log.d(TAG, "SurfaceTexture available: " + width + "x" + height);
                    mPreviewSurface = new Surface(surface);
                    if (mUVCCamera != null && mIsConnected) {
                        startPreviewInternal();
                    }
                }
                
                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                    Log.d(TAG, "SurfaceTexture size changed: " + width + "x" + height);
                }
                
                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                    Log.d(TAG, "SurfaceTexture destroyed");
                    if (mPreviewSurface != null) {
                        mPreviewSurface.release();
                        mPreviewSurface = null;
                    }
                    return true;
                }
                
                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                    // Called every frame - don't log here
                }
            });
        }
    }
    
    /**
     * Open UVC camera
     */
    private void openCamera(UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock) {
        try {
            // Validate device first - skip ONLY pure serial devices
            if (device != null) {
                int vendorId = device.getVendorId();
                int productId = device.getProductId();
                int deviceClass = device.getDeviceClass();
                String productName = device.getProductName();
                
                Log.i(TAG, "🔍 Attempting to open device: " + productName);
                Log.i(TAG, "   Vendor ID: " + vendorId + " (0x" + Integer.toHexString(vendorId) + ")");
                Log.i(TAG, "   Product ID: " + productId + " (0x" + Integer.toHexString(productId) + ")");
                Log.i(TAG, "   Device Class: " + deviceClass);
                Log.i(TAG, "   Interface Count: " + device.getInterfaceCount());
                
                // ONLY skip CH340/CH341 USB Serial (pure serial, no video)
                // VID 6790 (0x1A86) + Class 255 = Pure serial controller
                // Don't show error - just skip silently and let library try other devices
                if (vendorId == 6790 || vendorId == 0x1A86) {
                    if (deviceClass == 255) {
                        Log.i(TAG, "⏭️ Skipping USB Serial device (CH340 - for control, not video). Will try other devices...");
                        return;  // Skip silently, don't call error callback
                    }
                }
                
                // IMPORTANT: Do NOT skip Class 9 (Hub)!
                // Some USB cameras are COMBO devices:
                //   - Top level: Class 9 Hub (VID:3585 PID:2)
                //   - Child interface: Class 14 UVC Video (inside the hub)
                // UVCCamera library can find UVC interface inside the hub automatically
                
                // ONLY skip other Class 255 devices (pure serial/vendor specific)
                if (deviceClass == 255 && vendorId != 3585) {
                    Log.i(TAG, "⏭️ Skipping Class 255 device (Vendor Specific). Will try other devices...");
                    return;  // Skip silently
                }
                
                Log.i(TAG, "✅ Device validation passed. Proceeding to open...");
                
                // Device Class 9 (Hub) is OK - might contain UVC interface inside
                // Device Class 14 (Video) is OK - direct UVC camera
                // Let UVCCamera library handle interface detection
            }
            
            // Validate ctrlBlock
            if (ctrlBlock == null) {
                Log.e(TAG, "UsbControlBlock is null, cannot open camera");
                if (mCallback != null) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        mCallback.onCameraError("Camera connection failed")
                    );
                }
                return;
            }
            
            // Check if ctrlBlock has valid connection
            if (ctrlBlock.getConnection() == null) {
                Log.e(TAG, "UsbControlBlock has no connection - device not opened properly");
                if (mCallback != null) {
                    new Handler(Looper.getMainLooper()).post(() -> 
                        mCallback.onCameraError("Camera not compatible (no USB interfaces detected). Try reconnecting the USB hub.")
                    );
                }
                return;
            }
            
            // NOTE: Device Class 9 (Hub) with Interface Count 0 is NORMAL for some cameras!
            // GroundChat and similar cameras appear as Hub at top level
            // But UVCCamera library can find the UVC interface automatically
            // So we DON'T block Class 9 devices - let the library try
            
            // Log device info from ctrlBlock
            Log.i(TAG, "✅ Opening UVC camera with UsbControlBlock...");
            
            // CRITICAL FIX: Close previous camera instance if exists
            if (mUVCCamera != null) {
                Log.w(TAG, "⚠️ Camera already exists! Closing previous instance first...");
                try {
                    mUVCCamera.close();
                    mUVCCamera.destroy();
                } catch (Exception e) {
                    Log.e(TAG, "Error closing previous camera", e);
                }
                mUVCCamera = null;
                mIsConnected = false;
            }
            
            mUVCCamera = new UVCCamera();
            
            // AndroidUSBCamera's open() throws exception if fails
            mUVCCamera.open(ctrlBlock);
            
            mIsConnected = true;
            Log.i(TAG, "UVC camera opened successfully!");
            
            if (mCallback != null) {
                new Handler(Looper.getMainLooper()).post(() -> 
                    mCallback.onCameraConnected()
                );
            }
            
            // Start preview if surface is ready
            if (mPreviewSurface != null) {
                startPreviewInternal();
            }
            
        } catch (UnsupportedOperationException e) {
            String errorMsg = e.getMessage();
            Log.e(TAG, "❌ Camera not supported: " + errorMsg, e);
            
            // Provide user-friendly error message based on error code
            String userMsg = "Camera not compatible with this UVC library";
            
            if (errorMsg != null) {
                if (errorMsg.contains("result=-50")) {
                    userMsg = "This USB device is not a UVC camera. GroundChat is for LiDAR, not camera. Please connect a proper USB camera.";
                } else if (errorMsg.contains("result=-99")) {
                    // Error -99 means "No UVC interface found"
                    // This commonly happens with USB Hub-based cameras when the hub's child interfaces
                    // are not fully enumerated by Android
                    if (device != null && device.getDeviceClass() == 9) {
                        userMsg = "USB Hub Camera detected but not supported by current library.\n\n" +
                                "WORKAROUND OPTIONS:\n" +
                                "1. Use a DIRECT UVC camera (not through USB hub)\n" +
                                "2. Replug the USB hub and wait 5-10 seconds before toggling camera\n" +
                                "3. Try a different USB camera that connects directly (not via hub)\n\n" +
                                "Technical: Library cannot enumerate UVC interface inside USB Hub (VID:" + 
                                device.getVendorId() + " PID:" + device.getProductId() + ")";
                    } else {
                        userMsg = "Camera device found but no UVC video interface detected (error -99). " +
                                "Please ensure this is a UVC-compatible USB camera.";
                    }
                } else {
                    userMsg = "Camera error: Device may not be compatible with UVC standard";
                }
            }
            
            if (mCallback != null) {
                final String finalMsg = userMsg;
                new Handler(Looper.getMainLooper()).post(() -> 
                    mCallback.onCameraError(finalMsg)
                );
            }
        } catch (Exception e) {
            Log.e(TAG, "Error opening camera", e);
            if (mCallback != null) {
                new Handler(Looper.getMainLooper()).post(() -> 
                    mCallback.onCameraError("Camera error: " + e.getMessage())
                );
            }
        }
    }
    
    /**
     * Start camera preview
     */
    public void startPreview() {
        if (!mIsConnected || mUVCCamera == null) {
            Log.w(TAG, "Cannot start preview - camera not connected");
            return;
        }
        
        if (mPreviewSurface == null) {
            Log.w(TAG, "Preview surface not ready yet");
            return;
        }
        
        startPreviewInternal();
    }
    
    private void startPreviewInternal() {
        if (mUVCCamera == null || mPreviewSurface == null) {
            Log.w(TAG, "Cannot start preview - camera or surface not ready");
            return;
        }
        
        try {
            // Set preview surface
            mUVCCamera.setPreviewDisplay(mPreviewSurface);
            Log.d(TAG, "Preview display set");
            
            // Set preview size (try MJPEG format first, fallback to YUYV)
            mUVCCamera.setPreviewSize(PREVIEW_WIDTH, PREVIEW_HEIGHT, UVCCamera.FRAME_FORMAT_MJPEG);
            Log.d(TAG, "Preview size set: " + PREVIEW_WIDTH + "x" + PREVIEW_HEIGHT);
            
            // Start preview
            mUVCCamera.startPreview();
            
            mIsPreviewing = true;
            Log.i(TAG, "Preview started successfully");
            
            if (mCallback != null) {
                new Handler(Looper.getMainLooper()).post(() -> 
                    mCallback.onPreviewStarted()
                );
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting preview", e);
            if (mCallback != null) {
                new Handler(Looper.getMainLooper()).post(() -> 
                    mCallback.onCameraError("Preview error: " + e.getMessage())
                );
            }
        }
    }
    
    /**
     * Stop camera preview
     */
    public void stopPreview() {
        if (mUVCCamera != null && mIsPreviewing) {
            try {
                mUVCCamera.stopPreview();
                mIsPreviewing = false;
                Log.i(TAG, "Preview stopped");
            } catch (Exception e) {
                Log.e(TAG, "Error stopping preview", e);
            }
        }
    }
    
    /**
     * Close camera (public method for external calls)
     */
    public void closeCamera() {
        Log.i(TAG, "Closing UVC camera...");
        stopPreview();
        
        if (mUVCCamera != null) {
            try {
                mUVCCamera.close();
                mUVCCamera.destroy();
                mUVCCamera = null;
                mIsConnected = false;
                Log.i(TAG, "✅ UVC Camera closed successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error closing camera", e);
                mUVCCamera = null;
                mIsConnected = false;
            }
        } else {
            Log.d(TAG, "Camera already closed or was never opened");
        }
    }
    
    /**
     * Release all resources
     */
    public void release() {
        closeCamera();
        
        if (mUSBMonitor != null) {
            try {
                mUSBMonitor.unregister();
                mUSBMonitor = null;
            } catch (Exception e) {
                Log.e(TAG, "Error releasing USBMonitor", e);
            }
        }
        
        if (mPreviewSurface != null) {
            try {
                mPreviewSurface.release();
                mPreviewSurface = null;
            } catch (Exception e) {
                Log.e(TAG, "Error releasing preview surface", e);
            }
        }
        
        mCallback = null;
        Log.i(TAG, "Released");
    }
    
    /**
     * Capture still image (placeholder - requires additional implementation)
     */
    public Bitmap captureStillImage() {
        if (!mIsConnected || !mIsPreviewing) {
            Log.w(TAG, "Cannot capture - camera not ready");
            return null;
        }
        
        // For now, capture from TextureView
        if (mTextureView != null) {
            try {
                return mTextureView.getBitmap();
            } catch (Exception e) {
                Log.e(TAG, "Error capturing from TextureView", e);
                return null;
            }
        }
        
        return null;
    }
    
    // Getters
    public boolean isConnected() {
        return mIsConnected;
    }
    
    public boolean isPreviewing() {
        return mIsPreviewing;
    }
    
    /**
     * Manually request permission (called from user action like toggle switch)
     */
    public void requestCameraPermission() {
        if (mUSBMonitor == null) {
            Log.w(TAG, "USB Monitor not initialized");
            return;
        }
        
        java.util.List<UsbDevice> devices = mUSBMonitor.getDeviceList();
        if (!devices.isEmpty()) {
            Log.i(TAG, "Requesting permission for UVC camera");
            
            // Find first NON-SERIAL device (skip CH340 and other pure serial devices)
            UsbDevice targetDevice = null;
            for (UsbDevice device : devices) {
                int vendorId = device.getVendorId();
                int deviceClass = device.getDeviceClass();
                
                // Skip CH340/CH341 USB Serial (VID 6790 or 0x1A86, Class 255)
                if ((vendorId == 6790 || vendorId == 0x1A86) && deviceClass == 255) {
                    Log.i(TAG, "  Skipping serial device: " + device.getProductName() + 
                          " (VID:" + vendorId + " Class:" + deviceClass + ")");
                    continue;
                }
                
                // Skip other pure vendor-specific devices (Class 255), except our camera hub
                if (deviceClass == 255 && vendorId != 3585) {
                    Log.i(TAG, "  Skipping vendor-specific device: " + device.getProductName());
                    continue;
                }
                
                // This device looks like a camera - use it!
                targetDevice = device;
                Log.i(TAG, "  Selected device: " + device.getManufacturerName() + 
                      " (VID:" + vendorId + " PID:" + device.getProductId() + 
                      " Class:" + deviceClass + ")");
                break;
            }
            
            if (targetDevice != null) {
                mUSBMonitor.requestPermission(targetDevice);
            } else {
                Log.w(TAG, "No suitable UVC camera found (only serial devices detected)");
                if (mCallback != null) {
                    mCallback.onCameraError("No USB camera found. Please connect a UVC camera.");
                }
            }
        } else {
            Log.w(TAG, "No UVC devices found");
            if (mCallback != null) {
                mCallback.onCameraError("No USB camera connected");
            }
        }
    }
}
