# ========================================
# RESET GROUNDCHAT CAMERA SCRIPT
# ========================================
# Script untuk reset USB camera yang gelap/hijau
# Author: Capstone Project
# Date: 2025-12-24
# ========================================

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "   GROUNDCHAT CAMERA RESET TOOL" -ForegroundColor Yellow
Write-Host "========================================`n" -ForegroundColor Cyan

# Step 1: List all USB Camera devices
Write-Host "[1] Mencari USB Camera devices..." -ForegroundColor Yellow

$cameras = Get-PnpDevice | Where-Object {
    ($_.Class -eq "Camera" -or $_.Class -eq "Image") -and 
    $_.Status -eq "OK"
}

if ($cameras.Count -eq 0) {
    Write-Host "❌ Tidak ada camera terdeteksi!" -ForegroundColor Red
    Write-Host "   Pastikan GroundChat sudah tercolok ke USB laptop`n" -ForegroundColor Yellow
    exit 1
}

Write-Host "`n📷 Camera yang terdeteksi:" -ForegroundColor Green
$cameras | Format-Table -Property FriendlyName, InstanceId, Status -AutoSize

# Ask user to select camera
Write-Host "`n[2] Pilih camera yang akan di-reset:" -ForegroundColor Yellow
for ($i = 0; $i -lt $cameras.Count; $i++) {
    Write-Host "   [$i] $($cameras[$i].FriendlyName)" -ForegroundColor White
}

$selection = Read-Host "`nMasukkan nomor (0-$($cameras.Count - 1))"

if ($selection -match '^\d+$' -and [int]$selection -ge 0 -and [int]$selection -lt $cameras.Count) {
    $selectedCamera = $cameras[[int]$selection]
    
    Write-Host "`n[3] Reset camera: $($selectedCamera.FriendlyName)" -ForegroundColor Yellow
    Write-Host "   Device ID: $($selectedCamera.InstanceId)" -ForegroundColor Gray
    
    # Disable device
    Write-Host "`n⏳ Menonaktifkan device..." -ForegroundColor Yellow
    try {
        Disable-PnpDevice -InstanceId $selectedCamera.InstanceId -Confirm:$false -ErrorAction Stop
        Write-Host "✅ Device dinonaktifkan" -ForegroundColor Green
    } catch {
        Write-Host "❌ Gagal menonaktifkan device: $($_.Exception.Message)" -ForegroundColor Red
        Write-Host "⚠️  Coba jalankan PowerShell sebagai Administrator" -ForegroundColor Yellow
        exit 1
    }
    
    # Wait 5 seconds
    Write-Host "`n⏱️  Menunggu 5 detik..." -ForegroundColor Yellow
    for ($i = 5; $i -gt 0; $i--) {
        Write-Host "   $i..." -NoNewline
        Start-Sleep -Seconds 1
    }
    Write-Host ""
    
    # Enable device
    Write-Host "`n⚡ Mengaktifkan kembali device..." -ForegroundColor Yellow
    try {
        Enable-PnpDevice -InstanceId $selectedCamera.InstanceId -Confirm:$false -ErrorAction Stop
        Write-Host "✅ Device diaktifkan kembali" -ForegroundColor Green
    } catch {
        Write-Host "❌ Gagal mengaktifkan device: $($_.Exception.Message)" -ForegroundColor Red
        exit 1
    }
    
    # Wait for driver to load
    Write-Host "`n⏱️  Menunggu driver load (3 detik)..." -ForegroundColor Yellow
    Start-Sleep -Seconds 3
    
    Write-Host "`n========================================" -ForegroundColor Cyan
    Write-Host "✅ RESET SELESAI!" -ForegroundColor Green
    Write-Host "========================================`n" -ForegroundColor Cyan
    
    Write-Host "📋 LANGKAH SELANJUTNYA:" -ForegroundColor Yellow
    Write-Host "1. Buka aplikasi Camera di laptop (Windows Camera)" -ForegroundColor White
    Write-Host "2. Switch ke GroundChat camera" -ForegroundColor White
    Write-Host "3. Arahkan camera ke cahaya terang" -ForegroundColor White
    Write-Host "4. Tunggu 5-10 detik untuk auto-exposure menyesuaikan" -ForegroundColor White
    Write-Host "5. Cek apakah masih gelap/hijau`n" -ForegroundColor White
    
    # Offer to open Camera app
    $openCamera = Read-Host "Buka Windows Camera app sekarang? (y/n)"
    if ($openCamera -eq 'y' -or $openCamera -eq 'Y') {
        Write-Host "`n🎥 Membuka Windows Camera..." -ForegroundColor Cyan
        Start-Process "microsoft.windows.camera:"
    }
    
} else {
    Write-Host "`n❌ Pilihan tidak valid!" -ForegroundColor Red
    exit 1
}

Write-Host "`n✅ Script selesai.`n" -ForegroundColor Green
