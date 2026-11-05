# ms-catalogo: ejecucion, catalogo y verificacion

Microservicio independiente de Gas El Volcan: Java 21, Spring Boot 4.1.2,
Maven, MySQL 8.4 y puerto HTTP 8082. No necesita ms-pedidos para arrancar.

## Requisitos y configuracion

Ejecutar los comandos desde la carpeta `ms-catalogo`. Maven Wrapper esta incluido:
`./mvnw` en Linux/macOS y `.\mvnw.cmd` en Windows.

La configuracion de desarrollo apunta a `localhost:3307/volcan_catalogo`,
con usuario `root`. La contrasena se suministra exclusivamente mediante
`CATALOGO_DB_PASSWORD`; no hay contrasena predeterminada en el repositorio.
No se debe subir un `.env`, credenciales ni respaldos de la base.
Spring Boot no lee archivos `.env` automaticamente: configurar las variables
en la terminal o el entorno de ejecucion. No incluir secretos en argumentos.

Spring Boot permite sustituir la conexion para otros entornos mediante
`SPRING_DATASOURCE_URL` y `SPRING_DATASOURCE_USERNAME`.
La propiedad de carga es `catalogo.carga-inicial.enabled`, desactivada por defecto.
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

`config/DatosIniciales` solo se activa expresamente con
`--catalogo.carga-inicial.enabled=true`. Activarlo implica posibles inserciones:
revisar y autorizar antes la conexion, el estado y el esquema de la base.
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
activado y la preservacion de modificaciones posteriores se prueban con mocks;
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
python -B -m unittest -v test_verificar_etapa5.py
```

La suite Maven completa incluye la prueba de contexto de Spring Boot con
H2 en memoria (dependencia solo de pruebas). Sus propiedades sobreescriben la
conexion de desarrollo: no usa Docker, MySQL ni CATALOGO_DB_PASSWORD.
`create-drop` se utiliza exclusivamente para esa base H2 efimera, no MySQL.
La carga inicial permanece desactivada. H2 comprueba arranque y mapeo JPA;
no reemplaza la comprobacion especifica de compatibilidad con MySQL.
Las demas pruebas usan servicios/repositorios simulados y MockMvc.
Las pruebas Python tampoco ejecutan Docker o Java: verifican los auxiliares con mocks.

## Verificadores opcionales contra MySQL existente

Los scripts Python son herramientas manuales; Maven no los ejecuta.
Requieren Python 3.10+, Docker CLI, Java 21 y el JAR generado con
`.\mvnw.cmd package`. El contenedor debe ser MySQL con su puerto 3306 publicado.
No contienen rutas personales, nombres de contenedor de una maquina ni claves.
Configurar externamente:

| Variable | Uso |
| --- | --- |
| `CATALOGO_VERIFY_CONTAINER` | Nombre exacto del contenedor autorizado; obligatoria |
| `CATALOGO_VERIFY_MYSQL_PORT` | Puerto MySQL publicado en localhost; obligatorio |
| `CATALOGO_VERIFY_DB_USER` | Usuario MySQL; obligatorio |
| `CATALOGO_DB_PASSWORD` | Clave del usuario; obligatoria, nunca se imprime |
| `CATALOGO_VERIFY_DATABASE` | Base; por defecto `volcan_catalogo` |
| `CATALOGO_VERIFY_API_PORT` | Puerto HTTP local; por defecto 8082 |

```powershell
python -B .\verificar_etapa5.py --solo-reinicio
python -B .\verificar_etapa6.py
```

Los verificadores inspeccionan contenedor y puerto, ejecutan SELECT y GET,
arrancan su propio Java con `validate`, carga inicial e inicializadores SQL
desactivados y comparan todas las filas e IDs antes/despues.
Se detienen si el puerto HTTP esta ocupado. Solo detienen el Java que iniciaron.
La clave se proporciona por entorno a Java y por entrada estandar al cliente MySQL,
nunca en argumentos o logs. La comunicacion MySQL usa utf8mb4 y Docker se decodifica en UTF-8.
No extraen automaticamente credenciales del contenedor.

Estas herramientas comprueban el catalogo original sin modificaciones; si ya se
editaron precios o stock, pueden informar diferencias sin sobrescribirlas.
Los logs y el OpenAPI capturado quedan en `target/`, excluido de Git.
Ante un fallo se conservan los datos; no se borran, recrean ni recargan tablas.

## Resultado de preparacion de entrega

Se ejecuto `.\mvnw.cmd test` sin seleccionar pruebas: 79 aprobadas,
0 fallos, 0 errores y 0 omitidas. Las 8 pruebas auxiliares Python tambien pasaron.
Los verificadores manuales parametrizados se probaron con mocks en esta revision;
no se volvieron a ejecutar contra MySQL. La base existente no fue modificada.
