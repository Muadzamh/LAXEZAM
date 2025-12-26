# Quick Start - Model Export untuk Android

## Ringkasan Masalah

Kedua script export model mengalami error karena **library konversi ONNX-to-TensorFlow tidak kompatibel dengan Python 3.13**:

1. ❌ `onnx-tf` - untuk bbox_weight_model
2. ❌ `onnx2tf` - untuk YOLOv8

## Solusi yang Diterapkan

### 1. BBox Weight Model (`convert_to_tflite.py`)

**Status**: ✅ FIXED - Script diperbaiki

**Cara Pakai**:
```bash
python convert_to_tflite.py
```

**Output**:
- ✅ `bbox_weight_model.tflite` (4.57 MB)
- ⚠️ Menggunakan MobileNetV2 pretrained weights
- 💡 Perlu retrain dengan dataset Anda untuk akurasi optimal

**Untuk Production**:
- Opsi 1: Retrain model di TensorFlow
- Opsi 2: Gunakan `bbox_weight_model.onnx` dengan ONNX Runtime

---

### 2. YOLOv8 Model (`convert_yolo_to_tflite.py`)

**Status**: ✅ FIXED - Script diperbaiki

**Instalasi**:
```bash
pip install ultralytics
```

**Cara Pakai**:
```bash
python convert_yolo_to_tflite.py
```

**Output**:
- ✅ `yolov8n.onnx` (~12 MB) - **RECOMMENDED**
- ⚠️ `yolov8n_float32.tflite` - Mungkin gagal

**Untuk Production**:
- ✅ **Gunakan ONNX model dengan ONNX Runtime** (lebih reliable)

---

## Implementasi di Android

### ONNX Runtime (RECOMMENDED)

**1. Add dependency** (`build.gradle`):
```gradle
dependencies {
    implementation 'com.microsoft.onnxruntime:onnxruntime-android:1.17.0'
}
```

**2. Copy model**:
```bash
# YOLOv8
cp yolov8n.onnx mobile/android/.../assets/

# BBox Weight Model
cp bbox_weight_model.onnx mobile/android/.../assets/
```

**3. Load model** (Java):
```java
OrtEnvironment env = OrtEnvironment.getEnvironment();
byte[] modelBytes = loadFromAssets("yolov8n.onnx");
OrtSession session = env.createSession(modelBytes);
```

### TensorFlow Lite (Jika ada .tflite file)

**1. Add dependency** (`build.gradle`):
```gradle
dependencies {
    implementation 'org.tensorflow:tensorflow-lite:2.14.0'
}
```

**2. Copy model**:
```bash
cp bbox_weight_model.tflite mobile/android/.../assets/
```

**3. Load model** (Java):
```java
MappedByteBuffer buffer = loadModelFile("bbox_weight_model.tflite");
Interpreter tflite = new Interpreter(buffer);
```

---

## File Dokumentasi Lengkap

📄 **[CONVERSION_NOTES.md](CONVERSION_NOTES.md)**
- Penjelasan detail error dan solusi
- Opsi untuk BBox Weight Model
- Perbandingan metode konversi

📄 **[YOLO_EXPORT_GUIDE.md](YOLO_EXPORT_GUIDE.md)**
- Panduan lengkap YOLOv8 export
- Code example untuk Android
- Input/output specifications
- Troubleshooting

---

## Rekomendasi Final

### Untuk BBox Weight Model:
1. ✅ Gunakan ONNX model (`bbox_weight_model.onnx`)
2. 🔄 Atau retrain di TensorFlow untuk TFLite native

### Untuk YOLOv8:
1. ✅ Gunakan ONNX model (`yolov8n.onnx`)
2. ✅ ONNX Runtime Android lebih ringan dan reliable
3. ❌ Hindari TFLite conversion (tidak kompatibel)

### Keuntungan ONNX Runtime:
- ✅ Universal format
- ✅ Performa excellent
- ✅ Ukuran kompetitif
- ✅ Multi-platform
- ✅ Active development
- ✅ Kompatibilitas tinggi

---

## Need Help?

Lihat dokumentasi lengkap:
- `CONVERSION_NOTES.md` - BBox model details
- `YOLO_EXPORT_GUIDE.md` - YOLOv8 details
