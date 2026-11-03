package cl.gaselvolcan.usuarios.dto;

// Lo que devuelve el login y la renovación: el token y los datos del usuario
public record LoginRespuesta(
        String token,
        String tipo,
        long expiraEnSegundos,
        UsuarioRespuesta usuario
) {
}
