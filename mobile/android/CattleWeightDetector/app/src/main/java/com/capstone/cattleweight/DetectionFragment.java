package com.capstone.cattleweight;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.Rect;
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

import androidx.appcompat.app.AlertDialog;
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

import android.graphics.YuvImage;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

import android.view.TextureView;
import android.graphics.SurfaceTexture;
import androidx.appcompat.widget.SwitchCompat;

public class DetectionFragment extends Fragment {
    
    private static final String TAG = "DetectionFragment";
    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final int STORAGE_PERMISSION_CODE = 101;
    private static final String SERVER_URL = "http://192.168.1.3:5000"; // IP WiFi Server (unused in USB mode)
    
    // UI Components
    private PreviewView cameraPreview;
    private TextureView uvcCameraView;
    private SwitchCompat switchCameraMode;
    private TextView tvDistance, tvSignalStrength, tvTemperature;
    private TextView tvConnectionStatus, tvTimestamp, tvEstimatedWeight, tvCarcassWeight, tvConfidence;
    private Button btnDetect;
    private DetectionOverlayView detectionOverlay;
    private AlertDialog loadingDialog;
    private AlertDialog processingDialog; // Dialog for weight prediction processing
    
    // Camera
    private Camera camera;
    private ImageCapture imageCapture;
    private ImageAnalysis imageAnalysis;
    private ExecutorService cameraExecutor;
    
    // USB Camera (GroundChat)
    private UvcCameraManager uvcCameraManager;
    private boolean isUsingUsbCamera = false;
    private Bitmap lastUvcFrame = null;
    private final Object uvcFrameLock = new Object();
    
    // ML Models
    private CowDetector cowDetector;
    private CattleWeightPredictor weightPredictor;
    private boolean modelsInitialized = false;
    private boolean isDetecting = false;
    private volatile boolean isPredicting = false; // Flag to pause detection loop during prediction
    private ExecutorService predictionExecutor; // Separate executor for weight prediction
    private List<CowDetector.Detection> latestDetections = new ArrayList<>();
    private final Object detectionLock = new Object();
    
    // LiDAR
    private LidarDataReceiver lidarReceiver;
    private UsbSerialLidarReader usbLidarReader;
    // LiDAR - USB Mode Only (no toggle)
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
        
        // Initialize executors FIRST before initializeViews (needed for ML model loading)
        cameraExecutor = Executors.newSingleThreadExecutor();
        predictionExecutor = Executors.newSingleThreadExecutor(); // Separate executor for weight prediction
        
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
            uvcCameraView = view.findViewById(R.id.uvcCameraView);
            switchCameraMode = view.findViewById(R.id.switchCameraMode);
            detectionOverlay = view.findViewById(R.id.detectionOverlay);
            btnDetect = view.findViewById(R.id.btnDetect);
            tvDistance = view.findViewById(R.id.tvDistance);
            tvSignalStrength = view.findViewById(R.id.tvSignalStrength);
            tvTemperature = view.findViewById(R.id.tvTemperature);
            tvConnectionStatus = view.findViewById(R.id.tvConnectionStatus);
            tvTimestamp = view.findViewById(R.id.tvTimestamp);
            tvEstimatedWeight = view.findViewById(R.id.tvEstimatedWeight);
            tvCarcassWeight = view.findViewById(R.id.tvCarcassWeight);
            tvConfidence = view.findViewById(R.id.tvConfidence);
            
            Log.d(TAG, "Views found. Setting up button...");
            
            // Button initially disabled until cow is detected
            btnDetect.setEnabled(false);
            btnDetect.setText("⏳ Waiting for cow...");
            btnDetect.setOnClickListener(v -> performWeightPrediction());
            
            // Camera switch listener
            switchCameraMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
                switchCameraSource(isChecked);
            });
            
            // Initialize UVC Camera Manager
            initializeUvcCamera();
            
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
            // Android 12 and below - need WRITE for saving photos
            permissions = new String[]{
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
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
                
                Log.d(TAG, "📷 Camera Active");
                
            } catch (Exception e) {
                Log.e(TAG, "Camera initialization failed", e);
                Toast.makeText(requireContext(), "❌ Camera Error", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }
    
    private void initializeLidarReceiver() {
        // Always use USB mode - initialize USB LiDAR directly
        initializeUsbLidar();
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
    
    // ========================================
    // USB CAMERA (GroundChat) METHODS
    // ========================================
    
    private void initializeUvcCamera() {
        Log.d(TAG, "Initializing UVC Camera Manager...");
        
        if (uvcCameraManager == null) {
            uvcCameraManager = new UvcCameraManager(requireContext());
            
            // Initialize USB monitor with callback
            uvcCameraManager.initialize(new UvcCameraManager.UvcCameraCallback() {
                @Override
                public void onCameraConnected() {
                    Log.d(TAG, "✅ UVC Camera Connected");
                    new Handler(Looper.getMainLooper()).post(() -> {
                        Toast.makeText(requireContext(), "📷 USB Camera Connected", Toast.LENGTH_SHORT).show();
                    });
                }
                
                @Override
                public void onCameraDisconnected() {
                    Log.d(TAG, "❌ UVC Camera Disconnected");
                    new Handler(Looper.getMainLooper()).post(() -> {
                        // Switch back to built-in camera if USB disconnected
                        if (isUsingUsbCamera && switchCameraMode != null) {
                            switchCameraMode.setChecked(false);
                            Toast.makeText(requireContext(), "📷 USB Camera Disconnected - Switched to built-in", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
                
                @Override
                public void onCameraError(String error) {
                    Log.e(TAG, "UVC Camera Error: " + error);
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (isUsingUsbCamera && switchCameraMode != null) {
                            switchCameraMode.setChecked(false);
                        }
                        Toast.makeText(requireContext(), "❌ USB Camera Error: " + error, Toast.LENGTH_SHORT).show();
                    });
                }
                
                @Override
                public void onPreviewStarted() {
                    Log.d(TAG, "USB Camera preview started");
                    new Handler(Looper.getMainLooper()).post(() -> {
                        Toast.makeText(requireContext(), "✅ USB Camera preview started", Toast.LENGTH_SHORT).show();
                        // Start YOLO detection loop for USB camera
                        startUvcDetectionLoop();
                    });
                }
            });
            
            // Set preview texture
            uvcCameraManager.setPreviewTexture(uvcCameraView);
        }
    }
    
    private void startUvcDetectionLoop() {
        // Run YOLO detection periodically on USB camera frames
        cameraExecutor.execute(() -> {
            while (isUsingUsbCamera && !Thread.currentThread().isInterrupted()) {
                // Skip detection if weight prediction is in progress
                if (isPredicting) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        break;
                    }
                    continue;
                }
                
                if (modelsInitialized && !isDetecting && uvcCameraView != null) {
                    try {
                        // Get frame from TextureView
                        Bitmap frame = uvcCameraView.getBitmap();
                        if (frame != null) {
                            // Store for capture
                            synchronized (uvcFrameLock) {
                                if (lastUvcFrame != null && !lastUvcFrame.isRecycled()) {
                                    lastUvcFrame.recycle();
                                }
                                lastUvcFrame = frame.copy(frame.getConfig(), true);
                            }
                            
                            // Run YOLO detection
                            runYoloDetectionOnBitmap(frame);
                            frame.recycle();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error in UVC detection loop", e);
                    }
                }
                
                // Sleep to limit frame rate (~10 fps for detection)
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
    }
    
    private void switchCameraSource(boolean useUsbCamera) {
        Log.d(TAG, "Switching camera source: USB=" + useUsbCamera);
        isUsingUsbCamera = useUsbCamera;
        
        if (useUsbCamera) {
            // Switch to USB Camera (GroundChat)
            cameraPreview.setVisibility(View.GONE);
            uvcCameraView.setVisibility(View.VISIBLE);
            
            // Stop built-in camera
            try {
                ProcessCameraProvider cameraProvider = ProcessCameraProvider.getInstance(requireContext()).get();
                cameraProvider.unbindAll();
                Log.d(TAG, "Built-in camera stopped");
            } catch (Exception e) {
                Log.e(TAG, "Error stopping built-in camera", e);
            }
            
            // Start USB camera
            if (uvcCameraManager != null) {
                uvcCameraManager.requestCameraPermission();
                Log.d(TAG, "USB camera permission requested");
            }
            
            Toast.makeText(requireContext(), "📷 Switching to USB Camera...", Toast.LENGTH_SHORT).show();
            
        } else {
            // Switch to Built-in Camera
            uvcCameraView.setVisibility(View.GONE);
            cameraPreview.setVisibility(View.VISIBLE);
            
            // Stop USB camera
            if (uvcCameraManager != null) {
                uvcCameraManager.closeCamera();
                Log.d(TAG, "USB camera closed");
            }
            
            // Restart built-in camera
            startCamera();
            Log.d(TAG, "Built-in camera restarted");
            
            Toast.makeText(requireContext(), "📷 Switched to Built-in Camera", Toast.LENGTH_SHORT).show();
        }
        
        // Clear detection overlay when switching
        if (detectionOverlay != null) {
            detectionOverlay.clearDetections();
        }
    }
    
    private void runYoloDetectionOnBitmap(Bitmap bitmap) {
        if (cowDetector == null || bitmap == null) return;
        
        isDetecting = true;
        
        // Save bitmap dimensions for overlay scaling
        final int bitmapWidth = bitmap.getWidth();
        final int bitmapHeight = bitmap.getHeight();
        
        try {
            List<CowDetector.Detection> detections = cowDetector.detectCows(bitmap);
            
            synchronized (detectionLock) {
                latestDetections = detections;
            }
            
            new Handler(Looper.getMainLooper()).post(() -> {
                // Update overlay - convert to DetectionResult
                if (detectionOverlay != null) {
                    // IMPORTANT: Set image size for proper coordinate scaling
                    // This ensures bbox from CowDetector (in original image space) 
                    // scales correctly to overlay view size
                    detectionOverlay.setImageSize(bitmapWidth, bitmapHeight);
                    
                    List<DetectionOverlayView.DetectionResult> results = new ArrayList<>();
                    for (CowDetector.Detection det : detections) {
                        DetectionOverlayView.DetectionResult result = new DetectionOverlayView.DetectionResult();
                        result.bbox = det.bbox;
                        result.confidence = det.confidence;
                        result.weight = 0; // Not predicted yet
                        results.add(result);
                    }
                    detectionOverlay.setDetections(results);
                }
                
                // Update button state
                boolean hasCow = detections != null && !detections.isEmpty();
                btnDetect.setEnabled(hasCow && modelsInitialized);
                if (hasCow) {
                    btnDetect.setText("🔍\n\nD\nE\nT\nE\nC\nT");
                } else {
                    btnDetect.setText("⏳ Waiting for cow...");
                }
                
                isDetecting = false;
            });
            
        } catch (Exception e) {
            Log.e(TAG, "YOLO detection on UVC frame failed", e);
            isDetecting = false;
        }
    }

    private void initializeMLModels() {
        Log.d(TAG, "=== initializeMLModels() CALLED ===");
        Log.d(TAG, "cameraExecutor: " + cameraExecutor);
        
        // Show loading dialog
        new Handler(Looper.getMainLooper()).post(() -> {
            loadingDialog = new AlertDialog.Builder(requireContext())
                    .setTitle("⏳ Loading Models")
                    .setMessage("Copying models from APK...")
                    .setCancelable(false)
                    .create();
            loadingDialog.show();
        });
        
        // Initialize in background thread
        cameraExecutor.execute(() -> {
            Log.d(TAG, "=== Background thread STARTED ===");
            try {
                // Copy models from assets to internal storage first
                java.io.File modelsDir = new java.io.File(requireContext().getFilesDir(), "models");
                if (!modelsDir.exists()) {
                    modelsDir.mkdirs();
                    Log.d(TAG, "Created models directory: " + modelsDir.getAbsolutePath());
                }
                
                String yoloPath = new java.io.File(modelsDir, "yolov8n.onnx").getAbsolutePath();
                String weightPath = new java.io.File(modelsDir, "bbox_weight_model.onnx").getAbsolutePath();
                
                // Copy YOLO model from assets if not exists
                java.io.File yoloFile = new java.io.File(yoloPath);
                if (!yoloFile.exists()) {
                    Log.d(TAG, "Copying yolov8n.onnx from assets...");
                    copyAssetToFile("yolov8n.onnx", yoloPath);
                    Log.d(TAG, "✅ YOLO model copied");
                } else {
                    Log.d(TAG, "YOLO model already exists: " + yoloPath);
                }
                
                // Copy weight model from assets if not exists
                java.io.File weightFile = new java.io.File(weightPath);
                if (!weightFile.exists()) {
                    Log.d(TAG, "Copying bbox_weight_model.onnx from assets...");
                    copyAssetToFile("bbox_weight_model.onnx", weightPath);
                    Log.d(TAG, "✅ Weight model copied");
                } else {
                    Log.d(TAG, "Weight model already exists: " + weightPath);
                }
                
                Log.d(TAG, "Creating CowDetector...");
                cowDetector = new CowDetector(requireContext());
                Log.d(TAG, "CowDetector created, initializing from: " + yoloPath);
                
                // Update dialog: Loading YOLO
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (loadingDialog != null && loadingDialog.isShowing()) {
                        loadingDialog.setMessage("Loading YOLO model (1/2)...");
                    }
                });
                
                boolean yoloLoaded = cowDetector.initialize(yoloPath);
                Log.d(TAG, "YOLO loaded: " + yoloLoaded);
                
                // Update dialog: Loading Weight Model
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (loadingDialog != null && loadingDialog.isShowing()) {
                        loadingDialog.setMessage("Loading Weight Model (2/2)...");
                    }
                });
                
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
                        // Dismiss loading dialog
                        if (loadingDialog != null && loadingDialog.isShowing()) {
                            loadingDialog.dismiss();
                        }
                        Toast.makeText(requireContext(), "✅ Models Ready!", Toast.LENGTH_SHORT).show();
                    } else {
                        Log.e(TAG, "❌ Failed to load models");
                        // Update dialog to error
                        if (loadingDialog != null && loadingDialog.isShowing()) {
                            loadingDialog.dismiss();
                        }
                        new AlertDialog.Builder(requireContext())
                                .setTitle("❌ Model Error")
                                .setMessage("Failed to load ML models")
                                .setPositiveButton("OK", null)
                                .show();
                        btnDetect.setText("❌ Model Error");
                        btnDetect.setEnabled(false);
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "=== EXCEPTION in model initialization ===", e);
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (loadingDialog != null && loadingDialog.isShowing()) {
                        loadingDialog.dismiss();
                    }
                    new AlertDialog.Builder(requireContext())
                            .setTitle("❌ Model Error")
                            .setMessage("Error: " + e.getMessage())
                            .setPositiveButton("OK", null)
                            .show();
                    btnDetect.setText("❌ Model Error");
                    btnDetect.setEnabled(false);
                });
            }
        });
    }
    
    /**
     * Copy file from assets to internal storage
     */
    private void copyAssetToFile(String assetFileName, String destFilePath) throws java.io.IOException {
        java.io.InputStream in = requireContext().getAssets().open(assetFileName);
        java.io.OutputStream out = new java.io.FileOutputStream(destFilePath);
        
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        
        in.close();
        out.flush();
        out.close();
        
        Log.d(TAG, "✅ Copied " + assetFileName + " to " + destFilePath);
    }
    
    private void analyzeImage(@NonNull ImageProxy imageProxy) {
        Log.d(TAG, ">>> analyzeImage() CALLED - modelsInit=" + modelsInitialized + " isDetecting=" + isDetecting);
        
        // Skip if models not ready or already detecting
        if (!modelsInitialized || isDetecting) {
            Log.d(TAG, ">>> SKIPPED - models not ready or detecting");
            imageProxy.close();
            return;
        }
        
        Log.d(TAG, ">>> Processing frame...");
        
        try {
            // Convert ImageProxy to Bitmap
            Bitmap bitmap = imageProxyToBitmap(imageProxy);
            if (bitmap == null) {
                Log.e(TAG, ">>> Bitmap is NULL");
                imageProxy.close();
                return;
            }
            
            Log.d(TAG, String.format(">>> Bitmap OK: %dx%d", bitmap.getWidth(), bitmap.getHeight()));
            
            // Set image size for overlay coordinate scaling
            detectionOverlay.setImageSize(bitmap.getWidth(), bitmap.getHeight());
            
            // Run YOLO detection (bbox will be in original image coordinates)
            Log.d(TAG, ">>> Running YOLO detection...");
            List<CowDetector.Detection> detections = cowDetector.detectCows(bitmap);
            Log.d(TAG, String.format(">>> YOLO result: %d detections", detections.size()));
            
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
        final List<CowDetector.Detection> detectionsToPredict;
        synchronized (detectionLock) {
            if (latestDetections.isEmpty()) {
                showError("No cow detected");
                return;
            }
            // Copy current detections to use for prediction
            detectionsToPredict = new ArrayList<>(latestDetections);
        }
        
        // Prevent concurrent predictions - use isPredicting flag (not isDetecting)
        if (isPredicting) {
            Log.d(TAG, "Already predicting, ignoring click");
            return;
        }
        
        isPredicting = true;
        btnDetect.setEnabled(false);
        btnDetect.setText("⏳\n\nP\nR\nO\nC\nE\nS\nS");
        
        // Show processing dialog
        new Handler(Looper.getMainLooper()).post(() -> {
            if (processingDialog != null && processingDialog.isShowing()) {
                processingDialog.dismiss();
            }
            String cameraType = isUsingUsbCamera ? "GroundChat USB camera" : "built-in camera";
            processingDialog = new AlertDialog.Builder(requireContext())
                    .setTitle("⏳ Processing...")
                    .setMessage("Calculating weight from " + cameraType + "...")
                    .setCancelable(false)
                    .create();
            processingDialog.show();
        });
        
        Log.d(TAG, "=== performWeightPrediction START ===");
        Log.d(TAG, "isUsingUsbCamera: " + isUsingUsbCamera);
        Log.d(TAG, "LiDAR distance: " + currentLidarDistance + " cm");
        Log.d(TAG, "Detections to predict: " + detectionsToPredict.size());
        
        // Handle USB Camera vs Built-in Camera
        if (isUsingUsbCamera) {
            Log.d(TAG, "Using USB Camera path...");
            // Use last frame from USB camera
            Bitmap uvcBitmap = null;
            synchronized (uvcFrameLock) {
                if (lastUvcFrame != null && !lastUvcFrame.isRecycled()) {
                    uvcBitmap = lastUvcFrame.copy(lastUvcFrame.getConfig(), true);
                    Log.d(TAG, "Got UVC frame: " + uvcBitmap.getWidth() + "x" + uvcBitmap.getHeight());
                } else {
                    Log.e(TAG, "lastUvcFrame is null or recycled!");
                }
            }
            
            if (uvcBitmap == null) {
                showError("No frame from USB camera");
                resetDetectionState();
                return;
            }
            
            final Bitmap bitmap = uvcBitmap;
            
            // Use separate executor for prediction (cameraExecutor is busy with detection loop)
            predictionExecutor.execute(() -> {
                Log.d(TAG, "Calling processWeightPrediction for USB camera...");
                processWeightPrediction(bitmap, detectionsToPredict);
                // Note: resetDetectionState() is called inside processWeightPrediction
            });
            
        } else {
            // Use ImageCapture for built-in camera
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
                        
                        processWeightPrediction(bitmap, detectionsToPredict);
                        
                    } finally {
                        imageProxy.close();
                    }
                }
                
                @Override
                public void onError(@NonNull androidx.camera.core.ImageCaptureException exception) {
                    Log.e(TAG, "Image capture failed", exception);
                    showError("Image capture failed");
                    resetDetectionState();
                }
            });
        }
    }
    
    private void processWeightPrediction(Bitmap bitmap, List<CowDetector.Detection> detectionsToPredict) {
        Log.d(TAG, String.format("Processing image: %dx%d, using %d detections", 
            bitmap.getWidth(), bitmap.getHeight(), detectionsToPredict.size()));
        
        try {
            // Get distance in meters
            float distanceMeters = currentLidarDistance / 100f;
            
            // Predict weight for each detected cow
            List<DetectionOverlayView.DetectionResult> results = new ArrayList<>();
            for (CowDetector.Detection detection : detectionsToPredict) {
                try {
                    // Pass preview dimensions untuk bbox scaling
                    int previewWidth = detectionOverlay.getImageWidth();
                    int previewHeight = detectionOverlay.getImageHeight();
                    
                    CattleWeightPredictor.WeightResult weightResult = 
                            weightPredictor.predictWeight(bitmap, detection, 
                                    previewWidth, previewHeight, distanceMeters);
                    
                    DetectionOverlayView.DetectionResult result = 
                            new DetectionOverlayView.DetectionResult();
                    result.bbox = weightResult.scaledBbox;
                    result.confidence = detection.confidence;
                    result.weight = weightResult.weight;
                    result.normalizedWidth = weightResult.normalizedWidth;
                    result.normalizedHeight = weightResult.normalizedHeight;
                    result.normalizedArea = weightResult.normalizedArea;
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
                    
                    // Berat Badan (from model)
                    float bodyWeight = firstResult.weight;
                    tvEstimatedWeight.setText(String.format("Berat Badan: %.1f kg", bodyWeight));
                    
                    // Berat Karkas (50% - 60% range)
                    float carcassMin = bodyWeight * 0.50f;
                    float carcassMax = bodyWeight * 0.60f;
                    tvCarcassWeight.setText(String.format("Berat Karkas: %.1f - %.1f kg", carcassMin, carcassMax));
                    
                    tvConfidence.setText(String.format("Confidence: %.0f%%", firstResult.confidence * 100));
                    
                    // Save photo with detection overlay and metadata
                    saveDetectionPhoto(bitmap, firstResult, distanceMeters);
                }
                
                resetDetectionState();
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Error in weight prediction", e);
            showError("Prediction error: " + e.getMessage());
            resetDetectionState();
        }
    }
    
    private Bitmap imageProxyToBitmap(@NonNull ImageProxy imageProxy) {
        try {
            // Handle different ImageProxy formats
            ImageProxy.PlaneProxy[] planes = imageProxy.getPlanes();
            
            Log.d(TAG, String.format("ImageProxy format: %d, planes: %d, size: %dx%d", 
                imageProxy.getFormat(), planes.length, imageProxy.getWidth(), imageProxy.getHeight()));
            
            if (planes.length == 0) {
                Log.e(TAG, "No planes in ImageProxy");
                return null;
            }
            
            // For single plane (already in compressed format like JPEG)
            if (planes.length == 1) {
                ByteBuffer buffer = planes[0].getBuffer();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            }
            
            // For YUV format (3 planes)
            if (planes.length >= 3) {
                return yuv420ToBitmap(imageProxy);
            }
            
            Log.e(TAG, "Unsupported plane count: " + planes.length);
            return null;
            
        } catch (Exception e) {
            Log.e(TAG, "Error converting ImageProxy to Bitmap", e);
            return null;
        }
    }
    
    private Bitmap yuv420ToBitmap(ImageProxy imageProxy) {
        try {
            ImageProxy.PlaneProxy[] planes = imageProxy.getPlanes();
            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();
            
            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();
            
            byte[] nv21 = new byte[ySize + uSize + vSize];
            
            // Copy Y plane
            yBuffer.get(nv21, 0, ySize);
            
            // Handle U and V planes based on pixel stride
            int uvPixelStride = planes[1].getPixelStride();
            
            if (uvPixelStride == 1) {
                // Tightly packed - simple copy
                vBuffer.get(nv21, ySize, vSize);
                uBuffer.get(nv21, ySize + vSize, uSize);
            } else if (uvPixelStride == 2) {
                // Interleaved UV - de-interleave for NV21
                int uvRowStride = planes[1].getRowStride();
                int uvWidth = imageProxy.getWidth() / 2;
                int uvHeight = imageProxy.getHeight() / 2;
                
                int idxNv21 = ySize;
                for (int row = 0; row < uvHeight; row++) {
                    for (int col = 0; col < uvWidth; col++) {
                        int uvIndex = row * uvRowStride + col * uvPixelStride;
                        // NV21 format: VUVUVU...
                        nv21[idxNv21++] = vBuffer.get(uvIndex);
                        nv21[idxNv21++] = uBuffer.get(uvIndex);
                    }
                }
            } else {
                Log.w(TAG, "Unexpected UV pixel stride: " + uvPixelStride + ", attempting simple copy");
                // Fallback: try simple copy
                vBuffer.get(nv21, ySize, vSize);
                uBuffer.get(nv21, ySize + vSize, uSize);
            }
            
            YuvImage yuvImage = new YuvImage(nv21, 
                    ImageFormat.NV21,
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
            isPredicting = false;
            
            // Dismiss processing dialog
            if (processingDialog != null && processingDialog.isShowing()) {
                processingDialog.dismiss();
                processingDialog = null;
            }
            
            synchronized (detectionLock) {
                if (!latestDetections.isEmpty()) {
                    btnDetect.setEnabled(true);
                    btnDetect.setText("🔍\n\nD\nE\nT\nE\nC\nT");
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
        
        // Stop UVC detection loop
        isUsingUsbCamera = false;
        
        // Release ML models
        if (cowDetector != null) {
            cowDetector.release();
        }
        if (weightPredictor != null) {
            weightPredictor.release();
        }
        
        // Release USB camera
        if (uvcCameraManager != null) {
            uvcCameraManager.closeCamera();
        }
        
        // Release last UVC frame
        synchronized (uvcFrameLock) {
            if (lastUvcFrame != null && !lastUvcFrame.isRecycled()) {
                lastUvcFrame.recycle();
                lastUvcFrame = null;
            }
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
        if (predictionExecutor != null) {
            predictionExecutor.shutdown();
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
    
    private void saveDetectionPhoto(Bitmap originalBitmap, DetectionOverlayView.DetectionResult result, float distance) {
        try {
            // Create copy to draw on
            Bitmap bitmapWithOverlay = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
            Canvas canvas = new Canvas(bitmapWithOverlay);
            
            // result.bbox is already in captured image coordinates (scaled by predictWeight)
            // No need to scale again!
            
            Log.d(TAG, String.format("📐 Drawing bbox on captured image %dx%d",
                originalBitmap.getWidth(), originalBitmap.getHeight()));
            Log.d(TAG, String.format("   Bbox: [%.0f,%.0f,%.0f,%.0f] (%.0fx%.0f)",
                result.bbox.left, result.bbox.top, result.bbox.right, result.bbox.bottom,
                result.bbox.width(), result.bbox.height()));
            
            // Draw bounding box
            Paint boxPaint = new Paint();
            boxPaint.setColor(0xFF4CAF50); // Green
            boxPaint.setStyle(Paint.Style.STROKE);
            boxPaint.setStrokeWidth(12);
            canvas.drawRect(result.bbox, boxPaint);
            
            // Draw label background
            Paint textBgPaint = new Paint();
            textBgPaint.setColor(0xDD4CAF50);
            textBgPaint.setStyle(Paint.Style.FILL);
            
            // Draw text with size proportional to image resolution
            Paint textPaint = new Paint();
            textPaint.setColor(0xFFFFFFFF);
            // Scale text size based on image width (40px for 1080p, proportionally larger for 4K)
            float baseTextSize = 40f;
            float scaledTextSize = baseTextSize * (originalBitmap.getWidth() / 1080f);
            textPaint.setTextSize(scaledTextSize);
            textPaint.setAntiAlias(true);
            textPaint.setFakeBoldText(true);
            
            String label = String.format("%.1f kg (%.0f%%)", result.weight, result.confidence * 100);
            float textWidth = textPaint.measureText(label);
            float textX = result.bbox.left + 10;
            float textY = result.bbox.top - 10;
            
            canvas.drawRect(textX - 5, textY - 35, textX + textWidth + 5, textY + 5, textBgPaint);
            canvas.drawText(label, textX, textY, textPaint);
            
            // Save to app-specific Pictures directory (no permission needed)
            String timeStamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(new java.util.Date());
            String fileName = "COW_" + timeStamp + ".jpg";
            
            // Use app-specific directory: /storage/emulated/0/Android/data/com.capstone.cattleweight/files/Pictures/CattleWeight
            java.io.File picturesDir = new java.io.File(
                requireContext().getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES),
                "CattleWeight"
            );
            if (!picturesDir.exists()) {
                boolean created = picturesDir.mkdirs();
                Log.d(TAG, "📁 Created directory: " + picturesDir.getAbsolutePath() + " - Success: " + created);
            }
            
            java.io.File imageFile = new java.io.File(picturesDir, fileName);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(imageFile);
            bitmapWithOverlay.compress(Bitmap.CompressFormat.JPEG, 95, fos);
            fos.flush();
            fos.close();
            
            // Save metadata to text file
            String metadataFileName = "COW_" + timeStamp + "_metadata.txt";
            java.io.File metadataFile = new java.io.File(picturesDir, metadataFileName);
            java.io.FileWriter writer = new java.io.FileWriter(metadataFile);
            writer.write("=== CATTLE WEIGHT DETECTION RESULT ===\\n");
            writer.write("Timestamp: " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date()) + "\\n");
            writer.write("\\n--- DETECTION INFO ---\\n");
            writer.write(String.format("Predicted Weight: %.2f kg\\n", result.weight));
            // Carcass weight calculation (50% - 60% range)
            float carcassMin = result.weight * 0.50f;
            float carcassMax = result.weight * 0.60f;
            writer.write(String.format("Carcass Weight: %.2f - %.2f kg (50%% - 60%%)\\n", carcassMin, carcassMax));
            writer.write(String.format("Confidence: %.1f%%\\n", result.confidence * 100));
            writer.write(String.format("Distance (LiDAR): %.2f meters (%.0f cm)\\n", distance, distance * 100));
            writer.write("\\n--- BOUNDING BOX (in saved image) ---\\n");
            writer.write(String.format("Left: %.0f px\\n", result.bbox.left));
            writer.write(String.format("Top: %.0f px\\n", result.bbox.top));
            writer.write(String.format("Right: %.0f px\\n", result.bbox.right));
            writer.write(String.format("Bottom: %.0f px\\n", result.bbox.bottom));
            writer.write(String.format("Width: %.0f px\\n", result.bbox.width()));
            writer.write(String.format("Height: %.0f px\\n", result.bbox.height()));
            writer.write(String.format("Area: %.0f px²\\n", result.bbox.width() * result.bbox.height()));
            writer.write("\\n--- NORMALIZED BBOX (for model input) ---\\n");
            writer.write(String.format("Normalized Width: %.2f px (at training resolution)\\n", result.normalizedWidth));
            writer.write(String.format("Normalized Height: %.2f px (at training resolution)\\n", result.normalizedHeight));
            writer.write(String.format("Normalized Area: %.0f px² (at training resolution)\\n", result.normalizedArea));
            writer.write(String.format("Size Feature: %.2f (area × distance²)\\n", result.normalizedArea * distance * distance));
            writer.write("\\n--- IMAGE INFO ---\\n");
            writer.write(String.format("Image Resolution: %dx%d\\n", originalBitmap.getWidth(), originalBitmap.getHeight()));
            writer.write(String.format("Saved File: %s\\n", fileName));
            writer.write(String.format("Saved Path: %s\\n", imageFile.getAbsolutePath()));
            writer.close();
            
            bitmapWithOverlay.recycle();
            
            Log.i(TAG, "✅ Photo saved: " + imageFile.getAbsolutePath());
            new Handler(Looper.getMainLooper()).post(() -> {
                Toast.makeText(requireContext(), "📸 Saved: " + imageFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
            });
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to save photo", e);
        }
    }
}
