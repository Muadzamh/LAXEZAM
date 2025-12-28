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

public class DetectionFragment extends Fragment {
    
    private static final String TAG = "DetectionFragment";
    private static final int CAMERA_PERMISSION_CODE = 100;
    private static final int STORAGE_PERMISSION_CODE = 101;
    private static final String SERVER_URL = "http://192.168.0.100:5000"; // IP WiFi Server (unused in USB mode)
    
    // UI Components
    private PreviewView cameraPreview;
    private TextView tvCameraStatus, tvModelStatus, tvDistance, tvSignalStrength, tvTemperature;
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
            tvModelStatus = view.findViewById(R.id.tvModelStatus);
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
        
        // Show loading indicator
        new Handler(Looper.getMainLooper()).post(() -> {
            if (tvModelStatus != null) {
                tvModelStatus.setVisibility(View.VISIBLE);
                tvModelStatus.setText("⏳ Loading YOLO model...");
                tvModelStatus.setBackgroundColor(0xDDFF9800); // Orange
            }
        });
        
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
                
                // Update UI: Loading YOLO
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (tvModelStatus != null) {
                        tvModelStatus.setText("⏳ Loading YOLO (1/2)...");
                    }
                });
                
                boolean yoloLoaded = cowDetector.initialize(yoloPath);
                Log.d(TAG, "YOLO loaded: " + yoloLoaded);
                
                // Update UI: Loading Weight Model
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (tvModelStatus != null) {
                        tvModelStatus.setText("⏳ Loading Weight Model (2/2)...");
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
                        if (tvModelStatus != null) {
                            tvModelStatus.setText("✅ Models Ready");
                            tvModelStatus.setBackgroundColor(0xDD4CAF50); // Green
                            // Hide after 3 seconds
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                if (tvModelStatus != null) {
                                    tvModelStatus.setVisibility(View.GONE);
                                }
                            }, 3000);
                        }
                        Toast.makeText(requireContext(), "✅ Models loaded successfully", Toast.LENGTH_SHORT).show();
                    } else {
                        Log.e(TAG, "❌ Failed to load models");
                        if (tvModelStatus != null) {
                            tvModelStatus.setText("❌ Model Failed");
                            tvModelStatus.setBackgroundColor(0xDDF44336); // Red
                        }
                        btnDetect.setText("❌ Model Error");
                        btnDetect.setEnabled(false);
                        tvCameraStatus.setText("❌ Model Loading Failed");
                        Toast.makeText(requireContext(), "❌ Failed to load models", Toast.LENGTH_LONG).show();
                    }
                });
                
            } catch (Exception e) {
                Log.e(TAG, "=== EXCEPTION in model initialization ===", e);
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (tvModelStatus != null) {
                        tvModelStatus.setText("❌ Error: " + e.getMessage());
                        tvModelStatus.setBackgroundColor(0xDDF44336); // Red
                    }
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
        final List<CowDetector.Detection> detectionsToPredict;
        synchronized (detectionLock) {
            if (latestDetections.isEmpty()) {
                showError("No cow detected");
                return;
            }
            // Copy current detections to use for prediction
            detectionsToPredict = new ArrayList<>(latestDetections);
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
                    
                    Log.d(TAG, String.format("Captured image: %dx%d, using %d detections from preview", 
                        bitmap.getWidth(), bitmap.getHeight(), detectionsToPredict.size()));
                    
                    // Predict weight for each detected cow (from preview detections)
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
                            result.bbox = weightResult.scaledBbox;  // Use scaled bbox for accurate drawing
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
                            tvEstimatedWeight.setText(String.format("%.1f kg", firstResult.weight));
                            tvConfidence.setText(String.format("%.0f%%", firstResult.confidence * 100));
                            
                            // Save photo with detection overlay and metadata
                            saveDetectionPhoto(bitmap, firstResult, distanceMeters);
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
