package com.capstone.cattleweight;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DetectionFragment extends Fragment {
    
    private static final String TAG = "DetectionFragment";
    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final String SERVER_URL = "http://192.168.1.100:5000";
    
    // UI Components
    private PreviewView cameraPreview;
    private DetectionOverlay detectionOverlay;
    private Button btnPredictWeight;
    private TextView tvCameraStatus, tvDistance, tvSignalStrength, tvTemperature;
    private TextView tvConnectionStatus, tvTimestamp, tvEstimatedWeight, tvConfidence;
    
    // Camera
    private Camera camera;
    private ExecutorService cameraExecutor;
    private ExecutorService analysisExecutor;
    
    // ML Models
    private YoloDetector yoloDetector;
    private WeightPredictor weightPredictor;
    
    // Detection State
    private DetectionOverlay.DetectionResult latestDetection;
    private Bitmap latestFrame;
    private LidarData latestLidarData;
    
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
        initializeMLModels();
        
        cameraExecutor = Executors.newSingleThreadExecutor();
        analysisExecutor = Executors.newSingleThreadExecutor();
        
        if (checkCameraPermission()) {
            startCamera();
        } else {
            requestCameraPermission();
        }
        
        initializeLidarReceiver();
    }
    
    private void initializeViews(View view) {
        cameraPreview = view.findViewById(R.id.cameraPreview);
        detectionOverlay = view.findViewById(R.id.detectionOverlay);
        btnPredictWeight = view.findViewById(R.id.btnPredictWeight);
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
        
        // Button predict weight
        btnPredictWeight.setOnClickListener(v -> performWeightPrediction());
    }
    
    private void initializeMLModels() {
        yoloDetector = new YoloDetector(requireContext());
        weightPredictor = new WeightPredictor(requireContext());
        Log.i(TAG, "ML Models initialized");
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
                
                // Preview
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());
                
                // Image Analysis untuk YOLO detection
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                
                imageAnalysis.setAnalyzer(analysisExecutor, this::analyzeImage);
                
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                cameraProvider.unbindAll();
                camera = cameraProvider.bindToLifecycle(
                        getViewLifecycleOwner(), cameraSelector, preview, imageAnalysis);
                
                tvCameraStatus.setText("📷 Camera Active");
                tvCameraStatus.setTextColor(0xFF4CAF50);
                
            } catch (Exception e) {
                Log.e(TAG, "Camera initialization failed", e);
                tvCameraStatus.setText("📷 Camera Error");
                tvCameraStatus.setTextColor(0xFFF44336);
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }
    
    /**
     * Analyze camera frame untuk YOLO detection
     */
    private void analyzeImage(ImageProxy imageProxy) {
        try {
            // Convert ImageProxy to Bitmap
            Bitmap bitmap = imageProxyToBitmap(imageProxy);
            if (bitmap == null) {
                imageProxy.close();
                return;
            }
            
            // Simpan frame terbaru
            latestFrame = bitmap;
            
            // Run YOLO detection
            List<DetectionOverlay.DetectionResult> detections = yoloDetector.detect(bitmap);
            
            // Update overlay di UI thread
            new Handler(Looper.getMainLooper()).post(() -> {
                if (detections != null && !detections.isEmpty()) {
                    detectionOverlay.setDetections(detections);
                    
                    // Ambil detection terbesar
                    latestDetection = yoloDetector.getLargestDetection(detections);
                    
                    // Update confidence display
                    tvConfidence.setText(String.format("Confidence: %.0f%%", 
                            latestDetection.confidence * 100));
                    
                    // Enable predict button jika ada detection dan LiDAR data
                    btnPredictWeight.setEnabled(latestLidarData != null);
                } else {
                    detectionOverlay.clearDetections();
                    latestDetection = null;
                    tvConfidence.setText("Confidence: --%");
                    btnPredictWeight.setEnabled(false);
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Error analyzing image", e);
        } finally {
            imageProxy.close();
        }
    }
    
    /**
     * Convert ImageProxy ke Bitmap
     */
    private Bitmap imageProxyToBitmap(ImageProxy imageProxy) {
        try {
            ByteBuffer yBuffer = imageProxy.getPlanes()[0].getBuffer();
            ByteBuffer uBuffer = imageProxy.getPlanes()[1].getBuffer();
            ByteBuffer vBuffer = imageProxy.getPlanes()[2].getBuffer();
            
            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();
            
            byte[] nv21 = new byte[ySize + uSize + vSize];
            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);
            
            YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, 
                    imageProxy.getWidth(), imageProxy.getHeight(), null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, 
                    imageProxy.getWidth(), imageProxy.getHeight()), 100, out);
            
            byte[] imageBytes = out.toByteArray();
            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
            
        } catch (Exception e) {
            Log.e(TAG, "Error converting ImageProxy to Bitmap", e);
            return null;
        }
    }
    
    /**
     * Perform weight prediction
     */
    private void performWeightPrediction() {
        if (latestDetection == null) {
            Toast.makeText(requireContext(), "Tidak ada sapi terdeteksi", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (latestLidarData == null) {
            Toast.makeText(requireContext(), "Menunggu data LiDAR...", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (latestFrame == null) {
            Toast.makeText(requireContext(), "Frame tidak tersedia", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Disable button saat processing
        btnPredictWeight.setEnabled(false);
        tvEstimatedWeight.setText("Memproses...");
        
        // Run prediction di background thread
        analysisExecutor.execute(() -> {
            try {
                // 1. Crop image menggunakan bbox
                int left = (int) latestDetection.box.left;
                int top = (int) latestDetection.box.top;
                int width = (int) latestDetection.getWidth();
                int height = (int) latestDetection.getHeight();
                
                // Pastikan bbox dalam bounds
                left = Math.max(0, Math.min(left, latestFrame.getWidth() - 1));
                top = Math.max(0, Math.min(top, latestFrame.getHeight() - 1));
                width = Math.min(width, latestFrame.getWidth() - left);
                height = Math.min(height, latestFrame.getHeight() - top);
                
                Bitmap croppedBitmap = Bitmap.createBitmap(latestFrame, left, top, width, height);
                
                // 2. Get bbox area
                float bboxArea = latestDetection.getArea();
                
                // 3. Convert jarak dari cm ke meter
                float distanceMeters = latestLidarData.getJarak() / 100.0f;
                
                // 4. Predict weight
                float predictedWeight = weightPredictor.predictWeight(
                        croppedBitmap, bboxArea, distanceMeters);
                
                // 5. Update UI
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (predictedWeight > 0) {
                        tvEstimatedWeight.setText(String.format("Bobot: %.1f kg", predictedWeight));
                        Toast.makeText(requireContext(), 
                                String.format("Prediksi: %.1f kg", predictedWeight), 
                                Toast.LENGTH_LONG).show();
                    } else {
                        tvEstimatedWeight.setText("Bobot: Error");
                        Toast.makeText(requireContext(), 
                                "Gagal memprediksi berat", 
                                Toast.LENGTH_SHORT).show();
                    }
                    btnPredictWeight.setEnabled(true);
                });
                
            } catch (Exception e) {
                Log.e(TAG, "Error predicting weight", e);
                new Handler(Looper.getMainLooper()).post(() -> {
                    tvEstimatedWeight.setText("Bobot: Error");
                    Toast.makeText(requireContext(), 
                            "Error: " + e.getMessage(), 
                            Toast.LENGTH_SHORT).show();
                    btnPredictWeight.setEnabled(true);
                });
            }
        });
    }
    
    private void initializeLidarReceiver() {
        if (isUsbMode) {
            return; // Skip WiFi initialization in USB mode
        }
        
        lidarReceiver = new LidarDataReceiver(SERVER_URL, new LidarDataReceiver.LidarDataCallback() {
            @Override
            public void onDataReceived(LidarData data) {
                latestLidarData = data; // Store latest data
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
                latestLidarData = data; // Store latest data
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
        
        // Stop LiDAR
        if (lidarReceiver != null) {
            lidarReceiver.stopReceiving();
        }
        if (usbLidarReader != null) {
            usbLidarReader.stopReading();
        }
        
        // Stop ML Models
        if (yoloDetector != null) {
            yoloDetector.close();
        }
        if (weightPredictor != null) {
            weightPredictor.close();
        }
        
        // Stop executors
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        if (analysisExecutor != null) {
            analysisExecutor.shutdown();
        }
    }
}
