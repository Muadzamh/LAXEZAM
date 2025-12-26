# 🐄 Cattle Weight Detection - ML Model Integration

> Implementasi lengkap model machine learning untuk prediksi berat sapi ke aplikasi Android dengan YOLO detection real-time.

---

## 📖 Overview

Project ini mengintegrasikan model machine learning yang ditraining di `ml_model/capstone_crop_box.ipynb` ke aplikasi Android mobile. Fitur utama:

- **🎯 YOLO Real-time Detection**: Mendeteksi sapi dan menampilkan bounding box
- **⚖️ Weight Prediction**: Prediksi berat menggunakan model ResNet18 + regressor
- **📏 LiDAR Integration**: Menggunakan data jarak untuk kalkulasi akurat
- **📱 Android Native**: TensorFlow Lite untuk inference cepat

---

## 🏗️ Struktur Project

```
LAXEZAM/
├── ml_model/                          # Machine Learning
│   ├── capstone_crop_box.ipynb       # Training notebook (Colab)
│   ├── bbox_weight_model.pt          # Model yang sudah di-training
│   ├── convert_to_tflite.py          # Script konversi PyTorch → TFLite
│   ├── convert_yolo_to_tflite.py     # Script konversi YOLO → TFLite
│   ├── EXPORT_MODEL_GUIDE.md         # Panduan export dari notebook
│   └── README.md
│
├── mobile/android/                    # Android App
│   ├── CattleWeightDetector/
│   │   └── app/src/main/
│   │       ├── assets/               # ← Model TFLite disimpan disini
│   │       │   ├── bbox_weight_model.tflite
│   │       │   └── yolov8n_float32.tflite
│   │       ├── java/com/capstone/cattleweight/
│   │       │   ├── DetectionFragment.java      # Main detection logic
│   │       │   ├── YoloDetector.java           # YOLO inference
│   │       │   ├── WeightPredictor.java        # Weight prediction
│   │       │   └── DetectionOverlay.java       # BBox rendering
│   │       └── res/layout/
│   │           └── fragment_detection.xml      # UI dengan button
│   └── IMPLEMENTATION_GUIDE.md       # Panduan lengkap deployment
│
├── lidar_logic/                       # LiDAR integration
└── SETUP_ML_DEPLOYMENT.ps1           # Setup script otomatis
```

---

## 🚀 Quick Start

### Prerequisites

- Python 3.8+ dengan pip
- Android Studio
- Device Android (API 24+) atau Emulator
- Model `bbox_weight_model.pt` yang sudah di-training

### Step-by-Step Setup

#### 1️⃣ Clone & Navigate
```bash
cd "d:\Download\projek sapi\LAXEZAM"
```

#### 2️⃣ Run Setup Script (Otomatis)
```powershell
.\SETUP_ML_DEPLOYMENT.ps1
```

Script ini akan:
- ✅ Check & install Python dependencies
- ✅ Convert models ke TFLite
- ✅ Copy ke Android assets folder
- ✅ Verify setup

#### 3️⃣ Open Android Studio
```
File → Open → mobile/android/CattleWeightDetector
```

#### 4️⃣ Build & Run
```
Run → Run 'app' (Shift+F10)
```

---

## 📱 Cara Menggunakan Aplikasi

### 1. Buka Tab "Detection"
- Camera preview akan aktif
- YOLO mulai mendeteksi secara real-time

### 2. Arahkan ke Sapi
- Bounding box hijau akan muncul otomatis
- Confidence score ditampilkan

### 3. Pastikan LiDAR Terhubung
- Status koneksi: 🟢 Connected
- Jarak ditampilkan dalam cm

### 4. Tekan "🎯 PREDIKSI BERAT"
- Button aktif jika ada deteksi + data LiDAR
- Hasil muncul dalam 1-2 detik
- Bobot ditampilkan dalam kg

---

## 🧪 Pipeline ML

### Training Pipeline (Notebook)
```
Raw Image → YOLO Detection → Crop BBox → Feature Extraction
                ↓                            ↓
          BBox Dimensions              Size Feature
         (width, height, area)    (area × distance²)
                ↓                            ↓
                └──────────→ ResNet18 + Regressor
                                    ↓
                              Weight (kg)
```

### Android Implementation
```
CameraX → ImageAnalysis → YoloDetector
              ↓
    DetectionOverlay (real-time bbox)
              ↓
    User Press "PREDIKSI BERAT"
              ↓
    Crop + Extract Features + LiDAR Data
              ↓
        WeightPredictor
              ↓
    Display Result (kg)
```

---

## 📊 Model Details

### YOLO Detector
- **Model**: YOLOv8n (nano)
- **Input**: 640×640 RGB
- **Output**: Bounding boxes + confidence
- **Target Class**: Cow (COCO class 19)
- **Confidence**: 40% threshold

### Weight Predictor
- **Backbone**: ResNet18 (pretrained ImageNet)
- **Input 1**: Cropped image (224×224)
- **Input 2**: Size feature (bbox_area × distance²)
- **Architecture**: CNN features + MLP regressor
- **Output**: Weight in kg

---

## 📂 File-file Penting

### Java Classes (Baru)
| File | Fungsi |
|------|--------|
| `YoloDetector.java` | YOLO inference dengan TFLite |
| `WeightPredictor.java` | Weight prediction model |
| `DetectionOverlay.java` | Custom view untuk bbox |
| `DetectionFragment.java` | Integration logic |

### Python Scripts
| File | Fungsi |
|------|--------|
| `convert_to_tflite.py` | Convert bbox model |
| `convert_yolo_to_tflite.py` | Convert YOLO |
| `capstone_crop_box.ipynb` | Training notebook |

### Documentation
| File | Isi |
|------|-----|
| `IMPLEMENTATION_GUIDE.md` | Panduan lengkap deployment |
| `EXPORT_MODEL_GUIDE.md` | Export dari notebook |
| `README.md` | This file |

---

## ⚙️ Configuration

### Adjust Detection Threshold
**File**: `YoloDetector.java`
```java
private static final float CONFIDENCE_THRESHOLD = 0.4f; // 40%
```

### Change Bounding Box Color
**File**: `DetectionOverlay.java`
```java
boxPaint.setColor(Color.GREEN); // Change to RED, BLUE, etc.
```

### Modify Weight Prediction
**File**: `WeightPredictor.java`
```java
private static final int INPUT_SIZE = 224; // ResNet input size
```

---

## 🐛 Troubleshooting

### ❌ "Model file not found"
**Solusi:**
1. Check: `app/src/main/assets/bbox_weight_model.tflite`
2. Run: `.\SETUP_ML_DEPLOYMENT.ps1`
3. Clean & Rebuild project di Android Studio

### ❌ YOLO tidak mendeteksi
**Solusi:**
1. Lower confidence threshold (0.4 → 0.3)
2. Check lighting dan jarak kamera
3. Verify class ID (cow = 19)

### ❌ Button "Prediksi Berat" disabled
**Kondisi enable:**
- ✅ Ada deteksi sapi (bbox hijau)
- ✅ LiDAR connected (🟢)

**Check:**
- LiDAR server running
- Distance > 0 cm

### ❌ Prediksi tidak akurat
**Possible causes:**
1. Model perlu lebih banyak training data
2. Distance LiDAR tidak akurat
3. BBox crop tidak tepat

**Solusi:**
- Retrain dengan lebih banyak data
- Calibrate LiDAR
- Fine-tune YOLO untuk sapi

---

## 📈 Performance

### Model Size
- YOLO: ~6 MB (float32)
- Weight Predictor: ~45 MB (ResNet18)
- **Total**: ~51 MB

### Inference Speed (Mid-range device)
- YOLO Detection: 100-200ms
- Weight Prediction: 50-150ms
- **Total**: < 400ms per prediction

### Accuracy
- Depends on training data quality
- Check MAE from notebook validation

---

## 🔄 Update Model

### After Retraining

1. **Export new model**
   ```bash
   # In Colab or local
   cd ml_model
   python convert_to_tflite.py
   ```

2. **Copy to assets**
   ```bash
   cp bbox_weight_model.tflite ../mobile/android/.../assets/
   ```

3. **Rebuild app**
   - Clean Project
   - Rebuild Project
   - Run

---

## 📝 Development Notes

### Model Input Requirements
**Critical**: Preprocessing harus sama dengan training!

**Weight Predictor:**
- Image: 224×224, RGB
- Normalization: ImageNet (mean/std)
- Size Feature: `bbox_area_px × (distance_m)²`

**Distance Unit:**
- LiDAR output: cm
- Model input: meters
- Conversion: `/100`

---

## 🎯 Next Steps

- [ ] Train dengan lebih banyak data
- [ ] Fine-tune YOLO dengan dataset sapi custom
- [ ] Optimize model (quantization)
- [ ] Add prediction history/logging
- [ ] Export predictions ke CSV/Firebase
- [ ] Add multi-cow detection
- [ ] Improve UI/UX

---

## 📚 References

- [Ultralytics YOLOv8](https://docs.ultralytics.com/)
- [TensorFlow Lite Android](https://www.tensorflow.org/lite/android)
- [CameraX Android](https://developer.android.com/training/camerax)
- [PyTorch to TFLite](https://www.tensorflow.org/lite/convert)

---

## 👥 Team

Project: Cattle Weight Detection System
Integration: YOLO + Custom Weight Model
Platform: Android (TensorFlow Lite)

---

## 📄 License

[Add your license here]

---

## ✅ Status

- ✅ ML Model trained
- ✅ Conversion scripts ready
- ✅ Android implementation complete
- ✅ Documentation done
- ⏳ Testing & validation
- ⏳ Production deployment

---

**Last Updated**: December 2025

*Ready for deployment! 🚀*
