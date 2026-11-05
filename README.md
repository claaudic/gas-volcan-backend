# Gas El Volcán · Backend

Backend de la tienda en línea **Gas El Volcán** (DSY1104 Desarrollo FullStack II, Evaluación Parcial 3, Grupo 7).

**Integrantes:** Claudia Cardoza y Nicolás Morales.

El frontend (React + Vite) está en otro repositorio y se conecta a este backend mediante una API REST.

## Estructura

```
gas-volcan-backend/
├── docker-compose.yml      → MySQL 8.4 en Docker
├── mysql-init/
│   └── 01-bases.sql        → crea las bases volcan_usuarios y volcan_tienda
├── ms-usuarios/            → usuarios, login y JWT · puerto 8081
└── ms-tienda/              → (en construcción) categorías, productos y boletas · puerto 8082
```

## Requisitos

- Docker Desktop (abierto antes de ejecutar los comandos)
- JDK 21

## Base de datos (MySQL en Docker)

Desde la carpeta `gas-volcan-backend`:

```bash
docker compose up -d
```

Esto descarga MySQL 8.4 (solo la primera vez), crea el contenedor `volcan-mysql` en el puerto `3306` y crea las bases `volcan_usuarios` y `volcan_tienda`.

Comprobar que está funcionando:

```bash
docker ps
docker exec -it volcan-mysql mysql -uvolcan -pvolcan123 -e "SHOW DATABASES;"
```

Apagar (los datos se mantienen en el volumen `volcan-datos`):

```bash
docker compose stop
```

> Las contraseñas de `docker-compose.yml` son solo para desarrollo local.

## ms-usuarios (puerto 8081)

Con MySQL encendido, desde la carpeta `ms-usuarios`:

```bash
./mvnw spring-boot:run
```

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

Los tokens se firman con la variable de entorno `JWT_SECRET` (al menos 32 caracteres, la misma en los dos microservicios). Si no está definida, se usa una clave solo para desarrollo local.

```bash
export JWT_SECRET="una-clave-larga-de-al-menos-32-caracteres"
```

### CORS

La API acepta llamadas desde el frontend en `http://localhost:5173` (Vite). Para permitir otras páginas, usar la variable de entorno `CORS_ORIGENES`, separadas por coma:

```bash
export CORS_ORIGENES="http://localhost:5173,http://localhost:4173"
```
