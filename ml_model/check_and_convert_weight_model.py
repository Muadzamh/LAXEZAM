"""
Check weight predictor model IR version dan convert jika perlu
"""

import onnx
import torch
import torch.onnx
import os

def check_current_model():
    """Check IR version of current weight model"""
    print("Checking bbox_weight_model.onnx...")
    
    if os.path.exists('bbox_weight_model.onnx'):
        model = onnx.load('bbox_weight_model.onnx')
        ir_version = model.ir_version
        opset_version = model.opset_import[0].version
        
        print(f"  Current IR version: {ir_version}")
        print(f"  Current Opset version: {opset_version}")
        print(f"  File size: {os.path.getsize('bbox_weight_model.onnx') / 1024:.1f} KB")
        
        if ir_version > 9:
            print(f"  ⚠️  INCOMPATIBLE with ONNX Runtime 1.16.3!")
            return False
        else:
            print(f"  ✅ Compatible!")
            return True
    else:
        print("  ❌ File not found!")
        return False

def convert_from_pytorch():
    """Convert PyTorch weight model to ONNX with opset 13"""
    print("\nConverting from bbox_weight_model.pt...")
    
    if not os.path.exists('bbox_weight_model.pt'):
        print("  ❌ bbox_weight_model.pt not found!")
        print("  Please provide the PyTorch model file.")
        return False
    
    # Load PyTorch model
    model = torch.load('bbox_weight_model.pt', weights_only=False)
    model.eval()
    
    # Create dummy input (bbox: width, height, area, aspect_ratio)
    dummy_input = torch.randn(1, 4)
    
    # Export to ONNX with opset 13
    output_path = 'bbox_weight_model_compatible.onnx'
    
    torch.onnx.export(
        model,
        dummy_input,
        output_path,
        export_params=True,
        opset_version=13,  # IR version 9
        do_constant_folding=True,
        input_names=['bbox_features'],
        output_names=['weight'],
        dynamic_axes={
            'bbox_features': {0: 'batch_size'},
            'weight': {0: 'batch_size'}
        }
    )
    
    # Verify
    converted_model = onnx.load(output_path)
    ir_version = converted_model.ir_version
    opset_version = converted_model.opset_import[0].version
    
    print(f"\n✓ Exported to: {output_path}")
    print(f"  IR version: {ir_version}")
    print(f"  Opset version: {opset_version}")
    print(f"  File size: {os.path.getsize(output_path) / 1024:.1f} KB")
    
    if ir_version <= 9:
        print(f"  ✅ Compatible with ONNX Runtime 1.16.3")
        # Rename to replace old model
        if os.path.exists('bbox_weight_model.onnx'):
            os.rename('bbox_weight_model.onnx', 'bbox_weight_model_old.onnx')
            print(f"  Old model backed up as bbox_weight_model_old.onnx")
        os.rename(output_path, 'bbox_weight_model.onnx')
        print(f"  Renamed to bbox_weight_model.onnx")
        return True
    else:
        print(f"  ⚠️  Still incompatible!")
        return False

if __name__ == '__main__':
    print("=" * 70)
    print("Weight Predictor Model Compatibility Check")
    print("=" * 70)
    
    is_compatible = check_current_model()
    
    if not is_compatible:
        print("\n" + "=" * 70)
        print("Converting to compatible version...")
        print("=" * 70)
        convert_from_pytorch()
    
    print("\n" + "=" * 70)
    print("Done!")
    print("=" * 70)
