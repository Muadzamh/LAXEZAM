package com.capstone.cattleweight;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.TextureView;

import java.util.HashMap;

/**
 * Simplified UVC Camera Manager
 * Detects USB camera dan provides basic functionality
 * 
 * Note: Full UVC implementation memerlukan library native yang complex.
 * Untuk saat ini, kita deteksi camera dan bisa capture dari app external seperti "USB Camera".
 */
public class UvcCameraManager {
    
    private static final String TAG = "UvcCameraManager";
    private static final String ACTION_USB_PERMISSION = "com.capstone.cattleweight.USB_PERMISSION";
    private static final int USB_CLASS_VIDEO = 14;
    
    // GroundChat Camera identifiers
    private static final int GROUNDCHAT_VENDOR_ID = 0x0EDC;
    private static final int GROUNDCHAT_PRODUCT_ID = 0x2050; // UVC class
    
    private Context context;
    private UsbManager usbManager;
    private UsbDevice uvcDevice;
    private UsbDeviceConnection connection;
    private TextureView textureView;
    private boolean isConnected = false;
    private boolean isPreviewing = false;
    
    // Callback interface
    public interface UvcCameraCallback {
        void onCameraConnected();
        void onCameraDisconnected();
        void onCameraError(String error);
        void onPreviewStarted();
    }
    
    private UvcCameraCallback callback;
    
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            Log.d(TAG, "USB permission granted for: " + device.getProductName());
                            openCamera(device);
                        }
                    } else {
                        Log.e(TAG, "USB permission denied");
                        if (callback != null) {
                            new Handler(Looper.getMainLooper()).post(() ->
                                callback.onCameraError("USB permission denied. Please allow USB access.")
                            );
                        }
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null && device.equals(uvcDevice)) {
                    Log.d(TAG, "USB camera detached");
                    closeCamera();
                    if (callback != null) {
                        new Handler(Looper.getMainLooper()).post(() ->
                            callback.onCameraDisconnected()
                        );
                    }
                }
            }
        }
    };
    
    public UvcCameraManager(Context context) {
        this.context = context;
        this.usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
    }
    
    /**
     * Initialize UVC camera
     */
    public void initialize(Object cameraView, UvcCameraCallback callback) {
        this.callback = callback;
        
        if (cameraView instanceof TextureView) {
            this.textureView = (TextureView) cameraView;
        }
        
        // Register USB receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(usbReceiver, filter);
        }
        
        // Check for connected USB cameras
        detectUsbCamera();
    }
    
    /**
     * Detect USB camera devices
     */
    private void detectUsbCamera() {
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        Log.d(TAG, "USB devices found: " + deviceList.size());
        
        for (UsbDevice device : deviceList.values()) {
            Log.d(TAG, "Device: " + device.getDeviceName() + 
                  ", Product: " + device.getProductName() +
                  ", Class: " + device.getDeviceClass() +
                  ", Vendor: " + device.getVendorId() +
                  ", ProductID: " + device.getProductId());
            
            // Check if device is UVC camera
            if (isUvcCamera(device)) {
                Log.d(TAG, "UVC Camera found: " + device.getProductName());
                uvcDevice = device;
                requestPermission(device);
                return;
            }
        }
        
        Log.d(TAG, "No UVC camera found");
    }
    
    /**
     * Check if device is UVC camera
     */
    private boolean isUvcCamera(UsbDevice device) {
        // Explicitly check for GroundChat camera by VID/PID
        if (device.getVendorId() == GROUNDCHAT_VENDOR_ID && 
            device.getProductId() == GROUNDCHAT_PRODUCT_ID) {
            Log.d(TAG, "GroundChat camera detected by VID/PID");
            return true;
        }
        
        // Exclude USB Serial devices (LiDAR)
        // Check device class - if it's vendor specific (0xFF) or CDC (0x02), skip
        int deviceClass = device.getDeviceClass();
        if (deviceClass == 0xFF || deviceClass == 0x02) {
            Log.d(TAG, "Skipping USB Serial device: " + device.getProductName());
            return false;
        }
        
        // Check for "serial" in product name (skip LiDAR)
        String productName = device.getProductName();
        if (productName != null && productName.toLowerCase().contains("serial")) {
            Log.d(TAG, "Skipping USB Serial device: " + productName);
            return false;
        }
        
        // Check device class (14 = Video)
        if (deviceClass == USB_CLASS_VIDEO) {
            return true;
        }
        
        // Check interfaces for video class
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface usbInterface = device.getInterface(i);
            if (usbInterface.getInterfaceClass() == USB_CLASS_VIDEO) {
                return true;
            }
        }
        
        // Check for common USB camera patterns (but not "serial")
        if (productName != null) {
            String lowerName = productName.toLowerCase();
            if ((lowerName.contains("camera") || lowerName.contains("webcam") || lowerName.contains("uvc")) 
                && !lowerName.contains("serial")) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Request USB permission
     */
    private void requestPermission(UsbDevice device) {
        if (usbManager.hasPermission(device)) {
            Log.d(TAG, "USB permission already granted");
            openCamera(device);
        } else {
            Log.d(TAG, "Requesting USB permission for: " + device.getProductName());
            PendingIntent permissionIntent = PendingIntent.getBroadcast(
                context, 0, 
                new Intent(ACTION_USB_PERMISSION), 
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? 
                    PendingIntent.FLAG_MUTABLE : 0
            );
            usbManager.requestPermission(device, permissionIntent);
        }
    }
    
    /**
     * Open camera
     */
    private void openCamera(UsbDevice device) {
        connection = usbManager.openDevice(device);
        if (connection != null) {
            Log.d(TAG, "USB device opened successfully: " + device.getProductName());
            uvcDevice = device;
            isConnected = true;
            if (callback != null) {
                new Handler(Looper.getMainLooper()).post(() ->
                    callback.onCameraConnected()
                );
            }
            startPreview();
        } else {
            Log.e(TAG, "Failed to open USB device");
            if (callback != null) {
                new Handler(Looper.getMainLooper()).post(() ->
                    callback.onCameraError("Failed to open USB device. Check USB connection.")
                );
            }
        }
    }
    
    /**
     * Close camera
     */
    private void closeCamera() {
        stopPreview();
        if (connection != null) {
            connection.close();
            connection = null;
        }
        isConnected = false;
        uvcDevice = null;
    }
    
    /**
     * Start preview
     * Note: Basic implementation - shows placeholder
     */
    public void startPreview() {
        if (isConnected && !isPreviewing) {
            Log.d(TAG, "Starting USB camera preview (limited support)");
            isPreviewing = true;
            if (callback != null) {
                new Handler(Looper.getMainLooper()).post(() ->
                    callback.onPreviewStarted()
                );
            }
            
            // Log device info
            if (uvcDevice != null) {
                Log.i(TAG, "USB Camera active: " + uvcDevice.getProductName() + 
                          " (VID: 0x" + Integer.toHexString(uvcDevice.getVendorId()).toUpperCase() + 
                          ", PID: 0x" + Integer.toHexString(uvcDevice.getProductId()).toUpperCase() + ")");
            }
        }
    }
    
    /**
     * Stop preview
     */
    public void stopPreview() {
        if (isPreviewing) {
            Log.d(TAG, "Stopping USB camera preview");
            isPreviewing = false;
        }
    }
    
    /**
     * Capture image - Creates placeholder with camera info
     */
    public void captureImage(final OnImageCapturedListener listener) {
        if (isPreviewing) {
            Log.d(TAG, "Capturing placeholder from USB camera");
            
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (listener != null) {
                    // Create info bitmap
                    Bitmap bitmap = createInfoBitmap();
                    listener.onImageCaptured(bitmap);
                }
            }, 300);
        } else {
            if (listener != null) {
                new Handler(Looper.getMainLooper()).post(() ->
                    listener.onError("Camera not ready")
                );
            }
        }
    }
    
    /**
     * Create informational bitmap
     */
    private Bitmap createInfoBitmap() {
        Bitmap bitmap = Bitmap.createBitmap(960, 1280, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        
        // Background
        canvas.drawColor(Color.parseColor("#2C3E50"));
        
        // Text
        Paint paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setTextSize(48);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setAntiAlias(true);
        
        String deviceName = uvcDevice != null ? uvcDevice.getProductName() : "Unknown";
        canvas.drawText("USB Camera Detected", 480, 500, paint);
        
        paint.setTextSize(36);
        canvas.drawText(deviceName, 480, 580, paint);
        
        paint.setTextSize(28);
        paint.setColor(Color.parseColor("#ECF0F1"));
        canvas.drawText("Preview requires UVC library", 480, 700, paint);
        canvas.drawText("Use 'USB Camera' app for full support", 480, 750, paint);
        
        return bitmap;
    }
    
    /**
     * Check if connected
     */
    public boolean isConnected() {
        return isConnected;
    }
    
    /**
     * Check if previewing
     */
    public boolean isPreviewing() {
        return isPreviewing;
    }
    
    /**
     * Get device count
     */
    public int getConnectedCameraCount() {
        if (uvcDevice != null) {
            return 1;
        }
        return 0;
    }
    
    /**
     * Register USB - rescan for devices
     */
    public void registerUSB() {
        Log.d(TAG, "Registering USB monitor");
        detectUsbCamera();
    }
    
    /**
     * Unregister USB
     */
    public void unregisterUSB() {
        Log.d(TAG, "Unregistering USB monitor");
    }
    
    /**
     * Release resources
     */
    public void release() {
        Log.d(TAG, "Releasing USB camera");
        closeCamera();
        try {
            context.unregisterReceiver(usbReceiver);
        } catch (Exception e) {
            Log.e(TAG, "Error unregistering receiver", e);
        }
    }
    
    // Callback interface
    public interface OnImageCapturedListener {
        void onImageCaptured(Bitmap bitmap);
        void onError(String error);
    }
}
