package com.capstone.cattleweight;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.HashMap;

/**
 * CONTOH ACTIVITY UNTUK TEST USB DEVICE LIST
 * 
 * Cara pakai:
 * 1. Tambahkan activity ini di AndroidManifest.xml:
 *    <activity android:name=".UsbTestActivity" />
 * 
 * 2. Buat layout file: res/layout/activity_usb_test.xml dengan:
 *    - Button dengan id: btnCheckUsb
 *    - TextView dengan id: tvUsbList
 * 
 * 3. Atau gunakan method listAllUsbDevices() di Activity manapun
 */
public class UsbTestActivity extends AppCompatActivity {
    
    private static final String TAG = "UsbTestActivity";
    private TextView tvUsbList;
    private Button btnCheckUsb;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_usb_test);
        
        // Initialize views
        tvUsbList = findViewById(R.id.tvUsbList);
        btnCheckUsb = findViewById(R.id.btnCheckUsb);
        
        // Set button click listener
        btnCheckUsb.setOnClickListener(v -> {
            // Method 1: Check dan tampilkan di Logcat
            listAllUsbDevices();
            
            // Method 2: Tampilkan di UI
            displayUsbDevicesInUI();
            
            // Method 3: Test dengan UvcCameraManager
            testWithUvcCameraManager();
        });
        
        // Auto-check saat activity dibuka
        Log.i(TAG, "UsbTestActivity created - Auto-checking USB devices...");
        listAllUsbDevices();
    }
    
    /**
     * Method 1: Check USB menggunakan UsbManager langsung
     * Ini adalah versi paling simple dan standalone
     */
    private void listAllUsbDevices() {
        // Dapatkan UsbManager dari system service
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        
        if (usbManager == null) {
            Log.e(TAG, "❌ UsbManager is null - USB service not available");
            return;
        }
        
        // Dapatkan list semua USB devices
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        
        if (deviceList.isEmpty()) {
            Log.i(TAG, "⚠️ No USB devices found");
            Log.i(TAG, "Tips: Make sure USB device is connected via OTG cable");
            return;
        }
        
        // Print hasil ke Logcat
        Log.i(TAG, "╔════════════════════════════════════════╗");
        Log.i(TAG, "║     USB DEVICES LIST                   ║");
        Log.i(TAG, "╠════════════════════════════════════════╣");
        Log.i(TAG, "  Total devices: " + deviceList.size());
        Log.i(TAG, "╚════════════════════════════════════════╝");
        
        int index = 1;
        for (UsbDevice device : deviceList.values()) {
            Log.i(TAG, "");
            Log.i(TAG, "┌─── Device #" + index + " ───────────────────────");
            Log.i(TAG, "│ Device Name    : " + device.getDeviceName());
            Log.i(TAG, "│ Product Name   : " + (device.getProductName() != null ? device.getProductName() : "Unknown"));
            Log.i(TAG, "│ Manufacturer   : " + (device.getManufacturerName() != null ? device.getManufacturerName() : "Unknown"));
            Log.i(TAG, "│ Vendor ID      : 0x" + Integer.toHexString(device.getVendorId()).toUpperCase());
            Log.i(TAG, "│ Product ID     : 0x" + Integer.toHexString(device.getProductId()).toUpperCase());
            Log.i(TAG, "│ Device ID      : " + device.getDeviceId());
            Log.i(TAG, "│ Device Class   : " + device.getDeviceClass() + " (" + getDeviceClassName(device.getDeviceClass()) + ")");
            Log.i(TAG, "│ Subclass       : " + device.getDeviceSubclass());
            Log.i(TAG, "│ Protocol       : " + device.getDeviceProtocol());
            Log.i(TAG, "│ Interface Count: " + device.getInterfaceCount());
            Log.i(TAG, "└───────────────────────────────────────");
            
            // Check if device has permission
            boolean hasPermission = usbManager.hasPermission(device);
            Log.i(TAG, "  📋 Permission: " + (hasPermission ? "✅ GRANTED" : "❌ NOT GRANTED"));
            
            index++;
        }
        
        Log.i(TAG, "");
        Log.i(TAG, "════════════════════════════════════════");
    }
    
    /**
     * Method 2: Check USB menggunakan UvcCameraManager yang sudah ada
     */
    private void testWithUvcCameraManager() {
        Log.i(TAG, "");
        Log.i(TAG, "Testing with UvcCameraManager...");
        UvcCameraManager manager = new UvcCameraManager(this);
        manager.listAllUsbDevices();
    }
    
    /**
     * Helper method untuk mendapatkan nama class dari USB Device Class code
     */
    private String getDeviceClassName(int deviceClass) {
        switch (deviceClass) {
            case 0: return "Per Interface";
            case 1: return "Audio";
            case 2: return "Communication";
            case 3: return "HID (Human Interface)";
            case 5: return "Physical";
            case 6: return "Image";
            case 7: return "Printer";
            case 8: return "Mass Storage";
            case 9: return "Hub";
            case 10: return "CDC-Data";
            case 11: return "Smart Card";
            case 13: return "Content Security";
            case 14: return "Video";
            case 15: return "Personal Healthcare";
            case 16: return "Audio/Video";
            case 224: return "Wireless";
            case 239: return "Miscellaneous";
            case 254: return "Application Specific";
            case 255: return "Vendor Specific";
            default: return "Unknown";
        }
    }
    
    /**
     * Optional: Method untuk show hasil di UI (TextView)
     * Panggil method ini jika Anda punya layout dengan TextView
     */
    private void displayUsbDevicesInUI() {
        UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        
        if (usbManager == null) {
            if (tvUsbList != null) {
                tvUsbList.setText("❌ USB Manager not available");
            }
            return;
        }
        
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        
        StringBuilder sb = new StringBuilder();
        sb.append("📱 USB Devices Found: ").append(deviceList.size()).append("\n\n");
        
        if (deviceList.isEmpty()) {
            sb.append("No USB devices connected\n");
            sb.append("Please connect USB device via OTG cable");
        } else {
            int index = 1;
            for (UsbDevice device : deviceList.values()) {
                sb.append("━━━━━━━━━━━━━━━━━━━━━━\n");
                sb.append("Device ").append(index).append("\n");
                sb.append("━━━━━━━━━━━━━━━━━━━━━━\n");
                sb.append("Name: ").append(device.getDeviceName()).append("\n");
                sb.append("Product: ").append(device.getProductName()).append("\n");
                sb.append("Vendor: ").append(device.getManufacturerName()).append("\n");
                sb.append("VID: 0x").append(Integer.toHexString(device.getVendorId())).append("\n");
                sb.append("PID: 0x").append(Integer.toHexString(device.getProductId())).append("\n");
                sb.append("Class: ").append(getDeviceClassName(device.getDeviceClass())).append("\n");
                sb.append("\n");
                index++;
            }
        }
        
        if (tvUsbList != null) {
            tvUsbList.setText(sb.toString());
        }
    }
}
