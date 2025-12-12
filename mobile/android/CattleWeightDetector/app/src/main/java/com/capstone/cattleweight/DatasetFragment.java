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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
    private FloatingActionButton btnCapture;
    private TextView tvCameraStatus, tvSaveStatus, tvDistance, tvSignalStrength, tvTemperature;
    private TextView tvConnectionStatus, tvTimestamp, tvDatasetCount;
    
    // Camera
    private Camera camera;
    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;
    
    // LiDAR
    private LidarDataReceiver lidarReceiver;
    private LidarData currentLidarData;
    
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
    }
    
    private void initializeViews(View view) {
        cameraPreview = view.findViewById(R.id.cameraPreview);
        btnCapture = view.findViewById(R.id.btnCapture);
        tvCameraStatus = view.findViewById(R.id.tvCameraStatus);
        tvSaveStatus = view.findViewById(R.id.tvSaveStatus);
        tvDistance = view.findViewById(R.id.tvDistance);
        tvSignalStrength = view.findViewById(R.id.tvSignalStrength);
        tvTemperature = view.findViewById(R.id.tvTemperature);
        tvConnectionStatus = view.findViewById(R.id.tvConnectionStatus);
        tvTimestamp = view.findViewById(R.id.tvTimestamp);
        tvDatasetCount = view.findViewById(R.id.tvDatasetCount);
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
                
                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();
                
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                cameraProvider.unbindAll();
                camera = cameraProvider.bindToLifecycle(
                        getViewLifecycleOwner(), cameraSelector, preview, imageCapture);
                
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
        if (imageCapture == null || currentLidarData == null) {
            Toast.makeText(requireContext(), "Camera or LiDAR not ready!", Toast.LENGTH_SHORT).show();
            return;
        }
        
        tvSaveStatus.setVisibility(View.VISIBLE);
        tvSaveStatus.setText("📸 Capturing...");
        tvSaveStatus.setTextColor(0xFFFFC107);
        btnCapture.setEnabled(false);
        
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
    
    private void saveToGallery(ImageProxy image, LidarData lidarData) {
        try {
            // Convert ImageProxy to Bitmap
            Bitmap bitmap = imageProxyToBitmap(image);
            
            // Create unique filename
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    .format(new Date());
            String filename = "cattle_" + timestamp + ".jpg";
            
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
                    
                    // Save metadata to SQLite
                    saveMetadataToDatabase(uri.toString(), lidarData);
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
    
    private void saveMetadataToDatabase(String imagePath, LidarData lidarData) {
        long id = database.insertDataset(
                imagePath,
                lidarData.getJarak(),
                lidarData.getKekuatan(),
                lidarData.getSuhu()
        );
        
        if (id > 0) {
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
            new Handler(Looper.getMainLooper()).post(() -> {
                tvSaveStatus.setText("❌ Database Error");
                tvSaveStatus.setTextColor(0xFFF44336);
                btnCapture.setEnabled(true);
                Toast.makeText(requireContext(), "Failed to save metadata", 
                        Toast.LENGTH_SHORT).show();
            });
        }
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
    public void onDestroyView() {
        super.onDestroyView();
        if (lidarReceiver != null) {
            lidarReceiver.stopReceiving();
        }
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }
}
