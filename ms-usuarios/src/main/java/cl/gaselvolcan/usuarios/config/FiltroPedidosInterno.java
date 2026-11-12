package cl.gaselvolcan.usuarios.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// Protege la ruta interna /internal/v1/usuarios/{id}/pedido, que solo usa ms-pedidos
// para conocer el nombre y contacto de un cliente. No pasa por el Gateway.
// ms-pedidos firma un token corto (30 s) con una clave derivada de JWT_SECRET:
// así ese token no sirve como token de persona, ni un token de persona sirve aquí.
public class FiltroPedidosInterno extends OncePerRequestFilter {

    // Debe ser la misma etiqueta que usa UsuariosPedidosClient en ms-pedidos
    public static final String ETIQUETA_CLAVE = "gas-volcan:pedidos-usuarios:v1";
    public static final String PERMISO = "PEDIDOS_USUARIO";

    private static final String PREFIJO = "Bearer ";
    private static final long VIDA_MAXIMA_MS = 60_000;

    private final SecretKey clave;

    public FiltroPedidosInterno(String secreto) {
        this.clave = derivarClave(secreto);
    }

    // HMAC(JWT_SECRET, etiqueta): una clave distinta a la de los tokens de persona
    static SecretKey derivarClave(String secreto) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Keys.hmacShaKeyFor(mac.doFinal(ETIQUETA_CLAVE.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("No se pudo crear la clave interna de pedidos.", error);
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta,
                                    FilterChain cadena) throws ServletException, IOException {
        if (!tokenValido(peticion)) {
            SecurityContextHolder.clearContext();
            respuesta.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            respuesta.setContentType("application/json");
            respuesta.setCharacterEncoding("UTF-8");
            respuesta.getWriter().write("{\"estado\":401,\"mensaje\":\"Autenticacion interna no valida.\"}");
            return;
        }

        var servicio = new UsernamePasswordAuthenticationToken("ms-pedidos", null,
                List.of(new SimpleGrantedAuthority(PERMISO)));
        SecurityContextHolder.getContext().setAuthentication(servicio);
        cadena.doFilter(peticion, respuesta);
    }

    private boolean tokenValido(HttpServletRequest peticion) {
        String encabezado = peticion.getHeader(HttpHeaders.AUTHORIZATION);
        if (encabezado == null || !encabezado.startsWith(PREFIJO)) {
            return false;
        }
        try {
            // Revisa firma, vencimiento, emisor, sujeto y alcance
            Jws<Claims> token = Jwts.parser().verifyWith(clave)
                    .requireIssuer("ms-pedidos").requireSubject("ms-pedidos").require("scope", "pedidos:usuario")
                    .build().parseSignedClaims(encabezado.substring(PREFIJO.length()));
            Claims datos = token.getPayload();
            Long usuarioId = datos.get("usuarioId", Long.class);

            return "HS256".equals(token.getHeader().getAlgorithm())
                    && Set.of("ms-usuarios").equals(datos.getAudience())
                    && vigenciaCorta(datos)
                    && usuarioId != null && usuarioId > 0
                    // El token solo sirve para consultar al usuario que nombra
                    && peticion.getRequestURI().equals("/internal/v1/usuarios/" + usuarioId + "/pedido");
        } catch (RuntimeException error) {
            return false;
        }
    }

    // El token no puede venir del futuro ni durar más de 60 segundos
    private boolean vigenciaCorta(Claims datos) {
        Date emitido = datos.getIssuedAt();
        Date vence = datos.getExpiration();
        if (emitido == null || vence == null) {
            return false;
        }
        long duracion = vence.getTime() - emitido.getTime();
        return emitido.getTime() <= System.currentTimeMillis() + 1000 && duracion > 0 && duracion <= VIDA_MAXIMA_MS;
    }
}
