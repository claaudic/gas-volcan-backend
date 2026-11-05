package cl.gaselvolcan.ms_catalogo.controlador;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import cl.gaselvolcan.ms_catalogo.exception.ErrorRespuesta;
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
import jakarta.validation.Valid;
import cl.gaselvolcan.ms_catalogo.dto.CategoriaRespuesta;
import cl.gaselvolcan.ms_catalogo.dto.CategoriaSolicitud;
import cl.gaselvolcan.ms_catalogo.servicio.CategoriaService;

// Recibe HTTP y delega las reglas de negocio al servicio.
@Tag(name = "Categorias", description = "CRUD del catalogo")
@RestController
@RequestMapping("/api/v1/categorias")
public class CategoriaController {
    private final CategoriaService servicio;

    public CategoriaController(CategoriaService servicio) {
        this.servicio = servicio;
    }

    @Operation(summary = "Listar categorias")
    @ApiResponse(responseCode = "200", description = "Operacion exitosa")
    @ApiResponse(responseCode = "500", description = "Error interno sin detalles sensibles", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorRespuesta.class)))
    @GetMapping
    public List<CategoriaRespuesta> listar() {
        return servicio.listar();
    }

    @Operation(summary = "Consultar por ID")
    @ApiResponse(responseCode = "200", description = "Operacion exitosa")
    @ApiResponse(responseCode = "404", description = "Producto o categoria inexistente", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorRespuesta.class)))
    @ApiResponse(responseCode = "400", description = "Datos invalidos, duplicados o eliminacion de categoria con productos", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorRespuesta.class)))
    @ApiResponse(responseCode = "500", description = "Error interno sin detalles sensibles", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorRespuesta.class)))
    @GetMapping("/{id}")
    public CategoriaRespuesta buscar(@Parameter(description = "ID positivo del recurso", example = "2") @PathVariable Long id) {
        return servicio.buscarPorId(id);
    }

    @Operation(summary = "Crear categoria")
    @ApiResponse(responseCode = "201", description = "Operacion exitosa")
    @ApiResponse(responseCode = "400", description = "Datos invalidos, duplicados o eliminacion de categoria con productos", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorRespuesta.class)))
    @ApiResponse(responseCode = "500", description = "Error interno sin detalles sensibles", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorRespuesta.class)))
    @PostMapping
    public ResponseEntity<CategoriaRespuesta> crear(@Valid @RequestBody CategoriaSolicitud datos) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.crear(datos));
    }

    @Operation(summary = "Actualizar categoria")
    @ApiResponse(responseCode = "200", description = "Operacion exitosa")
    @ApiResponse(responseCode = "404", description = "Producto o categoria inexistente", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorRespuesta.class)))
    @ApiResponse(responseCode = "400", description = "Datos invalidos, duplicados o eliminacion de categoria con productos", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorRespuesta.class)))
    @ApiResponse(responseCode = "500", description = "Error interno sin detalles sensibles", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorRespuesta.class)))
    @PutMapping("/{id}")
    public CategoriaRespuesta actualizar(@Parameter(description = "ID positivo del recurso", example = "2") @PathVariable Long id, @Valid @RequestBody CategoriaSolicitud datos) {
        return servicio.actualizar(id, datos);
    }

    @Operation(summary = "Eliminar categoria")
    @ApiResponse(responseCode = "204", description = "Operacion exitosa", content = @Content)
    @ApiResponse(responseCode = "404", description = "Producto o categoria inexistente", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorRespuesta.class)))
    @ApiResponse(responseCode = "400", description = "Datos invalidos, duplicados o eliminacion de categoria con productos", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorRespuesta.class)))
    @ApiResponse(responseCode = "500", description = "Error interno sin detalles sensibles", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorRespuesta.class)))
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@Parameter(description = "ID positivo del recurso", example = "2") @PathVariable Long id) {
        servicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
