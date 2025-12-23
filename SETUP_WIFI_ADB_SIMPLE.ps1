# ADB over WiFi Setup Script
# Simple version without unicode issues

Write-Host "===============================================" -ForegroundColor Cyan
Write-Host "   ADB OVER WIFI SETUP" -ForegroundColor Cyan
Write-Host "===============================================" -ForegroundColor Cyan
Write-Host ""

# Step 1: Check USB connection
Write-Host "[1] Checking USB connection..." -ForegroundColor Yellow
$devices = adb devices | Select-String "device$"
if ($devices.Count -eq 0) {
    Write-Host "ERROR: No device found!" -ForegroundColor Red
    Write-Host "Please connect your phone via USB first" -ForegroundColor Red
    exit
}
Write-Host "OK - Phone detected via USB" -ForegroundColor Green
Write-Host ""

# Step 2: Enable TCP/IP
Write-Host "[2] Enabling ADB over TCP/IP..." -ForegroundColor Yellow
adb tcpip 5555
Start-Sleep -Seconds 2
Write-Host "OK - TCP/IP mode enabled on port 5555" -ForegroundColor Green
Write-Host ""

# Step 3: Get IP address
Write-Host "[3] Get your phone's WiFi IP address" -ForegroundColor Yellow
Write-Host ""
Write-Host "How to find IP on your phone:" -ForegroundColor White
Write-Host "  - Settings > About Phone > Status" -ForegroundColor Gray
Write-Host "  - Or Settings > Wi-Fi > (Your Network) > Advanced" -ForegroundColor Gray
Write-Host ""
$phoneIP = Read-Host "Enter phone IP address (e.g., 192.168.1.123)"

if ([string]::IsNullOrWhiteSpace($phoneIP)) {
    Write-Host "ERROR: IP Address cannot be empty!" -ForegroundColor Red
    exit
}

Write-Host ""
Write-Host "Target IP: $phoneIP" -ForegroundColor Green
Write-Host ""

# Step 4: Disconnect USB
Write-Host "[4] DISCONNECT USB CABLE NOW" -ForegroundColor Yellow
Write-Host ""
Write-Host "You can now:" -ForegroundColor White
Write-Host "  - Disconnect USB cable from phone" -ForegroundColor Gray
Write-Host "  - Connect GroundChat to USB Hub" -ForegroundColor Gray
Write-Host "  - Connect USB Hub to phone" -ForegroundColor Gray
Write-Host ""
Read-Host "Press ENTER after disconnecting USB"

# Step 5: Connect via WiFi
Write-Host ""
Write-Host "[5] Connecting via WiFi..." -ForegroundColor Yellow
$result = adb connect "${phoneIP}:5555"
Write-Host $result

if ($result -like "*connected*") {
    Write-Host ""
    Write-Host "===============================================" -ForegroundColor Green
    Write-Host "   SUCCESS - WIFI ADB CONNECTED!" -ForegroundColor Green
    Write-Host "===============================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "Next steps:" -ForegroundColor Cyan
    Write-Host "  1. USB port is now free for GroundChat/USB Hub" -ForegroundColor White
    Write-Host "  2. Build & Install APK via WiFi:" -ForegroundColor White
    Write-Host "     .\BUILD_AND_INSTALL_WIFI.ps1" -ForegroundColor Gray
    Write-Host ""
    
    # Save config
    $config = "PHONE_IP=$phoneIP`nPORT=5555`nFULL_ADDRESS=${phoneIP}:5555"
    $config | Out-File "WIFI_ADB_CONFIG.txt" -Encoding ASCII
    Write-Host "Saved to: WIFI_ADB_CONFIG.txt" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "To reconnect later:" -ForegroundColor Cyan
    Write-Host "  adb connect ${phoneIP}:5555" -ForegroundColor White
    Write-Host ""
    
    # Test
    Write-Host "Testing connection..." -ForegroundColor Yellow
    adb devices
    
} else {
    Write-Host ""
    Write-Host "FAILED to connect via WiFi" -ForegroundColor Red
    Write-Host "Possible issues:" -ForegroundColor Yellow
    Write-Host "  - Phone and laptop not on same WiFi" -ForegroundColor White
    Write-Host "  - Wrong IP address" -ForegroundColor White
    Write-Host "  - Firewall blocking port 5555" -ForegroundColor White
}

Write-Host ""
Read-Host "Press ENTER to exit"
