# GROUNDCHAT CAMERA RESET - SIMPLE VERSION
# ==========================================

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "  GROUNDCHAT CAMERA RESET GUIDE" -ForegroundColor Yellow
Write-Host "========================================`n" -ForegroundColor Cyan

# STEP 1: Hardware Reset
Write-Host "LANGKAH 1: HARDWARE RESET" -ForegroundColor Yellow
Write-Host "==========================`n" -ForegroundColor Yellow

Write-Host "Instruksi:" -ForegroundColor White
Write-Host "1. CABUT kabel USB GroundChat dari laptop SEKARANG" -ForegroundColor Cyan
Write-Host "2. Tunggu 15-20 detik (jangan langsung colok lagi!)" -ForegroundColor Cyan
Write-Host "3. COLOK kembali ke USB port" -ForegroundColor Cyan
Write-Host "4. Tunggu 5 detik sampai terdeteksi`n" -ForegroundColor Cyan

Read-Host "Tekan ENTER setelah selesai melakukan langkah di atas"

# STEP 2: List cameras
Write-Host "`nLANGKAH 2: CEK CAMERA TERDETEKSI" -ForegroundColor Yellow
Write-Host "==================================`n" -ForegroundColor Yellow

$cameras = Get-PnpDevice | Where-Object {$_.Class -eq "Camera" -and $_.Status -eq "OK"}

if ($cameras.Count -eq 0) {
    Write-Host "ERROR: Tidak ada camera terdeteksi!`n" -ForegroundColor Red
    exit 1
}

Write-Host "Camera yang terdeteksi:" -ForegroundColor Green
$cameras | ForEach-Object { Write-Host "  - $($_.FriendlyName)" -ForegroundColor White }

# STEP 3: Open Camera app
Write-Host "`nLANGKAH 3: TEST DI WINDOWS CAMERA" -ForegroundColor Yellow
Write-Host "==================================`n" -ForegroundColor Yellow

Write-Host "Akan membuka Windows Camera app untuk test...`n" -ForegroundColor Cyan
Read-Host "Tekan ENTER untuk membuka Camera app"

Start-Process "microsoft.windows.camera:"

Write-Host "`nCara test di Camera app:" -ForegroundColor Yellow
Write-Host "1. Klik icon gear (settings) di pojok kanan atas" -ForegroundColor White
Write-Host "2. Pilih camera: GroundChat atau USB Camera" -ForegroundColor White
Write-Host "3. Arahkan camera ke CAHAYA TERANG (lampu/jendela)" -ForegroundColor White
Write-Host "4. Tunggu 10-15 detik untuk auto-exposure menyesuaikan`n" -ForegroundColor White

# STEP 4: Result
Write-Host "LANGKAH 4: CEK HASIL" -ForegroundColor Yellow
Write-Host "====================`n" -ForegroundColor Yellow

$result = Read-Host "Apakah camera sudah normal (terang & warna OK)? (y/n)"

if ($result -eq 'y') {
    Write-Host "`n================================" -ForegroundColor Green
    Write-Host "  CAMERA SUDAH NORMAL!" -ForegroundColor Green
    Write-Host "================================" -ForegroundColor Green
    Write-Host "`nSekarang bisa test di Android app!`n" -ForegroundColor Cyan
} else {
    Write-Host "`n================================" -ForegroundColor Red
    Write-Host "  CAMERA MASIH BERMASALAH" -ForegroundColor Red
    Write-Host "================================`n" -ForegroundColor Red
    
    Write-Host "Kemungkinan penyebab:" -ForegroundColor Yellow
    Write-Host "1. Camera rusak (sensor bermasalah)" -ForegroundColor White
    Write-Host "2. Cahaya kurang terang" -ForegroundColor White
    Write-Host "3. Driver camera bermasalah`n" -ForegroundColor White
    
    Write-Host "Coba lagi:" -ForegroundColor Yellow
    Write-Host "- Restart laptop" -ForegroundColor White
    Write-Host "- Test di laptop lain" -ForegroundColor White
    Write-Host "- Coba USB port lain`n" -ForegroundColor White
}

Write-Host "Script selesai.`n" -ForegroundColor Cyan
