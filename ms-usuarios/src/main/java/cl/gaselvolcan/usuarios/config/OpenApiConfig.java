package cl.gaselvolcan.usuarios.config;

import cl.gaselvolcan.usuarios.exception.ErrorRespuesta;
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
        return new OpenAPI().info(new Info().title("Gas El Volcan - Usuarios").version("v1"))
                // Relativo al origen: Try it out funciona tanto directo como por Gateway.
                .servers(List.of(new Server().url("/"))).components(componentes());
    }

    @Bean
    public OpenApiCustomizer contratosPublicos() {
        return api -> {
            registrarEsquemasError(api);
            api.getPaths().entrySet().removeIf(entry -> !entry.getKey().startsWith("/api/v1/"));
            api.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, op) -> {
                boolean auth = path.startsWith("/api/v1/auth/");
                boolean renovar = path.endsWith("/renovar");
                boolean publico = auth && !renovar;
                op.setSecurity(publico ? List.of() : List.of(new SecurityRequirement().addList("bearerJWT")));
                String permiso = publico ? "Publico, sin autenticacion."
                        : renovar ? "Renueva el JWT de usuario enviado en Authorization."
                        : path.endsWith("/yo") ? (method == HttpMethod.DELETE ? "Solo CLIENTE: elimina su propia cuenta." : "Cualquier usuario autenticado: su propio perfil.")
                        : path.endsWith("/repartidores") ? "ADMINISTRADOR o DESPACHADORA."
                        : "Solo ADMINISTRADOR.";
                op.setDescription(permiso);
                error(op, "401", "JWT ausente, invalido o credenciales incorrectas");
                if (!publico && !renovar) error(op, "403", "Rol insuficiente");
                if (method == HttpMethod.POST && !path.endsWith("/login") && !renovar) exito(op, "201");
                if (method == HttpMethod.DELETE) exito(op, "204");
                if (path.contains("{id}") || path.endsWith("/yo")) error(op, "404", "Usuario inexistente");
                if (method == HttpMethod.POST && !renovar || method == HttpMethod.PUT || path.contains("{id}"))
                    error(op, "400", "Datos invalidos o regla de negocio incumplida");
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
