package cl.gaselvolcan.ms_catalogo.config;

import org.springframework.context.annotation.Configuration;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

@Configuration
@OpenAPIDefinition(info = @Info(title = "Gas El Volcan - Catalogo", version = "v1",
        description = "Categorias, productos y consulta informativa de disponibilidad para ms-pedidos. Precios en CLP. PUT reemplaza los datos del DTO."))
public class OpenApiConfig {
}
