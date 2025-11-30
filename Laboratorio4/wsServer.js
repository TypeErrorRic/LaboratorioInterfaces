require('dotenv').config();
const { EventEmitter } = require('events');
const WebSocket = require('ws');
const IntProcesoData = require('./api/IntProcesoData');

// Configuracion base del WS y de las variables a consultar
const DEFAULT_WS_PORT = parseInt(process.env.WS_PORT, 10) || 8080;              // Puerto WS (o HTTP host)
const POLL_INTERVAL_MS = parseInt(process.env.WS_POLL_INTERVAL_MS, 10) || 500; // Intervalo de polling a BD
const ANALOG_BASE_ID = parseInt(process.env.ADC_BASE_ID, 10) || 10;             // ID inicial de canales analogicos
const ANALOG_CHANNELS = 8;                                                      // Numero de canales analogicos
const analogVarIds = Array.from({ length: ANALOG_CHANNELS }, (_, i) => ANALOG_BASE_ID + i);

/**
 * Crea un servidor WebSocket con manejo de DB (polling) y helpers de cierre.
 * @param {number|import('http').Server} portOrServer Puerto o servidor HTTP ya creado.
 */
function createWebSocketServer(portOrServer = DEFAULT_WS_PORT) {
  let listenPort = null;
  let options = null;

  if (typeof portOrServer === 'number') {
    listenPort = portOrServer;
    options = { port: portOrServer };
  } else if (portOrServer && typeof portOrServer.on === 'function') {
    options = { server: portOrServer };
  } else {
    throw new Error('Par\u00e1metro inv\u00e1lido para crear el WebSocket');
  }

  const wss = new WebSocket.Server(options);
  const clients = new Set();
  const wsEvents = new EventEmitter();

  let pollTimer = null;
  let pollInFlight = false;
  let lastBroadcastId = 0;

  // Eventos del servidor
  // Emite 'listening' tanto si arranca solo como si cuelga de un server HTTP
  const emitListening = () => {
    const addr = typeof wss.address === 'function' ? wss.address() : null;
    const port = listenPort || (addr && addr.port);
    wsEvents.emit('listening', { port });
  };

  wss.on('listening', emitListening);
  if (options.server && typeof options.server.on === 'function') {
    options.server.on('listening', emitListening);
  }
  wss.on('error', (error) => wsEvents.emit('server_error', { error }));
  wss.on('close', () => wsEvents.emit('server_closed'));

  // Manejo de conexiones entrantes
  wss.on('connection', (socket) => {
    clients.add(socket);
    wsEvents.emit('connection', { size: clients.size });
    console.log(`[WS] Cliente conectado (${clients.size} activos)`);

    socket.on('message', (data) => {
      wsEvents.emit('client_message', { data });

      // Responder a pings simples para pruebas manuales
      try {
        const msg = JSON.parse(data.toString());
        if (msg && msg.type === 'ping') {
          socket.send(JSON.stringify({ type: 'pong', ts: Date.now() }));
        }
      } catch {
        // Ignorar mensajes no JSON
      }
    });
    socket.on('close', () => handleClose(socket));
    socket.on('error', (err) => handleError(err, socket));
  });

  function handleClose(socket) {
    if (clients.delete(socket)) {
      wsEvents.emit('disconnect', { size: clients.size });
    }
  }

  function handleError(err, socket) {
    wsEvents.emit('client_error', { error: err });
    handleClose(socket);
  }

  /**
   * Difunde un mensaje a todos los clientes.
   */
  function broadcast(message) {
    const payload = typeof message === 'string' ? message : JSON.stringify(message);
    let delivered = 0;

    for (const socket of clients) {
      if (socket.readyState === WebSocket.OPEN) {
        socket.send(payload);
        delivered++;
      }
    }

    wsEvents.emit('broadcast', { delivered });
    return delivered;
  }

  /**
   * Envía filas de int_proceso_vars_data a los clientes.
   */
  function broadcastVarsData(rows) {
    if (!Array.isArray(rows) || rows.length === 0) return 0;
    return broadcast({ type: 'vars_data', data: rows });
  }

  /**
   * Inicia un polling periodico a la BD y publica nuevas filas.
   * Se ejecuta cada intervalMs y solo emite si detecta IDs mayores a lastBroadcastId.
   */
  async function startVarsDataPolling(intervalMs = POLL_INTERVAL_MS) {
    if (pollTimer) return pollTimer;

    const poll = async () => {
      if (pollInFlight) return; // Evitar reentradas si la consulta anterior sigue viva
      pollInFlight = true;

      try {
        // Traer todas las filas nuevas (id > lastBroadcastId) para los vars solicitados
        const rows = await IntProcesoData.getAnalogDataAfterId(lastBroadcastId, analogVarIds);

        if (rows.length > 0) {
          const maxId = rows[rows.length - 1].id;
          lastBroadcastId = maxId;
          broadcastVarsData(rows);
        }
      } catch (err) {
        wsEvents.emit('polling_error', { error: err });
      } finally {
        pollInFlight = false;
      }
    };

    await poll();
    pollTimer = setInterval(poll, intervalMs);
    wsEvents.emit('polling_started', { intervalMs });
    return pollTimer;
  }

  /**
   * Detiene el polling y cierra el pool de la BD.
   */
  async function stopVarsDataPolling() {
    if (pollTimer) {
      clearInterval(pollTimer);
      pollTimer = null;
    }

    pollInFlight = false;
    lastBroadcastId = 0;

    await IntProcesoData.close().catch((err) =>
      wsEvents.emit('pool_close_error', { error: err })
    );

    wsEvents.emit('polling_stopped');
  }

  /**
   * Arranca el ciclo completo (polling + broadcast).
   */
  async function startServer(intervalMs = POLL_INTERVAL_MS) {
    await startVarsDataPolling(intervalMs);
    return wss;
  }

  /**
   * Cierra polling, clientes y el servidor WebSocket.
   */
  async function stopServer() {
    await stopVarsDataPolling();

    for (const socket of clients) {
      try {
        if (socket.readyState === WebSocket.OPEN) {
          socket.close();
        } else {
          socket.terminate();
        }
      } catch {
        // Ignorar errores al cerrar sockets individuales
      }
    }

    clients.clear();

    await new Promise((resolve) => wss.close(() => resolve()));
  }

  return {
    server: wss,
    events: wsEvents,
    startServer,
    stopServer
  };
}

module.exports = {
  createWebSocketServer
};
