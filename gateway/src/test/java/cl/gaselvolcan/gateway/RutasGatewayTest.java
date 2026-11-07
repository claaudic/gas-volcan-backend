package cl.gaselvolcan.gateway;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.sun.net.httpserver.HttpServer;

// Pruebas de las rutas del Gateway, sin encender los microservicios de verdad:
// cada "microservicio" es un servidor de mentira que responde quién es,
// qué ruta recibió y qué encabezado Authorization le llegó.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RutasGatewayTest {

    private static final String REACT = "http://localhost:5173";

    private static final HttpServer USUARIOS = servidorDePrueba("usuarios");
    private static final HttpServer CATALOGO = servidorDePrueba("catalogo");
    private static final HttpServer PEDIDOS = servidorDePrueba("pedidos");

    @DynamicPropertySource
    static void direcciones(DynamicPropertyRegistry propiedades) {
        propiedades.add("USUARIOS_URL", () -> direccion(USUARIOS));
        propiedades.add("CATALOGO_URL", () -> direccion(CATALOGO));
        propiedades.add("PEDIDOS_URL", () -> direccion(PEDIDOS));
    }

    @AfterAll
    static void apagar() {
        USUARIOS.stop(0);
        CATALOGO.stop(0);
        PEDIDOS.stop(0);
    }

    @Value("${local.server.port}")
    private int puerto;

    private WebTestClient gateway;

    @BeforeEach
    void preparar() {
        gateway = WebTestClient.bindToServer().baseUrl("http://localhost:" + puerto).build();
    }

    // --- Cada ruta llega a su microservicio, con la misma ruta ---

    @Test
    void elLoginVaAMsUsuarios() {
        gateway.post().uri("/api/v1/auth/login").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.servicio").isEqualTo("usuarios")
                .jsonPath("$.ruta").isEqualTo("/api/v1/auth/login");
    }

    @Test
    void losUsuariosVanAMsUsuarios() {
        gateway.get().uri("/api/v1/usuarios/repartidores").exchange()
                .expectBody().jsonPath("$.servicio").isEqualTo("usuarios");
    }

    @Test
    void losProductosYCategoriasVanAMsCatalogo() {
        gateway.get().uri("/api/v1/productos/5").exchange()
                .expectBody()
                .jsonPath("$.servicio").isEqualTo("catalogo")
                .jsonPath("$.ruta").isEqualTo("/api/v1/productos/5");

        gateway.get().uri("/api/v1/categorias").exchange()
                .expectBody().jsonPath("$.servicio").isEqualTo("catalogo");
    }

    @Test
    void lasBoletasVanAMsPedidos() {
        gateway.get().uri("/api/v1/boletas").exchange()
                .expectBody().jsonPath("$.servicio").isEqualTo("pedidos");
    }

    @Test
    void elTokenLlegaIntactoAlMicroservicio() {
        gateway.get().uri("/api/v1/usuarios/yo")
                .header(HttpHeaders.AUTHORIZATION, "Bearer token-de-prueba")
                .exchange()
                .expectBody().jsonPath("$.auth").isEqualTo("Bearer token-de-prueba");
    }

    @Test
    void unaRutaQueNoExisteResponde404() {
        gateway.get().uri("/api/v1/otra-cosa").exchange()
                .expectStatus().isNotFound();
    }

    // --- CORS ---

    @Test
    void reactEnEl5173PuedeLlamarAlGateway() {
        gateway.options().uri("/api/v1/auth/login")
                .header(HttpHeaders.ORIGIN, REACT)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, REACT);
    }

    @Test
    void otraPaginaNoPuedeLlamarAlGateway() {
        gateway.options().uri("/api/v1/auth/login")
                .header(HttpHeaders.ORIGIN, "http://pagina-extrana.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void siElMicroservicioTambienMandaCorsLlegaUnaSolaCabecera() {
        gateway.get().uri("/api/v1/productos")
                .header(HttpHeaders.ORIGIN, REACT)
                .exchange()
                .expectHeader().valuesMatch(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, REACT);
    }

    // --- Servidores de mentira ---

    private static HttpServer servidorDePrueba(String nombre) {
        try {
            HttpServer servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            servidor.createContext("/", intercambio -> {
                String auth = intercambio.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION);
                String json = "{\"servicio\":\"" + nombre + "\",\"ruta\":\"" + intercambio.getRequestURI().getPath()
                        + "\",\"auth\":\"" + (auth == null ? "" : auth) + "\"}";
                byte[] cuerpo = json.getBytes(StandardCharsets.UTF_8);

                intercambio.getResponseHeaders().add("Content-Type", "application/json");
                // Igual que los microservicios reales: también responden su cabecera CORS
                if (intercambio.getRequestHeaders().containsKey(HttpHeaders.ORIGIN)) {
                    intercambio.getResponseHeaders().add(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, REACT);
                }
                intercambio.sendResponseHeaders(200, cuerpo.length);
                intercambio.getResponseBody().write(cuerpo);
                intercambio.close();
            });
            servidor.start();
            return servidor;
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static String direccion(HttpServer servidor) {
        return "http://localhost:" + servidor.getAddress().getPort();
    }
}
