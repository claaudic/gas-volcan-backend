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

| Método | Ruta | Qué hace |
|---|---|---|
| POST | `/api/v1/auth/login` | Inicia sesión y entrega el token JWT |
| POST | `/api/v1/auth/renovar` | Entrega un token nuevo (encabezado `Authorization: Bearer <token>`) |
| POST | `/api/v1/auth/registro` | Registro público (siempre rol CLIENTE) |
| GET · POST | `/api/v1/usuarios` | Listar y crear usuarios |
| GET · PUT · DELETE | `/api/v1/usuarios/{id}` | Buscar, actualizar y eliminar un usuario |

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

