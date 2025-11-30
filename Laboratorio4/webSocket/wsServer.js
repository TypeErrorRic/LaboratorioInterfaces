require('dotenv').config();
const WebSocket = require('ws');
const mysql = require('mysql2/promise');

const SERVER_PORT = process.env.WS_PORT || 8080;
const POLL_INTERVAL_MS = parseInt(process.env.WS_POLL_INTERVAL_MS, 10) || 1000;

const DB_CONFIG = {
    host: process.env.DB_HOST || 'localhost',
    port: parseInt(process.env.DB_PORT, 10) || 3306,
    user: process.env.DB_USER || 'root',
    password: process.env.DB_PASSWORD || '',
    database: process.env.DB_NAME || 'laboratorio_virtual',
    waitForConnections: true,
    connectionLimit: 4,
    queueLimit: 0
};

const wss = new WebSocket.Server({port: SERVER_PORT});
const clients = new Set();
let dbPool = null;
let pollTimer = null;
let lastVarsDataId = 0;
let pollInFlight = false;

console.log(`WS: Listening on port ${SERVER_PORT}`);

/**
 * Handles a new client connection and wires event listeners.
 */
function handleConnection(socket)
{
    console.log('WS: New connection');
    clients.add(socket);

    socket.on('message', handleMessage);
    socket.on('close', () => handleClose(socket));
    socket.on('error', err => handleError(err, socket));
}

/**
 * Logs incoming client messages.
 */
function handleMessage(data)
{
    console.log(`WS: From client: ${data}`);
}

/**
 * Cleans up a socket and logs its closure.
 */
function handleClose(socket)
{
    if (clients.delete(socket))
        console.log('WS: Connection closed');
}

/**
 * Logs client errors and closes the socket.
 */
function handleError(err, socket)
{
    console.log(`WS: Client error: ${err.message}`);
    handleClose(socket);
}

wss.on('connection', handleConnection);

/**
 * Starts a periodic query against int_proceso_vars_data and broadcasts new rows.
 */
async function startVarsDataPolling(intervalMs = POLL_INTERVAL_MS)
{
    if (pollTimer)
    {
        console.log('WS: Vars data polling already running');
        return pollTimer;
    }

    if (!dbPool)
        dbPool = mysql.createPool(DB_CONFIG);

    const poll = async () =>
    {
        if (pollInFlight)
            return;

        pollInFlight = true;

        try
        {
            const [rows] = await dbPool.execute(
                `SELECT id, int_proceso_vars_id, valor, tiempo, fecha, hora
                 FROM int_proceso_vars_data
                 WHERE id > ?
                 ORDER BY id ASC
                 LIMIT 200`,
                [lastVarsDataId]
            );

            if (rows.length > 0)
            {
                lastVarsDataId = rows[rows.length - 1].id;
                broadcast(JSON.stringify({ type: 'vars_data', data: rows }));
            }
        }
        catch (err)
        {
            console.log(`WS: Vars data polling error: ${err.message}`);
        }
        finally
        {
            pollInFlight = false;
        }
    };

    // Trigger an immediate query and then keep polling
    await poll();
    pollTimer = setInterval(poll, intervalMs);
    console.log(`WS: Started polling int_proceso_vars_data every ${intervalMs}ms`);
    return pollTimer;
}

/**
 * Stops the polling loop and closes the database pool.
 */
async function stopVarsDataPolling()
{
    if (pollTimer)
    {
        clearInterval(pollTimer);
        pollTimer = null;
    }

    pollInFlight = false;
    lastVarsDataId = 0;

    if (dbPool)
    {
        await dbPool.end();
        dbPool = null;
    }

    console.log('WS: Vars data polling stopped');
}

/**
 * Broadcasts a message to all connected WebSocket clients.
 * Safe to call from other modules.
 */
function broadcast(message)
{
    for (const socket of clients)
        if (socket.readyState === WebSocket.OPEN)
            socket.send(message);
}

if (require.main === module)
    startVarsDataPolling().catch(err => console.log(`WS: Polling could not start: ${err.message}`));

module.exports = { wss, broadcast, startVarsDataPolling, stopVarsDataPolling };
