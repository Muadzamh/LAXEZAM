# 🚀 QUICK START - USB Device List

## ✅ Yang Sudah Dibuat

### 1️⃣ **Method di UvcCameraManager**
📂 File: `UvcCameraManager.java`
```java
uvcCameraManager.listAllUsbDevices();
```

### 2️⃣ **UsbTestActivity (Complete Testing Tool)**
📂 Files:
- `app/src/main/java/com/capstone/cattleweight/UsbTestActivity.java`
- `app/src/main/res/layout/activity_usb_test.xml`

---

## 🎯 CARA PAKAI TERCEPAT

### Option A: Dari Activity yang Sudah Ada (MainActivity / DatasetFragment)

**Tambahkan 2 baris kode di `onCreate()` atau `onViewCreated()`:**

```java
UvcCameraManager testManager = new UvcCameraManager(this); // atau requireContext()
testManager.listAllUsbDevices();
```

**Contoh di MainActivity.java:**
```java
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    
    // ✅ TAMBAHKAN 2 BARIS INI
    UvcCameraManager usbChecker = new UvcCameraManager(this);
    usbChecker.listAllUsbDevices();
    
    // ... rest of code
}
```

**Contoh di DetectionFragment.java:**
```java
@Override
public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    
    // ✅ TAMBAHKAN 2 BARIS INI
    UvcCameraManager usbChecker = new UvcCameraManager(requireContext());
    usbChecker.listAllUsbDevices();
    
    // ... rest of code
}
```

---

### Option B: Pakai UsbTestActivity (Recommended untuk Testing)

#### Step 1: Tambahkan Activity di AndroidManifest.xml

File: `app/src/main/AndroidManifest.xml`

Tambahkan di dalam `<application>`:
```xml
<activity 
    android:name=".UsbTestActivity"
    android:label="USB Device Test"
    android:exported="true">
    <!-- Optional: buat launcher icon tersendiri -->
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
```

#### Step 2: Run App

Build & Install app, kemudian:
1. Colokkan USB device ke HP via OTG
2. Buka "USB Device Test" app dari launcher
3. Click button "🔍 Check USB Devices"
4. Lihat hasil di layar dan di Logcat

---

### Option C: Simple Method Standalone

Tambahkan method ini di Activity/Fragment manapun:

```java
private void checkUsbDevices() {
    android.hardware.usb.UsbManager usbManager = 
        (android.hardware.usb.UsbManager) getSystemService(Context.USB_SERVICE);
    
    java.util.HashMap<String, android.hardware.usb.UsbDevice> devices = 
        usbManager.getDeviceList();
    
    android.util.Log.d("USB_CHECK", "Found " + devices.size() + " devices");
    
    for (android.hardware.usb.UsbDevice device : devices.values()) {
        android.util.Log.d("USB_CHECK", "Device: " + device.getProductName());
    }
}
```

Panggil: `checkUsbDevices();`

---

## 📱 Cara Lihat Hasil

### Di Logcat (Android Studio):
1. Buka Logcat tab
2. Filter dengan tag: **`UvcCameraManager`** atau **`UsbTestActivity`**
3. Lihat output detail

### Di UI (Jika pakai UsbTestActivity):
Langsung tampil di TextView di screen

---

## 🎯 REKOMENDASI UNTUK ANDA

**Untuk Test Cepat:**
```java
// Di MainActivity.onCreate() - TAMBAHKAN 2 BARIS INI:
new UvcCameraManager(this).listAllUsbDevices();
```

**Untuk Test Lengkap dengan UI:**
1. Add UsbTestActivity di Manifest
2. Build & Run
3. Buka UsbTestActivity
4. Click button

---

## 📋 Output Yang Didapat

```
╔════════════════════════════════════════╗
║     USB DEVICES LIST                   ║
╠════════════════════════════════════════╣
  Total devices: 2
╚════════════════════════════════════════╝

┌─── Device #1 ───────────────────────
│ Device Name    : /dev/bus/usb/001/002
│ Product Name   : USB 2.0 Camera
│ Manufacturer   : Generic
│ Vendor ID      : 0x1234
│ Product ID     : 0x5678
│ Device Class   : 239 (Miscellaneous)
│ Interface Count: 2
└───────────────────────────────────────
  📋 Permission: ✅ GRANTED
```

---

## ⚙️ Permissions (Sudah Ada)

Di `AndroidManifest.xml`:
```xml
<uses-feature android:name="android.hardware.usb.host" />
<uses-permission android:name="android.permission.USB_PERMISSION" />
```

---

## 🔥 PILIH SALAH SATU:

| Method | Mudah | Cepat | Detail | UI |
|--------|-------|-------|--------|-----|
| Option A (2 baris code) | ⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐ | ❌ |
| Option B (UsbTestActivity) | ⭐⭐ | ⭐⭐ | ⭐⭐⭐ | ✅ |
| Option C (Standalone) | ⭐⭐⭐ | ⭐⭐⭐ | ⭐ | ❌ |

**Rekomendasi: Option A untuk test cepat!**

---

## 📞 Files Modified/Created

✅ Modified:
- `UvcCameraManager.java` (added `listAllUsbDevices()` method)

✅ Created:
- `UsbTestActivity.java` (complete test tool)
- `activity_usb_test.xml` (UI layout)
- `USB_DEVICE_LIST_GUIDE.md` (detailed guide)
- `QUICK_START_USB.md` (this file)

---

## 🎓 Penjelasan Singkat

```java
// 1. Get system service
UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

// 2. Get device list (returns HashMap)
HashMap<String, UsbDevice> devices = usbManager.getDeviceList();

// 3. Loop through devices
for (UsbDevice device : devices.values()) {
    String name = device.getProductName();
    int vendorId = device.getVendorId();
    int productId = device.getProductId();
}
```

---

## ✅ DONE!

Semua sudah siap pakai. Tinggal pilih method mana yang Anda suka dan jalankan!

**Paling mudah: Tambahkan 2 baris di MainActivity.onCreate() (Option A)**
