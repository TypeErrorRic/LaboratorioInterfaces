require('dotenv').config();
const SerialListener = require('./serialListener');
const DatabaseConnection = require('./dbConnection');
const { insertFrameData, formatDataForLog } = require('./dataInserter');
const { createWebSocketServer } = require('./wsServer');
const IntProcesoData = require('./api/IntProcesoData');
const express = require('express');
const path = require('path');

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
let httpServer = null;

// Instancias
const serialListener = new SerialListener(
  config.serial.port,
  config.serial.baudRate,
  config.serial.reconnectDelay
);

const db = new DatabaseConnection(config.database);

/**
 * Maneja la recepción de una trama
 */
async function handleFrame(parsedData) {
  // Inicializar timestamp de referencia en la primera trama
  if (startTime === null) {
    startTime = Date.now();
    console.log('[App] Timestamp inicial establecido');
  }

  // Calcular tiempo relativo en milisegundos
  const relativeTime = Date.now() - startTime;

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

  // Servir archivos est?ticos (index.html y web/*) en el mismo puerto usando Express
  const app = express();
  const staticDir = __dirname;

  app.use(express.static(staticDir));
  app.use('/web', express.static(path.join(staticDir, "web")));

  app.get("/", (_req, res) => {
    res.sendFile(path.join(staticDir, "index.html"));
  });

  httpServer = app.listen(config.websocket.port, () => {
    console.log(`[HTTP] Servidor disponible en http://localhost:${config.websocket.port}`);
  });

  // Iniciar servidor WebSocket
  console.log('[App] Iniciando servidor WebSocket...');
  wsServer = createWebSocketServer(httpServer);
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
  
  // Deshabilitar streaming antes de cerrar
  if (serialListener.streamingEnabled) {
    serialListener.disableStreaming();
    await new Promise(resolve => setTimeout(resolve, 200));
  }
  
  await serialListener.close();
  await db.close();
  if (wsServer) {
    await wsServer.stopServer();
  }
  if (httpServer) {
    await new Promise(resolve => httpServer.close(() => resolve()));
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
