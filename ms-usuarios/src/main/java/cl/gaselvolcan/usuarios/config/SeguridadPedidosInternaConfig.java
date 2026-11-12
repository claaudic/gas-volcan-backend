package cl.gaselvolcan.usuarios.config;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Configuration
public class SeguridadPedidosInternaConfig {
    @Bean @Order(1)
    SecurityFilterChain pedidosInternos(HttpSecurity http, @Value("${jwt.secret}") String secreto) throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        var clave = Keys.hmacShaKeyFor(mac.doFinal("gas-volcan:pedidos-usuarios:v1".getBytes(StandardCharsets.UTF_8)));
        var filtro = new OncePerRequestFilter() {
            @Override protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest q,
                    jakarta.servlet.http.HttpServletResponse r, jakarta.servlet.FilterChain cadena)
                    throws java.io.IOException, jakarta.servlet.ServletException {
                try {
                    String cabecera = q.getHeader("Authorization");
                    if (cabecera == null || !cabecera.startsWith("Bearer ")) throw new IllegalArgumentException();
                    var jwt = Jwts.parser().verifyWith(clave).requireIssuer("ms-pedidos").requireSubject("ms-pedidos")
                            .require("scope", "pedidos:usuario").build().parseSignedClaims(cabecera.substring(7));
                    var datos = jwt.getPayload();
                    Long id = datos.get("usuarioId", Long.class);
                    Date emitido = datos.getIssuedAt(), vence = datos.getExpiration();
                    if (!"HS256".equals(jwt.getHeader().getAlgorithm()) || datos.getAudience() == null
                            || !datos.getAudience().equals(java.util.Set.of("ms-usuarios"))
                            || id == null || id <= 0 || emitido == null || vence == null
                            || emitido.getTime() > System.currentTimeMillis() + 1000
                            || vence.getTime() <= emitido.getTime() || vence.getTime() - emitido.getTime() > 60_000
                            || !q.getRequestURI().equals("/internal/v1/usuarios/" + id + "/pedido")) throw new IllegalArgumentException();
                    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                            "ms-pedidos", null, List.of(new SimpleGrantedAuthority("PEDIDOS_USUARIO"))));
                } catch (RuntimeException error) {
                    SecurityContextHolder.clearContext();
                    r.setStatus(401); r.setContentType("application/json"); r.setCharacterEncoding("UTF-8");
                    r.getWriter().write("{\"estado\":401,\"mensaje\":\"Autenticacion interna no valida.\"}");
                    return;
                }
                cadena.doFilter(q, r);
            }
        };
        return http.securityMatcher("/internal/v1/usuarios/**").csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.requestMatchers(HttpMethod.GET, "/internal/v1/usuarios/*/pedido")
                        .hasAuthority("PEDIDOS_USUARIO").anyRequest().denyAll())
                .addFilterBefore(filtro, UsernamePasswordAuthenticationFilter.class).build();
    }
}
