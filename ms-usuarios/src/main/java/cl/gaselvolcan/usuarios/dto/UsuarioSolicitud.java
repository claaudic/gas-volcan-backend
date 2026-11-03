package cl.gaselvolcan.usuarios.dto;

import cl.gaselvolcan.usuarios.modelo.Rol;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Los datos que llegan en el JSON al crear o actualizar un usuario.
// Las reglas son las mismas de los formularios de la EP2.
public record UsuarioSolicitud(

        String run,

        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 50, message = "El nombre no puede superar los 50 caracteres.")
        String nombre,

        @NotBlank(message = "Los apellidos son obligatorios.")
        @Size(max = 100, message = "Los apellidos no pueden superar los 100 caracteres.")
        String apellidos,

        @NotBlank(message = "El correo es obligatorio.")
        @Email(message = "El correo no tiene un formato válido.")
        @Size(max = 100, message = "El correo no puede superar los 100 caracteres.")
        String correo,

        // Obligatoria al crear; al actualizar, si viene vacía se mantiene la anterior.
        @Size(min = 4, max = 10, message = "La contraseña debe tener entre 4 y 10 caracteres.")
        String contrasena,

        @NotNull(message = "El rol es obligatorio.")
        Rol rol,

        // Opcional: vacío, o entre 8 y 15 números, espacios o +
        @Pattern(regexp = "^$|^[+0-9\\s]{8,15}$", message = "El teléfono solo admite números, espacios y el signo +.")
        String telefono,

        @Size(max = 60, message = "La comuna no puede superar los 60 caracteres.")
        String comuna,

        @Size(max = 300, message = "La dirección no puede superar los 300 caracteres.")
        String direccion,

        // Opcional: si no viene, al crear queda activo y al actualizar no cambia.
        Boolean activo
) {
}
