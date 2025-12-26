"""
Script untuk konversi model PyTorch ke TensorFlow Lite
untuk deployment di Android

Dependencies:
- torch, torchvision
- onnx, onnxscript (untuk PyTorch ONNX export)
- onnx-tf (untuk ONNX to TensorFlow)
- tensorflow (untuk TFLite conversion)

Install:
pip install torch torchvision onnx onnxscript onnx-tf tensorflow
"""

import torch
import torch.nn as nn
from torchvision import models
import tensorflow as tf
import numpy as np
import onnx
from onnx_tf.backend import prepare

class BBoxWeightModel(nn.Module):
    """Model yang sama seperti di notebook"""
    def __init__(self):
        super().__init__()
        
        backbone = models.resnet18(pretrained=False)
        backbone.fc = nn.Identity()
        self.cnn = backbone
        
        self.regressor = nn.Sequential(
            nn.Linear(512 + 1, 128),
            nn.ReLU(),
            nn.Linear(128, 1)
        )
    
    def forward(self, img, size_feat):
        img_feat = self.cnn(img)
        x = torch.cat([img_feat, size_feat], dim=1)
        return self.regressor(x).squeeze(1)


def convert_pytorch_to_onnx(model_path, onnx_path):
    """Convert PyTorch model to ONNX"""
    print("Loading PyTorch model...")
    model = BBoxWeightModel()
    model.load_state_dict(torch.load(model_path, map_location='cpu'))
    model.eval()
    
    # Dummy inputs
    dummy_img = torch.randn(1, 3, 224, 224)
    dummy_size = torch.randn(1, 1)
    
    print("Exporting to ONNX...")
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
    print(f"ONNX model saved to: {onnx_path}")


def convert_onnx_to_tflite(onnx_path, tflite_path):
    """Convert ONNX model to TensorFlow Lite"""
    print("Loading ONNX model...")
    onnx_model = onnx.load(onnx_path)
    
    print("Converting to TensorFlow...")
    tf_rep = prepare(onnx_model)
    
    # Export as TensorFlow SavedModel first
    saved_model_path = "temp_saved_model"
    tf_rep.export_graph(saved_model_path)
    
    print("Converting to TensorFlow Lite...")
    converter = tf.lite.TFLiteConverter.from_saved_model(saved_model_path)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_types = [tf.float16]
    
    tflite_model = converter.convert()
    
    with open(tflite_path, 'wb') as f:
        f.write(tflite_model)
    
    print(f"TFLite model saved to: {tflite_path}")
    
    # Test the model
    test_tflite_model(tflite_path)


def test_tflite_model(tflite_path):
    """Test the converted TFLite model"""
    print("\nTesting TFLite model...")
    interpreter = tf.lite.Interpreter(model_path=tflite_path)
    interpreter.allocate_tensors()
    
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()
    
    print("Input details:")
    for detail in input_details:
        print(f"  - {detail['name']}: {detail['shape']} ({detail['dtype']})")
    
    print("Output details:")
    for detail in output_details:
        print(f"  - {detail['name']}: {detail['shape']} ({detail['dtype']})")
    
    # Test with dummy data
    test_img = np.random.randn(1, 3, 224, 224).astype(np.float32)
    test_size = np.random.randn(1, 1).astype(np.float32)
    
    interpreter.set_tensor(input_details[0]['index'], test_img)
    interpreter.set_tensor(input_details[1]['index'], test_size)
    
    interpreter.invoke()
    
    output = interpreter.get_tensor(output_details[0]['index'])
    print(f"Test prediction: {output[0]:.2f} kg")


if __name__ == "__main__":
    import os
    
    # Paths
    PYTORCH_MODEL = "bbox_weight_model.pt"
    ONNX_MODEL = "bbox_weight_model.onnx"
    TFLITE_MODEL = "bbox_weight_model.tflite"
    
    if not os.path.exists(PYTORCH_MODEL):
        print(f"ERROR: {PYTORCH_MODEL} not found!")
        print("Please copy the model file to this directory first.")
        exit(1)
    
    print("=" * 60)
    print("PyTorch to TensorFlow Lite Conversion")
    print("=" * 60)
    
    # Step 1: PyTorch -> ONNX
    convert_pytorch_to_onnx(PYTORCH_MODEL, ONNX_MODEL)
    
    # Step 2: ONNX -> TFLite
    convert_onnx_to_tflite(ONNX_MODEL, TFLITE_MODEL)
    
    print("\n" + "=" * 60)
    print("Conversion completed successfully!")
    print(f"TFLite model ready for Android: {TFLITE_MODEL}")
    print("=" * 60)
