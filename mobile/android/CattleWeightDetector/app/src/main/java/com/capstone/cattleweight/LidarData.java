package com.capstone.cattleweight;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Data class untuk menyimpan informasi dari LiDAR TF Luna
 */
public class LidarData {
    private int jarak;          // Jarak dalam cm
    private int kekuatan;       // Kekuatan sinyal
    private double suhu;        // Suhu chip dalam Celsius
    private long timestamp;     // Timestamp dalam milliseconds
    private String status;      // Status koneksi
    
    public LidarData() {
        this.jarak = 0;
        this.kekuatan = 0;
        this.suhu = 0.0;
        this.timestamp = 0;
        this.status = "disconnected";
    }
    
    public LidarData(int jarak, int kekuatan, double suhu, long timestamp, String status) {
        this.jarak = jarak;
        this.kekuatan = kekuatan;
        this.suhu = suhu;
        this.timestamp = timestamp;
        this.status = status;
    }
    
    // Getters
    public int getJarak() {
        return jarak;
    }
    
    public int getKekuatan() {
        return kekuatan;
    }
    
    public double getSuhu() {
        return suhu;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public String getStatus() {
        return status;
    }
    
    // Setters
    public void setJarak(int jarak) {
        this.jarak = jarak;
    }
    
    public void setKekuatan(int kekuatan) {
        this.kekuatan = kekuatan;
    }
    
    public void setSuhu(double suhu) {
        this.suhu = suhu;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    /**
     * Mendapatkan jarak dalam format meter
     */
    public double getJarakInMeters() {
        return jarak / 100.0;
    }
    
    /**
     * Mengecek apakah data valid
     */
    public boolean isValid() {
        return jarak > 0 && jarak < 800 && kekuatan > 0;
    }
    
    /**
     * Mengecek apakah koneksi aktif
     */
    public boolean isConnected() {
        return "connected".equals(status);
    }
    
    /**
     * Format timestamp menjadi string readable
     */
    public String getFormattedTimestamp() {
        if (timestamp == 0) {
            return "--";
        }
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }
    
    @Override
    public String toString() {
        return "LidarData{" +
                "jarak=" + jarak + " cm" +
                ", kekuatan=" + kekuatan +
                ", suhu=" + suhu + "°C" +
                ", timestamp=" + getFormattedTimestamp() +
                ", status='" + status + '\'' +
                '}';
    }
}
