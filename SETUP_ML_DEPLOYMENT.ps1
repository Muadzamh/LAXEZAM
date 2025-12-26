#!/usr/bin/env pwsh
# ============================================
# Setup Script untuk ML Model Deployment
# ============================================

Write-Host "=" -NoNewline -ForegroundColor Cyan
Write-Host ("=" * 58) -ForegroundColor Cyan
Write-Host "  SETUP ML MODEL UNTUK ANDROID DEPLOYMENT" -ForegroundColor Green
Write-Host "=" -NoNewline -ForegroundColor Cyan
Write-Host ("=" * 58) -ForegroundColor Cyan
Write-Host ""

$ML_MODEL_DIR = "ml_model"
$ANDROID_ASSETS = "mobile\android\CattleWeightDetector\app\src\main\assets"

# ============================================
# STEP 1: Check Dependencies
# ============================================
Write-Host "[1/5] Checking Python Dependencies..." -ForegroundColor Yellow

$required = @("torch", "torchvision", "ultralytics", "tensorflow", "onnx")
$missing = @()

foreach ($pkg in $required) {
    $check = python -c "import $pkg" 2>&1
    if ($LASTEXITCODE -ne 0) {
        $missing += $pkg
    }
}

if ($missing.Count -gt 0) {
    Write-Host "   Missing packages: $($missing -join ', ')" -ForegroundColor Red
    Write-Host "   Installing..." -ForegroundColor Yellow
    
    $packages = "torch torchvision ultralytics tensorflow onnx onnx-tf opencv-python"
    python -m pip install $packages
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "   ✓ Dependencies installed" -ForegroundColor Green
    } else {
        Write-Host "   ✗ Installation failed" -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "   ✓ All dependencies satisfied" -ForegroundColor Green
}

# ============================================
# STEP 2: Check Model Files
# ============================================
Write-Host "`n[2/5] Checking Model Files..." -ForegroundColor Yellow

$BBOX_MODEL = Join-Path $ML_MODEL_DIR "bbox_weight_model.pt"

if (Test-Path $BBOX_MODEL) {
    $size = (Get-Item $BBOX_MODEL).Length / 1MB
    Write-Host "   ✓ Found bbox_weight_model.pt ($([math]::Round($size, 2)) MB)" -ForegroundColor Green
} else {
    Write-Host "   ✗ bbox_weight_model.pt NOT FOUND" -ForegroundColor Red
    Write-Host "   Please copy your trained model to: $BBOX_MODEL" -ForegroundColor Yellow
    exit 1
}

# ============================================
# STEP 3: Convert Models to TFLite
# ============================================
Write-Host "`n[3/5] Converting Models to TensorFlow Lite..." -ForegroundColor Yellow

# Convert Weight Prediction Model
Write-Host "   Converting bbox_weight_model..." -ForegroundColor Cyan
Push-Location $ML_MODEL_DIR

python convert_to_tflite.py
if ($LASTEXITCODE -eq 0) {
    Write-Host "   ✓ Weight model converted" -ForegroundColor Green
} else {
    Write-Host "   ✗ Conversion failed" -ForegroundColor Red
    Pop-Location
    exit 1
}

# Convert YOLO Model
Write-Host "   Converting YOLO model..." -ForegroundColor Cyan
python convert_yolo_to_tflite.py
if ($LASTEXITCODE -eq 0) {
    Write-Host "   ✓ YOLO model converted" -ForegroundColor Green
} else {
    Write-Host "   ⚠ YOLO conversion warning (check logs)" -ForegroundColor Yellow
}

Pop-Location

# ============================================
# STEP 4: Copy to Android Assets
# ============================================
Write-Host "`n[4/5] Copying Models to Android Assets..." -ForegroundColor Yellow

# Create assets directory if not exists
if (!(Test-Path $ANDROID_ASSETS)) {
    New-Item -ItemType Directory -Path $ANDROID_ASSETS -Force | Out-Null
    Write-Host "   Created assets directory" -ForegroundColor Cyan
}

# Copy models
$models = @(
    "bbox_weight_model.tflite",
    "yolov8n_float32.tflite"
)

foreach ($model in $models) {
    $src = Join-Path $ML_MODEL_DIR $model
    $dst = Join-Path $ANDROID_ASSETS $model
    
    if (Test-Path $src) {
        Copy-Item $src $dst -Force
        $size = (Get-Item $dst).Length / 1MB
        Write-Host "   ✓ Copied $model ($([math]::Round($size, 2)) MB)" -ForegroundColor Green
    } else {
        Write-Host "   ✗ $model not found" -ForegroundColor Red
    }
}

# ============================================
# STEP 5: Verify Setup
# ============================================
Write-Host "`n[5/5] Verifying Setup..." -ForegroundColor Yellow

$allGood = $true

# Check assets
foreach ($model in $models) {
    $path = Join-Path $ANDROID_ASSETS $model
    if (!(Test-Path $path)) {
        Write-Host "   ✗ Missing: $model" -ForegroundColor Red
        $allGood = $false
    }
}

if ($allGood) {
    Write-Host "   ✓ All models in place" -ForegroundColor Green
}

# ============================================
# SUMMARY
# ============================================
Write-Host ""
Write-Host "=" -NoNewline -ForegroundColor Cyan
Write-Host ("=" * 58) -ForegroundColor Cyan
Write-Host "  SETUP COMPLETE!" -ForegroundColor Green
Write-Host "=" -NoNewline -ForegroundColor Cyan
Write-Host ("=" * 58) -ForegroundColor Cyan
Write-Host ""

if ($allGood) {
    Write-Host "✅ All steps completed successfully!" -ForegroundColor Green
    Write-Host ""
    Write-Host "NEXT STEPS:" -ForegroundColor Yellow
    Write-Host "1. Open Android Studio" -ForegroundColor White
    Write-Host "2. Open project: mobile/android/CattleWeightDetector" -ForegroundColor White
    Write-Host "3. Sync Gradle (should succeed)" -ForegroundColor White
    Write-Host "4. Build & Run on device" -ForegroundColor White
    Write-Host ""
    Write-Host "📖 Read IMPLEMENTATION_GUIDE.md for detailed instructions" -ForegroundColor Cyan
} else {
    Write-Host "⚠ Some steps failed. Check errors above." -ForegroundColor Yellow
    Write-Host "   See IMPLEMENTATION_GUIDE.md for troubleshooting" -ForegroundColor Cyan
}

Write-Host ""
Write-Host "=" -NoNewline -ForegroundColor Cyan
Write-Host ("=" * 58) -ForegroundColor Cyan
