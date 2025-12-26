"""
Script untuk download dan convert YOLOv8 ke TensorFlow Lite/ONNX
untuk deployment di Android

IMPORTANT: 
- ONNX export selalu berhasil dan recommended untuk Android
- TFLite export mungkin gagal karena onnx2tf tidak kompatibel dengan Python 3.13
- Gunakan ONNX Runtime untuk Android (lebih ringan dan reliable)

Dependencies:
    pip install ultralytics onnx
"""

from ultralytics import YOLO
import os

def download_and_convert_yolo():
    """
    Download YOLOv8n dan convert ke TFLite menggunakan ONNX
    """
    print("=" * 60)
    print("YOLOv8 to TensorFlow Lite Conversion")
    print("=" * 60)
    
    # Load YOLOv8n (akan auto-download jika belum ada)
    print("\n1. Loading/Downloading YOLOv8n model...")
    model = YOLO('yolov8n.pt')
    print("✓ Model loaded successfully")
    
    # Export ke ONNX dulu (lebih reliable)
    print("\n2. Exporting to ONNX format first...")
    
    try:
        # Export ke ONNX
        onnx_path = model.export(
            format='onnx',
            imgsz=640,
            simplify=True,
        )
        print(f"✓ ONNX export successful: {onnx_path}")
        
        if os.path.exists('yolov8n.onnx'):
            size = os.path.getsize('yolov8n.onnx') / (1024 * 1024)
            print(f"   File size: {size:.2f} MB")
        
        # Coba export TFLite (optional, karena ONNX sudah cukup untuk Android)
        print("\n3. Attempting TFLite export...")
        print("   Note: This may fail due to onnx2tf compatibility issues")
        print("   If it fails, you can use ONNX model directly with ONNX Runtime")
        
        try:
            tflite_path = model.export(
                format='tflite',
                imgsz=640,
                int8=False,
            )
            print(f"✓ TFLite export successful: {tflite_path}")
            
            if os.path.exists('yolov8n_float32.tflite'):
                size = os.path.getsize('yolov8n_float32.tflite') / (1024 * 1024)
                print(f"   File size: {size:.2f} MB")
                
        except Exception as tflite_error:
            print(f"⚠ TFLite export failed: {tflite_error}")
            print("\n   No problem! You can use ONNX model instead.")
        
        # Info
        print("\n" + "=" * 60)
        print("EXPORT SUMMARY:")
        print("=" * 60)
        
        if os.path.exists('yolov8n.onnx'):
            print("✓ ONNX Model: yolov8n.onnx")
            print("  - Can be used with ONNX Runtime for Android")
            print("  - Lightweight and efficient")
            
        if os.path.exists('yolov8n_float32.tflite'):
            print("✓ TFLite Model: yolov8n_float32.tflite")
            print("  - Can be used with TensorFlow Lite")
        
        print("\n" + "=" * 60)
        print("NEXT STEPS FOR ANDROID:")
        print("=" * 60)
        print("\nOption 1: Using ONNX Runtime (Recommended)")
        print("1. Add dependency to build.gradle:")
        print("   implementation 'com.microsoft.onnxruntime:onnxruntime-android:latest'")
        print("2. Copy ONNX model to assets:")
        print("   cp yolov8n.onnx ../mobile/android/.../assets/")
        
        if os.path.exists('yolov8n_float32.tflite'):
            print("\nOption 2: Using TensorFlow Lite")
            print("1. Copy TFLite model to assets:")
            print("   cp yolov8n_float32.tflite ../mobile/android/.../assets/")
            print("2. Use TFLite interpreter in your app")
        
        print("=" * 60)
        
    except Exception as e:
        print(f"✗ Export failed: {e}")
        print("\nTroubleshooting:")
        print("1. Install dependencies: pip install ultralytics onnx")
        print("2. Update ultralytics: pip install -U ultralytics")
        print("3. For TFLite: pip install tensorflow (optional)")


def test_yolo_detection():
    """
    Test YOLO detection dengan sample image
    """
    print("\n" + "=" * 60)
    print("Testing YOLO Detection")
    print("=" * 60)
    
    try:
        # Load model
        model = YOLO('yolov8n.pt')
        
        # Check classes
        print("\nCOCO Classes that YOLO can detect:")
        print(f"Total classes: {len(model.names)}")
        print(f"\nClass 19 (Cow): {model.names[19] if 19 in model.names else 'Not found'}")
        
        # Find all animal classes
        print("\nAnimal-related classes:")
        for idx, name in model.names.items():
            if any(animal in name.lower() for animal in ['cow', 'horse', 'dog', 'cat', 'sheep']):
                print(f"  {idx}: {name}")
        
    except Exception as e:
        print(f"Error: {e}")


if __name__ == "__main__":
    import sys
    
    if '--test' in sys.argv:
        test_yolo_detection()
    else:
        download_and_convert_yolo()
        
        # Optional: test
        if '--with-test' in sys.argv:
            test_yolo_detection()
