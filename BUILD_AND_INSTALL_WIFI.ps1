# ========================================
# BUILD & INSTALL APK VIA WIFI
# ========================================
# Script untuk build dan install APK via ADB WiFi
# HP tidak perlu colok USB - bisa pakai USB Hub untuk GroundChat
# ========================================

param(
    [string]$PhoneIP = ""
)

Write-Host "╔═══════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║     BUILD & INSTALL APK via WiFi                  ║" -ForegroundColor Cyan
Write-Host "╚═══════════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

# Load config if exists
$configFile = "WIFI_ADB_CONFIG.txt"
if (Test-Path $configFile) {
    Write-Host "📄 Loading WiFi config..." -ForegroundColor Yellow
    $configContent = Get-Content $configFile | Where-Object { $_ -notmatch '^#' -and $_ -match 'PHONE_IP=' }
    if ($configContent) {
        $savedIP = ($configContent -split '=')[1]
        if ([string]::IsNullOrWhiteSpace($PhoneIP)) {
            $PhoneIP = $savedIP
            Write-Host "✅ Using saved IP: $PhoneIP" -ForegroundColor Green
        }
    }
}

# Ask for IP if not provided
if ([string]::IsNullOrWhiteSpace($PhoneIP)) {
    Write-Host "⚠️  No IP address found" -ForegroundColor Yellow
    Write-Host ""
    $PhoneIP = Read-Host "Enter phone IP address (or run SETUP_WIFI_ADB.ps1 first)"
}

if ([string]::IsNullOrWhiteSpace($PhoneIP)) {
    Write-Host "❌ IP address required!" -ForegroundColor Red
    exit
}

Write-Host ""
Write-Host "📱 Target Phone: $PhoneIP`:5555" -ForegroundColor Cyan
Write-Host ""

# Step 1: Check/Connect to phone via WiFi
Write-Host "📶 Step 1: Connecting to phone via WiFi..." -ForegroundColor Yellow
$connectResult = adb connect "${PhoneIP}:5555" 2>&1
if ($connectResult -like "*already connected*" -or $connectResult -like "*connected to*") {
    Write-Host "✅ Connected to phone" -ForegroundColor Green
} else {
    Write-Host "❌ Failed to connect to $PhoneIP" -ForegroundColor Red
    Write-Host "   Make sure:" -ForegroundColor Yellow
    Write-Host "   - Phone and laptop on same WiFi" -ForegroundColor White
    Write-Host "   - Run SETUP_WIFI_ADB.ps1 first" -ForegroundColor White
    exit
}

Write-Host ""
adb devices
Write-Host ""

# Step 2: Navigate to project directory
Write-Host "📂 Step 2: Navigating to project..." -ForegroundColor Yellow
$projectPath = "D:\My Project\Capstone\mobile\android\CattleWeightDetector"
if (-not (Test-Path $projectPath)) {
    Write-Host "❌ Project not found at: $projectPath" -ForegroundColor Red
    exit
}
Set-Location $projectPath
Write-Host "✅ Project path: $projectPath" -ForegroundColor Green
Write-Host ""

# Step 3: Build APK
Write-Host "🔨 Step 3: Building APK..." -ForegroundColor Yellow
Write-Host "   This may take a minute..." -ForegroundColor Gray
Write-Host ""

$buildResult = .\gradlew.bat assembleDebug 2>&1

if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ Build successful!" -ForegroundColor Green
} else {
    Write-Host "❌ Build failed!" -ForegroundColor Red
    Write-Host $buildResult
    exit
}

Write-Host ""

# Step 4: Install APK
Write-Host "📦 Step 4: Installing APK to phone (via WiFi)..." -ForegroundColor Yellow
$apkPath = "app\build\outputs\apk\debug\app-debug.apk"

if (-not (Test-Path $apkPath)) {
    Write-Host "❌ APK not found at: $apkPath" -ForegroundColor Red
    exit
}

Write-Host "   APK: $apkPath" -ForegroundColor Gray
Write-Host "   Installing..." -ForegroundColor Gray

$installResult = adb -s "${PhoneIP}:5555" install -r $apkPath 2>&1

if ($installResult -like "*Success*") {
    Write-Host "✅ APK installed successfully!" -ForegroundColor Green
} else {
    Write-Host "❌ Installation failed!" -ForegroundColor Red
    Write-Host $installResult
    exit
}

Write-Host ""

# Step 5: Launch app
Write-Host "🚀 Step 5: Launching app..." -ForegroundColor Yellow
adb -s "${PhoneIP}:5555" shell am start -n com.capstone.cattleweight/.MainActivityNew
Start-Sleep -Seconds 2
Write-Host "✅ App launched!" -ForegroundColor Green
Write-Host ""

# Step 6: Show logs
Write-Host "📋 Step 6: Showing USB device check logs..." -ForegroundColor Yellow
Write-Host "   (Press Ctrl+C to stop)" -ForegroundColor Gray
Write-Host ""

Start-Sleep -Seconds 2
adb -s "${PhoneIP}:5555" logcat -d -s MainActivityNew:D UvcCameraManager:I | Select-String "USB|MainActivityNew" | Select-Object -First 30

Write-Host ""
Write-Host "╔═══════════════════════════════════════════════════╗" -ForegroundColor Green
Write-Host "║              ✅ DONE!                             ║" -ForegroundColor Green
Write-Host "╚═══════════════════════════════════════════════════╝" -ForegroundColor Green
Write-Host ""
Write-Host "✅ APK installed via WiFi" -ForegroundColor Green
Write-Host "✅ USB port bebas untuk GroundChat/USB Hub" -ForegroundColor Green
Write-Host "✅ Check Logcat untuk hasil USB device scan" -ForegroundColor Green
Write-Host ""
Write-Host "💡 Tips:" -ForegroundColor Cyan
Write-Host "   - Colok GroundChat ke USB Hub sekarang" -ForegroundColor White
Write-Host "   - Open app untuk lihat USB devices" -ForegroundColor White
Write-Host "   - Run script ini lagi untuk rebuild & install" -ForegroundColor White
Write-Host ""
