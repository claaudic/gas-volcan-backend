package cl.gaselvolcan.ms_pedidos.config;

import cl.gaselvolcan.ms_pedidos.exception.ErrorRespuesta;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI documentacion() {
        return new OpenAPI().info(new Info().title("Gas El Volcan - Pedidos").version("v1"))
                // Relativo al origen: Try it out funciona tanto directo como por Gateway.
                .servers(List.of(new Server().url("/"))).components(componentes());
    }

    @Bean
    public OpenApiCustomizer contratosPublicos() {
        return api -> {
            registrarEsquemasError(api);
            api.getPaths().entrySet().removeIf(entry -> !entry.getKey().startsWith("/api/v1/"));
            api.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, op) -> {
                boolean comprar = method == HttpMethod.POST;
                op.setSecurity(comprar ? List.of(new SecurityRequirement(), new SecurityRequirement().addList("bearerJWT"))
                        : List.of(new SecurityRequirement().addList("bearerJWT")));
                op.setDescription(comprar
                        ? "Compra publica como invitado con nombre, correo y telefono obligatorios, o con JWT CLIENTE cuya identidad prevalece sobre clienteId. Idempotency-Key UUID obligatorio; conservar clave y contenido en reintentos. Total y stock calculados por el servidor."
                        : method == HttpMethod.GET
                        ? "Requiere sesion: CLIENTE solo sus boletas; REPARTIDOR solo las asignadas. ADMINISTRADOR y DESPACHADORA mantienen consulta operativa."
                        : "ADMINISTRADOR o DESPACHADORA; REPARTIDOR solo puede cambiar estado de boletas asignadas. Una boleta con stock acreditado no admite reemplazo general. Cancelar programa restitucion durable de stock.");
                error(op, "401", "JWT ausente en ruta protegida, invalido o vencido");
                error(op, "403", "Permisos insuficientes o boleta ajena");
                if (path.endsWith("/repartidor")) {
                    op.setDescription("ADMINISTRADOR o DESPACHADORA. Pedido PENDIENTE; versionEsperada entera obligatoria, admite 0. repartidorId obligatorio: ID de repartidor activo o null explicito para desasignar. No modifica stock ni detalles.");
                    error(op, "503", "No fue posible validar la cuenta del repartidor");
                }
                if (path.endsWith("/anulacion")) {
                    op.setDescription("Solo ADMINISTRADOR. Anulacion logica de pedido PENDIENTE con versionEsperada. Conserva historial. Repetir sobre CANCELADO no duplica compensacion. HTTP 200 confirma cancelacion comercial, no restitucion de stock: consultar /api/v1/boletas/{id}/stock.");
                }
                if (path.endsWith("/gestion")) {
                    op.setDescription("Contacto y entrega solo de pedidos visibles al usuario. Invitados: contacto persistido en boleta. Autenticados: perfil actual consultado internamente; no es una instantanea historica. Respuesta no almacenable en cache. CLIENTE solo propios; REPARTIDOR solo asignados; ADMINISTRADOR y DESPACHADORA consulta operativa.");
                    error(op, "503", "No fue posible consultar el contacto; no se inventan datos ni se utiliza respaldo local");
                    error(op, "404", "Boleta o cuenta requerida inexistente");
                }
                if (comprar) {
                    exito(op, "201");
                    error(op, "503", "Catalogo no disponible u operacion pendiente: reintentar misma clave y contenido");
                }
                if (path.contains("{id}")) error(op, "404", "Boleta inexistente");
                if (method != HttpMethod.GET || path.contains("{id}")) error(op, "400", "Datos o transicion invalidos");
                if (method != HttpMethod.GET) error(op, "409", "Conflicto de idempotencia o modificacion concurrente");
                error(op, "500", "Error interno sin detalles sensibles");
            }));
        };
    }

    private static Components componentes() {
        return new Components().addSecuritySchemes("bearerJWT", new SecurityScheme()
                .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                .description("JWT de usuario emitido por ms-usuarios. Ingresar solo el token, sin el prefijo Bearer."));
    }

    private static void registrarEsquemasError(OpenAPI api) {
        // Springdoc limpia los componentes no referenciados antes de los customizers.
        // Registrar el DTO real aqui, junto a las respuestas que lo referencian.
        ModelConverters.getInstance().read(ErrorRespuesta.class).forEach(api.getComponents()::addSchemas);
    }

    private static void error(Operation op, String code, String description) {
        op.getResponses().putIfAbsent(code, new ApiResponse().description(description)
                .content(new Content().addMediaType("application/json", new MediaType()
                        .schema(new Schema<>().$ref("#/components/schemas/ErrorRespuesta")))));
    }

    private static void exito(Operation op, String code) {
        if (!op.getResponses().containsKey(code) && op.getResponses().containsKey("200")) {
            op.getResponses().addApiResponse(code, op.getResponses().remove("200"));
        }
        if (code.equals("204")) op.getResponses().get(code).setContent(null);
    }
}
