package com.capstone.cattleweight;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.image.ops.Rot90Op;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Weight Predictor Model
 * Menggunakan model yang ditraining di capstone_crop_box.ipynb
 * Input: Cropped image + size feature
 * Output: Weight prediction (kg)
 */
public class WeightPredictor {
    
    private static final String TAG = "WeightPredictor";
    private static final String MODEL_FILE = "bbox_weight_model.tflite";
    
    // Model input size (sesuai training: 224x224)
    private static final int INPUT_SIZE = 224;
    
    // Normalization values (ImageNet standards)
    private static final float[] MEAN = {0.485f, 0.456f, 0.406f};
    private static final float[] STD = {0.229f, 0.224f, 0.225f};
    
    private Interpreter interpreter;
    private Context context;
    
    public WeightPredictor(Context context) {
        this.context = context;
        try {
            interpreter = new Interpreter(loadModelFile());
            Log.i(TAG, "Weight prediction model loaded successfully");
        } catch (IOException e) {
            Log.e(TAG, "Error loading weight prediction model", e);
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
     * Predict weight dari cropped image dan distance
     * 
     * @param croppedBitmap Gambar sapi yang sudah di-crop (dari bbox)
     * @param bboxArea Area bounding box dalam pixel
     * @param distanceMeters Jarak dari LiDAR dalam meter (convert dari cm!)
     * @return Predicted weight dalam kg
     */
    public float predictWeight(Bitmap croppedBitmap, float bboxArea, float distanceMeters) {
        if (interpreter == null) {
            Log.e(TAG, "Interpreter is null");
            return -1f;
        }
        
        try {
            // 1. Preprocess image
            float[][][][] imageInput = preprocessImage(croppedBitmap);
            
            // 2. Calculate size feature: bbox_area * (distance^2)
            float sizeFeature = bboxArea * (distanceMeters * distanceMeters);
            float[][] sizeFeatureInput = {{sizeFeature}};
            
            // 3. Prepare output
            float[][] output = new float[1][1];
            
            // 4. Run inference
            Object[] inputs = {imageInput, sizeFeatureInput};
            interpreter.runForMultipleInputsOutputs(inputs, new Object[]{output});
            
            float predictedWeight = output[0][0];
            
            Log.d(TAG, String.format("Prediction - BBox Area: %.0f px, Distance: %.2f m, " +
                    "Size Feature: %.2f, Weight: %.2f kg", 
                    bboxArea, distanceMeters, sizeFeature, predictedWeight));
            
            return predictedWeight;
            
        } catch (Exception e) {
            Log.e(TAG, "Error during weight prediction", e);
            return -1f;
        }
    }
    
    /**
     * Preprocess image: Resize to 224x224 dan normalize
     */
    private float[][][][] preprocessImage(Bitmap bitmap) {
        // Resize to 224x224
        ImageProcessor imageProcessor = new ImageProcessor.Builder()
            .add(new ResizeOp(INPUT_SIZE, INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
            .build();
        
        TensorImage tensorImage = new TensorImage();
        tensorImage.load(bitmap);
        tensorImage = imageProcessor.process(tensorImage);
        
        // Get pixel values
        int[] pixels = new int[INPUT_SIZE * INPUT_SIZE];
        tensorImage.getBitmap().getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);
        
        // Normalize (ImageNet normalization)
        float[][][][] normalizedImage = new float[1][INPUT_SIZE][INPUT_SIZE][3];
        
        for (int y = 0; y < INPUT_SIZE; y++) {
            for (int x = 0; x < INPUT_SIZE; x++) {
                int pixel = pixels[y * INPUT_SIZE + x];
                
                // Extract RGB (0-255)
                float r = ((pixel >> 16) & 0xFF) / 255.0f;
                float g = ((pixel >> 8) & 0xFF) / 255.0f;
                float b = (pixel & 0xFF) / 255.0f;
                
                // Normalize using ImageNet mean and std
                normalizedImage[0][y][x][0] = (r - MEAN[0]) / STD[0];
                normalizedImage[0][y][x][1] = (g - MEAN[1]) / STD[1];
                normalizedImage[0][y][x][2] = (b - MEAN[2]) / STD[2];
            }
        }
        
        return normalizedImage;
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
}
