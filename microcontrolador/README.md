# Laboratorio 2 – Interfaces (Firmware MCU)

Este proyecto implementa un firmware para microcontrolador (Arduino/PlatformIO) que:

- Configura comunicación UART 115200-8N1 con protocolo binario.
- Controla 4 salidas digitales (LED0..LED3).
- Lee 4 entradas digitales (DIP0..DIP3) con `INPUT_PULLUP`.
- Adquiere 4 señales analógicas (AN0..AN3) y transmite adicionalmente 4 derivadas (AN4..AN7 = AN0..AN3 divididas por 2).
- Permite configurar tiempos de muestreo independientes para DIP y ADC.
- Envía tramas de datos continuas con estructura fija.

## Pines (ajustables en `src/main.cpp`)

- LEDs (salidas): D8, D9, D10, D11 (LED0..LED3)
- DIP (entradas pull-up): D2, D3, D4, D5 (DIP0..DIP3) – activo en HIGH (la función `readDipMask()` devuelve 1 cuando el pin está HIGH)
- Analógicos: A0, A1, A2, A3 (AN0..AN3)

## Trama de datos (streaming)

Tamaño total: 20 bytes, Little Endian para analógicos.

```
[0]  0x7A            Cabecera 1
[1]  0x7B            Cabecera 2
[2]  DIGITAL         Nibble alto=DIP (DIP3..DIP0), nibble bajo=LEDs (LED3..LED0)
[3]  AN0_L  [4] AN0_H  Original
[5]  AN1_L  [6] AN1_H  Original
[7]  AN2_L  [8] AN2_H  Original
[9]  AN3_L [10] AN3_H  Original
[11] AN4_L [12] AN4_H  = AN0/2
[13] AN5_L [14] AN5_H  = AN1/2
[15] AN6_L [16] AN6_H  = AN2/2
[17] AN7_L [18] AN7_H  = AN3/2
[19] 0x7C            Fin de trama
```

Notas:
- Resolución del ADC depende del MCU (p.ej., AVR: 10 bits, 0..1023). Voltaje aprox. (Vref=5V): `V = raw * (5.0/1023.0)`.
- AN4..AN7 usan división entera `raw/2`.

## Protocolo de comandos

Formato de comando (PC→MCU):
```
[0x55][0xAA][CMD][LEN][PAYLOAD...][CHK]
```
Formato de respuesta (MCU→PC):
```
[0x55][0xAB][STATUS][CMD][LEN][PAYLOAD...][CHK]
```

- STATUS: `0x00=OK`, `0x01=CHK inválido`, `0x02=Parámetro inválido`, `0x03=CMD desconocido`.
- CHK: XOR de todos los bytes desde `CMD` (en comando) o `STATUS` (en respuesta) hasta el final del `PAYLOAD`.

Comandos soportados:
- `0x01` Set LED mask (LEN=1). Payload: `[mask 0..15]`. Resp: 1B máscara aplicada.
- `0x02` Get DIP (LEN=0). Resp: 1B máscara DIP.
- `0x03` Set Ts DIP (LEN=2, uint16 LE). Resp: Ts aplicado (2B LE).
- `0x04` Get Ts DIP (LEN=0). Resp: Ts actual (2B LE).
- `0x05` Streaming enable (LEN=1: 0/1). Resp: estado (1B).
- `0x06` Snapshot (LEN=0). Resp: OK y se envía 1 trama de datos.
- `0x07` Get info (LEN=0). Resp: ASCII `LAB2 v1.0`.
- `0x08` Set Ts ADC (LEN=2, uint16 LE). Resp: Ts aplicado (2B LE).
- `0x09` Get Ts ADC (LEN=0). Resp: Ts actual (2B LE).

## Pruebas rápidas (Windows PowerShell)

Reemplaza `COM5` por el puerto de tu Arduino.

```powershell
# Abrir puerto
$port = New-Object System.IO.Ports.SerialPort "COM5",115200,"None",8,"One"
$port.Open(); $port.ReadTimeout = 500
function Send-Hex([byte[]]$b){ $port.Write($b,0,$b.Length) }

# Get info
Send-Hex ([byte[]](0x55,0xAA,0x07,0x00,0x07))
$buf = New-Object byte[] 64; $n = $port.Read($buf,0,$buf.Length)
($buf[0..($n-1)] | % { '{0:X2}' -f $_ }) -join ' '

# Encender LEDs (1010b)
Send-Hex ([byte[]](0x55,0xAA,0x01,0x01,0x0A,0x0A))

# Habilitar streaming
Send-Hex ([byte[]](0x55,0xAA,0x05,0x01,0x01,0x05))
$buf = New-Object byte[] 64; $n = $port.Read($buf,0,$buf.Length)
($buf[0..($n-1)] | % { '{0:X2}' -f $_ }) -join ' '

# Deshabilitar streaming
Send-Hex ([byte[]](0x55,0xAA,0x05,0x01,0x00,0x04))
$port.Close()
```

## Ajuste de tiempos de muestreo

- Ts DIP por defecto: 100 ms. Comando para 50 ms: `55 AA 03 02 32 00 31`.
- Ts ADC por defecto: 50 ms. Comando para 20 ms: `55 AA 08 02 14 00 1C`.

La transmisión continua usa el período más corto entre Ts DIP y Ts ADC.

## Estructura del código

- `src/main.cpp`: implementación completa (UART, parser, comandos, muestreo, trama).
- Comentarios Doxygen en funciones clave para facilitar mantenimiento y extensión.
