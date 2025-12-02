require('dotenv').config();
const { EventEmitter } = require('events');
const WebSocket = require('ws');
const IntProcesoData = require('./api/IntProcesoData');
const IntProcesoRefs = require('./api/IntProcesoRefs');

// Configuracion base del WS y de las variables a consultar
const DEFAULT_WS_PORT = parseInt(process.env.WS_PORT, 10) || 8090;              // Puerto WS (o HTTP host)
const POLL_INTERVAL_MS = parseInt(process.env.WS_POLL_INTERVAL_MS, 10) || 500; // Intervalo de polling a BD

/**
 * Crea un servidor WebSocket con manejo de DB (polling) y helpers de cierre.
 * @param {number|import('http').Server} portOrServer Puerto o servidor HTTP ya creado.
 * @param {Function} getRelativeTime Función opcional para obtener tiempo relativo
 */
function createWebSocketServer(portOrServer = DEFAULT_WS_PORT, getRelativeTime = null) {
  let listenPort = null;
  let options = null;

  if (typeof portOrServer === 'number') {
    listenPort = portOrServer;
    options = { port: portOrServer };
  } else if (portOrServer && typeof portOrServer.on === 'function') {
    options = { server: portOrServer };
  } else {
    throw new Error('Parámetro inválido para crear el WebSocket');
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

      // Handle incoming messages
      try {
        const msg = JSON.parse(data.toString());
        
        // Handle ping
        if (msg && msg.type === 'ping') {
          socket.send(JSON.stringify({ type: 'pong', ts: Date.now() }));
        }
        
        // Handle toggle commands (set digital output)
        if (msg && msg.type === 'set_dout') {
          handleSetDout(msg, socket);
        }

        // Handle request for latest DOUT value
        if (msg && msg.type === 'get_latest_dout') {
          handleGetLatestDout(msg, socket);
        }
      } catch (err) {
        console.error('[WS] Error parsing message:', err.message);
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
   * Handle set digital output command from client
   */
  async function handleSetDout(msg, socket) {
    try {
      const { refId, valor } = msg;
      
      // Validate parameters
      if (typeof refId !== 'number' || typeof valor !== 'number') {
        socket.send(JSON.stringify({ 
          type: 'error', 
          message: 'Invalid parameters for set_dout' 
        }));
        return;
      }

      // Calculate relative time using the function from index.js or fallback to Date.now()
      const timestamp = getRelativeTime ? getRelativeTime() : Date.now();

      // Insert into database
      const result = await IntProcesoRefs.insertRefData(refId, valor, timestamp);
      
      // Send success response to client
      socket.send(JSON.stringify({ 
        type: 'set_dout_response', 
        success: true,
        data: result
      }));

      // Broadcast to all clients
      broadcast({
        type: 'dout_changed',
        refId,
        valor,
        tiempo: timestamp
      });

      wsEvents.emit('dout_set', { refId, valor, tiempo: timestamp });
    } catch (error) {
      console.error('[WS] Error setting DOUT:', error.message);
      socket.send(JSON.stringify({ 
        type: 'error', 
        message: 'Failed to set digital output' 
      }));
      wsEvents.emit('dout_error', { error });
    }
  }

  /**
   * Handle get latest DOUT value request from client
   */
  async function handleGetLatestDout(msg, socket) {
    try {
      const { refId } = msg;
      
      // Validate parameters
      if (typeof refId !== 'number') {
        socket.send(JSON.stringify({ 
          type: 'error', 
          message: 'Invalid refId for get_latest_dout' 
        }));
        return;
      }

      // Get latest value from database
      const latestValue = await IntProcesoRefs.getLatestRefValue(refId);
      
      // Send response to client
      socket.send(JSON.stringify({ 
        type: 'latest_dout_response', 
        refId,
        data: latestValue
      }));

      wsEvents.emit('latest_dout_requested', { refId, found: !!latestValue });
    } catch (error) {
      console.error('[WS] Error getting latest DOUT:', error.message);
      socket.send(JSON.stringify({ 
        type: 'error', 
        message: 'Failed to get latest digital output value' 
      }));
      wsEvents.emit('latest_dout_error', { error });
    }
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
        const rows = await IntProcesoData.getVarsDataAfterId(lastBroadcastId);

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
