/**
 * Implementación de protocolo de comandos binario
 * Gestiona la construcción, envío y validación de comandos al microcontrolador
 * Formato: [Header][CMD][LEN][Payload][Checksum]
 */

const CMD_HEADER_1 = 0x55;
const CMD_HEADER_2 = 0xAA;
const RESP_HEADER_2 = 0xAB;

// Códigos de comando
const COMMANDS = {
  SET_LED_MASK: 0x01,
  GET_DIP: 0x02,
  SET_TSAMPLE_DIP: 0x03,
  GET_TSAMPLE_DIP: 0x04,
  STREAMING_ENABLE: 0x05,
  SNAPSHOT: 0x06,
  GET_INFO: 0x07,
  SET_TSAMPLE_ADC: 0x08,
  GET_TSAMPLE_ADC: 0x09
};

// Códigos de estado de respuesta
const STATUS = {
  OK: 0x00,
  CHK_INVALID: 0x01,
  PARAM_INVALID: 0x02,
  CMD_UNKNOWN: 0x03
};

/**
 * Calcula el checksum XOR
 * @param {Buffer} data - Datos para calcular checksum
 * @returns {number}
 */
function calculateChecksum(data) {
  let checksum = 0;
  for (let i = 0; i < data.length; i++) {
    checksum ^= data[i];
  }
  return checksum;
}

/**
 * Construye un comando para enviar al microcontrolador
 * @param {number} cmd - Código de comando
 * @param {Buffer|Array} payload - Datos del payload (opcional)
 * @returns {Buffer}
 */
function buildCommand(cmd, payload = []) {
  const payloadBuffer = Buffer.isBuffer(payload) ? payload : Buffer.from(payload);
  const len = payloadBuffer.length;
  
  const command = Buffer.alloc(5 + len); // Header(2) + CMD + LEN + PAYLOAD + CHK
  command[0] = CMD_HEADER_1;
  command[1] = CMD_HEADER_2;
  command[2] = cmd;
  command[3] = len;
  
  if (len > 0) {
    payloadBuffer.copy(command, 4);
  }
  
  // Calcular checksum desde CMD hasta final de PAYLOAD
  const dataForChecksum = command.slice(2, 4 + len);
  command[4 + len] = calculateChecksum(dataForChecksum);
  
  return command;
}

/**
 * Parsea una respuesta del microcontrolador
 * @param {Buffer} response - Buffer de respuesta
 * @returns {Object|null}
 */
function parseResponse(response) {
  if (!response || response.length < 6) {
    return null;
  }
  
  // Verificar header
  if (response[0] !== CMD_HEADER_1 || response[1] !== RESP_HEADER_2) {
    return null;
  }
  
  const status = response[2];
  const cmd = response[3];
  const len = response[4];
  
  if (response.length < 6 + len) {
    return null;
  }
  
  const payload = len > 0 ? response.slice(5, 5 + len) : Buffer.alloc(0);
  const receivedChecksum = response[5 + len];
  
  // Verificar checksum
  const dataForChecksum = response.slice(2, 5 + len);
  const calculatedChecksum = calculateChecksum(dataForChecksum);
  
  if (receivedChecksum !== calculatedChecksum) {
    console.warn('[CommandProtocol] Checksum inválido en respuesta');
    return null;
  }
  
  return {
    status,
    cmd,
    payload,
    isOk: status === STATUS.OK
  };
}

/**
 * Comando: Habilitar/deshabilitar streaming de datos
 * @param {boolean} enable - true para habilitar, false para deshabilitar
 * @returns {Buffer}
 */
function streamingEnable(enable) {
  return buildCommand(COMMANDS.STREAMING_ENABLE, [enable ? 1 : 0]);
}

/**
 * Comando: Establecer máscara de LEDs
 * @param {number} mask - Máscara de 4 bits (0-15)
 * @returns {Buffer}
 */
function setLedMask(mask) {
  return buildCommand(COMMANDS.SET_LED_MASK, [mask & 0x0F]);
}

/**
 * Comando: Obtener estado del DIP switch
 * @returns {Buffer}
 */
function getDip() {
  return buildCommand(COMMANDS.GET_DIP, []);
}

/**
 * Comando: Establecer período de muestreo DIP (ms)
 * @param {number} periodMs - Período en milisegundos (10-5000)
 * @returns {Buffer}
 */
function setTsampleDip(periodMs) {
  const value = Math.max(10, Math.min(5000, periodMs));
  return buildCommand(COMMANDS.SET_TSAMPLE_DIP, [
    value & 0xFF,        // Low byte
    (value >> 8) & 0xFF  // High byte
  ]);
}

/**
 * Comando: Establecer período de muestreo ADC (ms)
 * @param {number} periodMs - Período en milisegundos (10-5000)
 * @returns {Buffer}
 */
function setTsampleAdc(periodMs) {
  const value = Math.max(10, Math.min(5000, periodMs));
  return buildCommand(COMMANDS.SET_TSAMPLE_ADC, [
    value & 0xFF,        // Low byte
    (value >> 8) & 0xFF  // High byte
  ]);
}

/**
 * Comando: Obtener información del sistema
 * @returns {Buffer}
 */
function getInfo() {
  return buildCommand(COMMANDS.GET_INFO, []);
}

/**
 * Comando: Capturar una trama (snapshot)
 * @returns {Buffer}
 */
function snapshot() {
  return buildCommand(COMMANDS.SNAPSHOT, []);
}

module.exports = {
  COMMANDS,
  STATUS,
  buildCommand,
  parseResponse,
  streamingEnable,
  setLedMask,
  getDip,
  setTsampleDip,
  setTsampleAdc,
  getInfo,
  snapshot
};
