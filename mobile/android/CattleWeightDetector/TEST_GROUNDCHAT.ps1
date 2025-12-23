# GroundChat USB Camera Testing Script
# Capstone Project - Cattle Weight Detection

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   GROUNDCHAT CAMERA TESTING" -ForegroundColor Cyan  
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Step 1: Check device
Write-Host "STEP 1: Checking device..." -ForegroundColor Yellow
$devices = adb devices 2>&1 | Select-String "device$"
if ($devices) {
    Write-Host "  OK - Device connected" -ForegroundColor Green
} else {
    Write-Host "  ERROR - No device found" -ForegroundColor Red
    exit 1
}

# Step 2: Check APK
Write-Host ""
Write-Host "STEP 2: Checking APK..." -ForegroundColor Yellow
$apkPath = "app\build\outputs\apk\debug\app-debug.apk"
if (Test-Path $apkPath) {
    $apkSize = [math]::Round(((Get-Item $apkPath).Length / 1MB), 2)
    Write-Host "  OK - APK found: $apkSize MB" -ForegroundColor Green
} else {
    Write-Host "  ERROR - APK not found" -ForegroundColor Red
    exit 1
}

# Step 3: Install
Write-Host ""
Write-Host "STEP 3: Installing APK..." -ForegroundColor Yellow
$result = adb install -r $apkPath 2>&1 | Out-String
if ($result -match "Success") {
    Write-Host "  OK - Installation successful" -ForegroundColor Green
} else {
    Write-Host "  ERROR - Installation failed" -ForegroundColor Red
    exit 1
}

# Step 4: Clear logs
Write-Host ""
Write-Host "STEP 4: Clearing logs..." -ForegroundColor Yellow
adb logcat -c 2>&1 | Out-Null
Write-Host "  OK - Logs cleared" -ForegroundColor Green

# Step 5: Launch
Write-Host ""
Write-Host "STEP 5: Launching app..." -ForegroundColor Yellow
adb shell am start -n com.capstone.cattleweight/.MainActivityNew 2>&1 | Out-Null
Start-Sleep -Seconds 2
Write-Host "  OK - App launched" -ForegroundColor Green

# Step 6: Monitor
Write-Host ""
Write-Host "STEP 6: Monitoring logs..." -ForegroundColor Yellow
Write-Host "  PLUG IN GROUNDCHAT CAMERA NOW" -ForegroundColor Cyan
Write-Host "  Expected VID: 0x0EDC, PID: 0x2050" -ForegroundColor Gray
Write-Host "  Press Ctrl+C to stop" -ForegroundColor Gray
Write-Host ""

adb logcat -v time -s UvcCameraManager:* USBMonitor:* UVCCamera:* DatasetFragment:* AndroidRuntime:E
