package cl.gaselvolcan.ms_catalogo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SeguridadInternaConfig {
    @Bean
    @Order(0)
    public SecurityFilterChain seguridadStockInterno(HttpSecurity http,
            @Value("${internal.jwt.secret:}") String secreto,
            @Value("${jwt.secret}") String secretoUsuarios) throws Exception {
        // El filtro no es un bean servlet: solo se ejecuta en esta cadena.
        var filtro = new FiltroJwtServicio(secreto, secretoUsuarios);
        return http.securityMatcher("/internal/v1/stock/**")
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(r -> r
                        .requestMatchers(HttpMethod.POST, "/internal/v1/stock/descuentos").hasAuthority("stock:descontar")
                        .requestMatchers(HttpMethod.GET, "/internal/v1/stock/descuentos/{id}").hasAuthority("stock:consultar")
                        .requestMatchers(HttpMethod.PUT, "/internal/v1/stock/descuentos/{id}/compensacion").hasAuthority("stock:compensar")
                        .anyRequest().denyAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((q, s, x) -> SeguridadConfig.responderJson(s, 401, "Falta autenticacion interna valida."))
                        .accessDeniedHandler((q, s, x) -> SeguridadConfig.responderJson(s, 403, "Permiso interno insuficiente.")))
                .addFilterBefore(filtro, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
