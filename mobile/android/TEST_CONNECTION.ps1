# ============================================
# TEST ADB CONNECTION TO HP
# ============================================

Write-Host "=== Testing ADB Connection ===" -ForegroundColor Green

Write-Host "`n[1/3] Checking ADB installation..." -ForegroundColor Cyan
$adbCheck = Get-Command adb -ErrorAction SilentlyContinue

if ($adbCheck) {
    Write-Host "✓ ADB found: $($adbCheck.Source)" -ForegroundColor Green
} else {
    Write-Host "✗ ADB not found in PATH!" -ForegroundColor Red
    
    # Try direct path
    if (Test-Path "C:\Android\platform-tools\adb.exe") {
        Write-Host "`nℹ ADB exists but not in PATH. Using direct path..." -ForegroundColor Yellow
        $adbPath = "C:\Android\platform-tools\adb.exe"
    } else {
        Write-Host "`nPlease run SETUP_ANDROID_SDK.ps1 first, then restart PowerShell!" -ForegroundColor Yellow
        exit
    }
}

Write-Host "`n[2/3] Starting ADB server..." -ForegroundColor Cyan
adb start-server

Write-Host "`n[3/3] Checking connected devices..." -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
adb devices -l
Write-Host "============================================" -ForegroundColor Cyan

$devices = adb devices | Select-String "device$"
if ($devices) {
    Write-Host "`n✓ HP detected and authorized!" -ForegroundColor Green
    Write-Host "`nYou can now run BUILD_APK.ps1 to build and install the app!" -ForegroundColor Yellow
} else {
    $unauthorized = adb devices | Select-String "unauthorized"
    if ($unauthorized) {
        Write-Host "`n⚠️  HP detected but UNAUTHORIZED!" -ForegroundColor Yellow
        Write-Host "`nPlease:" -ForegroundColor Cyan
        Write-Host "1. Check your phone screen"
        Write-Host "2. Allow 'USB debugging' popup"
        Write-Host "3. Check 'Always allow from this computer'"
        Write-Host "4. Run this script again"
    } else {
        Write-Host "`n✗ No device detected!" -ForegroundColor Red
        Write-Host "`nPlease:" -ForegroundColor Yellow
        Write-Host "1. Connect HP via USB cable"
        Write-Host "2. Make sure USB Debugging is enabled in Developer Options"
        Write-Host "3. Try different USB cable/port if not detected"
        Write-Host "4. Run this script again"
    }
}

Write-Host "`n============================================" -ForegroundColor Green
