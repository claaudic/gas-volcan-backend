package cl.gaselvolcan.usuarios.servicio;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.gaselvolcan.usuarios.dto.LoginRespuesta;
import cl.gaselvolcan.usuarios.dto.LoginSolicitud;
import cl.gaselvolcan.usuarios.dto.RegistroSolicitud;
import cl.gaselvolcan.usuarios.dto.UsuarioRespuesta;
import cl.gaselvolcan.usuarios.excepcion.CredencialesInvalidasException;
import cl.gaselvolcan.usuarios.modelo.Usuario;
import cl.gaselvolcan.usuarios.repositorio.UsuarioRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

// Login, renovación del token y registro público
@Service
public class AuthService {

    private static final String CREDENCIALES_MALAS = "Correo o contraseña incorrectos.";

    private final UsuarioRepository repositorio;
    private final PasswordEncoder cifrador;
    private final JwtService jwt;
    private final UsuarioService usuarios;

    // Un hash de relleno: si el correo no existe, igual se compara contra él.
    // Así el login tarda lo mismo exista o no el correo, y no se puede adivinar por el tiempo.
    private final String hashDeRelleno;

    public AuthService(UsuarioRepository repositorio, PasswordEncoder cifrador,
                       JwtService jwt, UsuarioService usuarios) {
        this.repositorio = repositorio;
        this.cifrador = cifrador;
        this.jwt = jwt;
        this.usuarios = usuarios;
        this.hashDeRelleno = cifrador.encode("relleno-para-igualar-tiempos");
    }

    @Transactional(readOnly = true)
    public LoginRespuesta login(LoginSolicitud datos) {
        String correo = datos.correo().trim().toLowerCase();
        Usuario usuario = repositorio.findByCorreo(correo).orElse(null);

        // BCrypt cifra lo escrito y lo compara con lo guardado
        String hashGuardado = usuario != null ? usuario.getContrasena() : hashDeRelleno;
        boolean coincide = cifrador.matches(datos.contrasena(), hashGuardado);

        // Mismo mensaje si no existe el correo o si la contraseña falla:
        // así no se revela qué correos están registrados.
        if (usuario == null || !coincide) {
            throw new CredencialesInvalidasException(CREDENCIALES_MALAS);
        }
        if (!usuario.isActivo()) {
            throw new CredencialesInvalidasException("La cuenta está desactivada. Contacta al administrador.");
        }

        return respuestaCon(usuario);
    }

    // Recibe un token que todavía no vence y entrega uno nuevo con 30 minutos más
    @Transactional(readOnly = true)
    public LoginRespuesta renovar(String encabezado) {
        if (encabezado == null || !encabezado.startsWith("Bearer ")) {
            throw new CredencialesInvalidasException("Falta el token.");
        }

        Claims datos;
        try {
            datos = jwt.verificar(encabezado.substring(7));
        } catch (JwtException | IllegalArgumentException error) {
            throw new CredencialesInvalidasException("El token no es válido o ya venció. Inicia sesión de nuevo.");
        }

        // Se vuelve a buscar en la base: si lo desactivaron o cambió de rol, se nota aquí
        Usuario usuario = repositorio.findByCorreo(datos.getSubject())
                .filter(Usuario::isActivo)
                .orElseThrow(() -> new CredencialesInvalidasException("La cuenta ya no está disponible."));

        return respuestaCon(usuario);
    }

    // Registro público: el rol siempre es CLIENTE, venga lo que venga
    public UsuarioRespuesta registrar(RegistroSolicitud datos) {
        return usuarios.crear(datos.comoCliente());
    }

    private LoginRespuesta respuestaCon(Usuario usuario) {
        return new LoginRespuesta(
                jwt.generar(usuario),
                "Bearer",
                jwt.duracionSegundos(),
                UsuarioRespuesta.desde(usuario)
        );
    }
}
