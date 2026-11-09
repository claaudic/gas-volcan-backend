# JWT interno y recuperación tras reinicio real

Prueba manual automatizada de tarjeta 09, independiente de Compose y de las bases
del proyecto. Requiere Java 21, Python 3, Docker y la imagen `mysql:8.4` disponible.
Desde la raíz:

```sh
bash ms-pedidos/mvnw -f pom.xml -pl ms-catalogo,ms-pedidos package
python3 ms-pedidos/validation/restart_mysql.py --output /tmp/gas-volcan-reinicio-evidencia-nueva
```

El directorio de salida debe ser nuevo y estar fuera del repositorio. Guarda logs
de las cuatro JVM (catálogo/pedidos antes y después del reinicio) y `evidence.json`.
Un resultado correcto requiere código de salida 0, `result: PASS` y
`cleanup_completed: true`; un archivo parcial no significa que pasó.

La prueba crea un contenedor `gas-volcan-restart-test-<aleatorio>` sin volúmenes,
con puerto aleatorio publicado solo en 127.0.0.1, y dos bases independientes.
Aplica los scripts SQL exclusivamente a esas bases nuevas. Inicia los JAR locales
con `ddl-auto=validate`, carga inicial deshabilitada y credenciales sintéticas
aleatorias. No lee `.env` ni hereda las variables de conexión del entorno.
Los secretos y tokens no se escriben en la evidencia. No inicia ms-usuarios:
los JWT de usuario son sintéticos, con el contrato que ya consume el filtro.
La regresión de generación/verificación de usuarios se ejecuta aparte con sus
pruebas existentes, sin modificar ese microservicio.

Un proxy HTTP local reenvía las llamadas a catálogo real. Tras el primer descuento
confirmado, reemplaza su respuesta por 503 y bloquea las siguientes consultas de
stock: la compra debe quedar `COTIZADA`, sin boleta, pero con stock ya descontado.
El recuperador está deshabilitado en la primera JVM para fijar ese punto de fallo.
Se termina pedidos con `kill` de proceso y se reinicia catálogo. MySQL sigue vivo.
Se arranca otra JVM de pedidos con `PEDIDOS_RECUPERACION_ENABLED=true` e intervalo
de 1 segundo, conservando exactamente la base de prueba. Al restablecer el transporte,
el recuperador debe confirmar sin un segundo POST de descuento. El intervalo es
solo para esta prueba; el repositorio conserva su valor de 30 segundos.

Después se reenvía la compra con la misma clave, se cancela repetidamente y se
comprueban por SQL stock, número de boletas, estados, líneas e historial durable.
También se cuenta el número de POST/PUT internos reenviados para acreditar un único
descuento y una única compensación. Los datos de la solicitud, cotización y fecha
de creación se comparan antes y después del reinicio y de la cancelación.

La matriz JWT comprueba firma válida, ausencia, firma incorrecta, vencimiento,
JWT CLIENTE y scope insuficiente. El token válido consulta una operación inexistente
(404 de negocio, no 401) y el flujo real prueba descuento y compensación autorizados.
Los intentos no autorizados de POST deben dejar stock e historial intactos.
El JWT CLIENTE permite consultar boletas y se usa en la compra; el JWT ADMINISTRADOR
permite cancelar, preservando los permisos existentes.

En `finally` se detienen solo las JVM creadas y se elimina exclusivamente el ID del
contenedor creado por esta ejecución. No se usan ni eliminan volúmenes existentes.
Si el proceso Python se interrumpe abruptamente, inspeccionar sus PID/nombre de
contenedor antes de limpiar; nunca usar comandos globales de limpieza de Docker.

Límites: se prueba reinicio de procesos y respuesta perdida después de commit real,
no caída del host MySQL ni interrupción de energía durante un commit. No acredita
por sí sola HTTPS, rotación de claves ni configuración de secretos en despliegue.

## Resultado ejecutado sobre 288f7a0

Ejecución real completada con salida 0 y `result: PASS`, usando los JAR recién
construidos y las migraciones actuales en MySQL 8.4 aislado. Evidencia local:
`/tmp/gas-volcan-reinicio-evidencia-09/evidence.json` y logs en el mismo directorio.
No se incluyen esos logs ni tokens en Git.

| Punto comprobado por SQL | Compra | Stock | Boletas | Operaciones compra / stock |
|---|---|---:|---:|---:|
| Respuesta perdida después del descuento | COTIZADA | 7 | 0 | 1 / 1 |
| Tras reiniciar ambas JVM, transporte aún bloqueado | COTIZADA | 7 | 0 | 1 / 1 |
| Recuperación automática | CONFIRMADA | 7 | 1 | 1 / 1 |
| Reintento con misma clave | CONFIRMADA | 7 | 1 | 1 / 1 |
| Cancelación repetida | COMPENSADA | 10 | 1 | 1 / 1 |

Stock inicial: 10; cantidad comprada: 3. Se observó exactamente un POST de descuento
y un PUT de compensación. La operación de catálogo pasó de APLICADO a COMPENSADO;
la línea durable conservó producto 1, cantidad 3. La solicitud, cotización y fecha
de creación se conservaron. Reintentar comprar después de cancelar devolvió 409.
Las cuatro JVM terminaron y el contenedor exclusivo de prueba fue eliminado.

JWT: token interno válido llegó al negocio (404 para UUID inexistente); ausencia,
firma incorrecta, vencimiento y JWT CLIENTE recibieron 401. Los POST sin token,
con firma incorrecta o con JWT CLIENTE recibieron 401 sin cambiar stock ni crear
operaciones. Scope solo de consulta recibió 403 al intentar descontar.
JWT CLIENTE válido consultó boletas (200), sin sesión recibió 401; la compra tomó
cliente 10 del token pese al cliente 999 enviado en el JSON. La cancelación fue
realizada con el rol ADMINISTRADOR.

Regresión ejecutada: 108 pruebas de catálogo, 124 de pedidos y 22 de usuarios
(`JwtServiceTest` y `SeguridadTest`), todas aprobadas, sin errores ni omisiones.
Los dos módulos de negocio también se empaquetaron correctamente.
No se ejecutó la suite completa de usuarios en esta validación.

Pendientes de despliegue: revisión y autorización de migraciones sobre copias del
esquema real; aprovisionamiento de secretos internos iguales entre servicios y
distintos de JWT_SECRET; transporte HTTPS y revisión final de contratos por el
equipo. Esta prueba acredita recuperación de procesos con MySQL vivo y durable.
