package cl.gaselvolcan.ms_catalogo.config;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
    private final String[] origenes;

    public CorsConfig(@Value("${catalogo.cors.allowed-origins}") String origenes) {
        this.origenes = Arrays.stream(origenes.split(","))
                .map(String::trim).filter(origen -> !origen.isEmpty())
                .distinct().toArray(String[]::new);
        if (this.origenes.length == 0 || Arrays.stream(this.origenes).anyMatch(origen -> origen.contains("*"))) {
            throw new IllegalArgumentException("Configura origenes CORS explicitos, sin comodines.");
        }
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/v1/**")
                .allowedOrigins(origenes)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "Accept", "Authorization")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
