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
 * 1. Cropped image of cattle (from YOLO bbox)
 * 2. Size feature: (bbox_width * bbox_height) / (lidar_distance_meters ^ 2)
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
        
        public WeightResult(float weight, float confidence) {
            this.weight = weight;
            this.confidence = confidence;
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
     * @param originalImage Original camera frame
     * @param detection YOLO detection result (bounding box)
     * @param lidarDistanceMeters Distance from LiDAR in meters
     * @return Weight prediction result
     */
    public WeightResult predictWeight(Bitmap originalImage, CowDetector.Detection detection, float lidarDistanceMeters) {
        if (ortSession == null) {
            Log.w(TAG, "Model not initialized");
            return new WeightResult(0, 0);
        }
        
        try {
            long startTime = System.currentTimeMillis();
            
            // 1. Crop image based on bounding box
            Bitmap croppedCow = cropBitmap(originalImage, detection.bbox);
            
            // 2. Preprocess cropped image (resize + normalize)
            float[] imageInput = preprocessImage(croppedCow);
            croppedCow.recycle();
            
            // 3. Calculate size feature
            // Formula: (bbox_width * bbox_height) / (distance_meters^2)
            float bboxArea = detection.getWidth() * detection.getHeight();
            float sizeFeature = bboxArea / (lidarDistanceMeters * lidarDistanceMeters);
            
            Log.d(TAG, String.format("Size feature calculation: bbox_area=%.2f, distance=%.2fm, size_feature=%.2f", 
                    bboxArea, lidarDistanceMeters, sizeFeature));
            
            // 4. Create input tensors
            long[] imageShape = {1, 3, IMAGE_SIZE, IMAGE_SIZE};
            OnnxTensor imageTensor = OnnxTensor.createTensor(
                    ortEnvironment,
                    FloatBuffer.wrap(imageInput),
                    imageShape
            );
            
            long[] sizeShape = {1, 1};
            float[] sizeInput = {sizeFeature};
            OnnxTensor sizeTensor = OnnxTensor.createTensor(
                    ortEnvironment,
                    FloatBuffer.wrap(sizeInput),
                    sizeShape
            );
            
            // 5. Run inference
            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put("image", imageTensor);
            inputs.put("size_feature", sizeTensor);
            
            OrtSession.Result results = ortSession.run(inputs);
            
            // 6. Parse output
            float[][] output = (float[][]) results.get(0).getValue();
            float predictedWeight = output[0][0];
            
            // Calculate confidence (simple heuristic: inverse of prediction uncertainty)
            // You can improve this based on your model's output
            float confidence = 0.85f; // Default confidence
            
            // Cleanup
            imageTensor.close();
            sizeTensor.close();
            results.close();
            
            long endTime = System.currentTimeMillis();
            Log.d(TAG, String.format("Weight prediction: %.2f kg (confidence: %.2f%%) in %d ms",
                    predictedWeight, confidence * 100, (endTime - startTime)));
            
            return new WeightResult(predictedWeight, confidence);
            
        } catch (Exception e) {
            Log.e(TAG, "Weight prediction failed", e);
            return new WeightResult(0, 0);
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
