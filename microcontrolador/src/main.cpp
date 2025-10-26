#include <Arduino.h>

/*
Resumen y protocolo:
- UART: 115200-8N1
- Pines (ajustables según tu hardware):
  - LEDs (salidas digitales): D8, D9, D10, D11
  - DIP-SWITCH (entradas digitales con pull-up): D2, D3, D4, D5 (activo en LOW -> bit '1')
  - Analógicos: A0, A1, A2, A3
- Muestreo: no bloqueante con millis(), períodos configurables independientes (ms).
- Trama de datos continua (#47):
  [0x7A][0x7B][DIGITAL(1B)][AN0_L][AN0_H]...[AN3_H][AN4_L][AN4_H]...[AN7_H][0x7C]
  DIGITAL: nibble alto = DIP (DIP3..DIP0), nibble bajo = LEDs (LED3..LED0)
  AN0..AN3: lecturas originales
  AN4..AN7: lecturas divididas /2 (AN0/2, AN1/2, AN2/2, AN3/2)
- Protocolo de comandos (#48): binario con cabecera 0x55 0xAA
  Comando: [0x55][0xAA][CMD][LEN][PAYLOAD...][CHK]
  Respuesta: [0x55][0xAB][STATUS][CMD][LEN][PAYLOAD...][CHK]
  - CHK = XOR de todos los bytes desde CMD (en comando) o desde STATUS (en respuesta) hasta el final del PAYLOAD.
  - STATUS: 0x00=OK, 0x01=CHK inválido, 0x02=Parámetro inválido, 0x03=CMD desconocido
  CMDs:
    0x01 Set LED mask (LEN=1: mask 0..15). Resp payload: 1B mask aplicado.
    0x02 Get DIP (LEN=0). Resp payload: 1B DIP mask.
    0x03 Set Tsample DIP ms (LEN=2: uint16 LE). Resp payload: uint16 LE aplicado.
    0x04 Get Tsample DIP (LEN=0). Resp payload: uint16 LE actual.
    0x05 Streaming enable (LEN=1: 0=off,!=0=on). Resp payload: 1B estado.
    0x06 Snapshot (LEN=0). Envía respuesta OK y además 1 trama de datos.
    0x07 Get info (LEN=0). Resp payload: ASCII "LAB2 v1.0".
    0x08 Set Tsample ADC ms (LEN=2: uint16 LE). Resp payload: uint16 LE aplicado.
    0x09 Get Tsample ADC (LEN=0). Resp payload: uint16 LE actual.
*/

/*
Detalle ampliado del protocolo y la trama
----------------------------------------
- Trama de datos (20 bytes):
  [0]=0x7A, [1]=0x7B, [2]=DIGITAL,
  [3..4]=AN0, [5..6]=AN1, [7..8]=AN2, [9..10]=AN3,
  [11..12]=AN4(=AN0/2), [13..14]=AN5(=AN1/2), [15..16]=AN6(=AN2/2), [17..18]=AN7(=AN3/2),
  [19]=0x7C. Todos los analógicos en Little Endian (LSB primero).
- DIGITAL: nibble alto = DIP; nibble bajo = LEDs.
- Checksum de control (respuestas): XOR de [STATUS..PAYLOAD].

Comandos (PC->MCU): 55 AA CMD LEN PAYLOAD CHK
- 0x01 Set LED mask (LEN=1). Payload: [mask].
- 0x02 Get DIP (LEN=0).
- 0x03 Set Ts DIP (LEN=2, uint16 LE).
- 0x04 Get Ts DIP (LEN=0).
- 0x05 Streaming (LEN=1, 0/1).
- 0x06 Snapshot (LEN=0).
- 0x07 Get info (LEN=0).
- 0x08 Set Ts ADC (LEN=2, uint16 LE).
- 0x09 Get Ts ADC (LEN=0).

Notas prácticas
- Si usas AVR con Vref=5V y 10 bits, V≈raw*4.887mV.
- El envío continuo usa el período más corto entre Ts DIP y Ts ADC.
- AN4..AN7 son derivados (división entera por 2) de AN0..AN3.
*/

static const uint32_t SERIAL_BAUD = 115200;

// Ajusta estos pines a tu placa
static const uint8_t LED_PINS[4] = {8, 9, 10, 11};      // LED0..LED3
static const uint8_t DIP_PINS[4] = {2, 3, 4, 5};        // DIP0..DIP3 (INPUT_PULLUP)
static const uint8_t ADC_PINS[4] = {A0, A1, A2, A3};    // AN0..AN3

// Estado
static volatile uint8_t ledMask = 0x00; // bits 0..3
static uint8_t lastDipMask = 0x00;      // bits 0..3
static uint16_t lastAdc[8] = {0, 0, 0, 0, 0, 0, 0, 0}; // 0-3: originales, 4-7: divididas /2

static uint16_t samplePeriodDipMs = 100; // tiempo de muestreo DIP
static uint16_t samplePeriodAdcMs = 50;  // tiempo de muestreo ADC
static const uint16_t SAMPLE_MIN_MS = 10;
static const uint16_t SAMPLE_MAX_MS = 5000;
static bool streamingEnabled = false;

static uint32_t lastSampleDipMillis = 0;
static uint32_t lastSampleAdcMillis = 0;

// Utilidades
/**
 * @brief Calcula el checksum XOR de un buffer.
 * @param data Puntero a los datos.
 * @param len  Número de bytes a considerar.
 * @return XOR de todos los bytes.
 */
static inline uint8_t xorChecksum(const uint8_t* data, size_t len) {
  uint8_t x = 0;
  for (size_t i = 0; i < len; ++i) x ^= data[i];
  return x;
}

/**
 * @brief Aplica la máscara de LEDs a las 4 salidas digitales.
 * @param mask Bits [3:0] corresponden a LED3..LED0 (1=ON, 0=OFF).
 */
static void applyLedMask(uint8_t mask) {
  ledMask = (mask & 0x0F);
  for (uint8_t i = 0; i < 4; ++i) {
    digitalWrite(LED_PINS[i], (ledMask & (1u << i)) ? HIGH : LOW);
  }
}

/**
 * @brief Lee el estado del DIP-SWITCH (4 bits) con entradas pull-up.
 * @return Máscara de 4 bits donde 1 indica switch activo (HIGH en pin).
 */
static uint8_t readDipMask() {
  // Nota: tratar el pin HIGH como switch activo (1).
  // Esto invierte la lógica anterior (estaba considerando LOW como activo).
  uint8_t m = 0;
  for (uint8_t i = 0; i < 4; ++i) {
    int v = digitalRead(DIP_PINS[i]);
    if (v == HIGH) m |= (1u << i);
  }
  lastDipMask = m;
  return m;
}

/**
 * @brief Lee las 4 entradas analógicas y deriva 4 señales adicionales divididas por 2.
 * @param out Arreglo de 8 valores: [0..3]=originales, [4..7]=originales/2.
 */
static void readAdcAll(uint16_t out[8]) {
  // Leer las 4 originales y calcular las 4 divididas /2
  for (uint8_t i = 0; i < 4; ++i) {
    uint16_t raw = (uint16_t)analogRead(ADC_PINS[i]);
    out[i] = raw;           // AN0..AN3: originales
    out[i + 4] = raw / 2;   // AN4..AN7: divididas /2
    lastAdc[i] = out[i];
    lastAdc[i + 4] = out[i + 4];
  }
}

/**
 * @brief Envía una trama binaria de datos (20 bytes) con digitales y 8 analógicos.
 * Estructura: 0x7A, 0x7B, DIGITAL, AN0..AN7 (LSB,MSB), 0x7C.
 */
static void sendDataFrame() {
  // [0x7A][0x7B][DIGITAL][AN0_L][AN0_H]...[AN7_H][0x7C]
  uint8_t digitalByte = ((lastDipMask & 0x0F) << 4) | (ledMask & 0x0F);

  uint8_t frame[20]; // 2 cabecera + 1 digital + 16 analógicos (8*2) + 1 fin
  frame[0] = 0x7A;
  frame[1] = 0x7B;
  frame[2] = digitalByte;
  
  // AN0..AN7 en LE (Little Endian)
  for (uint8_t i = 0; i < 8; ++i) {
    frame[3 + i*2] = (uint8_t)(lastAdc[i] & 0xFF);      // byte bajo
    frame[3 + i*2 + 1] = (uint8_t)(lastAdc[i] >> 8);    // byte alto
  }
  
  frame[19] = 0x7C;

  Serial.write(frame, sizeof(frame));
}

// Envío de respuesta del protocolo
/**
 * @brief Envía una respuesta del protocolo 0x55 0xAB.
 * @param status Código de estado (0=OK, 1=CHK inválido, 2=Parámetro inválido, 3=CMD desconocido).
 * @param cmd    Eco del comando recibido.
 * @param payload Datos a incluir (puede ser nullptr si len=0).
 * @param len    Longitud del payload en bytes.
 */
static void sendResponse(uint8_t status, uint8_t cmd, const uint8_t* payload, uint8_t len) {
  Serial.write(0x55);
  Serial.write(0xAB);
  Serial.write(status);
  Serial.write(cmd);
  Serial.write(len);
  if (len && payload) Serial.write(payload, len);

  // CHK = XOR de [STATUS, CMD, LEN, PAYLOAD...]
  uint8_t bufChk[3];
  bufChk[0] = status;
  bufChk[1] = cmd;
  bufChk[2] = len;
  uint8_t x = xorChecksum(bufChk, 3);
  if (len && payload) x ^= xorChecksum(payload, len);
  Serial.write(x);
}

// Manejador de comandos
/**
 * @brief Maneja los comandos del protocolo según su código CMD.
 * @param cmd Código de comando.
 * @param pl  Puntero al payload recibido.
 * @param len Longitud del payload.
 */
static void handleCommand(uint8_t cmd, const uint8_t* pl, uint8_t len) {
  switch (cmd) {
    case 0x01: { // Set LED mask
      if (len != 1) { sendResponse(0x02, cmd, nullptr, 0); return; }
      applyLedMask(pl[0] & 0x0F);
      uint8_t out = ledMask;
      sendResponse(0x00, cmd, &out, 1);
    } break;

    case 0x02: { // Get DIP
      if (len != 0) { sendResponse(0x02, cmd, nullptr, 0); return; }
      uint8_t dip = readDipMask();
      sendResponse(0x00, cmd, &dip, 1);
    } break;

    case 0x03: { // Set sample period DIP (ms), uint16 LE
      if (len != 2) { sendResponse(0x02, cmd, nullptr, 0); return; }
      uint16_t ms = (uint16_t)pl[0] | ((uint16_t)pl[1] << 8);
      if (ms < SAMPLE_MIN_MS) ms = SAMPLE_MIN_MS;
      if (ms > SAMPLE_MAX_MS) ms = SAMPLE_MAX_MS;
      samplePeriodDipMs = ms;
      uint8_t resp[2] = {(uint8_t)(ms & 0xFF), (uint8_t)(ms >> 8)};
      sendResponse(0x00, cmd, resp, 2);
    } break;

    case 0x04: { // Get sample period DIP
      if (len != 0) { sendResponse(0x02, cmd, nullptr, 0); return; }
      uint16_t ms = samplePeriodDipMs;
      uint8_t resp[2] = {(uint8_t)(ms & 0xFF), (uint8_t)(ms >> 8)};
      sendResponse(0x00, cmd, resp, 2);
    } break;

    case 0x05: { // Streaming enable (0/1)
      if (len != 1) { sendResponse(0x02, cmd, nullptr, 0); return; }
      streamingEnabled = (pl[0] != 0);
      uint8_t resp = streamingEnabled ? 1 : 0;
      sendResponse(0x00, cmd, &resp, 1);
    } break;

    case 0x06: { // Snapshot: enviar 1 trama
      if (len != 0) { sendResponse(0x02, cmd, nullptr, 0); return; }
      sendResponse(0x00, cmd, nullptr, 0);
      sendDataFrame();
    } break;

    case 0x07: { // Get info
      if (len != 0) { sendResponse(0x02, cmd, nullptr, 0); return; }
      const char* s = "LAB2 v1.0";
      sendResponse(0x00, cmd, (const uint8_t*)s, (uint8_t)strlen(s));
    } break;

    case 0x08: { // Set sample period ADC (ms), uint16 LE
      if (len != 2) { sendResponse(0x02, cmd, nullptr, 0); return; }
      uint16_t ms = (uint16_t)pl[0] | ((uint16_t)pl[1] << 8);
      if (ms < SAMPLE_MIN_MS) ms = SAMPLE_MIN_MS;
      if (ms > SAMPLE_MAX_MS) ms = SAMPLE_MAX_MS;
      samplePeriodAdcMs = ms;
      uint8_t resp[2] = {(uint8_t)(ms & 0xFF), (uint8_t)(ms >> 8)};
      sendResponse(0x00, cmd, resp, 2);
    } break;

    case 0x09: { // Get sample period ADC
      if (len != 0) { sendResponse(0x02, cmd, nullptr, 0); return; }
      uint16_t ms = samplePeriodAdcMs;
      uint8_t resp[2] = {(uint8_t)(ms & 0xFF), (uint8_t)(ms >> 8)};
      sendResponse(0x00, cmd, resp, 2);
    } break;

    default:
      sendResponse(0x03, cmd, nullptr, 0);
      break;
  }
}

// Parser de comandos (state machine)
enum class RxState : uint8_t { WAIT_H1, WAIT_H2, WAIT_CMD, WAIT_LEN, WAIT_PAYLOAD, WAIT_CHK };
static RxState rxState = RxState::WAIT_H1;
static uint8_t rxCmd = 0;
static uint8_t rxLen = 0;
static uint8_t rxPayload[64];
static uint8_t rxIndex = 0;

/**
 * @brief Parser no bloqueante de comandos por UART (máquina de estados).
 * Procesa bytes disponibles y, si el paquete es válido (checksum OK), llama a handleCommand().
 */
static void processSerial() {
  while (Serial.available() > 0) {
    uint8_t b = (uint8_t)Serial.read();
    switch (rxState) {
      case RxState::WAIT_H1:
        if (b == 0x55) rxState = RxState::WAIT_H2;
        break;
      case RxState::WAIT_H2:
        if (b == 0xAA) rxState = RxState::WAIT_CMD;
        else rxState = RxState::WAIT_H1;
        break;
      case RxState::WAIT_CMD:
        rxCmd = b;
        rxState = RxState::WAIT_LEN;
        break;
      case RxState::WAIT_LEN:
        rxLen = b;
        if (rxLen > sizeof(rxPayload)) {
          // Longitud inválida
          sendResponse(0x02, rxCmd, nullptr, 0);
          rxState = RxState::WAIT_H1;
        } else if (rxLen == 0) {
          rxState = RxState::WAIT_CHK;
        } else {
          rxIndex = 0;
          rxState = RxState::WAIT_PAYLOAD;
        }
        break;
      case RxState::WAIT_PAYLOAD:
        rxPayload[rxIndex++] = b;
        if (rxIndex >= rxLen) {
          rxState = RxState::WAIT_CHK;
        }
        break;
      case RxState::WAIT_CHK: {
        // Verificar checksum: XOR de [CMD, LEN, PAYLOAD...]
        uint8_t buf[2] = {rxCmd, rxLen};
        uint8_t x = xorChecksum(buf, 2);
        if (rxLen) x ^= xorChecksum(rxPayload, rxLen);
        if (x == b) {
          handleCommand(rxCmd, rxPayload, rxLen);
        } else {
          sendResponse(0x01, rxCmd, nullptr, 0);
        }
        rxState = RxState::WAIT_H1;
      } break;
    }
  }
}

/**
 * @brief Inicializa UART, GPIOs y realiza lecturas iniciales.
 */
void setup() {
  // UART
  Serial.begin(SERIAL_BAUD);
  // Estructura base pins
  for (uint8_t i = 0; i < 4; ++i) {
    pinMode(LED_PINS[i], OUTPUT);
    digitalWrite(LED_PINS[i], LOW);
  }
  for (uint8_t i = 0; i < 4; ++i) {
    pinMode(DIP_PINS[i], INPUT_PULLUP);
  }
  // Lecturas iniciales
  readDipMask();
  readAdcAll(lastAdc);
  lastSampleDipMillis = millis();
  lastSampleAdcMillis = millis();
}

/**
 * @brief Bucle principal: procesa comandos, muestrea señales con sus períodos
 *        y transmite tramas si el streaming está habilitado.
 */
void loop() {
  // Procesar comandos entrantes por UART (#42, #48)
  processSerial();

  uint32_t now = millis();

  // Muestreo DIP (#44, #46)
  if ((uint32_t)(now - lastSampleDipMillis) >= samplePeriodDipMs) {
    lastSampleDipMillis = now;
    readDipMask();
  }

  // Muestreo ADC (#45, #46)
  if ((uint32_t)(now - lastSampleAdcMillis) >= samplePeriodAdcMs) {
    lastSampleAdcMillis = now;
    readAdcAll(lastAdc);
  }

  // Envío continuo de tramas (#47) - usa el período más corto para transmitir
  static uint32_t lastTxMillis = 0;
  uint16_t txPeriod = min(samplePeriodDipMs, samplePeriodAdcMs);
  if (streamingEnabled && (uint32_t)(now - lastTxMillis) >= txPeriod) {
    lastTxMillis = now;
    sendDataFrame();
  }
}