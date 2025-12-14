# Cattle Weight Detection System

Sistem deteksi bobot karkas sapi menggunakan kombinasi **Computer Vision (Camera)** dan **LiDAR** untuk akurasi tinggi.

## 📁 Struktur Project

```
Capstone/
├── lidar_logic/
│   └── baca_lidar.py              # Script pembaca LiDAR TF Luna (COM7)
│
├── mobile/
│   ├── backend/
│   │   ├── lidar_server.py        # Flask server untuk streaming data LiDAR
│   │   ├── requirements.txt       # Python dependencies
│   │   └── README.md              # Dokumentasi backend
│   │
│   ├── android/
│   │   ├── CattleWeightDetector/  # Android application
│   │   └── README.md              # Dokumentasi Android
│   │
│   └── README.md                  # Dokumentasi mobile (ini)
│
└── ml_model/                      # (Future) Untuk model ML
    ├── model/
    │   └── cattle_weight.tflite   # TensorFlow Lite model
    └── training/
        └── train.py               # Training script
```

## 🚀 Quick Start Guide

### 1️⃣ Setup Backend (Flask Server)

```powershell
# Masuk ke folder backend
cd mobile\backend

# Install dependencies
pip install -r requirements.txt

# Jalankan server
python lidar_server.py
```

Server akan berjalan di `http://0.0.0.0:5000`

📝 **Catatan**: Pastikan LiDAR TF Luna terkoneksi di **COM7**

### 2️⃣ Setup Android App

1. **Buka Android Studio**
   ```
   File → Open → mobile/android/CattleWeightDetector
   ```

2. **Edit Server URL**
   
   File: `MainActivity.java` (line 52)
   ```java
   private static final String SERVER_URL = "http://192.168.1.100:5000";
   ```
   Ganti `192.168.1.100` dengan **IP komputer Anda**

3. **Connect HP via USB Debugging**
   - Enable Developer Options di HP
   - Enable USB Debugging
   - Colokkan USB ke laptop

4. **Build & Run**
   - Klik tombol Run (▶️) di Android Studio
   - App akan ter-install di HP

### 3️⃣ Testing

1. **Start Flask Server** di PC/Laptop
2. **Connect HP dan PC** ke WiFi yang sama
3. **Buka aplikasi** di HP
4. **Cek:**
   - ✅ Camera preview muncul (kotak hijau)
   - ✅ Data LiDAR update real-time (kotak merah)
   - ✅ Status: "🟢 Connected to LiDAR Server"

## 🎯 Workflow Development

### Untuk Mobile Developer (Anda)
✅ **Sudah Selesai:**
- Setup Flask backend untuk streaming data LiDAR
- Android app dengan camera preview
- Integrasi LiDAR data receiver
- UI layout sesuai requirement (30% camera, 70% LiDAR)

🔜 **Next Steps:**
- Testing koneksi end-to-end
- Fine-tuning UI/UX
- Integration dengan ML model dari tim

### Untuk ML Engineer (Teman Anda)
📋 **Yang Perlu Dikerjakan:**
1. Training model untuk estimasi bobot karkas
2. Export model ke **TensorFlow Lite** format (`.tflite`)
3. Taruh model di folder `ml_model/model/`
4. Dokumentasikan input/output model

🔗 **Integration Point:**
- Input: Camera frame + Jarak LiDAR
- Output: Estimasi bobot karkas (kg) + confidence score
- File: Buat `CattleWeightAnalyzer.java` di folder Java

## 📊 API Endpoints (Flask Backend)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/` | GET | Status server |
| `/api/lidar` | GET | Get latest LiDAR data (JSON) |
| `/api/lidar/stream` | GET | Server-Sent Events stream |
| `/api/status` | GET | Check LiDAR connection status |

**Response Example (`/api/lidar`):**
```json
{
  "jarak": 245,
  "kekuatan": 180,
  "suhu": 24.5,
  "timestamp": 1701789456789,
  "status": "connected"
}
```

## 🛠️ Tech Stack

### Backend
- **Python 3.x**
- **Flask** - Web framework
- **PySerial** - Serial communication dengan LiDAR
- **Flask-CORS** - Cross-origin resource sharing

### Mobile (Android)
- **Java** - Programming language
- **CameraX** - Modern camera API
- **OkHttp** - HTTP client
- **Gson** - JSON parsing
- **TensorFlow Lite** - ML inference (ready for integration)

### Hardware
- **TF Luna LiDAR** - Distance sensor (COM7)
- **Android Phone** - Camera & display
- **PC/Laptop** - Backend server

## 🔧 Troubleshooting

### Backend Issues

**❌ COM7 tidak bisa dibuka**
```
Error: Tidak bisa membuka COM7
```
**Solusi:**
- Tutup Serial Monitor Arduino IDE
- Cek Device Manager → Port (COM & LPT)
- Pastikan driver USB-Serial ter-install

**❌ Port 5000 sudah digunakan**
```
Address already in use
```
**Solusi:**
- Matikan aplikasi lain yang pakai port 5000
- Atau ganti port di `lidar_server.py` dan `MainActivity.java`

### Android Issues

**🔴 LiDAR Disconnected**
- Pastikan Flask server running
- Cek IP address di `SERVER_URL`
- Test dari browser HP: `http://<IP_PC>:5000`
- Pastikan firewall tidak block port 5000

**❌ Camera Error**
- Cek permission kamera di Settings → Apps
- Restart aplikasi

## 📞 Network Configuration

**Cara Cek IP Komputer:**
```powershell
ipconfig
```
Lihat **IPv4 Address** di adapter WiFi/Ethernet.

**Test Koneksi dari HP:**
- Buka browser di HP
- Akses: `http://<IP_KOMPUTER>:5000`
- Harus muncul JSON response dari server

## 🎓 Learning Resources

- [CameraX Documentation](https://developer.android.com/training/camerax)
- [TensorFlow Lite Android](https://www.tensorflow.org/lite/guide/android)
- [Flask Documentation](https://flask.palletsprojects.com/)
- [TF Luna LiDAR Datasheet](https://www.benewake.com/en/tfmini.html)

## 📝 Notes

- **Frame Layout**: 30% camera (hijau) + 70% LiDAR (merah)
- **Update Rate**: LiDAR data polling setiap 100ms
- **ML Model**: Placeholder sudah ready untuk integrasi
- **Scalability**: Arsitektur mendukung penambahan sensor lain

## 👥 Team

- **Mobile Developer**: Camera & LiDAR integration ✅
- **ML Engineer**: Model training & deployment 🔜
- **Hardware**: Sensor setup & calibration ✅

---

**Last Updated**: December 2025  
**Version**: 1.0.0  
**Status**: Development (Mobile App ✅ | ML Model 🔜)
