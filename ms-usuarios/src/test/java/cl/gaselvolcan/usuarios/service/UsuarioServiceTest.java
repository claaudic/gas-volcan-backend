package cl.gaselvolcan.usuarios.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import cl.gaselvolcan.usuarios.dto.PerfilSolicitud;
import cl.gaselvolcan.usuarios.dto.UsuarioRespuesta;
import cl.gaselvolcan.usuarios.dto.UsuarioSolicitud;
import cl.gaselvolcan.usuarios.exception.RecursoNoEncontradoException;
import cl.gaselvolcan.usuarios.exception.SolicitudInvalidaException;
import cl.gaselvolcan.usuarios.model.Rol;
import cl.gaselvolcan.usuarios.model.Usuario;
import cl.gaselvolcan.usuarios.repository.UsuarioRepository;

// Pruebas de las reglas del CRUD de usuarios, con un repositorio falso (mock), sin MySQL.
class UsuarioServiceTest {

    private final PasswordEncoder cifrador = new BCryptPasswordEncoder();
    private UsuarioRepository repositorio;
    private UsuarioService servicio;

    @BeforeEach
    void preparar() {
        repositorio = mock(UsuarioRepository.class);
        servicio = new UsuarioService(repositorio, cifrador);
        // saveAndFlush devuelve el mismo usuario que recibe
        when(repositorio.saveAndFlush(any(Usuario.class))).thenAnswer(llamada -> llamada.getArgument(0));
    }

    private static final String ADMIN = "admin@gaselvolcan.cl";

    private UsuarioSolicitud solicitud(String run, String correo, String contrasena, Boolean activo) {
        return new UsuarioSolicitud(run, "Ana", "Soto", correo, contrasena, Rol.CLIENTE, null, null, null, null, null, activo);
    }

    // --- RUN (mismo algoritmo que runValido de la EP2) ---

    @Test
    void runConDigitoVerificadorCorrecto() {
        assertTrue(UsuarioService.runValido("111111111"));
        assertTrue(UsuarioService.runValido("123456785"));
        assertTrue(UsuarioService.runValido("10000013K"));
    }

    @Test
    void guardaLaFechaDeNacimientoYLaRegion() {
        UsuarioSolicitud datos = new UsuarioSolicitud(null, "Ana", "Soto", "ana@gmail.com", "Ana1234", Rol.CLIENTE,
                null, "Chillán", null, LocalDate.of(2000, 5, 31), " Ñuble ", null);

        UsuarioRespuesta creado = servicio.crear(datos);

        assertEquals(LocalDate.of(2000, 5, 31), creado.nacimiento());
        assertEquals("Ñuble", creado.region());
    }

    @Test
    void runConDigitoVerificadorIncorrectoOFormatoMalo() {
        assertFalse(UsuarioService.runValido("123456789"));
        assertFalse(UsuarioService.runValido("12345"));
        assertFalse(UsuarioService.runValido("ABCDEFGHI"));
    }

    // --- Crear ---

    @Test
    void creaUnUsuarioActivoConContrasenaCifrada() {
        UsuarioRespuesta creado = servicio.crear(solicitud("11.111.111-1", " Ana@Gmail.com ", "Ana1234", null));

        assertEquals("ana@gmail.com", creado.correo());
        assertEquals("111111111", creado.run());
        assertTrue(creado.activo());
        verify(repositorio).saveAndFlush(any(Usuario.class));
    }

    @Test
    void laContrasenaSeGuardaCifradaYSinRecortar() {
        Usuario[] guardado = new Usuario[1];
        when(repositorio.saveAndFlush(any(Usuario.class))).thenAnswer(llamada -> guardado[0] = llamada.getArgument(0));

        servicio.crear(solicitud(null, "ana@gmail.com", " Ana1 ", null));

        assertNotEquals(" Ana1 ", guardado[0].getContrasena());
        assertTrue(cifrador.matches(" Ana1 ", guardado[0].getContrasena()));
        assertFalse(cifrador.matches("Ana1", guardado[0].getContrasena()));
    }

    @Test
    void rechazaDominioNoPermitido() {
        assertThrows(SolicitudInvalidaException.class,
                () -> servicio.crear(solicitud(null, "ana@hotmail.com", "Ana1234", null)));
    }

    @Test
    void rechazaCorreoRepetido() {
        when(repositorio.existsByCorreo("ana@gmail.com")).thenReturn(true);

        assertThrows(SolicitudInvalidaException.class,
                () -> servicio.crear(solicitud(null, "ana@gmail.com", "Ana1234", null)));
        verify(repositorio, never()).saveAndFlush(any());
    }

    @Test
    void correoRepetidoQueLlegaAlMismoTiempoTambienDa400() {
        // existsByCorreo dijo que no existía, pero MySQL lo rechaza por la columna única
        when(repositorio.saveAndFlush(any(Usuario.class))).thenThrow(new DataIntegrityViolationException("correo"));

        SolicitudInvalidaException error = assertThrows(SolicitudInvalidaException.class,
                () -> servicio.crear(solicitud(null, "ana@gmail.com", "Ana1234", null)));
        assertEquals("Ya existe un usuario con ese correo.", error.getMessage());
    }

    @Test
    void rechazaCrearSinContrasena() {
        assertThrows(SolicitudInvalidaException.class,
                () -> servicio.crear(solicitud(null, "ana@gmail.com", null, null)));
    }

    @Test
    void rechazaRunInvalido() {
        assertThrows(SolicitudInvalidaException.class,
                () -> servicio.crear(solicitud("12.345.678-9", "ana@gmail.com", "Ana1234", null)));
    }

    // --- Actualizar ---

    @Test
    void alActualizarSinContrasenaSeMantieneLaAnterior() {
        Usuario ana = new Usuario("Ana", "Soto", "ana@gmail.com", cifrador.encode("Ana1234"), Rol.CLIENTE);
        String hashAnterior = ana.getContrasena();
        when(repositorio.findById(6L)).thenReturn(Optional.of(ana));

        servicio.actualizar(6L, solicitud(null, "ana@gmail.com", null, false), ADMIN);

        assertEquals(hashAnterior, ana.getContrasena());
        assertFalse(ana.isActivo());
    }

    @Test
    void noPuedeCambiarseAUnCorreoDeOtraCuenta() {
        Usuario ana = new Usuario("Ana", "Soto", "ana@gmail.com", "x", Rol.CLIENTE);
        when(repositorio.findById(6L)).thenReturn(Optional.of(ana));
        when(repositorio.existsByCorreo("camila@gmail.com")).thenReturn(true);

        assertThrows(SolicitudInvalidaException.class,
                () -> servicio.actualizar(6L, solicitud(null, "camila@gmail.com", null, null), ADMIN));
    }

    // --- No existe → 404 ---

    @Test
    void buscarActualizarOEliminarUnIdQueNoExisteDa404() {
        when(repositorio.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> servicio.buscarPorId(99L));
        assertThrows(RecursoNoEncontradoException.class,
                () -> servicio.actualizar(99L, solicitud(null, "ana@gmail.com", null, null), ADMIN));
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.eliminar(99L, ADMIN));
    }

    // --- Cuenta propia del administrador (mismas reglas de la EP2) ---

    private Usuario sofia() {
        return new Usuario("Sofía", "Pérez", ADMIN, "x", Rol.ADMINISTRADOR);
    }

    private UsuarioSolicitud cambioDeSofia(String correo, Rol rol, Boolean activo) {
        return new UsuarioSolicitud(null, "Sofía", "Pérez", correo, null, rol, null, null, null, null, null, activo);
    }

    private void esperarError(String mensaje, Runnable accion) {
        SolicitudInvalidaException error = assertThrows(SolicitudInvalidaException.class, accion::run);
        assertEquals(mensaje, error.getMessage());
    }

    @Test
    void elAdministradorNoPuedeEliminarSuPropiaCuenta() {
        when(repositorio.findById(1L)).thenReturn(Optional.of(sofia()));

        esperarError("No puedes eliminar tu propia cuenta.", () -> servicio.eliminar(1L, ADMIN));
        verify(repositorio, never()).delete(any(Usuario.class));
    }

    @Test
    void elAdministradorNoPuedeCambiarSuRolCorreoNiDesactivarse() {
        when(repositorio.findById(1L)).thenReturn(Optional.of(sofia()));

        esperarError("No puedes cambiar el rol de tu propia cuenta.",
                () -> servicio.actualizar(1L, cambioDeSofia(ADMIN, Rol.CLIENTE, null), ADMIN));
        esperarError("No puedes cambiar el correo de tu propia cuenta.",
                () -> servicio.actualizar(1L, cambioDeSofia("sofia@gaselvolcan.cl", Rol.ADMINISTRADOR, null), ADMIN));
        esperarError("No puedes desactivar tu propia cuenta.",
                () -> servicio.actualizar(1L, cambioDeSofia(ADMIN, Rol.ADMINISTRADOR, false), ADMIN));
    }

    @Test
    void elAdministradorSiPuedeEditarSusOtrosDatos() {
        Usuario sofia = sofia();
        when(repositorio.findById(1L)).thenReturn(Optional.of(sofia));

        servicio.actualizar(1L, cambioDeSofia(ADMIN, Rol.ADMINISTRADOR, true), ADMIN);

        assertEquals(Rol.ADMINISTRADOR, sofia.getRol());
    }

    // --- Mi perfil ---

    @Test
    void elPerfilSoloCambiaNombreTelefonoComunaYDireccion() {
        Usuario camila = new Usuario("Camila", "Rojas", "camila@gmail.com", "x", Rol.CLIENTE);
        when(repositorio.findByCorreo("camila@gmail.com")).thenReturn(Optional.of(camila));

        servicio.actualizarPerfil("camila@gmail.com",
                new PerfilSolicitud(" Cami ", "Rojas", "+56 9 1234 5678", "Chillán", ""));

        assertEquals("Cami", camila.getNombre());
        assertEquals("Chillán", camila.getComuna());
        assertEquals(null, camila.getDireccion());
        assertEquals("camila@gmail.com", camila.getCorreo());
        assertEquals(Rol.CLIENTE, camila.getRol());
    }
}
