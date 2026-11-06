package cl.gaselvolcan.usuarios.dto;

import static cl.gaselvolcan.usuarios.dto.ReglasUsuario.*;

import java.time.LocalDate;

import cl.gaselvolcan.usuarios.model.Rol;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Los datos que llegan en el JSON al crear o actualizar un usuario (administrador).
// Los límites y mensajes vienen de ReglasUsuario.
public record UsuarioSolicitud(

        String run,

        @NotBlank(message = MSJ_NOMBRE_VACIO)
        @Size(max = MAX_NOMBRE, message = MSJ_NOMBRE_LARGO)
        String nombre,

        @NotBlank(message = MSJ_APELLIDOS_VACIO)
        @Size(max = MAX_APELLIDOS, message = MSJ_APELLIDOS_LARGO)
        String apellidos,

        @NotBlank(message = MSJ_CORREO_VACIO)
        @Email(message = MSJ_CORREO_FORMATO)
        @Size(max = MAX_CORREO, message = MSJ_CORREO_LARGO)
        String correo,

        // Obligatoria al crear; al actualizar, si viene vacía se mantiene la anterior.
        @Size(min = MIN_CONTRASENA, max = MAX_CONTRASENA, message = MSJ_CONTRASENA_LARGO)
        String contrasena,

        @NotNull(message = "El rol es obligatorio.")
        Rol rol,

        @Pattern(regexp = TELEFONO, message = MSJ_TELEFONO)
        String telefono,

        @Size(max = MAX_COMUNA, message = MSJ_COMUNA_LARGO)
        String comuna,

        @Size(max = MAX_DIRECCION, message = MSJ_DIRECCION_LARGO)
        String direccion,

        // Opcional. En el JSON va como "2000-05-31". Hoy o antes, nunca en el futuro
        @PastOrPresent(message = MSJ_NACIMIENTO_FUTURO)
        LocalDate nacimiento,

        @Size(max = MAX_REGION, message = MSJ_REGION_LARGO)
        String region,

        // Opcional: si no viene, al crear queda activo y al actualizar no cambia.
        Boolean activo
) {
}
