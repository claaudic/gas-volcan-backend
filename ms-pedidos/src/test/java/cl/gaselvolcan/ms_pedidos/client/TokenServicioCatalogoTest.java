package cl.gaselvolcan.ms_pedidos.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import cl.gaselvolcan.ms_pedidos.config.CatalogoInternoConfig;

class TokenServicioCatalogoTest {
    private static final String INTERNA = "clave-interna-sintetica-exclusiva-de-pruebas-123456";
    private static final String USUARIOS = "clave-usuarios-sintetica-exclusiva-de-pruebas-123456";
    private final TokenServicioCatalogo tokens = new TokenServicioCatalogo(INTERNA, USUARIOS);

    @Test void emiteIdentidadLimitadaYTemporalSinRolNiIdDeUsuario() {
        var jwt = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(INTERNA.getBytes(StandardCharsets.UTF_8)))
                .build().parseSignedClaims(tokens.generar());
        var c = jwt.getPayload();
        assertEquals("HS256", jwt.getHeader().getAlgorithm());
        assertEquals("ms-pedidos", c.getIssuer());
        assertEquals("ms-pedidos", c.getSubject());
        assertEquals(java.util.Set.of("ms-catalogo"), c.getAudience());
        assertEquals(60_000, c.getExpiration().getTime() - c.getIssuedAt().getTime());
        assertEquals("stock:descontar stock:consultar stock:compensar", c.get("scope"));
        assertNull(c.get("rol")); assertNull(c.get("id")); assertNotNull(c.getId());
        assertThrows(io.jsonwebtoken.JwtException.class, () -> Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(USUARIOS.getBytes(StandardCharsets.UTF_8)))
                .build().parseSignedClaims(tokens.generar()));
    }

    @Test void clienteEnviaTokenSoloAlDestinoInternoConfigurado() {
        var cliente = new CatalogoInternoConfig().catalogoInternoRestClient(tokens,
                URI.create("http://catalogo.test:8082"), Duration.ofSeconds(1), Duration.ofSeconds(1));
        var builder = cliente.mutate();
        var servidor = MockRestServiceServer.bindTo(builder).build();
        var http = builder.build();
        servidor.expect(requestTo("http://catalogo.test:8082/internal/v1/stock/descuentos/op"))
                .andExpect(s -> {
                    String h = s.getHeaders().getFirst("Authorization");
                    assertNotNull(h); assertTrue(h.startsWith("Bearer "));
                    assertEquals("ms-pedidos", Jwts.parser()
                            .verifyWith(Keys.hmacShaKeyFor(INTERNA.getBytes(StandardCharsets.UTF_8)))
                            .build().parseSignedClaims(h.substring(7)).getPayload().getSubject());
                }).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        http.get().uri("/internal/v1/stock/descuentos/op").retrieve().toBodilessEntity();
        for (String ruta : new String[] {"http://otro.test/internal/v1/stock/descuentos/op", "/api/v1/productos",
                "/internal/v1/stock/../../../api/v1/productos"}) {
            assertThrows(IllegalArgumentException.class, () -> http.get().uri(ruta).retrieve().toBodilessEntity());
        }
        servidor.verify();
    }

    @Test void exigeClaveIndependienteSinValorPorDefecto() {
        assertThrows(IllegalStateException.class, () -> new TokenServicioCatalogo("", USUARIOS));
        assertThrows(IllegalStateException.class, () -> new TokenServicioCatalogo("corta", USUARIOS));
        assertThrows(IllegalStateException.class, () -> new TokenServicioCatalogo(USUARIOS, USUARIOS));
    }
}
