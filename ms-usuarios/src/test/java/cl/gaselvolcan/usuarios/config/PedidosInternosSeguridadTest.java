package cl.gaselvolcan.usuarios.config;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import cl.gaselvolcan.usuarios.controller.UsuarioPedidoInternoController;
import cl.gaselvolcan.usuarios.service.*;
import cl.gaselvolcan.usuarios.dto.UsuarioRespuesta;
import cl.gaselvolcan.usuarios.model.Rol;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@WebMvcTest(controllers = UsuarioPedidoInternoController.class, properties = {
    "jwt.secret=clave-sintetica-usuarios-internos-1234567890", "jwt.expiracion-minutos=30", "cors.origenes=http://localhost:5173"
})
@Import({SeguridadPedidosInternaConfig.class, SeguridadConfig.class, JwtService.class})
class PedidosInternosSeguridadTest {
    static final String CLAVE = "clave-sintetica-usuarios-internos-1234567890";
    @Autowired MockMvc mvc;
    @MockitoBean UsuarioService usuarios;
    @BeforeEach void preparar() {
        when(usuarios.buscarPorId(5L)).thenReturn(new UsuarioRespuesta(5L, "privado", "Persona", "Prueba",
                "persona@example.test", Rol.REPARTIDOR, "+56912345678", "Comuna", "Direccion", null, null, true));
    }
    String token(String variante) throws Exception {
        var mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(CLAVE.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        var clave = Keys.hmacShaKeyFor(variante.equals("publico") ? CLAVE.getBytes(StandardCharsets.UTF_8)
                : mac.doFinal("gas-volcan:pedidos-usuarios:v1".getBytes(StandardCharsets.UTF_8)));
        long ahora = System.currentTimeMillis();
        return Jwts.builder().issuer(variante.equals("emisor") ? "otro" : "ms-pedidos").subject("ms-pedidos")
                .audience().add(variante.equals("audiencia") ? "otro" : "ms-usuarios").and()
                .claim("scope", variante.equals("scope") ? "otro" : "pedidos:usuario")
                .claim("usuarioId", variante.equals("id") ? 6L : 5L).issuedAt(new Date(ahora - 1000))
                .expiration(new Date(variante.equals("vencido") ? ahora - 1 : ahora + (variante.equals("duracion") ? 120_000 : 29_000)))
                .signWith(clave, Jwts.SIG.HS256).compact();
    }
    @Test void respondeSoloLosCamposNecesariosConTokenInternoLimitado() throws Exception {
        mvc.perform(get("/internal/v1/usuarios/5/pedido").header("Authorization", "Bearer " + token("valido")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Persona Prueba"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.run").doesNotExist()).andExpect(jsonPath("$.direccion").doesNotExist())
                .andExpect(jsonPath("$.nacimiento").doesNotExist()).andExpect(jsonPath("$.contrasena").doesNotExist());
    }
    @ParameterizedTest @ValueSource(strings = {"publico", "emisor", "audiencia", "scope", "id", "vencido", "duracion"})
    void rechazaCredencialesFueraDeSuAlcance(String variante) throws Exception {
        mvc.perform(get("/internal/v1/usuarios/5/pedido").header("Authorization", "Bearer " + token(variante)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(usuarios);
    }
    @Test void exigeTokenYNoPermiteEscriturasNiReutilizarloEnApiPublica() throws Exception {
        mvc.perform(get("/internal/v1/usuarios/5/pedido")).andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/v1/usuarios/5/pedido").header("Authorization", "Bearer " + token("valido")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/usuarios/5").header("Authorization", "Bearer " + token("valido")))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(usuarios);
    }
}
