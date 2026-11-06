package cl.gaselvolcan.ms_pedidos.config;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class CatalogoConfig {
    @Bean
    public RestClient catalogoRestClient(
            @Value("${pedidos.catalogo.url}") URI url,
            @Value("${pedidos.catalogo.connect-timeout}") Duration conexion,
            @Value("${pedidos.catalogo.read-timeout}") Duration lectura) {
        if (!("http".equals(url.getScheme()) || "https".equals(url.getScheme()))
                || url.getHost() == null || url.getUserInfo() != null
                || url.getQuery() != null || url.getFragment() != null) {
            throw new IllegalArgumentException("La URL del catalogo debe ser HTTP(S) sin credenciales, consulta ni fragmento.");
        }
        if (conexion.isZero() || conexion.isNegative() || lectura.isZero() || lectura.isNegative()) {
            throw new IllegalArgumentException("Los tiempos de espera del catalogo deben ser positivos.");
        }
        var http = HttpClient.newBuilder().connectTimeout(conexion).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(lectura);
        return RestClient.builder().baseUrl(url.toString()).requestFactory(factory).build();
    }
}
