package com.capstone.cattleweight;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CattleDatasetDatabase extends SQLiteOpenHelper {
    
    private static final String DATABASE_NAME = "cattle_dataset.db";
    private static final int DATABASE_VERSION = 1;
    
    // Table name
    private static final String TABLE_DATASET = "dataset";
    
    // Columns
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_IMAGE_PATH = "image_path";
    private static final String COLUMN_DISTANCE = "distance_cm";
    private static final String COLUMN_SIGNAL = "signal_strength";
    private static final String COLUMN_TEMPERATURE = "temperature";
    private static final String COLUMN_TIMESTAMP = "timestamp";
    
    public CattleDatasetDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }
    
    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_DATASET + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_IMAGE_PATH + " TEXT NOT NULL, " +
                COLUMN_DISTANCE + " INTEGER, " +
                COLUMN_SIGNAL + " INTEGER, " +
                COLUMN_TEMPERATURE + " REAL, " +
                COLUMN_TIMESTAMP + " TEXT" +
                ")";
        db.execSQL(createTable);
    }
    
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_DATASET);
        onCreate(db);
    }
    
    // Insert new dataset entry
    public long insertDataset(String imagePath, int distance, int signal, double temperature) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date());
        
        values.put(COLUMN_IMAGE_PATH, imagePath);
        values.put(COLUMN_DISTANCE, distance);
        values.put(COLUMN_SIGNAL, signal);
        values.put(COLUMN_TEMPERATURE, temperature);
        values.put(COLUMN_TIMESTAMP, timestamp);
        
        long id = db.insert(TABLE_DATASET, null, values);
        db.close();
        return id;
    }
    
    // Get total dataset count
    public int getDatasetCount() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_DATASET, null);
        int count = 0;
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }
        cursor.close();
        db.close();
        return count;
    }
    
    // Get all dataset (for export)
    public List<CattleDatasetEntry> getAllDataset() {
        List<CattleDatasetEntry> datasetList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        
        Cursor cursor = db.query(TABLE_DATASET, null, null, null, null, null, 
                COLUMN_TIMESTAMP + " DESC");
        
        if (cursor.moveToFirst()) {
            do {
                CattleDatasetEntry entry = new CattleDatasetEntry(
                    cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_IMAGE_PATH)),
                    cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_DISTANCE)),
                    cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_SIGNAL)),
                    cursor.getDouble(cursor.getColumnIndexOrThrow(COLUMN_TEMPERATURE)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
                );
                datasetList.add(entry);
            } while (cursor.moveToNext());
        }
        
        cursor.close();
        db.close();
        return datasetList;
    }
    
    // Dataset Entry class
    public static class CattleDatasetEntry {
        public int id;
        public String imagePath;
        public int distance;
        public int signal;
        public double temperature;
        public String timestamp;
        
        public CattleDatasetEntry(int id, String imagePath, int distance, int signal, 
                                 double temperature, String timestamp) {
            this.id = id;
            this.imagePath = imagePath;
            this.distance = distance;
            this.signal = signal;
            this.temperature = temperature;
            this.timestamp = timestamp;
        }
    }
}
