#!/usr/bin/env python3
# serial_test.py
# Prueba del protocolo binario con MCU (envío de comandos y lectura de tramas).
# Requiere: pip install pyserial

import serial
import time
import struct

# Configuración
PORT = 'COM2'       # Cambia por tu puerto (COM2, COM3, etc. en Windows)
BAUD = 115200
TIMEOUT = 0.5       # segundos

def xor_checksum(data: bytes) -> int:
    x = 0
    for b in data:
        x ^= b
    return x

def send_bytes(ser: serial.Serial, data: bytes):
    ser.write(data)

def read_bytes(ser: serial.Serial, n: int, timeout=1.0) -> bytes:
    """
    Lee hasta n bytes (espera hasta timeout). Devuelve los bytes leídos.
    """
    end = time.time() + timeout
    buf = bytearray()
    while len(buf) < n and time.time() < end:
        chunk = ser.read(n - len(buf))
        if chunk:
            buf.extend(chunk)
        else:
            # si no hay datos, espera un poco
            time.sleep(0.01)
    return bytes(buf)

def print_hex(b: bytes):
    print(' '.join(f'{x:02X}' for x in b))

def send_command(ser: serial.Serial, cmd: int, payload: bytes = b'') -> bytes:
    """
    Construye y envía comando: 55 AA CMD LEN PAYLOAD CHK
    Devuelve los bytes de la respuesta recibida (lee hasta 64 bytes con timeout).
    """
    header = bytes([0x55, 0xAA])
    length = len(payload)
    body = bytes([cmd, length]) + payload
    chk = xor_checksum(body)
    packet = header + body + bytes([chk])
    send_bytes(ser, packet)
    # leer respuesta (lee lo que haya)
    # lectura básica: intentamos leer hasta 64 bytes y retornarlos
    resp = read_bytes(ser, 64, timeout=0.5)
    return resp

def parse_response(resp: bytes):
    """
    Intenta parsear una respuesta 55 AB STATUS CMD LEN PAYLOAD CHK.
    Si no es respuesta, imprime bruto.
    """
    if len(resp) < 1:
        print("<sin datos>")
        return
    # buscar 0x55 0xAB en el buffer
    idx = resp.find(b'\x55\xAB')
    if idx == -1:
        print("Respuesta no estándar (bruto):")
        print_hex(resp)
        return
    # intentar parsear
    if idx + 5 > len(resp):
        print("Respuesta incompleta:", resp.hex().upper())
        return
    start = idx
    status = resp[start+2]
    cmd = resp[start+3]
    length = resp[start+4]
    expected_len = 5 + length + 1  # 2hdr + status+cmd+len + payload + chk
    if start + expected_len > len(resp):
        print("Respuesta corta, bytes recibidos parciales:")
        print_hex(resp[start:])
        return
    payload = resp[start+5:start+5+length]
    chk = resp[start+5+length]
    # calc chk
    chk_calc = xor_checksum(bytes([status, cmd, length]) + payload)
    ok = (chk_calc == chk)
    print("RESPUESTA:")
    print(f" STATUS=0x{status:02X} CMD=0x{cmd:02X} LEN={length} CHK=0x{chk:02X} (calc=0x{chk_calc:02X}) OK={ok}")
    if length:
        print(" PAYLOAD:", ' '.join(f'{x:02X}' for x in payload))

def find_frames(buf: bytes):
    """
    Extrae y retorna todas las tramas 7A 7B ... 7C contenidas en buf.
    Devuelve lista de bytes (cada trama completa).
    """
    frames = []
    i = 0
    while True:
        start = buf.find(b'\x7A\x7B', i)
        if start == -1:
            break
        end = buf.find(b'\x7C', start+2)
        if end == -1:
            break
        frames.append(buf[start:end+1])
        i = end + 1
    return frames

def parse_frame(frame: bytes):
    """
    Parsea una trama de 20 bytes:
    [0x7A][0x7B][DIGITAL][AN0_L][AN0_H]...[AN7_H][0x7C]
    Retorna (digital_byte, [8 valores ADC uint16]).
    """
    if len(frame) < 20:
        return None
    if frame[0] != 0x7A or frame[1] != 0x7B or frame[-1] != 0x7C:
        return None
    digital = frame[2]
    # leer 8 valores 16-bit LE desde offset 3
    vals = []
    for i in range(8):
        lo = frame[3 + i*2]
        hi = frame[3 + i*2 + 1]
        v = lo | (hi << 8)
        vals.append(v)
    return digital, vals

def main():
    ser = serial.Serial(PORT, BAUD, timeout=0)
    try:
        print("Puerto abierto:", ser.portstr)
        time.sleep(0.1)

        # 1) Get info
        print("\n==> Get info")
        resp = send_command(ser, 0x07, b'')
        print_hex(resp)
        parse_response(resp)

        # 2) Set LED mask = 0x0A (LED3..LED0 = 1010)
        print("\n==> Set LED mask = 0x0A")
        resp = send_command(ser, 0x01, bytes([0x0A]))
        print_hex(resp)
        parse_response(resp)

        # 3) Enable streaming
        print("\n==> Enable streaming")
        resp = send_command(ser, 0x05, bytes([0x01]))
        print_hex(resp)
        parse_response(resp)

        # 4) Leer varias tramas streaming (espera y agrupa)
        print("\n==> Leyendo tramas (1 segundo)...")
        buf = bytearray()
        t_end = time.time() + 1.0
        while time.time() < t_end:
            chunk = ser.read(64)
            if chunk:
                buf.extend(chunk)
            else:
                time.sleep(0.01)

        print("Bytes recibidos:", len(buf))
        print_hex(bytes(buf[:min(128, len(buf))]))
        frames = find_frames(bytes(buf))
        print(f"Se encontraron {len(frames)} tramas completas.")
        for i, f in enumerate(frames):
            parsed = parse_frame(f)
            print(f"Trama {i}: len={len(f)}")
            print_hex(f)
            if parsed:
                digital, vals = parsed
                dip = (digital >> 4) & 0x0F
                leds = digital & 0x0F
                print(f" DIP={dip:01X} LEDS={leds:01X}")
                for ch, v in enumerate(vals):
                    print(f"  AN{ch} = {v} (raw)")

        # 5) Disable streaming
        print("\n==> Disable streaming")
        resp = send_command(ser, 0x05, bytes([0x00]))
        print_hex(resp)
        parse_response(resp)

    finally:
        ser.close()
        print("Puerto cerrado.")

if __name__ == '__main__':
    main()