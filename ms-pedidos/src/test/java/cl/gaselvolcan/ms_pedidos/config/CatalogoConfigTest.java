package cl.gaselvolcan.ms_pedidos.config;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class CatalogoConfigTest {
    private final CatalogoConfig config = new CatalogoConfig();
    @Test void permiteHostConfigurableSinConexionAlCrearCliente() {
        assertNotNull(config.catalogoRestClient(URI.create("http://catalogo:8082"), Duration.ofSeconds(2), Duration.ofSeconds(5)));
    }
    @Test void rechazaTiemposDeEsperaNoPositivos() {
        assertThrows(IllegalArgumentException.class, () -> config.catalogoRestClient(
                URI.create("http://localhost:8082"), Duration.ZERO, Duration.ofSeconds(5)));
        assertThrows(IllegalArgumentException.class, () -> config.catalogoRestClient(
                URI.create("http://localhost:8082"), Duration.ofSeconds(2), Duration.ofSeconds(-1)));
    }
    @Test void rechazaUrlsInvalidasOConCredenciales() {
        for (String url : new String[] {"ftp://catalogo", "catalogo", "http://usuario:clave@catalogo", "http://catalogo?x=1", "http://catalogo#x"}) {
            assertThrows(IllegalArgumentException.class, () -> config.catalogoRestClient(
                    URI.create(url), Duration.ofSeconds(2), Duration.ofSeconds(5)));
        }
    }
}
