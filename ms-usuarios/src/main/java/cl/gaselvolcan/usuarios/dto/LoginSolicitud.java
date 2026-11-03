package cl.gaselvolcan.usuarios.dto;

import jakarta.validation.constraints.NotBlank;

// Lo que llega al iniciar sesión
public record LoginSolicitud(

        @NotBlank(message = "Ingresa tu correo electrónico.")
        String correo,

        @NotBlank(message = "Ingresa tu contraseña.")
        String contrasena
) {
}
