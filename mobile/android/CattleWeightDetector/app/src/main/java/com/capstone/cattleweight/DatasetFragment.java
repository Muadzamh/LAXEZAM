package com.capstone.cattleweight;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.view.TextureView;
import androidx.appcompat.widget.SwitchCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DatasetFragment extends Fragment {
    
    private static final String TAG = "DatasetFragment";
    private static final int CAMERA_PERMISSION_CODE = 101;
    private static final String SERVER_URL = "http://192.168.1.100:5000";
    
    // UI Components
    private PreviewView cameraPreview;
    private TextureView uvcCameraView;
    private FloatingActionButton btnCapture;
    private SwitchCompat switchLidarMode;
    private SwitchCompat switchCameraMode;
    private TextView tvCameraStatus, tvSaveStatus, tvDistance, tvSignalStrength, tvTemperature;
    private TextView tvConnectionStatus, tvTimestamp, tvDatasetCount;
    
    // Camera - Built-in (CameraX)
    private Camera camera;
    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;
    private CameraSelector currentCameraSelector;
    private int currentCameraIndex = 0;
    private java.util.List<androidx.camera.core.CameraInfo> availableCameras;
    
    // Camera - USB (UVC)
    private UvcCameraManager uvcCameraManager;
    private boolean isUsingUsbCamera = false;
    
    // LiDAR - WiFi mode
    private LidarDataReceiver lidarReceiver;
    // LiDAR - USB mode
    private UsbSerialLidarReader usbLidarReader;
    private LidarData currentLidarData;
    private boolean isUsbMode = false;
    
    // Database
    private CattleDatasetDatabase database;
    
    private int datasetCount = 0;
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, 
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dataset, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        database = new CattleDatasetDatabase(requireContext());
        initializeViews(view);
        cameraExecutor = Executors.newSingleThreadExecutor();
        
        if (checkCameraPermission()) {
            startCamera();
        } else {
            requestCameraPermission();
        }
        
        initializeLidarReceiver();
        loadDatasetCount();
        
        btnCapture.setOnClickListener(v -> captureAndSaveData());
        
        // Initialize UVC camera here instead of in initializeViews
        // This gives USB system time to enumerate devices
        initializeUvcCamera();
    }
    
    private void initializeViews(View view) {
        cameraPreview = view.findViewById(R.id.cameraPreview);
        uvcCameraView = view.findViewById(R.id.uvcCameraView);
        btnCapture = view.findViewById(R.id.btnCapture);
        switchLidarMode = view.findViewById(R.id.switchLidarMode);
        switchCameraMode = view.findViewById(R.id.switchCameraMode);
        tvCameraStatus = view.findViewById(R.id.tvCameraStatus);
        tvSaveStatus = view.findViewById(R.id.tvSaveStatus);
        tvDistance = view.findViewById(R.id.tvDistance);
        tvSignalStrength = view.findViewById(R.id.tvSignalStrength);
        tvTemperature = view.findViewById(R.id.tvTemperature);
        tvConnectionStatus = view.findViewById(R.id.tvConnectionStatus);
        tvTimestamp = view.findViewById(R.id.tvTimestamp);
        tvDatasetCount = view.findViewById(R.id.tvDatasetCount);
        
        // Set switch listener
        switchLidarMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isUsbMode = isChecked;
            switchLidarMode();
        });
        
        // Camera switch listener - switch between built-in and USB camera
        if (switchCameraMode != null) {
            switchCameraMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
                isUsingUsbCamera = isChecked;
                switchCameraSource();
            });
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
    
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = 
                ProcessCameraProvider.getInstance(requireContext());
        
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                
                // Enumerate all available cameras
                availableCameras = cameraProvider.getAvailableCameraInfos();
                Log.d(TAG, "Available cameras: " + availableCameras.size());
                
                // Update camera status to show number of cameras
                if (availableCameras.size() > 1 && switchCameraMode != null) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        switchCameraMode.setVisibility(View.VISIBLE);
                        tvCameraStatus.setText("📷 Cameras: " + availableCameras.size());
                    });
                }
                
                // Use default back camera initially
                currentCameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                currentCameraIndex = 0;
                
                bindCamera(cameraProvider);
                
            } catch (Exception e) {
                Log.e(TAG, "Camera initialization failed", e);
                tvCameraStatus.setText("📷 Camera Error");
                tvCameraStatus.setTextColor(0xFFF44336);
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }
    
    private void bindCamera(ProcessCameraProvider cameraProvider) {
        try {
            Preview preview = new Preview.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .build();
            preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());
            
            imageCapture = new ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetResolution(new android.util.Size(960, 1280))
                    .build();
            
            cameraProvider.unbindAll();
            camera = cameraProvider.bindToLifecycle(
                    getViewLifecycleOwner(), currentCameraSelector, preview, imageCapture);
            
            tvCameraStatus.setText("📷 Camera Active");
            tvCameraStatus.setTextColor(0xFF4CAF50);
            
        } catch (Exception e) {
            Log.e(TAG, "Camera binding failed", e);
            tvCameraStatus.setText("📷 Camera Error");
            tvCameraStatus.setTextColor(0xFFF44336);
        }
    }
    
    private void switchCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = 
                ProcessCameraProvider.getInstance(requireContext());
        
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                
                // Cycle through available cameras
                if (availableCameras != null && availableCameras.size() > 1) {
                    currentCameraIndex = (currentCameraIndex + 1) % availableCameras.size();
                    
                    // Get camera selector for the selected camera
                    androidx.camera.core.CameraInfo selectedCamera = availableCameras.get(currentCameraIndex);
                    
                    // Try to create selector from available cameras
                    // First check if it's front or back camera
                    try {
                        Integer lensFacing = selectedCamera.getLensFacing();
                        if (lensFacing != null) {
                            if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                                currentCameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
                            } else if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                currentCameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                            } else {
                                // External camera (USB) - use camera filter
                                currentCameraSelector = new CameraSelector.Builder()
                                        .addCameraFilter(cameraInfos -> {
                                            java.util.List<androidx.camera.core.CameraInfo> filtered = new java.util.ArrayList<>();
                                            for (androidx.camera.core.CameraInfo info : cameraInfos) {
                                                if (info == selectedCamera) {
                                                    filtered.add(info);
                                                }
                                            }
                                            return filtered;
                                        })
                                        .build();
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error getting lens facing", e);
                    }
                    
                    // Rebind camera
                    bindCamera(cameraProvider);
                    
                    String cameraType = getCameraName(selectedCamera);
                    tvCameraStatus.setText("📷 " + cameraType + " (" + (currentCameraIndex + 1) + "/" + availableCameras.size() + ")");
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Camera switch failed", e);
                Toast.makeText(requireContext(), "Failed to switch camera", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }
    
    private String getCameraName(androidx.camera.core.CameraInfo cameraInfo) {
        try {
            Integer lensFacing = cameraInfo.getLensFacing();
            if (lensFacing != null) {
                if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                    return "Front Camera";
                } else if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                    return "Back Camera";
                } else {
                    return "External Camera";
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting camera name", e);
        }
        return "Camera " + (currentCameraIndex + 1);
    }
    
    /**
     * Initialize UVC camera for USB camera (GroundChat)
     */
    private void initializeUvcCamera() {
        if (uvcCameraManager == null) {
            uvcCameraManager = new UvcCameraManager(requireContext());
            uvcCameraManager.initialize(uvcCameraView, new UvcCameraManager.UvcCameraCallback() {
                @Override
                public void onCameraConnected() {
                    Log.d(TAG, "USB Camera connected");
                    new Handler(Looper.getMainLooper()).post(() -> {
                        // Show switch if USB camera is available
                        if (switchCameraMode != null) {
                            switchCameraMode.setVisibility(View.VISIBLE);
                        }
                        Toast.makeText(requireContext(), "GroundChat camera detected", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onCameraDisconnected() {
                    Log.d(TAG, "USB Camera disconnected");
                    new Handler(Looper.getMainLooper()).post(() -> {
                        // Switch back to built-in camera if disconnected
                        if (isUsingUsbCamera) {
                            switchCameraMode.setChecked(false);
                        }
                        // Hide switch if no USB camera
                        if (switchCameraMode != null && uvcCameraManager != null && !uvcCameraManager.isConnected()) {
                            switchCameraMode.setVisibility(View.GONE);
                        }
                    });
                }

                @Override
                public void onCameraError(String error) {
                    Log.e(TAG, "USB Camera error: " + error);
                    new Handler(Looper.getMainLooper()).post(() -> {
                        Toast.makeText(requireContext(), "USB Camera error: " + error, Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onPreviewStarted() {
                    Log.d(TAG, "USB Camera preview started");
                    new Handler(Looper.getMainLooper()).post(() -> {
                        tvCameraStatus.setText("📷 GroundChat Camera");
                        tvCameraStatus.setTextColor(0xFF4CAF50);
                    });
                }
            });
        }
    }
    
    /**
     * Switch between built-in camera (CameraX) and USB camera (UVC)
     */
    private void switchCameraSource() {
        if (isUsingUsbCamera) {
            // Switch to USB camera
            if (uvcCameraManager != null && uvcCameraManager.isConnected()) {
                // Hide CameraX preview
                cameraPreview.setVisibility(View.GONE);
                uvcCameraView.setVisibility(View.VISIBLE);
                
                // Stop CameraX
                if (camera != null) {
                    ListenableFuture<ProcessCameraProvider> cameraProviderFuture = 
                            ProcessCameraProvider.getInstance(requireContext());
                    cameraProviderFuture.addListener(() -> {
                        try {
                            ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                            cameraProvider.unbindAll();
                        } catch (Exception e) {
                            Log.e(TAG, "Error unbinding CameraX", e);
                        }
                    }, ContextCompat.getMainExecutor(requireContext()));
                }
                
                // Start UVC preview
                uvcCameraManager.startPreview();
                tvCameraStatus.setText("📷 GroundChat Camera");
            } else {
                // No USB camera connected, switch back
                switchCameraMode.setChecked(false);
                Toast.makeText(requireContext(), "GroundChat camera not connected", Toast.LENGTH_SHORT).show();
            }
        } else {
            // Switch to built-in camera
            cameraPreview.setVisibility(View.VISIBLE);
            uvcCameraView.setVisibility(View.GONE);
            
            // Stop UVC
            if (uvcCameraManager != null) {
                uvcCameraManager.stopPreview();
            }
            
            // Restart CameraX
            startCamera();
        }
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
                currentLidarData = data;
                new Handler(Looper.getMainLooper()).post(() -> updateLidarUI(data));
            }
            
            @Override
            public void onConnectionStatusChanged(boolean connected) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (connected) {
                        tvConnectionStatus.setText("🟢 Connected via USB");
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
    
    private void initializeLidarReceiver() {
        lidarReceiver = new LidarDataReceiver(SERVER_URL, new LidarDataReceiver.LidarDataCallback() {
            @Override
            public void onDataReceived(LidarData data) {
                currentLidarData = data;
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
    
    private void updateLidarUI(LidarData data) {
        tvDistance.setText(data.getJarak() + " cm");
        tvSignalStrength.setText(String.valueOf(data.getKekuatan()));
        tvTemperature.setText(String.format("%.1f°C", data.getSuhu()));
        tvTimestamp.setText("Last update: " + data.getFormattedTimestamp());
    }
    
    private void captureAndSaveData() {
        if (currentLidarData == null) {
            Toast.makeText(requireContext(), "LiDAR not ready!", Toast.LENGTH_SHORT).show();
            return;
        }
        
        tvSaveStatus.setVisibility(View.VISIBLE);
        tvSaveStatus.setText("📸 Capturing...");
        tvSaveStatus.setTextColor(0xFFFFC107);
        btnCapture.setEnabled(false);
        
        // Check which camera is being used
        if (isUsingUsbCamera && uvcCameraManager != null && uvcCameraManager.isPreviewing()) {
            // Capture from USB camera
            captureFromUsbCamera();
        } else if (imageCapture != null) {
            // Capture from built-in camera
            captureFromBuiltInCamera();
        } else {
            Toast.makeText(requireContext(), "Camera not ready!", Toast.LENGTH_SHORT).show();
            btnCapture.setEnabled(true);
        }
    }
    
    /**
     * Capture photo from built-in camera (CameraX)
     */
    private void captureFromBuiltInCamera() {
        imageCapture.takePicture(cameraExecutor, new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy image) {
                saveToGallery(image, currentLidarData);
                image.close();
            }
            
            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e(TAG, "Image capture failed", exception);
                new Handler(Looper.getMainLooper()).post(() -> {
                    tvSaveStatus.setText("❌ Capture Failed");
                    tvSaveStatus.setTextColor(0xFFF44336);
                    btnCapture.setEnabled(true);
                });
            }
        });
    }
    
    /**
     * Capture photo from USB camera (GroundChat)
     */
    private void captureFromUsbCamera() {
        uvcCameraManager.captureImage(new UvcCameraManager.OnImageCapturedListener() {
            @Override
            public void onImageCaptured(Bitmap bitmap) {
                Log.d(TAG, "USB camera image captured");
                saveUsbCameraToGallery(bitmap, currentLidarData);
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "USB camera capture failed: " + error);
                new Handler(Looper.getMainLooper()).post(() -> {
                    tvSaveStatus.setText("❌ USB Capture Failed");
                    tvSaveStatus.setTextColor(0xFFF44336);
                    btnCapture.setEnabled(true);
                    Toast.makeText(requireContext(), "Failed: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    private void saveToGallery(ImageProxy image, LidarData lidarData) {
        try {
            // Convert ImageProxy to Bitmap
            Bitmap bitmap = imageProxyToBitmap(image);
            
            // Save metadata to database first to get ID
            long id = saveMetadataToDatabase(null, lidarData);
            
            if (id <= 0) {
                throw new Exception("Failed to save metadata to database");
            }
            
            // Create filename with format: id_cattle_[jarak]_[signalstrength]_
            String filename = id + "_cattle_" + lidarData.getJarak() + "_" + 
                            lidarData.getKekuatan() + "_.jpg";
            
            new Handler(Looper.getMainLooper()).post(() -> {
                tvSaveStatus.setText("💾 Saving...");
            });
            
            // Convert bitmap to JPEG bytes
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            byte[] imageBytes = out.toByteArray();
            
            // Save to Gallery using MediaStore
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CattleDataset");
                values.put(MediaStore.Images.Media.IS_PENDING, 1);
            }
            
            Uri uri = requireContext().getContentResolver().insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            
            if (uri != null) {
                OutputStream outputStream = requireContext().getContentResolver().openOutputStream(uri);
                if (outputStream != null) {
                    outputStream.write(imageBytes);
                    outputStream.close();
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        values.clear();
                        values.put(MediaStore.Images.Media.IS_PENDING, 0);
                        requireContext().getContentResolver().update(uri, values, null, null);
                    }
                    
                    // Update image path in database
                    database.updateImagePath(id, uri.toString());
                    
                    // Show success message
                    datasetCount++;
                    new Handler(Looper.getMainLooper()).post(() -> {
                        tvSaveStatus.setText("✅ Saved!");
                        tvSaveStatus.setTextColor(0xFF4CAF50);
                        tvDatasetCount.setText("📊 Total Data: " + datasetCount);
                        btnCapture.setEnabled(true);
                        Toast.makeText(requireContext(), "Data saved successfully!", 
                                Toast.LENGTH_SHORT).show();
                        
                        // Hide status after 2 seconds
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            tvSaveStatus.setVisibility(View.GONE);
                        }, 2000);
                    });
                } else {
                    throw new Exception("Failed to open output stream");
                }
            } else {
                throw new Exception("Failed to create MediaStore entry");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Save failed", e);
            new Handler(Looper.getMainLooper()).post(() -> {
                tvSaveStatus.setText("❌ Save Failed");
                tvSaveStatus.setTextColor(0xFFF44336);
                btnCapture.setEnabled(true);
                Toast.makeText(requireContext(), "Save failed: " + e.getMessage(), 
                        Toast.LENGTH_SHORT).show();
            });
        }
    }
    
    /**
     * Save USB camera bitmap to gallery
     */
    private void saveUsbCameraToGallery(Bitmap bitmap, LidarData lidarData) {
        try {
            // Save metadata first to get ID
            long id = saveMetadataToDatabase(null, lidarData);
            
            if (id <= 0) {
                throw new Exception("Failed to save metadata to database");
            }
            
            // Create filename with database ID
            String filename = id + "_cattle_" + lidarData.getJarak() + "_" + 
                             lidarData.getKekuatan() + "_.jpg";
            
            new Handler(Looper.getMainLooper()).post(() -> {
                tvSaveStatus.setText("💾 Saving...");
            });
            
            // Convert bitmap to JPEG bytes
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            byte[] imageBytes = out.toByteArray();
            
            // Save to MediaStore
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CattleDataset");
                values.put(MediaStore.Images.Media.IS_PENDING, 1);
            }
            
            Uri collection = MediaStore.Images.Media.getContentUri(
                    MediaStore.VOLUME_EXTERNAL_PRIMARY);
            Uri uri = requireContext().getContentResolver().insert(collection, values);
            
            if (uri != null) {
                OutputStream outputStream = requireContext().getContentResolver().openOutputStream(uri);
                if (outputStream != null) {
                    outputStream.write(imageBytes);
                    outputStream.close();
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        values.clear();
                        values.put(MediaStore.Images.Media.IS_PENDING, 0);
                        requireContext().getContentResolver().update(uri, values, null, null);
                    }
                    
                    // Update database with image path
                    database.updateImagePath(id, uri.toString());
                    
                    datasetCount++;
                    new Handler(Looper.getMainLooper()).post(() -> {
                        tvSaveStatus.setText("✅ USB Camera Saved!");
                        tvSaveStatus.setTextColor(0xFF4CAF50);
                        tvDatasetCount.setText("📊 Total Data: " + datasetCount);
                        btnCapture.setEnabled(true);
                        Toast.makeText(requireContext(), "GroundChat photo saved!", 
                                Toast.LENGTH_SHORT).show();
                        
                        // Hide status after 2 seconds
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            tvSaveStatus.setVisibility(View.GONE);
                        }, 2000);
                    });
                } else {
                    throw new Exception("Failed to open output stream");
                }
            } else {
                throw new Exception("Failed to create MediaStore entry");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "USB camera save failed", e);
            new Handler(Looper.getMainLooper()).post(() -> {
                tvSaveStatus.setText("❌ USB Save Failed");
                tvSaveStatus.setTextColor(0xFFF44336);
                btnCapture.setEnabled(true);
                Toast.makeText(requireContext(), "Save failed: " + e.getMessage(), 
                        Toast.LENGTH_SHORT).show();
            });
        }
    }
    
    private long saveMetadataToDatabase(String imagePath, LidarData lidarData) {
        long id = database.insertDataset(
                imagePath,
                lidarData.getJarak(),
                lidarData.getKekuatan(),
                lidarData.getSuhu()
        );
        return id;
    }
    
    private void loadDatasetCount() {
        datasetCount = database.getDatasetCount();
        new Handler(Looper.getMainLooper()).post(() -> {
            tvDatasetCount.setText("📊 Total Data: " + datasetCount);
        });
    }
    
    private Bitmap imageProxyToBitmap(ImageProxy image) {
        ImageProxy.PlaneProxy planeProxy = image.getPlanes()[0];
        ByteBuffer buffer = planeProxy.getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        
        // Rotate bitmap based on image rotation info
        int rotationDegrees = image.getImageInfo().getRotationDegrees();
        if (rotationDegrees != 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(rotationDegrees);
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), 
                    bitmap.getHeight(), matrix, true);
        }
        
        return bitmap;
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // Register USB monitor when fragment resumes
        if (uvcCameraManager != null) {
            uvcCameraManager.registerUSB();
        }
    }
    
    @Override
    public void onPause() {
        super.onPause();
        // Unregister USB monitor when fragment pauses
        if (uvcCameraManager != null) {
            uvcCameraManager.unregisterUSB();
        }
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Stop LiDAR receivers FIRST before cleaning up resources
        if (lidarReceiver != null) {
            lidarReceiver.stopReceiving();
            lidarReceiver = null;
        }
        if (usbLidarReader != null) {
            usbLidarReader.stopReading();
            usbLidarReader = null;
        }
        // Clean up camera resources
        if (uvcCameraManager != null) {
            uvcCameraManager.release();
            uvcCameraManager = null;
        }
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
            cameraExecutor = null;
        }
    }
}
