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
const ANALOG_VAR_IDS = Array.from({ length: ANALOG_MAX_ID - ANALOG_BASE_ID + 1 }, (_, i) => ANALOG_BASE_ID + i);

// Digital variable ID range (DIN0-DIN3)
const DIGITAL_BASE_ID = parseInt(process.env.DIN_BASE_ID, 10) || 18;
const DIGITAL_MAX_ID = DIGITAL_BASE_ID + 3;
const DIGITAL_VAR_IDS = Array.from({ length: DIGITAL_MAX_ID - DIGITAL_BASE_ID + 1 }, (_, i) => DIGITAL_BASE_ID + i);

const ALL_VAR_IDS = [...ANALOG_VAR_IDS, ...DIGITAL_VAR_IDS];

const pool = mysql.createPool(DB_CONFIG);

function normalizeVarIds(varIds, fallback) {
  const ids = Array.isArray(varIds) && varIds.length > 0 ? varIds : fallback;
  return [...new Set(ids.map((id) => parseInt(id, 10)).filter((n) => !Number.isNaN(n)))];
}

/**
 * Generic fetch for any allowed var ids (analog + digital).
 * @param {number} lastId exclusive lower bound for id
 * @param {Array<number>} varIds ids to include
 * @param {number} limit maximum rows to return
 */
async function getVarsDataAfterId(lastId = 0, varIds = [], limit = 2000) {
  const uniqueIds = normalizeVarIds(varIds, ALL_VAR_IDS);
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

/**
 * Borra todas las filas de int_proceso_vars_data.
 */
async function clearVarsData() {
  await pool.query('TRUNCATE TABLE int_proceso_vars_data');
}

module.exports = {
  getVarsDataAfterId,
  clearVarsData,
  close
};
