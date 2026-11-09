package cl.gaselvolcan.ms_pedidos.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import cl.gaselvolcan.ms_pedidos.controller.BoletaController;
import cl.gaselvolcan.ms_pedidos.service.BoletaService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

// Pruebas de SeguridadConfig con tokens firmados como los de ms-usuarios.
// Comprar está abierto (también para visitantes, ERS RF-08); el resto pide sesión y rol.
// Un cuerpo vacío ("{}") que responde 400 significa que la seguridad SÍ dejó pasar
// (la validación de la boleta es lo que lo rechaza).
@WebMvcTest(controllers = BoletaController.class, properties = "jwt.secret=" + SeguridadTest.CLAVE)
@Import(SeguridadConfig.class)
class SeguridadTest {

    static final String CLAVE = "clave-solo-para-las-pruebas-de-gas-el-volcan";
    private static final String BOLETAS = "/api/v1/boletas";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private BoletaService boletas;
    @MockitoBean
    private cl.gaselvolcan.ms_pedidos.service.CompraService compras;

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    static String token(String rol, long minutos) {
        long ahora = System.currentTimeMillis();
        return "Bearer " + Jwts.builder()
                .subject("camila@gmail.com")
                .claim("id", 5L)
                .claim("rol", rol)
                .issuedAt(new Date(ahora))
                .expiration(new Date(ahora + minutos * 60_000))
                .signWith(Keys.hmacShaKeyFor(CLAVE.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
    }

    static String token(String rol) {
        return token(rol, 30);
    }

    // --- Comprar ---

    @Test
    void unVisitanteSinCuentaPuedeComprar() throws Exception {
        mvc.perform(post(BOLETAS).header("Idempotency-Key", java.util.UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void conUnTokenVencidoNoSeCompraComoInvitadoSinAvisar() throws Exception {
        mvc.perform(post(BOLETAS).header("Idempotency-Key", java.util.UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION, token("CLIENTE", -1))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("El token no es válido o ya venció. Inicia sesión de nuevo."));
    }

    // --- Ver boletas ---

    @Test
    void verBoletasPideSesion() throws Exception {
        mvc.perform(get(BOLETAS))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Debes iniciar sesión para continuar."));

        mvc.perform(get(BOLETAS).header(HttpHeaders.AUTHORIZATION, token("CLIENTE")))
                .andExpect(status().isOk());
    }

    // --- Cambiar estado o repartidor ---

    @Test
    void unClienteNoPuedeCambiarUnaBoleta() throws Exception {
        mvc.perform(put(BOLETAS + "/1").header(HttpHeaders.AUTHORIZATION, token("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.estado").value(403));
    }

    @Test
    void elRepartidorYLaDespachadoraPuedenCambiarUnaBoleta() throws Exception {
        for (String rol : new String[] { "REPARTIDOR", "DESPACHADORA", "ADMINISTRADOR" }) {
            mvc.perform(put(BOLETAS + "/1").header(HttpHeaders.AUTHORIZATION, token(rol))
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void endpointEstadoConservaLosPermisosJwt() throws Exception {
        String ruta = BOLETAS + "/1/estado";
        mvc.perform(put(ruta).contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"EN_CAMINO\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(put(ruta).header(HttpHeaders.AUTHORIZATION, token("CLIENTE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"EN_CAMINO\"}"))
                .andExpect(status().isForbidden());
        for (String rol : new String[] {"REPARTIDOR", "DESPACHADORA", "ADMINISTRADOR"}) {
            mvc.perform(put(ruta).header(HttpHeaders.AUTHORIZATION, token(rol))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"EN_CAMINO\"}"))
                    .andExpect(status().isOk());
        }
    }

    // --- Eliminar ---

    @Test
    void ningunRolEliminaBoletas() throws Exception {
        mvc.perform(delete(BOLETAS + "/1"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete(BOLETAS + "/1").header(HttpHeaders.AUTHORIZATION, token("DESPACHADORA")))
                .andExpect(status().isForbidden());

        mvc.perform(delete(BOLETAS + "/1").header(HttpHeaders.AUTHORIZATION, token("ADMINISTRADOR")))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void tokenInternoNoAutenticaComoUsuarioNiAdministrador() throws Exception {
        var generador = new cl.gaselvolcan.ms_pedidos.client.TokenServicioCatalogo(
                "clave-interna-sintetica-exclusiva-de-pruebas-123456", CLAVE);
        String encabezado = "Bearer " + generador.generar();
        mvc.perform(get(BOLETAS).header(HttpHeaders.AUTHORIZATION, encabezado))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete(BOLETAS + "/1").header(HttpHeaders.AUTHORIZATION, encabezado))
                .andExpect(status().isUnauthorized());
    }

    // --- Quién es el usuario ---

    @Test
    void elFiltroEntregaElIdElCorreoYElRolDelToken() throws Exception {
        var peticion = new MockHttpServletRequest();
        peticion.addHeader(HttpHeaders.AUTHORIZATION, token("CLIENTE"));

        new FiltroJwt(CLAVE).doFilter(peticion, new MockHttpServletResponse(), new MockFilterChain());

        var usuario = (UsuarioToken) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertEquals(new UsuarioToken(5L, "camila@gmail.com", "CLIENTE"), usuario);
        assertEquals("camila@gmail.com", usuario.getName());
    }

    @Test
    void sinClaveJwtElServicioNoArranca() {
        assertThrows(IllegalStateException.class, () -> new FiltroJwt(null));
    }
}
