package cl.gaselvolcan.ms_catalogo.config;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import jakarta.servlet.http.HttpServletResponse;

// Quién puede entrar a cada ruta de ms-catalogo.
// Cada microservicio revisa el token por su cuenta: el Gateway solo reparte.
// Sin token en una ruta protegida → 401. Con un rol que no corresponde → 403.
@Configuration
public class SeguridadConfig {

    static final String SIN_SESION = "Debes iniciar sesión para continuar.";
    static final String TOKEN_MALO = "El token no es válido o ya venció. Inicia sesión de nuevo.";
    static final String SIN_PERMISO = "No tienes permiso para realizar esta acción.";

    @Bean
    public SecurityFilterChain filtroDeSeguridad(HttpSecurity http, @Value("${jwt.secret}") String secreto) throws Exception {
        http
            // CSRF protege sesiones con cookies; esta API usa el token en el encabezado
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            // No se guardan sesiones: cada petición se identifica con su token
            .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Las reglas se leen de arriba hacia abajo: gana la primera que calza
            .authorizeHttpRequests(rutas -> rutas
                // La tienda es pública: cualquiera ve productos, categorías y stock
                .requestMatchers(HttpMethod.GET, "/api/v1/productos/**", "/api/v1/categorias/**").permitAll()
                // Swagger y la página de error
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/error").permitAll()
                // Crear, editar y eliminar productos o categorías: solo el administrador
                .requestMatchers("/api/v1/**").hasRole("ADMINISTRADOR")
                .anyRequest().authenticated())

            // Los 401 y 403 también responden en JSON, igual que ManejadorErrores
            .exceptionHandling(errores -> errores
                .authenticationEntryPoint((peticion, respuesta, error) -> responderJson(respuesta, 401, SIN_SESION))
                .accessDeniedHandler((peticion, respuesta, error) -> responderJson(respuesta, 403, SIN_PERMISO)))

            .addFilterBefore(new FiltroJwt(secreto), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // Mismo formato que ErrorRespuesta: { "estado": ..., "mensaje": "..." }
    static void responderJson(HttpServletResponse respuesta, int estado, String mensaje) throws IOException {
        respuesta.setStatus(estado);
        respuesta.setContentType("application/json");
        respuesta.setCharacterEncoding("UTF-8");
        respuesta.getWriter().write("{\"estado\":" + estado + ",\"mensaje\":\"" + mensaje + "\"}");
    }
}
