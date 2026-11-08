package cl.gaselvolcan.ms_pedidos.config;

import java.security.Principal;

// Quién hizo la petición, leído del token JWT (lo firma ms-usuarios).
// En un controlador se obtiene con: @AuthenticationPrincipal UsuarioToken usuario
// id puede ser null en tokens antiguos (antes de que ms-usuarios agregara el id).
public record UsuarioToken(Long id, String correo, String rol) implements Principal {

    // Para Spring, el "nombre" del usuario es su correo (como en ms-usuarios)
    @Override
    public String getName() {
        return correo;
    }
}
