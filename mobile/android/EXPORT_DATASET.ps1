# Script Export Dataset Cattle Weight Detector
# Untuk Tim ML - Export database SQLite dan foto dari HP Android

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  CATTLE DATASET EXPORT TOOL" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Check ADB
Write-Host "[1/5] Checking ADB connection..." -ForegroundColor Yellow
$adbCheck = adb devices
if ($adbCheck -match "device$") {
    Write-Host "  ✅ Device connected" -ForegroundColor Green
} else {
    Write-Host "  ❌ No device found! Please connect your phone." -ForegroundColor Red
    exit 1
}

# Create output directory
Write-Host ""
Write-Host "[2/5] Creating output directory..." -ForegroundColor Yellow
$exportDir = ".\cattle_dataset_export_$(Get-Date -Format 'yyyyMMdd_HHmmss')"
New-Item -ItemType Directory -Path $exportDir -Force | Out-Null
Write-Host "  ✅ Created: $exportDir" -ForegroundColor Green

# Export database
Write-Host ""
Write-Host "[3/5] Exporting SQLite database..." -ForegroundColor Yellow
try {
    # Try direct pull first
    $dbPath = "/data/data/com.capstone.cattleweight/databases/cattle_dataset.db"
    adb pull $dbPath "$exportDir\cattle_dataset.db" 2>&1 | Out-Null
    
    if (Test-Path "$exportDir\cattle_dataset.db") {
        Write-Host "  ✅ Database exported successfully" -ForegroundColor Green
    } else {
        # Fallback: copy to sdcard first
        Write-Host "  ⚠️  Direct pull failed, trying alternative method..." -ForegroundColor Yellow
        adb shell "run-as com.capstone.cattleweight cp databases/cattle_dataset.db /sdcard/cattle_db_temp.db" 2>&1 | Out-Null
        adb pull /sdcard/cattle_db_temp.db "$exportDir\cattle_dataset.db" 2>&1 | Out-Null
        adb shell "rm /sdcard/cattle_db_temp.db" 2>&1 | Out-Null
        
        if (Test-Path "$exportDir\cattle_dataset.db") {
            Write-Host "  ✅ Database exported via sdcard" -ForegroundColor Green
        } else {
            Write-Host "  ❌ Database export failed" -ForegroundColor Red
        }
    }
} catch {
    Write-Host "  ❌ Error: $_" -ForegroundColor Red
}

# Export images
Write-Host ""
Write-Host "[4/5] Exporting cattle images..." -ForegroundColor Yellow
$imagesDir = "$exportDir\images"
New-Item -ItemType Directory -Path $imagesDir -Force | Out-Null

try {
    adb pull /storage/emulated/0/Pictures/CattleDataset $imagesDir 2>&1 | Out-Null
    
    $imageCount = (Get-ChildItem -Path $imagesDir -Filter "*.jpg" -Recurse).Count
    if ($imageCount -gt 0) {
        Write-Host "  ✅ Exported $imageCount images" -ForegroundColor Green
    } else {
        Write-Host "  ⚠️  No images found in /Pictures/CattleDataset" -ForegroundColor Yellow
    }
} catch {
    Write-Host "  ❌ Error: $_" -ForegroundColor Red
}

# Generate CSV from database
Write-Host ""
Write-Host "[5/5] Converting database to CSV..." -ForegroundColor Yellow

if (Test-Path "$exportDir\cattle_dataset.db") {
    # Check if sqlite3 is available
    $sqliteAvailable = Get-Command sqlite3 -ErrorAction SilentlyContinue
    
    if ($sqliteAvailable) {
        $query = "SELECT * FROM dataset;"
        $csvPath = "$exportDir\cattle_dataset.csv"
        sqlite3 -header -csv "$exportDir\cattle_dataset.db" $query > $csvPath
        Write-Host "  ✅ CSV exported: cattle_dataset.csv" -ForegroundColor Green
    } else {
        Write-Host "  ⚠️  SQLite3 not found. Creating Python export script..." -ForegroundColor Yellow
        
        $pythonScript = @"
import sqlite3
import pandas as pd
import os

db_path = 'cattle_dataset.db'
csv_path = 'cattle_dataset.csv'

try:
    conn = sqlite3.connect(db_path)
    df = pd.read_sql_query("SELECT * FROM dataset", conn)
    df.to_csv(csv_path, index=False)
    conn.close()
    
    print(f"✅ CSV exported: {csv_path}")
    print(f"📊 Total records: {len(df)}")
    print("\nDataset Statistics:")
    print(df.describe())
    
except Exception as e:
    print(f"❌ Error: {e}")
    print("\nManual export using DB Browser for SQLite:")
    print("1. Download: https://sqlitebrowser.org/")
    print("2. Open cattle_dataset.db")
    print("3. File → Export → Table as CSV")
"@
        
        $pythonScript | Out-File -FilePath "$exportDir\export_to_csv.py" -Encoding UTF8
        Write-Host "  ✅ Python script created: export_to_csv.py" -ForegroundColor Green
        Write-Host "     Run: cd $exportDir; python export_to_csv.py" -ForegroundColor Cyan
    }
} else {
    Write-Host "  ❌ Database not found, skipping CSV conversion" -ForegroundColor Red
}

# Summary
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  EXPORT SUMMARY" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

if (Test-Path "$exportDir\cattle_dataset.db") {
    $dbSize = (Get-Item "$exportDir\cattle_dataset.db").Length / 1KB
    Write-Host "📁 Database: $('{0:N2}' -f $dbSize) KB" -ForegroundColor Green
}

if (Test-Path $imagesDir) {
    $totalImages = (Get-ChildItem -Path $imagesDir -Filter "*.jpg" -Recurse).Count
    $totalImageSize = (Get-ChildItem -Path $imagesDir -Filter "*.jpg" -Recurse | Measure-Object -Property Length -Sum).Sum / 1MB
    Write-Host "📸 Images: $totalImages files, $('{0:N2}' -f $totalImageSize) MB" -ForegroundColor Green
}

if (Test-Path "$exportDir\cattle_dataset.csv") {
    $csvLines = (Get-Content "$exportDir\cattle_dataset.csv").Count - 1
    Write-Host "📊 CSV: $csvLines records" -ForegroundColor Green
}

Write-Host ""
Write-Host "📂 Export location: $exportDir" -ForegroundColor Cyan
Write-Host ""
Write-Host "✅ Export completed!" -ForegroundColor Green
Write-Host ""

# Open folder
$openFolder = Read-Host "Open export folder? (Y/N)"
if ($openFolder -eq "Y" -or $openFolder -eq "y") {
    Invoke-Item $exportDir
}
