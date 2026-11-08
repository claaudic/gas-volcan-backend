package cl.gaselvolcan.usuarios.service;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import cl.gaselvolcan.usuarios.model.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

// Genera, verifica y lee los tokens JWT.
// El token lleva: sub = correo, id = id de la cuenta, rol = rol del usuario, exp = cuándo vence.
// El id lo usan ms-pedidos (boletas del cliente o del repartidor) sin preguntarle a ms-usuarios.
@Service
public class JwtService {

    private final SecretKey clave;
    private final long duracionMilisegundos;

    public JwtService(@Value("${jwt.secret}") String secreto,
                      @Value("${jwt.expiracion-minutos}") long minutos) {
        // HS256 necesita una clave de al menos 32 bytes. Si falta, se avisa con un mensaje claro.
        if (secreto == null || secreto.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "Falta JWT_SECRET (al menos 32 caracteres) en ms-usuarios/.env o en las variables de entorno.");
        }
        this.clave = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        this.duracionMilisegundos = minutos * 60 * 1000;
    }

    // GENERAR: crea un token firmado para el usuario
    public String generar(Usuario usuario) {
        Date ahora = new Date();
        Date vence = new Date(ahora.getTime() + duracionMilisegundos);

        return Jwts.builder()
                .subject(usuario.getCorreo())
                .claim("id", usuario.getId())
                .claim("rol", usuario.getRol().name())
                .issuedAt(ahora)
                .expiration(vence)
                .signWith(clave, Jwts.SIG.HS256)  // algoritmo fijo: los otros microservicios saben qué esperar
                .compact();
    }

    // VERIFICAR: revisa la firma y la fecha. Si algo falla, lanza JwtException.
    public Claims verificar(String token) {
        return Jwts.parser()
                .verifyWith(clave)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean esValido(String token) {
        try {
            verificar(token);
            return true;
        } catch (JwtException | IllegalArgumentException error) {
            return false;
        }
    }

    public long duracionSegundos() {
        return duracionMilisegundos / 1000;
    }
}
