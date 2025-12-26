"""
Convert PyTorch weight model directly to compatible ONNX
"""

import torch
import torch.nn as nn
import torch.onnx
import onnx
from torchvision import models

class BBoxWeightModel(nn.Module):
    """Weight prediction model with ResNet18 backbone - exact training structure"""
    def __init__(self):
        super().__init__()
        # ResNet18 as feature extractor (exact structure from training)
        backbone = models.resnet18(weights=None)
        # Keep full ResNet18 except final FC layer
        backbone.fc = nn.Identity()  # Remove fc layer
        self.cnn = backbone
        # Regressor: (512 from ResNet + 1 bbox_area) -> weight
        self.regressor = nn.Sequential(
            nn.Linear(513, 128),
            nn.ReLU(),
            nn.Linear(128, 1)
        )
    
    def forward(self, img, bbox_area):
        img_feat = self.cnn(img)
        x = torch.cat([img_feat, bbox_area.unsqueeze(1)], dim=1)
        return self.regressor(x).squeeze(1)

# Create model and load trained weights
print("Creating BBoxWeightModel architecture...")
model = BBoxWeightModel()

print("Loading weights from bbox_weight_model.pt...")
state_dict = torch.load('bbox_weight_model.pt', weights_only=False, map_location='cpu')

# If state_dict is a dict, load it; if it's the model itself, use it
if isinstance(state_dict, dict):
    model.load_state_dict(state_dict)
else:
    # If it's already a model
    model = state_dict

model.eval()

print("✓ Model loaded successfully")

# Create dummy inputs
# Image: (batch_size, channels, height, width)
# Size feature: (batch_size, 1) - normalized bbox area
dummy_img = torch.randn(1, 3, 224, 224)
dummy_size_feature = torch.randn(1)  # Will be broadcasted to (1, 1) by cat operation

# Test inference
with torch.no_grad():
    test_output = model(dummy_img, dummy_size_feature)
    print(f"\nTest inference:")
    print(f"  Image shape: {dummy_img.shape}")
    print(f"  Size feature shape: {dummy_size_feature.shape}")
    print(f"  Output shape: {test_output.shape}")
    print(f"  Predicted weight: {test_output.item():.2f}")

# Export to ONNX with opset 13 (IR version 9)
output_path = 'bbox_weight_model.onnx'

print(f"\nExporting to ONNX with opset 13...")
torch.onnx.export(
    model,
    (dummy_img, dummy_size_feature),  # Tuple of inputs
    output_path,
    export_params=True,
    opset_version=13,  # Ensures IR version 9
    do_constant_folding=True,
    input_names=['image', 'size_feature'],  # Match Android app expectations
    output_names=['weight'],
    dynamic_axes={
        'image': {0: 'batch_size'},
        'size_feature': {0: 'batch_size'},
        'weight': {0: 'batch_size'}
    },
    verbose=False
)

# Verify exported model
print(f"\n✓ Model exported to: {output_path}")

# Check IR version
import os
onnx_model = onnx.load(output_path)
ir_version = onnx_model.ir_version
opset_version = onnx_model.opset_import[0].version
file_size = os.path.getsize(output_path) / 1024

print(f"  IR version: {ir_version}")
print(f"  Opset version: {opset_version}")
print(f"  File size: {file_size:.1f} KB")

if ir_version <= 9:
    print(f"  ✅ Compatible with ONNX Runtime 1.16.3")
else:
    print(f"  ⚠️  WARNING: IR version > 9!")

print("\n" + "=" * 70)
print("Export complete!")
print("=" * 70)
print("\nNext: Push models to Android device")
print("  1. adb push yolov8n.onnx /sdcard/")
print("  2. adb push bbox_weight_model.onnx /sdcard/")
print("  3. Run commands to copy to internal storage")
