# Autenticacion interna de stock

Configurar `INTERNAL_STOCK_JWT_SECRET` fuera de Git, en el entorno o `.env` de
catalogo y pedidos. Debe ser la misma en ambos, tener al menos 32 bytes UTF-8 y
ser distinta de `JWT_SECRET`. No hay una clave predeterminada: la ausencia,
una clave corta o la reutilizacion de la clave de usuarios impiden arrancar.
Las claves sinteticas del codigo de pruebas no son credenciales de despliegue.

Una cadena Spring Security con prioridad 0 se aplica exclusivamente a
`/internal/v1/stock/**`. El filtro interno solo pertenece a esa cadena y no se
registra como filtro servlet global. Las otras rutas conservan SeguridadConfig,
FiltroJwt y UsuarioToken existentes.

Contrato: HS256 obligatorio; `iss=ms-pedidos`, `sub=ms-pedidos`,
`aud` debe contener `ms-catalogo`; `iat` y `exp` obligatorios, sin emision futura,
con vigencia maxima de cinco minutos. Pedidos emite tokens de un minuto y jti
aleatorio. No usa claims de rol ni identificadores de usuario.

Permisos por ruta prevista:

| Metodo y ruta | scope requerido |
| --- | --- |
| POST `/internal/v1/stock/descuentos` | `stock:descontar` |
| GET `/internal/v1/stock/descuentos/{id}` | `stock:consultar` |
| PUT `/internal/v1/stock/descuentos/{id}/compensacion` | `stock:compensar` |

Resto de metodos y rutas internas: denegados. Token ausente o invalido: 401;
token valido sin permiso: 403. Los JWT de usuarios, incluso administradores,
no autentican en esta cadena. Los internos no autentican en las rutas existentes.

Los endpoints anteriores estan implementados por StockController. Las pruebas de
autenticacion aislada conservan controladores exclusivos de prueba; las de stock
ejercitan el controlador real, filtros, servicio y persistencia.

## Descuento y compensacion

POST requiere `Idempotency-Key` con un UUID y un cuerpo como:

```json
{"productos":[{"productoId":1,"cantidad":2},{"productoId":2,"cantidad":3}]}
```

Se permiten hasta 500 lineas, con IDs y cantidades enteras positivas. Se acumulan
productos repetidos con control de desbordamiento y se ordenan por ID. El UUID
es tambien el operacionId consultable. El orden y la division de cantidades en
lineas no cambian la identidad del contenido.

Respuesta de POST/GET/PUT: `operacionId`, `estado`, `productos` normalizados,
`codigoResultado`, `mensaje`, `creada` y `compensada` (nula antes de compensar).
POST aplicado responde 200. Repetir la misma clave/contenido devuelve el resultado
persistido, sin volver a tocar stock, incluso despues de compensar. Otra solicitud
con la misma clave devuelve 409. No se eliminan registros de idempotencia.

Estados: APLICADO, RECHAZADO y COMPENSADO. Un rechazo de negocio tambien se
persiste: producto inexistente 404, inactivo 400, stock insuficiente 409.
POST repetido conserva ese rechazo aunque luego cambie el inventario; para una
nueva compra se necesita una nueva clave. Solicitudes malformadas, cantidades
invalidas, claves ausentes o no UUID responden 400 sin registrar una operacion.
GET devuelve 200 para cualquier operacion existente, incluyendo rechazos, y
404 si no existe. El codigo original se conserva en codigoResultado.

Compensar usa las cantidades originales, no acepta cantidades del llamador y
responde 200. Solo permite APLICADO -> COMPENSADO; repetir COMPENSADO es seguro.
Un rechazo no se compensa (409); una operacion inexistente responde 404. Se
restituye tambien si el producto fue desactivado despues de comprar. Si fue
eliminado o la restitucion desbordaria el stock, responde 409 sin cambios
parciales y conserva APLICADO para resolver la incidencia. Nunca recrea productos.

La operacion y los cambios de stock se confirman en una transaccion local.
La PK de operaciones serializa claves repetidas; se bloquea la operacion y los
productos con PESSIMISTIC_WRITE, siempre en orden de ID, con READ_COMMITTED.
Producto incorpora una columna version para detectar ediciones administrativas
obsoletas (409), sin cambiar los contratos de producto. Conflictos de bloqueo
responden 503; reintentar con la misma clave. Fallos inesperados revierten toda
la transaccion y responden 500 sin detalles internos. Un timeout de red no prueba
que la transaccion fallara: consultar el UUID o repetir con la misma clave.

El esquema incorpora operaciones_stock, operacion_stock_productos y
productos.version (bigint con valor inicial 0). Antes de desplegar sobre datos
existentes, validar la actualizacion de esquema y el backfill de version en MySQL
con copia de respaldo. Las pruebas automatizadas de concurrencia usan H2;
queda pendiente comprobar bloqueos, migracion y recuperacion sobre MySQL real.
No usar escrituras SQL externas que evadan el control de version.

Pedidos todavia no invoca estas operaciones ni coordina la confirmacion de boletas.
Si compensacion llega antes de que exista una operacion devuelve 404; el
coordinador debe resolver descuentos en curso antes de compensar y no interpretar
ese 404 como prueba de que un descuento no se aplicara posteriormente.

Despliegue: usar HTTPS fuera del desarrollo local y proteger el puerto de catalogo.
No exponer ni registrar tokens o claves. Coordinar la rotacion de la clave entre
ambos servicios; no existe renovacion automatica de claves ni lista de revocacion.
El jti identifica el token, no sustituye la futura clave de idempotencia de compra.
No se han agregado rutas en Gateway ni cuentas administrativas de servicio.
