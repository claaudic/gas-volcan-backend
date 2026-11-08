package cl.gaselvolcan.ms_catalogo.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.SecretKey;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// Se ejecuta en CADA petición. Este microservicio no crea tokens (eso lo hace
// ms-usuarios): solo revisa la firma con la misma clave JWT_SECRET y la fecha.
// - Sin token: sigue sin usuario (SeguridadConfig decide si la ruta es pública).
// - Token válido: Spring sabe quién es (UsuarioToken) y su rol (ROLE_...).
// - Token malo o vencido: responde 401 de inmediato, para que nadie quede
//   comprando "como invitado" sin darse cuenta de que su sesión venció.
public class FiltroJwt extends OncePerRequestFilter {

    private static final String PREFIJO = "Bearer ";

    private final SecretKey clave;

    public FiltroJwt(String secreto) {
        // HS256 necesita una clave de al menos 32 bytes. Si falta, se avisa con un mensaje claro.
        if (secreto == null || secreto.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "Falta JWT_SECRET (al menos 32 caracteres, la misma de ms-usuarios) en el .env o en las variables de entorno.");
        }
        this.clave = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta,
                                    FilterChain cadena) throws ServletException, IOException {
        String encabezado = peticion.getHeader(HttpHeaders.AUTHORIZATION);

        if (encabezado != null && encabezado.startsWith(PREFIJO)) {
            try {
                Claims datos = Jwts.parser().verifyWith(clave).build()
                        .parseSignedClaims(encabezado.substring(PREFIJO.length()))
                        .getPayload();

                String rol = datos.get("rol", String.class);
                var usuario = new UsuarioToken(datos.get("id", Long.class), datos.getSubject(), rol);
                var autenticacion = new UsernamePasswordAuthenticationToken(
                        usuario, null, List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
                SecurityContextHolder.getContext().setAuthentication(autenticacion);
            } catch (JwtException | IllegalArgumentException error) {
                SeguridadConfig.responderJson(respuesta, 401, SeguridadConfig.TOKEN_MALO);
                return;
            }
        }

        cadena.doFilter(peticion, respuesta);
    }
}
