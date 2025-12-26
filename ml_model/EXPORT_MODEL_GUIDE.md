# 📱 Export Model untuk Android

Tambahkan cell-cell berikut di akhir notebook `capstone_crop_box.ipynb` untuk export model:

## Cell 1: Install Dependencies untuk Export

```python
# Install dengan versi yang kompatibel
!pip install torch torchvision
!pip install onnx==1.15.0
!pip install onnx-tf
!pip install tensorflow
```

**Note:** Gunakan `onnx==1.15.0` untuk kompatibilitas dengan `onnx-tf`.

## Cell 2: Export Model ke ONNX

```python
import torch
import torch.onnx

# Load best model
model = BBoxWeightModel().to(device)
model.load_state_dict(torch.load(MODEL_OUT, map_location=device))
model.eval()

# Dummy inputs untuk tracing
dummy_img = torch.randn(1, 3, 224, 224).to(device)
dummy_size = torch.randn(1, 1).to(device)

# Export ke ONNX
onnx_path = f"{BASE_PATH}/models/bbox_weight_model.onnx"
torch.onnx.export(
    model,
    (dummy_img, dummy_size),
    onnx_path,
    export_params=True,
    opset_version=11,
    do_constant_folding=True,
    input_names=['image', 'size_feature'],
    output_names=['weight_prediction'],
    dynamic_axes={
        'image': {0: 'batch_size'},
        'size_feature': {0: 'batch_size'},
        'weight_prediction': {0: 'batch_size'}
    }
)

print(f"✓ Model exported to ONNX: {onnx_path}")
```

## Cell 3: Convert ONNX to TFLite (Method 1 - Recommended)

```python
import onnx
import tensorflow as tf
from onnx_tf.backend import prepare

# Load ONNX
print("Loading ONNX model...")
onnx_model = onnx.load(onnx_path)

# Convert to TensorFlow
print("Converting to TensorFlow...")
tf_rep = prepare(onnx_model)

# Save as SavedModel
saved_model_path = f"{BASE_PATH}/models/temp_saved_model"
tf_rep.export_graph(saved_model_path)

# Convert to TFLite
print("Converting to TensorFlow Lite...")
converter = tf.lite.TFLiteConverter.from_saved_model(saved_model_path)
converter.optimizations = [tf.lite.Optimize.DEFAULT]
converter.target_spec.supported_types = [tf.float16]

tflite_model = converter.convert()

# Save TFLite
tflite_path = f"{BASE_PATH}/models/bbox_weight_model.tflite"
with open(tflite_path, 'wb') as f:
    f.write(tflite_model)

print(f"✓ TFLite model saved: {tflite_path}")
print(f"   Size: {len(tflite_model) / (1024*1024):.2f} MB")
```

**ALTERNATIVE: Jika masih error, gunakan Method 2 (skip onnx-tf):**

## Cell 3 Alternative: Direct PyTorch to TFLite

```python
import tensorflow as tf

# Method langsung tanpa ONNX intermediate
# Cara 1: Manual conversion menggunakan representative dataset

print("Converting directly to TFLite...")

# Load PyTorch model
model = BBoxWeightModel().to(device)
model.load_state_dict(torch.load(MODEL_OUT, map_location=device))
model.eval()

# Export predictions dari validation set untuk calibration
print("Generating representative dataset...")
sample_images = []
sample_sizes = []

for i in range(min(100, len(val_ds))):
    img, size_feat, _ = val_ds[i]
    sample_images.append(img.numpy())
    sample_sizes.append(size_feat.numpy())

# Save sebagai numpy arrays
import numpy as np
np.save(f"{BASE_PATH}/models/sample_images.npy", np.array(sample_images))
np.save(f"{BASE_PATH}/models/sample_sizes.npy", np.array(sample_sizes))

print("✓ Sample data saved for manual conversion")
print("⚠️ For TFLite conversion, please use local script: convert_to_tflite.py")
print("   (onnx-tf has compatibility issues in Colab)")
```

## Cell 4: Test TFLite Model

```python
import numpy as np

# Load TFLite model
interpreter = tf.lite.Interpreter(model_path=tflite_path)
interpreter.allocate_tensors()

# Get input/output details
input_details = interpreter.get_input_details()
output_details = interpreter.get_output_details()

print("TFLite Model Details:")
print("\nInputs:")
for detail in input_details:
    print(f"  {detail['name']}: {detail['shape']} ({detail['dtype']})")

print("\nOutputs:")
for detail files
print("Downloading files for Android deployment...")

# Download ONNX (untuk local conversion jika diperlukan)
files.download(onnx_path)

# Download TFLite jika berhasil di-convert
if os.path.exists(tflite_path):
    files.download(tflite_path)
    print("\n✅ TFLite model ready!")
else:
    print("\n⚠️ TFLite conversion failed in Colab")
    print("   Please use local script for conversion:")
    print("   1. Download bbox_weight_model.onnx")
    print("   2. Run: python convert_to_tflite.py")

# Download PyTorch model juga
files.download(MODEL_OUT)

print("\nNext steps:")
print("1. If TFLite conversion succeeded:")
print("   Copy bbox_weight_model.tflite to:")
print("   mobile/android/CattleWeightDetector/app/src/main/assets/")
print("\n2. If conversion failed:")
print("   Use local script: cd ml_model && python convert_to_tflite.py")
print("\n3. Convert YOLO model:")
print("   cd ml_model && python convert_yolo_to_tflite.py")
print("\n4. Build Android app inails[0]['index'], test_img_np)
interpreter.set_tensor(input_details[1]['index'], test_size_np)
interpreter.invoke()

# Get output
tflite_pred = interpreter.get_tensor(output_details[0]['index'])[0]

# Compare with PyTorch
with torch.no_grad():
    pytorch_pred = model(
        test_img.unsqueeze(0).to(device),
        test_size.unsqueeze(0).to(device)
    ).item()

print(f"\n✓ Inference Test:")
print(f"  True Weight:    {test_weight.item():.2f} kg")
print(f"  PyTorch Pred:   {pytorch_pred:.2f} kg")
print(f"  TFLite Pred:    {tflite_pred:.2f} kg")
print(f"  Difference:     {abs(pytorch_pred - tflite_pred):.4f} kg")
```

## Cell 5: Download untuk Android

```python
from google.colab import files

# Download TFLite model
print("Downloading TFLite model...")
files.download(tflite_path)

print("\n✅ Model siap untuk Android!")
print("\nNext steps:")
print("1. Copy bbox_weight_model.tflite ke:")
print("   mobile/android/CattleWeightDetector/app/src/main/assets/")
print("\n2. Convert YOLO model:")
print("   cd ml_model")
print("   python convert_yolo_to_tflite.py")
print("\n3. Build Android app di Android Studio")
```

---

## Alternative: Local Conversion (Non-Colab)

Jika tidak menggunakan Colab, gunakan script yang sudah dibuat:

```bash
cd ml_model

# 1. Copy model dari training
cp /path/to/bbox_weight_model.pt ./

# 2. Convert to TFLite
python convert_to_tflite.py

# 3. Convert YOLO
python convert_yolo_to_tflite.py

# 4. Copy to Android assets
mkdir -p ../mobile/android/CattleWeightDetector/app/src/main/assets
cp bbox_weight_model.tflite ../mobile/android/CattleWeightDetector/app/src/main/assets/
cp yolov8n_float32.tflite ../mobile/android/CattleWeightDetector/app/src/main/assets/
```

---

## Verification Checklist


### Error: ImportError: cannot import name 'mapping' from 'onnx'
**Penyebab:** Incompatibility antara versi onnx dan onnx-tf

**Solusi 1 - Downgrade onnx:**
```python
!pip uninstall onnx -y
!pip install onnx==1.15.0
!pip install onnx-tf
```

**Solusi 2 - Skip Colab conversion (RECOMMENDED):**
1. Download model `.pt` dan `.onnx` dari Colab
2. Jalankan conversion di local:
```bash
# Di komputer lokal
cd ml_model
python convert_to_tflite.py
```

Script local lebih stabil karena bisa control versi dependencies. ] Model file copied ke Android assets folder
- [ ] Build Android app tanpa error
- [ ] Test prediksi di device real

---

## Troubleshooting

### Error: ModuleNotFoundError: No module named 'onnxscript'
**Solusi:**
```python
!pip install onnxscript
```
Atau install ulang semua dependencies:
```python
!pip install --upgrade onnx onnxscript onnx-tf tensorflow
```

### Error: onnx-tf not found
```bash
pip install onnx-tf
```

### Error: TFLite conversion failed
```python
# Try without optimization
converter = tf.lite.TFLiteConverter.from_saved_model(saved_model_path)
# Don't set optimizations
tflite_model = converter.convert()
```

### Large difference between PyTorch and TFLite
- Check normalization (mean/std)
- Verify input preprocessing
- Try without float16 quantization
