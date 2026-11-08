package cl.gaselvolcan.ms_catalogo.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.function.Consumer;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import cl.gaselvolcan.ms_catalogo.controller.ProductoController;
import cl.gaselvolcan.ms_catalogo.service.ProductoService;

@WebMvcTest(controllers = ProductoController.class, properties = {
        "jwt.secret=" + SeguridadInternaTest.USUARIOS,
        "internal.jwt.secret=" + SeguridadInternaTest.INTERNA
})
@Import({SeguridadConfig.class, SeguridadInternaConfig.class, SeguridadInternaTest.RutasPrueba.class})
class SeguridadInternaTest {
    static final String USUARIOS = "clave-usuarios-sintetica-de-pruebas-no-productiva-12345678901234567890";
    static final String INTERNA = "clave-interna-sintetica-de-pruebas-no-productiva-12345678901234567890";
    private static final String RUTA = "/internal/v1/stock/descuentos";
    @Autowired MockMvc mvc;
    @MockitoBean ProductoService productos;

    // Solo existen en el contexto de pruebas; no descuentan existencias.
    @RestController
    static class RutasPrueba {
        @PostMapping(RUTA) String descontar() { return "ok"; }
        @GetMapping(RUTA + "/{id}") String consultar() { return "ok"; }
        @PutMapping(RUTA + "/{id}/compensacion") String compensar() { return "ok"; }
        @GetMapping("/internal/v1/stock/otra") String otra() { return "no"; }
    }

    private JwtBuilder datos() {
        Instant ahora = Instant.now();
        return Jwts.builder().issuer("ms-pedidos").subject("ms-pedidos")
                .audience().add("ms-catalogo").and().issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plusSeconds(60)))
                .claim("scope", "stock:consultar stock:descontar stock:compensar");
    }
    private String token(Consumer<JwtBuilder> cambio) {
        var b = datos(); cambio.accept(b);
        return "Bearer " + b.signWith(Keys.hmacShaKeyFor(INTERNA.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256).compact();
    }

    @Test void permiteCadaOperacionSoloConSuPermiso() throws Exception {
        mvc.perform(post(RUTA).header("Authorization", token(b -> b.claim("scope", "stock:descontar"))))
                .andExpect(status().isOk());
        mvc.perform(get(RUTA + "/op").header("Authorization", token(b -> b.claim("scope", "stock:consultar"))))
                .andExpect(status().isOk());
        mvc.perform(put(RUTA + "/op/compensacion").header("Authorization", token(b -> b.claim("scope", "stock:compensar"))))
                .andExpect(status().isOk());
        mvc.perform(post(RUTA).header("Authorization", token(b -> b.claim("scope", "stock:consultar"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/internal/v1/stock/otra").header("Authorization", token(b -> {})))
                .andExpect(status().isForbidden());
    }

    @Test void rechazaAusentesMalformadosYUsuariosInclusoAdministradores() throws Exception {
        mvc.perform(post(RUTA)).andExpect(status().isUnauthorized());
        String usuario = "Bearer " + Jwts.builder().subject("admin@example.test").claim("rol", "ADMINISTRADOR")
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(USUARIOS.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256).compact();
        for (String t : new String[] {"Bearer invalido", "Basic invalido", usuario}) {
            mvc.perform(post(RUTA).header("Authorization", t)).andExpect(status().isUnauthorized());
        }
    }

    @Test void rechazaClaimsInvalidosOAusentes() throws Exception {
        var cambios = java.util.List.<Consumer<JwtBuilder>>of(
                b -> b.issuer("otro"), b -> b.subject("otro"),
                b -> b.audience().clear().add("otro").and(),
                b -> b.issuer(null), b -> b.subject(null), b -> b.audience().clear().and(),
                b -> b.expiration(null), b -> b.issuedAt(null),
                b -> b.expiration(Date.from(Instant.now().minusSeconds(10))),
                b -> b.issuedAt(Date.from(Instant.now().plusSeconds(20))),
                b -> b.expiration(Date.from(Instant.now().plusSeconds(600))),
                b -> b.claim("scope", null), b -> b.claim("scope", "ADMINISTRADOR"));
        for (var cambio : cambios) {
            mvc.perform(post(RUTA).header("Authorization", token(cambio))).andExpect(status().isUnauthorized());
        }
    }

    @Test void rechazaAlgoritmoDistintoAunqueLaClaveSeaCorrecta() throws Exception {
        String t = "Bearer " + datos().signWith(Keys.hmacShaKeyFor(INTERNA.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS384).compact();
        mvc.perform(post(RUTA).header("Authorization", t)).andExpect(status().isUnauthorized());
    }

    @Test void tokenInternoNoAutorizaRutasActualesAunqueDeclareRolAdmin() throws Exception {
        String t = token(b -> b.claim("rol", "ADMINISTRADOR"));
        mvc.perform(delete("/api/v1/productos/1").header("Authorization", t)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/productos")).andExpect(status().isOk());
        String admin = "Bearer " + Jwts.builder().subject("admin@example.test").claim("rol", "ADMINISTRADOR")
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(USUARIOS.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256).compact();
        mvc.perform(delete("/api/v1/productos/1").header("Authorization", admin))
                .andExpect(status().isNoContent());
    }

    @Test void noArrancaSinClaveIndependiente() {
        assertThrows(IllegalStateException.class, () -> new FiltroJwtServicio("", USUARIOS));
        assertThrows(IllegalStateException.class, () -> new FiltroJwtServicio("corta", USUARIOS));
        assertThrows(IllegalStateException.class, () -> new FiltroJwtServicio(USUARIOS, USUARIOS));
    }
}
