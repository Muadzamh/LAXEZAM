# 🎯 Quick Start - Test Aplikasi Cattle Weight Detection

## ✅ Checklist Persiapan

- [x] Model `yolov8n_float32.tflite` atau `yolov8n.onnx` di `assets/`
- [x] Model `bbox_weight_model.tflite` di `assets/`
- [x] Code implementasi lengkap (YoloDetector, WeightPredictor, DetectionFragment)
- [x] Layout UI dengan camera preview dan tombol

---

## 🚀 Cara Test (5 Menit)

### Step 1: Build & Install

```bash
# Di Android Studio
1. File → Sync Project with Gradle Files
2. Build → Clean Project
3. Build → Rebuild Project
4. Run → Run 'app' (Shift + F10)
```

Atau via command line:
```bash
cd mobile/android/CattleWeightDetector
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

### Step 2: Test Detection (Tanpa LiDAR)

1. **Buka app** di device
2. **Buka tab "Detection"**
3. **Izinkan akses camera** (jika diminta)
4. **Arahkan ke gambar/video sapi**
   - Bisa dari Google Images di laptop
   - Atau print gambar sapi
   - Atau video sapi di YouTube

**Ekspektasi**:
- ✅ Camera preview aktif
- ✅ Status: "📷 Camera Active" (hijau)
- ✅ Bounding box hijau muncul di sekitar sapi
- ✅ Label "Sapi 85%" tampil
- ✅ Confidence score update real-time
- ❌ Button "PREDIKSI BERAT" disabled (karena belum ada LiDAR)

**Screenshot Checklist**:
```
+---------------------------+
|  [Camera Status: Active]  |
|  [🎯 PREDIKSI] (disabled) |
|                           |
|    +---------------+      |
|    | SAPI 85%      |      |
|    |   [Image]     |      |
|    |               |      |
|    +---------------+      |
|                           |
| Bobot: -- kg              |
| Confidence: 85%           |
+---------------------------+
```

---

### Step 3: Test dengan Mock Data (Development)

Jika ingin test weight prediction tanpa LiDAR hardware:

**Edit**: `DetectionFragment.java` (line ~278)

```java
private void performWeightPrediction() {
    if (latestDetection == null) {
        Toast.makeText(requireContext(), "Tidak ada sapi terdeteksi", Toast.LENGTH_SHORT).show();
        return;
    }
    
    // TAMBAHKAN INI UNTUK TESTING
    if (latestLidarData == null) {
        // Mock LiDAR data untuk testing
        latestLidarData = new LidarData();
        latestLidarData.setJarak(250);      // 250 cm = 2.5 meter
        latestLidarData.setKekuatan(80);    // Signal strength
        latestLidarData.setSuhu(25.0f);     // Temperature
        Log.i(TAG, "Using MOCK LiDAR data for testing");
    }
    
    // ... rest of code
```

**Rebuild & Run lagi**

Sekarang:
- ✅ Button "PREDIKSI BERAT" aktif (merah cerah)
- ✅ Click button → Processing
- ✅ Hasil: "Bobot: XXX.X kg"

---

### Step 4: Test dengan LiDAR Real

#### A. WiFi Mode (Flask Server)

1. **Start Flask server** di PC/Raspberry Pi:
```bash
cd lidar_logic
python lidar_server.py
```

2. **Cek IP server**:
```bash
# Windows
ipconfig

# Linux/Mac
ifconfig
```

3. **Update IP di app** (jika berbeda):
   - File: `DetectionFragment.java` line 46
   - Change: `http://192.168.1.100:5000` → IP Anda

4. **Pastikan device & server di network yang sama**

5. **Run app**
   - Status: "🟢 Connected to LiDAR Server"
   - Jarak: "XXX cm"

#### B. USB Mode (LiDAR via OTG)

1. **Connect LiDAR ke Android device** via USB OTG
2. **Toggle switch** "USB Mode" di app
3. **Izinkan akses USB** (jika diminta)
4. Status: "🟢 USB LiDAR Connected"

---

### Step 5: Full Test Prediction

1. **Pastikan**:
   - ✅ Camera showing cow
   - ✅ Bounding box visible
   - ✅ LiDAR connected (mock atau real)

2. **Click** "🎯 PREDIKSI BERAT"

3. **Observe**:
   - Text berubah: "Memproses..."
   - Button disabled (grey)
   - Wait 1-2 seconds
   - Hasil: "Bobot: 345.2 kg"
   - Toast: "Prediksi: 345.2 kg"
   - Button enabled kembali

---

## 📊 Expected Behavior

### Normal Flow:
```
1. App start
   → Camera permission request
   → Grant permission
   → Camera active ✅

2. Point to cow
   → YOLO detecting...
   → Bounding box appears ✅
   → Confidence: 75-95% ✅

3. LiDAR connected
   → Status: 🟢 Connected
   → Distance: 150-500 cm ✅

4. Click PREDIKSI BERAT
   → Processing... (1-2s)
   → Result: 200-600 kg ✅
   → Toast notification ✅
```

### Debug Mode (dengan Mock Data):
```
1. App start
   → Camera active ✅

2. Point to cow
   → Bounding box appears ✅

3. Click PREDIKSI BERAT
   → Mock LiDAR injected (250cm)
   → Processing...
   → Result appears ✅
```

---

## 🐛 Common Issues & Quick Fixes

### Issue 1: Camera tidak muncul

**Check**:
```java
// Logcat filter: "DetectionFragment"
// Expected log:
I/DetectionFragment: ML Models initialized
I/DetectionFragment: Camera initialized successfully
```

**Fix**:
- Settings → Apps → Your App → Permissions → Camera → Allow
- Restart app

### Issue 2: Bounding box tidak muncul

**Debug**:
```java
// Add to YoloDetector.java, method detect():
Log.d(TAG, "Detections found: " + detections.size());

// If size = 0:
// 1. Lower threshold to 0.3
// 2. Check if object is actually a cow
// 3. Improve lighting
```

### Issue 3: Button disabled

**Check**:
```java
// DetectionFragment.java, analyzeImage():
Log.d(TAG, "Detection: " + (latestDetection != null));
Log.d(TAG, "LiDAR: " + (latestLidarData != null));

// Both must be true for button to enable
```

### Issue 4: Crash saat inference

**Logcat**:
```
E/TfLiteInterpreter: Failed to invoke interpreter
E/WeightPredictor: Error during weight prediction
```

**Fix**:
1. Check model file exists in assets
2. Verify input shapes match model
3. Check memory available

---

## 📸 Screenshots for Verification

### 1. Camera Active (No Detection)
```
Status: 📷 Camera Active (green)
Preview: Showing camera feed
Overlay: No bounding box
Button: Disabled (grey)
```

### 2. Detection Running
```
Status: 📷 Camera Active
Preview: Cow visible
Overlay: Green bounding box
Label: "Sapi 85%"
Confidence: "Confidence: 85%"
Button: Enabled if LiDAR connected
```

### 3. LiDAR Connected
```
Connection: 🟢 Connected to LiDAR Server
Distance: 250 cm
Signal: 75
Temperature: 25.0°C
```

### 4. Prediction Result
```
Bobot: 345.2 kg (displayed in overlay)
Toast: "Prediksi: 345.2 kg"
Confidence: 85%
```

---

## 🎬 Demo Video Checklist

Record screen saat testing untuk dokumentasi:

- [ ] App launch & permissions
- [ ] Camera preview active
- [ ] Point to cow image/video
- [ ] Bounding box appears
- [ ] Confidence score updates
- [ ] LiDAR data streaming
- [ ] Click PREDIKSI BERAT button
- [ ] Processing indicator
- [ ] Result displayed
- [ ] Toast notification

---

## ✅ Success Criteria

App dianggap **BERHASIL** jika:

1. ✅ Camera preview tampil smooth (>20 FPS)
2. ✅ YOLO detection akurat (bounding box tepat)
3. ✅ Confidence score reasonable (>70% untuk cow yang jelas)
4. ✅ LiDAR data streaming real-time (<1s delay)
5. ✅ Weight prediction cepat (<2s processing)
6. ✅ Hasil prediksi masuk akal (200-600 kg untuk sapi dewasa)
7. ✅ UI responsive (tidak freeze)
8. ✅ Tidak ada crash

---

## 📝 Log Template untuk Reporting

Copy template ini saat testing:

```
=== TESTING REPORT ===
Date: [DATE]
Device: [DEVICE MODEL]
Android Version: [VERSION]

1. YOLO Detection:
   - Camera Active: [YES/NO]
   - Detection Working: [YES/NO]
   - Accuracy: [GOOD/FAIR/POOR]
   - FPS: [XX FPS]
   - Issue: [NONE / DESCRIBE]

2. LiDAR Connection:
   - Mode: [WiFi / USB]
   - Connected: [YES/NO]
   - Data Streaming: [YES/NO]
   - Latency: [XX ms]
   - Issue: [NONE / DESCRIBE]

3. Weight Prediction:
   - Button Active: [YES/NO]
   - Processing Time: [XX seconds]
   - Result Displayed: [YES/NO]
   - Result Value: [XXX kg]
   - Reasonable: [YES/NO]
   - Issue: [NONE / DESCRIBE]

4. Overall:
   - App Stability: [STABLE/UNSTABLE]
   - Performance: [GOOD/FAIR/POOR]
   - Ready for Production: [YES/NO]
   
Notes:
[Additional observations]
```

---

## 🎯 Next After Testing

Jika testing **SUKSES**:
1. ✅ Document results
2. ✅ Record demo video
3. ✅ Fine-tune parameters (threshold, etc)
4. ✅ Test dengan sapi real (lapangan)
5. ✅ Collect data untuk improve model
6. ✅ Build release APK

Jika ada **ISSUES**:
1. Check IMPLEMENTATION_STATUS.md → Troubleshooting
2. Review logs (Logcat)
3. Verify model files
4. Test step-by-step
5. Use mock data untuk isolate problem

---

**Good luck testing! 🚀**
