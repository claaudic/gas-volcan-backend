package cl.gaselvolcan.ms_pedidos.config;

import java.net.URI;
import java.util.Objects;
import java.time.Duration;
import cl.gaselvolcan.ms_pedidos.client.TokenServicioCatalogo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class CatalogoInternoConfig {
    @Bean
    public RestClient catalogoInternoRestClient(TokenServicioCatalogo tokens,
            @Value("${pedidos.catalogo.url}") URI url,
            @Value("${pedidos.catalogo.connect-timeout}") Duration conexion,
            @Value("${pedidos.catalogo.read-timeout}") Duration lectura) {
        return new CatalogoConfig().catalogoRestClient(url, conexion, lectura).mutate()
                .requestInterceptor((solicitud, cuerpo, ejecucion) -> {
                    URI destino = solicitud.getURI();
                    if (!Objects.equals(url.getScheme(), destino.getScheme())
                            || !Objects.equals(url.getRawAuthority(), destino.getRawAuthority())
                            || !destino.normalize().getPath().startsWith("/internal/v1/stock/")) {
                        throw new IllegalArgumentException("El token interno solo se envia a rutas de stock del catalogo configurado.");
                    }
                    solicitud.getHeaders().setBearerAuth(tokens.generar());
                    return ejecucion.execute(solicitud, cuerpo);
                }).build();
    }
}
