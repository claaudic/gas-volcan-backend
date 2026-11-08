# ms-pedidos - boletas y detalles

Spring Boot 4.1.2, Java 21 y Maven. Puerto 8083 y base `volcan_pedidos`.
Incluye entidades, repositorios, DTO, servicio transaccional, controlador REST y
manejo centralizado de errores y consultas REST a ms-catalogo. No reserva ni
descuenta stock. La disponibilidad es informativa y puede cambiar tras consultarla.

## Configuracion

| Variable | Valor predeterminado |
| --- | --- |
| `PEDIDOS_PORT` | `8083` |
| `PEDIDOS_DB_HOST` | `localhost`; usar `mysql` dentro de la red Docker |
| `PEDIDOS_DB_PORT` | `3306`; en la prueba local se utilizo `3309` |
| `PEDIDOS_DB_NAME` | `volcan_pedidos` |
| `PEDIDOS_DB_URL` | Opcional: sustituye la URL JDBC completa |
| `PEDIDOS_DB_USERNAME` | `volcan` |
| `PEDIDOS_DB_PASSWORD` | Sin valor predeterminado; alternativa `MYSQL_PASSWORD` |
| `PEDIDOS_DDL_AUTO` | `update`; usar `validate` para revisar sin cambiar esquema |
| `PEDIDOS_SHOW_SQL` | `false` |
| `PEDIDOS_CATALOGO_URL` | `http://localhost:8082`; usar el host del servicio al ejecutar en Docker |
| `PEDIDOS_CATALOGO_CONNECT_TIMEOUT` | `2s` |
| `PEDIDOS_CATALOGO_READ_TIMEOUT` | `5s` |

Definir las credenciales en el entorno de Java; no se copian automaticamente
las variables del contenedor MySQL. No subir `.env`, claves, logs ni respaldos.
El valor predeterminado `update` puede crear o modificar el esquema. Despues de
crear las tablas se utiliza `PEDIDOS_DDL_AUTO=validate`: comprueba que el esquema
sea compatible, sin crearlo ni actualizarlo. Si faltan tablas, `validate` falla;
la inicializacion debe prepararse y autorizarse por separado.

## Ejecucion con Java 21 y Maven Wrapper

Se necesita un JDK 21 disponible en `JAVA_HOME` o en `PATH`. El repositorio incluye
Maven Wrapper: no es necesario instalar Maven por separado. Desde `ms-pedidos`,
comprobar las versiones:

```powershell
java -version
.\mvnw.cmd -version
```

MySQL debe estar funcionando, con la base `volcan_pedidos`, el usuario configurado
y las tablas creadas. Para crear o reemplazar boletas tambien debe responder
ms-catalogo; listar y consultar boletas no requiere llamar a catalogo.

Ejemplo de configuracion temporal en PowerShell, sin contrasenas en el comando.
La solicitud interactiva debe recibir la credencial del entorno que corresponda;
no utilizar una credencial real como texto de ejemplo ni guardarla en el README.

```powershell
$env:PEDIDOS_PORT = '8083'
$env:PEDIDOS_DB_HOST = '127.0.0.1'
$env:PEDIDOS_DB_PORT = '3309'
$env:PEDIDOS_DB_NAME = 'volcan_pedidos'
$env:PEDIDOS_DB_USERNAME = 'volcan'
$env:PEDIDOS_DDL_AUTO = 'validate'
$env:PEDIDOS_CATALOGO_URL = 'http://localhost:8082'

# Evitar que una URL JDBC anterior tenga prioridad sobre HOST, PORT y NAME.
Remove-Item Env:PEDIDOS_DB_URL -ErrorAction SilentlyContinue

$credencialPedidos = Read-Host 'Contrasena MySQL del entorno de pruebas' -AsSecureString
$punteroPedidos = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($credencialPedidos)
try {
    $env:PEDIDOS_DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($punteroPedidos)
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($punteroPedidos)
    $credencialPedidos.Dispose()
}

try {
    .\mvnw.cmd spring-boot:run
} finally {
    Remove-Item Env:PEDIDOS_DB_PASSWORD -ErrorAction SilentlyContinue
}
```

Detener con `Ctrl+C`. Las variables del ejemplo afectan a esa sesion de PowerShell
y a sus procesos hijos; no se guardan en archivos ni como variables permanentes.
La contrasena se transmite a Java mediante el entorno, sin escribirla en sus
argumentos. Si `PEDIDOS_DB_URL` se define, sustituye la URL JDBC construida con
HOST, PORT y NAME.

El servicio atiende en `http://localhost:8083/api/v1/boletas`. En las pruebas
locales se utilizo MySQL en `127.0.0.1:3309/volcan_pedidos` y catalogo en
`http://localhost:8082`. El puerto MySQL predeterminado del equipo sigue siendo
3306; dentro de una red Docker deben configurarse los nombres de host apropiados
en lugar de asumir que `localhost` identifica otro contenedor.

Como alternativa, compilar y ejecutar el JAR generado:

```powershell
.\mvnw.cmd -B package
# Con las mismas variables de entorno y PEDIDOS_DB_PASSWORD ya suministrada:
java -jar .\target\ms-pedidos-0.0.1-SNAPSHOT.jar
```

El JAR pertenece a `target` y no se incluye en el commit. La credencial debe
retirarse de la sesion al terminar, como en el bloque `finally` del ejemplo.

## Modelo

- `Boleta`: clienteId, direccion, comuna, estado, repartidorId opcional, fecha,
  total y detalles. Cliente/repartidor son IDs externos, sin FK a ms-usuarios.
- `DetalleBoleta`: productoId, cantidad, precio unitario historico y boleta.
  Solo boleta_id es una FK interna. No hay entidad Producto ni acceso a volcan_catalogo.
- Relacion 1:N bidireccional con persistencia en cascada y eliminacion de huerfanos.
  Agregar/quitar lineas mediante los metodos de Boleta mantiene ambos extremos.
- Las boletas requieren al menos un detalle y admiten dos o mas productos.
- Importes BigDecimal con precision 19 y escala 2, no negativos; cantidades e IDs
  positivos. El servicio calcula el total como suma de cantidad por precio y
  rechaza resultados que no caben en DECIMAL(19,2).
- Estado es texto obligatorio: valores y transiciones pendientes de la etapa de negocio.
- Tablas separadas `boletas` y `detalles_boleta`: no se repite la cabecera en cada linea.
  El total de cabecera se conserva por el requisito de Trello.

Los paquetes siguen `cl.gaselvolcan.ms_pedidos` con `model` y `repository`.
Las capas implementadas son `model`, `repository`, `service`, `controller`, `dto`
y `exception`. `config` configura el cliente HTTP y `client` encapsula el contrato remoto.

## Compilacion y pruebas

Desde `ms-pedidos`, en PowerShell:

```powershell
.\mvnw.cmd test
.\mvnw.cmd -B package
```

En Linux/macOS: `bash ./mvnw test`.
Las pruebas de persistencia arrancan Spring con H2 efimera en memoria y una
transaccion con rollback por prueba; no necesitan MySQL, Docker, catalogo ni usuarios.
`create-drop` se limita a H2 de pruebas. Se comprueban dos productos, repositorios,
FK interna, precision monetaria y cascadas. H2 no sustituye la comprobacion de
compatibilidad MySQL; esta tambien se realizo mediante la prueba real descrita abajo.

Datos sinteticos de pruebas: no son ventas ni precios del catalogo original.
No se incluyen datos iniciales. En pruebas, todas las consultas a catalogo se simulan;
MockRestServiceServer verifica rutas, parametros, JSON, estados y fallos de transporte.

## API de boletas

| Metodo | Ruta | Respuesta exitosa |
| --- | --- | --- |
| GET | `/api/v1/boletas` | 200 con lista, incluso vacia |
| GET | `/api/v1/boletas/{id}` | 200 con cabecera y detalles |
| POST | `/api/v1/boletas` | 201 con boleta creada |
| PUT | `/api/v1/boletas/{id}` | 200 con boleta reemplazada |
| PUT | `/api/v1/boletas/{id}/estado` | 200 con el nuevo estado |
| DELETE | `/api/v1/boletas/{id}` | Deshabilitado: 401/403/405 segun autenticacion y rol |

### Conservacion y cancelacion

La API no permite borrar fisicamente boletas ni sus detalles. Spring Security
procesa primero la peticion, sin cambios en la configuracion JWT: DELETE devuelve
401 sin autenticacion valida, 403 para roles sin permiso y 405 Method Not Allowed
para ADMINISTRADOR autenticado, porque el controlador no ofrece ese metodo.
El 405 conserva la cabecera Allow generada por Spring MVC. Ninguno de estos
intentos elimina boletas ni detalles, incluso si el ID no existe.

Para cancelar se utiliza `PUT /api/v1/boletas/{id}/estado` con
`{"estado":"CANCELADO"}`, respetando los permisos y la transicion
PENDIENTE -> CANCELADO. La boleta y sus detalles permanecen almacenados.
Todavia no se implementa restitucion de stock.

Los errores devuelven `{ "estado": 400, "mensaje": "..." }`. Datos invalidos,
IDs no positivos, producto inactivo, stock insuficiente y total fuera de precision
responden 400; boletas o productos inexistentes, 404. Catalogo no disponible o
tiempo de espera agotado responde 503; respuesta remota inesperada o incompleta, 502. JSON invalido responde 400; metodo no admitido 405; contenido no JSON 415.
Los errores inesperados responden 500 sin divulgar detalles internos.

POST/PUT requieren clienteId positivo, direccion, comuna y fecha,
ademas de una lista no vacia de detalles validados. repartidorId es opcional.
Cada linea requiere productoId positivo y cantidad entera positiva. El precio se
obtiene exclusivamente del catalogo; cualquier atributo `precio` enviado se ignora. Se admiten dos o mas productos y tambien una
sola linea, manteniendo el requisito de detalles no vacios.

Solicitud sintetica de ejemplo (no es una venta ni utiliza precios reales):

```json
{
  "clienteId": 10,
  "direccion": "Direccion de prueba 123",
  "comuna": "Comuna de prueba",
  "estado": "PENDIENTE",
  "repartidorId": null,
  "fecha": "2026-01-01T12:00:00",
  "detalles": [
    {"productoId": 91001, "cantidad": 2},
    {"productoId": 91002, "cantidad": 3}
  ]
}
```

El resultado depende de los precios reales de esos IDs en catalogo. Los IDs del
ejemplo son sinteticos y no se garantiza que existan. `total` no es un campo del DTO de solicitud:
si el JSON incluye ese atributo, se ignora expresamente. No se confia en el
total enviado por el cliente. El DTO de respuesta si incluye el total calculado.

POST y PUT consultan `GET /api/v1/productos/{id}` y
`GET /api/v1/productos/{id}/disponibilidad?cantidad=X`. Todas las lineas se
validan antes de alterar la entidad. Las cantidades de IDs repetidos se suman
para consultar disponibilidad y cada producto se consulta una vez por operacion.
Los productos deben existir, estar activos y tener stock suficiente.

Politica de precio vigente: se utiliza `precioOferta` si no es nulo; en otro
caso, `precioResidencial`. No se calculan descuentos adicionales. El contrato
actual no identifica un tipo de cliente para seleccionar tarifa comercial.
El precio aplicado queda como historico en cada detalle. Un PUT completo vuelve
a validar y valorar todas las lineas con los precios actuales. GET no
consultan catalogo; el arranque tampoco necesita conectarse a ese servicio.
No hay acceso a volcan_catalogo ni relaciones JPA externas. No se reservan ni
descuentan existencias, por lo que no se garantiza stock frente a ventas concurrentes.
La consulta del producto y la de disponibilidad no constituyen una instantanea atomica.
No se valida aun la existencia del cliente o repartidor.
La fecha proviene de la solicitud. El estado inicial es PENDIENTE; se permiten
PENDIENTE -> EN_CAMINO -> ENTREGADO y PENDIENTE -> CANCELADO.
ENTREGADO y CANCELADO son estados finales. El PUT general solo admite
boletas PENDIENTE y no permite cambiar el estado.

PUT reemplaza toda la cabecera y todas las lineas, recalcula el total, conserva
el ID de boleta y genera nuevos IDs de detalle. Las lineas anteriores se eliminan
por orphanRemoval dentro de la transaccion. DELETE esta deshabilitado para
conservar el historial; la cancelacion se realiza mediante el estado CANCELADO.
Los DTO evitan serializar entidades y referencias circulares.

Las pruebas de etapa 2 cubren servicio, validacion DTO, errores HTTP y CRUD
completo con Spring/JPA sobre H2, incluida la eliminacion de huerfanos y la
comprobacion de que un total manipulado en JSON no se guarda. Las 11 pruebas
originales de entidades y repositorios se conservan sin modificaciones.

Las pruebas de etapa 3 conservan la cobertura anterior, adaptando los casos de
precios provisionales al nuevo contrato. Se verifican precios del catalogo,
ofertas publicadas, cantidades repetidas, disponibilidad, errores remotos,
respuestas inconsistentes y ausencia de escrituras parciales. Las pruebas Java
no necesitan MySQL real ni un ms-catalogo en ejecucion.

## Evidencia de integracion real con MySQL y catalogo

La prueba real se completo el 6 de noviembre de 2026, con ambos servicios
iniciados temporalmente y JPA en `validate`. MySQL de pedidos se consulto en
`127.0.0.1:3309`, catalogo REST en `http://localhost:8082` y su MySQL en el puerto
3307. La carga inicial del catalogo se mantuvo desactivada. Estos son datos de
verificacion del entorno de pruebas, no requisitos de infraestructura del equipo.

Se envio exactamente un POST con cliente y direccion ficticios, sin precios ni
total en la solicitud. El backend consulto catalogo, comprobo actividad y
existencias y calculo los importes con `BigDecimal`.

| Comprobacion | Resultado |
| --- | --- |
| Boleta creada | ID `1` |
| POST `/api/v1/boletas` | HTTP `201` |
| GET `/api/v1/boletas/1` | HTTP `200`, coincide con la respuesta del POST |
| CL001, producto ID `1`, cantidad `1` | Precio aplicado `$6.500` |
| CL002, producto ID `2`, cantidad `1` | Precio de oferta aplicado `$10.500` |
| Total calculado | `$17.000` |
| SELECT en MySQL | `1` boleta y `2` detalles relacionados |
| SUM de cantidad por precio | `$17.000`, coincide con el total de cabecera |
| Catalogo despues de la prueba | Precios e IDs sin cambios; stock CL001 `80` y CL002 `200` |

La boleta se conservo como evidencia; no se repitio el POST ni se elimino el
registro automaticamente. Se detuvieron unicamente los procesos Java iniciados
para la prueba. Los datos de MySQL no se distribuyen como archivos del proyecto.
Los contadores `AUTO_INCREMENT` avanzaron al insertar la boleta y los detalles;
ese avance normal no representa un cambio de columnas o de relaciones JPA.

La disponibilidad de stock es informativa: consultar o crear una boleta no
reserva ni descuenta existencias en catalogo. El stock puede cambiar por otras
operaciones despues de consultarlo. Esta integracion no garantiza una reserva
frente a ventas concurrentes.

La ultima ejecucion completa de `.\mvnw.cmd -B package` termino con `BUILD SUCCESS`:
67 pruebas, 0 fallos, 0 errores y 0 omitidas. La suite automatizada utiliza H2 y
HTTP simulado y no requiere MySQL real ni catalogo en ejecucion.
