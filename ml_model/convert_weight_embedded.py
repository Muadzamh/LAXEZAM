"""
Convert weight model dengan semua data embedded (no external .data file)
"""

import torch
import torch.nn as nn
from torchvision import models
import onnx

class BBoxWeightModel(nn.Module):
    """Weight prediction model with ResNet18 backbone - exact training structure"""
    def __init__(self):
        super().__init__()
        backbone = models.resnet18(weights=None)
        backbone.fc = nn.Identity()
        self.cnn = backbone
        self.regressor = nn.Sequential(
            nn.Linear(513, 128),
            nn.ReLU(),
            nn.Linear(128, 1)
        )
    
    def forward(self, img, size_feature):
        img_feat = self.cnn(img)
        x = torch.cat([img_feat, size_feature.unsqueeze(1)], dim=1)
        return self.regressor(x).squeeze(1)

# Load model
print("Loading model...")
model = BBoxWeightModel()
state_dict = torch.load('bbox_weight_model.pt', weights_only=False, map_location='cpu')
if isinstance(state_dict, dict):
    model.load_state_dict(state_dict)
else:
    model = state_dict
model.eval()
print("✓ Model loaded")

# Test
dummy_img = torch.randn(1, 3, 224, 224)
dummy_size = torch.randn(1)
with torch.no_grad():
    out = model(dummy_img, dummy_size)
    print(f"Test output: {out.item():.2f}")

# Export dengan data embedded (no external files)
print("\nExporting to ONNX (embedded data, opset 13)...")

# Use older export API yang lebih simple
import io
f = io.BytesIO()

torch.onnx.export(
    model,
    (dummy_img, dummy_size),
    f,
    export_params=True,
    opset_version=13,
    input_names=['image', 'size_feature'],
    output_names=['weight'],
    dynamic_axes={
        'image': {0: 'batch'},
        'size_feature': {0: 'batch'},
        'weight': {0: 'batch'}
    }
)

# Load and save (will embed data)
onnx_model = onnx.load_model_from_string(f.getvalue())

# Force save with embedded data
print("Saving with embedded data...")
onnx.save(onnx_model, 'bbox_weight_model.onnx', 
          save_as_external_data=False)  # No external data file!

# Verify
final_model = onnx.load('bbox_weight_model.onnx')
print(f"\n✓ Saved: bbox_weight_model.onnx")
print(f"  IR version: {final_model.ir_version}")
print(f"  Opset: {final_model.opset_import[0].version}")

import os
size_kb = os.path.getsize('bbox_weight_model.onnx') / 1024
print(f"  Size: {size_kb:.1f} KB")

if final_model.ir_version <= 9:
    print("  ✅ Compatible with ONNX Runtime 1.16+")
else:
    print(f"  ⚠️  Requires ONNX Runtime 1.19+ (IR v{final_model.ir_version})")

print("\nDone! File is self-contained (no .data file needed)")
