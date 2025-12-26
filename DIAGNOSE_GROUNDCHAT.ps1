# ========================================
# GROUNDCHAT CAMERA DIAGNOSTIC & FIX
# ========================================
# Script untuk diagnosa dan perbaikan camera gelap/hijau
# ========================================

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "  GROUNDCHAT CAMERA DIAGNOSTIC TOOL" -ForegroundColor Yellow
Write-Host "========================================`n" -ForegroundColor Cyan

# Function to test if FFmpeg is available
function Test-FFmpeg {
    try {
        $null = ffmpeg -version 2>&1
        return $true
    } catch {
        return $false
    }
}

# Step 1: Hardware Reset Instruction
Write-Host "🔧 METODE 1: HARDWARE RESET (RECOMMENDED)" -ForegroundColor Yellow
Write-Host "=========================================`n" -ForegroundColor Yellow

Write-Host "Instruksi:" -ForegroundColor Cyan
Write-Host "1. CABUT kabel USB GroundChat dari laptop" -ForegroundColor White
Write-Host "2. TUNGGU 15-20 detik (penting!)" -ForegroundColor White
Write-Host "3. COLOK kembali ke USB port yang SAMA" -ForegroundColor White
Write-Host "4. TUNGGU 5 detik sampai driver terdeteksi`n" -ForegroundColor White

$continue = Read-Host "Apakah sudah melakukan hardware reset di atas? (y/n)"

if ($continue -ne 'y' -and $continue -ne 'Y') {
    Write-Host "`n⚠️  Silakan lakukan hardware reset dulu!" -ForegroundColor Yellow
    Write-Host "Press any key to exit..."
    $null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
    exit 0
}

# Step 2: List cameras
Write-Host "`n🔍 Mencari GroundChat camera..." -ForegroundColor Yellow

$cameras = Get-PnpDevice | Where-Object {
    $_.Class -eq "Camera" -and $_.Status -eq "OK"
}

if ($cameras.Count -eq 0) {
    Write-Host "❌ Tidak ada camera terdeteksi!" -ForegroundColor Red
    Write-Host "   Pastikan GroundChat sudah tercolok`n" -ForegroundColor Yellow
    exit 1
}

Write-Host "`n📷 Camera terdeteksi:" -ForegroundColor Green
$cameras | ForEach-Object { Write-Host "   - $($_.FriendlyName)" -ForegroundColor White }

# Step 3: Check FFmpeg
Write-Host "`n🔧 METODE 2: FFMPEG CAMERA TEST" -ForegroundColor Yellow
Write-Host "================================`n" -ForegroundColor Yellow

if (Test-FFmpeg) {
    Write-Host "✅ FFmpeg terdeteksi!" -ForegroundColor Green
    
    Write-Host "`n📋 Listing video devices dengan FFmpeg..." -ForegroundColor Cyan
    Write-Host "(Cari nama GroundChat di list ini)`n" -ForegroundColor Gray
    
    ffmpeg -list_devices true -f dshow -i dummy 2>&1 | Select-String "video"
    
    Write-Host "`n💡 Untuk test camera dengan FFmpeg:" -ForegroundColor Yellow
    Write-Host "ffmpeg -f dshow -i video=`"NAMA_CAMERA`" -t 1 -f null -`n" -ForegroundColor Gray
    
} else {
    Write-Host "⚠️  FFmpeg tidak terinstall" -ForegroundColor Yellow
    Write-Host "   Install via: choco install ffmpeg" -ForegroundColor Gray
    Write-Host "   Atau skip metode ini`n" -ForegroundColor Gray
}

# Step 4: Open Camera app
Write-Host "`n🎥 METODE 3: TEST DI WINDOWS CAMERA APP" -ForegroundColor Yellow
Write-Host "========================================`n" -ForegroundColor Yellow

$openCamera = Read-Host "Buka Windows Camera app untuk test? (y/n)"

if ($openCamera -eq 'y' -or $openCamera -eq 'Y') {
    Write-Host "`n🎥 Membuka Windows Camera..." -ForegroundColor Cyan
    Start-Process "microsoft.windows.camera:"
    
    Write-Host "`n📋 CARA TEST DI CAMERA APP:" -ForegroundColor Yellow
    Write-Host "1. Klik icon gear (⚙️) di pojok kanan atas" -ForegroundColor White
    Write-Host "2. Pilih camera: GroundChat / USB Camera" -ForegroundColor White
    Write-Host "3. Arahkan ke CAHAYA TERANG (lampu/jendela)" -ForegroundColor White
    Write-Host "4. Tunggu 10 detik untuk auto-exposure adjust" -ForegroundColor White
    Write-Host "5. Cek hasil:`n" -ForegroundColor White
    Write-Host "   ✅ Terang & warna normal = SUKSES!" -ForegroundColor Green
    Write-Host "   ❌ Masih gelap/hijau = CAMERA RUSAK`n" -ForegroundColor Red
}

# Step 5: Diagnostic result
Write-Host "`n📊 HASIL DIAGNOSTIC:" -ForegroundColor Yellow
$result = Read-Host "Apakah camera sudah normal? (y/n)"

if ($result -eq 'y' -or $result -eq 'Y') {
    Write-Host "`n✅ CAMERA SUDAH NORMAL!" -ForegroundColor Green
    Write-Host "   Sekarang bisa test di Android app!`n" -ForegroundColor Cyan
} else {
    Write-Host "`n⚠️  TROUBLESHOOTING LANJUTAN:" -ForegroundColor Yellow
    Write-Host "========================================`n" -ForegroundColor Yellow
    
    Write-Host "1. MASIH GELAP:" -ForegroundColor Red
    Write-Host "   - Pastikan ada cahaya SANGAT TERANG di depan camera" -ForegroundColor White
    Write-Host "   - Tunggu minimal 15 detik di Camera app" -ForegroundColor White
    Write-Host "   - Coba restart laptop`n" -ForegroundColor White
    
    Write-Host "2. MASIH HIJAU:" -ForegroundColor Red
    Write-Host "   - Ini masalah color format (YUYV vs MJPEG)" -ForegroundColor White
    Write-Host "   - Update driver camera dari Device Manager" -ForegroundColor White
    Write-Host "   - Atau camera mungkin rusak`n" -ForegroundColor White
    
    Write-Host "3. CAMERA MUNGKIN RUSAK jika:" -ForegroundColor Red
    Write-Host "   - Sudah coba semua metode di atas" -ForegroundColor White
    Write-Host "   - Masih gelap/hijau di semua aplikasi" -ForegroundColor White
    Write-Host "   - Masih gelap/hijau di laptop berbeda`n" -ForegroundColor White
    
    $resetRegistry = Read-Host "Reset Windows Camera registry? (y/n)"
    if ($resetRegistry -eq 'y' -or $resetRegistry -eq 'Y') {
        Write-Host "`n⚠️  Mereset camera registry..." -ForegroundColor Yellow
        try {
            reg delete "HKCU\Software\Microsoft\Windows\CurrentVersion\Camera" /f 2>&1 | Out-Null
            Write-Host "✅ Registry direset. Restart laptop untuk apply changes`n" -ForegroundColor Green
        } catch {
            Write-Host "❌ Gagal reset registry. Coba jalankan sebagai Administrator`n" -ForegroundColor Red
        }
    }
}

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "         DIAGNOSTIC SELESAI" -ForegroundColor Yellow
Write-Host "========================================`n" -ForegroundColor Cyan
