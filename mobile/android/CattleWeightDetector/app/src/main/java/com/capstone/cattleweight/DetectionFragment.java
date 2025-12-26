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
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.common.util.concurrent.ListenableFuture;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.widget.Button;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DetectionFragment extends Fragment {
    
    private static final String TAG = "DetectionFragment";
    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final int STORAGE_PERMISSION_CODE = 101;
    private static final String SERVER_URL = "http://192.168.1.100:5000";
    
    // UI Components
    private PreviewView cameraPreview;
    private TextView tvCameraStatus, tvDistance, tvSignalStrength, tvTemperature;
    private TextView tvConnectionStatus, tvTimestamp, tvEstimatedWeight, tvConfidence;
    private Button btnDetect;
    private DetectionOverlayView detectionOverlay;
    
    // Camera
    private Camera camera;
    private ImageCapture imageCapture;
    private ImageAnalysis imageAnalysis;
    private ExecutorService cameraExecutor;
    
    // ML Models
    private CowDetector cowDetector;
    private CattleWeightPredictor weightPredictor;
    private boolean modelsInitialized = false;
    private boolean isDetecting = false;
    private List<CowDetector.Detection> latestDetections = new ArrayList<>();
    private final Object detectionLock = new Object();
    
    // LiDAR
    private LidarDataReceiver lidarReceiver;
    private UsbSerialLidarReader usbLidarReader;
    private SwitchCompat switchLidarMode;
    private boolean isUsbMode = false;
    private float currentLidarDistance = 0f; // in cm
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, 
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_detection, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Initialize executor FIRST before initializeViews (needed for ML model loading)
        cameraExecutor = Executors.newSingleThreadExecutor();
        
        initializeViews(view);
        
        // Check and request storage permission first
        if (!checkStoragePermission()) {
            requestStoragePermission();
        } else {
            // Permission already granted, initialize ML models
            initializeMLModels();
        }
        
        if (checkCameraPermission()) {
            startCamera();
        } else {
            requestCameraPermission();
        }
        
        initializeLidarReceiver();
    }
    
    private void initializeViews(View view) {
        Log.d(TAG, "=== initializeViews START ===");
        try {
            cameraPreview = view.findViewById(R.id.cameraPreview);
            detectionOverlay = view.findViewById(R.id.detectionOverlay);
            btnDetect = view.findViewById(R.id.btnDetect);
            tvCameraStatus = view.findViewById(R.id.tvCameraStatus);
            tvDistance = view.findViewById(R.id.tvDistance);
            tvSignalStrength = view.findViewById(R.id.tvSignalStrength);
            tvTemperature = view.findViewById(R.id.tvTemperature);
            tvConnectionStatus = view.findViewById(R.id.tvConnectionStatus);
            tvTimestamp = view.findViewById(R.id.tvTimestamp);
            tvEstimatedWeight = view.findViewById(R.id.tvEstimatedWeight);
            tvConfidence = view.findViewById(R.id.tvConfidence);
            
            Log.d(TAG, "Views found. Setting up button...");
            
            // Button initially disabled until cow is detected
            btnDetect.setEnabled(false);
            btnDetect.setText("⏳ Waiting for cow...");
            btnDetect.setOnClickListener(v -> performWeightPrediction());
            
            switchLidarMode = view.findViewById(R.id.switchLidarMode);
            switchLidarMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
                isUsbMode = isChecked;
                switchLidarMode();
            });
            
        } catch (Exception e) {
            Log.e(TAG, "EXCEPTION in initializeViews!", e);
        }
    }
    
    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) 
                == PackageManager.PERMISSION_GRANTED;
    }
    
    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(requireActivity(), 
                new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
    }
    
    private boolean checkStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ (API 33+)
            return ContextCompat.checkSelfPermission(requireContext(), 
                    android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
        } else {
            // Android 12 and below
            return ContextCompat.checkSelfPermission(requireContext(), 
                    android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }
    
    private void requestStoragePermission() {
        String[] permissions;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{android.Manifest.permission.READ_MEDIA_IMAGES};
        } else {
            permissions = new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE};
        }
        ActivityCompat.requestPermissions(requireActivity(), permissions, STORAGE_PERMISSION_CODE);
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
                
                // Image Capture for high-quality capture when button pressed
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .build();
                
                // Image Analysis for real-time detection
                imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);
                
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                cameraProvider.unbindAll();
                camera = cameraProvider.bindToLifecycle(
                        getViewLifecycleOwner(), cameraSelector, preview, imageCapture, imageAnalysis);
                
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
        currentLidarDistance = data.getJarak(); // Store for ML prediction
        tvDistance.setText(data.getJarak() + " cm");
        tvSignalStrength.setText(String.valueOf(data.getKekuatan()));
        tvTemperature.setText(String.format("%.1f°C", data.getSuhu()));
        tvTimestamp.setText("Last update: " + data.getFormattedTimestamp());
    }
    
    private void initializeMLModels() {
        Log.d(TAG, "=== initializeMLModels() CALLED ===");
        Log.d(TAG, "cameraExecutor: " + cameraExecutor);
        
        // Initialize in background thread
        cameraExecutor.execute(() -> {
            Log.d(TAG, "=== Background thread STARTED ===");
            try {
                // Use internal app storage (no permission needed)
                String yoloPath = requireContext().getFilesDir() + "/models/yolov8n.onnx";
                String weightPath = requireContext().getFilesDir() + "/models/bbox_weight_model.onnx";
                
                Log.d(TAG, "Creating CowDetector...");
                cowDetector = new CowDetector(requireContext());
                Log.d(TAG, "CowDetector created, initializing from: " + yoloPath);
                boolean yoloLoaded = cowDetector.initialize(yoloPath);
                Log.d(TAG, "YOLO loaded: " + yoloLoaded);
                
                Log.d(TAG, "Creating CattleWeightPredictor...");
                weightPredictor = new CattleWeightPredictor(requireContext());
                Log.d(TAG, "CattleWeightPredictor created, initializing from: " + weightPath);
                boolean weightLoaded = weightPredictor.initialize(weightPath);
                Log.d(TAG, "Weight predictor loaded: " + weightLoaded);
                
                modelsInitialized = yoloLoaded && weightLoaded;
                Log.d(TAG, "Models initialized: " + modelsInitialized);
                
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (modelsInitialized) {
                        Log.i(TAG, "✅ ML Models loaded successfully");
                        Toast.makeText(requireContext(), "✅ Models loaded", Toast.LENGTH_SHORT).show();
                    } else {
                        Log.e(TAG, "❌ Failed to load models");
                        btnDetect.setText("❌ Model Error");
                        btnDetect.setEnabled(false);
                        tvCameraStatus.setText("❌ Model Loading Failed");
                        Toast.makeText(requireContext(), "❌ Failed to load models", Toast.LENGTH_LONG).show();
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "=== EXCEPTION in model initialization ===", e);
                new Handler(Looper.getMainLooper()).post(() -> {
                    btnDetect.setText("❌ Model Error");
                    btnDetect.setEnabled(false);
                    Toast.makeText(requireContext(), "Model error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    
    private void analyzeImage(@NonNull ImageProxy imageProxy) {
        // Skip if models not ready or already detecting
        if (!modelsInitialized || isDetecting) {
            imageProxy.close();
            return;
        }
        
        try {
            // Convert ImageProxy to Bitmap
            Bitmap bitmap = imageProxyToBitmap(imageProxy);
            if (bitmap == null) {
                imageProxy.close();
                return;
            }
            
            // Set image size for overlay coordinate scaling
            detectionOverlay.setImageSize(bitmap.getWidth(), bitmap.getHeight());
            
            // Run YOLO detection (bbox will be in original image coordinates)
            List<CowDetector.Detection> detections = cowDetector.detectCows(bitmap);
            
            // Update detection state
            synchronized (detectionLock) {
                latestDetections = new ArrayList<>(detections);
            }
            
            // Update UI on main thread
            new Handler(Looper.getMainLooper()).post(() -> {
                if (detections.isEmpty()) {
                    // No cow detected - disable button, clear overlay
                    btnDetect.setEnabled(false);
                    btnDetect.setText("⏳ Waiting for cow...");
                    detectionOverlay.clearDetections();
                } else {
                    // Cow detected - enable button, show bounding boxes (without weight)
                    btnDetect.setEnabled(true);
                    btnDetect.setText("🔍 DETECT WEIGHT");
                    
                    Log.d(TAG, String.format("Displaying %d cow detections on overlay", detections.size()));
                    
                    // Convert detections to display format (without weight yet)
                    List<DetectionOverlayView.DetectionResult> displayResults = new ArrayList<>();
                    for (CowDetector.Detection det : detections) {
                        DetectionOverlayView.DetectionResult result = 
                                new DetectionOverlayView.DetectionResult();
                        result.bbox = det.bbox;
                        result.confidence = det.confidence;
                        result.weight = 0f; // No weight yet, just detection
                        displayResults.add(result);
                        
                        Log.d(TAG, String.format("  -> Display bbox: [%.0f, %.0f, %.0f, %.0f] conf=%.1f%%",
                            det.bbox.left, det.bbox.top, det.bbox.right, det.bbox.bottom, det.confidence * 100));
                    }
                    detectionOverlay.setDetections(displayResults);
                }
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Error analyzing image", e);
        } finally {
            imageProxy.close();
        }
    }
    
    private void performWeightPrediction() {
        // Validation checks
        if (!modelsInitialized) {
            showError("Models not loaded");
            return;
        }
        
        if (currentLidarDistance <= 0) {
            showError("LiDAR data not available");
            return;
        }
        
        // Check distance range (0.5m to 10m)
        float distanceMeters = currentLidarDistance / 100f;
        if (distanceMeters < 0.5f || distanceMeters > 10f) {
            showError(String.format("Distance out of range (%.1fm). Please position between 0.5m - 10m", distanceMeters));
            return;
        }
        
        // Check if cow is detected
        synchronized (detectionLock) {
            if (latestDetections.isEmpty()) {
                showError("No cow detected");
                return;
            }
        }
        
        // Prevent concurrent predictions
        if (isDetecting) {
            return;
        }
        
        isDetecting = true;
        btnDetect.setEnabled(false);
        btnDetect.setText("⏳ Processing...");
        
        // Capture high-quality image for weight prediction
        imageCapture.takePicture(cameraExecutor, new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy imageProxy) {
                try {
                    Bitmap bitmap = imageProxyToBitmap(imageProxy);
                    if (bitmap == null) {
                        showError("Failed to capture image");
                        resetDetectionState();
                        return;
                    }
                    
                    // Re-run detection on captured image (for accuracy)
                    List<CowDetector.Detection> capturedDetections = cowDetector.detectCows(bitmap);
                    if (capturedDetections.isEmpty()) {
                        showError("Cow not found in captured image");
                        resetDetectionState();
                        return;
                    }
                    
                    // Predict weight for each detected cow
                    List<DetectionOverlayView.DetectionResult> results = new ArrayList<>();
                    for (CowDetector.Detection detection : capturedDetections) {
                        try {
                            CattleWeightPredictor.WeightResult weightResult = 
                                    weightPredictor.predictWeight(bitmap, detection, distanceMeters);
                            
                            DetectionOverlayView.DetectionResult result = 
                                    new DetectionOverlayView.DetectionResult();
                            result.bbox = detection.bbox;
                            result.confidence = detection.confidence;
                            result.weight = weightResult.weight;
                            results.add(result);
                            
                        } catch (Exception e) {
                            Log.e(TAG, "Weight prediction error for detection", e);
                        }
                    }
                    
                    // Update UI with results
                    new Handler(Looper.getMainLooper()).post(() -> {
                        detectionOverlay.setDetections(results);
                        
                        if (!results.isEmpty()) {
                            DetectionOverlayView.DetectionResult firstResult = results.get(0);
                            tvEstimatedWeight.setText(String.format("%.1f kg", firstResult.weight));
                            tvConfidence.setText(String.format("%.0f%%", firstResult.confidence * 100));
                        }
                        
                        resetDetectionState();
                    });
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error in weight prediction", e);
                    showError("Prediction error: " + e.getMessage());
                    resetDetectionState();
                } finally {
                    imageProxy.close();
                }
            }
            
            @Override
            public void onError(@NonNull androidx.camera.core.ImageCaptureException exception) {
                Log.e(TAG, "Image capture failed", exception);
                showError("Capture failed");
                resetDetectionState();
            }
        });
    }
    
    private Bitmap imageProxyToBitmap(@NonNull ImageProxy imageProxy) {
        try {
            // CameraX default format is YUV_420_888
            // Convert to NV21 format for processing
            ByteBuffer yBuffer = imageProxy.getPlanes()[0].getBuffer();
            ByteBuffer uBuffer = imageProxy.getPlanes()[1].getBuffer();
            ByteBuffer vBuffer = imageProxy.getPlanes()[2].getBuffer();
            
            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();
            
            byte[] nv21 = new byte[ySize + uSize + vSize];
            
            // Y plane
            yBuffer.get(nv21, 0, ySize);
            
            // U and V are swapped for NV21 format
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);
            
            YuvImage yuvImage = new YuvImage(nv21, 
                    ImageFormat.NV21,  // Use NV21 explicitly
                    imageProxy.getWidth(), 
                    imageProxy.getHeight(), 
                    null);
            
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, 
                    imageProxy.getWidth(), 
                    imageProxy.getHeight()), 100, out);
            
            byte[] imageBytes = out.toByteArray();
            Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
            
            // Rotate bitmap if needed
            Matrix matrix = new Matrix();
            matrix.postRotate(imageProxy.getImageInfo().getRotationDegrees());
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            
        } catch (Exception e) {
            Log.e(TAG, "Error converting ImageProxy to Bitmap", e);
            return null;
        }
    }
    
    private void showError(String message) {
        new Handler(Looper.getMainLooper()).post(() -> {
            Toast.makeText(requireContext(), "⚠️ " + message, Toast.LENGTH_SHORT).show();
            Log.w(TAG, message);
        });
    }
    
    private void resetDetectionState() {
        new Handler(Looper.getMainLooper()).post(() -> {
            isDetecting = false;
            
            synchronized (detectionLock) {
                if (!latestDetections.isEmpty()) {
                    btnDetect.setEnabled(true);
                    btnDetect.setText("🔍 DETECT WEIGHT");
                } else {
                    btnDetect.setEnabled(false);
                    btnDetect.setText("⏳ Waiting for cow...");
                }
            }
        });
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        
        // Release ML models
        if (cowDetector != null) {
            cowDetector.release();
        }
        if (weightPredictor != null) {
            weightPredictor.release();
        }
        
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
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                          @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(requireContext(), "Camera permission required", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, initialize ML models
                initializeMLModels();
            } else {
                Toast.makeText(requireContext(), "Storage permission required to load AI models", 
                        Toast.LENGTH_LONG).show();
                btnDetect.setText("❌ Storage Permission Denied");
            }
        }
    }
}
