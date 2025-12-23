package com.capstone.cattleweight;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

public class MainActivityNew extends AppCompatActivity {
    
    private static final String TAG = "MainActivityNew";
    private BottomNavigationView bottomNav;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_new);
        
        // ✅ CHECK USB DEVICES saat app start
        Log.d(TAG, "╔═══════════════════════════════════════════╗");
        Log.d(TAG, "║  CATTLE WEIGHT DETECTOR - USB CHECK       ║");
        Log.d(TAG, "╚═══════════════════════════════════════════╝");
        checkUsbDevices();
        
        bottomNav = findViewById(R.id.bottom_navigation);
        
        // Set default fragment
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new DetectionFragment())
                    .commit();
        }
        
        // Bottom navigation listener
        bottomNav.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                Fragment selectedFragment = null;
                
                int itemId = item.getItemId();
                if (itemId == R.id.navigation_detection) {
                    selectedFragment = new DetectionFragment();
                } else if (itemId == R.id.navigation_dataset) {
                    selectedFragment = new DatasetFragment();
                }
                
                if (selectedFragment != null) {
                    getSupportFragmentManager().beginTransaction()
                            .replace(R.id.fragment_container, selectedFragment)
                            .commit();
                    return true;
                }
                
                return false;
            }
        });
    }
    
    /**
     * Check semua USB device yang tercolok ke HP
     * Hasil akan ditampilkan di Logcat dengan tag "UvcCameraManager"
     */
    private void checkUsbDevices() {
        try {
            Log.d(TAG, "Initializing USB device check...");
            UvcCameraManager usbChecker = new UvcCameraManager(this);
            usbChecker.listAllUsbDevices();
            Log.d(TAG, "✅ USB device check completed!");
            Log.d(TAG, "📋 Check log with tag 'UvcCameraManager' for details");
        } catch (Exception e) {
            Log.e(TAG, "❌ Error checking USB devices: " + e.getMessage(), e);
        }
    }
}
