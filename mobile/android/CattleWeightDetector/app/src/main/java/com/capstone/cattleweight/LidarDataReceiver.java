package com.capstone.cattleweight;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;

import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Class untuk menerima data LiDAR dari Flask server
 * Menggunakan polling untuk mendapatkan data terbaru
 */
public class LidarDataReceiver {
    
    private static final String TAG = "LidarDataReceiver";
    private static final long POLLING_INTERVAL_MS = 100; // Poll setiap 100ms
    
    private final String serverUrl;
    private final LidarDataCallback callback;
    private final OkHttpClient client;
    private final Handler handler;
    private final Gson gson;
    
    private boolean isReceiving = false;
    private boolean isConnected = false;
    
    public interface LidarDataCallback {
        void onDataReceived(LidarData data);
        void onConnectionStatusChanged(boolean connected);
        void onError(String error);
    }
    
    public LidarDataReceiver(String serverUrl, LidarDataCallback callback) {
        this.serverUrl = serverUrl;
        this.callback = callback;
        this.handler = new Handler(Looper.getMainLooper());
        this.gson = new Gson();
        
        // Configure OkHttp client dengan timeout
        this.client = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .build();
    }
    
    /**
     * Mulai menerima data LiDAR
     */
    public void startReceiving() {
        if (isReceiving) {
            Log.w(TAG, "Already receiving data");
            return;
        }
        
        isReceiving = true;
        Log.d(TAG, "Starting to receive LiDAR data from: " + serverUrl);
        pollLidarData();
    }
    
    /**
     * Berhenti menerima data LiDAR
     */
    public void stopReceiving() {
        isReceiving = false;
        handler.removeCallbacksAndMessages(null);
        Log.d(TAG, "Stopped receiving LiDAR data");
    }
    
    /**
     * Polling data dari server
     */
    private void pollLidarData() {
        if (!isReceiving) {
            return;
        }
        
        String endpoint = serverUrl + "/api/lidar";
        Request request = new Request.Builder()
                .url(endpoint)
                .get()
                .build();
        
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Request failed: " + e.getMessage());
                
                if (isConnected) {
                    isConnected = false;
                    callback.onConnectionStatusChanged(false);
                }
                
                callback.onError("Connection failed: " + e.getMessage());
                
                // Retry setelah delay lebih lama jika gagal
                handler.postDelayed(() -> pollLidarData(), 1000);
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    callback.onError("Server error: " + response.code());
                    handler.postDelayed(() -> pollLidarData(), 1000);
                    return;
                }
                
                try {
                    String responseBody = response.body().string();
                    JSONObject json = new JSONObject(responseBody);
                    
                    // Parse data
                    LidarData data = new LidarData(
                            json.getInt("jarak"),
                            json.getInt("kekuatan"),
                            json.getDouble("suhu"),
                            json.getLong("timestamp"),
                            json.getString("status")
                    );
                    
                    // Update connection status
                    boolean newConnectionStatus = data.isConnected();
                    if (newConnectionStatus != isConnected) {
                        isConnected = newConnectionStatus;
                        callback.onConnectionStatusChanged(isConnected);
                    }
                    
                    // Kirim data ke callback
                    callback.onDataReceived(data);
                    
                    Log.d(TAG, "Data received: " + data.toString());
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing JSON: " + e.getMessage());
                    callback.onError("Parse error: " + e.getMessage());
                }
                
                // Schedule next poll
                handler.postDelayed(() -> pollLidarData(), POLLING_INTERVAL_MS);
            }
        });
    }
    
    /**
     * Check status koneksi ke server
     */
    public void checkServerStatus() {
        String endpoint = serverUrl + "/api/status";
        Request request = new Request.Builder()
                .url(endpoint)
                .get()
                .build();
        
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Status check failed: " + e.getMessage());
                callback.onError("Server not reachable");
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    Log.d(TAG, "Server status: " + responseBody);
                    
                    try {
                        JSONObject json = new JSONObject(responseBody);
                        boolean lidarConnected = json.getBoolean("lidar_connected");
                        callback.onConnectionStatusChanged(lidarConnected);
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing status: " + e.getMessage());
                    }
                }
            }
        });
    }
}
