package cl.gaselvolcan.ms_pedidos.client;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TokenServicioCatalogo {
    private final SecretKey clave;

    public TokenServicioCatalogo(@Value("${internal.jwt.secret:}") String secreto,
            @Value("${jwt.secret}") String secretoUsuarios) {
        if (secreto == null || secreto.getBytes(StandardCharsets.UTF_8).length < 32
                || MessageDigest.isEqual(secreto.getBytes(StandardCharsets.UTF_8), secretoUsuarios.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalStateException("Configura INTERNAL_STOCK_JWT_SECRET independiente de JWT_SECRET, de al menos 32 bytes.");
        }
        clave = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
    }

    public String generar() {
        Instant ahora = Instant.now();
        return Jwts.builder().issuer("ms-pedidos").subject("ms-pedidos")
                .audience().add("ms-catalogo").and()
                .claim("scope", "stock:descontar stock:consultar stock:compensar")
                .id(UUID.randomUUID().toString()).issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plusSeconds(60))).signWith(clave, Jwts.SIG.HS256).compact();
    }
}
