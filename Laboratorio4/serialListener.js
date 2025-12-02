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
    this.pendingCommandResolve = null; // Para esperar respuestas de comandos
    this.commandTimeout = null;
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

    // Si estamos esperando una respuesta de comando, intentar parsearla primero
    if (this.pendingCommandResolve) {
      this.commandResponseBuffer = Buffer.concat([this.commandResponseBuffer, data]);
      const response = parseResponse(this.commandResponseBuffer);
      
      if (response) {
        // Respuesta válida recibida
        clearTimeout(this.commandTimeout);
        this.commandResponseBuffer = Buffer.alloc(0);
        const resolve = this.pendingCommandResolve;
        this.pendingCommandResolve = null;
        resolve(response);
        return; // No procesar como trama de datos
      }
      
      // Si el buffer de respuesta es muy grande, algo salió mal
      if (this.commandResponseBuffer.length > 100) {
        console.warn('[Serial] Buffer de respuesta muy grande, limpiando...');
        this.commandResponseBuffer = Buffer.alloc(0);
      }
    }

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
   * @param {boolean} waitResponse - Si debe esperar respuesta
   * @param {number} timeout - Timeout en ms para esperar respuesta
   * @returns {Promise<Object|void>}
   */
  sendCommand(command, waitResponse = false, timeout = 2000) {
    return new Promise((resolve, reject) => {
      if (!this.port || !this.port.isOpen) {
        console.warn('[Serial] Puerto no abierto, no se puede enviar comando');
        return reject(new Error('Puerto no abierto'));
      }

      this.port.write(command, (err) => {
        if (err) {
          console.error('[Serial] Error al enviar comando:', err.message);
          return reject(err);
        }

        if (!waitResponse) {
          return resolve();
        }

        // Configurar espera de respuesta
        this.commandResponseBuffer = Buffer.alloc(0);
        this.pendingCommandResolve = resolve;

        // Configurar timeout
        this.commandTimeout = setTimeout(() => {
          this.pendingCommandResolve = null;
          this.commandResponseBuffer = Buffer.alloc(0);
          reject(new Error('Timeout esperando respuesta del microcontrolador'));
        }, timeout);
      });
    });
  }

  /**
   * Habilita el streaming de datos del microcontrolador
   */
  async enableStreaming() {
    console.log('[Serial] Enviando comando para habilitar streaming...');
    const cmd = streamingEnable(true);
    try {
      const response = await this.sendCommand(cmd, true, 2000);
      if (response && response.isOk) {
        console.log('[Serial] Streaming habilitado correctamente (ACK recibido)');
        this.streamingEnabled = true;
      } else {
        console.warn('[Serial] Respuesta de streaming no OK:', response);
        this.streamingEnabled = false;
      }
    } catch (error) {
      console.error('[Serial] Error al habilitar streaming:', error.message);
      this.streamingEnabled = false;
    }
  }

  /**
   * Deshabilita el streaming de datos del microcontrolador
   * @returns {Promise<void>}
   */
  async disableStreaming() {
    console.log('[Serial] Enviando comando para deshabilitar streaming...');
    const cmd = streamingEnable(false);
    try {
      const response = await this.sendCommand(cmd, true, 2000);
      if (response && response.isOk) {
        console.log('[Serial] Streaming deshabilitado correctamente (ACK recibido)');
        this.streamingEnabled = false;
      } else {
        console.warn('[Serial] Respuesta de disable streaming no OK:', response);
      }
    } catch (error) {
      console.error('[Serial] Error al deshabilitar streaming:', error.message);
      // Continuar de todas formas
    }
  }
}

module.exports = SerialListener;
