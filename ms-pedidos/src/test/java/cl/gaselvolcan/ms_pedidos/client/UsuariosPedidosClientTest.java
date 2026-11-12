package cl.gaselvolcan.ms_pedidos.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import cl.gaselvolcan.ms_pedidos.exception.*;

class UsuariosPedidosClientTest {
    static final String CLAVE = "clave-sintetica-usuarios-internos-1234567890";
    MockRestServiceServer servidor;
    UsuariosPedidosClient cliente;
    @BeforeEach void preparar() {
        var builder = RestClient.builder().baseUrl("http://usuarios.test");
        servidor = MockRestServiceServer.bindTo(builder).build(); cliente = new UsuariosPedidosClient(builder.build(), CLAVE);
    }
    @AfterEach void verificar() { servidor.verify(); }
    @Test void emiteCredencialAcotadaAlUsuarioYLaRutaInterna() throws Exception {
        var mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(CLAVE.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        var clave = Keys.hmacShaKeyFor(mac.doFinal("gas-volcan:pedidos-usuarios:v1".getBytes(StandardCharsets.UTF_8)));
        servidor.expect(requestTo("http://usuarios.test/internal/v1/usuarios/5/pedido")).andExpect(method(HttpMethod.GET))
                .andExpect(q -> {
                    String token = q.getHeaders().getFirst("Authorization").substring(7);
                    var datos = Jwts.parser().verifyWith(clave).build().parseSignedClaims(token).getPayload();
                    assertEquals(5L, datos.get("usuarioId", Long.class)); assertEquals("pedidos:usuario", datos.get("scope"));
                    assertEquals(30_000, datos.getExpiration().getTime() - datos.getIssuedAt().getTime());
                    assertNull(datos.get("rol"));
                }).andRespond(withSuccess("{\"id\":5,\"nombre\":\"Persona Prueba\",\"correo\":\"p@example.test\",\"telefono\":null,\"rol\":\"REPARTIDOR\",\"activo\":true}", MediaType.APPLICATION_JSON));
        assertEquals(5L, cliente.consultar(5L).id());
    }
    @ParameterizedTest @ValueSource(ints = {401, 403, 500, 503})
    void noReintentaNiSustituyeErroresConDatosLocales(int codigo) {
        servidor.expect(requestTo("http://usuarios.test/internal/v1/usuarios/5/pedido")).andRespond(withStatus(HttpStatusCode.valueOf(codigo)));
        assertThrows(CatalogoNoDisponibleException.class, () -> cliente.consultar(5L));
    }
    @Test void cuentaEliminadaInforma404SinInventarContacto() {
        servidor.expect(anything()).andRespond(withResourceNotFound());
        assertThrows(RecursoNoEncontradoException.class, () -> cliente.consultar(5L));
    }
    @Test void respuestaInconsistenteNoSeUsa() {
        servidor.expect(anything()).andRespond(withSuccess("{\"id\":6}", MediaType.APPLICATION_JSON));
        assertThrows(CatalogoNoDisponibleException.class, () -> cliente.consultar(5L));
    }
}
