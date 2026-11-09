package cl.gaselvolcan.ms_catalogo.config;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import cl.gaselvolcan.ms_catalogo.controller.CategoriaController;
import cl.gaselvolcan.ms_catalogo.controller.ProductoController;
import cl.gaselvolcan.ms_catalogo.service.CategoriaService;
import cl.gaselvolcan.ms_catalogo.service.ProductoService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

// Pruebas de SeguridadConfig con tokens firmados como los de ms-usuarios:
// la tienda es pública, y crear/editar/eliminar es solo del administrador.
@WebMvcTest(controllers = { ProductoController.class, CategoriaController.class },
        properties = "jwt.secret=" + SeguridadTest.CLAVE)
@org.springframework.test.context.TestPropertySource(properties = "internal.jwt.secret=clave-interna-sintetica-exclusiva-de-pruebas-123456")
@Import(SeguridadConfig.class)
class SeguridadTest {

    static final String CLAVE = "clave-solo-para-las-pruebas-de-gas-el-volcan";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProductoService productos;

    @MockitoBean
    private CategoriaService categorias;

    // Un token con la misma forma que los de ms-usuarios
    static String token(String rol, String clave, long minutos) {
        long ahora = System.currentTimeMillis();
        return "Bearer " + Jwts.builder()
                .subject("prueba@gaselvolcan.cl")
                .claim("id", 9L)
                .claim("rol", rol)
                .issuedAt(new Date(ahora))
                .expiration(new Date(ahora + minutos * 60_000))
                .signWith(Keys.hmacShaKeyFor(clave.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
    }

    static String token(String rol) {
        return token(rol, CLAVE, 30);
    }

    // --- Público ---

    @Test
    void cualquieraVeLosProductosLasCategoriasYElStock() throws Exception {
        mvc.perform(get("/api/v1/productos")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/categorias")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/productos/1/disponibilidad")).andExpect(status().isOk());
    }

    // --- 401 ---

    @Test
    void crearSinTokenResponde401EnJson() throws Exception {
        mvc.perform(post("/api/v1/productos").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.estado").value(401))
                .andExpect(jsonPath("$.mensaje").value("Debes iniciar sesión para continuar."));
    }

    @Test
    void unTokenFalsoOVencidoOFirmadoConOtraClaveResponde401() throws Exception {
        mvc.perform(delete("/api/v1/productos/1").header(HttpHeaders.AUTHORIZATION, "Bearer token-inventado"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("El token no es válido o ya venció. Inicia sesión de nuevo."));

        mvc.perform(delete("/api/v1/productos/1").header(HttpHeaders.AUTHORIZATION, token("ADMINISTRADOR", CLAVE, -1)))
                .andExpect(status().isUnauthorized());

        mvc.perform(delete("/api/v1/productos/1")
                        .header(HttpHeaders.AUTHORIZATION, token("ADMINISTRADOR", "otra-clave-distinta-de-mas-de-32-caracteres", 30)))
                .andExpect(status().isUnauthorized());
    }

    // --- 403 ---

    @Test
    void unClienteOLaDespachadoraNoPuedenCambiarElCatalogo() throws Exception {
        mvc.perform(delete("/api/v1/productos/1").header(HttpHeaders.AUTHORIZATION, token("CLIENTE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.estado").value(403))
                .andExpect(jsonPath("$.mensaje").value("No tienes permiso para realizar esta acción."));

        mvc.perform(delete("/api/v1/categorias/1").header(HttpHeaders.AUTHORIZATION, token("DESPACHADORA")))
                .andExpect(status().isForbidden());
    }

    // --- Rol correcto ---

    @Test
    void elAdministradorPuedeEliminar() throws Exception {
        mvc.perform(delete("/api/v1/productos/1").header(HttpHeaders.AUTHORIZATION, token("ADMINISTRADOR")))
                .andExpect(status().isNoContent());
    }

    @Test
    void sinClaveJwtElServicioNoArranca() {
        assertThrows(IllegalStateException.class, () -> new FiltroJwt(""));
        assertThrows(IllegalStateException.class, () -> new FiltroJwt("corta"));
    }
}
