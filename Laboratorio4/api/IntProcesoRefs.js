require('dotenv').config();
const mysql = require('mysql2/promise');

// Database configuration from environment (same as IntProcesoData.js)
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

// Digital output reference IDs (DOUT0-DOUT3)
const DOUT_BASE_ID = 4; // ID 4 = DOUT0, 5 = DOUT1, 6 = DOUT2, 7 = DOUT3
const DOUT_MAX_ID = 7;
const DOUT_REF_IDS = Array.from({ length: DOUT_MAX_ID - DOUT_BASE_ID + 1 }, (_, i) => DOUT_BASE_ID + i);

const pool = mysql.createPool(DB_CONFIG);

function normalizeRefIds(refIds, fallback) {
  const ids = Array.isArray(refIds) && refIds.length > 0 ? refIds : fallback;
  return [...new Set(ids.map((id) => parseInt(id, 10)).filter((n) => !Number.isNaN(n)))];
}

/**
 * Get reference data after a specific ID
 * @param {number} lastId exclusive lower bound for id
 * @param {Array<number>} refIds ids to include
 * @param {number} limit maximum rows to return
 */
async function getRefsDataAfterId(lastId = 0, refIds = [], limit = 2000) {
  const uniqueIds = normalizeRefIds(refIds, DOUT_REF_IDS);
  if (uniqueIds.length === 0) return [];

  const cappedLimit = Math.max(1, Math.min(limit, 5000));
  const placeholders = uniqueIds.map(() => '?').join(', ');

  const [rows] = await pool.query(
    `SELECT id, int_proceso_refs_id, valor, tiempo, fecha, hora
     FROM int_proceso_refs_data
     WHERE id > ? AND int_proceso_refs_id IN (${placeholders})
     ORDER BY id ASC
     LIMIT ${cappedLimit}`,
    [lastId, ...uniqueIds]
  );

  return rows;
}

/**
 * Insert a new reference value (e.g., DOUT0 toggle)
 * @param {number} refId reference ID (4-7 for DOUT0-DOUT3)
 * @param {number} valor value (0 or 1)
 * @param {number} tiempo timestamp in milliseconds
 */
async function insertRefData(refId, valor, tiempo) {
  const normalizedRefId = parseInt(refId, 10);
  const normalizedValor = parseInt(valor, 10);
  const normalizedTiempo = parseInt(tiempo, 10);

  if (Number.isNaN(normalizedRefId) || Number.isNaN(normalizedValor) || Number.isNaN(normalizedTiempo)) {
    throw new Error('Invalid parameters for insertRefData');
  }

  const [result] = await pool.query(
    `INSERT INTO int_proceso_refs_data 
     (int_proceso_refs_id, valor, tiempo, fecha, hora)
     VALUES (?, ?, ?, CURDATE(), CURTIME())`,
    [normalizedRefId, normalizedValor, normalizedTiempo]
  );

  // Retornar el registro insertado con fecha y hora
  const [inserted] = await pool.query(
    `SELECT id, int_proceso_refs_id, valor, tiempo, fecha, hora
     FROM int_proceso_refs_data
     WHERE id = ?`,
    [result.insertId]
  );

  return inserted[0];
}

/**
 * Get the latest value for a specific reference
 * @param {number} refId reference ID
 */
async function getLatestRefValue(refId) {
  const normalizedRefId = parseInt(refId, 10);
  if (Number.isNaN(normalizedRefId)) {
    throw new Error('Invalid refId');
  }

  const [rows] = await pool.query(
    `SELECT id, int_proceso_refs_id, valor, tiempo, fecha, hora
     FROM int_proceso_refs_data
     WHERE int_proceso_refs_id = ?
     ORDER BY id DESC
     LIMIT 1`,
    [normalizedRefId]
  );

  return rows.length > 0 ? rows[0] : null;
}

/**
 * Close the database pool
 */
async function close() {
  await pool.end();
}

module.exports = {
  getRefsDataAfterId,
  insertRefData,
  getLatestRefValue,
  close,
  DOUT_BASE_ID,
  DOUT_MAX_ID,
  DOUT_REF_IDS
};
