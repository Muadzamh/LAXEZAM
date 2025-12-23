# QUICK START - GroundChat Camera Testing

## 🚀 Automated Test (Recommended)

```powershell
cd "d:\My Project\Capstone\mobile\android\CattleWeightDetector"
.\TEST_GROUNDCHAT.ps1
```

**Script akan:**
1. ✅ Check device connection
2. ✅ Install APK (23.72 MB)
3. ✅ Launch app
4. ✅ Monitor logs real-time

**Saat script running:**
- Tunggu sampai "PLUG IN GROUNDCHAT CAMERA NOW"
- Hubungkan GroundChat via USB OTG
- Watch log untuk deteksi otomatis

---

## 📱 Manual Testing Steps

### A. Install & Launch
```powershell
# Install
adb install -r app\build\outputs\apk\debug\app-debug.apk

# Launch
adb shell am start -n com.capstone.cattleweight/.MainActivityNew
```

### B. Monitor Logs (Terminal terpisah)
```powershell
# Real-time UVC logs
adb logcat -v time -s UvcCameraManager:* USBMonitor:* UVCCamera:*

# Full debug logs
adb logcat -v time > test_$(Get-Date -Format 'yyyyMMdd_HHmmss').log
```

### C. Test Flow

1. **Launch App** ✅ (sudah dilakukan di atas)

2. **Navigate ke Dataset Tab**
   - Tap "Dataset" di bottom navigation
   
3. **Hubungkan GroundChat**
   - Pasang USB OTG adapter
   - Colok GroundChat camera
   
4. **Expected Logs:**
   ```
   I USBMonitor: USB device attached: USB Camera
   I USBMonitor: Vendor ID: 0x0EDC, Product ID: 0x2050  
   I UvcCameraManager: USB device attached
   I UvcCameraManager: UVC camera opened successfully
   I UvcCameraManager: Preview started successfully
   ```

5. **Expected UI:**
   - 🔔 Toast: "GroundChat camera detected"
   - 🎚️ Toggle switch "Use USB Camera" muncul
   - ✅ Permission dialog (first time only)

6. **Grant Permission**
   - Tap "OK" pada dialog
   - Optional: Check "Always allow"

7. **Aktifkan USB Camera**
   - Toggle switch ON → "Use USB Camera"
   - Camera status: "📷 GroundChat Camera" (hijau)
   - Preview live video dari GroundChat

8. **Test Capture**
   - Arahkan ke objek
   - Tap "📸 Capture"
   - Status: "✅ Image Saved"
   - Check gallery: DCIM/CattleDataset/

9. **Test Switch**
   - Toggle OFF → back to built-in camera
   - Toggle ON → back to GroundChat
   - No crash, smooth transition

10. **Test Disconnect**
    - Cabut GroundChat
    - Auto-switch ke built-in
    - Toggle switch hilang
    - No crash

---

## 🔍 Quick Log Checks

### Check if camera detected:
```powershell
adb logcat -d | Select-String "0x0EDC"
```

### Check preview status:
```powershell
adb logcat -d | Select-String "Preview started"
```

### Check for crashes:
```powershell
adb logcat -d AndroidRuntime:E *:S
```

### Check native library loaded:
```powershell
adb logcat -d | Select-String "libUVCCamera"
```

---

## ✅ Success Indicators

**Camera Detected:**
- Log: "USB device attached: USB Camera"
- Log: "Vendor ID: 0x0EDC, Product ID: 0x2050"

**Preview Working:**
- Log: "Preview started successfully"
- UI: Live video tampil (not black screen)
- UI: Status hijau "📷 GroundChat Camera"

**Capture Working:**
- Log: "USB camera image captured"
- File saved: `/sdcard/DCIM/CattleDataset/cattle_YYYYMMDD_HHMMSS.jpg`
- Toast: "Saved to DCIM/CattleDataset/"

---

## ❌ Troubleshooting

### "No UVC devices found"
```powershell
# Check USB devices on phone
adb shell lsusb

# Check OTG support
adb shell cat /sys/class/android_usb/android0/functions
```

### Black preview screen
```powershell
# Check format errors
adb logcat -d | Select-String "setPreviewSize"

# Expected: MJPEG or YUYV fallback
```

### Permission denied
```powershell
# Grant manually
adb shell pm grant com.capstone.cattleweight android.permission.CAMERA

# Or reinstall
adb uninstall com.capstone.cattleweight
adb install app\build\outputs\apk\debug\app-debug.apk
```

### Native library error
```powershell
# Check .so files in APK
unzip -l app\build\outputs\apk\debug\app-debug.apk | Select-String ".so"

# Expected:
# lib/armeabi-v7a/libUVCCamera.so
# lib/armeabi-v7a/libuvc.so
# lib/armeabi-v7a/libusb100.so
# lib/armeabi-v7a/libjpeg-turbo1500.so
# (same for arm64-v8a)
```

---

## 📊 Performance Check

```powershell
# CPU usage (during preview)
adb shell top -n 1 | Select-String "cattleweight"

# Memory usage
adb shell dumpsys meminfo com.capstone.cattleweight | Select-String "TOTAL"

# Expected:
# CPU: < 50%
# Memory: < 200 MB
```

---

## 📸 Verify Captured Images

```powershell
# List recent captures
adb shell ls -lt /sdcard/DCIM/CattleDataset/ | Select-Object -First 10

# Pull latest image
adb pull /sdcard/DCIM/CattleDataset/cattle_$(Get-Date -Format 'yyyyMMdd')_*.jpg ./test_images/

# Check image details
Get-Item ./test_images/*.jpg | Select-Object Name, Length, LastWriteTime
```

---

## 🎯 Test Completion Checklist

- [ ] APK installed successfully
- [ ] App launches without crash
- [ ] GroundChat detected (VID: 0x0EDC, PID: 0x2050)
- [ ] Permission granted
- [ ] Live preview displays video
- [ ] Can capture images (640x480)
- [ ] Images saved to gallery
- [ ] Switch camera works (built-in ↔ USB)
- [ ] No crash on disconnect
- [ ] LiDAR still works (if connected)
- [ ] Performance acceptable

---

**Full checklist:** See [TESTING_CHECKLIST.md](TESTING_CHECKLIST.md)

