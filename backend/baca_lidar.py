import serial
import time

# --- KONFIGURASI ---
SERIAL_PORT = 'COM7'   # Sesuai yang Anda temukan
BAUD_RATE = 115200     # Default TF Luna

def read_tfluna():
    print(f"Mencoba menghubungkan ke {SERIAL_PORT}...")
    try:
        # Membuka koneksi serial
        ser = serial.Serial(SERIAL_PORT, BAUD_RATE, timeout=1)
        
        if ser.isOpen():
            print("Koneksi BERHASIL! Silakan arahkan sensor ke objek...")
            print("-" * 40)
            
        while True:
            # Cek apakah ada data masuk
            counter = ser.in_waiting
            if counter > 8:
                # Baca 9 byte (paket standar TF Luna)
                bytes_serial = ser.read(9) 
                ser.reset_input_buffer() 

                # Cek Header (0x59 = 89 desimal)
                if bytes_serial[0] == 0x59 and bytes_serial[1] == 0x59:
                    
                    # --- RUMUS DECODE DATA ---
                    # Jarak (Distance) = Low Byte + High Byte * 256
                    jarak = bytes_serial[2] + bytes_serial[3] * 256
                    
                    # Kekuatan Sinyal (Signal Strength)
                    kekuatan = bytes_serial[4] + bytes_serial[5] * 256
                    
                    # Suhu Chip (Temperature)
                    suhu = (bytes_serial[6] + bytes_serial[7] * 256) / 8 - 256
                    
                    # Tampilkan Hasil
                    print(f"Jarak: {jarak} cm \t| Sinyal: {kekuatan} \t| Suhu: {suhu:.1f} C")
                
    except serial.SerialException:
        print(f"ERROR: Tidak bisa membuka {SERIAL_PORT}. Pastikan:")
        print("1. Port tidak sedang dipakai aplikasi lain (tutup Serial Monitor Arduino IDE!)")
        print("2. Kabel USB tercolok dengan benar.")
    except KeyboardInterrupt:
        print("\nProgram berhenti.")
        if 'ser' in locals() and ser.isOpen():
            ser.close()

if __name__ == "__main__":
    read_tfluna()