package cl.gaselvolcan.usuarios.controller;

import io.swagger.v3.oas.annotations.Operation;
import java.security.Principal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.gaselvolcan.usuarios.dto.PerfilSolicitud;
import cl.gaselvolcan.usuarios.dto.UsuarioRespuesta;
import cl.gaselvolcan.usuarios.dto.UsuarioSolicitud;
import cl.gaselvolcan.usuarios.service.UsuarioService;
import jakarta.validation.Valid;

// Las rutas REST de usuarios. Recibe la petición, llama al servicio y responde JSON.
// Quién puede usar cada ruta se define en SeguridadConfig.
// "Principal" es el usuario que hace la petición: su nombre es el correo que viene en el token.
@RestController
@RequestMapping("/api/v1/usuarios")
public class UsuarioController {

    private final UsuarioService servicio;

    public UsuarioController(UsuarioService servicio) {
        this.servicio = servicio;
    }

    // GET /api/v1/usuarios → 200 con la lista
    @Operation(summary = "Listar usuarios")
    @GetMapping
    public List<UsuarioRespuesta> listar() {
        return servicio.listar();
    }

    // GET /api/v1/usuarios/repartidores → 200 con los repartidores activos (administrador y despachadora)
    @Operation(summary = "Listar repartidores activos")
    @GetMapping("/repartidores")
    public List<UsuarioRespuesta> listarRepartidores() {
        return servicio.listarRepartidores();
    }

    // GET /api/v1/usuarios/yo → 200 con los datos de quien tiene la sesión
    @Operation(summary = "Consultar mi perfil")
    @GetMapping("/yo")
    public UsuarioRespuesta miPerfil(Principal usuario) {
        return servicio.buscarPorCorreo(usuario.getName());
    }

    // PUT /api/v1/usuarios/yo → 200 con el perfil actualizado
    @Operation(summary = "Actualizar mi perfil")
    @PutMapping("/yo")
    public UsuarioRespuesta actualizarMiPerfil(Principal usuario, @Valid @RequestBody PerfilSolicitud datos) {
        return servicio.actualizarPerfil(usuario.getName(), datos);
    }

    // DELETE /api/v1/usuarios/yo → 204 (solo clientes, como en la EP2)
    @Operation(summary = "Eliminar mi cuenta")
    @DeleteMapping("/yo")
    public ResponseEntity<Void> eliminarMiCuenta(Principal usuario) {
        servicio.eliminarCuentaPropia(usuario.getName());
        return ResponseEntity.noContent().build();
    }

    // GET /api/v1/usuarios/3 → 200, o 404 si no existe
    @Operation(summary = "Consultar usuario por ID")
    @GetMapping("/{id}")
    public UsuarioRespuesta buscar(@PathVariable Long id) {
        return servicio.buscarPorId(id);
    }

    // POST /api/v1/usuarios → 201 con el usuario creado, o 400 si los datos fallan
    @Operation(summary = "Crear usuario")
    @PostMapping
    public ResponseEntity<UsuarioRespuesta> crear(@Valid @RequestBody UsuarioSolicitud datos) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.crear(datos));
    }

    // PUT /api/v1/usuarios/3 → 200 con el usuario actualizado
    @Operation(summary = "Actualizar usuario")
    @PutMapping("/{id}")
    public UsuarioRespuesta actualizar(@PathVariable Long id, @Valid @RequestBody UsuarioSolicitud datos,
                                       Principal administrador) {
        return servicio.actualizar(id, datos, administrador.getName());
    }

    // DELETE /api/v1/usuarios/3 → 204 sin contenido
    @Operation(summary = "Eliminar usuario")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, Principal administrador) {
        servicio.eliminar(id, administrador.getName());
        return ResponseEntity.noContent().build();
    }
}
