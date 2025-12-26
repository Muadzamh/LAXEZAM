# 🚀 Implementasi ML Model - Cattle Weight Detection

## ✅ Status Implementasi

Implementasi **SUDAH SELESAI** dan **SIAP DIGUNAKAN**!

### Yang Sudah Ada:

#### 1. Model Files (di `app/src/main/assets/`)
- ✅ `yolov8n_float32.tflite` atau `yolov8n.onnx` - YOLO detector
- ✅ `bbox_weight_model.tflite` - Weight predictor

#### 2. Java Classes
- ✅ **YoloDetector.java** - Real-time YOLO detection
- ✅ **WeightPredictor.java** - Prediksi berat sapi
- ✅ **DetectionOverlay.java** - Tampilkan bounding box
- ✅ **DetectionFragment.java** - Main integration logic

#### 3. Layout
- ✅ **fragment_detection.xml** - UI dengan camera preview dan tombol

---

## 📋 Cara Kerja Aplikasi

### Alur Lengkap:

```
1. Buka Tab "Detection"
   ↓
2. Camera Preview Aktif
   ↓
3. YOLO Detection Running (Real-time)
   - Frame from camera → YoloDetector
   - Deteksi sapi → Bounding box hijau muncul
   - Confidence score ditampilkan
   ↓
4. LiDAR Data Streaming
   - WiFi mode: Dari Flask server
   - USB mode: Langsung dari device
   - Jarak (cm) ditampilkan
   ↓
5. Button "🎯 PREDIKSI BERAT" aktif jika:
   - ✅ Ada deteksi sapi (bbox hijau)
   - ✅ LiDAR data tersedia
   ↓
6. User Click Button
   ↓
7. System Process:
   - Crop image dari bbox
   - Calculate bbox area
   - Convert distance (cm → meter)
   - Run WeightPredictor model
   ↓
8. Hasil Prediksi
   - Bobot ditampilkan (kg)
   - Toast notification muncul
```

---

## 🎯 Fitur-Fitur Utama

### 1. Real-time YOLO Detection
**Lokasi**: `YoloDetector.java`

```java
// Auto-detect setiap frame dari camera
List<DetectionResult> detections = yoloDetector.detect(bitmap);

// Filter: Hanya class "Cow" (ID 19)
// Confidence threshold: 40%
```

**Hasil**:
- ✅ Bounding box hijau muncul otomatis
- ✅ Label "Sapi 85%" ditampilkan
- ✅ Multiple detections dengan NMS (Non-Maximum Suppression)

### 2. Weight Prediction
**Lokasi**: `WeightPredictor.java`

```java
// Input model:
// 1. Cropped image (224x224, normalized)
// 2. Size feature = bbox_area × distance²

float weight = weightPredictor.predictWeight(
    croppedBitmap,     // Gambar sapi yang di-crop
    bboxArea,          // Luas bbox dalam pixel
    distanceMeters     // Jarak LiDAR dalam meter
);
```

**Preprocessing**:
- ✅ Resize ke 224×224
- ✅ ImageNet normalization (mean/std)
- ✅ Size feature calculation: `area × (distance_m)²`

### 3. Detection Overlay
**Lokasi**: `DetectionOverlay.java`

Custom View yang menggambar:
- ✅ Bounding box (hijau, 8px stroke)
- ✅ Label dengan background (hitam transparan)
- ✅ Confidence percentage

### 4. LiDAR Integration
**Dual Mode**:
- 🌐 WiFi: Dari Flask server (192.168.1.100:5000)
- 🔌 USB: Langsung dari LiDAR device

**Data**:
- Jarak (cm)
- Kekuatan sinyal
- Suhu (°C)
- Timestamp

---

## 🔧 Konfigurasi

### Ubah Threshold Detection

**File**: `YoloDetector.java` (line ~32)

```java
private static final float CONFIDENCE_THRESHOLD = 0.4f; // 40%

// Ubah menjadi:
private static final float CONFIDENCE_THRESHOLD = 0.3f; // 30% (lebih sensitif)
```

### Ubah Warna Bounding Box

**File**: `DetectionOverlay.java` (line ~41)

```java
boxPaint.setColor(Color.GREEN);

// Ubah menjadi:
boxPaint.setColor(Color.RED);    // Merah
boxPaint.setColor(Color.BLUE);   // Biru
boxPaint.setColor(Color.YELLOW); // Kuning
```

### Ubah Server LiDAR

**File**: `DetectionFragment.java` (line ~46)

```java
private static final String SERVER_URL = "http://192.168.1.100:5000";

// Ubah sesuai IP server Anda
```

---

## 📱 Testing Guide

### 1. Persiapan
```bash
# Di Android Studio:
1. Build → Clean Project
2. Build → Rebuild Project
3. Run → Run 'app'
```

### 2. Test Detection
1. **Buka tab "Detection"**
   - Camera preview harus aktif
   - Status: "📷 Camera Active" (hijau)

2. **Arahkan ke gambar/video sapi**
   - Bounding box hijau harus muncul
   - Label "Sapi 85%" tampil
   - Confidence percentage update real-time

3. **Test tanpa LiDAR** (untuk development)
   - Detection tetap jalan
   - Button "PREDIKSI BERAT" disabled
   - Status: "🔴 Disconnected"

### 3. Test Weight Prediction
1. **Pastikan LiDAR terhubung**
   - Status: "🟢 Connected to LiDAR Server"
   - Jarak: "XXX cm"

2. **Arahkan ke sapi**
   - Bounding box muncul
   - Button "PREDIKSI BERAT" aktif (merah cerah)

3. **Click button**
   - Text berubah "Memproses..."
   - Tunggu 1-2 detik
   - Hasil: "Bobot: 350.5 kg"
   - Toast notification muncul

### 4. Test dengan Mock Data
Jika tidak ada LiDAR, tambahkan mock data untuk testing:

**File**: `DetectionFragment.java` (method `performWeightPrediction`)

```java
// Tambahkan sebelum checking latestLidarData:
if (latestLidarData == null) {
    // MOCK DATA UNTUK TESTING
    latestLidarData = new LidarData();
    latestLidarData.setJarak(250); // 250 cm = 2.5 meter
}
```

---

## 🐛 Troubleshooting

### Problem 1: "Model file not found"

**Error**: `FileNotFoundException: yolov8n_float32.tflite`

**Solusi**:
```bash
# 1. Cek file ada di assets
ls mobile/android/CattleWeightDetector/app/src/main/assets/

# 2. Pastikan nama file sama dengan di code
# YoloDetector.java line 28:
private static final String MODEL_FILE = "yolov8n_float32.tflite";

# 3. Clean & Rebuild
Build → Clean Project
Build → Rebuild Project
```

### Problem 2: Bounding box tidak muncul

**Kemungkinan**:
1. ❌ Confidence terlalu tinggi
   - **Fix**: Turunkan threshold ke 0.3
   
2. ❌ Bukan class "Cow"
   - **Fix**: Log class ID yang terdeteksi
   
3. ❌ Preprocessing salah
   - **Fix**: Cek input size (640×640)

**Debug Code**:

```java
// Di YoloDetector.java, method postprocess():
Log.d(TAG, String.format("Detection: class=%d, conf=%.2f, box=[%.0f,%.0f,%.0f,%.0f]",
    maxClassId, confidence, left, top, right, bottom));
```

### Problem 3: Button "PREDIKSI BERAT" disabled

**Kondisi enable**:
```java
btnPredictWeight.setEnabled(
    latestDetection != null &&  // Ada deteksi
    latestLidarData != null     // Ada data LiDAR
);
```

**Cek**:
1. ✅ Bounding box hijau muncul? → latestDetection OK
2. ✅ Status LiDAR "🟢 Connected"? → latestLidarData OK
3. ✅ Jarak > 0 cm? → Data valid

### Problem 4: Prediksi berat tidak akurat

**Kemungkinan**:
1. ❌ Model belum di-retrain dengan data yang cukup
2. ❌ Distance tidak akurat
3. ❌ Preprocessing berbeda dengan training

**Debug**:

```java
// Di WeightPredictor.java, method predictWeight():
Log.d(TAG, String.format(
    "Input - BBox Area: %.0f px, Distance: %.2f m, Size Feature: %.2f",
    bboxArea, distanceMeters, sizeFeature
));
```

**Ekspektasi**:
- Distance: 1-5 meter (100-500 cm)
- BBox Area: 50,000 - 200,000 px (tergantung jarak)
- Weight: 200-600 kg (sapi dewasa)

### Problem 5: App crash saat inference

**Error**: `OutOfMemoryError` atau `IllegalArgumentException`

**Solusi**:

1. **Reduce input size**:
```java
// Di YoloDetector.java:
private static final int INPUT_SIZE = 416; // Dari 640 → 416
```

2. **Check tensor shapes**:
```java
// Log input/output shapes
Tensor inputTensor = interpreter.getInputTensor(0);
Log.d(TAG, "Input shape: " + Arrays.toString(inputTensor.shape()));
```

3. **Enable logs untuk debugging**:
```java
// Di WeightPredictor.java constructor:
Interpreter.Options options = new Interpreter.Options();
options.setNumThreads(2);
options.setUseNNAPI(true); // Use Android Neural Network API
interpreter = new Interpreter(loadModelFile(), options);
```

---

## 📊 Performance Tips

### Optimasi Inference Speed

1. **Reduce image size** sebelum YOLO:
```java
// Downscale sebelum process
Bitmap scaledBitmap = Bitmap.createScaledBitmap(
    bitmap, 
    bitmap.getWidth() / 2, 
    bitmap.getHeight() / 2, 
    true
);
```

2. **Skip frames** untuk YOLO:
```java
private int frameCounter = 0;

private void analyzeImage(ImageProxy imageProxy) {
    frameCounter++;
    if (frameCounter % 3 != 0) { // Process every 3rd frame
        imageProxy.close();
        return;
    }
    // ... process
}
```

3. **Use GPU delegate** (optional):
```gradle
// build.gradle
implementation 'org.tensorflow:tensorflow-lite-gpu:2.14.0'
```

```java
// YoloDetector.java
GpuDelegate delegate = new GpuDelegate();
Interpreter.Options options = new Interpreter.Options();
options.addDelegate(delegate);
interpreter = new Interpreter(loadModelFile(), options);
```

---

## 📝 Model Input/Output Specs

### YOLO Model

**Input**:
- Shape: `[1, 640, 640, 3]` atau `[1, 3, 640, 640]`
- Type: float32
- Format: RGB, normalized (0-1)

**Output**:
- Shape: `[1, 25200, 85]` (YOLOv8n)
- Format: `[cx, cy, w, h, objectness, class_scores...]`
- Classes: 80 (COCO dataset)
- Cow class ID: 19

### Weight Predictor Model

**Input 1 (Image)**:
- Shape: `[1, 224, 224, 3]`
- Type: float32
- Normalization: ImageNet (mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225])

**Input 2 (Size Feature)**:
- Shape: `[1, 1]`
- Type: float32
- Formula: `bbox_area_px × (distance_m)²`

**Output**:
- Shape: `[1, 1]`
- Type: float32
- Unit: kilogram (kg)

---

## 🎓 Next Steps

### Immediate:
- [x] Implementasi YOLO detection ✅
- [x] Implementasi weight prediction ✅
- [x] LiDAR integration ✅
- [ ] **Testing dengan sapi real** 🔄
- [ ] **Fine-tune threshold** 🔄

### Future Enhancements:
- [ ] Multi-cow detection (track multiple cows)
- [ ] Save prediction history to database
- [ ] Export predictions to CSV/Excel
- [ ] Add photo capture saat prediction
- [ ] Cloud sync (Firebase)
- [ ] Offline mode dengan local storage
- [ ] Performance analytics

---

## 📞 Support

Jika ada error atau pertanyaan:

1. **Check logs**: Android Studio → Logcat → Filter "YoloDetector" atau "WeightPredictor"
2. **Verify models**: Pastikan file .tflite ada di assets
3. **Test step-by-step**: Detection → LiDAR → Prediction
4. **Use mock data**: Untuk testing tanpa hardware

---

## ✨ Summary

Implementasi **SUDAH LENGKAP** dengan fitur:

✅ Real-time YOLO detection dengan bounding box
✅ Weight prediction dengan model ML
✅ LiDAR integration (WiFi + USB)
✅ UI responsif dengan button control
✅ Error handling lengkap
✅ Logging untuk debugging

**SIAP DIGUNAKAN!** 🎉

Tinggal:
1. Build APK
2. Install di device
3. Test dengan sapi real
4. Fine-tune parameter jika diperlukan
