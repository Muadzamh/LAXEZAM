package com.capstone.cattleweight;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fragment untuk menampilkan daftar hasil deteksi bobot karkas
 */
public class ResultsFragment extends Fragment {
    
    private static final String TAG = "ResultsFragment";
    
    private RecyclerView recyclerResults;
    private LinearLayout layoutEmpty;
    private TextView txtResultCount;
    private ImageButton btnRefresh;
    
    private ResultsAdapter adapter;
    private List<ResultItem> resultList;
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_results, container, false);
        
        // Initialize views
        recyclerResults = view.findViewById(R.id.recyclerResults);
        layoutEmpty = view.findViewById(R.id.layoutEmpty);
        txtResultCount = view.findViewById(R.id.txtResultCount);
        btnRefresh = view.findViewById(R.id.btnRefresh);
        
        // Setup RecyclerView
        resultList = new ArrayList<>();
        adapter = new ResultsAdapter(requireContext(), resultList);
        recyclerResults.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerResults.setAdapter(adapter);
        
        // Set click listeners
        adapter.setOnResultClickListener(this::showDetailDialog);
        adapter.setOnDeleteClickListener(this::confirmDelete);
        
        btnRefresh.setOnClickListener(v -> loadResults());
        
        // Load results
        loadResults();
        
        return view;
    }
    
    @Override
    public void onResume() {
        super.onResume();
        loadResults(); // Refresh when fragment becomes visible
    }
    
    /**
     * Load detection results from storage directory
     */
    private void loadResults() {
        resultList.clear();
        
        File picturesDir = new File(
            requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            "CattleWeight"
        );
        
        Log.d(TAG, "Loading results from: " + picturesDir.getAbsolutePath());
        
        if (!picturesDir.exists() || !picturesDir.isDirectory()) {
            Log.w(TAG, "Pictures directory does not exist");
            updateUI();
            return;
        }
        
        // Get all image files (COW_*.jpg)
        File[] imageFiles = picturesDir.listFiles((dir, name) -> 
            name.startsWith("COW_") && name.endsWith(".jpg") && !name.contains("_metadata")
        );
        
        if (imageFiles == null || imageFiles.length == 0) {
            Log.d(TAG, "No result images found");
            updateUI();
            return;
        }
        
        Log.d(TAG, "Found " + imageFiles.length + " result images");
        
        // Process each image file
        for (File imageFile : imageFiles) {
            ResultItem item = new ResultItem();
            item.setImageFile(imageFile);
            item.setFileName(imageFile.getName());
            item.setTimestamp(imageFile.lastModified());
            
            // Find corresponding metadata file
            String metadataFileName = imageFile.getName().replace(".jpg", "_metadata.txt");
            File metadataFile = new File(picturesDir, metadataFileName);
            
            if (metadataFile.exists()) {
                item.setMetadataFile(metadataFile);
                parseMetadata(item, metadataFile);
            }
            
            resultList.add(item);
        }
        
        // Sort by timestamp descending (newest first)
        Collections.sort(resultList, (a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        
        updateUI();
    }
    
    /**
     * Parse metadata file and extract values
     */
    private void parseMetadata(ResultItem item, File metadataFile) {
        try (BufferedReader reader = new BufferedReader(new FileReader(metadataFile))) {
            String line;
            
            // Patterns to match metadata lines
            Pattern weightPattern = Pattern.compile("Predicted Weight:\\s*([\\d.]+)\\s*kg");
            Pattern distancePattern = Pattern.compile("Distance \\(LiDAR\\):\\s*[\\d.]+\\s*meters\\s*\\(([\\d.]+)\\s*cm\\)");
            Pattern widthPattern = Pattern.compile("^Width:\\s*([\\d.]+)\\s*px");
            Pattern heightPattern = Pattern.compile("^Height:\\s*([\\d.]+)\\s*px");
            
            while ((line = reader.readLine()) != null) {
                // Parse weight
                Matcher weightMatcher = weightPattern.matcher(line);
                if (weightMatcher.find()) {
                    item.setWeight(weightMatcher.group(1));
                }
                
                // Parse distance
                Matcher distanceMatcher = distancePattern.matcher(line);
                if (distanceMatcher.find()) {
                    item.setDistance(distanceMatcher.group(1));
                }
                
                // Parse width (only first occurrence, which is actual bbox)
                if (item.getBboxWidth().equals("-")) {
                    Matcher widthMatcher = widthPattern.matcher(line);
                    if (widthMatcher.find()) {
                        item.setBboxWidth(widthMatcher.group(1));
                    }
                }
                
                // Parse height (only first occurrence, which is actual bbox)
                if (item.getBboxHeight().equals("-")) {
                    Matcher heightMatcher = heightPattern.matcher(line);
                    if (heightMatcher.find()) {
                        item.setBboxHeight(heightMatcher.group(1));
                    }
                }
            }
            
            Log.d(TAG, "Parsed metadata for " + metadataFile.getName() + 
                  " - Weight: " + item.getWeight() + 
                  ", Distance: " + item.getDistance());
            
        } catch (Exception e) {
            Log.e(TAG, "Error parsing metadata: " + metadataFile.getName(), e);
        }
    }
    
    /**
     * Update UI based on data
     */
    private void updateUI() {
        adapter.notifyDataSetChanged();
        
        if (resultList.isEmpty()) {
            recyclerResults.setVisibility(View.GONE);
            layoutEmpty.setVisibility(View.VISIBLE);
            txtResultCount.setText("0 hasil");
        } else {
            recyclerResults.setVisibility(View.VISIBLE);
            layoutEmpty.setVisibility(View.GONE);
            txtResultCount.setText(resultList.size() + " hasil");
        }
    }
    
    /**
     * Show detail dialog when item is clicked
     */
    private void showDetailDialog(ResultItem item, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        
        // Inflate custom layout
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_result_detail, null);
        
        ImageView imgDetail = dialogView.findViewById(R.id.imgDetail);
        TextView txtDetailWeight = dialogView.findViewById(R.id.txtDetailWeight);
        TextView txtDetailDistance = dialogView.findViewById(R.id.txtDetailDistance);
        TextView txtDetailBbox = dialogView.findViewById(R.id.txtDetailBbox);
        TextView txtDetailFile = dialogView.findViewById(R.id.txtDetailFile);
        TextView txtDetailMetadata = dialogView.findViewById(R.id.txtDetailMetadata);
        
        // Load full image
        if (item.getImageFile() != null && item.getImageFile().exists()) {
            try {
                Bitmap bitmap = BitmapFactory.decodeFile(item.getImageFile().getAbsolutePath());
                imgDetail.setImageBitmap(bitmap);
            } catch (Exception e) {
                imgDetail.setImageResource(android.R.drawable.ic_menu_gallery);
            }
        }
        
        // Set text info
        txtDetailWeight.setText("Bobot: " + item.getWeight() + " kg");
        txtDetailDistance.setText("Jarak: " + item.getDistance() + " cm");
        txtDetailBbox.setText("BBox: " + item.getBboxSize());
        txtDetailFile.setText("File: " + item.getFileName());
        
        // Load full metadata
        if (item.getMetadataFile() != null && item.getMetadataFile().exists()) {
            try {
                StringBuilder sb = new StringBuilder();
                BufferedReader reader = new BufferedReader(new FileReader(item.getMetadataFile()));
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                reader.close();
                txtDetailMetadata.setText(sb.toString());
            } catch (Exception e) {
                txtDetailMetadata.setText("Error reading metadata");
            }
        } else {
            txtDetailMetadata.setText("Metadata not available");
        }
        
        builder.setView(dialogView);
        builder.setTitle("📊 Detail Hasil Deteksi");
        builder.setPositiveButton("Tutup", null);
        builder.setNegativeButton("Hapus", (dialog, which) -> confirmDelete(item, position));
        
        builder.create().show();
    }
    
    /**
     * Confirm and delete result
     */
    private void confirmDelete(ResultItem item, int position) {
        new AlertDialog.Builder(requireContext())
            .setTitle("🗑️ Hapus Hasil?")
            .setMessage("Apakah Anda yakin ingin menghapus hasil deteksi ini?\n\n" + item.getFileName())
            .setPositiveButton("Hapus", (dialog, which) -> {
                deleteResult(item, position);
            })
            .setNegativeButton("Batal", null)
            .show();
    }
    
    /**
     * Delete result files and update list
     */
    private void deleteResult(ResultItem item, int position) {
        boolean imageDeleted = false;
        boolean metadataDeleted = false;
        
        // Delete image file
        if (item.getImageFile() != null && item.getImageFile().exists()) {
            imageDeleted = item.getImageFile().delete();
            Log.d(TAG, "Image deleted: " + imageDeleted + " - " + item.getImageFile().getName());
        }
        
        // Delete metadata file
        if (item.getMetadataFile() != null && item.getMetadataFile().exists()) {
            metadataDeleted = item.getMetadataFile().delete();
            Log.d(TAG, "Metadata deleted: " + metadataDeleted + " - " + item.getMetadataFile().getName());
        }
        
        if (imageDeleted) {
            adapter.removeItem(position);
            updateUI();
            Toast.makeText(requireContext(), "✅ Hasil dihapus", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(requireContext(), "❌ Gagal menghapus file", Toast.LENGTH_SHORT).show();
        }
    }
}
