package cl.gaselvolcan.ms_pedidos.client;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import cl.gaselvolcan.ms_pedidos.config.CatalogoConfig;
import cl.gaselvolcan.ms_pedidos.exception.CatalogoNoDisponibleException;
import cl.gaselvolcan.ms_pedidos.exception.RecursoNoEncontradoException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class UsuariosPedidosClient {
    public record UsuarioPedido(Long id, String nombre, String correo, String telefono, String rol, Boolean activo) {}
    // Debe ser la misma etiqueta que usa FiltroPedidosInterno en ms-usuarios
    private static final String ETIQUETA_CLAVE = "gas-volcan:pedidos-usuarios:v1";
    private final RestClient http;
    private final SecretKey clave;

    @Autowired
    public UsuariosPedidosClient(@Value("${pedidos.usuarios.url}") URI url,
            @Value("${jwt.secret}") String secreto) {
        this(new CatalogoConfig().catalogoRestClient(url, Duration.ofSeconds(2), Duration.ofSeconds(5)), secreto);
    }

    public UsuariosPedidosClient(RestClient http, String secreto) {
        this.http = http;
        try {
            // Separación criptográfica: este token no autentica en las APIs públicas.
            if (secreto == null || secreto.getBytes(StandardCharsets.UTF_8).length < 32) throw new IllegalArgumentException();
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            clave = Keys.hmacShaKeyFor(mac.doFinal(ETIQUETA_CLAVE.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("No se pudo configurar la autenticacion interna de usuarios.", error);
        }
    }

    public UsuarioPedido consultar(Long id) {
        if (id == null || id <= 0) throw new IllegalArgumentException("ID de usuario invalido.");
        var ahora = new Date();
        String token = Jwts.builder().issuer("ms-pedidos").subject("ms-pedidos")
                .audience().add("ms-usuarios").and().claim("scope", "pedidos:usuario").claim("usuarioId", id)
                .issuedAt(ahora).expiration(new Date(ahora.getTime() + 30_000)).signWith(clave, Jwts.SIG.HS256).compact();
        try {
            var usuario = http.get().uri("/internal/v1/usuarios/{id}/pedido", id)
                    .headers(h -> h.setBearerAuth(token)).retrieve()
                    .onStatus(s -> s.value() != 200, (q, r) -> {
                        if (r.getStatusCode().value() == 404) throw new RecursoNoEncontradoException("No existe la cuenta asociada al pedido.");
                        throw noDisponible();
                    }).body(UsuarioPedido.class);
            if (usuario == null || !id.equals(usuario.id()) || usuario.nombre() == null || usuario.nombre().isBlank()
                    || usuario.correo() == null || usuario.correo().isBlank() || usuario.rol() == null || usuario.activo() == null) {
                throw noDisponible();
            }
            return usuario;
        } catch (RestClientException error) { throw noDisponible(); }
    }
    private CatalogoNoDisponibleException noDisponible() {
        return new CatalogoNoDisponibleException("No se pudieron comprobar los datos de la cuenta asociada al pedido.");
    }
}
