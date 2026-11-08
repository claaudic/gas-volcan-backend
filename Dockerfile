# Una sola "receta" para los 4 servicios (como el ejemplo del profesor).
# Se elige cuál armar con SERVICIO, por ejemplo:
#   docker build --build-arg SERVICIO=ms-usuarios -t volcan/ms-usuarios .
# docker-compose.yml ya lo hace para los cuatro.

# --- Etapa 1: compilar con Maven y Java 21 ---
FROM maven:3.9-eclipse-temurin-21 AS compilar
ARG SERVICIO
WORKDIR /app

# Primero solo el pom.xml: si no cambian las dependencias, Docker reutiliza
# esta capa y no las vuelve a descargar en cada construcción
COPY ${SERVICIO}/pom.xml .
RUN mvn -q -B dependency:go-offline || true

COPY ${SERVICIO}/src ./src
RUN mvn -q -B -DskipTests package && cp target/*.jar app.jar

# --- Etapa 2: solo lo necesario para ejecutar (sin Maven ni el código fuente) ---
FROM eclipse-temurin:21-jre
WORKDIR /app

# No se ejecuta como administrador (root) del contenedor
RUN useradd --system --no-create-home volcan
COPY --from=compilar /app/app.jar app.jar
USER volcan

ENTRYPOINT ["java", "-jar", "app.jar"]
