/**
 * Módulo de parseo de protocolo binario de comunicación
 * Procesa tramas de 20 bytes con datos digitales y analógicos
 * Protocolo: [Header][Digital][8xADC][Tail]
 */

const FRAME_SIZE = 20;
const HEADER_1 = 0x7A;
const HEADER_2 = 0x7B;
const TAIL = 0x7C;

/**
 * Valida que una trama tenga la estructura correcta
 * @param {Buffer} frame - Buffer de 20 bytes
 * @returns {boolean}
 */
function validateFrame(frame) {
  if (!Buffer.isBuffer(frame) || frame.length !== FRAME_SIZE) {
    return false;
  }

  // Verificar header y tail
  if (frame[0] !== HEADER_1 || frame[1] !== HEADER_2 || frame[19] !== TAIL) {
    return false;
  }

  return true;
}

/**
 * Parsea una trama válida y extrae los datos
 * @param {Buffer} frame - Buffer de 20 bytes válido
 * @returns {Object} Objeto con digital y array de 8 valores ADC
 */
function parseFrame(frame) {
  if (!validateFrame(frame)) {
    throw new Error('Trama inválida');
  }

  // Byte digital (posición 2)
  const digital = frame[2];

  // Extraer DIP (nibble alto) y LEDs (nibble bajo)
  const dipMask = (digital >> 4) & 0x0F;  // Bits 7-4: DIP3..DIP0
  const ledMask = digital & 0x0F;          // Bits 3-0: LED3..LED0

  // Extraer bits individuales del DIP (DIN0-DIN3)
  const din = [
    (dipMask & 0x01) ? 1 : 0,  // DIN0 = bit 0 del nibble alto
    (dipMask & 0x02) ? 1 : 0,  // DIN1 = bit 1
    (dipMask & 0x04) ? 1 : 0,  // DIN2 = bit 2
    (dipMask & 0x08) ? 1 : 0   // DIN3 = bit 3
  ];

  // Parsear 8 valores ADC (16 bits Little Endian cada uno)
  const adc = [];
  for (let i = 0; i < 8; i++) {
    const lowByte = frame[3 + i * 2];
    const highByte = frame[4 + i * 2];
    // Little Endian: byte bajo primero
    const value = lowByte | (highByte << 8);
    adc.push(value);
  }

  return {
    digital,      // Byte completo
    dipMask,      // Nibble alto (DIP switches)
    ledMask,      // Nibble bajo (LEDs)
    din,          // Array de 4 bits individuales [DIN0, DIN1, DIN2, DIN3]
    adc,          // Array de 8 valores uint16 [AN0-AN7]
    timestamp: Date.now()
  };
}

/**
 * Busca y extrae tramas completas de un buffer acumulativo
 * @param {Buffer} buffer - Buffer acumulativo con datos seriales
 * @returns {Array<{frame: Buffer, remainder: Buffer}>}
 */
function findFrames(buffer) {
  const frames = [];
  let offset = 0;

  while (offset < buffer.length) {
    // Buscar inicio de trama (0x7A 0x7B)
    const headerIndex = buffer.indexOf(HEADER_1, offset);
    
    if (headerIndex === -1 || headerIndex + 1 >= buffer.length) {
      // No hay más headers, devolver el resto
      break;
    }

    // Verificar segundo byte del header
    if (buffer[headerIndex + 1] !== HEADER_2) {
      offset = headerIndex + 1;
      continue;
    }

    // Verificar que haya suficientes bytes para una trama completa
    if (headerIndex + FRAME_SIZE > buffer.length) {
      // Trama incompleta, guardar desde el header
      offset = headerIndex;
      break;
    }

    // Extraer posible trama
    const possibleFrame = buffer.slice(headerIndex, headerIndex + FRAME_SIZE);

    // Validar tail
    if (possibleFrame[19] === TAIL) {
      frames.push(possibleFrame);
      offset = headerIndex + FRAME_SIZE;
    } else {
      // Header falso, continuar buscando
      offset = headerIndex + 1;
    }
  }

  // Remainder: datos restantes que no forman trama completa
  const remainder = offset < buffer.length ? buffer.slice(offset) : Buffer.alloc(0);

  return { frames, remainder };
}

module.exports = {
  FRAME_SIZE,
  validateFrame,
  parseFrame,
  findFrames
};
