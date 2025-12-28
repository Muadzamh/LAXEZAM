package com.capstone.cattleweight;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Custom overlay view to draw bounding boxes and labels on camera preview
 */
public class DetectionOverlayView extends View {
    
    private Paint boxPaint;
    private Paint textPaint;
    private Paint backgroundPaint;
    
    private List<DetectionResult> detections = new ArrayList<>();
    
    // Image dimensions for coordinate scaling
    private int imageWidth = 1;
    private int imageHeight = 1;
    
    public static class DetectionResult {
        public RectF bbox;
        public float confidence;
        public float weight; // in kg
        public float normalizedWidth;  // Bbox width normalized to training resolution
        public float normalizedHeight; // Bbox height normalized to training resolution
        public float normalizedArea;   // Bbox area normalized to training resolution
        
        public DetectionResult() {
            // Default constructor
        }
        
        public DetectionResult(RectF bbox, float confidence, float weight) {
            this.bbox = bbox;
            this.confidence = confidence;
            this.weight = weight;
        }
    }
    
    public DetectionOverlayView(Context context) {
        super(context);
        init();
    }
    
    public DetectionOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    private void init() {
        // Bounding box paint
        boxPaint = new Paint();
        boxPaint.setColor(Color.GREEN);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(6f);
        
        // Text paint
        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(40f);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setAntiAlias(true);
        
        // Background paint for text
        backgroundPaint = new Paint();
        backgroundPaint.setColor(Color.argb(180, 0, 128, 0)); // Semi-transparent green
        backgroundPaint.setStyle(Paint.Style.FILL);
    }
    
    /**
     * Update detections to display
     */
    public void setDetections(List<DetectionResult> detections) {
        this.detections = new ArrayList<>(detections);
        postInvalidate(); // Redraw
    }
    
    /**
     * Set image dimensions for coordinate scaling
     */
    public void setImageSize(int width, int height) {
        this.imageWidth = width;
        this.imageHeight = height;
    }
    
    /**
     * Get image width for bbox scaling calculations
     */
    public int getImageWidth() {
        return imageWidth;
    }
    
    /**
     * Get image height for bbox scaling calculations
     */
    public int getImageHeight() {
        return imageHeight;
    }
    
    /**
     * Clear all detections
     */
    public void clearDetections() {
        this.detections.clear();
        postInvalidate();
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        if (detections.isEmpty()) {
            return;
        }
        
        // Log scaling info (only once)
        float scaleX = (float) getWidth() / imageWidth;
        float scaleY = (float) getHeight() / imageHeight;
        Log.d("DetectionOverlay", String.format("Drawing %d detections. View: %dx%d, Image: %dx%d, Scale: %.3fx%.3f",
            detections.size(), getWidth(), getHeight(), imageWidth, imageHeight, scaleX, scaleY));
        
        for (DetectionResult detection : detections) {
            // Scale from image coordinates to view coordinates
            // Bbox is in original camera image space, need to scale to view size
            
            RectF scaledBbox = new RectF(
                detection.bbox.left * scaleX,
                detection.bbox.top * scaleY,
                detection.bbox.right * scaleX,
                detection.bbox.bottom * scaleY
            );
            
            Log.d("DetectionOverlay", String.format("  Draw: orig[%.0f,%.0f,%.0f,%.0f] -> scaled[%.0f,%.0f,%.0f,%.0f]",
                detection.bbox.left, detection.bbox.top, detection.bbox.right, detection.bbox.bottom,
                scaledBbox.left, scaledBbox.top, scaledBbox.right, scaledBbox.bottom));
            
            // Draw bounding box
            canvas.drawRect(scaledBbox, boxPaint);
            
            // Only draw label if weight > 0 (prediction done)
            if (detection.weight > 0) {
                // Prepare label text with weight
                String label = String.format(Locale.US, "🐄 %.1f kg (%.0f%%)", 
                        detection.weight, detection.confidence * 100);
                
                // Measure text size
                float textWidth = textPaint.measureText(label);
                float textHeight = textPaint.getTextSize();
                
                // Draw background rectangle for text
                float padding = 10f;
                RectF textBg = new RectF(
                        scaledBbox.left,
                        scaledBbox.top - textHeight - padding * 2,
                        scaledBbox.left + textWidth + padding * 2,
                        scaledBbox.top
                );
                canvas.drawRect(textBg, backgroundPaint);
                
                // Draw text
                canvas.drawText(label, 
                        scaledBbox.left + padding, 
                        scaledBbox.top - padding, 
                        textPaint);
            } else {
                // Just show "Cow Detected" without weight
                String label = String.format(Locale.US, "🐄 Detected (%.0f%%)", 
                        detection.confidence * 100);
                
                float textWidth = textPaint.measureText(label);
                float textHeight = textPaint.getTextSize();
                float padding = 10f;
                
                RectF textBg = new RectF(
                        scaledBbox.left,
                        scaledBbox.top - textHeight - padding * 2,
                        scaledBbox.left + textWidth + padding * 2,
                        scaledBbox.top
                );
                canvas.drawRect(textBg, backgroundPaint);
                
                canvas.drawText(label, 
                        scaledBbox.left + padding, 
                        scaledBbox.top - padding, 
                        textPaint);
            }
        }
    }
}
