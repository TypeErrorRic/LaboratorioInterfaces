const mysql = require('mysql2/promise');
const EventEmitter = require('events');

/**
 * Gestor de conexiones a base de datos MySQL
 * Implementa pool de conexiones, operaciones batch y recuperación ante fallos
 */
class DatabaseConnection extends EventEmitter {
  constructor(config) {
    super();
    this.config = config;
    this.pool = null;
    this.reconnectDelay = config.reconnectDelay || 5000;
    this.isConnecting = false;
  }

  /**
   * Inicializa el pool de conexiones
   */
  async connect() {
    if (this.isConnecting) return;
    this.isConnecting = true;

    try {
      this.pool = mysql.createPool({
        host: this.config.host,
        port: this.config.port,
        user: this.config.user,
        password: this.config.password,
        database: this.config.database,
        waitForConnections: true,
        connectionLimit: 10,
        queueLimit: 0
      });

      // Verificar conexión
      const connection = await this.pool.getConnection();
      console.log(`[DB] Conectado a MySQL: ${this.config.database}@${this.config.host}`);
      connection.release();
      
      this.isConnecting = false;
      this.emit('connected');
      return true;
    } catch (error) {
      console.error('[DB] Error al conectar:', error.message);
      this.isConnecting = false;
      this.emit('error', error);
      this.scheduleReconnect();
      return false;
    }
  }

  /**
   * Programa un intento de reconexión
   */
  scheduleReconnect() {
    console.log(`[DB] Reintentando conexión en ${this.reconnectDelay / 1000}s...`);
    setTimeout(() => {
      this.connect();
    }, this.reconnectDelay);
  }

  /**
   * Ejecuta una consulta INSERT para datos de sensores
   * @param {number} varId - ID de la variable (10-21)
   * @param {number} valor - Valor a insertar
   * @param {number} tiempo - Timestamp relativo en ms
   * @returns {Promise<boolean>}
   */
  async insertSensorData(varId, valor, tiempo) {
    if (!this.pool) {
      console.error('[DB] Pool no inicializado');
      return false;
    }

    try {
      const query = `
        INSERT INTO int_proceso_vars_data 
        (int_proceso_vars_id, valor, tiempo, fecha, hora)
        VALUES (?, ?, ?, CURDATE(), CURTIME())
      `;
      
      await this.pool.execute(query, [varId, valor, tiempo]);
      return true;
    } catch (error) {
      console.error(`[DB] Error al insertar datos (varId=${varId}):`, error.message);
      this.emit('error', error);
      
      // Si es error de conexión, intentar reconectar
      if (error.code === 'PROTOCOL_CONNECTION_LOST' || error.code === 'ECONNREFUSED') {
        this.scheduleReconnect();
      }
      return false;
    }
  }

  /**
   * Ejecuta múltiples inserciones en batch
   * @param {Array<{varId, valor, tiempo}>} dataArray
   * @returns {Promise<number>} Número de inserciones exitosas
   */
  async insertBatch(dataArray) {
    if (!this.pool) {
      console.error('[DB] Pool no inicializado');
      return 0;
    }

    let successCount = 0;
    const connection = await this.pool.getConnection();
    
    try {
      await connection.beginTransaction();

      const query = `
        INSERT INTO int_proceso_vars_data 
        (int_proceso_vars_id, valor, tiempo, fecha, hora)
        VALUES (?, ?, ?, CURDATE(), CURTIME())
      `;

      for (const data of dataArray) {
        await connection.execute(query, [data.varId, data.valor, data.tiempo]);
        successCount++;
      }

      await connection.commit();
      return successCount;
    } catch (error) {
      await connection.rollback();
      console.error('[DB] Error en inserción batch:', error.message);
      this.emit('error', error);
      
      if (error.code === 'PROTOCOL_CONNECTION_LOST' || error.code === 'ECONNREFUSED') {
        this.scheduleReconnect();
      }
      return successCount;
    } finally {
      connection.release();
    }
  }

  /**
   * Cierra el pool de conexiones
   */
  async close() {
    if (this.pool) {
      await this.pool.end();
      console.log('[DB] Conexión cerrada');
    }
  }
}

module.exports = DatabaseConnection;
