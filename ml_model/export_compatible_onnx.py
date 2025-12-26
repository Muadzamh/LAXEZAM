"""
Export YOLO models dengan ONNX IR version 9 (compatible dengan ONNX Runtime 1.16.3)

Root cause error:
    ai.onnxruntime.OrtException: Unsupported model IR version: 10, 
    max supported IR version: 9

Solution:
    Export dengan opset_version yang menghasilkan IR version 9
"""

from ultralytics import YOLO
import onnx
import os

def export_yolo_compatible():
    """Export YOLOv8n dengan ONNX IR version 9"""
    
    print("=" * 70)
    print("Exporting YOLO models compatible with ONNX Runtime 1.16.3")
    print("Target: IR version 9 (opset 13)")
    print("=" * 70)
    
    # 1. Export YOLO detection model
    print("\n[1/2] Exporting YOLOv8n detection model...")
    yolo_model = YOLO('yolov8n.pt')
    
    # Export dengan opset_version 13 untuk IR version 9
    onnx_path = yolo_model.export(
        format='onnx',
        imgsz=640,
        opset=13,  # Opset 13 → IR version 9 (compatible!)
        simplify=True,
        dynamic=False  # Static shapes untuk performa lebih baik
    )
    
    print(f"✓ Exported to: {onnx_path}")
    
    # Verify ONNX model IR version
    model = onnx.load(onnx_path)
    ir_version = model.ir_version
    opset_version = model.opset_import[0].version
    
    print(f"  IR version: {ir_version}")
    print(f"  Opset version: {opset_version}")
    print(f"  File size: {os.path.getsize(onnx_path) / (1024*1024):.2f} MB")
    
    if ir_version <= 9:
        print("  ✅ Compatible with ONNX Runtime 1.16.3")
    else:
        print("  ⚠️  WARNING: IR version > 9, may not be compatible!")
    
    # 2. Check weight predictor model
    print("\n[2/2] Checking weight predictor model...")
    weight_model_path = 'bbox_weight_model.onnx'
    
    if os.path.exists(weight_model_path):
        weight_model = onnx.load(weight_model_path)
        ir_version = weight_model.ir_version
        opset_version = weight_model.opset_import[0].version
        
        print(f"  Current IR version: {ir_version}")
        print(f"  Current Opset version: {opset_version}")
        
        if ir_version > 9:
            print(f"  ⚠️  Incompatible! Need to re-export with opset 13")
            print(f"  Please re-train or convert from bbox_weight_model.pt")
        else:
            print(f"  ✅ Already compatible!")
    else:
        print(f"  ⚠️  File not found: {weight_model_path}")
    
    print("\n" + "=" * 70)
    print("Export complete!")
    print("=" * 70)
    print("\nNext steps:")
    print("1. Copy yolov8n.onnx to /sdcard/ on Android device")
    print("2. If weight model incompatible, re-export with opset=13")
    print("3. Push to device:")
    print("   adb push yolov8n.onnx /sdcard/")
    print("   adb shell 'cat /sdcard/yolov8n.onnx | run-as com.capstone.cattleweight sh -c \"cat > files/models/yolov8n.onnx\"'")

if __name__ == '__main__':
    export_yolo_compatible()
