"""
Flask Backend Server untuk Streaming Data LiDAR TF Luna
Mengirim data jarak, sinyal, dan suhu secara real-time ke aplikasi Android
"""

from flask import Flask, jsonify, Response
from flask_cors import CORS
import serial
import time
import json
from threading import Thread, Lock
import sys

app = Flask(__name__)
CORS(app)  # Enable CORS untuk komunikasi dengan Android

# --- KONFIGURASI ---
SERIAL_PORT = 'COM9'
BAUD_RATE = 115200

# Global variables untuk menyimpan data terbaru
latest_data = {
    'jarak': 0,
    'kekuatan': 0,
    'suhu': 0.0,
    'timestamp': 0,
    'status': 'disconnected'
}
data_lock = Lock()
ser = None

def read_lidar_continuous():
    """Thread untuk membaca data LiDAR secara kontinyu"""
    global latest_data, ser
    
    print(f"[INFO] Mencoba menghubungkan ke {SERIAL_PORT}...")
    
    try:
        ser = serial.Serial(SERIAL_PORT, BAUD_RATE, timeout=1)
        
        if ser.isOpen():
            print("[SUCCESS] Koneksi LiDAR BERHASIL!")
            with data_lock:
                latest_data['status'] = 'connected'
            
            while True:
                counter = ser.in_waiting
                if counter > 8:
                    bytes_serial = ser.read(9)
                    ser.reset_input_buffer()
                    
                    # Cek Header (0x59 = 89 desimal)
                    if bytes_serial[0] == 0x59 and bytes_serial[1] == 0x59:
                        # Decode data
                        jarak = bytes_serial[2] + bytes_serial[3] * 256
                        kekuatan = bytes_serial[4] + bytes_serial[5] * 256
                        suhu = (bytes_serial[6] + bytes_serial[7] * 256) / 8 - 256
                        
                        # Update data global dengan thread safety
                        with data_lock:
                            latest_data['jarak'] = jarak
                            latest_data['kekuatan'] = kekuatan
                            latest_data['suhu'] = round(suhu, 1)
                            latest_data['timestamp'] = int(time.time() * 1000)
                            latest_data['status'] = 'connected'
                        
                        print(f"Jarak: {jarak} cm | Sinyal: {kekuatan} | Suhu: {suhu:.1f}°C")
                
                time.sleep(0.01)  # Small delay untuk mencegah CPU overload
                
    except serial.SerialException as e:
        print(f"[ERROR] Tidak bisa membuka {SERIAL_PORT}: {e}")
        with data_lock:
            latest_data['status'] = 'error'
    except Exception as e:
        print(f"[ERROR] Unexpected error: {e}")
        with data_lock:
            latest_data['status'] = 'error'

@app.route('/')
def index():
    """Endpoint untuk cek status server"""
    return jsonify({
        'status': 'running',
        'message': 'LiDAR Server is active',
        'endpoints': {
            '/api/lidar': 'Get latest LiDAR data (JSON)',
            '/api/lidar/stream': 'Server-Sent Events stream'
        }
    })

@app.route('/api/lidar', methods=['GET'])
def get_lidar_data():
    """Endpoint untuk mendapatkan data LiDAR terbaru (single request)"""
    with data_lock:
        return jsonify(latest_data)

@app.route('/api/lidar/stream')
def stream_lidar():
    """Server-Sent Events untuk streaming data real-time"""
    def generate():
        while True:
            with data_lock:
                data = latest_data.copy()
            
            # Format SSE
            yield f"data: {json.dumps(data)}\n\n"
            time.sleep(0.1)  # Update setiap 100ms
    
    return Response(generate(), mimetype='text/event-stream')

@app.route('/api/status', methods=['GET'])
def get_status():
    """Endpoint untuk cek status koneksi LiDAR"""
    with data_lock:
        status = latest_data['status']
    
    return jsonify({
        'lidar_connected': status == 'connected',
        'serial_port': SERIAL_PORT,
        'baud_rate': BAUD_RATE
    })

def cleanup():
    """Cleanup saat server shutdown"""
    global ser
    if ser and ser.isOpen():
        ser.close()
        print("[INFO] Serial port closed")

if __name__ == '__main__':
    # Start LiDAR reading thread
    lidar_thread = Thread(target=read_lidar_continuous, daemon=True)
    lidar_thread.start()
    
    print("\n" + "="*60)
    print("🚀 LiDAR Flask Server Starting...")
    print("="*60)
    print(f"📡 Serial Port: {SERIAL_PORT}")
    print(f"📊 Baud Rate: {BAUD_RATE}")
    print(f"🌐 Server akan berjalan di: http://0.0.0.0:5000")
    print(f"📱 Untuk akses dari Android: http://<IP_KOMPUTER_ANDA>:5000")
    print("="*60 + "\n")
    
    try:
        # Run Flask server
        app.run(host='0.0.0.0', port=5000, debug=False, threaded=True)
    except KeyboardInterrupt:
        print("\n[INFO] Server dihentikan oleh user")
    finally:
        cleanup()
