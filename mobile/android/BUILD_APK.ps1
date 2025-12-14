# ============================================
# BUILD APK DAN INSTALL KE HP
# ============================================

Write-Host "=== Building Cattle Weight Detector APK ===" -ForegroundColor Green

# Navigate to project
$projectPath = "d:\My Project\Capstone\mobile\android\CattleWeightDetector"
Set-Location $projectPath

Write-Host "`nProject path: $projectPath" -ForegroundColor Cyan

# 1. Clean previous build
Write-Host "`n[1/4] Cleaning previous build..." -ForegroundColor Cyan
if (Test-Path ".\gradlew.bat") {
    .\gradlew.bat clean
} else {
    Write-Host "✗ gradlew.bat not found!" -ForegroundColor Red
    Write-Host "Make sure you're in the correct directory." -ForegroundColor Yellow
    exit
}

# 2. Build APK
Write-Host "`n[2/4] Building APK..." -ForegroundColor Cyan
Write-Host "⏳ This may take 3-5 minutes on first build (downloading dependencies)..." -ForegroundColor Yellow
Write-Host "Please wait..." -ForegroundColor Yellow

.\gradlew.bat assembleDebug

# 3. Check if build successful
$apkPath = ".\app\build\outputs\apk\debug\app-debug.apk"
if (Test-Path $apkPath) {
    $apkSize = (Get-Item $apkPath).Length / 1MB
    Write-Host "`n============================================" -ForegroundColor Green
    Write-Host "✓ APK built successfully!" -ForegroundColor Green
    Write-Host "============================================" -ForegroundColor Green
    Write-Host "Location: $apkPath" -ForegroundColor Cyan
    Write-Host "Size: $([math]::Round($apkSize, 2)) MB" -ForegroundColor Cyan
    
    # 4. Check HP connection
    Write-Host "`n[3/4] Checking HP connection..." -ForegroundColor Cyan
    $devices = adb devices
    Write-Host $devices
    
    if ($devices -match "device$") {
        Write-Host "`n✓ HP detected!" -ForegroundColor Green
        
        # 5. Install to HP
        Write-Host "`n[4/4] Installing to HP..." -ForegroundColor Cyan
        Write-Host "⏳ Installing APK..." -ForegroundColor Yellow
        
        adb install -r $apkPath
        
        if ($LASTEXITCODE -eq 0) {
            Write-Host "`n============================================" -ForegroundColor Green
            Write-Host "✓ Installation complete!" -ForegroundColor Green
            Write-Host "============================================" -ForegroundColor Green
            Write-Host "`n📱 Check your phone for 'Cattle Weight Detector' app!" -ForegroundColor Yellow
            Write-Host "`nNext steps:" -ForegroundColor Cyan
            Write-Host "1. Make sure Flask server is running (python lidar_server.py)"
            Write-Host "2. Update SERVER_URL in MainActivity.java with your PC IP"
            Write-Host "3. Make sure HP and PC are on same WiFi"
            Write-Host "4. Open the app and test!"
        } else {
            Write-Host "`n✗ Installation failed!" -ForegroundColor Red
            Write-Host "Try manual install: adb install -r $apkPath" -ForegroundColor Yellow
        }
    } else {
        Write-Host "`n✗ No device detected!" -ForegroundColor Red
        Write-Host "`nPlease:" -ForegroundColor Yellow
        Write-Host "1. Connect HP via USB"
        Write-Host "2. Enable USB Debugging in Developer Options"
        Write-Host "3. Allow USB debugging popup on phone"
        Write-Host "4. Run: .\TEST_CONNECTION.ps1"
        Write-Host "`nOr manually install:"
        Write-Host "adb install -r $apkPath" -ForegroundColor Cyan
    }
} else {
    Write-Host "`n============================================" -ForegroundColor Red
    Write-Host "✗ Build failed!" -ForegroundColor Red
    Write-Host "============================================" -ForegroundColor Red
    Write-Host "`nCheck errors above for details." -ForegroundColor Yellow
    Write-Host "`nCommon issues:" -ForegroundColor Cyan
    Write-Host "1. Internet connection needed for first build (downloads dependencies)"
    Write-Host "2. Make sure Java 23 is installed and in PATH"
    Write-Host "3. Try: .\gradlew.bat clean assembleDebug --stacktrace"
}

Write-Host "`n============================================" -ForegroundColor Green
