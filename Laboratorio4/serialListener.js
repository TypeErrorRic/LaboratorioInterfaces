const { SerialPort } = require('serialport');
const EventEmitter = require('events');
const { findFrames, parseFrame } = require('./frameParser');
const { streamingEnable, parseResponse } = require('./commandProtocol');

/**
 * Gestor de comunicación serial con microcontrolador
 * Implementa buffer acumulativo, detección de tramas y reconexión automática
 */
class SerialListener extends EventEmitter {
  constructor(portPath, baudRate, reconnectDelay = 3000) {
    super();
    this.portPath = portPath;
    this.baudRate = baudRate;
    this.reconnectDelay = reconnectDelay;
    this.port = null;
    this.buffer = Buffer.alloc(0);
    this.isConnecting = false;
    this.shouldRun = false;
    this.frameCount = 0;
    this.streamingEnabled = false;
    this.commandResponseBuffer = Buffer.alloc(0);
  }

  /**
   * Abre el puerto serial
   */
  async open() {
    if (this.isConnecting) return;
    this.isConnecting = true;
    this.shouldRun = true;

    try {
      this.port = new SerialPort({
        path: this.portPath,
        baudRate: this.baudRate,
        dataBits: 8,
        parity: 'none',
        stopBits: 1
      });

      // Eventos del puerto
      this.port.on('open', () => {
        console.log(`[Serial] Puerto abierto: ${this.portPath} @ ${this.baudRate} baud`);
        this.isConnecting = false;
        this.buffer = Buffer.alloc(0);
        this.emit('connected');
        
        // Esperar a que el microcontrolador se resetee y esté listo
        // Arduino se resetea al abrir puerto serial (DTR)
        console.log('[Serial] Esperando reseteo del microcontrolador...');
        setTimeout(() => {
          this.enableStreaming();
        }, 2000); // 2 segundos para asegurar que el bootloader termine
      });

      this.port.on('data', (data) => {
        this.handleData(data);
      });

      this.port.on('error', (error) => {
        console.error('[Serial] Error:', error.message);
        this.emit('error', error);
      });

      this.port.on('close', () => {
        console.log('[Serial] Puerto cerrado');
        this.emit('disconnected');
        if (this.shouldRun) {
          this.scheduleReconnect();
        }
      });

    } catch (error) {
      console.error('[Serial] Error al abrir puerto:', error.message);
      this.isConnecting = false;
      this.emit('error', error);
      if (this.shouldRun) {
        this.scheduleReconnect();
      }
    }
  }

  /**
   * Maneja datos recibidos del puerto
   * @param {Buffer} data - Datos recibidos
   */
  handleData(data) {
    // Acumular datos en el buffer
    this.buffer = Buffer.concat([this.buffer, data]);

    // Buscar tramas completas en el buffer
    const { frames, remainder } = findFrames(this.buffer);

    // Actualizar buffer con datos no procesados
    this.buffer = remainder;

    // Procesar cada trama encontrada
    frames.forEach(frameBuffer => {
      try {
        const parsedData = parseFrame(frameBuffer);
        this.frameCount++;
        
        // Emitir evento con datos parseados
        this.emit('frame', parsedData);
        
        // Log cada 100 tramas
        if (this.frameCount % 100 === 0) {
          console.log(`[Serial] ${this.frameCount} tramas procesadas`);
        }
      } catch (error) {
        console.error('[Serial] Error al parsear trama:', error.message);
        this.emit('parseError', error);
      }
    });

    // Si el buffer crece demasiado, limpiarlo (posible basura)
    if (this.buffer.length > 1000) {
      console.warn('[Serial] Buffer demasiado grande, limpiando...');
      this.buffer = Buffer.alloc(0);
    }
  }

  /**
   * Programa un intento de reconexión
   */
  scheduleReconnect() {
    if (!this.shouldRun) return;
    
    console.log(`[Serial] Reintentando conexión en ${this.reconnectDelay / 1000}s...`);
    setTimeout(() => {
      this.open();
    }, this.reconnectDelay);
  }

  /**
   * Cierra el puerto serial
   */
  async close() {
    this.shouldRun = false;
    if (this.port && this.port.isOpen) {
      return new Promise((resolve) => {
        this.port.close((err) => {
          if (err) {
            console.error('[Serial] Error al cerrar puerto:', err.message);
          }
          resolve();
        });
      });
    }
  }

  /**
   * Verifica si el puerto está abierto
   */
  isOpen() {
    return this.port && this.port.isOpen;
  }

  /**
   * Envía un comando al microcontrolador
   * @param {Buffer} command - Comando a enviar
   */
  sendCommand(command) {
    if (this.port && this.port.isOpen) {
      this.port.write(command, (err) => {
        if (err) {
          console.error('[Serial] Error al enviar comando:', err.message);
        }
      });
    } else {
      console.warn('[Serial] Puerto no abierto, no se puede enviar comando');
    }
  }

  /**
   * Habilita el streaming de datos del microcontrolador
   */
  enableStreaming() {
    console.log('[Serial] Enviando comando para habilitar streaming...');
    const cmd = streamingEnable(true);
    this.sendCommand(cmd);
    this.streamingEnabled = true;
  }

  /**
   * Deshabilita el streaming de datos del microcontrolador
   */
  disableStreaming() {
    console.log('[Serial] Enviando comando para deshabilitar streaming...');
    const cmd = streamingEnable(false);
    this.sendCommand(cmd);
    this.streamingEnabled = false;
  }
}

module.exports = SerialListener;
