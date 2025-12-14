# ML Model Integration Guide

Panduan untuk **ML Engineer** mengintegrasikan model TensorFlow Lite ke aplikasi.

## 📋 Requirement Model

### Input
1. **Camera Frame** (Image)
   - Format: RGB atau BGR
   - Size: Tergantung model (contoh: 224x224, 300x300)
   - Preprocessing: Normalization, resize

2. **LiDAR Distance** (Float)
   - Unit: cm atau meter
   - Range: 20-800 cm (TF Luna spec)
   - Purpose: Meningkatkan akurasi estimasi bobot

### Output
1. **Estimated Weight** (Float)
   - Unit: kg
   - Bobot karkas sapi yang diprediksi

2. **Confidence Score** (Float)
   - Range: 0.0 - 1.0 (atau 0% - 100%)
   - Tingkat kepercayaan prediksi

## 🔧 Langkah Integrasi

### 1. Export Model ke TensorFlow Lite

Setelah training, convert model ke `.tflite`:

```python
import tensorflow as tf

# Load trained model
model = tf.keras.models.load_model('cattle_weight_model.h5')

# Convert to TFLite
converter = tf.lite.TFLiteConverter.from_keras_model(model)
converter.optimizations = [tf.lite.Optimize.DEFAULT]
tflite_model = converter.convert()

# Save TFLite model
with open('cattle_weight.tflite', 'wb') as f:
    f.write(tflite_model)
```

### 2. Taruh Model di Android Project

```
CattleWeightDetector/
└── app/
    └── src/
        └── main/
            └── assets/
                └── cattle_weight.tflite  # Taruh di sini
```

### 3. Buat Analyzer Class

Create file: `CattleWeightAnalyzer.java`

```java
package com.capstone.cattleweight;

import android.graphics.Bitmap;
import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import org.tensorflow.lite.Interpreter;
import java.nio.MappedByteBuffer;

public class CattleWeightAnalyzer implements ImageAnalysis.Analyzer {
    
    private final Interpreter tflite;
    private final AnalysisCallback callback;
    private float currentDistance = 0f;
    
    public interface AnalysisCallback {
        void onWeightEstimated(float weight, float confidence);
    }
    
    public CattleWeightAnalyzer(MappedByteBuffer model, AnalysisCallback callback) {
        this.tflite = new Interpreter(model);
        this.callback = callback;
    }
    
    public void updateDistance(float distance) {
        this.currentDistance = distance;
    }
    
    @Override
    public void analyze(@NonNull ImageProxy image) {
        // 1. Convert ImageProxy to Bitmap
        Bitmap bitmap = imageProxyToBitmap(image);
        
        // 2. Preprocess image
        float[][][][] input = preprocessImage(bitmap);
        
        // 3. Prepare output buffers
        float[][] output = new float[1][2]; // [weight, confidence]
        
        // 4. Run inference
        tflite.run(input, output);
        
        // 5. Post-process results
        float weight = output[0][0];
        float confidence = output[0][1];
        
        // 6. Callback with results
        callback.onWeightEstimated(weight, confidence);
        
        image.close();
    }
    
    private Bitmap imageProxyToBitmap(ImageProxy image) {
        // TODO: Implement conversion
        return null;
    }
    
    private float[][][][] preprocessImage(Bitmap bitmap) {
        // TODO: Implement preprocessing
        // - Resize to model input size
        // - Normalize pixel values
        // - Convert to float array
        return new float[1][224][224][3];
    }
}
```

### 4. Load Model di MainActivity

Edit `MainActivity.java`:

```java
import android.content.res.AssetFileDescriptor;
import java.io.FileInputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class MainActivity extends AppCompatActivity {
    
    private CattleWeightAnalyzer analyzer;
    
    private void setupMLModel() {
        try {
            MappedByteBuffer model = loadModelFile("cattle_weight.tflite");
            
            analyzer = new CattleWeightAnalyzer(model, 
                new CattleWeightAnalyzer.AnalysisCallback() {
                    @Override
                    public void onWeightEstimated(float weight, float confidence) {
                        runOnUiThread(() -> {
                            tvEstimatedWeight.setText(
                                String.format("Bobot Karkas: %.1f kg", weight)
                            );
                            tvConfidence.setText(
                                String.format("Confidence: %.1f%%", confidence * 100)
                            );
                        });
                    }
                });
            
            // Set analyzer ke ImageAnalysis
            imageAnalysis.setAnalyzer(cameraExecutor, analyzer);
            
        } catch (Exception e) {
            Log.e(TAG, "Error loading model: " + e.getMessage());
        }
    }
    
    private MappedByteBuffer loadModelFile(String filename) throws IOException {
        AssetFileDescriptor fileDescriptor = getAssets().openFd(filename);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }
    
    // Update distance ke analyzer saat data LiDAR diterima
    private void updateLidarUI(LidarData data) {
        // ... existing code ...
        
        // Update distance ke ML model
        if (analyzer != null) {
            analyzer.updateDistance(data.getJarak());
        }
    }
}
```

## 📊 Model Specification Template

Documentsikan model Anda:

```markdown
## Model Information

- **Model Name**: Cattle Weight Estimator v1.0
- **Model Type**: Regression / Classification
- **Framework**: TensorFlow / PyTorch
- **Input Shape**: 
  - Image: (224, 224, 3)
  - Distance: (1,)
- **Output Shape**: (2,) [weight, confidence]
- **Model Size**: XX MB
- **Inference Time**: ~XX ms (on phone)
- **Accuracy**: MAE = XX kg, R² = 0.XX
```

## 🧪 Testing Checklist

- [ ] Model ter-load tanpa error
- [ ] Inference berjalan < 200ms
- [ ] Output weight dalam range reasonable (50-500 kg)
- [ ] Confidence score 0.0-1.0
- [ ] UI ter-update dengan hasil prediksi
- [ ] Tidak ada memory leak
- [ ] Works dengan berbagai lighting conditions

## 📁 File yang Perlu Dibuat

```
ml_model/
├── model/
│   ├── cattle_weight.tflite       # TFLite model
│   └── model_info.md              # Model documentation
├── training/
│   ├── train.py                   # Training script
│   ├── dataset_info.md            # Dataset documentation
│   └── requirements.txt           # Python dependencies
└── README.md                      # Integration guide
```

## 🤝 Koordinasi dengan Mobile Developer

**Yang perlu dikomunikasikan:**
1. Input shape model (image size, channel order RGB/BGR)
2. Preprocessing steps (normalization range, mean/std)
3. Output format (single value / multiple values)
4. Expected inference time
5. Minimum confidence threshold

## 📞 Contact

Jika ada pertanyaan tentang integrasi, contact mobile developer team.

---

**Status**: 🔜 Waiting for ML Model  
**Priority**: High  
**Deadline**: TBD
