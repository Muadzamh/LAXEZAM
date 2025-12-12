package com.capstone.cattleweight;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DetectionFragment extends Fragment {
    
    private static final String TAG = "DetectionFragment";
    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final String SERVER_URL = "http://192.168.1.100:5000";
    
    // UI Components
    private PreviewView cameraPreview;
    private TextView tvCameraStatus, tvDistance, tvSignalStrength, tvTemperature;
    private TextView tvConnectionStatus, tvTimestamp, tvEstimatedWeight, tvConfidence;
    
    // Camera
    private Camera camera;
    private ExecutorService cameraExecutor;
    
    // LiDAR
    private LidarDataReceiver lidarReceiver;
    private UsbSerialLidarReader usbLidarReader;
    private SwitchCompat switchLidarMode;
    private boolean isUsbMode = false;
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, 
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_detection, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        initializeViews(view);
        cameraExecutor = Executors.newSingleThreadExecutor();
        
        if (checkCameraPermission()) {
            startCamera();
        } else {
            requestCameraPermission();
        }
        
        initializeLidarReceiver();
    }
    
    private void initializeViews(View view) {
        cameraPreview = view.findViewById(R.id.cameraPreview);
        tvCameraStatus = view.findViewById(R.id.tvCameraStatus);
        tvDistance = view.findViewById(R.id.tvDistance);
        tvSignalStrength = view.findViewById(R.id.tvSignalStrength);
        tvTemperature = view.findViewById(R.id.tvTemperature);
        tvConnectionStatus = view.findViewById(R.id.tvConnectionStatus);
        tvTimestamp = view.findViewById(R.id.tvTimestamp);
        tvEstimatedWeight = view.findViewById(R.id.tvEstimatedWeight);
        tvConfidence = view.findViewById(R.id.tvConfidence);
        
        switchLidarMode = view.findViewById(R.id.switchLidarMode);
        switchLidarMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isUsbMode = isChecked;
            switchLidarMode();
        });
    }
    
    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) 
                == PackageManager.PERMISSION_GRANTED;
    }
    
    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(requireActivity(), 
                new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
    }
    
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = 
                ProcessCameraProvider.getInstance(requireContext());
        
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());
                
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                cameraProvider.unbindAll();
                camera = cameraProvider.bindToLifecycle(
                        getViewLifecycleOwner(), cameraSelector, preview);
                
                tvCameraStatus.setText("📷 Camera Active");
                tvCameraStatus.setTextColor(0xFF4CAF50);
                
            } catch (Exception e) {
                Log.e(TAG, "Camera initialization failed", e);
                tvCameraStatus.setText("📷 Camera Error");
                tvCameraStatus.setTextColor(0xFFF44336);
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }
    
    private void initializeLidarReceiver() {
        if (isUsbMode) {
            return; // Skip WiFi initialization in USB mode
        }
        
        lidarReceiver = new LidarDataReceiver(SERVER_URL, new LidarDataReceiver.LidarDataCallback() {
            @Override
            public void onDataReceived(LidarData data) {
                new Handler(Looper.getMainLooper()).post(() -> updateLidarUI(data));
            }
            
            @Override
            public void onConnectionStatusChanged(boolean connected) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (connected) {
                        tvConnectionStatus.setText("🟢 Connected to LiDAR Server");
                        tvConnectionStatus.setTextColor(0xFF4CAF50);
                    } else {
                        tvConnectionStatus.setText("🔴 Disconnected");
                        tvConnectionStatus.setTextColor(0xFFF44336);
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    tvConnectionStatus.setText("⚠️ Error: " + error);
                    tvConnectionStatus.setTextColor(0xFFFF9800);
                });
            }
        });
        
        lidarReceiver.startReceiving();
    }
    
    private void switchLidarMode() {
        // Stop current mode
        if (lidarReceiver != null) {
            lidarReceiver.stopReceiving();
            lidarReceiver = null;
        }
        if (usbLidarReader != null) {
            usbLidarReader.stopReading();
            usbLidarReader = null;
        }
        
        // Start new mode
        if (isUsbMode) {
            initializeUsbLidar();
        } else {
            initializeLidarReceiver();
        }
    }
    
    private void initializeUsbLidar() {
        usbLidarReader = new UsbSerialLidarReader(requireContext(), new UsbSerialLidarReader.LidarDataCallback() {
            @Override
            public void onDataReceived(LidarData data) {
                new Handler(Looper.getMainLooper()).post(() -> updateLidarUI(data));
            }
            
            @Override
            public void onConnectionStatusChanged(boolean connected) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (connected) {
                        tvConnectionStatus.setText("🟢 USB LiDAR Connected");
                        tvConnectionStatus.setTextColor(0xFF4CAF50);
                    } else {
                        tvConnectionStatus.setText("🔴 USB Disconnected");
                        tvConnectionStatus.setTextColor(0xFFF44336);
                    }
                });
            }
            
            @Override
            public void onError(String error) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    tvConnectionStatus.setText("⚠️ USB Error: " + error);
                    tvConnectionStatus.setTextColor(0xFFFF9800);
                });
            }
        });
        
        usbLidarReader.startReading();
    }
    
    private void updateLidarUI(LidarData data) {
        tvDistance.setText(data.getJarak() + " cm");
        tvSignalStrength.setText(String.valueOf(data.getKekuatan()));
        tvTemperature.setText(String.format("%.1f°C", data.getSuhu()));
        tvTimestamp.setText("Last update: " + data.getFormattedTimestamp());
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (lidarReceiver != null) {
            lidarReceiver.stopReceiving();
        }
        if (usbLidarReader != null) {
            usbLidarReader.stopReading();
        }
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }
}
