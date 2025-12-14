# Firebase Setup Guide untuk Cattle Weight Detector

## 🔥 **Aplikasi Sudah Diinstall!**

Aplikasi sekarang memiliki **2 halaman** dengan Bottom Navigation:

### **Tab 1: DETEKSI** (Halaman Deteksi Real-time)
- Camera preview (45%)
- Data LiDAR real-time (55%)
- Prediksi bobot karkas sapi

### **Tab 2: DATASET** (Halaman Capture Dataset)
- Camera preview (45%)
- Data LiDAR (55%)
- **Tombol kamera hijau** untuk capture foto
- **Auto-save ke Firebase** (foto + metadata LiDAR)

---

## 📋 **Setup Firebase (Langkah Wajib)**

Saat ini Firebase masih dummy config. Untuk aktifkan fitur capture dataset, ikuti langkah berikut:

### **1. Buat Firebase Project**
1. Buka https://console.firebase.google.com/
2. Klik **"Add Project"**
3. Nama project: `cattle-weight-detector` (atau bebas)
4. Aktifkan Google Analytics (opsional)
5. Klik **"Create project"**

### **2. Daftar Android App ke Firebase**
1. Di Firebase Console, klik ⚙️ **Settings** → **Project settings**
2. Scroll ke bawah, klik **"Add app"** → **Android**
3. Isi form:
   - **Android package name**: `com.capstone.cattleweight`
   - **App nickname**: Cattle Weight Detector (opsional)
   - **Debug signing certificate SHA-1**: (skip untuk dev)
4. Klik **"Register app"**

### **3. Download google-services.json**
1. Download file `google-services.json` dari Firebase Console
2. **Replace** file ini:
   ```
   D:\My Project\Capstone\mobile\android\CattleWeightDetector\app\google-services.json
   ```
3. Paste file baru yang di-download

### **4. Aktifkan Firebase Storage**
1. Di Firebase Console, klik **"Storage"** di sidebar
2. Klik **"Get started"**
3. Pilih **"Start in test mode"** (untuk development)
4. Pilih lokasi: **asia-southeast2** (Jakarta) atau terdekat
5. Klik **"Done"**

### **5. Aktifkan Firestore Database**
1. Di Firebase Console, klik **"Firestore Database"**
2. Klik **"Create database"**
3. Pilih **"Start in test mode"**
4. Lokasi: **asia-southeast2 (Jakarta)**
5. Klik **"Enable"**

### **6. Rebuild APK**
```powershell
cd "D:\My Project\Capstone\mobile\android\CattleWeightDetector"
$env:JAVA_HOME = "C:\Program Files\Java\jdk-23"
.\gradlew.bat installDebug
```

---

## 📁 **Struktur Data di Firebase**

Setelah setup, data akan tersimpan otomatis:

### **Firebase Storage** (Gambar)
```
cattle_images/
├── img_20251212_103000.jpg
├── img_20251212_103015.jpg
└── ...
```

### **Firestore Database** (Metadata)
```
Collection: cattle_dataset
├── Document ID (auto)
│   ├── image_url: "https://firebasestorage.../img_20251212_103000.jpg"
│   ├── distance_cm: 150
│   ├── signal_strength: 5000
│   ├── temperature: 48.5
│   ├── timestamp: 2025-12-12T10:30:00
│   └── captured_by: "android_user"
└── ...
```

---

## 🎯 **Cara Pakai App**

### **Halaman DETEKSI (Tab 1)**
1. Arahkan kamera ke sapi
2. Data LiDAR muncul real-time
3. ML model (jika sudah ditambahkan) akan prediksi bobot

### **Halaman DATASET (Tab 2)**
1. Arahkan kamera ke sapi
2. Posisikan sampai jarak LiDAR stabil
3. **Tap tombol kamera hijau** 📷
4. Foto + data LiDAR otomatis tersimpan ke Firebase
5. Notifikasi "Data saved successfully!" muncul
6. Counter dataset bertambah

---

## 🔐 **Firebase Security Rules (Production)**

Untuk produksi, update Security Rules:

**Storage Rules:**
```javascript
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /cattle_images/{imageId} {
      allow read: if true; // Public read
      allow write: if request.auth != null; // Authenticated write only
    }
  }
}
```

**Firestore Rules:**
```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /cattle_dataset/{document} {
      allow read: if true;
      allow write: if request.auth != null;
    }
  }
}
```

---

## 📊 **Akses Dataset untuk Tim ML**

Tim ML bisa akses dataset melalui:

### **Cara 1: Firebase Console**
1. Buka https://console.firebase.google.com/
2. Pilih project
3. Storage → lihat semua foto
4. Firestore → query data dengan metadata

### **Cara 2: Export via Python**
```python
import firebase_admin
from firebase_admin import credentials, firestore, storage

# Init Firebase
cred = credentials.Certificate("serviceAccountKey.json")
firebase_admin.initialize_app(cred, {
    'storageBucket': 'cattle-weight-detector.appspot.com'
})

db = firestore.client()
bucket = storage.bucket()

# Get all dataset
docs = db.collection('cattle_dataset').stream()

for doc in docs:
    data = doc.to_dict()
    print(f"Distance: {data['distance_cm']}cm, Image: {data['image_url']}")
    
    # Download image
    blob = bucket.blob(data['image_url'])
    blob.download_to_filename(f"dataset/{doc.id}.jpg")
```

---

## ✅ **Status Implementasi**

- ✅ Bottom Navigation (2 tab)
- ✅ DetectionFragment (real-time detection)
- ✅ DatasetFragment (capture + save)
- ✅ Firebase Integration (Storage + Firestore)
- ✅ Camera capture with ImageCapture API
- ✅ LiDAR data sync
- ✅ Auto-upload to cloud
- ⚠️ Firebase config (dummy → perlu replace dengan real config)

---

## 🚀 **Next Steps**

1. Setup Firebase project (15 menit)
2. Download & replace google-services.json
3. Rebuild APK
4. Test capture foto di halaman DATASET
5. Verifikasi data masuk ke Firebase Console
6. Share akses Firebase dengan tim ML

**Butuh bantuan setup Firebase? Beritahu saya!** 🔥
