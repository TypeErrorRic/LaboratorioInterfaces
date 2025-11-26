-- phpMyAdmin SQL Dump
-- version 5.2.1
-- https://www.phpmyadmin.net/
--
-- Servidor: 127.0.0.1
-- Tiempo de generación: 26-11-2025 a las 16:26:36
-- Versión del servidor: 10.4.32-MariaDB
-- Versión de PHP: 8.2.12

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Base de datos: `laboratorio_virtual`
--

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `int_proceso`
--

CREATE TABLE `int_proceso` (
  `id` int(10) UNSIGNED NOT NULL,
  `int_proceso_tipo_id` int(10) UNSIGNED NOT NULL,
  `nombre` varchar(255) NOT NULL,
  `descripcion` varchar(500) NOT NULL,
  `tiempo_muestreo` int(11) NOT NULL,
  `tiempo_muestreo_2` int(11) NOT NULL,
  `archivo_especificaciones` varchar(500) NOT NULL,
  `archivo_manual` varchar(500) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

--
-- Volcado de datos para la tabla `int_proceso`
--

INSERT INTO `int_proceso` (`id`, `int_proceso_tipo_id`, `nombre`, `descripcion`, `tiempo_muestreo`, `tiempo_muestreo_2`, `archivo_especificaciones`, `archivo_manual`) VALUES
(1, 1, 'Control de nivel', 'Control de nivel de uno o dos tanques acoplados', 0, 0, 'controlNivel.txt', 'guiaControlNivel.pdf'),
(2, 2, 'Control de temperatura', 'Control de temperatura en un tanque', 1, 0, 'controlTemperatura.txt', 'guiaControlTemperatura.pdf'),
(3, 3, 'Arduino Uno', 'Proceso de lectura de 8 canales analógicos, 4 digitales y generación de 4 salidas digitales.', 50, 100, 'https://docs.arduino.cc/resources/schematics/A000066-schematics.pdf', 'https://docs.arduino.cc/resources/datasheets/A000066-datasheet.pdf');

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `int_proceso_control`
--

CREATE TABLE `int_proceso_control` (
  `id` int(10) UNSIGNED NOT NULL,
  `int_proceso_id` int(10) UNSIGNED NOT NULL,
  `nombre` varchar(80) NOT NULL,
  `descripcion` blob NOT NULL,
  `parametro1` float(5,5) NOT NULL,
  `parametro2` float(5,5) NOT NULL,
  `parametro3` float(5,5) NOT NULL,
  `parametro4` float(5,5) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

--
-- Volcado de datos para la tabla `int_proceso_control`
--

INSERT INTO `int_proceso_control` (`id`, `int_proceso_id`, `nombre`, `descripcion`, `parametro1`, `parametro2`, `parametro3`, `parametro4`) VALUES
(1, 1, 'Control RST', 0x436f6e74726f6c206469676974616c207469706f20525354, 0.10000, 0.20000, 0.30000, 0.00000),
(2, 2, 'Control PID', 0x436f6e74726f6c20616e616cc3b36769636f207469706f20504944, 0.99999, 0.10000, 0.50000, 0.00000);

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `int_proceso_refs`
--

CREATE TABLE `int_proceso_refs` (
  `id` int(10) UNSIGNED NOT NULL,
  `int_proceso_id` int(10) UNSIGNED NOT NULL,
  `nombre` varchar(80) NOT NULL,
  `descripcion` varchar(500) NOT NULL,
  `max_2` int(11) NOT NULL,
  `min` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

--
-- Volcado de datos para la tabla `int_proceso_refs`
--

INSERT INTO `int_proceso_refs` (`id`, `int_proceso_id`, `nombre`, `descripcion`, `max_2`, `min`) VALUES
(1, 1, 'Nivel Tanque 1', 'Nivel deseado del tanque No. 1', 1, 0),
(2, 1, 'Nivel Tanque 2', 'Nivel deseado del tanque No. 2', 1, 0),
(3, 2, 'Temperatura Tanque 1', 'Temperatura deseada del tanque No. 1', 1, 0),
(4, 3, 'DOUT0', 'Digital out 0', 1, 0),
(5, 3, 'DOUT1', 'Digital out 1', 1, 0),
(6, 3, 'DOUT2', 'Digital out 2', 1, 0),
(7, 3, 'DOUT3', 'Digital out 3', 1, 0);

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `int_proceso_refs_data`
--

CREATE TABLE `int_proceso_refs_data` (
  `id` int(10) UNSIGNED NOT NULL,
  `int_proceso_refs_id` int(10) UNSIGNED NOT NULL,
  `valor` float(5,5) NOT NULL,
  `tiempo` float(5,5) NOT NULL,
  `fecha` date NOT NULL,
  `hora` time NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `int_proceso_tipo`
--

CREATE TABLE `int_proceso_tipo` (
  `id` int(10) UNSIGNED NOT NULL,
  `nombre` varchar(80) NOT NULL,
  `descripcion` blob NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

--
-- Volcado de datos para la tabla `int_proceso_tipo`
--

INSERT INTO `int_proceso_tipo` (`id`, `nombre`, `descripcion`) VALUES
(1, 'Digital', 0x436f6e74726f6c204469676974616c20646520756e2070726f6365736f),
(2, 'Analógico', 0x436f6e74726f6c20416e616cc3b36769636f20646520756e2070726f6365736f),
(3, 'Hibrido', 0x436f6e74726f6c204469676974616c207920416e616cc3b36769636f20646520756e2070726f6365736f);

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `int_proceso_vars`
--

CREATE TABLE `int_proceso_vars` (
  `id` int(10) UNSIGNED NOT NULL,
  `int_proceso_id` int(10) UNSIGNED NOT NULL,
  `nombre` varchar(80) NOT NULL,
  `descripcion` varchar(500) NOT NULL,
  `max_2` int(11) NOT NULL,
  `min` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

--
-- Volcado de datos para la tabla `int_proceso_vars`
--

INSERT INTO `int_proceso_vars` (`id`, `int_proceso_id`, `nombre`, `descripcion`, `max_2`, `min`) VALUES
(1, 1, 'Error Nivel Tanque 1', 'Error del nivel en el tanque No. 1', 1, 0),
(2, 1, 'Error Nivel Tanque 2', 'Error del nivel en el tanque No. 2', 1, 0),
(3, 1, 'Salida Controlador Nivel Tanque 1', 'Salida Controlador del nivel en el tanque No. 1', 1, 0),
(4, 1, 'Salida Controlador Nivel Tanque 2', 'Salida Controlador del nivel en el tanque No. 2', 1, 0),
(5, 1, 'Medida Nivel Tanque 1', 'Medida del nivel en el tanque No. 1', 1, 0),
(6, 1, 'Medida Nivel Tanque 2', 'Medida del nivel en el tanque No. 2', 1, 0),
(7, 2, 'Error Temperatura Tanque 1', 'Error del temperatura en el tanque No. 1', 1, 0),
(8, 2, 'Salida Controlador Temperatura Tanque 1', 'Salida Controlador del temperatura en el tanque No. 1', 1, 0),
(9, 2, 'Medida Temperatura Tanque 1', 'Medida del temperatura en el tanque No. 1', 1, 0),
(10, 3, 'ADC0', 'Canal analógico 0', 1023, 0),
(11, 3, 'ADC1', 'Canal analógico 1', 1023, 0),
(12, 3, 'ADC2', 'Canal analógico 2', 1023, 0),
(13, 3, 'ADC3', 'Canal analógico 3', 1023, 0),
(14, 3, 'ADC4', 'Canal analógico 4', 1023, 0),
(15, 3, 'ADC5', 'Canal analógico 5', 1023, 0),
(16, 3, 'ADC6', 'Canal analógico 6', 1023, 0),
(17, 3, 'ADC7', 'Canal analógico 7', 1023, 0),
(18, 3, 'DIN0', 'Digital in 0', 1, 0),
(19, 3, 'DIN1', 'Digital in 1', 1, 0),
(20, 3, 'DIN2', 'Digital in 2', 1, 0),
(21, 3, 'DIN3', 'Digital in 3', 1, 0);

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `int_proceso_vars_data`
--

CREATE TABLE `int_proceso_vars_data` (
  `id` int(10) UNSIGNED NOT NULL,
  `int_proceso_vars_id` int(10) UNSIGNED NOT NULL,
  `valor` float(5,5) NOT NULL,
  `tiempo` float(5,5) NOT NULL,
  `fecha` date NOT NULL,
  `hora` time NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `int_usuarios`
--

CREATE TABLE `int_usuarios` (
  `id` int(10) UNSIGNED NOT NULL,
  `int_usuarios_tipo_id` int(10) UNSIGNED NOT NULL,
  `nombres` varchar(255) NOT NULL,
  `apellidos` varchar(255) NOT NULL,
  `email` blob NOT NULL,
  `clave` varchar(30) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

--
-- Volcado de datos para la tabla `int_usuarios`
--

INSERT INTO `int_usuarios` (`id`, `int_usuarios_tipo_id`, `nombres`, `apellidos`, `email`, `clave`) VALUES
(1, 1, 'Super', 'User', 0x726f6f7440756e6976616c6c652e6564752e636f, '1234'),
(2, 2, 'Exp. 1', 'Controller', 0x657870312e636f6e74726f6c40756e6976616c6c652e6564752e636f, '1234'),
(3, 3, 'Exp. 2', 'Controller', 0x657870322e636f6e74726f6c40756e6976616c6c652e6564752e636f, '1234'),
(4, 4, 'Exp. 3', 'Controller', 0x657870332e636f6e74726f6c40756e6976616c6c652e6564752e636f, '1234'),
(5, 5, 'Exp. 1', 'Monitor1', 0x657870312e6d6f6e69746f723140756e6976616c6c652e6564752e636f, '1234'),
(6, 5, 'Exp. 1', 'Monitor2', 0x657870312e6d6f6e69746f723240756e6976616c6c652e6564752e636f, '1234'),
(7, 6, 'Exp. 2', 'Monitor1', 0x657870322e6d6f6e69746f723140756e6976616c6c652e6564752e636f, '1234'),
(8, 6, 'Exp. 2', 'Monitor2', 0x657870322e6d6f6e69746f723240756e6976616c6c652e6564752e636f, '1234'),
(9, 7, 'Exp. 3', 'Monitor1', 0x657870332e6d6f6e69746f723140756e6976616c6c652e6564752e636f, '1234'),
(10, 7, 'Exp. 3', 'Monitor2', 0x657870332e6d6f6e69746f723240756e6976616c6c652e6564752e636f, '1234');

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `int_usuarios_priv`
--

CREATE TABLE `int_usuarios_priv` (
  `id` int(10) UNSIGNED NOT NULL,
  `int_usuarios_tipo_id` int(10) UNSIGNED NOT NULL,
  `control` enum('0','1') NOT NULL,
  `configuracion` enum('0','1') NOT NULL,
  `descarga_datos` enum('0','1') NOT NULL,
  `monitor` enum('0','1') NOT NULL,
  `envio_datos` enum('0','1') NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

--
-- Volcado de datos para la tabla `int_usuarios_priv`
--

INSERT INTO `int_usuarios_priv` (`id`, `int_usuarios_tipo_id`, `control`, `configuracion`, `descarga_datos`, `monitor`, `envio_datos`) VALUES
(1, 1, '1', '1', '1', '1', '1'),
(2, 2, '1', '1', '1', '1', '1'),
(3, 3, '1', '1', '1', '0', '1'),
(4, 4, '1', '1', '1', '1', '1'),
(5, 5, '0', '0', '1', '1', '0'),
(6, 6, '0', '0', '1', '1', '0'),
(7, 7, '0', '0', '1', '1', '0');

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `int_usuarios_proceso`
--

CREATE TABLE `int_usuarios_proceso` (
  `id` int(10) UNSIGNED NOT NULL,
  `int_proceso_id` int(10) UNSIGNED NOT NULL,
  `int_usuarios_id` int(10) UNSIGNED NOT NULL,
  `fecha` date NOT NULL,
  `hora_inicio` time NOT NULL,
  `hora_fin` time NOT NULL,
  `hits` int(10) UNSIGNED NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

--
-- Volcado de datos para la tabla `int_usuarios_proceso`
--

INSERT INTO `int_usuarios_proceso` (`id`, `int_proceso_id`, `int_usuarios_id`, `fecha`, `hora_inicio`, `hora_fin`, `hits`) VALUES
(1, 1, 2, '2014-05-05', '08:00:00', '10:00:00', 1),
(2, 1, 2, '2014-05-06', '08:00:00', '10:00:00', 1),
(3, 1, 2, '2014-05-07', '08:00:00', '10:00:00', 1),
(4, 1, 5, '2014-05-05', '10:00:00', '11:00:00', 1),
(5, 1, 5, '2014-05-05', '08:00:00', '10:00:00', 1),
(6, 1, 6, '2014-05-06', '10:00:00', '11:00:00', 1),
(7, 1, 6, '2014-05-06', '08:00:00', '10:00:00', 1),
(8, 1, 6, '2014-05-06', '12:00:00', '13:00:00', 1),
(9, 2, 3, '2014-05-07', '08:00:00', '10:00:00', 1),
(10, 2, 3, '2014-05-07', '14:00:00', '16:00:00', 1),
(11, 2, 3, '2014-05-08', '10:00:00', '12:00:00', 1),
(12, 2, 7, '2014-05-07', '10:00:00', '12:00:00', 1),
(13, 2, 7, '2014-05-08', '10:00:00', '12:00:00', 1),
(14, 2, 8, '2014-05-08', '10:00:00', '12:00:00', 1),
(15, 2, 8, '2014-05-07', '10:00:00', '12:00:00', 1),
(16, 2, 8, '2014-05-07', '14:00:00', '16:00:00', 1),
(17, 2, 8, '2014-05-09', '14:00:00', '16:00:00', 1);

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `int_usuarios_tipo`
--

CREATE TABLE `int_usuarios_tipo` (
  `id` int(10) UNSIGNED NOT NULL,
  `nombre` varchar(80) NOT NULL,
  `descripcion` blob NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_unicode_ci;

--
-- Volcado de datos para la tabla `int_usuarios_tipo`
--

INSERT INTO `int_usuarios_tipo` (`id`, `nombre`, `descripcion`) VALUES
(1, 'Admin', 0x41646d696e6973747261646f72),
(2, 'ControlExp1', 0x436f6e74726f6c61646f72204578702e204e6f2e2031),
(3, 'ControlExp2', 0x436f6e74726f6c61646f72204578702e204e6f2e2032),
(4, 'ControlExp3', 0x436f6e74726f6c61646f72204578702e204e6f2e2033),
(5, 'ViewExp1', 0x537570657276697369c3b36e204578702e204e6f2e2031),
(6, 'ViewExp2', 0x537570657276697369c3b36e204578702e204e6f2e2032),
(7, 'ViewExp3', 0x537570657276697369c3b36e204578702e204e6f2e2033);

--
-- Índices para tablas volcadas
--

--
-- Indices de la tabla `int_proceso`
--
ALTER TABLE `int_proceso`
  ADD PRIMARY KEY (`id`),
  ADD KEY `int_proceo_FKIndex1` (`int_proceso_tipo_id`);

--
-- Indices de la tabla `int_proceso_control`
--
ALTER TABLE `int_proceso_control`
  ADD PRIMARY KEY (`id`),
  ADD KEY `int_proceso_control_FKIndex1` (`int_proceso_id`);

--
-- Indices de la tabla `int_proceso_refs`
--
ALTER TABLE `int_proceso_refs`
  ADD PRIMARY KEY (`id`),
  ADD KEY `int_proceso_refs_FKIndex1` (`int_proceso_id`);

--
-- Indices de la tabla `int_proceso_refs_data`
--
ALTER TABLE `int_proceso_refs_data`
  ADD PRIMARY KEY (`id`),
  ADD KEY `int_proceso_refs_data_FKIndex1` (`int_proceso_refs_id`);

--
-- Indices de la tabla `int_proceso_tipo`
--
ALTER TABLE `int_proceso_tipo`
  ADD PRIMARY KEY (`id`);

--
-- Indices de la tabla `int_proceso_vars`
--
ALTER TABLE `int_proceso_vars`
  ADD PRIMARY KEY (`id`),
  ADD KEY `int_proceso_vars_FKIndex1` (`int_proceso_id`);

--
-- Indices de la tabla `int_proceso_vars_data`
--
ALTER TABLE `int_proceso_vars_data`
  ADD PRIMARY KEY (`id`),
  ADD KEY `int_proceso_vars_data_FKIndex1` (`int_proceso_vars_id`);

--
-- Indices de la tabla `int_usuarios`
--
ALTER TABLE `int_usuarios`
  ADD PRIMARY KEY (`id`),
  ADD KEY `int_usuarios_FKIndex1` (`int_usuarios_tipo_id`);

--
-- Indices de la tabla `int_usuarios_priv`
--
ALTER TABLE `int_usuarios_priv`
  ADD PRIMARY KEY (`id`),
  ADD KEY `int_usuarios_priv_FKIndex1` (`int_usuarios_tipo_id`);

--
-- Indices de la tabla `int_usuarios_proceso`
--
ALTER TABLE `int_usuarios_proceso`
  ADD PRIMARY KEY (`id`),
  ADD KEY `Table_12_FKIndex1` (`int_usuarios_id`),
  ADD KEY `Table_12_FKIndex2` (`int_proceso_id`);

--
-- Indices de la tabla `int_usuarios_tipo`
--
ALTER TABLE `int_usuarios_tipo`
  ADD PRIMARY KEY (`id`);

--
-- AUTO_INCREMENT de las tablas volcadas
--

--
-- AUTO_INCREMENT de la tabla `int_proceso`
--
ALTER TABLE `int_proceso`
  MODIFY `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=4;

--
-- AUTO_INCREMENT de la tabla `int_proceso_control`
--
ALTER TABLE `int_proceso_control`
  MODIFY `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=3;

--
-- AUTO_INCREMENT de la tabla `int_proceso_refs`
--
ALTER TABLE `int_proceso_refs`
  MODIFY `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=8;

--
-- AUTO_INCREMENT de la tabla `int_proceso_refs_data`
--
ALTER TABLE `int_proceso_refs_data`
  MODIFY `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT de la tabla `int_proceso_tipo`
--
ALTER TABLE `int_proceso_tipo`
  MODIFY `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=4;

--
-- AUTO_INCREMENT de la tabla `int_proceso_vars`
--
ALTER TABLE `int_proceso_vars`
  MODIFY `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=22;

--
-- AUTO_INCREMENT de la tabla `int_proceso_vars_data`
--
ALTER TABLE `int_proceso_vars_data`
  MODIFY `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT de la tabla `int_usuarios`
--
ALTER TABLE `int_usuarios`
  MODIFY `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=11;

--
-- AUTO_INCREMENT de la tabla `int_usuarios_priv`
--
ALTER TABLE `int_usuarios_priv`
  MODIFY `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=8;

--
-- AUTO_INCREMENT de la tabla `int_usuarios_proceso`
--
ALTER TABLE `int_usuarios_proceso`
  MODIFY `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=18;

--
-- AUTO_INCREMENT de la tabla `int_usuarios_tipo`
--
ALTER TABLE `int_usuarios_tipo`
  MODIFY `id` int(10) UNSIGNED NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=8;

--
-- Restricciones para tablas volcadas
--

--
-- Filtros para la tabla `int_proceso`
--
ALTER TABLE `int_proceso`
  ADD CONSTRAINT `int_proceso_ibfk_1` FOREIGN KEY (`int_proceso_tipo_id`) REFERENCES `int_proceso_tipo` (`id`) ON UPDATE NO ACTION;

--
-- Filtros para la tabla `int_proceso_control`
--
ALTER TABLE `int_proceso_control`
  ADD CONSTRAINT `int_proceso_control_ibfk_1` FOREIGN KEY (`int_proceso_id`) REFERENCES `int_proceso` (`id`) ON UPDATE NO ACTION;

--
-- Filtros para la tabla `int_proceso_refs`
--
ALTER TABLE `int_proceso_refs`
  ADD CONSTRAINT `int_proceso_refs_ibfk_1` FOREIGN KEY (`int_proceso_id`) REFERENCES `int_proceso` (`id`) ON UPDATE NO ACTION;

--
-- Filtros para la tabla `int_proceso_refs_data`
--
ALTER TABLE `int_proceso_refs_data`
  ADD CONSTRAINT `int_proceso_refs_data_ibfk_1` FOREIGN KEY (`int_proceso_refs_id`) REFERENCES `int_proceso_refs` (`id`) ON UPDATE NO ACTION;

--
-- Filtros para la tabla `int_proceso_vars`
--
ALTER TABLE `int_proceso_vars`
  ADD CONSTRAINT `int_proceso_vars_ibfk_1` FOREIGN KEY (`int_proceso_id`) REFERENCES `int_proceso` (`id`) ON UPDATE NO ACTION;

--
-- Filtros para la tabla `int_proceso_vars_data`
--
ALTER TABLE `int_proceso_vars_data`
  ADD CONSTRAINT `int_proceso_vars_data_ibfk_1` FOREIGN KEY (`int_proceso_vars_id`) REFERENCES `int_proceso_vars` (`id`) ON UPDATE NO ACTION;

--
-- Filtros para la tabla `int_usuarios`
--
ALTER TABLE `int_usuarios`
  ADD CONSTRAINT `int_usuarios_ibfk_1` FOREIGN KEY (`int_usuarios_tipo_id`) REFERENCES `int_usuarios_tipo` (`id`) ON UPDATE NO ACTION;

--
-- Filtros para la tabla `int_usuarios_priv`
--
ALTER TABLE `int_usuarios_priv`
  ADD CONSTRAINT `int_usuarios_priv_ibfk_1` FOREIGN KEY (`int_usuarios_tipo_id`) REFERENCES `int_usuarios_tipo` (`id`) ON UPDATE NO ACTION;

--
-- Filtros para la tabla `int_usuarios_proceso`
--
ALTER TABLE `int_usuarios_proceso`
  ADD CONSTRAINT `int_usuarios_proceso_ibfk_1` FOREIGN KEY (`int_usuarios_id`) REFERENCES `int_usuarios` (`id`) ON UPDATE NO ACTION,
  ADD CONSTRAINT `int_usuarios_proceso_ibfk_2` FOREIGN KEY (`int_proceso_id`) REFERENCES `int_proceso` (`id`) ON UPDATE NO ACTION;
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
