-- Tarjeta 09 / MySQL 8.4 / ejecutar con la base pedidos seleccionada.
-- Manual, incremental y repetible. No se ejecuta al arrancar la aplicacion.
-- Detener servicios, respaldar y revisar README.md antes de usar.
-- No usar mysql --force: SIGNAL debe detener la ejecucion.
DELIMITER $$
CREATE PROCEDURE gas_volcan_v09_pedidos()
BEGIN
  IF DATABASE() IS NULL THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Selecciona explicitamente una base de datos'; END IF;
  IF VERSION() NOT LIKE '8.4.%' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Esta migracion requiere MySQL 8.4'; END IF;

  -- Preflight: boletas. No adaptar silenciosamente un esquema desconocido.
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='boletas') THEN
  IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='boletas' AND engine='InnoDB') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'boletas: se requiere InnoDB'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='boletas' AND column_name='id') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'boletas.id: falta columna base'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='boletas' AND column_name='id') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='boletas' AND column_name='id' AND column_type='bigint' AND is_nullable='NO') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'boletas.id: tipo o nulabilidad incompatible'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='boletas' AND column_name='cliente_id') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'boletas.cliente_id: falta columna base'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='boletas' AND column_name='cliente_id') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='boletas' AND column_name='cliente_id' AND column_type='bigint') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'boletas.cliente_id: tipo o nulabilidad incompatible'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='boletas' AND column_name='direccion') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'boletas.direccion: falta columna base'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='boletas' AND column_name='direccion') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='boletas' AND column_name='direccion' AND column_type='varchar(300)' AND is_nullable='NO') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'boletas.direccion: tipo o nulabilidad incompatible'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='boletas' AND column_name='comuna') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'boletas.comuna: falta columna base'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='boletas' AND column_name='comuna') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='boletas' AND column_name='comuna' AND column_type='varchar(100)' AND is_nullable='NO') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'boletas.comuna: tipo o nulabilidad incompatible'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='boletas' AND column_name='estado') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'boletas.estado: falta columna base'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='boletas' AND column_name='estado') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='boletas' AND column_name='estado' AND column_type='varchar(50)' AND is_nullable='NO') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'boletas.estado: tipo o nulabilidad incompatible'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='boletas' AND column_name='repartidor_id') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'boletas.repartidor_id: falta columna base'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='boletas' AND column_name='repartidor_id') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='boletas' AND column_name='repartidor_id' AND column_type='bigint' AND is_nullable='YES') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'boletas.repartidor_id: tipo o nulabilidad incompatible'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='boletas' AND column_name='fecha') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'boletas.fecha: falta columna base'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='boletas' AND column_name='fecha') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='boletas' AND column_name='fecha' AND column_type='datetime(6)' AND is_nullable='NO') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'boletas.fecha: tipo o nulabilidad incompatible'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='boletas' AND column_name='total') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'boletas.total: falta columna base'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='boletas' AND column_name='total') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='boletas' AND column_name='total' AND column_type='decimal(19,2)' AND is_nullable='NO') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'boletas.total: tipo o nulabilidad incompatible'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='boletas' AND column_name='version') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='boletas' AND column_name='version' AND column_type='bigint' AND is_nullable='NO') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'boletas.version: tipo o nulabilidad incompatible'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='boletas' AND column_name='operacion_stock_id') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='boletas' AND column_name='operacion_stock_id' AND column_type='varchar(36)' AND is_nullable='YES') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'boletas.operacion_stock_id: tipo o nulabilidad incompatible'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='boletas' AND column_name='nombre_invitado') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='boletas' AND column_name='nombre_invitado' AND column_type='varchar(150)' AND is_nullable='YES') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'boletas.nombre_invitado: tipo o nulabilidad incompatible'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='boletas' AND column_name='correo_invitado') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='boletas' AND column_name='correo_invitado' AND column_type='varchar(254)' AND is_nullable='YES') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'boletas.correo_invitado: tipo o nulabilidad incompatible'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='boletas' AND column_name='telefono_invitado') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='boletas' AND column_name='telefono_invitado' AND column_type='varchar(30)' AND is_nullable='YES') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'boletas.telefono_invitado: tipo o nulabilidad incompatible'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='boletas' AND index_name='PRIMARY' GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index)='id') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'boletas: clave primaria incompatible'; END IF;
  END IF;

  -- Preflight: detalles_boleta. No adaptar silenciosamente un esquema desconocido.
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='detalles_boleta') THEN
  IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='detalles_boleta' AND engine='InnoDB') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'detalles_boleta: se requiere InnoDB'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='detalles_boleta' AND column_name='id') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'detalles_boleta.id: falta columna base'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='detalles_boleta' AND column_name='id') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='detalles_boleta' AND column_name='id' AND column_type='bigint' AND is_nullable='NO') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'detalles_boleta.id: tipo o nulabilidad incompatible'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='detalles_boleta' AND column_name='producto_id') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'detalles_boleta.producto_id: falta columna base'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='detalles_boleta' AND column_name='producto_id') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='detalles_boleta' AND column_name='producto_id' AND column_type='bigint' AND is_nullable='NO') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'detalles_boleta.producto_id: tipo o nulabilidad incompatible'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='detalles_boleta' AND column_name='cantidad') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'detalles_boleta.cantidad: falta columna base'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='detalles_boleta' AND column_name='cantidad') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='detalles_boleta' AND column_name='cantidad' AND column_type='int' AND is_nullable='NO') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'detalles_boleta.cantidad: tipo o nulabilidad incompatible'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='detalles_boleta' AND column_name='precio') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'detalles_boleta.precio: falta columna base'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='detalles_boleta' AND column_name='precio') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='detalles_boleta' AND column_name='precio' AND column_type='decimal(19,2)' AND is_nullable='NO') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'detalles_boleta.precio: tipo o nulabilidad incompatible'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='detalles_boleta' AND column_name='boleta_id') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'detalles_boleta.boleta_id: falta columna base'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='detalles_boleta' AND column_name='boleta_id') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='detalles_boleta' AND column_name='boleta_id' AND column_type='bigint' AND is_nullable='NO') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'detalles_boleta.boleta_id: tipo o nulabilidad incompatible'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='detalles_boleta' AND index_name='PRIMARY' GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index)='id') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'detalles_boleta: clave primaria incompatible'; END IF;
  END IF;

  -- Preflight: operaciones_compra. No adaptar silenciosamente un esquema desconocido.
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='operaciones_compra') THEN
  IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='operaciones_compra' AND engine='InnoDB') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'operaciones_compra: se requiere InnoDB'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operaciones_compra' AND column_name='id') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'operaciones_compra.id: falta columna base'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operaciones_compra' AND column_name='id') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operaciones_compra' AND column_name='id' AND column_type='varchar(36)' AND is_nullable='NO') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'operaciones_compra.id: tipo o nulabilidad incompatible'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operaciones_compra' AND column_name='solicitud') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'operaciones_compra.solicitud: falta columna base'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operaciones_compra' AND column_name='solicitud') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operaciones_compra' AND column_name='solicitud' AND data_type IN ('tinytext','text','mediumtext','longtext') AND is_nullable='NO') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'operaciones_compra.solicitud: tipo o nulabilidad incompatible'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operaciones_compra' AND column_name='cotizacion') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'operaciones_compra.cotizacion: falta columna base'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operaciones_compra' AND column_name='cotizacion') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operaciones_compra' AND column_name='cotizacion' AND column_type='longtext' AND is_nullable='YES') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'operaciones_compra.cotizacion: tipo o nulabilidad incompatible'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operaciones_compra' AND column_name='estado') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'operaciones_compra.estado: falta columna base'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operaciones_compra' AND column_name='estado') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operaciones_compra' AND column_name='estado' AND column_type='varchar(40)' AND is_nullable='NO') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'operaciones_compra.estado: tipo o nulabilidad incompatible'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operaciones_compra' AND column_name='boleta_id') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'operaciones_compra.boleta_id: falta columna base'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operaciones_compra' AND column_name='boleta_id') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operaciones_compra' AND column_name='boleta_id' AND column_type='bigint' AND is_nullable='YES') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'operaciones_compra.boleta_id: tipo o nulabilidad incompatible'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operaciones_compra' AND column_name='trabajador') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'operaciones_compra.trabajador: falta columna base'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operaciones_compra' AND column_name='trabajador') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operaciones_compra' AND column_name='trabajador' AND column_type='varchar(36)' AND is_nullable='YES') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'operaciones_compra.trabajador: tipo o nulabilidad incompatible'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operaciones_compra' AND column_name='vence') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'operaciones_compra.vence: falta columna base'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operaciones_compra' AND column_name='vence') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operaciones_compra' AND column_name='vence' AND column_type='datetime(6)' AND is_nullable='YES') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'operaciones_compra.vence: tipo o nulabilidad incompatible'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operaciones_compra' AND column_name='creada') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'operaciones_compra.creada: falta columna base'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operaciones_compra' AND column_name='creada') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operaciones_compra' AND column_name='creada' AND column_type='datetime(6)' AND is_nullable='NO') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'operaciones_compra.creada: tipo o nulabilidad incompatible'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operaciones_compra' AND column_name='ultima_revision') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'operaciones_compra.ultima_revision: falta columna base'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operaciones_compra' AND column_name='ultima_revision') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operaciones_compra' AND column_name='ultima_revision' AND column_type='datetime(6)' AND is_nullable='NO') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'operaciones_compra.ultima_revision: tipo o nulabilidad incompatible'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operaciones_compra' AND column_name='mensaje') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'operaciones_compra.mensaje: falta columna base'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operaciones_compra' AND column_name='mensaje') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operaciones_compra' AND column_name='mensaje' AND column_type='varchar(255)' AND is_nullable='YES') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'operaciones_compra.mensaje: tipo o nulabilidad incompatible'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='operaciones_compra' AND index_name='PRIMARY' GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index)='id') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'operaciones_compra: clave primaria incompatible'; END IF;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='boletas' AND column_name='cliente_id' AND (column_default IS NOT NULL OR extra<>'' OR column_comment<>'')) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'boletas.cliente_id: revisar atributos personalizados'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operaciones_compra' AND column_name='solicitud' AND (column_default IS NOT NULL OR extra<>'' OR column_comment<>'')) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'operaciones_compra.solicitud: revisar atributos personalizados'; END IF;

  CREATE TABLE IF NOT EXISTS `boletas` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `cliente_id` bigint NULL,
    `direccion` varchar(300) NOT NULL,
    `comuna` varchar(100) NOT NULL,
    `estado` varchar(50) NOT NULL,
    `repartidor_id` bigint NULL,
    `fecha` datetime(6) NOT NULL,
    `total` decimal(19,2) NOT NULL,
    `version` bigint NOT NULL DEFAULT 0,
    `operacion_stock_id` varchar(36) NULL,
    `nombre_invitado` varchar(150) NULL,
    `correo_invitado` varchar(254) NULL,
    `telefono_invitado` varchar(30) NULL,
    PRIMARY KEY (`id`)
  ) ENGINE=InnoDB;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='boletas' AND column_name='operacion_stock_id') THEN ALTER TABLE `boletas` ADD COLUMN `operacion_stock_id` varchar(36) NULL; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='boletas' AND column_name='correo_invitado') THEN ALTER TABLE `boletas` ADD COLUMN `correo_invitado` varchar(254) NULL; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='boletas' AND column_name='version') THEN ALTER TABLE `boletas` ADD COLUMN `version` bigint NOT NULL DEFAULT 0; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='boletas' AND column_name='telefono_invitado') THEN ALTER TABLE `boletas` ADD COLUMN `telefono_invitado` varchar(30) NULL; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='boletas' AND column_name='nombre_invitado') THEN ALTER TABLE `boletas` ADD COLUMN `nombre_invitado` varchar(150) NULL; END IF;

  CREATE TABLE IF NOT EXISTS `detalles_boleta` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `producto_id` bigint NOT NULL,
    `cantidad` int NOT NULL,
    `precio` decimal(19,2) NOT NULL,
    `boleta_id` bigint NOT NULL,
    PRIMARY KEY (`id`)
  ) ENGINE=InnoDB;

  CREATE TABLE IF NOT EXISTS `operaciones_compra` (
    `id` varchar(36) NOT NULL,
    `solicitud` longtext NOT NULL,
    `cotizacion` longtext NULL,
    `estado` varchar(40) NOT NULL,
    `boleta_id` bigint NULL,
    `trabajador` varchar(36) NULL,
    `vence` datetime(6) NULL,
    `creada` datetime(6) NOT NULL,
    `ultima_revision` datetime(6) NOT NULL,
    `mensaje` varchar(255) NULL,
    PRIMARY KEY (`id`)
  ) ENGINE=InnoDB;

  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='boletas' AND column_name='cliente_id' AND is_nullable='NO') THEN
    ALTER TABLE boletas MODIFY COLUMN cliente_id BIGINT NULL;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
      AND table_name='operaciones_compra' AND column_name='solicitud' AND data_type<>'longtext') THEN
    -- Preservar el charset/collation de la columna, incluso si difiere de la tabla.
    SELECT CONCAT('ALTER TABLE operaciones_compra MODIFY COLUMN solicitud LONGTEXT CHARACTER SET `',
        character_set_name, '` COLLATE `', collation_name, '` NOT NULL') INTO @gas_volcan_v09_ddl
      FROM information_schema.columns WHERE table_schema=DATABASE()
        AND table_name='operaciones_compra' AND column_name='solicitud';
    PREPARE gas_volcan_v09_stmt FROM @gas_volcan_v09_ddl;
    EXECUTE gas_volcan_v09_stmt;
    DEALLOCATE PREPARE gas_volcan_v09_stmt;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='boletas' AND non_unique=0 GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index)='operacion_stock_id' AND COUNT(sub_part)=0) THEN
  IF EXISTS (SELECT `operacion_stock_id` FROM `boletas` WHERE `operacion_stock_id` IS NOT NULL GROUP BY `operacion_stock_id` HAVING COUNT(*)>1) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'boletas.operacion_stock_id: duplicados, revisar manualmente'; END IF;
    ALTER TABLE `boletas` ADD UNIQUE INDEX `uq_boletas_operacion_stock_v09` (`operacion_stock_id`);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND table_name='detalles_boleta' AND column_name='boleta_id' AND referenced_table_schema=DATABASE() AND referenced_table_name='boletas' AND referenced_column_name='id' AND constraint_name IN (SELECT constraint_name FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND table_name='detalles_boleta' GROUP BY constraint_name HAVING COUNT(*)=1)) THEN
  IF EXISTS (SELECT 1 FROM `detalles_boleta` a LEFT JOIN `boletas` b ON a.`boleta_id`=b.`id` WHERE b.`id` IS NULL) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'detalles_boleta: referencias huerfanas, revisar manualmente'; END IF;
    ALTER TABLE `detalles_boleta` ADD CONSTRAINT `fk_detalle_boleta` FOREIGN KEY (`boleta_id`) REFERENCES `boletas` (`id`);
  END IF;
END$$
DELIMITER ;
CALL gas_volcan_v09_pedidos();
DROP PROCEDURE gas_volcan_v09_pedidos;
