==============================================
  SETUP ADB WIFI - QUICK START
==============================================

MASALAH ANDA:
- HP perlu colok GroundChat via USB Hub
- Laptop perlu install APK ke HP
- Tidak bisa keduanya karena cuma 1 USB port

SOLUSI: ADB over WiFi!
- HP colok USB Hub untuk GroundChat
- Laptop install APK via WiFi
- Tidak perlu kabel USB HP-Laptop

==============================================
LANGKAH SETUP (Sekali doang!)
==============================================

1. COLOK HP KE LAPTOP VIA USB (sekali ini saja)

2. JALANKAN SCRIPT SETUP:
   .\SETUP_WIFI_ADB_SIMPLE.ps1

3. SCRIPT AKAN MINTA IP HP:
   - Buka HP: Settings > About Phone > Status
   - Cari "IP address" (contoh: 192.168.1.123)
   - Ketik IP tersebut di script

4. CABUT USB DARI HP
   - Script akan bilang kapan
   - Setelah cabut, HP dan Laptop tetap terhubung via WiFi!

5. COLOK GROUNDCHAT/USB HUB KE HP
   - Sekarang USB port HP bebas!

==============================================
BUILD & INSTALL APK VIA WIFI
==============================================

Setiap kali mau update APK:

   .\BUILD_AND_INSTALL_WIFI.ps1

Script akan:
- Connect via WiFi
- Build APK
- Install ke HP (tanpa kabel!)
- Launch app
- Show logs

==============================================
QUICK COMMANDS
==============================================

# Connect ke HP via WiFi
adb connect 192.168.1.123:5555
(ganti dengan IP HP Anda)

# Check koneksi
adb devices

# Build & Install
.\BUILD_AND_INSTALL_WIFI.ps1

# View logs
adb logcat -s UvcCameraManager MainActivityNew

# Disconnect
adb disconnect

==============================================
FILES YANG SUDAH DIBUAT
==============================================

1. SETUP_WIFI_ADB_SIMPLE.ps1
   -> Setup WiFi ADB (run sekali)

2. BUILD_AND_INSTALL_WIFI.ps1
   -> Build & install APK via WiFi

3. WIFI_ADB_GUIDE.md
   -> Panduan lengkap

4. WIFI_ADB_CONFIG.txt
   -> Auto-generated setelah setup

==============================================
TROUBLESHOOTING
==============================================

Q: Connection failed?
A: Pastikan HP dan Laptop di WiFi yang SAMA

Q: IP berubah?
A: Setup ulang dengan IP baru

Q: Lambat?
A: Normal, WiFi lebih lambat dari USB

Q: Connection lost?
A: Run: adb connect 192.168.1.123:5555

==============================================
CURRENT STATUS
==============================================

HP Anda: 22783b001e017ece
Status: Connected via USB
Next: Run SETUP_WIFI_ADB_SIMPLE.ps1

==============================================
READY TO GO!
==============================================

Langkah selanjutnya:
1. Cek IP HP di Settings
2. Run: .\SETUP_WIFI_ADB_SIMPLE.ps1
3. Masukkan IP HP
4. Cabut USB
5. Colok GroundChat
6. Run: .\BUILD_AND_INSTALL_WIFI.ps1
7. DONE!

==============================================
