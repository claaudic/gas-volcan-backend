package cl.gaselvolcan.usuarios.config;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import cl.gaselvolcan.usuarios.service.JwtService;
import jakarta.servlet.http.HttpServletResponse;

// Quién puede entrar a cada ruta.
// Sin token (o con uno malo) en una ruta protegida → 401.
// Con token, pero con un rol que no corresponde → 403.
@Configuration
public class SeguridadConfig {

    private static final String SIN_SESION = "Debes iniciar sesión para continuar.";
    private static final String TOKEN_MALO = "El token no es válido o ya venció. Inicia sesión de nuevo.";
    private static final String SIN_PERMISO = "No tienes permiso para realizar esta acción.";

    @Bean
    public SecurityFilterChain filtroDeSeguridad(HttpSecurity http, JwtService jwt) throws Exception {
        http
            // CSRF protege sesiones con cookies; esta API usa token en el encabezado, no cookies
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            // El servidor no guarda sesiones: cada petición se identifica con su token
            .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Las reglas se leen de arriba hacia abajo: gana la primera que calza
            .authorizeHttpRequests(rutas -> rutas
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/**").permitAll()
                .requestMatchers("/error").permitAll()
                // Para saber si el servicio está vivo (Docker, despliegue): sin datos internos
                .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/usuarios/repartidores")
                    .hasAnyRole("ADMINISTRADOR", "DESPACHADORA")
                // Como en la EP2: solo el cliente puede eliminar su propia cuenta
                .requestMatchers(HttpMethod.DELETE, "/api/v1/usuarios/yo").hasRole("CLIENTE")
                .requestMatchers("/api/v1/usuarios/yo").authenticated()
                .requestMatchers("/api/v1/usuarios/**").hasRole("ADMINISTRADOR")
                .anyRequest().authenticated())

            // Los 401 y 403 también responden en JSON, igual que ManejadorErrores
            .exceptionHandling(errores -> errores
                .authenticationEntryPoint((peticion, respuesta, error) -> {
                    boolean trajoToken = peticion.getHeader(HttpHeaders.AUTHORIZATION) != null;
                    responderJson(respuesta, 401, trajoToken ? TOKEN_MALO : SIN_SESION);
                })
                .accessDeniedHandler((peticion, respuesta, error) -> responderJson(respuesta, 403, SIN_PERMISO)))

            // FiltroJwt va antes del filtro de login de Spring
            .addFilterBefore(new FiltroJwt(jwt), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // CORS: el navegador solo deja que React (otro puerto) llame a esta API
    // si el backend lo autoriza. Aquí se autoriza el origen del frontend.
    // El método debe llamarse corsConfigurationSource: Spring Security lo busca por ese nombre.
    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${cors.origenes}") String[] origenes) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(origenes));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE));

        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/api/**", config);
        return fuente;
    }

    // Cifra las contraseñas con BCrypt antes de guardarlas
    @Bean
    public PasswordEncoder cifradorDeContrasenas() {
        return new BCryptPasswordEncoder();
    }

    // Mismo formato que ErrorRespuesta: { "estado": ..., "mensaje": "..." }
    private static void responderJson(HttpServletResponse respuesta, int estado, String mensaje) throws IOException {
        respuesta.setStatus(estado);
        respuesta.setContentType("application/json");
        respuesta.setCharacterEncoding("UTF-8");
        respuesta.getWriter().write("{\"estado\":" + estado + ",\"mensaje\":\"" + mensaje + "\"}");
    }
}
