# 📱 Cattle Weight Detector - Panduan Dataset

## ✨ Fitur Dataset Capture

Aplikasi ini memiliki 2 halaman:
1. **DETEKSI** - Halaman deteksi berat sapi secara real-time
2. **DATASET** - Halaman untuk mengambil foto sapi dan menyimpan data LiDAR

---

## 📸 Cara Mengambil Dataset

### 1. Buka Tab DATASET
- Di bagian bawah aplikasi, tap icon **DATASET**
- Kamera akan aktif dengan tampilan biru
- Data LiDAR (Jarak, Sinyal, Suhu) akan ditampilkan secara real-time

### 2. Arahkan Kamera ke Sapi
- Pastikan sapi terlihat jelas di layar kamera
- Tunggu hingga data LiDAR stabil dan akurat
- Pastikan jarak yang ditampilkan sesuai

### 3. Ambil Foto
- Tap tombol **CAPTURE** (tombol biru di kanan bawah)
- Status akan berubah:
  - 📸 **Capturing...** - Sedang mengambil foto
  - 💾 **Saving...** - Sedang menyimpan ke Gallery
  - ✅ **Saved!** - Berhasil disimpan

### 4. Lihat Jumlah Dataset
- Bagian bawah layar akan menampilkan: **📊 Total Data: X**
- Angka ini menunjukkan berapa banyak data yang sudah diambil

---

## 📂 Lokasi Penyimpanan

### Gambar (Gallery)
Semua foto sapi disimpan di:
```
Gallery HP → Pictures → CattleDataset
```

Cara membuka:
1. Buka aplikasi **Gallery** / **Photos** di HP
2. Cari album **CattleDataset**
3. Semua foto sapi tersimpan di sana

Format nama file:
```
cattle_20250121_143022.jpg
(cattle_YYYYMMDD_HHMMSS.jpg)
```

### Metadata (SQLite Database)
Data LiDAR dan informasi gambar disimpan di database internal:
- **Lokasi**: `/data/data/com.capstone.cattleweight/databases/cattle_dataset.db`
- **Tabel**: `dataset`
- **Kolom**:
  - `id` - ID unik
  - `image_path` - Lokasi gambar di Gallery
  - `distance_cm` - Jarak saat foto diambil (cm)
  - `signal_strength` - Kekuatan sinyal LiDAR
  - `temperature` - Suhu LiDAR (°C)
  - `timestamp` - Waktu pengambilan data

---

## 📊 Cara Export Dataset (untuk Tim ML)

### Menggunakan ADB (Android Debug Bridge)

1. **Hubungkan HP ke Laptop**
   ```powershell
   adb devices
   ```

2. **Export Database ke Laptop**
   ```powershell
   adb pull /data/data/com.capstone.cattleweight/databases/cattle_dataset.db .
   ```

3. **Export Semua Foto ke Laptop**
   ```powershell
   # Buat folder untuk menyimpan
   New-Item -ItemType Directory -Path ".\cattle_images" -Force
   
   # Copy semua foto
   adb pull /storage/emulated/0/Pictures/CattleDataset ./cattle_images
   ```

### Membaca Database (SQLite)

**Menggunakan SQLite Browser:**
1. Download [DB Browser for SQLite](https://sqlitebrowser.org/)
2. Buka file `cattle_dataset.db`
3. Export ke CSV:
   - File → Export → Table as CSV
   - Pilih tabel `dataset`
   - Simpan sebagai `dataset.csv`

**Menggunakan Python:**
```python
import sqlite3
import pandas as pd

# Buka database
conn = sqlite3.connect('cattle_dataset.db')

# Baca ke DataFrame
df = pd.read_sql_query("SELECT * FROM dataset", conn)

# Export ke CSV
df.to_csv('cattle_dataset.csv', index=False)

# Lihat statistik
print(df.describe())
print(f"\nTotal dataset: {len(df)}")

conn.close()
```

---

## 🔍 Format Data Export

**Contoh CSV:**
```csv
id,image_path,distance_cm,signal_strength,temperature,timestamp
1,content://media/external/images/media/1001,150,250,28.5,2025-01-21 14:30:22
2,content://media/external/images/media/1002,145,245,28.3,2025-01-21 14:31:05
3,content://media/external/images/media/1003,152,255,28.7,2025-01-21 14:32:18
```

**Keterangan:**
- `id`: Nomor urut dataset
- `image_path`: URI gambar di MediaStore Android
- `distance_cm`: Jarak LiDAR dalam centimeter
- `signal_strength`: Kekuatan sinyal (0-65535)
- `temperature`: Suhu sensor LiDAR dalam Celsius
- `timestamp`: Waktu pengambilan (YYYY-MM-DD HH:MM:SS)

---

## 🚨 Troubleshooting

### Foto Tidak Tersimpan di Gallery
- Pastikan aplikasi memiliki izin **Camera** dan **Storage**
- Buka **Settings** → **Apps** → **Cattle Weight Detector** → **Permissions**
- Aktifkan **Camera** dan **Photos and Videos**

### Database Tidak Bisa Diakses
- Pastikan HP dalam mode **USB Debugging**
- Run as Root (jika perlu):
  ```powershell
  adb root
  adb shell run-as com.capstone.cattleweight cp databases/cattle_dataset.db /sdcard/
  adb pull /sdcard/cattle_dataset.db .
  ```

### Total Data Tidak Update
- Tutup dan buka kembali aplikasi
- Database akan reload otomatis

---

## 📝 Tips Pengambilan Dataset Berkualitas

1. ✅ **Pencahayaan Baik** - Ambil foto di tempat terang
2. ✅ **Jarak Stabil** - Tunggu nilai LiDAR stabil sebelum capture
3. ✅ **Posisi Tegak** - Pastikan kamera sejajar dengan sapi
4. ✅ **Full Body** - Tangkap seluruh tubuh sapi jika memungkinkan
5. ✅ **Sinyal Kuat** - Pastikan signal strength > 100
6. ✅ **Beragam Angle** - Ambil dari berbagai sudut

---

## 🎯 Workflow untuk Tim ML

1. **Kumpulkan Dataset** (Field Team)
   - Ambil foto sapi menggunakan aplikasi
   - Pastikan minimal 100+ data per kategori

2. **Export Data** (Data Engineer)
   - Pull database dan foto dari HP
   - Convert database ke CSV
   - Organize folder structure

3. **Preprocessing** (ML Engineer)
   - Filter data dengan signal strength rendah
   - Resize gambar ke ukuran uniform
   - Split train/validation/test

4. **Training** (ML Engineer)
   - Load CSV sebagai ground truth
   - Train model dengan image + LiDAR features
   - Validate dengan test set

---

## 📦 Kebutuhan Server/Cloud (Opsional)

Jika ingin sinkronisasi otomatis:
1. Setup server PHP + MySQL
2. Tambahkan background sync service di aplikasi
3. Auto-upload saat koneksi WiFi tersedia

**Saat ini**: Semua data tersimpan **lokal di HP** tanpa cloud dependency.

---

## 💡 FAQ

**Q: Berapa kapasitas maksimal penyimpanan?**  
A: Tergantung storage HP. Estimasi 1 foto = 2-5 MB, 1000 foto = 2-5 GB.

**Q: Apakah data akan hilang jika aplikasi di-uninstall?**  
A: Foto di Gallery akan tetap ada. Database SQLite akan terhapus.

**Q: Bagaimana backup dataset?**  
A: Export menggunakan ADB ke laptop secara berkala.

**Q: Apakah bisa export langsung dari HP?**  
A: Saat ini belum tersedia fitur export langsung. Gunakan ADB.

---

**Dibuat untuk Tim ML Capstone Project - Cattle Weight Detection** 🐄
