package cl.gaselvolcan.usuarios.servicio;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import cl.gaselvolcan.usuarios.modelo.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

// Genera, verifica y lee los tokens JWT.
// El token lleva: sub = correo, rol = rol del usuario, exp = cuándo vence.
@Service
public class JwtService {

    private final SecretKey clave;
    private final long duracionMilisegundos;

    public JwtService(@Value("${jwt.secret}") String secreto,
                      @Value("${jwt.expiracion-minutos}") long minutos) {
        // HS256 necesita una clave de al menos 32 bytes (si es más corta, falla al arrancar)
        this.clave = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        this.duracionMilisegundos = minutos * 60 * 1000;
    }

    // GENERAR: crea un token firmado para el usuario
    public String generar(Usuario usuario) {
        Date ahora = new Date();
        Date vence = new Date(ahora.getTime() + duracionMilisegundos);

        return Jwts.builder()
                .subject(usuario.getCorreo())
                .claim("rol", usuario.getRol().name())
                .issuedAt(ahora)
                .expiration(vence)
                .signWith(clave, Jwts.SIG.HS256)  // algoritmo fijo: ms-tienda sabe qué esperar
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
