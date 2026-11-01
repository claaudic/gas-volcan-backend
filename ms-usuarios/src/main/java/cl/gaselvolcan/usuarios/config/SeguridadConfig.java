package cl.gaselvolcan.usuarios.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

// TEMPORAL: deja todas las rutas abiertas para probar el CRUD.
// En la tarea 07 se reemplaza por la seguridad con JWT y roles.
@Configuration
public class SeguridadConfig {

    @Bean
    public SecurityFilterChain filtroDeSeguridad(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(rutas -> rutas.anyRequest().permitAll());
        return http.build();
    }

    // Cifra las contraseñas con BCrypt antes de guardarlas
    @Bean
    public PasswordEncoder cifradorDeContrasenas() {
        return new BCryptPasswordEncoder();
    }
}
