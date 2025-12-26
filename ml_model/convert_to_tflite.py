"""
Script untuk konversi model PyTorch ke TensorFlow Lite
untuk deployment di Android

Dependencies:
- torch, torchvision
- tensorflow (untuk TFLite conversion)
- tf2onnx (untuk konversi lebih baik)

Install:
pip install torch torchvision tensorflow tf2onnx
"""

import torch
import torch.nn as nn
from torchvision import models
import tensorflow as tf
import numpy as np
import os
import shutil

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


def convert_pytorch_to_tflite_via_tf(model_path, tflite_path):
    """
    Convert PyTorch model to TFLite dengan membuat model TensorFlow equivalent
    """
    print("Loading PyTorch model...")
    pytorch_model = BBoxWeightModel()
    pytorch_model.load_state_dict(torch.load(model_path, map_location='cpu'))
    pytorch_model.eval()
    
    print("Building equivalent TensorFlow model...")
    
    # Extract weights dari PyTorch model
    # 1. CNN backbone (ResNet18)
    resnet_weights = {}
    for name, param in pytorch_model.cnn.named_parameters():
        resnet_weights[name] = param.detach().numpy()
    
    # 2. Regressor weights
    regressor_weights = []
    for param in pytorch_model.regressor.parameters():
        regressor_weights.append(param.detach().numpy())
    
    # Build TensorFlow model
    class TFBBoxWeightModel(tf.keras.Model):
        def __init__(self):
            super().__init__()
            # Use ResNet18 architecture
            self.resnet = tf.keras.applications.ResNet50(
                include_top=False,
                weights=None,
                input_shape=(224, 224, 3),
                pooling='avg'
            )
            
            # Regressor layers
            self.dense1 = tf.keras.layers.Dense(128, activation='relu')
            self.dense2 = tf.keras.layers.Dense(1)
        
        def call(self, inputs):
            image, size_feat = inputs
            # Note: TF expects channels_last format (B, H, W, C)
            img_feat = self.resnet(image)
            combined = tf.concat([img_feat, size_feat], axis=-1)
            x = self.dense1(combined)
            output = self.dense2(x)
            return tf.squeeze(output, axis=-1)
    
    # Create and build model
    tf_model = TFBBoxWeightModel()
    
    # Build model with dummy inputs
    dummy_img = tf.random.normal((1, 224, 224, 3))
    dummy_size = tf.random.normal((1, 1))
    _ = tf_model([dummy_img, dummy_size])
    
    # Note: Direct weight transfer dari ResNet18 PyTorch ke ResNet50 TF tidak straightforward
    # Kita akan menggunakan pendekatan training ulang atau menggunakan model yang sudah ada
    
    print("Saving as SavedModel...")
    saved_model_path = "temp_saved_model"
    if os.path.exists(saved_model_path):
        shutil.rmtree(saved_model_path)
    tf.saved_model.save(tf_model, saved_model_path)
    
    print("Converting to TensorFlow Lite...")
    converter = tf.lite.TFLiteConverter.from_saved_model(saved_model_path)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_types = [tf.float16]
    
    tflite_model = converter.convert()
    
    with open(tflite_path, 'wb') as f:
        f.write(tflite_model)
    
    print(f"TFLite model saved to: {tflite_path}")
    
    # Cleanup
    if os.path.exists(saved_model_path):
        shutil.rmtree(saved_model_path)
    
    return tflite_path


def convert_pytorch_to_tflite_simple(model_path, tflite_path):
    """
    Simplified conversion: Create simple TF model structure
    """
    print("=" * 60)
    print("WARNING: Simplified conversion method")
    print("This creates a TFLite model structure, but weights need retraining")
    print("=" * 60)
    
    print("\nCreating TensorFlow model structure...")
    
    # Create simple sequential model to avoid complex nested structures
    image_input = tf.keras.Input(shape=(224, 224, 3), name='image')
    size_input = tf.keras.Input(shape=(1,), name='size_feature')
    
    # CNN backbone - using MobileNetV2 as lightweight alternative
    base_model = tf.keras.applications.MobileNetV2(
        include_top=False,
        weights='imagenet',
        input_shape=(224, 224, 3),
        pooling='avg'
    )
    
    img_features = base_model(image_input)
    
    # Concatenate with size feature
    combined = tf.keras.layers.Concatenate()([img_features, size_input])
    
    # Regressor
    x = tf.keras.layers.Dense(128, activation='relu', name='dense1')(combined)
    output = tf.keras.layers.Dense(1, name='weight_prediction')(x)
    
    model = tf.keras.Model(inputs=[image_input, size_input], outputs=output)
    model.compile(optimizer='adam', loss='mse')  # Compile to make it concrete
    
    print("\nModel summary:")
    model.summary()
    
    print("\nConverting directly to TensorFlow Lite...")
    
    # Direct conversion to TFLite without SavedModel
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_types = [tf.float16]
    
    tflite_model = converter.convert()
    
    with open(tflite_path, 'wb') as f:
        f.write(tflite_model)
    
    print(f"\nTFLite model saved to: {tflite_path}")
    print(f"Model size: {len(tflite_model) / (1024*1024):.2f} MB")
    
    return tflite_path


def test_tflite_model(tflite_path):
    """Test the converted TFLite model"""
    print("\nTesting TFLite model...")
    interpreter = tf.lite.Interpreter(model_path=tflite_path)
    interpreter.allocate_tensors()
    
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()
    
    print("Input details:")
    for i, detail in enumerate(input_details):
        print(f"  [{i}] {detail['name']}: {detail['shape']} ({detail['dtype']})")
    
    print("Output details:")
    for detail in output_details:
        print(f"  - {detail['name']}: {detail['shape']} ({detail['dtype']})")
    
    # Find which input is which based on shape
    image_idx = None
    size_idx = None
    
    for i, detail in enumerate(input_details):
        if len(detail['shape']) == 4:  # Image: [batch, height, width, channels]
            image_idx = i
        elif len(detail['shape']) == 2:  # Size: [batch, 1]
            size_idx = i
    
    # Test with dummy data - Note TF uses channels_last format
    test_img = np.random.randn(1, 224, 224, 3).astype(np.float32)
    test_size = np.random.randn(1, 1).astype(np.float32)
    
    print(f"\nRunning inference...")
    print(f"  Image input at index {image_idx}: shape {test_img.shape}")
    print(f"  Size input at index {size_idx}: shape {test_size.shape}")
    
    interpreter.set_tensor(input_details[image_idx]['index'], test_img)
    interpreter.set_tensor(input_details[size_idx]['index'], test_size)
    
    interpreter.invoke()
    
    output = interpreter.get_tensor(output_details[0]['index'])
    print(f"\nTest prediction: {output[0][0]:.2f} kg")
    print("\n✓ Model inference successful!")


if __name__ == "__main__":
    import os
    
    # Get script directory
    script_dir = os.path.dirname(os.path.abspath(__file__))
    
    # Paths
    PYTORCH_MODEL = os.path.join(script_dir, "bbox_weight_model.pt")
    TFLITE_MODEL = os.path.join(script_dir, "bbox_weight_model.tflite")
    
    if not os.path.exists(PYTORCH_MODEL):
        print(f"ERROR: {PYTORCH_MODEL} not found!")
        print("Please copy the model file to this directory first.")
        exit(1)
    
    print("=" * 60)
    print("PyTorch to TensorFlow Lite Conversion")
    print("=" * 60)
    print("\nNote: Since direct PyTorch->TFLite weight transfer is complex,")
    print("this script creates a TFLite model structure.")
    print("You may need to retrain the model in TensorFlow for best results.")
    print("=" * 60)
    
    # Convert using simplified method
    convert_pytorch_to_tflite_simple(PYTORCH_MODEL, TFLITE_MODEL)
    
    # Test the model
    test_tflite_model(TFLITE_MODEL)
    
    print("\n" + "=" * 60)
    print("Conversion completed!")
    print(f"TFLite model: {TFLITE_MODEL}")
    print("\nIMPORTANT:")
    print("- Model structure is created but uses ImageNet weights")
    print("- For production, retrain this model with your dataset in TensorFlow")
    print("- Or use the ONNX model directly if your Android app supports it")
    print("=" * 60)
