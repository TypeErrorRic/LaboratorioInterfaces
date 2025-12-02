require('dotenv').config();
const SerialListener = require('./serialListener');
const DatabaseConnection = require('./dbConnection');
const { insertFrameData, formatDataForLog } = require('./dataInserter');
const { createWebSocketServer } = require('./wsServer');
const IntProcesoData = require('./api/IntProcesoData');
const IntProcesoRefs = require('./api/IntProcesoRefs');
const { setLedMask } = require('./commandProtocol');

/**
 * Sistema de adquisición de datos del microcontrolador
 * Gestiona la comunicación serial, procesamiento de tramas y persistencia en BD
 */

// Configuración desde variables de entorno
const config = {
  serial: {
    port: process.env.SERIAL_PORT || 'COM2',
    baudRate: parseInt(process.env.SERIAL_BAUDRATE) || 115200,
    reconnectDelay: parseInt(process.env.SERIAL_RECONNECT_DELAY) || 3000
  },
  database: {
    host: process.env.DB_HOST || 'localhost',
    port: parseInt(process.env.DB_PORT) || 3306,
    user: process.env.DB_USER || 'root',
    password: process.env.DB_PASSWORD || '1234',
    database: process.env.DB_NAME || 'laboratorio_virtual',
    reconnectDelay: parseInt(process.env.DB_RECONNECT_DELAY) || 5000
  },
  websocket: {
    port: parseInt(process.env.WS_PORT) || 8080
  },
  variables: {
    adcBaseId: parseInt(process.env.ADC_BASE_ID) || 10,
    dinBaseId: parseInt(process.env.DIN_BASE_ID) || 18
  }
};

// Timestamp inicial para cálculos relativos
let startTime = null;
let frameCount = 0;
let errorCount = 0;
let wsServer = null;
let doutPollingTimer = null;
let lastDoutValues = { 0: -1, 1: -1, 2: -1, 3: -1 }; // Últimos valores conocidos de DOUT0-3

// Instancias
const serialListener = new SerialListener(
  config.serial.port,
  config.serial.baudRate,
  config.serial.reconnectDelay
);

const db = new DatabaseConnection(config.database);

/**
 * Obtiene el tiempo relativo desde el inicio del sistema
 * @returns {number} Tiempo en milisegundos desde startTime
 */
function getRelativeTime() {
  if (startTime === null) {
    startTime = Date.now();
    console.log('[App] Timestamp inicial establecido');
  }
  return Date.now() - startTime;
}

/**
 * Consulta la BD y envía comandos al microcontrolador si hay cambios en DOUT
 */
async function pollAndUpdateDout() {
  try {
    let mask = 0;
    let hasChanges = false;

    // Consultar últimos valores de DOUT0-3 (refIds 4-7)
    for (let channel = 0; channel < 4; channel++) {
      const refId = IntProcesoRefs.DOUT_BASE_ID + channel;
      const latest = await IntProcesoRefs.getLatestRefValue(refId);
      
      if (latest) {
        const newValue = latest.valor;
        
        // Si hay cambio, actualizar y marcar flag
        if (lastDoutValues[channel] !== newValue) {
          lastDoutValues[channel] = newValue;
          hasChanges = true;
          console.log(`[DOUT] DOUT${channel} cambió a ${newValue}`);
        }
        
        // Construir máscara (bit 0 = DOUT0, bit 1 = DOUT1, etc.)
        if (newValue === 1) {
          mask |= (1 << channel);
        }
      }
    }

    // Solo enviar comando si hay cambios
    if (hasChanges && serialListener.isOpen()) {
      const command = setLedMask(mask);
      serialListener.sendCommand(command);
      console.log(`[DOUT] Comando SET_LED_MASK enviado: 0x${mask.toString(16).padStart(2, '0').toUpperCase()} (binario: ${mask.toString(2).padStart(4, '0')})`);
    }
  } catch (error) {
    console.error('[DOUT] Error al consultar/actualizar DOUT:', error.message);
  }
}

/**
 * Maneja la recepción de una trama
 */
async function handleFrame(parsedData) {
  // Calcular tiempo relativo en milisegundos
  const relativeTime = getRelativeTime();

  // Insertar datos en la base de datos
  const success = await insertFrameData(db, parsedData, relativeTime, config.variables);

  if (success) {
    frameCount++;
    
    // Log cada 50 tramas
    if (frameCount % 50 === 0) {
      console.log(`[App] ${frameCount} tramas guardadas | Tiempo: ${relativeTime}ms`);
      console.log(`[App] ${formatDataForLog(parsedData)}`);
    }
  } else {
    errorCount++;
    console.error(`[App] Error al guardar trama #${frameCount + errorCount}`);
  }
}

/**
 * Inicializa la aplicación
 */
async function initialize() {
  console.log('═══════════════════════════════════════════════════════════');
  console.log('  Sistema de Adquisición de Datos - Microcontrolador');
  console.log('═══════════════════════════════════════════════════════════');
  console.log('');
  console.log('Configuración:');
  console.log(`  Serial:   ${config.serial.port} @ ${config.serial.baudRate} baud`);
  console.log(`  Database: ${config.database.database}@${config.database.host}:${config.database.port}`);
  console.log(`  WebSocket: ws://localhost:${config.websocket.port}`);
  console.log(`  Variables: ADC=${config.variables.adcBaseId}-${config.variables.adcBaseId + 7}, DIN=${config.variables.dinBaseId}-${config.variables.dinBaseId + 3}`);
  console.log('');

  console.log('[App] Limpiando tabla int_proceso_vars_data...');
  await IntProcesoData.clearVarsData();
  console.log('[App] Tabla int_proceso_vars_data vaciada');

  // Iniciar servidor WebSocket (solo WS, los estáticos los sirve Apache)
  console.log('[App] Iniciando servidor WebSocket...');
  wsServer = createWebSocketServer(config.websocket.port, getRelativeTime);
  if (wsServer.events) {
    wsServer.events.on('listening', ({ port }) => {
      console.log(`[WS] Servidor WebSocket escuchando en ws://localhost:${port}`);
    });
  }
  await wsServer.startServer();

  // Conectar a la base de datos
  console.log('[App] Conectando a la base de datos...');
  await db.connect();

  // Eventos del serial listener
  serialListener.on('connected', () => {
    console.log('[App] Puerto serial conectado');
  });

  serialListener.on('frame', handleFrame);

  // Confirmar cuando el streaming esté activo
  setTimeout(() => {
    if (serialListener.streamingEnabled) {
      console.log('[App] Streaming habilitado - Esperando datos del microcontrolador...');
    }
  }, 1000);

  serialListener.on('error', (error) => {
    console.error('[App] Error en serial:', error.message);
  });

  serialListener.on('parseError', (error) => {
    console.error('[App] Error al parsear trama:', error.message);
  });

  // Eventos de base de datos
  db.on('error', (error) => {
    console.error('[App] Error en base de datos:', error.message);
  });

  // Abrir puerto serial
  console.log('[App] Abriendo puerto serial...');
  await serialListener.open();

  // Iniciar polling de DOUT cada 500ms
  const DOUT_POLL_INTERVAL = parseInt(process.env.DOUT_POLL_INTERVAL_MS) || 500;
  console.log(`[App] Iniciando polling de DOUT cada ${DOUT_POLL_INTERVAL}ms...`);
  doutPollingTimer = setInterval(pollAndUpdateDout, DOUT_POLL_INTERVAL);

  // Mostrar estadísticas cada 30 segundos
  setInterval(() => {
    if (frameCount > 0 || errorCount > 0) {
      const successRate = ((frameCount / (frameCount + errorCount)) * 100).toFixed(2);
      console.log(`[Stats] Tramas: ${frameCount} | Errores: ${errorCount} | Tasa de éxito: ${successRate}%`);
    }
  }, 30000);
}

/**
 * Cierre graceful de la aplicación
 */
async function shutdown() {
  console.log('');
  console.log('[App] Cerrando aplicación...');
  
  // Detener polling de DOUT
  if (doutPollingTimer) {
    clearInterval(doutPollingTimer);
    doutPollingTimer = null;
  }
  
  // Deshabilitar streaming antes de cerrar y esperar ACK
  if (serialListener.streamingEnabled) {
    console.log('[App] Esperando confirmación del microcontrolador...');
    try {
      await serialListener.disableStreaming();
      console.log('[App] Confirmación recibida');
    } catch (error) {
      console.warn('[App] No se recibió confirmación, continuando cierre...');
    }
  }
  
  await serialListener.close();
  await db.close();
  if (wsServer) {
    await wsServer.stopServer();
  }
  
  console.log(`[App] Resumen final: ${frameCount} tramas procesadas, ${errorCount} errores`);
  console.log('[App] Aplicación cerrada correctamente');
  process.exit(0);
}

// Manejo de señales de terminación
process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);

// Manejo de errores no capturados
process.on('uncaughtException', (error) => {
  console.error('[App] Error no capturado:', error);
  shutdown();
});

process.on('unhandledRejection', (reason, promise) => {
  console.error('[App] Promesa rechazada no manejada:', reason);
});

// Iniciar aplicación
initialize().catch((error) => {
  console.error('[App] Error al inicializar:', error);
  process.exit(1);
});
