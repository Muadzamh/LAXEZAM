# Cattle Weight Detector - Android App

Aplikasi Android untuk deteksi bobot karkas sapi menggunakan kombinasi **Camera** dan **LiDAR TF Luna**.

## 📋 Fitur Utama

- ✅ **Live Camera Preview** (30% layar atas) - Preview kamera real-time
- ✅ **LiDAR Data Display** (70% layar bawah) - Menampilkan jarak, sinyal, dan suhu
- 🔮 **ML Model Integration Ready** - Siap untuk integrasi model TensorFlow Lite
- 📡 **Real-time Data Streaming** - Update data LiDAR setiap 100ms

## 🏗️ Arsitektur

```
[LiDAR TF Luna] --> [PC/Laptop COM7] --> [Flask Server :5000] --> [Android App via WiFi]
                                                                          |
                                                                    [Camera Preview]
```

## 🚀 Setup & Installation

### Prasyarat

1. **Android Studio** (Arctic Fox atau lebih baru)
2. **Java Development Kit (JDK)** 8 atau lebih baru
3. **HP Android** dengan:
   - Android 7.0 (API 24) atau lebih baru
   - USB Debugging enabled
   - WiFi aktif

### Step 1: Import Project ke Android Studio

1. Buka Android Studio
2. **File → Open** → Pilih folder `mobile/android/CattleWeightDetector`
3. Wait untuk Gradle sync selesai
4. Jika ada error Gradle, klik **File → Sync Project with Gradle Files**

### Step 2: Konfigurasi Server URL

Edit file `MainActivity.java` line 52:

```java
private static final String SERVER_URL = "http://192.168.1.100:5000";
```

**Ganti `192.168.1.100` dengan IP address komputer Anda!**

Cara cek IP komputer:
```powershell
ipconfig
```
Lihat **IPv4 Address** pada adapter WiFi yang aktif.

### Step 3: Connect HP via USB Debugging

1. Di HP, aktifkan **Developer Options**:
   - **Settings → About Phone → Tap "Build Number" 7x**
2. Aktifkan **USB Debugging**:
   - **Settings → Developer Options → USB Debugging → ON**
3. Colokkan HP ke laptop via USB
4. Konfirmasi "Allow USB Debugging" di HP

### Step 4: Build & Run

1. Di Android Studio, pastikan HP Anda terdeteksi (lihat dropdown device di toolbar)
2. Klik tombol **Run** (▶️) atau tekan `Shift + F10`
3. Aplikasi akan ter-install dan otomatis buka di HP Anda

## 🔧 Testing

### Test 1: Camera
- Setelah app terbuka, izinkan akses kamera
- Kotak hijau di atas harus menampilkan camera preview
- Status: "📷 Camera Active"

### Test 2: LiDAR Connection
- Pastikan Flask server sudah running di PC
- Pastikan HP dan PC dalam **satu jaringan WiFi**
- Kotak merah di bawah akan menampilkan:
  - 🟢 Connected to LiDAR Server
  - Data jarak, sinyal, suhu yang update real-time

### Troubleshooting

**❌ Camera tidak muncul**
- Cek izin kamera di Settings → Apps → Cattle Weight Detector → Permissions

**🔴 LiDAR Disconnected**
- Cek apakah Flask server running (`python lidar_server.py`)
- Cek IP address di `SERVER_URL` sudah benar
- Test akses dari browser HP: `http://<IP_PC>:5000`
- Pastikan firewall tidak memblokir port 5000

**⚠️ Build Error**
- Pastikan Java 8+ ter-install
- Sync Gradle: **File → Sync Project with Gradle Files**
- Rebuild: **Build → Rebuild Project**

## 📱 Struktur UI

```
┌─────────────────────────────────────┐
│  📷 CAMERA SECTION (30%)            │
│  ┌──────────────────────────────┐   │
│  │  Camera Preview              │   │
│  │  [ML Model Result Overlay]   │   │
│  │  Bobot Karkas: -- kg         │   │
│  └──────────────────────────────┘   │
├─────────────────────────────────────┤
│  📡 LIDAR SECTION (70%)             │
│  ┌──────────────────────────────┐   │
│  │  📏 JARAK: -- cm             │   │
│  │  📶 SINYAL: --               │   │
│  │  🌡️ SUHU: --°C              │   │
│  │  Status: 🟢 Connected        │   │
│  └──────────────────────────────┘   │
└─────────────────────────────────────┘
```

## 🔮 Integrasi Model ML (Future)

Untuk tim ML: Taruh model `.tflite` di folder `ml_model/`, kemudian:

1. Copy file `.tflite` ke `app/src/main/assets/`
2. Buat class `CattleWeightAnalyzer.java`:
```java
public class CattleWeightAnalyzer implements ImageAnalysis.Analyzer {
    @Override
    public void analyze(@NonNull ImageProxy image) {
        // Load model
        // Run inference
        // Update UI dengan hasil prediksi
    }
}
```
3. Uncomment line di `MainActivity.java`:
```java
imageAnalysis.setAnalyzer(cameraExecutor, new CattleWeightAnalyzer());
```

## 📝 Dependencies

- **CameraX**: Camera preview & image analysis
- **OkHttp**: HTTP client untuk koneksi ke Flask server
- **Gson**: JSON parsing
- **TensorFlow Lite**: ML model inference (ready for integration)

## 👥 Tim Development

- **Mobile App Developer**: Setup camera & LiDAR integration
- **ML Engineer**: Model training & integration
- **Hardware**: LiDAR sensor setup

## 📄 License

Capstone Project - 2025
