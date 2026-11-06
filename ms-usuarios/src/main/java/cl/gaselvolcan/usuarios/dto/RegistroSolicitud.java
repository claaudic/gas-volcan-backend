package cl.gaselvolcan.usuarios.dto;

import static cl.gaselvolcan.usuarios.dto.ReglasUsuario.*;

import cl.gaselvolcan.usuarios.model.Rol;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Registro público de clientes. No tiene campo "rol": siempre se crea como CLIENTE.
// Los límites y mensajes vienen de ReglasUsuario, igual que en UsuarioSolicitud.
public record RegistroSolicitud(

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

        @NotBlank(message = MSJ_CONTRASENA_VACIA)
        @Size(min = MIN_CONTRASENA, max = MAX_CONTRASENA, message = MSJ_CONTRASENA_LARGO)
        String contrasena,

        @Pattern(regexp = TELEFONO, message = MSJ_TELEFONO)
        String telefono,

        @Size(max = MAX_COMUNA, message = MSJ_COMUNA_LARGO)
        String comuna,

        @Size(max = MAX_DIRECCION, message = MSJ_DIRECCION_LARGO)
        String direccion
) {

    // Convierte el registro en una solicitud de usuario, siempre como CLIENTE y activo
    public UsuarioSolicitud comoCliente() {
        return new UsuarioSolicitud(null, nombre, apellidos, correo, contrasena,
                Rol.CLIENTE, telefono, comuna, direccion, null, null, true);
    }
}
