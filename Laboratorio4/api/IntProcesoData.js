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

/**
 * Validates that the requested varId belongs to the analog range.
 */
function validateAnalogVarId(varId) {
  const numericId = parseInt(varId, 10);
  if (Number.isNaN(numericId) || numericId < ANALOG_BASE_ID || numericId > ANALOG_MAX_ID)
    throw new Error(`varId ${varId} is outside analog range ${ANALOG_BASE_ID}-${ANALOG_MAX_ID}`);
  return numericId;
}

/**
 * Returns the latest N records for a specific analog variable.
 * Results are ordered by descending id (newest first).
 */
async function getAnalogDataByVarId(varId, limit = 100) {
  const analogId = validateAnalogVarId(varId);
  const cappedLimit = Math.max(1, Math.min(limit, 1000));

  const [rows] = await pool.execute(
    `SELECT id, int_proceso_vars_id, valor, tiempo, fecha, hora
     FROM int_proceso_vars_data
     WHERE int_proceso_vars_id = ?
     ORDER BY id DESC
     LIMIT ?`,
    [analogId, cappedLimit]
  );

  return rows;
}

/**
 * Returns the latest record for a specific analog variable.
 */
async function getLatestAnalogData(varId) {
  const rows = await getAnalogDataByVarId(varId, 1);
  return rows[0] || null;
}

/**
 * Returns the latest record for each requested analog variable.
 * @param {Array<number>} varIds Array of analog varIds (e.g. 10-17)
 */
async function getLatestAnalogDataForVars(varIds) {
  const uniqueIds = [...new Set(varIds.map(validateAnalogVarId))];
  if (uniqueIds.length === 0) return [];

  // Query all requested vars and pick the latest per id
  const placeholders = uniqueIds.map(() => '?').join(', ');
  const [rows] = await pool.query(
    `SELECT id, int_proceso_vars_id, valor, tiempo, fecha, hora
     FROM int_proceso_vars_data
     WHERE int_proceso_vars_id IN (${placeholders})
     ORDER BY id DESC`,
    uniqueIds
  );

  const latestById = new Map();
  for (const row of rows) {
    if (!latestById.has(row.int_proceso_vars_id))
      latestById.set(row.int_proceso_vars_id, row);
  }

  return Array.from(latestById.values());
}

/**
 * Closes the underlying pool when the DAO is no longer needed.
 */
async function close() {
  await pool.end();
}

module.exports = {
  getAnalogDataByVarId,
  getLatestAnalogData,
  getLatestAnalogDataForVars,
  close
};
