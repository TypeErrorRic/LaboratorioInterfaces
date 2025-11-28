/**
 * Capa de persistencia de datos de sensores
 * Mapea datos del microcontrolador a registros de base de datos
 */

/**
 * Inserta los datos de una trama en la base de datos
 * @param {Object} db - Instancia de DatabaseConnection
 * @param {Object} parsedData - Datos parseados de la trama
 * @param {number} relativeTime - Timestamp relativo en milisegundos
 * @param {Object} config - Configuración con IDs base
 */
async function insertFrameData(db, parsedData, relativeTime, config) {
  const adcBaseId = parseInt(config.adcBaseId) || 10;
  const dinBaseId = parseInt(config.dinBaseId) || 18;
  
  const dataToInsert = [];

  // Insertar 8 canales ADC (AN0-AN7)
  // IDs: 10=ADC0, 11=ADC1, 12=ADC2, 13=ADC3, 14=ADC4, 15=ADC5, 16=ADC6, 17=ADC7
  for (let i = 0; i < 8; i++) {
    dataToInsert.push({
      varId: adcBaseId + i,
      valor: parsedData.adc[i],
      tiempo: relativeTime
    });
  }

  // Insertar 4 entradas digitales (DIN0-DIN3)
  // IDs: 18=DIN0, 19=DIN1, 20=DIN2, 21=DIN3
  for (let i = 0; i < 4; i++) {
    dataToInsert.push({
      varId: dinBaseId + i,
      valor: parsedData.din[i],
      tiempo: relativeTime
    });
  }

  // Insertar en batch (transacción) para mejor rendimiento
  try {
    const insertedCount = await db.insertBatch(dataToInsert);
    return insertedCount === 12; // Éxito si se insertaron las 12 variables
  } catch (error) {
    console.error('[DataInserter] Error al insertar datos:', error.message);
    return false;
  }
}

/**
 * Formatea los datos para logging/debug
 * @param {Object} parsedData - Datos parseados
 * @returns {string} String formateado
 */
function formatDataForLog(parsedData) {
  const adcStr = parsedData.adc.map((v, i) => `AN${i}=${v}`).join(', ');
  const dinStr = parsedData.din.map((v, i) => `DIN${i}=${v}`).join(', ');
  return `ADC: [${adcStr}] | Digital: [${dinStr}]`;
}

/**
 * Calcula estadísticas de los datos
 * @param {Object} parsedData - Datos parseados
 * @returns {Object} Estadísticas
 */
function calculateStats(parsedData) {
  const adcValues = parsedData.adc;
  const sum = adcValues.reduce((a, b) => a + b, 0);
  const avg = sum / adcValues.length;
  const max = Math.max(...adcValues);
  const min = Math.min(...adcValues);
  
  return {
    adcAvg: Math.round(avg),
    adcMax: max,
    adcMin: min,
    digitalActive: parsedData.din.filter(d => d === 1).length
  };
}

module.exports = {
  insertFrameData,
  formatDataForLog,
  calculateStats
};
