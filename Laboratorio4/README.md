# Sistema de Adquisición de Datos - Microcontrolador

Aplicación Node.js para comunicación serial con microcontrolador, procesamiento de datos en tiempo real y almacenamiento en base de datos MySQL.

## 📋 Descripción

Sistema completo de adquisición y gestión de datos desde microcontrolador Arduino Uno. Implementa comunicación bidireccional, protocolo binario optimizado y persistencia estructurada de variables analógicas y digitales.

### Características Principales

✅ **Comunicación Serial** - Gestión completa del puerto con eventos y buffer inteligente  
✅ **Protocolo Binario** - Parser optimizado para tramas de 20 bytes  
✅ **Procesamiento en Tiempo Real** - Conversión y validación de datos analógicos/digitales  
✅ **Persistencia MySQL** - Pool de conexiones con transacciones y batch inserts  
✅ **Alta Disponibilidad** - Reconexión automática ante fallos de hardware o red  
✅ **Control del Microcontrolador** - Comandos para configuración y control de streaming  
✅ **Monitoreo** - Estadísticas y logs detallados de operación

## 🔧 Protocolo Serial

### Protocolo de Comandos

El microcontrolador utiliza un protocolo de comandos binario para control:

**Estructura de comando**:
```
[0x55][0xAA][CMD][LEN][PAYLOAD...][CHK]
```

**Estructura de respuesta**:
```
[0x55][0xAB][STATUS][CMD][LEN][PAYLOAD...][CHK]
```

**Comandos disponibles**:
- `0x05`: **Streaming Enable** - Habilita/deshabilita transmisión continua
- `0x01`: Set LED mask
- `0x02`: Get DIP state
- `0x03/0x08`: Set sample periods
- `0x06`: Snapshot (single frame)
- `0x07`: Get info

**Inicialización**: La aplicación envía automáticamente el comando `0x05` (Streaming Enable) al conectarse para iniciar la transmisión de datos.

### Estructura de Trama (20 bytes)

```
[0x7A][0x7B][DIGITAL][AN0_L][AN0_H]...[AN7_H][0x7C]
```

- **Header**: `0x7A 0x7B` (2 bytes)
- **Digital**: 1 byte
  - Nibble alto (bits 7-4): DIP switches (DIP3..DIP0)
  - Nibble bajo (bits 3-0): LEDs (LED3..LED0)
- **Analógicos**: 8 canales × 2 bytes Little Endian (AN0-AN7)
  - AN0-AN3: Lecturas directas de A0-A3
  - AN4-AN7: Lecturas divididas /2 (AN0/2, AN1/2, AN2/2, AN3/2)
- **Tail**: `0x7C` (1 byte)

### Configuración Serial

- **Baudrate**: 115200
- **Data bits**: 8
- **Parity**: None
- **Stop bits**: 1

## 💾 Base de Datos

### Tabla: `int_proceso_vars_data`

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | INT AUTO_INCREMENT | Clave primaria |
| `int_proceso_vars_id` | INT | ID de variable (FK) |
| `valor` | INT | Valor del sensor |
| `tiempo` | INT | Timestamp relativo (ms) |
| `fecha` | DATE | Fecha de inserción |
| `hora` | TIME | Hora de inserción |

### Mapeo de Variables

| Variable | ID | Descripción |
|----------|----|----|
| ADC0 | 10 | Canal analógico 0 |
| ADC1 | 11 | Canal analógico 1 |
| ADC2 | 12 | Canal analógico 2 |
| ADC3 | 13 | Canal analógico 3 |
| ADC4 | 14 | Canal analógico 4 (AN0/2) |
| ADC5 | 15 | Canal analógico 5 (AN1/2) |
| ADC6 | 16 | Canal analógico 6 (AN2/2) |
| ADC7 | 17 | Canal analógico 7 (AN3/2) |
| DIN0 | 18 | Entrada digital 0 |
| DIN1 | 19 | Entrada digital 1 |
| DIN2 | 20 | Entrada digital 2 |
| DIN3 | 21 | Entrada digital 3 |

**Total**: 12 registros por trama

## 🚀 Instalación

### Prerrequisitos

- Node.js 16+ 
- MySQL/MariaDB 10.4+
- Base de datos `laboratorio_virtual` configurada
- Arduino conectado por USB

### Pasos

1. **Clonar o navegar al directorio**
```bash
cd Laboratorio4
```

2. **Instalar dependencias**
```bash
npm install
```

3. **Configurar variables de entorno**
```bash
# Copiar archivo de ejemplo
cp .env.example .env

# Editar .env con tus valores
notepad .env
```

4. **Configurar `.env`**
```env
# Puerto Serial
SERIAL_PORT=COM3
SERIAL_BAUDRATE=115200

# Base de Datos
DB_HOST=localhost
DB_PORT=3306
DB_USER=root
DB_PASSWORD=tu_password
DB_NAME=laboratorio_virtual

# Reconexión
SERIAL_RECONNECT_DELAY=3000
DB_RECONNECT_DELAY=5000

# IDs de Variables
ADC_BASE_ID=10
DIN_BASE_ID=18
```

## ▶️ Ejecución

### Modo normal
```bash
npm start
```

### Modo desarrollo (con auto-restart)
```bash
npm run dev
```

## 📁 Estructura del Proyecto

```
Laboratorio4/
├── package.json              # Configuración del proyecto y dependencias
├── index.js                  # Orquestador principal del sistema
├── serialListener.js         # Gestión de comunicación serial
├── frameParser.js            # Decodificador de protocolo binario
├── commandProtocol.js        # API de comandos del microcontrolador
├── dbConnection.js           # Capa de acceso a datos MySQL
├── dataInserter.js           # Mapeo y persistencia de variables
├── .env.example              # Plantilla de configuración
├── .env                      # Configuración del entorno
├── .gitignore               # Exclusiones de control de versiones
└── README.md                # Documentación del proyecto
```

## 🔍 Módulos

### `index.js`
- Orquestador principal
- Inicialización de componentes
- Manejo de señales de terminación (SIGINT, SIGTERM)
- Estadísticas cada 30 segundos

### `serialListener.js`
- Apertura del puerto serial
- Buffer acumulativo para bytes recibidos
- Detección y extracción de tramas completas
- **Envío de comando Streaming Enable (0x05) al conectar**
- Reconexión automática en caso de desconexión
- Métodos: `enableStreaming()`, `disableStreaming()`, `sendCommand()`
- Emisión de eventos: `connected`, `frame`, `error`, `disconnected`

### `commandProtocol.js`
- Construcción de comandos según protocolo 0x55 0xAA
- Cálculo de checksum XOR
- Parsing de respuestas del microcontrolador
- Funciones: `streamingEnable()`, `setLedMask()`, `setTsampleAdc()`, etc.
- Validación de respuestas con verificación de checksum

### `frameParser.js`
- Búsqueda de headers (`0x7A 0x7B`) en buffer
- Validación de estructura (20 bytes, tail `0x7C`)
- Extracción de byte digital (DIP + LEDs)
- Parsing de 8 canales ADC (Little Endian)
- Separación de bits DIN0-DIN3

### `dbConnection.js`
- Pool de conexiones MySQL
- Inserción individual y batch (transacciones)
- Reconexión automática en errores de red
- Manejo de errores `PROTOCOL_CONNECTION_LOST`

### `dataInserter.js`
- Conversión de datos parseados a registros
- Inserción de 12 variables por trama
- Cálculo de timestamp relativo
- Funciones de logging y estadísticas

## 🛡️ Manejo de Errores

### Reconexión Automática

**Puerto Serial**:
- Detecta desconexiones (`close` event)
- Reintenta cada 3 segundos (configurable)
- Limpia buffer en cada reconexión

**Base de Datos**:
- Detecta errores de conexión
- Reintenta cada 5 segundos (configurable)
- Usa pool para conexiones persistentes

### Validación de Tramas

- Header y tail obligatorios
- Tamaño exacto de 20 bytes
- Limpieza de buffer si crece >1000 bytes

## 📊 Logs y Monitoreo

### Salida de consola

```
═══════════════════════════════════════════════════════════
  Laboratorio 4 - Recepción de Datos del Microcontrolador
═══════════════════════════════════════════════════════════

Configuración:
  Serial:   COM3 @ 115200 baud
  Database: laboratorio_virtual@localhost:3306
  Variables: ADC=10-17, DIN=18-21

[App] Conectando a la base de datos...
[DB] Conectado a MySQL: laboratorio_virtual@localhost
[App] Abriendo puerto serial...
[Serial] Puerto abierto: COM3 @ 115200 baud
[App] Puerto serial conectado
[Serial] Enviando comando para habilitar streaming...
[App] Streaming habilitado - Esperando datos del microcontrolador...
[App] Timestamp inicial establecido
[Serial] 100 tramas procesadas
[App] 50 tramas guardadas | Tiempo: 10245ms
[App] ADC: [AN0=512, AN1=768, ...] | Digital: [DIN0=1, DIN1=0, ...]
[Stats] Tramas: 150 | Errores: 0 | Tasa de éxito: 100.00%
```

## 🧪 Pruebas

### Verificar inserción en MySQL

```sql
SELECT 
  v.nombre,
  d.valor,
  d.tiempo,
  d.fecha,
  d.hora
FROM int_proceso_vars_data d
INNER JOIN int_proceso_vars v ON d.int_proceso_vars_id = v.id
WHERE d.fecha = CURDATE()
ORDER BY d.id DESC
LIMIT 50;
```

### Verificar puerto serial disponible

```bash
# Windows PowerShell
[System.IO.Ports.SerialPort]::GetPortNames()

# Node.js
npx @serialport/list
```

## 🐛 Solución de Problemas

### Error: "Error al abrir puerto"
- Verificar que el puerto COM sea correcto
- Cerrar otras aplicaciones que usen el puerto (Arduino IDE, monitor serial)
- Revisar permisos de acceso al puerto

### Error: "ECONNREFUSED" (MySQL)
- Verificar que MySQL esté corriendo
- Comprobar credenciales en `.env`
- Verificar que la base de datos `laboratorio_virtual` exista

### No se reciben tramas
- Verificar que el microcontrolador esté transmitiendo (LED TX parpadeando)
- Comprobar baudrate (debe ser 115200)
- Revisar logs para detectar errores de parsing

### Buffer crece demasiado
- Posible ruido en la línea serial
- Verificar cable USB
- El sistema limpia automáticamente buffers >1000 bytes

## 📝 Notas Técnicas

- **Timestamp relativo**: Se inicializa en la primera trama recibida y se calcula como `Date.now() - startTime`
- **Transacciones**: Los 12 registros de cada trama se insertan en una transacción para garantizar atomicidad
- **Little Endian**: Los valores ADC se reciben con byte bajo primero: `valor = low | (high << 8)`
- **Pool de conexiones**: Permite hasta 10 conexiones simultáneas para manejar picos de carga

## 📚 Referencias

- Protocolo implementado según `microcontrolador/src/main.cpp`
- Base de datos definida en `src/main/java/com/db/laboratorio_virtual.sql`
- Lógica basada en `SerialProtocolRunner.java`

## 👤 Autor

Laboratorio de Interfaces - Universidad

## 📄 Licencia

ISC
