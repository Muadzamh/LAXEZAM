package com.capstone.cattleweight;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.util.Log;

import ai.onnxruntime.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * YOLO Detector for Cow Detection
 * Uses YOLOv8n ONNX model to detect cows (class 19 in COCO dataset)
 */
public class CowDetector {
    
    private static final String TAG = "CowDetector";
    private static final int INPUT_SIZE = 640; // YOLOv8 input size
    private static final float CONFIDENCE_THRESHOLD = 0.25f;
    private static final float IOU_THRESHOLD = 0.45f;
    private static final int COW_CLASS_INDEX = 19; // COCO dataset cow class
    
    private OrtEnvironment ortEnvironment;
    private OrtSession ortSession;
    private final Context context;
    
    // Thread safety for release during inference
    private volatile boolean isReleased = false;
    private final Object sessionLock = new Object();
    
    // Letterbox preprocessing info (for reverse coordinate transformation)
    private float lastScale = 1.0f;
    private int lastOffsetX = 0;
    private int lastOffsetY = 0;
    
    public static class Detection {
        public RectF bbox; // Bounding box [left, top, right, bottom]
        public float confidence;
        public int classId;
        
        public Detection(RectF bbox, float confidence, int classId) {
            this.bbox = bbox;
            this.confidence = confidence;
            this.classId = classId;
        }
        
        public float getWidth() {
            return bbox.width();
        }
        
        public float getHeight() {
            return bbox.height();
        }
        
        public float getArea() {
            return bbox.width() * bbox.height();
        }
    }
    
    public CowDetector(Context context) {
        this.context = context;
    }
    
    /**
     * Initialize ONNX model from file path
     */
    public boolean initialize(String modelPath) {
        try {
            Log.d(TAG, "Initializing YOLO model: " + modelPath);
            
            ortEnvironment = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
            
            // Try to use NNAPI for hardware acceleration
            try {
                opts.addNnapi();
                Log.d(TAG, "NNAPI acceleration enabled");
            } catch (Exception e) {
                Log.w(TAG, "NNAPI not available, using CPU");
            }
            
            // Load model from file
            File modelFile = new File(modelPath);
            if (!modelFile.exists()) {
                Log.e(TAG, "Model file not found: " + modelPath);
                return false;
            }
            
            ortSession = ortEnvironment.createSession(modelPath, opts);
            
            Log.i(TAG, "✅ YOLO model loaded successfully from: " + modelPath);
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to load YOLO model", e);
            return false;
        }
    }
    
    /**
     * Detect cows in image
     * @param bitmap Input image
     * @return List of cow detections (bounding boxes)
     */
    public List<Detection> detectCows(Bitmap bitmap) {
        // Check if already released
        if (isReleased) {
            Log.w(TAG, "Detector already released, skipping inference");
            return Collections.emptyList();
        }
        
        synchronized (sessionLock) {
            if (ortSession == null || isReleased) {
                Log.w(TAG, "Model not initialized or released");
                return Collections.emptyList();
            }
            
            try {
                long startTime = System.currentTimeMillis();
                
                // Preprocess image
                float[] inputData = preprocessImage(bitmap);
                
                // Create input tensor
                long[] inputShape = {1, 3, INPUT_SIZE, INPUT_SIZE};
                OnnxTensor inputTensor = OnnxTensor.createTensor(
                        ortEnvironment, 
                        FloatBuffer.wrap(inputData), 
                        inputShape
                );
                
                // Run inference
                Map<String, OnnxTensor> inputs = new HashMap<>();
                inputs.put("images", inputTensor); // YOLOv8 input name
                
                // Double-check before running
                if (isReleased || ortSession == null) {
                    inputTensor.close();
                    Log.w(TAG, "Session released during inference setup");
                    return Collections.emptyList();
                }
                
                OrtSession.Result results = ortSession.run(inputs);
            
            // Parse output
            List<Detection> detections = parseYoloOutput(results, bitmap.getWidth(), bitmap.getHeight());
            
            // Cleanup
            inputTensor.close();
            results.close();
            
            // Debug: Log all detections found
            Log.d(TAG, String.format("Total detections before filtering: %d", detections.size()));
            
            // Filter only cow detections
            List<Detection> cowDetections = new ArrayList<>();
            for (Detection det : detections) {
                Log.d(TAG, String.format("Detection: class=%d, conf=%.2f, bbox=[%.0f,%.0f,%.0f,%.0f] size=%.0fx%.0f", 
                        det.classId, det.confidence, 
                        det.bbox.left, det.bbox.top, det.bbox.right, det.bbox.bottom,
                        det.bbox.width(), det.bbox.height()));
                
                if (det.classId == COW_CLASS_INDEX && det.confidence >= CONFIDENCE_THRESHOLD) {
                    cowDetections.add(det);
                    Log.i(TAG, String.format("✓ COW FOUND! Conf: %.1f%%, BBox: [%.0f, %.0f, %.0f, %.0f], Size: %.0fx%.0f px", 
                            det.confidence * 100,
                            det.bbox.left, det.bbox.top, det.bbox.right, det.bbox.bottom,
                            det.bbox.width(), det.bbox.height()));
                }
            }
            
            long endTime = System.currentTimeMillis();
            Log.d(TAG, String.format("Detected %d cows in %d ms", cowDetections.size(), (endTime - startTime)));
            
            return cowDetections;
            
            } catch (Exception e) {
                Log.e(TAG, "Detection failed", e);
                return Collections.emptyList();
            }
        } // end synchronized
    }
    
    /**
     * Preprocess image for YOLO input with letterbox resize
     * Maintains aspect ratio by adding padding (like YOLOv8 training)
     */
    private float[] preprocessImage(Bitmap bitmap) {
        // Calculate scale to fit image into INPUT_SIZE x INPUT_SIZE while maintaining aspect ratio
        lastScale = Math.min(
            (float) INPUT_SIZE / bitmap.getWidth(),
            (float) INPUT_SIZE / bitmap.getHeight()
        );
        
        int scaledWidth = Math.round(bitmap.getWidth() * lastScale);
        int scaledHeight = Math.round(bitmap.getHeight() * lastScale);
        
        // Resize maintaining aspect ratio
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true);
        
        // Create square bitmap with letterbox (padding)
        Bitmap letterboxed = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(letterboxed);
        canvas.drawColor(Color.rgb(114, 114, 114)); // Gray padding (YOLO default)
        
        // Center the scaled image and save offsets
        lastOffsetX = (INPUT_SIZE - scaledWidth) / 2;
        lastOffsetY = (INPUT_SIZE - scaledHeight) / 2;
        canvas.drawBitmap(scaledBitmap, lastOffsetX, lastOffsetY, null);
        
        Log.d(TAG, String.format("Letterbox: scale=%.3f, offset=(%d,%d), scaled=(%d,%d)", 
                lastScale, lastOffsetX, lastOffsetY, scaledWidth, scaledHeight));
        
        scaledBitmap.recycle();
        
        // Convert to float array
        float[] inputData = new float[3 * INPUT_SIZE * INPUT_SIZE];
        int[] pixels = new int[INPUT_SIZE * INPUT_SIZE];
        letterboxed.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);
        
        // Convert to CHW format (channels first) and normalize
        for (int i = 0; i < INPUT_SIZE; i++) {
            for (int j = 0; j < INPUT_SIZE; j++) {
                int pixel = pixels[i * INPUT_SIZE + j];
                
                // Extract RGB and normalize to [0, 1]
                float r = ((pixel >> 16) & 0xFF) / 255.0f;
                float g = ((pixel >> 8) & 0xFF) / 255.0f;
                float b = (pixel & 0xFF) / 255.0f;
                
                // CHW format: [R channel][G channel][B channel]
                inputData[i * INPUT_SIZE + j] = r;  // R channel
                inputData[INPUT_SIZE * INPUT_SIZE + i * INPUT_SIZE + j] = g;  // G channel
                inputData[2 * INPUT_SIZE * INPUT_SIZE + i * INPUT_SIZE + j] = b;  // B channel
            }
        }
        
        letterboxed.recycle();
        
        return inputData;
    }
    
    /**
     * Parse YOLO output and apply NMS
     */
    private List<Detection> parseYoloOutput(OrtSession.Result results, int originalWidth, int originalHeight) {
        try {
            // Get output tensor
            OnnxValue outputValue = results.get(0);
            
            // YOLOv8 output shape: [1, 84, 8400]
            // 84 = 4 bbox coords + 80 class scores
            float[][][] output = (float[][][]) outputValue.getValue();
            
            List<Detection> detections = new ArrayList<>();
            
            int numDetections = output[0][0].length; // 8400
            
            // Debug: Track max confidence across all detections
            float globalMaxConf = 0;
            int globalMaxClass = -1;
            int cowDetectionsFound = 0;
            int passedThreshold = 0;
            
            // Parse detections
            for (int i = 0; i < numDetections; i++) {
                // Extract bbox (x_center, y_center, width, height)
                float x_center = output[0][0][i];
                float y_center = output[0][1][i];
                float width = output[0][2][i];
                float height = output[0][3][i];
                
                // Find class with max confidence
                float maxConf = 0;
                int maxClass = -1;
                for (int c = 0; c < 80; c++) {
                    float conf = output[0][4 + c][i];
                    if (conf > maxConf) {
                        maxConf = conf;
                        maxClass = c;
                    }
                }
                
                // Track global max for debugging
                if (maxConf > globalMaxConf) {
                    globalMaxConf = maxConf;
                    globalMaxClass = maxClass;
                }
                
                // Track cow detections (even below threshold)
                if (maxClass == COW_CLASS_INDEX && maxConf > 0.1f) {
                    cowDetectionsFound++;
                    if (cowDetectionsFound <= 3) { // Log first 3 only
                        Log.d(TAG, String.format("Cow candidate #%d: conf=%.3f (threshold=%.2f)", 
                            cowDetectionsFound, maxConf, CONFIDENCE_THRESHOLD));
                    }
                }
                
                // Only keep cow detections above threshold
                if (maxClass == COW_CLASS_INDEX && maxConf >= CONFIDENCE_THRESHOLD) {
                    passedThreshold++;
                    
                    // YOLO output coordinates are NORMALIZED [0-1]
                    // Convert to pixel coordinates in 640x640 letterbox space first
                    float x_center_px = x_center * INPUT_SIZE;
                    float y_center_px = y_center * INPUT_SIZE;
                    float width_px = width * INPUT_SIZE;
                    float height_px = height * INPUT_SIZE;
                    
                    // Log raw YOLO values untuk debugging transformasi
                    if (passedThreshold <= 2) { // Log 2 detections pertama
                        Log.d(TAG, String.format(">>> RAW YOLO [%d]: normalized=(%.2f,%.2f) size=(%.2fx%.2f)", 
                            i, x_center, y_center, width, height));
                        Log.d(TAG, String.format(">>> In pixels: center=(%.1f,%.1f) size=(%.1fx%.1f)", 
                            x_center_px, y_center_px, width_px, height_px));
                    }
                    
                    // Step 1: Convert from [x_center, y_center, w, h] to [x1, y1, x2, y2] in letterbox space
                    float x1_letterbox = x_center_px - width_px / 2.0f;
                    float y1_letterbox = y_center_px - height_px / 2.0f;
                    float x2_letterbox = x_center_px + width_px / 2.0f;
                    float y2_letterbox = y_center_px + height_px / 2.0f;
                    
                    // Step 2: Remove letterbox padding offset
                    // Coordinates dikurangi offset untuk mendapatkan posisi di scaled image
                    float x1_scaled = x1_letterbox - lastOffsetX;
                    float y1_scaled = y1_letterbox - lastOffsetY;
                    float x2_scaled = x2_letterbox - lastOffsetX;
                    float y2_scaled = y2_letterbox - lastOffsetY;
                    
                    // Step 3: Scale back to original image size
                    // Bagi dengan scale factor untuk kembali ke ukuran asli
                    float left = x1_scaled / lastScale;
                    float top = y1_scaled / lastScale;
                    float right = x2_scaled / lastScale;
                    float bottom = y2_scaled / lastScale;
                    
                    // Debug coordinate transformation
                    if (passedThreshold <= 2) { // Log 2 detections pertama
                        Log.d(TAG, String.format(">>> TRANSFORM [%d]: lastScale=%.4f, lastOffset=(%d,%d)", 
                            i, lastScale, lastOffsetX, lastOffsetY));
                        Log.d(TAG, String.format(">>> Letterbox corners: (%.2f,%.2f)-(%.2f,%.2f)", 
                            x1_letterbox, y1_letterbox, x2_letterbox, y2_letterbox));
                        Log.d(TAG, String.format(">>> After offset removal: (%.2f,%.2f)-(%.2f,%.2f)", 
                            x1_scaled, y1_scaled, x2_scaled, y2_scaled));
                        Log.d(TAG, String.format(">>> Final (original space): (%.2f,%.2f)-(%.2f,%.2f)", 
                            left, top, right, bottom));
                    }
                    
                    // Clamp to image bounds
                    left = Math.max(0, Math.min(originalWidth, left));
                    top = Math.max(0, Math.min(originalHeight, top));
                    right = Math.max(0, Math.min(originalWidth, right));
                    bottom = Math.max(0, Math.min(originalHeight, bottom));
                    
                    RectF bbox = new RectF(left, top, right, bottom);
                    detections.add(new Detection(bbox, maxConf, maxClass));
                }
            }
            
            // Debug summary
            Log.d(TAG, String.format("YOLO parsing summary: globalMaxConf=%.3f (class=%d), cowCandidates=%d, passedThreshold=%d",
                globalMaxConf, globalMaxClass, cowDetectionsFound, passedThreshold));
            
            // Apply NMS (Non-Maximum Suppression)
            return applyNMS(detections, IOU_THRESHOLD);
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse YOLO output", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Non-Maximum Suppression to remove overlapping boxes
     */
    private List<Detection> applyNMS(List<Detection> detections, float iouThreshold) {
        // Sort by confidence (descending)
        Collections.sort(detections, (a, b) -> Float.compare(b.confidence, a.confidence));
        
        List<Detection> result = new ArrayList<>();
        boolean[] suppressed = new boolean[detections.size()];
        
        for (int i = 0; i < detections.size(); i++) {
            if (suppressed[i]) continue;
            
            Detection det1 = detections.get(i);
            result.add(det1);
            
            for (int j = i + 1; j < detections.size(); j++) {
                if (suppressed[j]) continue;
                
                Detection det2 = detections.get(j);
                float iou = calculateIoU(det1.bbox, det2.bbox);
                
                if (iou > iouThreshold) {
                    suppressed[j] = true;
                }
            }
        }
        
        return result;
    }
    
    /**
     * Calculate Intersection over Union
     */
    private float calculateIoU(RectF box1, RectF box2) {
        float x1 = Math.max(box1.left, box2.left);
        float y1 = Math.max(box1.top, box2.top);
        float x2 = Math.min(box1.right, box2.right);
        float y2 = Math.min(box1.bottom, box2.bottom);
        
        float intersection = Math.max(0, x2 - x1) * Math.max(0, y2 - y1);
        float area1 = box1.width() * box1.height();
        float area2 = box2.width() * box2.height();
        float union = area1 + area2 - intersection;
        
        return union > 0 ? intersection / union : 0;
    }
    
    public void release() {
        // Set flag first to stop any ongoing/new inference
        isReleased = true;
        
        synchronized (sessionLock) {
            try {
                if (ortSession != null) {
                    ortSession.close();
                    ortSession = null;
                }
                Log.d(TAG, "YOLO detector released");
            } catch (Exception e) {
                Log.e(TAG, "Error releasing detector", e);
            }
        }
    }
}
