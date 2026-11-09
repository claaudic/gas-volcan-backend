-- Tarjeta 09 / MySQL 8.4 / ejecutar con la base catalogo seleccionada.
-- Manual, incremental y repetible. No se ejecuta al arrancar la aplicacion.
-- Detener servicios, respaldar y revisar README.md antes de usar.
-- No usar mysql --force: SIGNAL debe detener la ejecucion.
DELIMITER $$
CREATE PROCEDURE gas_volcan_v09_catalogo()
BEGIN
  IF DATABASE() IS NULL THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Selecciona explicitamente una base de datos'; END IF;
  IF VERSION() NOT LIKE '8.4.%' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Esta migracion requiere MySQL 8.4'; END IF;

  -- Preflight: categorias. No adaptar silenciosamente un esquema desconocido.
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='categorias') THEN
  IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='categorias' AND engine='InnoDB') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'categorias: se requiere InnoDB'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='categorias' AND column_name='id') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'categorias.id: falta columna base'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='categorias' AND column_name='id') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='categorias' AND column_name='id' AND column_type='bigint' AND is_nullable='NO') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'categorias.id: tipo o nulabilidad incompatible'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='categorias' AND column_name='nombre') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'categorias.nombre: falta columna base'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='categorias' AND column_name='nombre') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='categorias' AND column_name='nombre' AND column_type='varchar(255)' AND is_nullable='NO') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'categorias.nombre: tipo o nulabilidad incompatible'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='categorias' AND column_name='descripcion') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'categorias.descripcion: falta columna base'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='categorias' AND column_name='descripcion') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='categorias' AND column_name='descripcion' AND column_type='varchar(255)' AND is_nullable='YES') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'categorias.descripcion: tipo o nulabilidad incompatible'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='categorias' AND index_name='PRIMARY' GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index)='id') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'categorias: clave primaria incompatible'; END IF;
  END IF;

  -- Preflight: productos. No adaptar silenciosamente un esquema desconocido.
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='productos') THEN
  IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='productos' AND engine='InnoDB') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'productos: se requiere InnoDB'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='productos' AND column_name='id') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'productos.id: falta columna base'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='productos' AND column_name='id') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='productos' AND column_name='id' AND column_type='bigint' AND is_nullable='NO') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'productos.id: tipo o nulabilidad incompatible'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='productos' AND column_name='codigo') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'productos.codigo: falta columna base'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='productos' AND column_name='codigo') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='productos' AND column_name='codigo' AND column_type='varchar(255)' AND is_nullable='NO') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'productos.codigo: tipo o nulabilidad incompatible'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='productos' AND column_name='nombre') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'productos.nombre: falta columna base'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='productos' AND column_name='nombre') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='productos' AND column_name='nombre' AND column_type='varchar(255)' AND is_nullable='NO') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'productos.nombre: tipo o nulabilidad incompatible'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='productos' AND column_name='descripcion') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'productos.descripcion: falta columna base'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='productos' AND column_name='descripcion') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='productos' AND column_name='descripcion' AND column_type='varchar(255)' AND is_nullable='YES') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'productos.descripcion: tipo o nulabilidad incompatible'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='productos' AND column_name='precio_residencial') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'productos.precio_residencial: falta columna base'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='productos' AND column_name='precio_residencial') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='productos' AND column_name='precio_residencial' AND column_type='decimal(19,2)' AND is_nullable='NO') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'productos.precio_residencial: tipo o nulabilidad incompatible'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='productos' AND column_name='precio_comercial') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'productos.precio_comercial: falta columna base'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='productos' AND column_name='precio_comercial') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='productos' AND column_name='precio_comercial' AND column_type='decimal(19,2)' AND is_nullable='NO') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'productos.precio_comercial: tipo o nulabilidad incompatible'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='productos' AND column_name='stock') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'productos.stock: falta columna base'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='productos' AND column_name='stock') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='productos' AND column_name='stock' AND column_type='int' AND is_nullable='NO') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'productos.stock: tipo o nulabilidad incompatible'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='productos' AND column_name='activo') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'productos.activo: falta columna base'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='productos' AND column_name='activo') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='productos' AND column_name='activo' AND column_type='bit(1)' AND is_nullable='NO') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'productos.activo: tipo o nulabilidad incompatible'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='productos' AND column_name='categoria_id') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'productos.categoria_id: falta columna base'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='productos' AND column_name='categoria_id') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='productos' AND column_name='categoria_id' AND column_type='bigint' AND is_nullable='NO') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'productos.categoria_id: tipo o nulabilidad incompatible'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='productos' AND column_name='precio_oferta') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'productos.precio_oferta: falta columna base'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='productos' AND column_name='precio_oferta') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='productos' AND column_name='precio_oferta' AND column_type='decimal(19,2)' AND is_nullable='YES') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'productos.precio_oferta: tipo o nulabilidad incompatible'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='productos' AND column_name='etiqueta_oferta') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'productos.etiqueta_oferta: falta columna base'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='productos' AND column_name='etiqueta_oferta') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='productos' AND column_name='etiqueta_oferta' AND column_type='varchar(255)' AND is_nullable='YES') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'productos.etiqueta_oferta: tipo o nulabilidad incompatible'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='productos' AND column_name='stock_critico') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'productos.stock_critico: falta columna base'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='productos' AND column_name='stock_critico') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='productos' AND column_name='stock_critico' AND column_type='int' AND is_nullable='YES') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'productos.stock_critico: tipo o nulabilidad incompatible'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='productos' AND column_name='unidad') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'productos.unidad: falta columna base'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='productos' AND column_name='unidad') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='productos' AND column_name='unidad' AND column_type='varchar(255)' AND is_nullable='NO') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'productos.unidad: tipo o nulabilidad incompatible'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='productos' AND column_name='imagen') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'productos.imagen: falta columna base'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='productos' AND column_name='imagen') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='productos' AND column_name='imagen' AND column_type='varchar(2048)' AND is_nullable='YES') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'productos.imagen: tipo o nulabilidad incompatible'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='productos' AND column_name='version') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='productos' AND column_name='version' AND column_type='bigint' AND is_nullable='NO') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'productos.version: tipo o nulabilidad incompatible'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='productos' AND index_name='PRIMARY' GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index)='id') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'productos: clave primaria incompatible'; END IF;
  END IF;

  -- Preflight: operaciones_stock. No adaptar silenciosamente un esquema desconocido.
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='operaciones_stock') THEN
  IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='operaciones_stock' AND engine='InnoDB') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'operaciones_stock: se requiere InnoDB'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operaciones_stock' AND column_name='id') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'operaciones_stock.id: falta columna base'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operaciones_stock' AND column_name='id') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operaciones_stock' AND column_name='id' AND column_type='varchar(36)' AND is_nullable='NO') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'operaciones_stock.id: tipo o nulabilidad incompatible'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operaciones_stock' AND column_name='estado') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'operaciones_stock.estado: falta columna base'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operaciones_stock' AND column_name='estado') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operaciones_stock' AND column_name='estado' AND column_type='varchar(20)' AND is_nullable='NO') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'operaciones_stock.estado: tipo o nulabilidad incompatible'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operaciones_stock' AND column_name='codigo_resultado') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'operaciones_stock.codigo_resultado: falta columna base'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operaciones_stock' AND column_name='codigo_resultado') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operaciones_stock' AND column_name='codigo_resultado' AND column_type='int' AND is_nullable='NO') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'operaciones_stock.codigo_resultado: tipo o nulabilidad incompatible'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operaciones_stock' AND column_name='mensaje') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'operaciones_stock.mensaje: falta columna base'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operaciones_stock' AND column_name='mensaje') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operaciones_stock' AND column_name='mensaje' AND column_type='varchar(255)' AND is_nullable='NO') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'operaciones_stock.mensaje: tipo o nulabilidad incompatible'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operaciones_stock' AND column_name='creada') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'operaciones_stock.creada: falta columna base'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operaciones_stock' AND column_name='creada') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operaciones_stock' AND column_name='creada' AND column_type='datetime(6)' AND is_nullable='NO') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'operaciones_stock.creada: tipo o nulabilidad incompatible'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operaciones_stock' AND column_name='compensada') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'operaciones_stock.compensada: falta columna base'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operaciones_stock' AND column_name='compensada') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operaciones_stock' AND column_name='compensada' AND column_type='datetime(6)' AND is_nullable='YES') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'operaciones_stock.compensada: tipo o nulabilidad incompatible'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='operaciones_stock' AND index_name='PRIMARY' GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index)='id') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'operaciones_stock: clave primaria incompatible'; END IF;
  END IF;

  -- Preflight: operacion_stock_productos. No adaptar silenciosamente un esquema desconocido.
  IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='operacion_stock_productos') THEN
  IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='operacion_stock_productos' AND engine='InnoDB') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'operacion_stock_productos: se requiere InnoDB'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operacion_stock_productos' AND column_name='operacion_id') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'operacion_stock_productos.operacion_id: falta columna base'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operacion_stock_productos' AND column_name='operacion_id') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operacion_stock_productos' AND column_name='operacion_id' AND column_type='varchar(36)' AND is_nullable='NO') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'operacion_stock_productos.operacion_id: tipo o nulabilidad incompatible'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operacion_stock_productos' AND column_name='producto_id') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'operacion_stock_productos.producto_id: falta columna base'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operacion_stock_productos' AND column_name='producto_id') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operacion_stock_productos' AND column_name='producto_id' AND column_type='bigint' AND is_nullable='NO') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'operacion_stock_productos.producto_id: tipo o nulabilidad incompatible'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operacion_stock_productos' AND column_name='cantidad') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'operacion_stock_productos.cantidad: falta columna base'; END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operacion_stock_productos' AND column_name='cantidad') AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='operacion_stock_productos' AND column_name='cantidad' AND column_type='int' AND is_nullable='NO') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'operacion_stock_productos.cantidad: tipo o nulabilidad incompatible'; END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='operacion_stock_productos' AND index_name='PRIMARY' GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index)='producto_id,operacion_id') THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'operacion_stock_productos: clave primaria incompatible'; END IF;
  END IF;

  CREATE TABLE IF NOT EXISTS `categorias` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `nombre` varchar(255) NOT NULL,
    `descripcion` varchar(255) NULL,
    PRIMARY KEY (`id`)
  ) ENGINE=InnoDB;

  CREATE TABLE IF NOT EXISTS `productos` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `codigo` varchar(255) NOT NULL,
    `nombre` varchar(255) NOT NULL,
    `descripcion` varchar(255) NULL,
    `precio_residencial` decimal(19,2) NOT NULL,
    `precio_comercial` decimal(19,2) NOT NULL,
    `stock` int NOT NULL,
    `activo` bit(1) NOT NULL,
    `categoria_id` bigint NOT NULL,
    `precio_oferta` decimal(19,2) NULL,
    `etiqueta_oferta` varchar(255) NULL,
    `stock_critico` int NULL,
    `unidad` varchar(255) NOT NULL,
    `imagen` varchar(2048) NULL,
    `version` bigint NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    CHECK (`stock` >= 0),
    CHECK (`stock_critico` >= 0)
  ) ENGINE=InnoDB;
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='productos' AND column_name='version') THEN ALTER TABLE `productos` ADD COLUMN `version` bigint NOT NULL DEFAULT 0; END IF;

  CREATE TABLE IF NOT EXISTS `operaciones_stock` (
    `id` varchar(36) NOT NULL,
    `estado` varchar(20) NOT NULL,
    `codigo_resultado` int NOT NULL,
    `mensaje` varchar(255) NOT NULL,
    `creada` datetime(6) NOT NULL,
    `compensada` datetime(6) NULL,
    PRIMARY KEY (`id`)
  ) ENGINE=InnoDB;

  CREATE TABLE IF NOT EXISTS `operacion_stock_productos` (
    `operacion_id` varchar(36) NOT NULL,
    `producto_id` bigint NOT NULL,
    `cantidad` int NOT NULL,
    PRIMARY KEY (`producto_id`,`operacion_id`)
  ) ENGINE=InnoDB;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='productos' AND non_unique=0 GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index)='codigo' AND COUNT(sub_part)=0) THEN
  IF EXISTS (SELECT `codigo` FROM `productos` WHERE `codigo` IS NOT NULL GROUP BY `codigo` HAVING COUNT(*)>1) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'productos.codigo: duplicados, revisar manualmente'; END IF;
    ALTER TABLE `productos` ADD UNIQUE INDEX `uq_productos_codigo_v09` (`codigo`);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='categorias' AND non_unique=0 GROUP BY index_name HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index)='nombre' AND COUNT(sub_part)=0) THEN
  IF EXISTS (SELECT `nombre` FROM `categorias` WHERE `nombre` IS NOT NULL GROUP BY `nombre` HAVING COUNT(*)>1) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'categorias.nombre: duplicados, revisar manualmente'; END IF;
    ALTER TABLE `categorias` ADD UNIQUE INDEX `uq_categorias_nombre_v09` (`nombre`);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND table_name='productos' AND column_name='categoria_id' AND referenced_table_schema=DATABASE() AND referenced_table_name='categorias' AND referenced_column_name='id' AND constraint_name IN (SELECT constraint_name FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND table_name='productos' GROUP BY constraint_name HAVING COUNT(*)=1)) THEN
  IF EXISTS (SELECT 1 FROM `productos` a LEFT JOIN `categorias` b ON a.`categoria_id`=b.`id` WHERE b.`id` IS NULL) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'productos: referencias huerfanas, revisar manualmente'; END IF;
    ALTER TABLE `productos` ADD CONSTRAINT `fk_productos_categoria_v09` FOREIGN KEY (`categoria_id`) REFERENCES `categorias` (`id`);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND table_name='operacion_stock_productos' AND column_name='operacion_id' AND referenced_table_schema=DATABASE() AND referenced_table_name='operaciones_stock' AND referenced_column_name='id' AND constraint_name IN (SELECT constraint_name FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND table_name='operacion_stock_productos' GROUP BY constraint_name HAVING COUNT(*)=1)) THEN
  IF EXISTS (SELECT 1 FROM `operacion_stock_productos` a LEFT JOIN `operaciones_stock` b ON a.`operacion_id`=b.`id` WHERE b.`id` IS NULL) THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'operacion_stock_productos: referencias huerfanas, revisar manualmente'; END IF;
    ALTER TABLE `operacion_stock_productos` ADD CONSTRAINT `fk_operacion_stock_v09` FOREIGN KEY (`operacion_id`) REFERENCES `operaciones_stock` (`id`);
  END IF;
END$$
DELIMITER ;
CALL gas_volcan_v09_catalogo();
DROP PROCEDURE gas_volcan_v09_catalogo;
