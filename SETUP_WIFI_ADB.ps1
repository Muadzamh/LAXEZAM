# ========================================
# SETUP ADB OVER WIFI - Cattle Weight Detector
# ========================================
# Script untuk setup koneksi ADB via WiFi
# Setelah setup, HP bisa disconnect dari USB dan tetap bisa install APK
# ========================================

Write-Host "╔═══════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║   ADB OVER WIFI SETUP - Cattle Weight Detector    ║" -ForegroundColor Cyan
Write-Host "╚═══════════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

# Step 1: Check if phone is connected via USB
Write-Host "📱 Step 1: Checking USB connection..." -ForegroundColor Yellow
$devices = adb devices | Select-String "device$"
if ($devices.Count -eq 0) {
    Write-Host "❌ ERROR: No device found!" -ForegroundColor Red
    Write-Host "   Please connect your phone via USB first" -ForegroundColor Red
    exit
}
Write-Host "✅ Phone detected via USB" -ForegroundColor Green
Write-Host ""

# Step 2: Enable ADB over TCP/IP
Write-Host "🔧 Step 2: Enabling ADB over TCP/IP on port 5555..." -ForegroundColor Yellow
adb tcpip 5555
Start-Sleep -Seconds 2
Write-Host "✅ TCP/IP mode enabled" -ForegroundColor Green
Write-Host ""

# Step 3: Get phone's WiFi IP address
Write-Host "📡 Step 3: Getting phone's WiFi IP address..." -ForegroundColor Yellow
Write-Host ""
Write-Host "⚠️  MANUAL STEP REQUIRED:" -ForegroundColor Magenta
Write-Host "   1. Buka Settings > About Phone > Status" -ForegroundColor White
Write-Host "   2. Atau Settings > Wi-Fi > [Your Network] > Advanced" -ForegroundColor White
Write-Host "   3. Cari IP Address (contoh: 192.168.1.xxx)" -ForegroundColor White
Write-Host ""
$phoneIP = Read-Host "   Masukkan IP Address HP Anda"

if ([string]::IsNullOrWhiteSpace($phoneIP)) {
    Write-Host "❌ IP Address tidak boleh kosong!" -ForegroundColor Red
    exit
}

Write-Host ""
Write-Host "✅ IP Address: $phoneIP" -ForegroundColor Green
Write-Host ""

# Step 4: Instructions to disconnect USB
Write-Host "🔌 Step 4: DISCONNECT USB CABLE FROM PHONE" -ForegroundColor Yellow
Write-Host "   Now you can disconnect USB cable and connect GroundChat/USB Hub" -ForegroundColor White
Write-Host ""
Read-Host "   Press ENTER after disconnecting USB cable"

# Step 5: Connect via WiFi
Write-Host ""
Write-Host "📶 Step 5: Connecting to phone via WiFi..." -ForegroundColor Yellow
$result = adb connect "${phoneIP}:5555"
Write-Host $result

if ($result -like "*connected*") {
    Write-Host ""
    Write-Host "╔═══════════════════════════════════════════════════╗" -ForegroundColor Green
    Write-Host "║          ✅ SUCCESS - WIFI ADB CONNECTED!         ║" -ForegroundColor Green
    Write-Host "╚═══════════════════════════════════════════════════╝" -ForegroundColor Green
    Write-Host ""
    Write-Host "📋 Next Steps:" -ForegroundColor Cyan
    Write-Host "   1. ✅ USB port sekarang bebas untuk GroundChat/USB Hub" -ForegroundColor White
    Write-Host "   2. ✅ Build & Install APK tetap jalan via WiFi" -ForegroundColor White
    Write-Host "   3. ✅ Gunakan: .\BUILD_AND_INSTALL_WIFI.ps1" -ForegroundColor White
    Write-Host ""
    Write-Host "📝 Saved connection info to: WIFI_ADB_CONFIG.txt" -ForegroundColor Yellow
    
    # Save to config file
    $configContent = @"
# ADB over WiFi Configuration
# Generated: $(Get-Date)
PHONE_IP=$phoneIP
PORT=5555
FULL_ADDRESS=${phoneIP}:5555

# To reconnect in future:
# adb connect ${phoneIP}:5555

# To disconnect:
# adb disconnect

# To go back to USB mode:
# adb usb
"@
    $configContent | Out-File "WIFI_ADB_CONFIG.txt" -Encoding UTF8
    
    Write-Host ""
    Write-Host "🔄 To reconnect later (if connection lost):" -ForegroundColor Cyan
    Write-Host "   adb connect ${phoneIP}:5555" -ForegroundColor White
    Write-Host ""
    
    # Test connection
    Write-Host "🧪 Testing connection..." -ForegroundColor Yellow
    adb devices
    
} else {
    Write-Host ""
    Write-Host "❌ Failed to connect via WiFi" -ForegroundColor Red
    Write-Host "   Possible issues:" -ForegroundColor Yellow
    Write-Host "   - Phone and laptop not on same WiFi network" -ForegroundColor White
    Write-Host "   - Firewall blocking port 5555" -ForegroundColor White
    Write-Host "   - Wrong IP address" -ForegroundColor White
    Write-Host ""
    Write-Host "   Try again or check WiFi connection" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Press any key to exit..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
