# GroundChat Camera Testing Checklist
# Test semua fitur UVC camera integration

## 📋 TESTING CHECKLIST

### 1️⃣ Pre-Testing Setup
- [ ] Android device terhubung via USB (USB debugging ON)
- [ ] GroundChat camera siap
- [ ] USB OTG hub/adapter tersedia
- [ ] APK sudah di-build (`.\gradlew assembleDebug`)

### 2️⃣ Installation Test
```powershell
# Run automated test script
.\TEST_GROUNDCHAT.ps1
```

**Expected Results:**
- ✅ APK size ~24 MB (dengan native libraries)
- ✅ Installation successful
- ✅ App launches without crash
- ✅ USB Monitor registered (check logs)

### 3️⃣ Camera Detection Test

**Steps:**
1. Launch app
2. Navigate to Dataset Fragment
3. Plug in GroundChat camera via OTG

**Expected Logs:**
```
I USBMonitor: USB device attached: USB Camera
I USBMonitor: Vendor ID: 0x0EDC, Product ID: 0x2050
I UvcCameraManager: USB device attached: USB Camera
I UvcCameraManager: UVC camera opened successfully
I UvcCameraManager: Preview started successfully
```

**UI Expected:**
- [ ] Toast: "GroundChat camera detected"
- [ ] Switch camera toggle visible
- [ ] USB permission dialog appears (first time)

### 4️⃣ Permission Test

**Steps:**
1. When permission dialog appears
2. Check "Always allow" (optional)
3. Click "OK"

**Expected:**
- [ ] Permission granted
- [ ] Camera connects automatically
- [ ] Preview starts

**If permission denied:**
- [ ] Switch hidden
- [ ] App continues with built-in camera

### 5️⃣ Preview Test

**Steps:**
1. After camera connected
2. Toggle switch to "USB Camera"

**Expected:**
- [ ] CameraX preview hidden
- [ ] UVC preview shows (TextureView)
- [ ] Camera status: "📷 GroundChat Camera" (green)
- [ ] Live video from GroundChat displayed

**Check Logs:**
```
I UvcCameraManager: Preview started successfully
D DatasetFragment: USB Camera preview started
```

**If black screen:**
- Check logs for format errors
- Expected: MJPEG 640x480 or YUYV fallback

### 6️⃣ Capture Test

**Steps:**
1. With USB camera active
2. Position camera at cattle
3. Click "📸 Capture" button

**Expected:**
- [ ] Capture status shows progress
- [ ] Image saved to gallery
- [ ] Toast: "Saved to DCIM/CattleDataset/"
- [ ] Counter increments

**Check Image:**
- [ ] Image quality good (640x480)
- [ ] Metadata includes LiDAR data
- [ ] File format: JPG
- [ ] File size: ~100-300 KB

### 7️⃣ Switch Camera Test

**Steps:**
1. Toggle switch OFF (back to built-in)
2. Toggle switch ON (back to USB)

**Expected:**
- [ ] Smooth transition
- [ ] No crash
- [ ] Preview updates correctly
- [ ] Camera status updates

**Check Logs:**
```
D DatasetFragment: Switching to USB camera
I UvcCameraManager: Preview started successfully
```

### 8️⃣ Disconnect Test

**Steps:**
1. While USB camera active
2. Unplug GroundChat

**Expected:**
- [ ] Log: "USB device disconnected"
- [ ] Auto-switch to built-in camera
- [ ] Toggle switch becomes OFF
- [ ] Toast: camera disconnected
- [ ] No crash

### 9️⃣ Reconnect Test

**Steps:**
1. After disconnect
2. Plug in GroundChat again

**Expected:**
- [ ] Auto-detected
- [ ] Permission auto-granted (if "Always allow")
- [ ] Switch becomes visible again
- [ ] Can toggle back to USB camera

### 🔟 Performance Test

**Monitor during operation:**
```powershell
# CPU/Memory usage
adb shell top -n 1 | Select-String "cattleweight"
```

**Expected:**
- [ ] CPU usage < 50% during preview
- [ ] Memory < 200 MB
- [ ] No memory leaks
- [ ] Smooth frame rate (~15-30 fps)

### 1️⃣1️⃣ Stability Test

**Steps:**
1. Switch camera 10+ times
2. Capture 10+ images
3. Plug/unplug camera 5+ times
4. Rotate device

**Expected:**
- [ ] No crashes
- [ ] No ANR (Application Not Responding)
- [ ] All captures successful
- [ ] Preview restores after rotation

### 1️⃣2️⃣ LiDAR Integration Test

**Steps:**
1. Connect both LiDAR and GroundChat
2. Use USB hub if needed
3. Test data capture

**Expected:**
- [ ] LiDAR distance shows (not confused with camera)
- [ ] Camera preview works
- [ ] Both data saved together
- [ ] No USB conflicts

---

## 🐛 TROUBLESHOOTING

### Issue: "No UVC devices found"
**Solutions:**
- Check GroundChat connected via OTG
- Check USB mode (should be Host mode)
- Try different USB port/hub
- Check `lsusb` on device

### Issue: Black preview screen
**Solutions:**
- Check logs for format errors
- Camera might not support MJPEG → will fallback to YUYV
- Try different resolution
- Check Surface ready

### Issue: Permission always denied
**Solutions:**
- Grant manually via Settings > Apps > Permissions
- Check AndroidManifest USB intent filter
- Reinstall app

### Issue: Native library not found
**Solutions:**
- Check .so files in APK: `unzip -l app-debug.apk | grep "\.so"`
- Expected: lib/{armeabi-v7a,arm64-v8a}/libUVCCamera.so
- Rebuild: `.\gradlew clean assembleDebug`

### Issue: App crashes on capture
**Solutions:**
- Check TextureView bitmap available
- Check storage permission
- Check available disk space
- See crash logs: `adb logcat AndroidRuntime:E *:S`

---

## 📊 LOG COMMANDS

### Real-time camera monitoring:
```powershell
adb logcat -v time -s UvcCameraManager:* USBMonitor:* UVCCamera:*
```

### Full debug logs:
```powershell
adb logcat -v time > groundchat_test.log
```

### Crash logs only:
```powershell
adb logcat -v time AndroidRuntime:E *:S
```

### USB events:
```powershell
adb logcat -v time -s UsbHostManager:* UsbDeviceManager:*
```

---

## ✅ SUCCESS CRITERIA

**Minimum Requirements:**
- [x] APK builds successfully
- [ ] GroundChat detected (VID: 0x0EDC, PID: 0x2050)
- [ ] Permission granted
- [ ] Preview displays video
- [ ] Can capture images
- [ ] Images saved with metadata

**Full Success:**
- [ ] All above + smooth switching
- [ ] No crashes on disconnect
- [ ] LiDAR works simultaneously
- [ ] Performance acceptable
- [ ] Stable after extended use

---

## 📝 TEST RESULTS

**Date:** _____________
**Tester:** _____________
**Device:** _____________
**Android Version:** _____________

**Results:**
- Detection: ⬜ PASS ⬜ FAIL
- Preview: ⬜ PASS ⬜ FAIL
- Capture: ⬜ PASS ⬜ FAIL
- Stability: ⬜ PASS ⬜ FAIL

**Notes:**
_________________________________________________________________
_________________________________________________________________
_________________________________________________________________

