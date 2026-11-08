package cl.gaselvolcan.ms_pedidos.config;

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

// Quién puede entrar a cada ruta de ms-pedidos.
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
                .requestMatchers("/error").permitAll()
                // Comprar: también el visitante sin cuenta (ERS, RF-08). Si viene con
                // sesión, el cliente se identifica con su token (UsuarioToken)
                .requestMatchers(HttpMethod.POST, "/api/v1/boletas").permitAll()
                // Eliminar una boleta: solo el administrador
                .requestMatchers(HttpMethod.DELETE, "/api/v1/boletas/**").hasRole("ADMINISTRADOR")
                // Cambiar estado o repartidor: administrador, despachadora y repartidor
                .requestMatchers(HttpMethod.PUT, "/api/v1/boletas/**")
                    .hasAnyRole("ADMINISTRADOR", "DESPACHADORA", "REPARTIDOR")
                // Ver boletas: cualquiera con sesión (qué boletas ve cada rol lo filtra BoletaService)
                .requestMatchers(HttpMethod.GET, "/api/v1/boletas/**").authenticated()
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
