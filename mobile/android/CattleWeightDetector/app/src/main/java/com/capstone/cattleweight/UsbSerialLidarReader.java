package com.capstone.cattleweight;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

public class UsbSerialLidarReader implements SerialInputOutputManager.Listener {
    
    private static final String TAG = "UsbSerialLidarReader";
    private static final String ACTION_USB_PERMISSION = "com.capstone.cattleweight.USB_PERMISSION";
    private static final int BAUD_RATE = 115200;
    
    private final Context context;
    private final LidarDataCallback callback;
    
    private UsbManager usbManager;
    private UsbSerialPort serialPort;
    private SerialInputOutputManager ioManager;
    private boolean isConnected = false;
    private boolean receiverRegistered = false;
    
    private byte[] buffer = new byte[9];
    private int bufferIndex = 0;
    
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            connectToDevice(device);
                        }
                    } else {
                        Log.e(TAG, "USB permission denied");
                        callback.onConnectionStatusChanged(false);
                        callback.onError("USB permission denied");
                    }
                }
            }
        }
    };
    
    public interface LidarDataCallback {
        void onDataReceived(LidarData data);
        void onConnectionStatusChanged(boolean connected);
        void onError(String error);
    }
    
    public UsbSerialLidarReader(Context context, LidarDataCallback callback) {
        this.context = context;
        this.callback = callback;
        this.usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
    }
    
    public void startReading() {
        // Unregister previous receiver if exists
        if (receiverRegistered) {
            try {
                context.unregisterReceiver(usbReceiver);
            } catch (IllegalArgumentException e) {
                // Ignore
            }
        }
        
        // Register USB receiver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, new IntentFilter(ACTION_USB_PERMISSION), Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(usbReceiver, new IntentFilter(ACTION_USB_PERMISSION));
        }
        receiverRegistered = true;
        
        // Find USB serial devices
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        
        if (availableDrivers.isEmpty()) {
            Log.e(TAG, "No USB devices found");
            callback.onError("No USB device connected. Please connect LiDAR via USB OTG.");
            callback.onConnectionStatusChanged(false);
            return;
        }
        
        // Use first device (LiDAR)
        UsbSerialDriver driver = availableDrivers.get(0);
        UsbDevice device = driver.getDevice();
        
        // Request permission
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? 
                PendingIntent.FLAG_MUTABLE : PendingIntent.FLAG_ONE_SHOT;
        PendingIntent permissionIntent = PendingIntent.getBroadcast(context, 0, 
                new Intent(ACTION_USB_PERMISSION), flags);
        usbManager.requestPermission(device, permissionIntent);
    }
    
    private void connectToDevice(UsbDevice device) {
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        
        if (availableDrivers.isEmpty()) {
            callback.onError("USB driver not found");
            return;
        }
        
        UsbSerialDriver driver = availableDrivers.get(0);
        UsbDeviceConnection connection = usbManager.openDevice(device);
        
        if (connection == null) {
            callback.onError("Failed to open USB connection");
            return;
        }
        
        try {
            serialPort = driver.getPorts().get(0);
            serialPort.open(connection);
            serialPort.setParameters(BAUD_RATE, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            
            // Start IO manager
            ioManager = new SerialInputOutputManager(serialPort, this);
            ioManager.start();
            
            isConnected = true;
            callback.onConnectionStatusChanged(true);
            Log.d(TAG, "USB Serial connected successfully");
            
        } catch (IOException e) {
            Log.e(TAG, "Error opening serial port", e);
            callback.onError("Failed to open serial port: " + e.getMessage());
            callback.onConnectionStatusChanged(false);
        }
    }
    
    @Override
    public void onNewData(byte[] data) {
        // Parse TF Luna data protocol
        for (byte b : data) {
            if (bufferIndex == 0 && b != (byte) 0x59) {
                continue; // Wait for header byte
            }
            
            buffer[bufferIndex++] = b;
            
            if (bufferIndex == 9) {
                parseLidarData(buffer);
                bufferIndex = 0;
            }
        }
    }
    
    private void parseLidarData(byte[] data) {
        // TF Luna protocol: 0x59 0x59 Dist_L Dist_H Strength_L Strength_H Temp_L Temp_H Checksum
        if (data[0] != (byte) 0x59 || data[1] != (byte) 0x59) {
            return; // Invalid header
        }
        
        // Calculate checksum
        int checksum = 0;
        for (int i = 0; i < 8; i++) {
            checksum += data[i] & 0xFF;
        }
        checksum = checksum & 0xFF;
        
        if (checksum != (data[8] & 0xFF)) {
            Log.w(TAG, "Checksum mismatch");
            return;
        }
        
        // Parse distance (cm)
        int distance = ((data[3] & 0xFF) << 8) | (data[2] & 0xFF);
        
        // Parse signal strength
        int strength = ((data[5] & 0xFF) << 8) | (data[4] & 0xFF);
        
        // Parse temperature (°C, divide by 100)
        int tempRaw = ((data[7] & 0xFF) << 8) | (data[6] & 0xFF);
        float temperature = tempRaw / 100.0f;
        
        // Create LidarData object with all required parameters
        LidarData lidarData = new LidarData(distance, strength, (double)temperature, 
                System.currentTimeMillis(), "connected");
        
        // Callback on main thread
        new Handler(Looper.getMainLooper()).post(() -> {
            callback.onDataReceived(lidarData);
        });
    }
    
    @Override
    public void onRunError(Exception e) {
        Log.e(TAG, "Serial communication error", e);
        callback.onError("Communication error: " + e.getMessage());
        disconnect();
    }
    
    public void stopReading() {
        disconnect();
        if (receiverRegistered) {
            try {
                context.unregisterReceiver(usbReceiver);
                receiverRegistered = false;
            } catch (IllegalArgumentException e) {
                // Receiver not registered, ignore
                Log.w(TAG, "Receiver already unregistered");
            }
        }
    }
    
    private void disconnect() {
        if (ioManager != null) {
            ioManager.stop();
            ioManager = null;
        }
        
        if (serialPort != null) {
            try {
                serialPort.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing serial port", e);
            }
            serialPort = null;
        }
        
        isConnected = false;
        callback.onConnectionStatusChanged(false);
    }
    
    public boolean isConnected() {
        return isConnected;
    }
}
