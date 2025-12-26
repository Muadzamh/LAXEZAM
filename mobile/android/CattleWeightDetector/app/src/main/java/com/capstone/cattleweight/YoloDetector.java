package com.capstone.cattleweight;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.util.Log;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

/**
 * YOLO Detector untuk deteksi sapi secara real-time
 * Menggunakan TensorFlow Lite
 */
public class YoloDetector {
    
    private static final String TAG = "YoloDetector";
    private static final String MODEL_FILE = "yolov8n_float32.tflite"; // Ubah sesuai nama model
    
    // YOLO parameters
    private static final int INPUT_SIZE = 640; // YOLOv8 default input size
    private static final float CONFIDENCE_THRESHOLD = 0.4f;
    private static final float IOU_THRESHOLD = 0.5f;
    
    // Class ID untuk sapi/cow (COCO dataset: cow = 19)
    private static final int COW_CLASS_ID = 19;
    
    private Interpreter interpreter;
    private Context context;
    
    public YoloDetector(Context context) {
        this.context = context;
        try {
            interpreter = new Interpreter(loadModelFile());
            Log.i(TAG, "YOLO model loaded successfully");
        } catch (IOException e) {
            Log.e(TAG, "Error loading YOLO model", e);
        }
    }
    
    /**
     * Load TFLite model dari assets
     */
    private MappedByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(MODEL_FILE);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }
    
    /**
     * Detect cows in bitmap image
     */
    public List<DetectionOverlay.DetectionResult> detect(Bitmap bitmap) {
        if (interpreter == null) {
            Log.e(TAG, "Interpreter is null");
            return new ArrayList<>();
        }
        
        // Preprocess image
        TensorImage inputImage = preprocessImage(bitmap);
        
        // Prepare output buffers
        float[][][] output = new float[1][25200][85]; // YOLOv8n output: [1, 25200, 85]
        
        // Run inference
        interpreter.run(inputImage.getBuffer(), output);
        
        // Post-process results
        List<DetectionOverlay.DetectionResult> detections = postprocess(
            output[0], 
            bitmap.getWidth(), 
            bitmap.getHeight()
        );
        
        return detections;
    }
    
    /**
     * Preprocess image untuk YOLO input
     */
    private TensorImage preprocessImage(Bitmap bitmap) {
        ImageProcessor imageProcessor = new ImageProcessor.Builder()
            .add(new ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
            .build();
        
        TensorImage tensorImage = new TensorImage();
        tensorImage.load(bitmap);
        return imageProcessor.process(tensorImage);
    }
    
    /**
     * Post-process YOLO output
     */
    private List<DetectionOverlay.DetectionResult> postprocess(
            float[][] output, int imgWidth, int imgHeight) {
        
        List<DetectionOverlay.DetectionResult> results = new ArrayList<>();
        
        // Parse YOLO output
        for (int i = 0; i < output.length; i++) {
            float[] detection = output[i];
            
            // Format: [cx, cy, w, h, confidence, class_scores...]
            float cx = detection[0];
            float cy = detection[1];
            float w = detection[2];
            float h = detection[3];
            float objectness = detection[4];
            
            // Get class scores (index 5 onwards)
            float maxClassScore = 0;
            int maxClassId = -1;
            for (int j = 5; j < detection.length; j++) {
                if (detection[j] > maxClassScore) {
                    maxClassScore = detection[j];
                    maxClassId = j - 5;
                }
            }
            
            float confidence = objectness * maxClassScore;
            
            // Filter: confidence threshold dan hanya cow class
            if (confidence < CONFIDENCE_THRESHOLD) continue;
            if (maxClassId != COW_CLASS_ID) continue;
            
            // Convert to absolute coordinates
            float scaleX = (float) imgWidth / INPUT_SIZE;
            float scaleY = (float) imgHeight / INPUT_SIZE;
            
            float left = (cx - w / 2) * scaleX;
            float top = (cy - h / 2) * scaleY;
            float right = (cx + w / 2) * scaleX;
            float bottom = (cy + h / 2) * scaleY;
            
            RectF box = new RectF(left, top, right, bottom);
            results.add(new DetectionOverlay.DetectionResult(box, "Sapi", confidence, maxClassId));
        }
        
        // Apply Non-Maximum Suppression
        results = applyNMS(results, IOU_THRESHOLD);
        
        return results;
    }
    
    /**
     * Non-Maximum Suppression untuk menghilangkan duplikat deteksi
     */
    private List<DetectionOverlay.DetectionResult> applyNMS(
            List<DetectionOverlay.DetectionResult> detections, float iouThreshold) {
        
        List<DetectionOverlay.DetectionResult> result = new ArrayList<>();
        
        // Sort by confidence (descending)
        detections.sort((a, b) -> Float.compare(b.confidence, a.confidence));
        
        boolean[] suppressed = new boolean[detections.size()];
        
        for (int i = 0; i < detections.size(); i++) {
            if (suppressed[i]) continue;
            
            result.add(detections.get(i));
            
            for (int j = i + 1; j < detections.size(); j++) {
                if (suppressed[j]) continue;
                
                float iou = calculateIoU(detections.get(i).box, detections.get(j).box);
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
        float intersectLeft = Math.max(box1.left, box2.left);
        float intersectTop = Math.max(box1.top, box2.top);
        float intersectRight = Math.min(box1.right, box2.right);
        float intersectBottom = Math.min(box1.bottom, box2.bottom);
        
        float intersectWidth = Math.max(0, intersectRight - intersectLeft);
        float intersectHeight = Math.max(0, intersectBottom - intersectTop);
        float intersectArea = intersectWidth * intersectHeight;
        
        float box1Area = (box1.right - box1.left) * (box1.bottom - box1.top);
        float box2Area = (box2.right - box2.left) * (box2.bottom - box2.top);
        float unionArea = box1Area + box2Area - intersectArea;
        
        return intersectArea / unionArea;
    }
    
    /**
     * Release resources
     */
    public void close() {
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
        }
    }
    
    /**
     * Get largest detection (untuk weight prediction)
     */
    public DetectionOverlay.DetectionResult getLargestDetection(
            List<DetectionOverlay.DetectionResult> detections) {
        if (detections.isEmpty()) return null;
        
        DetectionOverlay.DetectionResult largest = detections.get(0);
        float maxArea = largest.getArea();
        
        for (DetectionOverlay.DetectionResult detection : detections) {
            float area = detection.getArea();
            if (area > maxArea) {
                maxArea = area;
                largest = detection;
            }
        }
        
        return largest;
    }
}
