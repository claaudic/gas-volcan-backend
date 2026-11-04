package cl.gaselvolcan.usuarios.dto;

import static cl.gaselvolcan.usuarios.dto.ReglasUsuario.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Lo que cada usuario puede cambiar en "Mi perfil", igual que en la EP2:
// nombre, apellidos, teléfono, comuna y dirección. El correo y el rol no se tocan aquí.
public record PerfilSolicitud(

        @NotBlank(message = MSJ_NOMBRE_VACIO)
        @Size(max = MAX_NOMBRE, message = MSJ_NOMBRE_LARGO)
        String nombre,

        @NotBlank(message = MSJ_APELLIDOS_VACIO)
        @Size(max = MAX_APELLIDOS, message = MSJ_APELLIDOS_LARGO)
        String apellidos,

        @Pattern(regexp = TELEFONO, message = MSJ_TELEFONO)
        String telefono,

        @Size(max = MAX_COMUNA, message = MSJ_COMUNA_LARGO)
        String comuna,

        @Size(max = MAX_DIRECCION, message = MSJ_DIRECCION_LARGO)
        String direccion
) {
}
