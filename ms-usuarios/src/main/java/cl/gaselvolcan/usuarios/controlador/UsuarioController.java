package cl.gaselvolcan.usuarios.controlador;

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

import cl.gaselvolcan.usuarios.dto.UsuarioRespuesta;
import cl.gaselvolcan.usuarios.dto.UsuarioSolicitud;
import cl.gaselvolcan.usuarios.servicio.UsuarioService;
import jakarta.validation.Valid;

// Las rutas REST de usuarios. Recibe la petición, llama al servicio y responde JSON.
@RestController
@RequestMapping("/api/v1/usuarios")
public class UsuarioController {

    private final UsuarioService servicio;

    public UsuarioController(UsuarioService servicio) {
        this.servicio = servicio;
    }

    // GET /api/v1/usuarios → 200 con la lista
    @GetMapping
    public List<UsuarioRespuesta> listar() {
        return servicio.listar();
    }

    // GET /api/v1/usuarios/3 → 200, o 404 si no existe
    @GetMapping("/{id}")
    public UsuarioRespuesta buscar(@PathVariable Long id) {
        return servicio.buscarPorId(id);
    }

    // POST /api/v1/usuarios → 201 con el usuario creado, o 400 si los datos fallan
    @PostMapping
    public ResponseEntity<UsuarioRespuesta> crear(@Valid @RequestBody UsuarioSolicitud datos) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.crear(datos));
    }

    // PUT /api/v1/usuarios/3 → 200 con el usuario actualizado
    @PutMapping("/{id}")
    public UsuarioRespuesta actualizar(@PathVariable Long id, @Valid @RequestBody UsuarioSolicitud datos) {
        return servicio.actualizar(id, datos);
    }

    // DELETE /api/v1/usuarios/3 → 204 sin contenido
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        servicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
