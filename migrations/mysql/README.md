# Tarjeta 09: migraciones manuales MySQL 8.4

**Estado: preparadas para revisión. No aplicadas a bases del proyecto.**

## Alcance y origen del esquema

El proyecto no usa Flyway ni Liquibase. Hibernate administra actualmente el esquema
con `ddl-auto=update`. `mysql-init/01-bases.sql` solo crea bases y permisos y Docker
solo lo ejecuta al inicializar un volumen vacío; no migra volúmenes existentes.
No se modifica ese mecanismo ni se configura ejecución automática de estos scripts.

Se eligen scripts SQL manuales, independientes por base, con comprobaciones de
`information_schema` y ejecución repetible. No introducen un segundo administrador
automático del esquema. Los tipos, nombres físicos y relaciones se contrastaron
con las entidades actuales y con el DDL producido por Hibernate 7.4.13 en MySQL 8.4.
El esquema real de una base compartida **no fue inspeccionado**: deberá contrastarse
antes del despliegue. Los scripts rechazan tipos o nulabilidad incompatibles en vez
de adaptar silenciosamente una estructura desconocida. Las columnas ajenas se conservan.

## Objetos afectados

| Base / script | Tabla | Acción |
|---|---|---|
| Pedidos / `09_pedidos.sql` | `boletas` | `cliente_id BIGINT NULL`; agregar si faltan `nombre_invitado VARCHAR(150)`, `correo_invitado VARCHAR(254)`, `telefono_invitado VARCHAR(30)` (todos NULL); `version BIGINT NOT NULL DEFAULT 0`; `operacion_stock_id VARCHAR(36) NULL` con índice único |
| Pedidos | `detalles_boleta` | Crear si falta y verificar estructura, PK y FK `boleta_id -> boletas.id` |
| Pedidos | `operaciones_compra` | Crear si falta; PK `id VARCHAR(36)`; `solicitud LONGTEXT NOT NULL`; ampliar antiguos tipos TEXT a LONGTEXT conservando contenido |
| Catálogo / `09_catalogo.sql` | `categorias`, `productos` | Crear si faltan para inicializar vacío; verificar PK, únicos de nombre/código y FK de categoría; agregar `productos.version BIGINT NOT NULL DEFAULT 0` si falta |
| Catálogo | `operaciones_stock` | Crear si falta; PK `id VARCHAR(36)` y resultado durable del descuento |
| Catálogo | `operacion_stock_productos` | Crear si falta; PK compuesta `(producto_id, operacion_id)` y FK `operacion_id -> operaciones_stock.id` |

`operaciones_compra`: `cotizacion LONGTEXT NULL`, `estado VARCHAR(40) NOT NULL`,
`boleta_id BIGINT NULL`, `trabajador VARCHAR(36) NULL`, `vence DATETIME(6) NULL`,
`creada` y `ultima_revision DATETIME(6) NOT NULL`, `mensaje VARCHAR(255) NULL`.
`operaciones_stock`: `estado VARCHAR(20) NOT NULL`, `codigo_resultado INT NOT NULL`,
`mensaje VARCHAR(255) NOT NULL`, `creada DATETIME(6) NOT NULL`,
`compensada DATETIME(6) NULL`. La colección guarda `cantidad INT NOT NULL` y
`producto_id BIGINT NOT NULL`. Los scripts contienen el resto del esquema base.

Las versiones nuevas se inicializan en 0 para filas históricas. No se inventan
operaciones de stock para boletas antiguas, ni se cambian totales, precios, stock,
estados, fechas, contenido JSON o claves de idempotencia. No se crean FK entre
microservicios: cliente, repartidor y producto siguen siendo referencias externas.
Tampoco hay FK JPA entre `operaciones_compra.boleta_id` y `boletas`.

Los índices únicos se reconocen por columnas completas y unicidad, no por el nombre
generado por Hibernate. Las FK se reconocen por su relación de columnas. No se
reemplazan restricciones existentes ni se agregan índices especulativos para la
consulta de recuperación: JPA solo declara las PK y únicos descritos arriba.

El script de catálogo es independiente porque pedidos no debe administrar su base.
Está fuera de los módulos; no modifica código, JWT, propiedades ni contratos de
catálogo, usuarios o Gateway. Debe revisarlo también el responsable de catálogo.

## Validación reproducible (solo infraestructura desechable)

Requisitos: Java 21, Python 3, Docker y la imagen `mysql:8.4` disponible.
Desde la raíz, construir artefactos con las entidades actuales:

```sh
bash ms-pedidos/mvnw -f pom.xml -DskipTests package
python3 migrations/mysql/validation/validate.py
```

El validador crea un contenedor con nombre aleatorio, **sin volúmenes** y con puerto
aleatorio restringido a 127.0.0.1. Solo usa una contraseña sintética propia de esa
instancia. En `finally` elimina exclusivamente el contenedor que acaba de crear.
No usa `.env`, credenciales reales, el contenedor de Compose ni bases existentes.
Si el proceso es interrumpido abruptamente puede quedar ese contenedor de pruebas;
identificar su nombre `gas-volcan-v09-test-*` antes de limpiar, nunca usar una
limpieza global de Docker.

Comprueba ambas migraciones en bases vacías, esquemas actuales creados por Hibernate
y estructuras históricas con datos sintéticos. Repite la aplicación, compara los
datos y el número de índices, comprueba versión 0, inserta una boleta invitada y
solicitudes de más de 255 caracteres, y ejecuta `hbm2ddl.auto=validate` contra las
entidades reales. Incluye ampliación del antiguo TINYTEXT conservando su charset/collation y rechazo
de drift, duplicados y referencias huérfanas sin borrar registros.
No arranca microservicios ni ejecuta carga inicial, recuperación o descuentos.
`SchemaProbe.java` se compila fuera del repositorio contra los JAR construidos por
los POM reales; no añade dependencias ni modifica las entidades.

## Procedimiento futuro de despliegue (requiere autorización separada)

1. Identificar la base y el servidor de cada servicio. Respaldar datos y DDL,
   probar restauración y ensayar los scripts sobre una copia aislada. Comparar
   `SHOW CREATE TABLE`, índices, FK, tipos y nulabilidad con el SQL y las entidades.
2. Detener escrituras de ambos servicios y el recuperador. Evitar DDL simultáneo,
   otros operadores y el `ddl-auto=update` mientras se aplican los scripts.
3. Con el cliente MySQL y un perfil seguro de conexión ya existente, seleccionar
   **explícitamente** la base de catálogo para `09_catalogo.sql` y la de pedidos
   para `09_pedidos.sql`. No se crean bases, usuarios ni permisos en estos scripts.
   No usar `--force` ni opciones que continúen tras un error. No se incluyen
   comandos apuntando a las bases del equipo para evitar una ejecución accidental.
4. Conservar salida y hash de los archivos en el registro operativo. Se requiere
   permiso de CREATE/ALTER/INDEX/REFERENCES y CREATE/ALTER ROUTINE en cada base.
   No hay tabla de historial de Flyway: los nombres `09_*` identifican el cambio,
   pero no constituyen un sistema automático de versionado.
5. Verificar estructura, preservación de datos y JPA con DDL en modo `validate`,
   acordando ese cambio de configuración de despliegue con el equipo (no se cambia
   el valor `update` del repositorio en esta entrega). Reactivar servicios y probar
   compras, idempotencia, cancelación, reinicio y recuperación de pendientes.

MySQL hace commit implícito del DDL: **no hay rollback transaccional** de la migración.
Un fallo tardío (duplicados, huérfanos, nombre de índice ocupado, disco o permisos)
puede dejar pasos aditivos ya aplicados. No se borran datos para corregirlo; revisar
la causa, corregir con autorización y repetir. Si un `SIGNAL` interrumpe el script,
queda la rutina auxiliar `gas_volcan_v09_pedidos` o `gas_volcan_v09_catalogo`:
inspeccionarla y eliminar **solo esa rutina** antes del reintento; en caso exitoso
el script la elimina al terminar. Una rutina preexistente causa error al comenzar
para no sobrescribirla. No se deben ejecutar dos copias simultáneamente.

Los ALTER pueden bloquear o reconstruir tablas: medir tamaño, duración y espacio
libre en una copia antes de acordar la ventana. Estructuras antiguas con columnas
faltantes fuera del alcance, fechas sin precisión 6, tipos unsigned o nulabilidad
diferente se rechazan y requieren revisión; no se propone una conversión masiva.
No hay migración inversa destructiva: una vez que existan invitados no se puede
restablecer `cliente_id NOT NULL` sin decidir qué hacer con esas compras.
