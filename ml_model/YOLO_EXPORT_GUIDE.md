# Panduan Export YOLOv8 ke TFLite/ONNX

## Masalah yang Terjadi

Error saat export YOLOv8 ke TFLite:
```
ERROR TensorFlow SavedModel: export failure: No module named 'onnx2tf'
```

**Penyebab**: 
- Library `onnx2tf` tidak kompatibel dengan Python 3.13
- Ultralytics YOLO memerlukan `onnx2tf` untuk konversi ONNX → TensorFlow → TFLite

## Solusi

### 1. Install Dependencies

```bash
# Install Ultralytics YOLOv8
pip install ultralytics

# Install ONNX (untuk export ONNX)
pip install onnx

# Optional: Install TensorFlow (untuk TFLite, tapi mungkin tetap gagal)
pip install tensorflow
```

### 2. Jalankan Script Export

```bash
python convert_yolo_to_tflite.py
```

Script akan:
1. ✅ Download YOLOv8n model (jika belum ada)
2. ✅ Export ke ONNX format (pasti berhasil)
3. ⚠️ Coba export ke TFLite (mungkin gagal karena onnx2tf)

### 3. Output yang Dihasilkan

#### File ONNX (Selalu Tersedia)
- **File**: `yolov8n.onnx`
- **Size**: ~12 MB
- **Format**: ONNX opset 22
- **Status**: ✅ Berhasil

#### File TFLite (Mungkin Gagal)
- **File**: `yolov8n_float32.tflite`
- **Status**: ⚠️ Tergantung kompatibilitas onnx2tf

## Menggunakan Model di Android

### Opsi 1: ONNX Runtime (RECOMMENDED)

#### Kelebihan:
- ✅ Lebih ringan
- ✅ Performa bagus
- ✅ Kompatibilitas tinggi
- ✅ Mendukung banyak model

#### Setup:

1. **Add dependency ke `build.gradle`**:
```gradle
dependencies {
    implementation 'com.microsoft.onnxruntime:onnxruntime-android:1.17.0'
}
```

2. **Copy model ke assets**:
```bash
cp yolov8n.onnx ../mobile/android/CattleWeightDetector/app/src/main/assets/
```

3. **Load dan jalankan model**:
```java
import ai.onnxruntime.*;

public class YoloDetector {
    private OrtEnvironment env;
    private OrtSession session;
    
    public void loadModel(Context context) throws Exception {
        env = OrtEnvironment.getEnvironment();
        
        // Load from assets
        byte[] modelBytes = loadModelFromAssets(context, "yolov8n.onnx");
        session = env.createSession(modelBytes);
    }
    
    public float[][] detect(Bitmap image) throws Exception {
        // Prepare input
        OnnxTensor inputTensor = createInputTensor(image);
        
        // Run inference
        OrtSession.Result results = session.run(
            Collections.singletonMap("images", inputTensor)
        );
        
        // Process output
        return processOutput(results);
    }
}
```

### Opsi 2: TensorFlow Lite (Jika Export Berhasil)

#### Setup:

1. **Add dependency ke `build.gradle`**:
```gradle
dependencies {
    implementation 'org.tensorflow:tensorflow-lite:2.14.0'
}
```

2. **Copy model ke assets**:
```bash
cp yolov8n_float32.tflite ../mobile/android/CattleWeightDetector/app/src/main/assets/
```

3. **Load dan jalankan model**:
```java
import org.tensorflow.lite.Interpreter;

public class YoloDetector {
    private Interpreter tflite;
    
    public void loadModel(Context context) throws Exception {
        MappedByteBuffer modelBuffer = loadModelFile(context, "yolov8n_float32.tflite");
        tflite = new Interpreter(modelBuffer);
    }
    
    public float[][] detect(Bitmap image) {
        // Prepare input
        float[][][][] input = preprocessImage(image);
        
        // Prepare output
        float[][][] output = new float[1][84][8400]; // YOLO output shape
        
        // Run inference
        tflite.run(input, output);
        
        return processOutput(output);
    }
}
```

## YOLOv8 Model Details

### Input
- **Shape**: [1, 3, 640, 640]
- **Type**: Float32
- **Format**: RGB
- **Range**: 0-1 (normalized)

### Output
- **Shape**: [1, 84, 8400]
- **Format**: [batch, attributes, detections]
- **Attributes**: 
  - [0:4] = bbox coordinates (x, y, w, h)
  - [4:] = class probabilities (80 COCO classes)

### COCO Classes for Cattle
- **Class 19**: Cow
- **Class 20**: Elephant
- **Class 21**: Bear
- **Class 22**: Zebra
- **Class 23**: Giraffe

## Testing Model

```bash
# Test YOLO detection
python convert_yolo_to_tflite.py --test

# Export with testing
python convert_yolo_to_tflite.py --with-test
```

## Troubleshooting

### Error: No module named 'ultralytics'
```bash
pip install ultralytics
```

### Error: No module named 'onnx2tf'
**Solusi**: Gunakan ONNX model, bukan TFLite. onnx2tf tidak kompatibel dengan Python 3.13.

### ONNX export berhasil, TFLite gagal
**Tidak masalah**: ONNX Runtime lebih ringan dan performant untuk mobile.

### Model terlalu besar
**Gunakan quantization**:
```python
model.export(format='onnx', imgsz=640, simplify=True, opset=12)
```

Atau gunakan model lebih kecil:
```python
model = YOLO('yolov8s.pt')  # Smaller model
```

## Perbandingan ONNX vs TFLite

| Aspek | ONNX Runtime | TFLite |
|-------|--------------|--------|
| Size | ~12 MB | ~6 MB |
| Speed | ⚡ Cepat | ⚡ Cepat |
| Compatibility | ✅ Tinggi | ⚠️ Medium |
| Setup | Mudah | Mudah |
| Ecosystem | Multi-platform | Android focus |

## Rekomendasi

1. **Gunakan ONNX Runtime** untuk deployment Android
2. Format ONNX lebih universal dan mudah di-maintain
3. Performa ONNX Runtime setara atau lebih baik dari TFLite
4. Hindari bergantung pada `onnx2tf` yang tidak kompatibel

## Resources

- [ONNX Runtime Android Tutorial](https://onnxruntime.ai/docs/tutorials/mobile/)
- [YOLOv8 Documentation](https://docs.ultralytics.com/)
- [ONNX Model Zoo](https://github.com/onnx/models)
