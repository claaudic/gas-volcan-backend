package cl.gaselvolcan.usuarios.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

// Cadena de seguridad aparte, solo para /internal/v1/usuarios/**.
// @Order(1) hace que Spring la revise antes que SeguridadConfig (la de las rutas públicas).
@Configuration
public class SeguridadPedidosInternaConfig {

    @Bean
    @Order(1)
    SecurityFilterChain pedidosInternos(HttpSecurity http, @Value("${jwt.secret}") String secreto) throws Exception {
        return http
                .securityMatcher("/internal/v1/usuarios/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(reglas -> reglas
                        // Lo único permitido: leer los datos de contacto para un pedido
                        .requestMatchers(HttpMethod.GET, "/internal/v1/usuarios/*/pedido")
                        .hasAuthority(FiltroPedidosInterno.PERMISO)
                        .anyRequest().denyAll())
                .addFilterBefore(new FiltroPedidosInterno(secreto), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
