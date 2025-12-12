# Script untuk Set Static IP 192.168.1.100
# Jalankan sebagai Administrator

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  SET STATIC IP - Cattle Weight Detector" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Cek admin privileges
$isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole] "Administrator")

if (-not $isAdmin) {
    Write-Host "ERROR: Script ini harus dijalankan sebagai Administrator!" -ForegroundColor Red
    Write-Host ""
    Write-Host "Cara menjalankan:" -ForegroundColor Yellow
    Write-Host "1. Klik kanan PowerShell" -ForegroundColor White
    Write-Host "2. Pilih 'Run as Administrator'" -ForegroundColor White
    Write-Host "3. Jalankan script ini lagi" -ForegroundColor White
    Write-Host ""
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host "OK: Running as Administrator" -ForegroundColor Green
Write-Host ""

# Konfigurasi
$newIP = "192.168.1.100"
$gateway = "192.168.1.1"
$dns1 = "8.8.8.8"
$dns2 = "8.8.4.4"
$prefixLength = 24

# Dapatkan WiFi adapter
Write-Host "[1/4] Mencari WiFi adapter..." -ForegroundColor Yellow
$adapter = Get-NetAdapter | Where-Object {$_.Status -eq "Up" -and $_.Name -like "*Wi-Fi*"}

if (-not $adapter) {
    Write-Host "ERROR: WiFi adapter tidak ditemukan!" -ForegroundColor Red
    exit 1
}

$adapterName = $adapter.Name
$adapterIndex = $adapter.InterfaceIndex
Write-Host "  OK: Ditemukan: $adapterName (Index: $adapterIndex)" -ForegroundColor Green
Write-Host ""

# Backup konfigurasi lama
Write-Host "[2/4] Backup konfigurasi lama..." -ForegroundColor Yellow
$oldIP = (Get-NetIPAddress -InterfaceIndex $adapterIndex -AddressFamily IPv4 -ErrorAction SilentlyContinue).IPAddress
$oldGateway = (Get-NetRoute -InterfaceIndex $adapterIndex -DestinationPrefix "0.0.0.0/0" -ErrorAction SilentlyContinue).NextHop
Write-Host "  IP lama: $oldIP" -ForegroundColor White
Write-Host "  Gateway lama: $oldGateway" -ForegroundColor White
Write-Host ""

# Hapus IP lama
Write-Host "[3/4] Menghapus konfigurasi DHCP..." -ForegroundColor Yellow
try {
    Remove-NetIPAddress -InterfaceIndex $adapterIndex -AddressFamily IPv4 -Confirm:$false -ErrorAction SilentlyContinue
    Remove-NetRoute -InterfaceIndex $adapterIndex -DestinationPrefix "0.0.0.0/0" -Confirm:$false -ErrorAction SilentlyContinue
    Write-Host "  OK: Konfigurasi lama dihapus" -ForegroundColor Green
} catch {
    Write-Host "  Warning: $_" -ForegroundColor Yellow
}
Write-Host ""

# Set IP statik
Write-Host "[4/4] Mengatur IP statik..." -ForegroundColor Yellow
try {
    # Set IP Address
    New-NetIPAddress -InterfaceIndex $adapterIndex -IPAddress $newIP -PrefixLength $prefixLength -DefaultGateway $gateway -ErrorAction Stop | Out-Null
    
    Write-Host "  OK: IP Address: $newIP" -ForegroundColor Green
    
    # Set DNS
    Set-DnsClientServerAddress -InterfaceIndex $adapterIndex -ServerAddresses ($dns1, $dns2) -ErrorAction Stop
    
    Write-Host "  OK: DNS: $dns1, $dns2" -ForegroundColor Green
    Write-Host ""
    
    # Verifikasi
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host "  KONFIGURASI BERHASIL!" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Konfigurasi Jaringan Baru:" -ForegroundColor Cyan
    Write-Host "   IP Address  : $newIP" -ForegroundColor White
    Write-Host "   Subnet Mask : 255.255.255.0" -ForegroundColor White
    Write-Host "   Gateway     : $gateway" -ForegroundColor White
    Write-Host "   DNS 1       : $dns1" -ForegroundColor White
    Write-Host "   DNS 2       : $dns2" -ForegroundColor White
    Write-Host ""
    
    # Test koneksi
    Write-Host "Testing koneksi..." -ForegroundColor Yellow
    $pingResult = Test-Connection -ComputerName $gateway -Count 2 -Quiet
    
    if ($pingResult) {
        Write-Host "   OK: Koneksi ke router berhasil!" -ForegroundColor Green
    } else {
        Write-Host "   Warning: Tidak bisa ping router" -ForegroundColor Yellow
    }
    
    Write-Host ""
    Write-Host "LANGKAH SELANJUTNYA:" -ForegroundColor Cyan
    Write-Host "   1. Update code aplikasi Android dengan IP baru" -ForegroundColor White
    Write-Host "   2. SERVER_URL = 'http://192.168.1.100:5000'" -ForegroundColor Yellow
    Write-Host "   3. Rebuild dan install APK" -ForegroundColor White
    Write-Host ""
    
} catch {
    Write-Host ""
    Write-Host "ERROR: $_" -ForegroundColor Red
    Write-Host ""
    Write-Host "Kembalikan ke DHCP? (Y/N)" -ForegroundColor Yellow
    $restore = Read-Host
    
    if ($restore -eq "Y" -or $restore -eq "y") {
        Set-NetIPInterface -InterfaceIndex $adapterIndex -Dhcp Enabled
        Set-DnsClientServerAddress -InterfaceIndex $adapterIndex -ResetServerAddresses
        Write-Host "OK: Dikembalikan ke DHCP" -ForegroundColor Green
    }
}

Write-Host ""
Read-Host "Press Enter to exit"
