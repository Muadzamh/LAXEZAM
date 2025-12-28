package com.capstone.cattleweight;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.util.Log;

import ai.onnxruntime.*;

import java.io.File;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Cattle Weight Predictor
 * Uses CNN ONNX model to predict cattle weight based on:
 * 1. Cropped image of cattle (from YOLO bbox), resized to 224x224 with ImageNet normalization
 * 2. Size feature: (bbox_width_px * bbox_height_px) * (lidar_distance_meters ^ 2)
 *    Note: Formula uses MULTIPLICATION, not division (as per training data)
 */
public class CattleWeightPredictor {
    
    private static final String TAG = "WeightPredictor";
    private static final int IMAGE_SIZE = 224; // CNN input size
    
    // Normalization constants (ImageNet standard)
    private static final float[] MEAN = {0.485f, 0.456f, 0.406f};
    private static final float[] STD = {0.229f, 0.224f, 0.225f};
    
    private OrtEnvironment ortEnvironment;
    private OrtSession ortSession;
    private final Context context;
    
    public static class WeightResult {
        public float weight; // Predicted weight in kg
        public float confidence; // Confidence score (0-1)
        public android.graphics.RectF scaledBbox;  // Bbox in captured image coordinates
        public float normalizedWidth;  // Bbox width normalized to training resolution
        public float normalizedHeight; // Bbox height normalized to training resolution
        public float normalizedArea;   // Bbox area normalized to training resolution
        
        public WeightResult(float weight, float confidence) {
            this.weight = weight;
            this.confidence = confidence;
            this.scaledBbox = null;
        }
        
        public WeightResult(float weight, float confidence, android.graphics.RectF scaledBbox) {
            this.weight = weight;
            this.confidence = confidence;
            this.scaledBbox = scaledBbox;
        }
        
        public WeightResult(float weight, float confidence, android.graphics.RectF scaledBbox,
                          float normalizedWidth, float normalizedHeight, float normalizedArea) {
            this.weight = weight;
            this.confidence = confidence;
            this.scaledBbox = scaledBbox;
            this.normalizedWidth = normalizedWidth;
            this.normalizedHeight = normalizedHeight;
            this.normalizedArea = normalizedArea;
        }
    }
    
    public CattleWeightPredictor(Context context) {
        this.context = context;
    }
    
    /**
     * Initialize ONNX model
     */
    public boolean initialize(String modelPath) {
        try {
            Log.d(TAG, "Initializing Weight Predictor model: " + modelPath);
            
            ortEnvironment = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
            
            // Try to use NNAPI for hardware acceleration
            try {
                opts.addNnapi();
                Log.d(TAG, "NNAPI acceleration enabled");
            } catch (Exception e) {
                Log.w(TAG, "NNAPI not available, using CPU");
            }
            
            // Load model
            File modelFile = new File(modelPath);
            if (!modelFile.exists()) {
                Log.e(TAG, "Model file not found: " + modelPath);
                return false;
            }
            
            ortSession = ortEnvironment.createSession(modelPath, opts);
            
            Log.i(TAG, "✅ Weight Predictor model loaded successfully");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to load Weight Predictor model", e);
            return false;
        }
    }
    
    /**
     * Predict cattle weight
     * 
     * @param originalImage Original camera frame (captured high-res image)
     * @param detection YOLO detection result (bounding box in PREVIEW coordinates)
     * @param previewWidth Width of preview image where detection was performed
     * @param previewHeight Height of preview image where detection was performed
     * @param lidarDistanceMeters Distance from LiDAR in meters
     * @return Weight prediction result with scaled bbox
     */
    public WeightResult predictWeight(Bitmap originalImage, CowDetector.Detection detection,
                                     int previewWidth, int previewHeight,
                                     float lidarDistanceMeters) {
        if (ortSession == null) {
            Log.w(TAG, "Model not initialized");
            return new WeightResult(0, 0, null);
        }
        
        try {
            long startTime = System.currentTimeMillis();
            
            // CRITICAL: Detection bbox is in PREVIEW coordinates, but originalImage is CAPTURED resolution
            // We must scale bbox to captured resolution first!
            int capturedWidth = originalImage.getWidth();
            int capturedHeight = originalImage.getHeight();
            
            float scaleX = (float) capturedWidth / previewWidth;
            float scaleY = (float) capturedHeight / previewHeight;
            
            // Scale bbox to captured image coordinates
            android.graphics.RectF scaledBbox = new android.graphics.RectF(
                detection.bbox.left * scaleX,
                detection.bbox.top * scaleY,
                detection.bbox.right * scaleX,
                detection.bbox.bottom * scaleY
            );
            
            Log.d(TAG, "========== BBOX SCALING ==========");
            Log.d(TAG, String.format("Preview: %dx%d, Captured: %dx%d", previewWidth, previewHeight, capturedWidth, capturedHeight));
            Log.d(TAG, String.format("Scale: %.3fx%.3f", scaleX, scaleY));
            Log.d(TAG, String.format("Bbox Preview: [%.0f,%.0f,%.0f,%.0f] (%.0fx%.0f)",
                detection.bbox.left, detection.bbox.top, detection.bbox.right, detection.bbox.bottom,
                detection.bbox.width(), detection.bbox.height()));
            Log.d(TAG, String.format("Bbox Scaled:  [%.0f,%.0f,%.0f,%.0f] (%.0fx%.0f)",
                scaledBbox.left, scaledBbox.top, scaledBbox.right, scaledBbox.bottom,
                scaledBbox.width(), scaledBbox.height()));
            
            // 1. Crop image based on SCALED bounding box
            Bitmap croppedCow = cropBitmap(originalImage, scaledBbox);
            
            // 2. Preprocess cropped image (resize + normalize)
            float[] imageInput = preprocessImage(croppedCow);
            croppedCow.recycle();
            
            // 3. Calculate size feature
            // Formula dari training: bbox_area_px * (distance_meters^2)
            // CRITICAL: Normalize bbox ke resolusi training!
            // Training menggunakan 1080x1440 landscape
            float bboxWidthPx = scaledBbox.width();  // Width in captured image pixels
            float bboxHeightPx = scaledBbox.height();  // Height in captured image pixels
            
            // Normalize ke resolusi training (1080x1440)
            float TRAINING_WIDTH = 1440f;
            float TRAINING_HEIGHT = 1080f;
            float normalizeScaleX = TRAINING_WIDTH / capturedWidth;
            float normalizeScaleY = TRAINING_HEIGHT / capturedHeight;
            
            // Normalize bbox ke resolusi training
            float normalizedWidth = bboxWidthPx * normalizeScaleX;
            float normalizedHeight = bboxHeightPx * normalizeScaleY;
            float normalizedAreaPx = normalizedWidth * normalizedHeight;
            
            // IMPORTANT: Sesuai training, KALI bukan BAGI!
            float sizeFeature = normalizedAreaPx * (lidarDistanceMeters * lidarDistanceMeters);
            
            Log.d(TAG, "========== WEIGHT PREDICTION DEBUG ==========");
            Log.d(TAG, String.format("Input 1 - Cropped Image: %.0fx%.0f pixels (before resize to 224x224)", 
                    bboxWidthPx, bboxHeightPx));
            Log.d(TAG, String.format("Input 2 - Size Feature Calculation:"));
            Log.d(TAG, String.format("  Captured Image: %dx%d", capturedWidth, capturedHeight));
            Log.d(TAG, String.format("  Training Resolution: %.0fx%.0f (landscape)", TRAINING_WIDTH, TRAINING_HEIGHT));
            Log.d(TAG, String.format("  Normalize Scale: %.4fx%.4f", normalizeScaleX, normalizeScaleY));
            Log.d(TAG, String.format("  bbox_width_px (captured)     = %.2f", bboxWidthPx));
            Log.d(TAG, String.format("  bbox_height_px (captured)    = %.2f", bboxHeightPx));
            Log.d(TAG, String.format("  bbox_width_px (normalized)   = %.2f", normalizedWidth));
            Log.d(TAG, String.format("  bbox_height_px (normalized)  = %.2f", normalizedHeight));
            Log.d(TAG, String.format("  bbox_area_px (normalized)    = %.2f", normalizedAreaPx));
            Log.d(TAG, String.format("  distance_m     = %.3f", lidarDistanceMeters));
            Log.d(TAG, String.format("  distance_m^2   = %.6f", lidarDistanceMeters * lidarDistanceMeters));
            Log.d(TAG, String.format("  size_feature   = %.2f (normalized_area * distance^2)", sizeFeature));
            
            // 4. Create input tensors
            long[] imageShape = {1, 3, IMAGE_SIZE, IMAGE_SIZE};
            OnnxTensor imageTensor = OnnxTensor.createTensor(
                    ortEnvironment,
                    FloatBuffer.wrap(imageInput),
                    imageShape
            );
            
            // Size feature: 1D tensor with shape [1] - single float value
            float[] sizeInput = {sizeFeature};
            OnnxTensor sizeTensor = OnnxTensor.createTensor(
                    ortEnvironment,
                    FloatBuffer.wrap(sizeInput),
                    new long[]{1}  // Shape [1] not [1,1]
            );
            
            // 5. Run inference
            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put("image", imageTensor);
            inputs.put("size_feature", sizeTensor);
            
            OrtSession.Result results = ortSession.run(inputs);
            
            // 6. Parse output
            // Model output is 1D tensor: float[] with shape [1]
            float[] output = (float[]) results.get(0).getValue();
            float predictedWeight = output[0];
            
            Log.d(TAG, String.format("Model Output (raw): %.6f", predictedWeight));
            
            // Calculate confidence (simple heuristic: inverse of prediction uncertainty)
            // You can improve this based on your model's output
            float confidence = 0.85f; // Default confidence
            
            // Cleanup
            imageTensor.close();
            sizeTensor.close();
            results.close();
            
            long endTime = System.currentTimeMillis();
            Log.d(TAG, String.format("FINAL PREDICTION: %.2f kg (confidence: %.0f%%) in %d ms",
                    predictedWeight, confidence * 100, (endTime - startTime)));
            Log.d(TAG, "============================================");
            
            return new WeightResult(predictedWeight, confidence, scaledBbox,
                    normalizedWidth, normalizedHeight, normalizedAreaPx);
            
        } catch (Exception e) {
            Log.e(TAG, "Weight prediction failed", e);
            return new WeightResult(0, 0, null);
        }
    }
    
    /**
     * Crop bitmap based on bounding box
     */
    private Bitmap cropBitmap(Bitmap source, RectF bbox) {
        // Ensure bbox is within image bounds
        int left = Math.max(0, (int) bbox.left);
        int top = Math.max(0, (int) bbox.top);
        int right = Math.min(source.getWidth(), (int) bbox.right);
        int bottom = Math.min(source.getHeight(), (int) bbox.bottom);
        
        int width = right - left;
        int height = bottom - top;
        
        if (width <= 0 || height <= 0) {
            Log.w(TAG, "Invalid crop dimensions");
            return source;
        }
        
        return Bitmap.createBitmap(source, left, top, width, height);
    }
    
    /**
     * Preprocess image for CNN input
     * Resize to 224x224 and normalize with ImageNet mean/std
     */
    private float[] preprocessImage(Bitmap bitmap) {
        // Resize to IMAGE_SIZE x IMAGE_SIZE
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, IMAGE_SIZE, IMAGE_SIZE, true);
        
        float[] inputData = new float[3 * IMAGE_SIZE * IMAGE_SIZE];
        int[] pixels = new int[IMAGE_SIZE * IMAGE_SIZE];
        resized.getPixels(pixels, 0, IMAGE_SIZE, 0, 0, IMAGE_SIZE, IMAGE_SIZE);
        
        // Convert to CHW format and normalize
        for (int i = 0; i < IMAGE_SIZE; i++) {
            for (int j = 0; j < IMAGE_SIZE; j++) {
                int pixel = pixels[i * IMAGE_SIZE + j];
                
                // Extract RGB and normalize to [0, 1]
                float r = ((pixel >> 16) & 0xFF) / 255.0f;
                float g = ((pixel >> 8) & 0xFF) / 255.0f;
                float b = (pixel & 0xFF) / 255.0f;
                
                // Apply ImageNet normalization: (value - mean) / std
                r = (r - MEAN[0]) / STD[0];
                g = (g - MEAN[1]) / STD[1];
                b = (b - MEAN[2]) / STD[2];
                
                // CHW format
                inputData[i * IMAGE_SIZE + j] = r;
                inputData[IMAGE_SIZE * IMAGE_SIZE + i * IMAGE_SIZE + j] = g;
                inputData[2 * IMAGE_SIZE * IMAGE_SIZE + i * IMAGE_SIZE + j] = b;
            }
        }
        
        if (!resized.equals(bitmap)) {
            resized.recycle();
        }
        
        return inputData;
    }
    
    public void release() {
        try {
            if (ortSession != null) {
                ortSession.close();
                ortSession = null;
            }
            Log.d(TAG, "Weight Predictor released");
        } catch (Exception e) {
            Log.e(TAG, "Error releasing predictor", e);
        }
    }
}
