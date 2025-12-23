# BUILD AND INSTALL APK VIA WIFI
# Simple version

Write-Host "===============================================" -ForegroundColor Cyan
Write-Host "   BUILD & INSTALL APK via WiFi" -ForegroundColor Cyan
Write-Host "===============================================" -ForegroundColor Cyan
Write-Host ""

# Load IP from config
$configFile = "WIFI_ADB_CONFIG.txt"
$PhoneIP = ""

if (Test-Path $configFile) {
    Write-Host "[INFO] Loading WiFi config..." -ForegroundColor Yellow
    $lines = Get-Content $configFile
    foreach ($line in $lines) {
        if ($line -match "PHONE_IP=(.+)") {
            $PhoneIP = $matches[1]
            break
        }
    }
}

if ([string]::IsNullOrWhiteSpace($PhoneIP)) {
    Write-Host "[ERROR] No IP found in config!" -ForegroundColor Red
    Write-Host "Please run SETUP_WIFI_ADB_SIMPLE.ps1 first" -ForegroundColor Yellow
    exit
}

Write-Host "[INFO] Target Phone: $PhoneIP`:5555" -ForegroundColor Green
Write-Host ""

# Step 1: Connect via WiFi
Write-Host "[1] Connecting to phone via WiFi..." -ForegroundColor Yellow
$target = "$PhoneIP`:5555"
adb connect $target
Start-Sleep -Seconds 2

# Check connection
$devices = adb devices | Select-String $PhoneIP
if (-not $devices) {
    Write-Host "[ERROR] Failed to connect!" -ForegroundColor Red
    Write-Host "Make sure phone and laptop on same WiFi" -ForegroundColor Yellow
    exit
}
Write-Host "OK - Connected to $target" -ForegroundColor Green
Write-Host ""

# Step 2: Navigate to project
Write-Host "[2] Navigating to project..." -ForegroundColor Yellow
$projectPath = "D:\My Project\Capstone\mobile\android\CattleWeightDetector"
if (-not (Test-Path $projectPath)) {
    Write-Host "[ERROR] Project not found!" -ForegroundColor Red
    exit
}
Set-Location $projectPath
Write-Host "OK - Project: $projectPath" -ForegroundColor Green
Write-Host ""

# Step 3: Build APK
Write-Host "[3] Building APK..." -ForegroundColor Yellow
Write-Host "    Please wait, this may take a minute..." -ForegroundColor Gray
.\gradlew.bat assembleDebug --quiet

if ($LASTEXITCODE -eq 0) {
    Write-Host "OK - Build successful!" -ForegroundColor Green
} else {
    Write-Host "[ERROR] Build failed!" -ForegroundColor Red
    exit
}
Write-Host ""

# Step 4: Install APK
Write-Host "[4] Installing APK via WiFi..." -ForegroundColor Yellow
$apkPath = "app\build\outputs\apk\debug\app-debug.apk"

if (-not (Test-Path $apkPath)) {
    Write-Host "[ERROR] APK not found!" -ForegroundColor Red
    exit
}

Write-Host "    APK: $apkPath" -ForegroundColor Gray
Write-Host "    Installing to $target..." -ForegroundColor Gray
adb -s $target install -r $apkPath

if ($LASTEXITCODE -eq 0) {
    Write-Host "OK - APK installed!" -ForegroundColor Green
} else {
    Write-Host "[ERROR] Installation failed!" -ForegroundColor Red
    exit
}
Write-Host ""

# Step 5: Launch app
Write-Host "[5] Launching app..." -ForegroundColor Yellow
adb -s $target shell am start -n com.capstone.cattleweight/.MainActivityNew
Start-Sleep -Seconds 3
Write-Host "OK - App launched!" -ForegroundColor Green
Write-Host ""

# Step 6: Show logs
Write-Host "[6] Checking USB devices..." -ForegroundColor Yellow
Write-Host "    (Showing last 30 relevant logs)" -ForegroundColor Gray
Write-Host ""

Start-Sleep -Seconds 2
adb -s $target logcat -d -s MainActivityNew:D UvcCameraManager:I | Select-String "USB|Device|MainActivityNew" | Select-Object -First 30

Write-Host ""
Write-Host "===============================================" -ForegroundColor Green
Write-Host "   DONE!" -ForegroundColor Green
Write-Host "===============================================" -ForegroundColor Green
Write-Host ""
Write-Host "APK installed via WiFi to: $target" -ForegroundColor Cyan
Write-Host "Check app on your phone for USB devices" -ForegroundColor Cyan
Write-Host ""
Write-Host "To see full logs:" -ForegroundColor Yellow
Write-Host "  adb -s $target logcat -s UvcCameraManager" -ForegroundColor White
Write-Host ""

Read-Host "Press ENTER to exit"
