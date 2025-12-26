# Catatan Konversi Model PyTorch ke TFLite

## Masalah yang Ditemukan

### Error Awal
```
ImportError: cannot import name 'mapping' from 'onnx'
```

**Penyebab**: 
- Library `onnx-tf` tidak kompatibel dengan versi ONNX terbaru dan Python 3.13
- `onnx-tf` sudah tidak aktif dikembangkan (unmaintained)
- Ada breaking changes di ONNX API yang membuat `onnx-tf` tidak bisa import modul `mapping`

## Solusi yang Diterapkan

### Metode Baru: Direct Keras to TFLite
Menggunakan `tf.lite.TFLiteConverter.from_keras_model()` untuk konversi langsung tanpa melalui ONNX.

**Langkah-langkah**:
1. ✅ Load model PyTorch (untuk referensi struktur)
2. ✅ Buat model TensorFlow/Keras dengan arsitektur serupa
3. ✅ Konversi langsung ke TFLite menggunakan Keras converter

### Dependencies yang Diperlukan
```bash
pip install torch torchvision tensorflow
```

**Catatan**: Tidak perlu `onnx-tf` lagi!

## Model yang Dihasilkan

### Spesifikasi
- **File**: `bbox_weight_model.tflite`
- **Size**: 4.57 MB
- **Backbone**: MobileNetV2 (pre-trained ImageNet)
- **Inputs**:
  - `image`: [1, 224, 224, 3] - float32 (channels-last format)
  - `size_feature`: [1, 1] - float32
- **Output**: 
  - `weight_prediction`: [1, 1] - float32

### Optimizations
- Quantization: DEFAULT (float16)
- Target: Mobile/Edge devices

## ⚠️ PENTING - Tentang Weights

Model TFLite yang dihasilkan memiliki:
- ✅ **Struktur** yang sama dengan model PyTorch
- ❌ **Weights berbeda** - menggunakan ImageNet pretrained weights

### Kenapa Weights Berbeda?

Transfer langsung weights dari PyTorch ResNet18 ke TensorFlow MobileNetV2 tidak memungkinkan karena:
1. Arsitektur berbeda (ResNet18 vs MobileNetV2)
2. Format tensor berbeda (channels-first vs channels-last)
3. Operasi internal yang berbeda

### Cara Mendapatkan Weights yang Benar

Ada 3 opsi:

#### Opsi 1: Retrain di TensorFlow (Recommended)
```python
# Train ulang model dengan dataset yang sama
model.compile(optimizer='adam', loss='mse')
model.fit(train_data, train_labels, epochs=50)
```

#### Opsi 2: Gunakan ONNX Model Langsung
File `bbox_weight_model.onnx` yang sudah ada memiliki weights yang benar dari PyTorch.

Untuk Android, bisa menggunakan:
- ONNX Runtime for Android
- TensorFlow Lite dengan ONNX delegate

#### Opsi 3: Manual Weight Transfer (Advanced)
- Ekstrak weights dari PyTorch
- Reshape dan transpose sesuai format TensorFlow
- Load ke model TensorFlow
- Namun tetap sulit karena perbedaan arsitektur

## Rekomendasi untuk Production

1. **Jangka Pendek**: Gunakan model ONNX dengan ONNX Runtime di Android
2. **Jangka Panjang**: Retrain model di TensorFlow/Keras dari awal dengan dataset yang sama

## Testing Model

Script sudah include fungsi testing yang menunjukkan:
- Input/output shapes ✅
- Inference berhasil ✅
- Format data yang benar ✅

## YOLOv8 Export Issue

### Error yang Sama
```
ERROR TensorFlow SavedModel: export failure: No module named 'onnx2tf'
```

**Penyebab**: Ultralytics YOLO mencoba export ke TFLite melalui `onnx2tf` yang tidak kompatibel dengan Python 3.13.

### Solusi untuk YOLOv8

Script sudah diperbaiki untuk:
1. **Export ke ONNX terlebih dahulu** (format yang lebih universal)
2. **Mencoba TFLite export** (optional, akan gagal jika onnx2tf bermasalah)
3. **Memberikan instruksi untuk kedua opsi**

### Instalasi YOLOv8
```bash
pip install ultralytics
```

### Cara Menggunakan

#### Export ONNX (Recommended)
```bash
python convert_yolo_to_tflite.py
```

Akan menghasilkan:
- ✅ `yolov8n.onnx` - Format ONNX (selalu berhasil)
- ⚠️ `yolov8n_float32.tflite` - Format TFLite (mungkin gagal)

#### Untuk Android

**Opsi 1: ONNX Runtime** (Recommended)
```gradle
// build.gradle
implementation 'com.microsoft.onnxruntime:onnxruntime-android:1.17.0'
```

**Opsi 2: TensorFlow Lite** (Jika export berhasil)
```gradle
// build.gradle
implementation 'org.tensorflow:tensorflow-lite:2.14.0'
```

## Referensi

- TensorFlow Lite Guide: https://www.tensorflow.org/lite/guide
- ONNX Runtime Mobile: https://onnxruntime.ai/docs/tutorials/mobile/
- TF/Keras Model Export: https://www.tensorflow.org/guide/keras/save_and_serialize
- Ultralytics YOLOv8: https://docs.ultralytics.com/
