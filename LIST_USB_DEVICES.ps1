# LIST USB DEVICES - Check semua device yang tercolok di HP
# Via ADB WiFi

param(
    [string]$PhoneIP = ""
)

Write-Host "===============================================" -ForegroundColor Cyan
Write-Host "   USB DEVICES LIST - Via ADB" -ForegroundColor Cyan
Write-Host "===============================================" -ForegroundColor Cyan
Write-Host ""

# Load IP from config
$configFile = "WIFI_ADB_CONFIG.txt"
if (Test-Path $configFile) {
    $lines = Get-Content $configFile
    foreach ($line in $lines) {
        if ($line -match "PHONE_IP=(.+)") {
            $PhoneIP = $matches[1]
            break
        }
    }
}

if ([string]::IsNullOrWhiteSpace($PhoneIP)) {
    $PhoneIP = "192.168.0.103"  # Default
}

$target = "$PhoneIP`:5555"
Write-Host "[INFO] Target Phone: $target" -ForegroundColor Yellow
Write-Host ""

# Check connection
$devices = adb devices | Select-String $PhoneIP
if (-not $devices) {
    Write-Host "[ERROR] Phone not connected!" -ForegroundColor Red
    Write-Host "Run: adb connect $target" -ForegroundColor Yellow
    exit
}

Write-Host "[1] USB Bus Devices List:" -ForegroundColor Green
Write-Host "    /dev/bus/usb/001/" -ForegroundColor Gray
Write-Host ""
adb -s $target shell "ls -la /dev/bus/usb/001/"
Write-Host ""

Write-Host "[2] Launching app to scan USB devices..." -ForegroundColor Green
adb -s $target shell am start -n com.capstone.cattleweight/.MainActivityNew > $null 2>&1
Start-Sleep -Seconds 3

Write-Host "[3] Reading USB device info from app log..." -ForegroundColor Green
Write-Host ""

# Get recent USB device logs
$logs = adb -s $target logcat -d -s UvcCameraManager:I | Select-String "USB DEVICES LIST|Device Name:|Product Name:|Manufacturer:|Vendor ID:|Product ID:|Device Class:|Interface Count:|Total USB devices" | Select-Object -Last 30

if ($logs) {
    Write-Host "===============================================" -ForegroundColor Cyan
    Write-Host "   DETECTED USB DEVICES" -ForegroundColor Cyan
    Write-Host "===============================================" -ForegroundColor Cyan
    Write-Host ""
    
    foreach ($log in $logs) {
        $line = $log.Line
        if ($line -match "Total USB devices connected: (\d+)") {
            Write-Host "Total Devices: $($matches[1])" -ForegroundColor Yellow
            Write-Host ""
        }
        elseif ($line -match "Device Name: (.+)") {
            Write-Host "  Device Path: $($matches[1])" -ForegroundColor White
        }
        elseif ($line -match "Product Name: (.+)") {
            Write-Host "  Product    : $($matches[1])" -ForegroundColor Cyan
        }
        elseif ($line -match "Manufacturer: (.+)") {
            Write-Host "  Maker      : $($matches[1])" -ForegroundColor Cyan
        }
        elseif ($line -match "Vendor ID: (\d+)") {
            $vid = $matches[1]
            $vidHex = "0x" + [Convert]::ToString([int]$vid, 16).ToUpper()
            Write-Host "  Vendor ID  : $vid ($vidHex)" -ForegroundColor Gray
        }
        elseif ($line -match "Product ID: (\d+)") {
            $pid = $matches[1]
            $pidHex = "0x" + [Convert]::ToString([int]$pid, 16).ToUpper()
            Write-Host "  Product ID : $pid ($pidHex)" -ForegroundColor Gray
        }
        elseif ($line -match "Device Class: (\d+)") {
            $class = $matches[1]
            $className = switch ($class) {
                "0"   { "Per Interface" }
                "1"   { "Audio" }
                "2"   { "Communication" }
                "3"   { "HID" }
                "9"   { "Hub" }
                "255" { "Vendor Specific (Serial)" }
                default { "Class $class" }
            }
            Write-Host "  Class      : $class ($className)" -ForegroundColor Gray
        }
        elseif ($line -match "Interface Count: (\d+)") {
            Write-Host "  Interfaces : $($matches[1])" -ForegroundColor Gray
            Write-Host ""
        }
    }
} else {
    Write-Host "[WARNING] No device info found in logs" -ForegroundColor Yellow
    Write-Host "Try closing and reopening the app" -ForegroundColor Gray
}

Write-Host "===============================================" -ForegroundColor Cyan
Write-Host "   DEVICE IDENTIFICATION GUIDE" -ForegroundColor Cyan
Write-Host "===============================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Common Devices:" -ForegroundColor Yellow
Write-Host "  Vendor 6790 (0x1A86)  = CH340 USB Serial (GroundChat/LiDAR)" -ForegroundColor White
Write-Host "  Vendor 3585 (0x0E01)  = Generic USB Hub" -ForegroundColor White
Write-Host "  Class 255             = Vendor Specific (Usually Serial)" -ForegroundColor White
Write-Host "  Class 9               = USB Hub" -ForegroundColor White
Write-Host "  Class 14              = Video Camera (UVC)" -ForegroundColor White
Write-Host ""

Write-Host "===============================================" -ForegroundColor Green
Write-Host "   To see live logs:" -ForegroundColor Green
Write-Host "   adb -s $target logcat -s UvcCameraManager" -ForegroundColor White
Write-Host "===============================================" -ForegroundColor Green
Write-Host ""

Read-Host "Press ENTER to exit"
