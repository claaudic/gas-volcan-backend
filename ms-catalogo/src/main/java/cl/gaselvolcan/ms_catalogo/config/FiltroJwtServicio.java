package cl.gaselvolcan.ms_catalogo.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import javax.crypto.SecretKey;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

final class FiltroJwtServicio extends OncePerRequestFilter {
    private static final Set<String> PERMISOS = Set.of("stock:descontar", "stock:consultar", "stock:compensar");
    private final SecretKey clave;

    FiltroJwtServicio(String secreto, String secretoUsuarios) {
        if (secreto == null || secreto.getBytes(StandardCharsets.UTF_8).length < 32
                || MessageDigest.isEqual(secreto.getBytes(StandardCharsets.UTF_8), secretoUsuarios.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalStateException("Configura INTERNAL_STOCK_JWT_SECRET independiente de JWT_SECRET, de al menos 32 bytes.");
        }
        clave = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest solicitud, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        String encabezado = solicitud.getHeader("Authorization");
        try {
            if (encabezado == null || !encabezado.startsWith("Bearer ")) throw new IllegalArgumentException();
            var jwt = Jwts.parser().verifyWith(clave)
                    .sig().clear().add(Jwts.SIG.HS256).and()
                    .requireIssuer("ms-pedidos").requireSubject("ms-pedidos")
                    .requireAudience("ms-catalogo").build().parseSignedClaims(encabezado.substring(7));
            var datos = jwt.getPayload();
            Instant ahora = Instant.now();
            if (datos.getExpiration() == null || datos.getIssuedAt() == null
                    || datos.getIssuedAt().toInstant().isAfter(ahora)
                    || !datos.getExpiration().after(datos.getIssuedAt())
                    || datos.getExpiration().getTime() - datos.getIssuedAt().getTime() > 300_000) {
                throw new IllegalArgumentException();
            }
            String scope = datos.get("scope", String.class);
            if (scope == null || scope.isBlank()) throw new IllegalArgumentException();
            var permisos = Arrays.stream(scope.split(" ")).distinct().toList();
            if (!PERMISOS.containsAll(permisos)) throw new IllegalArgumentException();
            var identidad = new ServicioAutenticado(datos.getSubject());
            var autenticacion = new UsernamePasswordAuthenticationToken(identidad, null,
                    permisos.stream().map(SimpleGrantedAuthority::new).toList());
            var contexto = SecurityContextHolder.createEmptyContext();
            contexto.setAuthentication(autenticacion);
            SecurityContextHolder.setContext(contexto);
        } catch (JwtException | IllegalArgumentException error) {
            SecurityContextHolder.clearContext();
            SeguridadConfig.responderJson(respuesta, 401, "Token interno ausente, invalido o vencido.");
            return;
        }
        cadena.doFilter(solicitud, respuesta);
    }
}
