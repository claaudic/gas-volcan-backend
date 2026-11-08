# Gas El Volcán · Backend

Backend de la tienda en línea **Gas El Volcán** (DSY1104 Desarrollo FullStack II, Evaluación Parcial 3, Grupo 7).

**Integrantes:** Claudia Cardoza y Nicolás Morales.

El frontend (React + Vite) está en otro repositorio y se conecta a este backend mediante una API REST.

## Arquitectura

Tres microservicios de negocio y un API Gateway. Cada microservicio tiene **su propia base de datos** y no lee las tablas de otro.

| Aplicación | Puerto | Responsabilidad | Base de datos |
|---|---:|---|---|
| gateway | 8080 | Entrada única del frontend: reparte las rutas y maneja CORS | — |
| ms-usuarios | 8081 | Usuarios, login, JWT y roles | `volcan_usuarios` |
| ms-catalogo | 8082 | Categorías, productos y stock | `volcan_catalogo` |
| ms-pedidos | 8083 | Boletas, detalle, estados y repartidor | `volcan_pedidos` |

## Estructura

```
gas-volcan-backend/
├── pom.xml                 → agrupa los microservicios (para abrirlos juntos en IntelliJ)
├── docker-compose.yml      → MySQL 8.4 en Docker
├── mysql-init/
│   └── 01-bases.sql        → crea las 3 bases: volcan_usuarios, volcan_catalogo y volcan_pedidos
├── gateway/                → API Gateway · puerto 8080
├── ms-usuarios/            → usuarios, login y JWT · puerto 8081
├── ms-catalogo/            → categorías y productos · puerto 8082
└── ms-pedidos/             → boletas y su detalle · puerto 8083
```

## Requisitos

- Docker Desktop (abierto antes de ejecutar los comandos)
- JDK 21

## Base de datos (MySQL en Docker)

Desde la carpeta `gas-volcan-backend`:

```bash
docker compose up -d
```

Esto descarga MySQL 8.4 (solo la primera vez), crea el contenedor `volcan-mysql` en el puerto `3306` y crea las bases `volcan_usuarios`, `volcan_catalogo` y `volcan_pedidos`.

Comprobar que está funcionando:

```bash
docker ps
docker exec -it volcan-mysql mysql -uvolcan -pvolcan123 -e "SHOW DATABASES;"
```

> `mysql-init/01-bases.sql` solo se ejecuta la **primera vez** que se crea el contenedor. Si ya lo tenías creado de antes y te falta alguna base, ejecútalo a mano (no borra datos):
>
> ```bash
> docker exec -i volcan-mysql mysql -uroot -proot123 < mysql-init/01-bases.sql
> ```

Apagar (los datos se mantienen en el volumen `volcan-datos`):

```bash
docker compose stop
```

> Las contraseñas de `docker-compose.yml` son solo para desarrollo local.

## Gateway (puerto 8080)

React llama **solo** al Gateway, y él reparte cada ruta a su microservicio (la ruta se reenvía igual):

| Ruta en el 8080 | Va a |
|---|---|
| `/api/v1/auth/**`, `/api/v1/usuarios/**` | ms-usuarios · 8081 |
| `/api/v1/productos/**`, `/api/v1/categorias/**` | ms-catalogo · 8082 |
| `/api/v1/boletas/**` | ms-pedidos · 8083 |

Desde la carpeta `gateway` (con los microservicios encendidos):

```bash
./mvnw spring-boot:run
```

- El Gateway **solo enruta**: el token `Authorization: Bearer` llega intacto y cada microservicio revisa la identidad y los permisos (401 / 403).
- **CORS** se maneja aquí, para `http://localhost:5173` (variable `CORS_ORIGENES`). Si un microservicio también manda su cabecera CORS, el Gateway deja una sola.
- Las direcciones de los microservicios se cambian con `USUARIOS_URL`, `CATALOGO_URL` y `PEDIDOS_URL` (por ejemplo, al desplegar).

## ms-usuarios (puerto 8081)

Con MySQL encendido, desde la carpeta `ms-usuarios`:

```bash
cp .env.example .env     # solo la primera vez; luego escribir la clave real en .env
./mvnw spring-boot:run
```

La contraseña de la base no está escrita en el código: se lee de `MYSQL_PASSWORD`, en el archivo `ms-usuarios/.env` (que no se sube a GitHub) o en una variable de entorno. Para otro MySQL (por ejemplo, Laragon) también se pueden cambiar `USUARIOS_DB_URL` y `USUARIOS_DB_USERNAME`.

Rutas principales:

| Método | Ruta | Qué hace | Quién puede |
|---|---|---|---|
| POST | `/api/v1/auth/login` | Inicia sesión y entrega el token JWT | Todos |
| POST | `/api/v1/auth/renovar` | Entrega un token nuevo (encabezado `Authorization: Bearer <token>`) | Todos (con token vigente) |
| POST | `/api/v1/auth/registro` | Registro público (siempre rol CLIENTE) | Todos |
| GET · PUT | `/api/v1/usuarios/yo` | Ver y editar mi perfil (nombre, apellidos, teléfono, comuna, dirección) | Cualquiera con sesión |
| DELETE | `/api/v1/usuarios/yo` | Eliminar mi cuenta | CLIENTE |
| GET | `/api/v1/usuarios/repartidores` | Repartidores activos | ADMINISTRADOR, DESPACHADORA |
| GET · POST | `/api/v1/usuarios` | Listar y crear usuarios | ADMINISTRADOR |
| GET · PUT · DELETE | `/api/v1/usuarios/{id}` | Buscar, actualizar y eliminar un usuario | ADMINISTRADOR |

Las rutas protegidas piden el encabezado `Authorization: Bearer <token>`:

- Sin token, o con un token falso o vencido → **401**
- Con un rol que no corresponde → **403**
- Como en la EP2, el administrador no puede eliminar, desactivar ni cambiar el rol o el correo de su propia cuenta → **400**

Los errores siempre responden `{ "estado": ..., "mensaje": "..." }`.

Cuentas de demostración (se cargan solas la primera vez):

| Rol | Correo | Contraseña |
|---|---|---|
| ADMINISTRADOR | admin@gaselvolcan.cl | Admin1234 |
| DESPACHADORA | despachadora@gaselvolcan.cl | Desp123 |
| REPARTIDOR | repartidor@gaselvolcan.cl | Repart123 |
| REPARTIDOR | repartidor2@gaselvolcan.cl | Repart123 |
| CLIENTE | camila@gmail.com | Clien1234 |

### Clave del JWT

Los tokens se firman con `JWT_SECRET` (al menos 32 caracteres, la misma en los tres microservicios). No está escrita en el código: va en `ms-usuarios/.env` (ver `.env.example`) o como variable de entorno. Si falta, ms-usuarios no arranca y lo avisa. Para generar una:

```bash
openssl rand -base64 48
```

### CORS

La API acepta llamadas desde el frontend en `http://localhost:5173` (Vite). Para permitir otras páginas, usar la variable de entorno `CORS_ORIGENES`, separadas por coma:

```bash
export CORS_ORIGENES="http://localhost:5173,http://localhost:4173"
```
