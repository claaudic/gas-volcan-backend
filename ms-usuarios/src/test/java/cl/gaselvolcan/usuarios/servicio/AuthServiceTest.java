package cl.gaselvolcan.usuarios.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import cl.gaselvolcan.usuarios.dto.LoginRespuesta;
import cl.gaselvolcan.usuarios.dto.LoginSolicitud;
import cl.gaselvolcan.usuarios.dto.RegistroSolicitud;
import cl.gaselvolcan.usuarios.dto.UsuarioSolicitud;
import cl.gaselvolcan.usuarios.excepcion.CredencialesInvalidasException;
import cl.gaselvolcan.usuarios.modelo.Rol;
import cl.gaselvolcan.usuarios.modelo.Usuario;
import cl.gaselvolcan.usuarios.repositorio.UsuarioRepository;

// Pruebas del login, la renovación y el registro.
// El repositorio es un "mock": un repositorio falso que responde lo que le indicamos, sin MySQL.
class AuthServiceTest {

    private final PasswordEncoder cifrador = new BCryptPasswordEncoder();
    private final JwtService jwt = new JwtService("clave-de-prueba-con-mas-de-32-caracteres", 30);

    private UsuarioRepository repositorio;
    private UsuarioService usuarios;
    private AuthService auth;
    private Usuario camila;

    @BeforeEach
    void preparar() {
        repositorio = mock(UsuarioRepository.class);
        usuarios = mock(UsuarioService.class);
        auth = new AuthService(repositorio, cifrador, jwt, usuarios);

        camila = new Usuario("Camila", "Rojas", "camila@gmail.com", cifrador.encode("Clien1234"), Rol.CLIENTE);
        when(repositorio.findByCorreo("camila@gmail.com")).thenReturn(Optional.of(camila));
    }

    @Test
    void loginCorrectoEntregaToken() {
        LoginRespuesta respuesta = auth.login(new LoginSolicitud("camila@gmail.com", "Clien1234"));

        assertNotNull(respuesta.token());
        assertEquals("Bearer", respuesta.tipo());
        assertEquals("camila@gmail.com", jwt.verificar(respuesta.token()).getSubject());
    }

    @Test
    void loginIgnoraMayusculasYEspaciosDelCorreo() {
        LoginRespuesta respuesta = auth.login(new LoginSolicitud("  Camila@Gmail.com ", "Clien1234"));

        assertNotNull(respuesta.token());
    }

    @Test
    void contrasenaIncorrectaDa401() {
        assertThrows(CredencialesInvalidasException.class,
                () -> auth.login(new LoginSolicitud("camila@gmail.com", "otra")));
    }

    @Test
    void correoQueNoExisteDa401() {
        assertThrows(CredencialesInvalidasException.class,
                () -> auth.login(new LoginSolicitud("nadie@gmail.com", "Clien1234")));
    }

    @Test
    void cuentaDesactivadaDa401() {
        camila.setActivo(false);

        assertThrows(CredencialesInvalidasException.class,
                () -> auth.login(new LoginSolicitud("camila@gmail.com", "Clien1234")));
    }

    @Test
    void renovarEntregaUnTokenNuevo() {
        String token = auth.login(new LoginSolicitud("camila@gmail.com", "Clien1234")).token();

        LoginRespuesta nuevo = auth.renovar("Bearer " + token);

        assertEquals("camila@gmail.com", jwt.verificar(nuevo.token()).getSubject());
    }

    @Test
    void renovarSinTokenOConTokenMaloDa401() {
        assertThrows(CredencialesInvalidasException.class, () -> auth.renovar(null));
        assertThrows(CredencialesInvalidasException.class, () -> auth.renovar("Bearer abc.def.ghi"));
    }

    @Test
    void renovarConCuentaDesactivadaDa401() {
        String token = jwt.generar(camila);
        camila.setActivo(false);

        assertThrows(CredencialesInvalidasException.class, () -> auth.renovar("Bearer " + token));
    }

    @Test
    void registroSiempreCreaUnCliente() {
        auth.registrar(new RegistroSolicitud("Ana", "Soto", "ana@gmail.com", "Ana1234", null, null, null));

        ArgumentCaptor<UsuarioSolicitud> enviado = ArgumentCaptor.forClass(UsuarioSolicitud.class);
        verify(usuarios).crear(enviado.capture());
        assertEquals(Rol.CLIENTE, enviado.getValue().rol());
    }
}
