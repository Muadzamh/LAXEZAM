# 🌐 ADB over WiFi - Setup Guide

## 🎯 Tujuan
- HP colok ke **USB Hub** untuk GroundChat
- Laptop tetap bisa **build & install APK** via **WiFi**
- Tidak perlu kabel USB antara laptop dan HP

---

## 📋 Prerequisites

1. ✅ HP dan Laptop harus di **WiFi yang sama**
2. ✅ USB debugging sudah enabled di HP
3. ✅ Kabel USB (untuk setup awal saja, sekali doang)

---

## 🚀 Langkah Setup (Sekali Saja)

### 1️⃣ Setup ADB over WiFi

```powershell
# Jalankan script setup
.\SETUP_WIFI_ADB.ps1
```

**Script akan:**
1. Detect HP via USB
2. Enable TCP/IP mode pada HP
3. Minta Anda input IP address HP
4. Instruksi untuk **cabut USB**
5. Connect via WiFi
6. Save config untuk nanti

**Cara dapat IP HP:**
- **Settings** → **About Phone** → **Status** → IP Address
- Atau **Settings** → **Wi-Fi** → (Tap your network) → **IP Address**
- Contoh: `192.168.1.123`

### 2️⃣ Setelah Setup Berhasil

✅ **HP sudah disconnect dari USB**  
✅ **Colok GroundChat ke USB Hub**  
✅ **Colok USB Hub ke HP**  

Sekarang HP punya koneksi:
- 📶 **WiFi** → untuk ADB (install APK)
- 🔌 **USB** → untuk GroundChat/Camera/LiDAR

---

## 🔨 Build & Install APK via WiFi

Setiap kali mau build & install:

```powershell
.\BUILD_AND_INSTALL_WIFI.ps1
```

**Script akan:**
1. ✅ Connect ke HP via WiFi
2. ✅ Build APK
3. ✅ Install ke HP (via WiFi)
4. ✅ Launch app
5. ✅ Show logs USB device check

---

## 🔧 Manual Commands (Jika diperlukan)

### Connect ke HP via WiFi
```powershell
adb connect 192.168.1.123:5555
# (ganti dengan IP HP Anda)
```

### Check koneksi
```powershell
adb devices
```

Output:
```
List of devices attached
192.168.1.123:5555      device
```

### Install APK manual
```powershell
cd "D:\My Project\Capstone\mobile\android\CattleWeightDetector"
.\gradlew.bat assembleDebug
adb -s 192.168.1.123:5555 install -r app\build\outputs\apk\debug\app-debug.apk
```

### Launch app
```powershell
adb -s 192.168.1.123:5555 shell am start -n com.capstone.cattleweight/.MainActivityNew
```

### View logs
```powershell
adb -s 192.168.1.123:5555 logcat -s MainActivityNew UvcCameraManager
```

### Disconnect WiFi ADB
```powershell
adb disconnect
```

### Kembali ke USB mode
```powershell
# Colok HP via USB lagi
adb usb
```

---

## 🔄 Workflow Sehari-hari

### Pagi/Awal kerja:
```powershell
# 1. Pastikan HP dan Laptop di WiFi yang sama

# 2. Connect ADB via WiFi
adb connect 192.168.1.123:5555

# 3. Check koneksi
adb devices

# 4. Colok GroundChat ke USB Hub → ke HP
```

### Saat development:
```powershell
# Build & install via WiFi
.\BUILD_AND_INSTALL_WIFI.ps1

# Atau manual:
.\gradlew.bat assembleDebug
adb -s 192.168.1.123:5555 install -r app\build\outputs\apk\debug\app-debug.apk
```

---

## ⚡ Quick Commands

| Task | Command |
|------|---------|
| **Setup WiFi ADB** | `.\SETUP_WIFI_ADB.ps1` |
| **Build & Install** | `.\BUILD_AND_INSTALL_WIFI.ps1` |
| **Connect** | `adb connect 192.168.1.123:5555` |
| **Check devices** | `adb devices` |
| **View logs** | `adb logcat -s UvcCameraManager` |
| **Disconnect** | `adb disconnect` |

---

## 🐛 Troubleshooting

### ❌ "unable to connect to 192.168.1.xxx:5555"

**Solusi:**
1. Pastikan HP dan laptop di WiFi yang sama
2. Check IP HP masih sama (bisa berubah)
3. Restart adb: `adb kill-server` lalu `adb start-server`
4. Setup ulang: `.\SETUP_WIFI_ADB.ps1`

### ❌ Connection lost/timeout

**Solusi:**
```powershell
adb disconnect
adb connect 192.168.1.123:5555
```

### ❌ WiFi lambat/lag saat install APK

- Normal, WiFi lebih lambat dari USB
- Pastikan WiFi signal kuat
- Jangan download/streaming besar saat install

### ❌ HP sleep, koneksi putus

**Solusi:**
- Keep screen on saat development
- Developer Options → Stay awake (when charging)
- Reconnect: `adb connect 192.168.1.123:5555`

---

## 📊 Comparison

| Metode | USB Port HP | Install Speed | Setup |
|--------|-------------|---------------|-------|
| **USB Cable** | ❌ Terpakai laptop | ⚡ Cepat | Easy |
| **WiFi ADB** | ✅ Bebas untuk USB Hub | 🐢 Lebih lambat | Sekali setup |

---

## ✅ Keuntungan WiFi ADB

1. ✅ **USB port bebas** - bisa colok GroundChat, Camera, LiDAR
2. ✅ **Tidak perlu kabel** panjang HP-Laptop
3. ✅ **Mobility** - HP bisa dipindah-pindah (selama WiFi)
4. ✅ **Multiple devices** - bisa test ke beberapa HP sekaligus
5. ✅ **Wireless** - lebih rapih, no cable mess

---

## 📝 Files Created

| File | Purpose |
|------|---------|
| `SETUP_WIFI_ADB.ps1` | Setup ADB WiFi pertama kali |
| `BUILD_AND_INSTALL_WIFI.ps1` | Build & install APK via WiFi |
| `WIFI_ADB_CONFIG.txt` | Saved IP config (auto-generated) |
| `WIFI_ADB_GUIDE.md` | This guide |

---

## 🎓 How It Works

```
┌─────────────┐                          ┌─────────────┐
│   Laptop    │ ◄──── WiFi ADB ─────────►│     HP      │
│             │      Port 5555            │             │
└─────────────┘                          └──────┬──────┘
                                                 │
                                                 │ USB
                                                 ▼
                                         ┌──────────────┐
                                         │   USB Hub    │
                                         └──────┬───────┘
                                                │
                                    ┌───────────┼──────────┐
                                    │           │          │
                                    ▼           ▼          ▼
                              GroundChat    Camera     LiDAR
```

---

## 🚀 Next Steps

1. ✅ Run `.\SETUP_WIFI_ADB.ps1`
2. ✅ Cabut USB dari HP
3. ✅ Colok GroundChat ke USB Hub
4. ✅ Colok USB Hub ke HP
5. ✅ Run `.\BUILD_AND_INSTALL_WIFI.ps1`
6. ✅ Check app - USB devices should be detected!

**Happy coding! 🎉**
