"""
Script untuk download dan convert YOLOv8 ke TensorFlow Lite
untuk deployment di Android
"""

from ultralytics import YOLO
import os

def download_and_convert_yolo():
    """
    Download YOLOv8n dan convert ke TFLite
    """
    print("=" * 60)
    print("YOLOv8 to TensorFlow Lite Conversion")
    print("=" * 60)
    
    # Load YOLOv8n (akan auto-download jika belum ada)
    print("\n1. Loading/Downloading YOLOv8n model...")
    model = YOLO('yolov8n.pt')
    print("✓ Model loaded successfully")
    
    # Export ke TFLite
    print("\n2. Exporting to TensorFlow Lite format...")
    print("   This may take a few minutes...")
    
    try:
        # Export dengan berbagai format
        success = model.export(
            format='tflite',
            imgsz=640,  # Input size
            int8=False,  # Use float32 (lebih akurat)
        )
        
        print(f"✓ Export successful!")
        print(f"   Output: {success}")
        
        # Check file
        if os.path.exists('yolov8n_float32.tflite'):
            size = os.path.getsize('yolov8n_float32.tflite') / (1024 * 1024)
            print(f"\n✓ TFLite model created: yolov8n_float32.tflite ({size:.2f} MB)")
        
        # Info
        print("\n" + "=" * 60)
        print("NEXT STEPS:")
        print("=" * 60)
        print("1. Copy model ke Android assets:")
        print("   cp yolov8n_float32.tflite ../mobile/android/CattleWeightDetector/app/src/main/assets/")
        print("\n2. Pastikan nama model di YoloDetector.java sesuai:")
        print("   private static final String MODEL_FILE = \"yolov8n_float32.tflite\";")
        print("=" * 60)
        
    except Exception as e:
        print(f"✗ Export failed: {e}")
        print("\nTroubleshooting:")
        print("1. Install dependencies: pip install ultralytics tensorflow")
        print("2. Update ultralytics: pip install -U ultralytics")


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
