package cl.gaselvolcan.ms_catalogo.config;

import cl.gaselvolcan.ms_catalogo.exception.ErrorRespuesta;
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
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI documentacion() {
        return new OpenAPI().info(new Info().title("Gas El Volcan - Catalogo").version("v1")
                        .description("Categorias, productos y consulta informativa de disponibilidad para ms-pedidos. Precios en CLP. PUT reemplaza los datos del DTO."))
                // Relativo al origen: Try it out funciona tanto directo como por Gateway.
                .servers(List.of(new Server().url("/"))).components(componentes());
    }

    @Bean
    public OpenApiCustomizer contratosPublicos() {
        return api -> {
            registrarEsquemasError(api);
            api.getPaths().entrySet().removeIf(entry -> !entry.getKey().startsWith("/api/v1/"));
            api.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, op) -> {
                boolean publico = method == HttpMethod.GET;
                op.setSecurity(publico ? List.of() : List.of(new SecurityRequirement().addList("bearerJWT")));
                op.setDescription((op.getDescription() == null ? "" : op.getDescription() + " ")
                        + (publico ? "Consulta publica. Un JWT enviado debe ser valido." : "Solo ADMINISTRADOR con JWT de usuario."));
                error(op, "401", "JWT invalido, vencido o ausente en escritura");
                if (!publico) {
                    error(op, "403", "Solo ADMINISTRADOR");
                    error(op, "409", "El producto cambio concurrentemente");
                }
                if (method == HttpMethod.POST) exito(op, "201");
                if (method == HttpMethod.DELETE) exito(op, "204");
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

    @Bean
    public GroupedOpenApi stockInterno() {
        return GroupedOpenApi.builder().group("stock-interno").pathsToMatch("/internal/v1/stock/**")
                .addOpenApiCustomizer(api -> {
                    registrarEsquemasError(api);
                    api.setInfo(new Info().title("Gas El Volcan - Stock interno").version("v1")
                            .description("Solo comunicacion ms-pedidos a ms-catalogo. No se enruta por Gateway."));
                    api.setComponents(api.getComponents().addSecuritySchemes("stockServicioJWT", new SecurityScheme()
                            .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                            .description("JWT de servicio firmado con INTERNAL_STOCK_JWT_SECRET, distinta de JWT_SECRET. issuer/subject ms-pedidos, audience ms-catalogo y scope de stock. No acepta JWT de clientes.")));
                    api.getComponents().getSecuritySchemes().remove("bearerJWT");
                    api.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, op) -> {
                        op.setSecurity(List.of(new SecurityRequirement().addList("stockServicioJWT")));
                        op.setDescription("Solo servicio ms-pedidos. Scope requerido: " + (method == HttpMethod.POST
                                ? "stock:descontar. Idempotency-Key obligatorio; una misma clave no descuenta dos veces."
                                : method == HttpMethod.PUT ? "stock:compensar. Restitucion idempotente." : "stock:consultar."));
                        error(op, "401", "Token de servicio ausente, invalido o JWT de usuario");
                        error(op, "403", "Scope de servicio insuficiente");
                        error(op, "400", "UUID, solicitud o cantidades invalidas");
                        error(op, "404", "Operacion o producto inexistente");
                        if (method != HttpMethod.GET) error(op, "409", "Conflicto de clave, stock insuficiente o compensacion no permitida");
                        // Un POST puede devolver el resultado durable del rechazo, o ErrorRespuesta.
                        if (method == HttpMethod.POST) {
                            for (String code : List.of("400", "404", "409")) {
                                op.getResponses().get(code).setContent(new Content().addMediaType("application/json", new MediaType()
                                        .schema(new Schema<>().oneOf(List.of(new Schema<>().$ref("#/components/schemas/DescuentoRespuesta"),
                                                new Schema<>().$ref("#/components/schemas/ErrorRespuesta"))))));
                            }
                        }
                        error(op, "503", "Bloqueo concurrente: reintentar con la misma clave");
                        error(op, "500", "Error interno sin detalles sensibles");
                    }));
                }).build();
    }
}
