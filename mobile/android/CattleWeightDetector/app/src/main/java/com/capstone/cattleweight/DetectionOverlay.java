package com.capstone.cattleweight;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom overlay untuk menampilkan bounding box deteksi YOLO
 */
public class DetectionOverlay extends View {
    
    private Paint boxPaint;
    private Paint textPaint;
    private Paint backgroundPaint;
    private List<DetectionResult> detections;
    
    public DetectionOverlay(Context context) {
        super(context);
        init();
    }
    
    public DetectionOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    private void init() {
        // Paint untuk bounding box
        boxPaint = new Paint();
        boxPaint.setColor(Color.GREEN);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(8f);
        
        // Paint untuk teks label
        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(40f);
        textPaint.setAntiAlias(true);
        textPaint.setStyle(Paint.Style.FILL);
        
        // Paint untuk background teks
        backgroundPaint = new Paint();
        backgroundPaint.setColor(Color.argb(180, 0, 0, 0));
        backgroundPaint.setStyle(Paint.Style.FILL);
        
        detections = new ArrayList<>();
    }
    
    /**
     * Update detections yang akan digambar
     */
    public void setDetections(List<DetectionResult> detections) {
        this.detections = detections;
        invalidate(); // Redraw
    }
    
    /**
     * Clear semua detections
     */
    public void clearDetections() {
        this.detections.clear();
        invalidate();
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        if (detections == null || detections.isEmpty()) {
            return;
        }
        
        for (DetectionResult detection : detections) {
            // Gambar bounding box
            canvas.drawRect(detection.box, boxPaint);
            
            // Gambar label dengan background
            String label = String.format("%s %.0f%%", detection.label, detection.confidence * 100);
            float textWidth = textPaint.measureText(label);
            float textHeight = textPaint.descent() - textPaint.ascent();
            
            // Background untuk teks
            RectF textBackground = new RectF(
                detection.box.left,
                detection.box.top - textHeight - 10,
                detection.box.left + textWidth + 20,
                detection.box.top
            );
            canvas.drawRect(textBackground, backgroundPaint);
            
            // Teks label
            canvas.drawText(
                label,
                detection.box.left + 10,
                detection.box.top - 15,
                textPaint
            );
        }
    }
    
    /**
     * Class untuk menyimpan hasil deteksi
     */
    public static class DetectionResult {
        public RectF box;           // Bounding box (left, top, right, bottom)
        public String label;        // Label class (e.g., "Sapi", "Cow")
        public float confidence;    // Confidence score (0-1)
        public int classId;         // Class ID
        
        public DetectionResult(RectF box, String label, float confidence, int classId) {
            this.box = box;
            this.label = label;
            this.confidence = confidence;
            this.classId = classId;
        }
        
        /**
         * Get bbox width in pixels
         */
        public float getWidth() {
            return box.right - box.left;
        }
        
        /**
         * Get bbox height in pixels
         */
        public float getHeight() {
            return box.bottom - box.top;
        }
        
        /**
         * Get bbox area in pixels
         */
        public float getArea() {
            return getWidth() * getHeight();
        }
    }
}
