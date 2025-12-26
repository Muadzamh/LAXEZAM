# Panduan Mengecek List USB Device di Android

## Ringkasan

Kode untuk mengecek list USB device yang terhubung:
```java
UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
```

Kode ini **sudah ditambahkan** di class `UvcCameraManager.java` dalam bentuk method `listAllUsbDevices()`.

---

## 📍 Lokasi File

File yang sudah dimodifikasi:
```
mobile/android/CattleWeightDetector/app/src/main/java/com/capstone/cattleweight/UvcCameraManager.java
```

---

## 📝 Method yang Sudah Ditambahkan

Di dalam class `UvcCameraManager`, sekarang ada method baru:

```java
/**
 * List all USB devices connected to the phone
 * This method shows how to get UsbManager and list all USB devices
 */
public void listAllUsbDevices() {
    // Get UsbManager from system service
    UsbManager usbManager = (UsbManager) mContext.getSystemService(Context.USB_SERVICE);
    
    if (usbManager == null) {
        Log.e(TAG, "UsbManager is null - USB service not available");
        return;
    }
    
    // Get all USB devices
    HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
    
    if (deviceList.isEmpty()) {
        Log.i(TAG, "No USB devices found");
        return;
    }
    
    Log.i(TAG, "========== USB DEVICES LIST ==========");
    Log.i(TAG, "Total USB devices connected: " + deviceList.size());
    
    int index = 1;
    for (UsbDevice device : deviceList.values()) {
        Log.i(TAG, "--- Device " + index + " ---");
        Log.i(TAG, "  Device Name: " + device.getDeviceName());
        Log.i(TAG, "  Product Name: " + device.getProductName());
        Log.i(TAG, "  Manufacturer: " + device.getManufacturerName());
        Log.i(TAG, "  Vendor ID: " + device.getVendorId());
        Log.i(TAG, "  Product ID: " + device.getProductId());
        Log.i(TAG, "  Device ID: " + device.getDeviceId());
        Log.i(TAG, "  Device Class: " + device.getDeviceClass());
        Log.i(TAG, "  Interface Count: " + device.getInterfaceCount());
        index++;
    }
    Log.i(TAG, "======================================");
}
```

---

## 🎯 Cara Menggunakan

### Opsi 1: Panggil dari DatasetFragment (Recommended)

Edit file: `DatasetFragment.java`

Tambahkan di method `initializeUvcCamera()` setelah `uvcCameraManager.initialize()`:

```java
private void initializeUvcCamera() {
    if (uvcCameraManager == null) {
        uvcCameraManager = new UvcCameraManager(requireContext());
        
        // Initialize camera callbacks
        uvcCameraManager.initialize(new UvcCameraManager.UvcCameraCallback() {
            @Override
            public void onCameraConnected() {
                // ... existing code ...
                
                // ✅ TAMBAHKAN BARIS INI untuk list semua USB devices
                uvcCameraManager.listAllUsbDevices();
            }
            
            // ... rest of callbacks ...
        });
    }
}
```

### Opsi 2: Panggil dari DetectionFragment

Edit file: `DetectionFragment.java`

Tambahkan UvcCameraManager dan panggil method di `onViewCreated()`:

```java
public class DetectionFragment extends Fragment {
    private UvcCameraManager testUvcManager; // Tambahkan field ini
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        initializeViews(view);
        
        // ✅ TAMBAHKAN KODE INI untuk test list USB devices
        testUvcManager = new UvcCameraManager(requireContext());
        testUvcManager.listAllUsbDevices();
        
        // ... existing code continues ...
    }
}
```

### Opsi 3: Panggil dari MainActivity

Edit file: `MainActivity.java`

Tambahkan di method `onCreate()`:

```java
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    
    // ✅ TAMBAHKAN KODE INI
    UvcCameraManager testManager = new UvcCameraManager(this);
    testManager.listAllUsbDevices();
    
    // ... existing code continues ...
}
```

---

## 📱 Cara Melihat Hasil

1. **Build & Install aplikasi** ke HP Android
2. **Colokkan USB device** (camera, LiDAR, dll) ke HP via USB OTG
3. **Buka Logcat** di Android Studio
4. **Filter dengan tag**: `UvcCameraManager`
5. Anda akan melihat output seperti:

```
========== USB DEVICES LIST ==========
Total USB devices connected: 2
--- Device 1 ---
  Device Name: /dev/bus/usb/001/002
  Product Name: USB 2.0 Camera
  Manufacturer: Generic
  Vendor ID: 1234
  Product ID: 5678
  Device ID: 2
  Device Class: 239
  Interface Count: 2
--- Device 2 ---
  Device Name: /dev/bus/usb/001/003
  Product Name: USB Serial
  Manufacturer: FTDI
  Vendor ID: 0403
  Product ID: 6001
  Device ID: 3
  Device Class: 0
  Interface Count: 1
======================================
```

---

## 🔍 Alternatif: Buat Method Standalone di Activity

Jika Anda ingin versi yang lebih simple tanpa dependency ke UvcCameraManager:

```java
// Tambahkan method ini di Activity atau Fragment manapun
private void listAllUsbDevices() {
    UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
    
    if (usbManager == null) {
        Log.e("USB_CHECK", "UsbManager is null");
        return;
    }
    
    HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
    
    Log.d("USB_CHECK", "Found " + deviceList.size() + " USB devices");
    
    for (UsbDevice device : deviceList.values()) {
        Log.d("USB_CHECK", "Device: " + device.getProductName() + 
              " (VID=" + device.getVendorId() + 
              " PID=" + device.getProductId() + ")");
    }
}

// Panggil di onCreate() atau onViewCreated()
listAllUsbDevices();
```

---

## ⚙️ Permissions Required

Pastikan di `AndroidManifest.xml` sudah ada:

```xml
<uses-feature android:name="android.hardware.usb.host" />
<uses-permission android:name="android.permission.USB_PERMISSION" />
```

---

## 📊 Informasi yang Didapat

Method ini akan memberikan informasi:
- ✅ **Total USB devices** yang terdeteksi
- ✅ **Device Name** (path di sistem)
- ✅ **Product Name** (nama produk)
- ✅ **Manufacturer** (pembuat)
- ✅ **Vendor ID** (ID vendor)
- ✅ **Product ID** (ID produk)
- ✅ **Device Class** (kelas device: Camera, Serial, dll)
- ✅ **Interface Count** (jumlah interface)

---

## 🎓 Penjelasan Kode

```java
// 1. Dapatkan UsbManager dari system service
UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);

// 2. Dapatkan HashMap berisi semua USB devices
HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();

// 3. Loop untuk setiap device
for (UsbDevice device : deviceList.values()) {
    // Akses info device
    String name = device.getDeviceName();
    String product = device.getProductName();
    int vendorId = device.getVendorId();
    // ... dll
}
```

---

## 🚀 Quick Test

Untuk test cepat, tambahkan di `MainActivity.onCreate()`:

```java
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    
    // Quick USB device check
    new UvcCameraManager(this).listAllUsbDevices();
    
    // ... rest of code ...
}
```

Build, install, colok USB device, dan cek Logcat dengan filter `UvcCameraManager`.

---

## 📌 Catatan Penting

1. **Context diperlukan** - method ini butuh Context (Activity/Fragment/Application)
2. **Runtime only** - Tidak bisa di-test di emulator (kecuali emulator support USB passthrough)
3. **USB OTG required** - HP harus support USB OTG
4. **Check Logcat** - Output ada di Logcat, bukan di UI
5. **Permission** - Beberapa device mungkin perlu permission runtime

---

## 🎯 Summary

| Aspek | Detail |
|-------|--------|
| **File yang dimodifikasi** | `UvcCameraManager.java` |
| **Method yang ditambahkan** | `listAllUsbDevices()` |
| **Cara pakai** | Panggil dari Activity/Fragment |
| **Output** | Logcat dengan tag `UvcCameraManager` |
| **Requirement** | USB OTG support + device tercolok |

---

**Selesai!** Method sudah siap digunakan. Tinggal panggil saja di tempat yang Anda inginkan.
