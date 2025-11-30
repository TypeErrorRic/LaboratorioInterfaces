require('dotenv').config();
const mysql = require('mysql2/promise');

// Database configuration from environment (fallbacks mirror dbConnection.js)
const DB_CONFIG = {
  host: process.env.DB_HOST || 'localhost',
  port: parseInt(process.env.DB_PORT, 10) || 3306,
  user: process.env.DB_USER || 'root',
  password: process.env.DB_PASSWORD || '1234',
  database: process.env.DB_NAME || 'laboratorio_virtual',
  waitForConnections: true,
  connectionLimit: 10,
  queueLimit: 0
};

// Analog variable ID range (ADC0-ADC7)
const ANALOG_BASE_ID = parseInt(process.env.ADC_BASE_ID, 10) || 10;
const ANALOG_MAX_ID = ANALOG_BASE_ID + 7;

const pool = mysql.createPool(DB_CONFIG);

// Valida que el varId caiga en el rango analogico permitido
function validateAnalogVarId(varId) {
  const numericId = parseInt(varId, 10);
  if (Number.isNaN(numericId) || numericId < ANALOG_BASE_ID || numericId > ANALOG_MAX_ID)
    throw new Error(`varId ${varId} is outside analog range ${ANALOG_BASE_ID}-${ANALOG_MAX_ID}`);
  return numericId;
}

/**
 * Returns all records newer than a given id for the requested analog variables.
 * Ordered by ascending id so consumers puedan emitir en orden de llegada.
 * @param {number} lastId id a partir del cual traer datos (exclusivo)
 * @param {Array<number>} varIds lista de varIds analogicos
 * @param {number} limit limite maximo de filas a devolver
 */
async function getAnalogDataAfterId(lastId = 0, varIds = [], limit = 2000) {
  const uniqueIds = [...new Set(varIds.map(validateAnalogVarId))];
  if (uniqueIds.length === 0) return [];

  const cappedLimit = Math.max(1, Math.min(limit, 5000));
  const placeholders = uniqueIds.map(() => '?').join(', ');

  const [rows] = await pool.query(
    `SELECT id, int_proceso_vars_id, valor, tiempo, fecha, hora
     FROM int_proceso_vars_data
     WHERE int_proceso_vars_id IN (${placeholders})
       AND id > ?
     ORDER BY id ASC
     LIMIT ?`,
    [...uniqueIds, lastId, cappedLimit]
  );

  return rows;
}

/**
 * Closes the underlying pool when the DAO is no longer needed.
 */
async function close() {
  await pool.end();
}

module.exports = {
  getAnalogDataAfterId,
  close
};
