# Compras y stock

POST `/api/v1/boletas` exige `Idempotency-Key` con UUID estable, generado por el
consumidor antes del primer intento. Los reintentos usan esa misma clave y cuerpo.
Una compra distinta debe usar otra clave. No hay una ruta alternativa que cree
boletas sin acreditar stock. El metodo cotizar solo prepara importes, no persiste.

Se validan y normalizan direccion/comuna, estado inicial PENDIENTE y cantidades
agrupadas por producto en orden de ID (maximo 500 lineas). La clave y la solicitud
completa se guardan en operaciones_compra antes de consultar catalogo. Misma clave
y contenido diferente: 409. No se almacenan tokens ni claves de autenticacion.

Flujo tecnico, separado de los cuatro estados comerciales:

1. REGISTRADA: intencion durable, sin boleta. Se obtienen y validan precios/ofertas
   y disponibilidad fuera de una transaccion. Fallos de transporte quedan pendientes.
2. COTIZADA: cotizacion fija durable. Se consulta GET interno por el UUID; si no
   existe, se solicita POST con ese mismo Idempotency-Key y todos los productos.
3. Solo un resultado APLICADO con ID, cantidades y codigo coherentes permite
   guardar la boleta PENDIENTE. Boleta, detalles y estado CONFIRMADA se confirman
   en la misma transaccion local. operacion_stock_id es unico en boletas.
4. RECHAZADA: rechazo acreditado o validacion previa fallida, devuelve 400 sin
   boleta confirmada. Un rechazo de stock 409 de catalogo se traduce a 400.
5. COMPENSACION_PENDIENTE: intencion durable de restituir, nunca resultado final.
6. COMPENSADA: catalogo acredito COMPENSADO; reintentar no reactiva la compra.

HTTP de creacion: 201 solo tras confirmacion; reintentar una compra confirmada
devuelve la misma boleta, sin cotizar o descontar otra vez. 400 para solicitudes
invalidas/rechazos, 409 por clave reutilizada con otra solicitud o compra cancelada,
503 si el resultado sigue pendiente o hay otro trabajador en curso. Un 503 no
significa que no se desconto: conservar clave y cuerpo; no crear una compra nueva.

## Recuperacion y concurrencia

Se usan transacciones cortas REQUIRES_NEW y llamadas HTTP con NOT_SUPPORTED.
No se mantiene un bloqueo/transaccion de base de datos mientras se llama a catalogo.
Cada operacion tiene un trabajador y una concesion temporal durable de 30 segundos.
La PK serializa registros simultaneos; las actualizaciones bloquean la operacion.
Un trabajador reemplazado no puede confirmar ni cerrar una compensacion: se exige
coincidencia de su identificador y del estado tecnico esperado en cada commit.

Si una llamada falla se consulta el resultado con la misma clave. Si sigue siendo
desconocido se conserva el pendiente; ni un timeout ni GET/PUT 404 acreditan que
un descuento no se aplicara. No se envia un nuevo descuento desde la fase de
compensacion. Los reintentos HTTP tardios son seguros por la idempotencia de catalogo
y la verificacion local del trabajador vigente. No hay compensacion especulativa.

Recuperacion programada, incluida tras reiniciar: cada 30 segundos se procesa un
lote de hasta 50 operaciones pendientes cuya concesion termino. El orden por ultima
revision evita que fallos antiguos monopolizen todos los lotes. Si la base o catalogo
siguen caidos las operaciones permanecen pendientes hasta recuperarlos. Un fallo al
persistir la boleta despues del descuento conserva COTIZADA y su cotizacion original;
la recuperacion consulta catalogo y vuelve a intentar confirmar sin descontar de nuevo.
No se abandona una compra ni se compensa solo por agotar un numero de intentos.

Configuracion no secreta:

- PEDIDOS_RECUPERACION_ENABLED: true por defecto; mantener true en despliegue.
- PEDIDOS_RECUPERACION_INTERVALO_MS: 30000 por defecto.

Los logs identifican el UUID y tipo de fallo, sin volcar solicitud, tokens ni secretos.
Pendientes persistentes requieren seguimiento operativo; una boleta que nunca pueda
guardarse conserva su descuento y necesita resolver la incidencia, no eliminar el registro.

## Cancelacion, edicion y consulta

El endpoint de estado y sus permisos se conservan. Al cancelar una boleta con
descuento registrado, CANCELADO y COMPENSACION_PENDIENTE se guardan atomicamente.
El trabajador de recuperacion consulta y solicita PUT de compensacion. Si la respuesta
se pierde, consulta/reintenta sin devolver existencias dos veces. Cancelar de nuevo una
boleta vinculada ya cancelada es idempotente. La cancelacion comercial no afirma que
la restitucion ya termino.

GET `/api/v1/boletas/{id}/stock` devuelve operacionId, estadoTecnico y
stockRestituido (true solo en COMPENSADA), con la misma autorizacion de consulta
y asignacion por repartidor. Boletas historicas: SIN_REGISTRO, sin restitucion automatica.

Boletas vinculadas a operaciones de stock no admiten PUT general: no se cambian
productos, cantidades, precios ni otros campos. No se ha acordado una lista de campos
administrativos editables; siguen disponibles las transiciones autorizadas del estado.
El PUT legado conserva su contrato para boletas PENDIENTE sin registro de stock,
con consultas fuera de transacciones y revalidacion local antes de guardar.
Versionado de Boleta detecta cambios concurrentes y responde 409. DELETE sigue ausente.

## Despliegue y limites de verificacion

Se agrega operaciones_compra y las columnas boletas.operacion_stock_id (unica,
nullable para datos historicos) y boletas.version (valor inicial 0). Validar migracion,
backfill, indices y concurrencia sobre MySQL antes de desplegar; no eliminar pendientes
ni restablecer claves de idempotencia. Mantener relojes sincronizados y copias de respaldo.

Las pruebas usan persistencia H2 real, fallos de BD y transporte HTTP simulado;
reconstruyen el coordinador sobre los registros guardados y simulan concesiones vencidas
y trabajadores retrasados. Esto no sustituye una prueba de reinicio de procesos completos
con MySQL y ambos servicios desplegados.

La clave interna y HTTPS siguen siendo requisitos del transporte. Gateway no se
modifico: su CORS actual no permite Idempotency-Key. Antes de conectar el navegador,
el responsable de Gateway debe permitir esa cabecera. Las llamadas directas de pruebas
no comprueban CORS del navegador.
