# Tarjeta 08 — Swagger y rutas versionadas

Entrega de tarjeta 08 en `feature/swagger-rutas-versionadas`. El usuario confirmó
la revisión de los cambios y autorizó publicar esta rama, sin merge a main ni PR
nuevo automático. El checklist real fue proporcionado por el usuario mediante
capturas, según su confirmación; este documento no agrega requisitos de aceptación.

## Auditoría previa

- Los tres servicios y Gateway usan Spring Boot 4.1.2 y Java 21.
- Catálogo ya tenía Springdoc 3.2.0, UI, permiso para documentación JSON,
  anotaciones de productos/categorías y una prueba básica de OpenAPI.
- Usuarios y pedidos no tenían Springdoc ni configuración de documentación.
- Todas las rutas REST de negocio ya usaban `/api/v1/`; no se renombró ninguna.
- Gateway conservaba sus tres rutas de negocio, CORS para Authorization e
  Idempotency-Key y Actuator. No tenía agregación de OpenAPI ni UI central.
- El stock interno tenía cadena de seguridad independiente y JWT de servicio.
  No debe presentarse como JWT de cliente ni exponerse a través de Gateway.

Se mantiene Springdoc 3.2.0, compatible con Boot 4.1.x según la
[matriz oficial](https://springdoc.org/#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot).
Usuarios y pedidos usan `springdoc-openapi-starter-webmvc-ui`; Gateway usa
`springdoc-openapi-starter-webflux-ui`. No se duplicó la dependencia de catálogo.

## Acceso a documentación

Las siguientes direcciones usan los puertos predeterminados para desarrollo. En
Docker los microservicios siguen sin publicar sus puertos: acceder por Gateway.

| Componente | Swagger UI | OpenAPI JSON | OpenAPI YAML |
|---|---|---|---|
| Usuarios | `http://localhost:8081/swagger-ui.html` | `/v3/api-docs` | `/v3/api-docs.yaml` |
| Catálogo | `http://localhost:8082/swagger-ui.html` | `/v3/api-docs` | `/v3/api-docs.yaml` |
| Pedidos | `http://localhost:8083/swagger-ui.html` | `/v3/api-docs` | `/v3/api-docs.yaml` |
| Gateway | `http://localhost:8080/swagger-ui.html` | Ver tres rutas siguientes | No se añade proxy YAML |

Desde Gateway, las tres rutas GET `/openapi/usuarios`, `/openapi/catalogo` y
`/openapi/pedidos` reenvían a `/v3/api-docs` del servicio correspondiente. No
permiten escoger otra ruta de destino ni enviar POST. La UI central ofrece
esas tres especificaciones. Los `servers` de OpenAPI usan `/`, para que las
peticiones de Try it out utilicen el mismo origen, directo o Gateway, y las
rutas de negocio originales. No se incorporan hosts internos a la especificación.

Catálogo también ofrece localmente `/v3/api-docs/stock-interno`. Su UI local
permite elegir entre “Catalogo publico” y “Stock interno”. El grupo interno
solo incluye `/internal/v1/stock/**`, usa `stockServicioJWT` y explica issuer,
audience, clave independiente y scopes. El JSON público y el Gateway no incluyen
esas operaciones; tampoco se agregó una ruta Gateway a la especificación interna.

Actuator, Swagger, OpenAPI y stock interno conservan sus prefijos técnicos. No
se intentó convertirlos a `/api/v1/`.

## Uso de Authorize y contratos

1. Obtener un JWT de usuario con POST `/api/v1/auth/login`.
2. Abrir Swagger y elegir la especificación deseada en Gateway.
3. Pulsar **Authorize**, ingresar solo el token (sin escribir `Bearer `) y cerrar
   el diálogo. El esquema HTTP Bearer agrega el encabezado Authorization.
4. Usar Try it out con el cuerpo y parámetros documentados. Para comprar se
   exige `Idempotency-Key` UUID; conservar clave y contenido en los reintentos.

Las consultas de catálogo, login y registro se documentan como públicas. Renovar
requiere Authorization válido aunque la ruta no exija sesión en el filtro.
Los usuarios conservan permisos administrativos y de perfil propio. En pedidos,
la compra admite invitado o JWT; las consultas y cambios mantienen sus permisos,
propiedad de boletas y reglas de la tarjeta 09. Los datos de invitado son
condicionalmente obligatorios y clienteId no prevalece sobre un JWT CLIENTE.

Los parámetros y cuerpos se derivan de los controladores/DTO reales. Se agregaron
resúmenes, requisitos de seguridad, permisos y respuestas de error por operación.
Se conservan las anotaciones de productos y categorías. No se cambian filtros,
claims, firmas, credenciales, puertos, contratos, reglas de negocio ni repositorios.
La única ampliación de acceso en seguridad corresponde a rutas de documentación:
GET Swagger/OpenAPI en usuarios y pedidos, y YAML en catálogo.

La documentación interna distingue los rechazos durables de stock del formato
`ErrorRespuesta`: en POST se declara `oneOf` cuando ambos pueden ocurrir. No se
reutiliza el esquema `bearerJWT` de usuario para operaciones de servicio.

## Checklist y evidencia

| Requisito solicitado | Resultado comprobado |
|---|---|
| Springdoc en los tres microservicios | Compilación y generación OpenAPI verificadas |
| Swagger UI disponible | Redirección, HTML, JS y swagger-config HTTP 200 en las pruebas |
| Métodos, rutas, parámetros, cuerpos y respuestas reales | Especificaciones comparadas con los mappings Spring MVC reales |
| Rutas de negocio `/api/v1/` | Verificadas en las tres especificaciones; sin cambios de rutas |
| Rutas públicas y protegidas identificadas | `security` por operación, permisos y errores documentados |
| HTTP Bearer JWT | Esquema `type=http`, `scheme=bearer`; JWT válido/ausente/inválido y roles comprobados |
| Authorize visual | El usuario confirmó en Brave registro 201, login 200, perfil con JWT 200 y administración con CLIENTE 403 |
| JWT interno separado | Grupo de stock independiente, sin JWT CLIENTE ni proxy Gateway |
| Documentación desde Gateway | Tres rutas fijas y UI con las tres URLs verificadas |
| Conservar Spring Security y permisos | Suites existentes más pruebas de login, perfil y 403 aprobadas |
| Conservar Docker y tarjeta 09 | Sin cambios Docker, filtros JWT, servicios o repositorios; regresión aprobada |

Primera ejecución completa de las suites con Maven y datos sintéticos en H2 en memoria:

| Módulo | Pruebas aprobadas | Fallos | Errores | Omitidas |
|---|---:|---:|---:|---:|
| ms-usuarios | 64 | 0 | 0 | 0 |
| ms-catalogo | 111 | 0 | 0 | 0 |
| ms-pedidos | 143 | 0 | 0 | 0 |
| Gateway | 14 | 0 | 0 | 0 |
| **Total** | **332** | **0** | **0** | **0** |

Comando reproducible desde la raíz:

```sh
bash ms-pedidos/mvnw -f pom.xml package
```

La prueba de arranque de usuarios se aisló en H2 y se sustituyó su carga inicial
por un mock solo en tests, para que la suite completa no acceda a MySQL local.
La dependencia H2 de usuarios tiene scope `test`. No se modifica su carga inicial
de producción. Los demás tests de negocio conservan sus bases H2 efímeras.

Además se ejecutaron cuatro procesos reales (tres servicios y Gateway) con puertos
locales aleatorios y bases H2 en memoria. Se verificaron por HTTP las tres
especificaciones, la UI central, registro y login sintéticos, perfil propio (200),
listado administrativo con CLIENTE (403), boletas con JWT (200), boletas sin sesión
(401) y productos públicos (200). Todos pasaron. Los cuatro procesos terminaron;
no se utilizaron contenedores ni bases compartidas. No se guardan tokens o logs
en el repositorio.

El usuario confirmó personalmente los flujos de Authorize y Try it out desde
Brave después de corregir CORS y confirmó visualmente que ErrorRespuesta ya resuelve
en Usuarios. La herramienta de Computer Use no tenía permisos: la confirmación
visual proviene del usuario. Catálogo, Pedidos y stock interno tienen sus JSON y
referencias verificados automáticamente; no se atribuye una confirmación visual
adicional al usuario. Falta comprobar EC2 y cotejar los 13 puntos exactos de Trello,
cuyo texto no está disponible en el repositorio ni en el mensaje de la tarea.

## Archivos de la entrega

- `gateway/pom.xml`, `gateway/src/main/resources/application.properties`,
  `gateway/src/test/java/cl/gaselvolcan/gateway/RutasGatewayTest.java`.
- `ms-usuarios/pom.xml`, `ms-usuarios/src/main/resources/application.properties`,
  `ms-usuarios/src/main/java/cl/gaselvolcan/usuarios/config/{OpenApiConfig,SeguridadConfig}.java`,
  `ms-usuarios/src/main/java/cl/gaselvolcan/usuarios/controller/{AuthController,UsuarioController}.java`,
  `ms-usuarios/src/test/java/cl/gaselvolcan/usuarios/MsUsuariosApplicationTests.java`,
  `ms-usuarios/src/test/java/cl/gaselvolcan/usuarios/config/OpenApiIntegrationTest.java`.
- `ms-catalogo/src/main/resources/application.properties`,
  `ms-catalogo/src/main/java/cl/gaselvolcan/ms_catalogo/config/{OpenApiConfig,SeguridadConfig}.java`,
  `ms-catalogo/src/main/java/cl/gaselvolcan/ms_catalogo/controller/StockController.java`,
  `ms-catalogo/src/test/java/cl/gaselvolcan/ms_catalogo/config/OpenApiIntegrationTest.java`.
- `ms-pedidos/pom.xml`, `ms-pedidos/src/main/resources/application.properties`,
  `ms-pedidos/src/main/java/cl/gaselvolcan/ms_pedidos/config/{OpenApiConfig,SeguridadConfig}.java`,
  `ms-pedidos/src/main/java/cl/gaselvolcan/ms_pedidos/controller/BoletaController.java`,
  `ms-pedidos/src/test/java/cl/gaselvolcan/ms_pedidos/config/OpenApiIntegrationTest.java`.
- Este documento: `docs/TARJETA08-SWAGGER.md`.

## Regresión CORS encontrada en la prueba manual

El navegador enviaba `Origin: http://127.0.0.1:53755` al Gateway de prueba.
`servers: /` era correcto: Try it out llamaba al mismo origen. Sin embargo, el
proxy sustituía Host por el host/puerto interno de usuarios. Spring CORS en
usuarios comparaba ambos y rechazaba la solicitud con `403 Invalid CORS request`
antes de llegar al controlador. La primera prueba HTTP sin Origin no lo detectó.

Se añadió únicamente el filtro estándar `PreserveHostHeader` a los filtros por
defecto del Gateway. Se conserva Host en el salto al servicio, sin quitar Origin,
sin ampliar listas CORS y sin cambiar filtros ni permisos JWT. Las reglas para
React y el filtro DedupeResponseHeader se mantienen. Referencia:
[Spring Cloud Gateway: PreserveHostHeader](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway-server-webflux/gatewayfilter-factories/preservehostheader-factory.html).

La prueba de regresión del Gateway ahora aplica el procesador CORS de Spring en
el upstream de prueba y envía Origin equivalente al de Swagger. Comprueba registro,
login, preservación de Host y Authorization y rechazo de un origen ajeno. Como
control negativo, al reemplazar PreserveHostHeader solo mediante propiedades de
test, la nueva prueba falla exactamente por 403 en vez de 200; al restablecer la
configuración pasa. No se modifican archivos para ejecutar ese control.

Tras esta corrección se repitieron las suites afectadas: **usuarios 64 y Gateway
16, sin fallos, errores ni omisiones (80 pruebas)**. La tabla anterior corresponde
a la primera ejecución de los cuatro módulos (332); catálogo y pedidos no se
modificaron ni se repitieron en esta ronda de CORS.

También se comprobó con los servicios reales H2 y el Origin exacto del navegador:
registro 201, login 200, perfil con JWT 200, usuarios con CLIENTE 403, perfil sin
JWT/inválido 401, boletas con JWT 200, productos públicos 200, origen ajeno 403 y
preflight de React 200. No se imprimieron ni guardaron los tokens. Solo se reinició
el Gateway de prueba en el mismo puerto, conservando los datos H2 de los servicios.
Los procesos de esta verificación manual quedan activos hasta el aviso del usuario.

Pendiente de despliegue: si EC2 termina HTTPS en otro proxy, verificar esquema y
Host externos con ese proxy; esta regresión comprobó HTTP local por el Gateway.


## Corrección de referencias de esquemas de error

En el JSON servido se encontraron 46 referencias sin destino en usuarios,
31 en pedidos y 20 en stock interno. Catálogo público conservaba ErrorRespuesta
porque sus anotaciones de controladores ya lo referenciaban durante el escaneo.
El esquema se declaraba en el bean inicial OpenAPI, pero Springdoc 3.2.0 ejecuta
`removeBrokenReferenceDefinitions` antes de los `OpenApiCustomizer`: eliminaba el
esquema aún no utilizado y los customizers añadían posteriormente referencias a
un componente ausente. El orden se contrastó con el código de Springdoc instalado.

La corrección registra el esquema en cada customizer público y en el del grupo
interno, en la misma fase que incorpora las respuestas. Usa
`ModelConverters.read(ErrorRespuesta.class)` con el DTO real de cada servicio;
no mantiene una copia manual de sus campos ni deshabilita la limpieza de Springdoc.
No cambia lógica de negocio, seguridad, CORS, rutas o cuerpos HTTP.

Se añadieron pruebas recursivas de todos los `$ref` de los tres documentos y del
grupo interno. Cada referencia local debe resolver mediante JSON Pointer y el
esquema de error debe contener estado entero y mensaje string. La validación se
hace sobre el JSON generado real, no sobre el bean Java de configuración.

Última ejecución completa y empaquetado: **usuarios 65, catálogo 113, pedidos 144,
Gateway 16: 338 pruebas aprobadas**, sin fallos, errores ni omisiones.

Los servicios H2 se reiniciaron en sus mismos puertos para cargar la configuración;
Gateway siguió activo y mantuvo PreserveHostHeader. Las cuentas H2 de prueba deben
registrarse nuevamente. No se usó MySQL ni Docker. Inspección HTTP posterior al
reinicio:

| Documento | Referencias examinadas | Referencias sin destino |
|---|---:|---:|
| Usuarios por Gateway | 62 | 0 |
| Catálogo por Gateway | 67 | 0 |
| Pedidos por Gateway | 42 | 0 |
| Stock interno, directo a catálogo | 29 | 0 |

En los cuatro documentos existe `components.schemas.ErrorRespuesta` con las
propiedades reales `estado` y `mensaje`. Registro con cuerpo vacío y Origin del
Gateway devuelve 400 JSON de validación, conservando la corrección de CORS.
Para verificación visual, recargar Swagger sin caché, elegir cada especificación y
expandir una respuesta 400/401/403 junto al modelo ErrorRespuesta. El grupo interno
se consulta por la UI local de catálogo, no por Gateway. Los procesos permanecen
activos hasta que el usuario indique finalizar; no se hizo commit ni push.


## Revisión final previa a publicación

- `git fetch origin` completado; HEAD y origin/main coinciden en `e7c0639`.
  No hay cambios nuevos de main ni conflictos de integración en esta revisión.
- Rama exclusiva `feature/swagger-rutas-versionadas`, todavía sin publicar.
  Entrega de 23 archivos: 17 modificados y 6 nuevos; ninguno eliminado. No hay
  staging, commit, push, merge ni rebase de esta tarjeta.
- No hay dependencias directas duplicadas en los cuatro POM ni imports sin uso
  detectados en los Java de la entrega. Springdoc mantiene versión 3.2.0.
- Los cuerpos de los cuatro controladores modificados son idénticos a main al
  excluir imports y anotaciones OpenAPI. Servicios, repositorios, entidades y
  filtros JWT no cambian. Las reglas de roles se conservan.
- No cambian Docker, Compose, credenciales o puertos. Las listas CORS se conservan;
  el ajuste de proxy es PreserveHostHeader. Los permisos nuevos son solo de lectura
  de documentación. La especificación interna no se enruta por Gateway.
- Revisión HTTP sobre los procesos activos: UI central, swagger-config y las tres
  especificaciones responden 200; las tres documentaciones usan server `/` y rutas
  `/api/v1/`. Todas sus referencias locales resuelven. Las rutas internas y el
  documento de stock interno responden 404 desde Gateway.
- Se confirmaron los informes de la última ejecución completa: 65 + 113 + 144 + 16
  = 338 pruebas aprobadas, sin fallos, errores u omisiones. No se repitieron suites
  en esta revisión porque no hubo cambios de código después de esa ejecución.
- `git diff --check` y comprobación de archivos nuevos aprobados. Sin tokens,
  credenciales reales, logs, binarios, marcadores de conflicto o temporales en Git.
- El usuario confirmó visualmente en Brave el registro 201, login 200, perfil
  propio 200, restricción CLIENTE 403 y desaparición del error ErrorRespuesta en
  Usuarios. Se mantiene la evidencia automatizada de los otros documentos.

El usuario confirmó que el checklist real de **13 requisitos de Trello** fue
proporcionado mediante capturas y autorizó publicar los 23 archivos revisados.
No se agregan requisitos de aceptación. Confirmó también en Brave registro 201,
login 200, Authorize con JWT, perfil propio 200, restricción CLIENTE 403 y desaparición
del error ErrorRespuesta. La entrega está autorizada para commit y push a esta rama;
crear un PR y hacer merge son acciones separadas que no se ejecutan automáticamente.

Riesgos de despliegue pendientes: verificar esquema/Host con el proxy TLS de EC2
(si existe), rutas del origen público y comportamiento visual final en ese entorno.
PreserveHostHeader resuelve el escenario HTTP comprobado; no acredita por sí solo
la terminación HTTPS de un proxy externo. Las futuras modificaciones de permisos
o contratos deben mantener alineadas las descripciones OpenAPI y sus pruebas.
Los procesos locales H2 permanecen activos hasta el aviso expreso del usuario.
