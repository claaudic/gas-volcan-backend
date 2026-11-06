package cl.gaselvolcan.usuarios.service;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.gaselvolcan.usuarios.dto.PerfilSolicitud;
import cl.gaselvolcan.usuarios.dto.ReglasUsuario;
import cl.gaselvolcan.usuarios.dto.UsuarioRespuesta;
import cl.gaselvolcan.usuarios.dto.UsuarioSolicitud;
import cl.gaselvolcan.usuarios.exception.RecursoNoEncontradoException;
import cl.gaselvolcan.usuarios.exception.SolicitudInvalidaException;
import cl.gaselvolcan.usuarios.model.Rol;
import cl.gaselvolcan.usuarios.model.Usuario;
import cl.gaselvolcan.usuarios.repository.UsuarioRepository;

// La lógica de negocio de los usuarios. El controlador solo recibe y responde;
// las reglas están aquí.
// @Transactional: cada método se ejecuta como UNA sola operación en la base:
// o se guarda todo, o no se guarda nada.
@Service
@Transactional
public class UsuarioService {

    private static final String CORREO_REPETIDO = "Ya existe un usuario con ese correo.";

    private final UsuarioRepository repositorio;
    private final PasswordEncoder cifrador;

    public UsuarioService(UsuarioRepository repositorio, PasswordEncoder cifrador) {
        this.repositorio = repositorio;
        this.cifrador = cifrador;
    }

    @Transactional(readOnly = true)
    public List<UsuarioRespuesta> listar() {
        return repositorio.findAll().stream()
                .map(UsuarioRespuesta::desde)
                .toList();
    }

    @Transactional(readOnly = true)
    public UsuarioRespuesta buscarPorId(Long id) {
        return UsuarioRespuesta.desde(obtener(id));
    }

    // Para la despachadora: a quién puede asignar un pedido
    @Transactional(readOnly = true)
    public List<UsuarioRespuesta> listarRepartidores() {
        return repositorio.findByRolAndActivoTrue(Rol.REPARTIDOR).stream()
                .map(UsuarioRespuesta::desde)
                .toList();
    }

    public UsuarioRespuesta crear(UsuarioSolicitud datos) {
        String correo = limpiarCorreo(datos.correo());
        validarDominio(correo);

        if (repositorio.existsByCorreo(correo)) {
            throw new SolicitudInvalidaException(CORREO_REPETIDO);
        }
        if (vacio(datos.contrasena())) {
            throw new SolicitudInvalidaException("La contraseña es obligatoria.");
        }

        Usuario usuario = new Usuario();
        copiarDatos(datos, usuario, correo);
        // La contraseña no se recorta: se guarda tal como se validó
        usuario.setContrasena(cifrador.encode(datos.contrasena()));
        usuario.setActivo(datos.activo() == null || datos.activo());

        return UsuarioRespuesta.desde(guardar(usuario));
    }

    // correoActual: quién está haciendo el cambio (sale de su token)
    public UsuarioRespuesta actualizar(Long id, UsuarioSolicitud datos, String correoActual) {
        Usuario usuario = obtener(id);
        String correo = limpiarCorreo(datos.correo());
        validarDominio(correo);

        if (usuario.getCorreo().equals(correoActual)) {
            validarCambiosEnCuentaPropia(usuario, datos, correo);
        }

        // Si cambia el correo, el nuevo no puede ser de otra cuenta
        if (!correo.equals(usuario.getCorreo()) && repositorio.existsByCorreo(correo)) {
            throw new SolicitudInvalidaException(CORREO_REPETIDO);
        }

        copiarDatos(datos, usuario, correo);
        if (!vacio(datos.contrasena())) {
            usuario.setContrasena(cifrador.encode(datos.contrasena()));
        }
        if (datos.activo() != null) {
            usuario.setActivo(datos.activo());
        }

        return UsuarioRespuesta.desde(guardar(usuario));
    }

    public void eliminar(Long id, String correoActual) {
        Usuario usuario = obtener(id);
        if (usuario.getCorreo().equals(correoActual)) {
            throw new SolicitudInvalidaException("No puedes eliminar tu propia cuenta.");
        }
        repositorio.delete(usuario);
    }

    // =========================================
    // MI PERFIL (cualquier usuario con sesión, sobre su propia cuenta)
    // =========================================

    @Transactional(readOnly = true)
    public UsuarioRespuesta buscarPorCorreo(String correo) {
        return UsuarioRespuesta.desde(obtenerPorCorreo(correo));
    }

    public UsuarioRespuesta actualizarPerfil(String correo, PerfilSolicitud datos) {
        Usuario usuario = obtenerPorCorreo(correo);
        usuario.setNombre(datos.nombre().trim());
        usuario.setApellidos(datos.apellidos().trim());
        usuario.setTelefono(textoOpcional(datos.telefono()));
        usuario.setComuna(textoOpcional(datos.comuna()));
        usuario.setDireccion(textoOpcional(datos.direccion()));
        return UsuarioRespuesta.desde(guardar(usuario));
    }

    public void eliminarCuentaPropia(String correo) {
        repositorio.delete(obtenerPorCorreo(correo));
    }

    // =========================================
    // AYUDANTES
    // =========================================

    // saveAndFlush envía el INSERT/UPDATE a MySQL en este momento.
    // Si dos personas se registran a la vez con el mismo correo, la que llega segunda
    // pasa el existsByCorreo pero MySQL la rechaza por la columna única: aquí se
    // convierte ese rechazo en un 400 con el mensaje de siempre.
    private Usuario guardar(Usuario usuario) {
        try {
            return repositorio.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException error) {
            throw new SolicitudInvalidaException(CORREO_REPETIDO);
        }
    }

    private Usuario obtener(Long id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe un usuario con id " + id + "."));
    }

    private Usuario obtenerPorCorreo(String correo) {
        return repositorio.findByCorreo(correo)
                .orElseThrow(() -> new RecursoNoEncontradoException("Tu cuenta ya no existe."));
    }

    // Las mismas reglas de la EP2: el administrador no puede quitarse el acceso a sí mismo
    private void validarCambiosEnCuentaPropia(Usuario actual, UsuarioSolicitud datos, String correoNuevo) {
        if (!correoNuevo.equals(actual.getCorreo())) {
            throw new SolicitudInvalidaException("No puedes cambiar el correo de tu propia cuenta.");
        }
        if (datos.rol() != actual.getRol()) {
            throw new SolicitudInvalidaException("No puedes cambiar el rol de tu propia cuenta.");
        }
        if (Boolean.FALSE.equals(datos.activo())) {
            throw new SolicitudInvalidaException("No puedes desactivar tu propia cuenta.");
        }
    }

    private void copiarDatos(UsuarioSolicitud datos, Usuario usuario, String correo) {
        usuario.setRun(limpiarRun(datos.run()));
        usuario.setNombre(datos.nombre().trim());
        usuario.setApellidos(datos.apellidos().trim());
        usuario.setCorreo(correo);
        usuario.setRol(datos.rol());
        usuario.setTelefono(textoOpcional(datos.telefono()));
        usuario.setComuna(textoOpcional(datos.comuna()));
        usuario.setDireccion(textoOpcional(datos.direccion()));
    }

    private String limpiarCorreo(String correo) {
        return correo.trim().toLowerCase();
    }

    private void validarDominio(String correo) {
        boolean permitido = ReglasUsuario.DOMINIOS_PERMITIDOS.stream().anyMatch(correo::endsWith);
        if (!permitido) {
            throw new SolicitudInvalidaException(
                    "Usa un correo @gaselvolcan.cl si es del equipo, o @gmail.com si es cliente.");
        }
    }

    // El RUN es opcional. Si viene, se guarda sin puntos ni guion y debe ser válido.
    private String limpiarRun(String run) {
        if (vacio(run)) {
            return null;
        }
        String limpio = run.replace(".", "").replace("-", "").trim().toUpperCase();
        if (!runValido(limpio)) {
            throw new SolicitudInvalidaException("El RUN no es válido.");
        }
        return limpio;
    }

    // Igual que runValido de la EP2: cuerpo de 6 a 8 números + dígito verificador (módulo 11)
    static boolean runValido(String run) {
        if (!run.matches("^[0-9]{6,8}[0-9K]$")) {
            return false;
        }

        String cuerpo = run.substring(0, run.length() - 1);
        char verificador = run.charAt(run.length() - 1);

        int suma = 0;
        int factor = 2;
        for (int i = cuerpo.length() - 1; i >= 0; i--) {
            suma += Character.getNumericValue(cuerpo.charAt(i)) * factor;
            factor = factor == 7 ? 2 : factor + 1;
        }

        int resto = 11 - (suma % 11);
        char esperado = resto == 11 ? '0' : resto == 10 ? 'K' : Character.forDigit(resto, 10);

        return verificador == esperado;
    }

    private String textoOpcional(String texto) {
        return vacio(texto) ? null : texto.trim();
    }

    private boolean vacio(String texto) {
        return texto == null || texto.trim().isEmpty();
    }
}
