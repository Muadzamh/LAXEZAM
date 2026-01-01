package com.capstone.cattleweight;

import java.io.File;

/**
 * Model class untuk item hasil deteksi bobot karkas
 */
public class ResultItem {
    private File imageFile;
    private File metadataFile;
    private String weight;
    private String distance;
    private String bboxWidth;
    private String bboxHeight;
    private long timestamp;
    private String fileName;
    
    public ResultItem() {
        this.weight = "-";
        this.distance = "-";
        this.bboxWidth = "-";
        this.bboxHeight = "-";
    }
    
    // Getters and Setters
    public File getImageFile() {
        return imageFile;
    }
    
    public void setImageFile(File imageFile) {
        this.imageFile = imageFile;
    }
    
    public File getMetadataFile() {
        return metadataFile;
    }
    
    public void setMetadataFile(File metadataFile) {
        this.metadataFile = metadataFile;
    }
    
    public String getWeight() {
        return weight;
    }
    
    public void setWeight(String weight) {
        this.weight = weight;
    }
    
    public String getDistance() {
        return distance;
    }
    
    public void setDistance(String distance) {
        this.distance = distance;
    }
    
    public String getBboxWidth() {
        return bboxWidth;
    }
    
    public void setBboxWidth(String bboxWidth) {
        this.bboxWidth = bboxWidth;
    }
    
    public String getBboxHeight() {
        return bboxHeight;
    }
    
    public void setBboxHeight(String bboxHeight) {
        this.bboxHeight = bboxHeight;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getFileName() {
        return fileName;
    }
    
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
    
    /**
     * Get formatted bbox size
     */
    public String getBboxSize() {
        return bboxWidth + " x " + bboxHeight + " px";
    }
}
