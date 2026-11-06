# ms-catalogo: ejecucion, catalogo y verificacion

Microservicio independiente de Gas El Volcan: Java 21, Spring Boot 4.1.2,
Maven, MySQL 8.4 y puerto HTTP 8082. No necesita ms-pedidos para arrancar.

## Requisitos y configuracion

Ejecutar los comandos desde la carpeta `ms-catalogo`. Maven Wrapper esta incluido:
`./mvnw` en Linux/macOS y `.\mvnw.cmd` en Windows.

La configuracion del equipo apunta por defecto a `localhost:3306/volcan_catalogo`,
con usuario `volcan`, como el servicio MySQL de Docker Compose.
La contrasena se obtiene de `CATALOGO_DB_PASSWORD` o, si no esta definida,
de `MYSQL_PASSWORD`. No hay contrasena predeterminada en el repositorio.
La variable definida dentro del contenedor MySQL no se exporta automaticamente
al proceso Java: definirla tambien en el entorno de ejecucion del microservicio.

La conexion es configurable sin editar archivos:

| Variable | Valor predeterminado / uso |
| --- | --- |
| `CATALOGO_DB_HOST` | `localhost`; usar `mysql` si la app comparte la red Docker |
| `CATALOGO_DB_PORT` | `3306`; usar `3307` para el contenedor local alternativo |
| `CATALOGO_DB_NAME` | `volcan_catalogo` |
| `CATALOGO_DB_URL` | Opcional: reemplaza la URL JDBC completa |
| `CATALOGO_DB_USERNAME` | `volcan`; configurable si el entorno local utiliza otro usuario |
| `CATALOGO_DB_PASSWORD` | Obligatoria si no se define `MYSQL_PASSWORD` |
| `CATALOGO_PORT` | `8082` |
| `CATALOGO_DDL_AUTO` | `update`; usar `validate` para comprobar sin cambiar esquema |
| `CATALOGO_SHOW_SQL` | `false` |
| `CATALOGO_CARGA_INICIAL_ENABLED` | `true`; usar `false` para desactivar la carga |

No se debe subir un `.env`, credenciales ni respaldos de la base.
Spring Boot no lee archivos `.env` automaticamente: configurar las variables
en la terminal o el entorno de ejecucion. No incluir secretos en argumentos.

Spring Boot permite sustituir la conexion para otros entornos mediante
`SPRING_DATASOURCE_URL` y `SPRING_DATASOURCE_USERNAME`.
La propiedad de carga es `catalogo.carga-inicial.enabled`, activada por defecto y configurable mediante `CATALOGO_CARGA_INICIAL_ENABLED`.
El repositorio conserva `spring.jpa.hibernate.ddl-auto=update` para desarrollo:
puede crear o modificar tablas al arrancar. Para una base ya preparada, utilizar
`SPRING_JPA_HIBERNATE_DDL_AUTO=validate`, que verifica el esquema sin cambiarlo.
No se incluyen instrucciones de borrado o reinicio de MySQL.

Para arrancar, una vez configurada la conexion:

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.arguments="--spring.jpa.hibernate.ddl-auto=validate --catalogo.carga-inicial.enabled=false"
```

## Catalogo original y carga inicial

`src/main/resources/catalogo-original.json` conserva los datos originales del
frontend React: `src/datos/productos.js` y `src/datos/ofertas.js`.
Las categorias se derivan de los productos como en `src/datos/categorias.js`.
No es necesario incluir el frontend para ejecutar este microservicio.

El recurso contiene 4 categorias, 14 productos y 4 ofertas. Conserva codigos,
nombres, descripciones, precios residencial y comercial, precio y etiqueta de
oferta, stock, stock critico, unidad, imagen y estado activo.
El id original es informativo: MySQL genera los ids persistentes.
Las categorias no tienen descripcion original y se crean con descripcion nula.

`config/DatosIniciales` se activa automaticamente en un entorno nuevo.
Antes de un arranque real, revisar la conexion, los permisos y el esquema:
la configuracion por defecto permite insertar los registros faltantes.
Para inspecciones sin escrituras usar `CATALOGO_CARGA_INICIAL_ENABLED=false`
y `CATALOGO_DDL_AUTO=validate`, o sus propiedades equivalentes por argumento.
Para una base nueva, preparar el esquema mediante el procedimiento acordado;
`validate` no crea tablas. No activar la carga como parte de pruebas habituales.

El inicializador valida todo el recurso antes de escribir y utiliza una
transaccion. Busca categorias por nombre y productos por codigo. Inserta solo
faltantes: no actualiza precios, stock, ofertas ni otros atributos existentes.
Si falta un producto, puede completar el catalogo parcial. Si hay una colision
concurrente de claves unicas, falla la transaccion; no se realiza una limpieza.
Los nuevos campos de productos preexistentes tampoco se rellenan automaticamente.

La carga real de desarrollo fue verificada con MySQL 8.4: 4 categorias,
14 productos, 4 ofertas y ningun codigo duplicado. Los precios y el stock
coincidieron con el origen. Un arranque posterior con `validate` y carga
desactivada conservo todas las columnas e IDs. La repeticion del inicializador
activado y la preservacion de modificaciones posteriores se prueban con mocks y H2;
no se afirma haber repetido esa carga activada contra la base real.

## API, ofertas y disponibilidad

| Metodo | Ruta | Exito |
| --- | --- | --- |
| GET | `/api/v1/categorias` | 200 |
| GET | `/api/v1/categorias/{id}` | 200 |
| POST | `/api/v1/categorias` | 201 |
| PUT | `/api/v1/categorias/{id}` | 200 |
| DELETE | `/api/v1/categorias/{id}` | 204 |
| GET | `/api/v1/productos` | 200 |
| GET | `/api/v1/productos/{id}` | 200 |
| POST | `/api/v1/productos` | 201 |
| PUT | `/api/v1/productos/{id}` | 200 |
| DELETE | `/api/v1/productos/{id}` | 204 |
| GET | `/api/v1/productos/{id}/disponibilidad?cantidad=3` | 200 |

Los errores de negocio responden JSON `{ "estado": 400, "mensaje": "..." }`.
Datos invalidos y codigos duplicados responden 400; recursos inexistentes, 404.
No se pueden eliminar categorias que tienen productos asociados.
POST/PUT validan sus DTO; PUT reemplaza todos los campos, no es una actualizacion parcial.
La categoria de respuesta es un objeto; al escribir se recibe `categoriaId`.

Precios en CLP, no negativos y con hasta dos decimales. Los precios base
permanecen separados de `precioOferta`; nulo indica ausencia de oferta.
`etiquetaOferta` conserva el texto original. No se define una regla comercial
de descuento ni fechas de vigencia. `stockCritico` es opcional, `unidad` es
obligatoria e `imagen` puede ser vacia. Las imagenes no se sirven desde esta API.

La disponibilidad acepta cantidad entera positiva (por defecto 1).
`disponible` es verdadero si el producto esta activo y el stock alcanza.
Stock insuficiente o producto inactivo devuelven 200 con `disponible=false`.
Una cantidad invalida devuelve 400 y un producto inexistente, 404.
Esta consulta no reserva, descuenta ni garantiza stock para compras concurrentes.

## Swagger y CORS

Con el servicio iniciado:

- Swagger UI: `http://localhost:8082/swagger-ui.html`.
- OpenAPI JSON: `http://localhost:8082/v3/api-docs`.

Se verificaron las 11 operaciones, DTO y restricciones, configuracion de Swagger,
HTML, JavaScript y CSS mediante HTTP. Estas pruebas no incluyen inspeccion visual.

`config/CorsConfig` centraliza CORS para `/api/v1/**`. Permite por defecto
`http://localhost:5173` y `http://127.0.0.1:5173`; para otros entornos definir
`CATALOGO_CORS_ORIGINS` con una lista de origenes separados por comas.
No admite comodines ni listas vacias. Permite GET, POST, PUT, DELETE y OPTIONS;
encabezados Content-Type, Accept y Authorization; sin credenciales de navegador.
CORS no sustituye autenticacion o autorizacion.

## Pruebas automatizadas sin MySQL

```powershell
.\mvnw.cmd test
```

La suite Maven completa incluye la prueba de contexto de Spring Boot con
H2 en memoria (dependencia solo de pruebas). Sus propiedades sobreescriben la
conexion de desarrollo: no usa Docker, MySQL ni CATALOGO_DB_PASSWORD.
`create-drop` se utiliza exclusivamente para esa base H2 efimera, no MySQL.
La prueba de contexto basica desactiva la carga; la prueba de integracion de carga la activa en otra base H2 y verifica idempotencia y preservacion de precios/stock. H2 comprueba arranque y mapeo JPA;
no reemplaza la comprobacion especifica de compatibilidad con MySQL.
Las demas pruebas usan servicios/repositorios simulados y MockMvc.
Las rutas y Swagger tambien se comprueban con el contexto real de Spring y MockMvc, sin MySQL.

## Estructura y herramientas de entrega

El paquete principal sigue siendo `cl.gaselvolcan.ms_catalogo`.
Los subpaquetes son `model`, `repository`, `service`, `controller`, `dto`,
`exception` y `config`. Las pruebas reflejan esta convencion.

Maven Wrapper es la herramienta para compilar y probar. Los verificadores Python
de las etapas 5 y 6 se retiraron: no eran dependencias del microservicio ni de
las pruebas Java. Los logs y resultados previos permanecen locales en `target/`,
excluido de Git. No se incluyen credenciales ni herramientas ligadas a una maquina.

En esta revision no se ejecutan inserciones ni cambios sobre MySQL. Las pruebas
con escritura se limitan a bases H2 en memoria independientes.

## Resultado de esta revision

Suite completa `.\mvnw.cmd test`: 81 pruebas aprobadas, 0 fallos,
0 errores y 0 omitidas. Swagger y REST verificados con Spring/MockMvc.
No se ejecutaron conexiones ni escrituras sobre MySQL durante esta revision.
