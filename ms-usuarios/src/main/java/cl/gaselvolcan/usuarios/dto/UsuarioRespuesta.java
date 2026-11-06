package cl.gaselvolcan.usuarios.dto;

import cl.gaselvolcan.usuarios.model.Rol;
import cl.gaselvolcan.usuarios.model.Usuario;

// Lo que la API devuelve. No incluye la contraseña.
public record UsuarioRespuesta(
        Long id,
        String run,
        String nombre,
        String apellidos,
        String correo,
        Rol rol,
        String telefono,
        String comuna,
        String direccion,
        boolean activo
) {

    public static UsuarioRespuesta desde(Usuario usuario) {
        return new UsuarioRespuesta(
                usuario.getId(),
                usuario.getRun(),
                usuario.getNombre(),
                usuario.getApellidos(),
                usuario.getCorreo(),
                usuario.getRol(),
                usuario.getTelefono(),
                usuario.getComuna(),
                usuario.getDireccion(),
                usuario.isActivo()
        );
    }
}
