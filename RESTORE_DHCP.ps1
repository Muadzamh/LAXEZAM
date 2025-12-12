# Script untuk Kembalikan ke DHCP (IP Otomatis)
# Jalankan sebagai Administrator

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  RESTORE DHCP - Back to Auto IP" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Cek admin privileges
$isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole] "Administrator")

if (-not $isAdmin) {
    Write-Host "❌ Script ini harus dijalankan sebagai Administrator!" -ForegroundColor Red
    Write-Host ""
    Read-Host "Press Enter to exit"
    exit 1
}

# Dapatkan WiFi adapter
$adapter = Get-NetAdapter | Where-Object {$_.Status -eq "Up" -and $_.Name -like "*Wi-Fi*"}

if (-not $adapter) {
    Write-Host "❌ WiFi adapter tidak ditemukan!" -ForegroundColor Red
    exit 1
}

$adapterName = $adapter.Name
$adapterIndex = $adapter.InterfaceIndex

Write-Host "Adapter: $adapterName" -ForegroundColor White
Write-Host ""

# Current IP
$currentIP = (Get-NetIPAddress -InterfaceIndex $adapterIndex -AddressFamily IPv4 -ErrorAction SilentlyContinue).IPAddress
Write-Host "IP sekarang: $currentIP" -ForegroundColor Yellow
Write-Host ""

Write-Host "Mengaktifkan DHCP..." -ForegroundColor Yellow

try {
    # Enable DHCP
    Set-NetIPInterface -InterfaceIndex $adapterIndex -Dhcp Enabled
    Set-DnsClientServerAddress -InterfaceIndex $adapterIndex -ResetServerAddresses
    
    # Restart adapter
    Restart-NetAdapter -Name $adapterName
    
    Start-Sleep -Seconds 3
    
    # Get new IP
    $newIP = (Get-NetIPAddress -InterfaceIndex $adapterIndex -AddressFamily IPv4 -ErrorAction SilentlyContinue).IPAddress
    
    Write-Host ""
    Write-Host "✅ DHCP diaktifkan!" -ForegroundColor Green
    Write-Host "   IP baru: $newIP" -ForegroundColor White
    Write-Host ""
    
} catch {
    Write-Host "❌ ERROR: $_" -ForegroundColor Red
}

Read-Host "Press Enter to exit"
