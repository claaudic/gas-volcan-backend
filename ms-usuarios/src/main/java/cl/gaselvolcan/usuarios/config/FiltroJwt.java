package cl.gaselvolcan.usuarios.config;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import cl.gaselvolcan.usuarios.servicio.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// Se ejecuta en CADA petición, antes de llegar al controlador.
// Si trae "Authorization: Bearer <token>" y el token es válido, le dice a Spring
// quién es el usuario (su correo) y qué rol tiene. Si no trae token, o es malo,
// la petición sigue sin usuario: SeguridadConfig decide si eso es 401.
public class FiltroJwt extends OncePerRequestFilter {

    private static final String PREFIJO = "Bearer ";

    private final JwtService jwt;

    public FiltroJwt(JwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta,
                                    FilterChain cadena) throws ServletException, IOException {
        String encabezado = peticion.getHeader(HttpHeaders.AUTHORIZATION);

        if (encabezado != null && encabezado.startsWith(PREFIJO)) {
            try {
                Claims datos = jwt.verificar(encabezado.substring(PREFIJO.length()));

                // Spring reconoce los roles con el prefijo ROLE_ (ROLE_ADMINISTRADOR, ROLE_CLIENTE...)
                var rol = new SimpleGrantedAuthority("ROLE_" + datos.get("rol", String.class));
                var usuario = new UsernamePasswordAuthenticationToken(datos.getSubject(), null, List.of(rol));
                SecurityContextHolder.getContext().setAuthentication(usuario);
            } catch (JwtException | IllegalArgumentException error) {
                // Token malo o vencido: se sigue sin usuario
            }
        }

        cadena.doFilter(peticion, respuesta);
    }
}
