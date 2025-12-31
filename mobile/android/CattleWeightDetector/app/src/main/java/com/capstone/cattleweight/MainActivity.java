package com.capstone.cattleweight;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    
    private static final String TAG = "CattleWeightApp";
    private static final int CAMERA_PERMISSION_CODE = 100;
    
    // UI Components
    private PreviewView cameraPreview;
    private TextView tvCameraStatus;
    private TextView tvDistance;
    private TextView tvSignalStrength;
    private TextView tvTemperature;
    private TextView tvConnectionStatus;
    private TextView tvTimestamp;
    private TextView tvEstimatedWeight;
    private TextView tvConfidence;
    
    // Camera
    private Camera camera;
    private ExecutorService cameraExecutor;
    
    // LiDAR Data Receiver
    private LidarDataReceiver lidarReceiver;
    
    // Server Configuration - GANTI DENGAN IP KOMPUTER ANDA
    private static final String SERVER_URL = "http://192.168.1.3:5000"; // IP WiFi Server
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // ✅ CHECK USB DEVICES - Tambahkan untuk test
        Log.d(TAG, "=== CHECKING USB DEVICES ===");
        checkUsbDevices();
        
        // Initialize UI Components
        initializeViews();
        
        // Initialize Camera Executor
        cameraExecutor = Executors.newSingleThreadExecutor();
        
        // Request Camera Permission
        if (checkCameraPermission()) {
            startCamera();
        } else {
            requestCameraPermission();
        }
        
        // Initialize LiDAR Data Receiver
        initializeLidarReceiver();
    }
    
    /**
     * Check and list all USB devices connected to phone
     * Results will be shown in Logcat with tag "CattleWeightApp"
     */
    private void checkUsbDevices() {
        try {
            UvcCameraManager usbChecker = new UvcCameraManager(this);
            usbChecker.listAllUsbDevices();
            Log.d(TAG, "USB device check completed - See log above for details");
        } catch (Exception e) {
            Log.e(TAG, "Error checking USB devices: " + e.getMessage(), e);
        }
    }
    
    private void initializeViews() {
        cameraPreview = findViewById(R.id.cameraPreview);
        tvCameraStatus = findViewById(R.id.tvCameraStatus);
        tvDistance = findViewById(R.id.tvDistance);
        tvSignalStrength = findViewById(R.id.tvSignalStrength);
        tvTemperature = findViewById(R.id.tvTemperature);
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus);
        tvTimestamp = findViewById(R.id.tvTimestamp);
        tvEstimatedWeight = findViewById(R.id.tvEstimatedWeight);
        tvConfidence = findViewById(R.id.tvConfidence);
    }
    
    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
                == PackageManager.PERMISSION_GRANTED;
    }
    
    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this, 
                new String[]{Manifest.permission.CAMERA}, 
                CAMERA_PERMISSION_CODE);
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                          @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Izin kamera diperlukan!", Toast.LENGTH_LONG).show();
                tvCameraStatus.setText("❌ Camera Permission Denied");
            }
        }
    }
    
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = 
                ProcessCameraProvider.getInstance(this);
        
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindCameraPreview(cameraProvider);
            } catch (Exception e) {
                Log.e(TAG, "Error starting camera: " + e.getMessage());
                tvCameraStatus.setText("❌ Camera Error");
            }
        }, ContextCompat.getMainExecutor(this));
    }
    
    private void bindCameraPreview(ProcessCameraProvider cameraProvider) {
        // Preview
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());
        
        // Camera Selector (back camera)
        CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
        
        // Image Analysis (untuk future ML model integration)
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        
        // TODO: Set analyzer untuk ML model
        // imageAnalysis.setAnalyzer(cameraExecutor, new CattleWeightAnalyzer());
        
        try {
            // Unbind all use cases before rebinding
            cameraProvider.unbindAll();
            
            // Bind use cases to camera
            camera = cameraProvider.bindToLifecycle(
                    this, 
                    cameraSelector, 
                    preview,
                    imageAnalysis
            );
            
            tvCameraStatus.setText("📷 Camera Active");
            Log.d(TAG, "Camera started successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Camera binding failed: " + e.getMessage());
            tvCameraStatus.setText("❌ Camera Binding Failed");
        }
    }
    
    private void initializeLidarReceiver() {
        lidarReceiver = new LidarDataReceiver(SERVER_URL, new LidarDataReceiver.LidarDataCallback() {
            @Override
            public void onDataReceived(LidarData data) {
                runOnUiThread(() -> updateLidarUI(data));
            }
            
            @Override
            public void onConnectionStatusChanged(boolean connected) {
                runOnUiThread(() -> updateConnectionStatus(connected));
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Log.e(TAG, "LiDAR Error: " + error);
                    tvConnectionStatus.setText("🔴 Error: " + error);
                });
            }
        });
        
        // Start receiving LiDAR data
        lidarReceiver.startReceiving();
    }
    
    private void updateLidarUI(LidarData data) {
        tvDistance.setText(data.getJarak() + " cm");
        tvSignalStrength.setText(String.valueOf(data.getKekuatan()));
        tvTemperature.setText(String.format("%.1f°C", data.getSuhu()));
        tvTimestamp.setText("Last update: " + data.getFormattedTimestamp());
    }
    
    private void updateConnectionStatus(boolean connected) {
        if (connected) {
            tvConnectionStatus.setText("🟢 Connected to LiDAR Server");
        } else {
            tvConnectionStatus.setText("🔴 Disconnected from LiDAR Server");
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Cleanup
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        
        if (lidarReceiver != null) {
            lidarReceiver.stopReceiving();
        }
    }
}
