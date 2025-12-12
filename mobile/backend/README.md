# LiDAR Flask Backend Server

Backend server untuk streaming data TF Luna LiDAR ke aplikasi Android.

## Setup & Installation

### 1. Install Dependencies
```bash
pip install -r requirements.txt
```

### 2. Konfigurasi Serial Port
Edit file `lidar_server.py` jika perlu mengubah port:
```python
SERIAL_PORT = 'COM7'  # Sesuaikan dengan port LiDAR Anda
BAUD_RATE = 115200
```

### 3. Menjalankan Server
```bash
python lidar_server.py
```

Server akan berjalan di `http://0.0.0.0:5000`

## API Endpoints

### GET `/`
Informasi status server dan list endpoint

### GET `/api/lidar`
Mendapatkan data LiDAR terbaru (single request)

**Response:**
```json
{
  "jarak": 245,
  "kekuatan": 180,
  "suhu": 24.5,
  "timestamp": 1701789456789,
  "status": "connected"
}
```

### GET `/api/lidar/stream`
Server-Sent Events untuk streaming real-time (recommended untuk Android)

### GET `/api/status`
Cek status koneksi LiDAR

## Untuk Aplikasi Android

Gunakan IP address komputer Anda untuk akses dari HP:
```
http://<IP_KOMPUTER>:5000/api/lidar
```

Cek IP komputer:
- Windows: `ipconfig` (lihat IPv4 Address)
- Pastikan HP dan komputer dalam satu jaringan WiFi

## Troubleshooting

**Error: Tidak bisa membuka COM7**
- Pastikan port tidak digunakan aplikasi lain (tutup Serial Monitor Arduino IDE)
- Cek device manager untuk memastikan port yang benar

**Android tidak bisa connect**
- Pastikan firewall Windows tidak memblokir port 5000
- Pastikan HP dan PC dalam satu jaringan WiFi
- Test dulu di browser HP: `http://<IP_PC>:5000`
