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

## Encender todo con Docker (un solo comando)

Además de usar IntelliJ, todo el sistema (MySQL + 3 microservicios + Gateway + la tienda React) se puede encender con Docker. Así se despliega en la EC2 (ver [Despliegue en AWS EC2](#despliegue-en-aws-ec2)).

```bash
cp .env.example .env          # solo la primera vez; escribir las claves reales
docker compose up -d --build  # construye y enciende los 6 servicios
docker compose ps             # 5 "healthy" y volcan-frontend "Up"
```

- `Dockerfile` (uno solo, en la raíz) sirve para los 4 servicios: compila con Maven y ejecuta con Java 21, sin ser root.
- Orden de arranque: MySQL → ms-usuarios y ms-catalogo → ms-pedidos → Gateway → tienda. Cada uno espera a que el anterior responda en `/actuator/health`.
- Solo quedan abiertos la **tienda (80)**, el **Gateway (8080)** y MySQL (3306, para desarrollo). Los microservicios se hablan por dentro de Docker.
- La tienda es el frontend React servido con nginx; las llamadas a `/api` las pasa al Gateway. El compose la busca en `../tienda-react`; como en nuestro caso está dentro de `Proyecto_Volcan`, en el `.env` va `FRONTEND_CARPETA=../Proyecto_Volcan/tienda-react`.
- Si IntelliJ ya está usando el 8080, cambiar el puerto con `GATEWAY_PUERTO=18080` en el `.env`.
- Apagar: `docker compose down` (los datos de MySQL se mantienen en el volumen).

## Despliegue en AWS EC2

El sistema completo (6 contenedores) corre en una sola instancia EC2 con Docker Compose.

### 1. Crear la instancia

| Opción | Valor |
|---|---|
| Cuenta | AWS Academy Learner Lab, región `us-east-1` |
| Sistema | Ubuntu Server 24.04 |
| Tipo | `t3.medium` (2 vCPU, 4 GB: Maven y 4 servicios Java necesitan memoria) |
| Disco | 20 GiB |
| Par de claves | `vockey` |

Puertos del grupo de seguridad:

| Puerto | Para qué | Origen |
|---|---|---|
| 22 | SSH (conectarse a la instancia) | Mi IP |
| 80 | Tienda | 0.0.0.0/0 |
| 8080 | Gateway y Swagger (opcional) | 0.0.0.0/0 |
| 3306 | MySQL | **No se abre**: solo se usa dentro de Docker |

### 2. Instalar Docker y Git

```bash
sudo apt-get update && sudo apt-get install -y docker.io docker-compose-v2 git
sudo usermod -aG docker ubuntu   # después salir (exit) y volver a conectarse
```

### 3. Clonar los dos repositorios, uno al lado del otro

```bash
cd ~
git clone https://github.com/claaudic/gas-volcan-backend.git
git clone https://github.com/claaudic/Proyecto_Volcan.git
```

### 4. Crear el `.env`

El `.env` guarda las claves y **no se sube a GitHub** (está en `.gitignore`). Se crea en la instancia a partir del ejemplo:

```bash
cd ~/gas-volcan-backend
cp .env.example .env
nano .env   # claves nuevas, distintas a las de desarrollo, y FRONTEND_CARPETA=../Proyecto_Volcan/tienda-react
```

### 5. Encender y comprobar

```bash
docker compose up -d --build   # la primera vez tarda varios minutos
docker compose ps              # 5 en (healthy) y volcan-frontend en Up
curl http://localhost:8080/actuator/health   # {"status":"UP"}
```

### 6. Abrir

- Tienda: `http://<IP-PÚBLICA>`
- Swagger: `http://<IP-PÚBLICA>:8080/swagger-ui.html`

La IP pública aparece en el detalle de la instancia en la consola de EC2.

### 7. Advertencias

- **La IP pública cambia** cada vez que se inicia el lab. Por eso la tienda llama a `/api` (misma dirección que la página) y no a una IP fija.
- La sesión del Learner Lab dura **4 horas** y el crédito es limitado: al terminar, detener la instancia (**Stop**) o usar **End Lab**. Nunca **Terminate**, porque borra la instancia.
- Al volver a iniciar el lab, los 6 contenedores se encienden solos (`restart: unless-stopped`): solo hay que buscar la nueva IP. Para revisarlos: `docker compose ps` en `~/gas-volcan-backend`.

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

## Seguridad en ms-catalogo (8082) y ms-pedidos (8083)

Cada microservicio revisa el token por su cuenta (`config/FiltroJwt` y `config/SeguridadConfig`), con la **misma** `JWT_SECRET` de ms-usuarios. Antes de arrancarlos: `cp .env.example .env` en cada carpeta y escribir `MYSQL_PASSWORD` y `JWT_SECRET`.

| Servicio | Ruta | Quién puede |
|---|---|---|
| ms-catalogo | `GET /api/v1/productos/**`, `GET /api/v1/categorias/**` | Todos (la tienda es pública) |
| ms-catalogo | `POST · PUT · DELETE` productos y categorías | ADMINISTRADOR |
| ms-pedidos | `POST /api/v1/boletas` (comprar) | Todos, también el visitante sin cuenta (ERS, RF-08) |
| ms-pedidos | `GET /api/v1/boletas/**` | Cualquiera con sesión |
| ms-pedidos | `PUT /api/v1/boletas/{id}` (estado, repartidor) | ADMINISTRADOR, DESPACHADORA, REPARTIDOR |
| ms-pedidos | `DELETE /api/v1/boletas/{id}` | ADMINISTRADOR |

- Sin token en una ruta protegida → **401**; con un rol que no corresponde → **403**; un token falso o vencido → **401** (incluso al comprar, para no comprar "como invitado" sin saberlo).
- El token trae `sub` (correo), `id` y `rol`. En un controlador se obtiene con `@AuthenticationPrincipal UsuarioToken usuario`.

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
