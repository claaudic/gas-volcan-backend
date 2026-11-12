package cl.gaselvolcan.ms_catalogo.controller;

import cl.gaselvolcan.ms_catalogo.dto.DisponibilidadRespuesta;
import org.springframework.web.bind.annotation.RequestParam;
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
import cl.gaselvolcan.ms_catalogo.dto.ProductoRespuesta;
import cl.gaselvolcan.ms_catalogo.dto.ProductoSolicitud;
import cl.gaselvolcan.ms_catalogo.dto.ProductoActualizacionSolicitud;
import cl.gaselvolcan.ms_catalogo.dto.EstadoProductoSolicitud;
import cl.gaselvolcan.ms_catalogo.service.ProductoService;

// Recibe HTTP y delega las reglas de negocio al servicio.
@Tag(name = "Productos", description = "CRUD del catalogo")
@RestController
@RequestMapping("/api/v1/productos")
public class ProductoController {
    private final ProductoService servicio;

    public ProductoController(ProductoService servicio) {
        this.servicio = servicio;
    }

    @Operation(summary = "Listar productos")
    @ApiResponse(responseCode = "200", description = "Operacion exitosa")
    @ApiResponse(responseCode = "500", description = "Error interno sin detalles sensibles", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorRespuesta.class)))
    @GetMapping
    public List<ProductoRespuesta> listar() {
        return servicio.listar();
    }

    @Operation(summary = "Consultar por ID")
    @ApiResponse(responseCode = "200", description = "Operacion exitosa")
    @ApiResponse(responseCode = "404", description = "Producto o categoria inexistente", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorRespuesta.class)))
    @ApiResponse(responseCode = "400", description = "Datos invalidos, duplicados o eliminacion de categoria con productos", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorRespuesta.class)))
    @ApiResponse(responseCode = "500", description = "Error interno sin detalles sensibles", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorRespuesta.class)))
    @GetMapping("/{id}")
    public ProductoRespuesta buscar(@Parameter(description = "ID positivo del recurso", example = "2") @PathVariable Long id) {
        return servicio.buscarPorId(id);
    }

    @Operation(summary = "Consultar disponibilidad de stock", description = "Para ms-pedidos. No reserva ni descuenta stock: la disponibilidad puede cambiar despues de consultar. Un producto inactivo o sin suficientes unidades devuelve 200 con disponible=false.")
    @ApiResponse(responseCode = "200", description = "Disponibilidad actual")
    @ApiResponse(responseCode = "400", description = "ID o cantidad invalidos", content = @Content(schema = @Schema(implementation = ErrorRespuesta.class)))
    @ApiResponse(responseCode = "404", description = "Producto inexistente", content = @Content(schema = @Schema(implementation = ErrorRespuesta.class)))
    @GetMapping("/{id}/disponibilidad")
    public DisponibilidadRespuesta disponibilidad(
            @Parameter(description = "ID positivo del producto", example = "2") @PathVariable Long id,
            @Parameter(description = "Cantidad entera positiva; por defecto 1", example = "3", schema = @Schema(minimum = "1"))
            @RequestParam(defaultValue = "1") Integer cantidad) {
        return servicio.consultarDisponibilidad(id, cantidad);
    }

    @Operation(summary = "Crear producto")
    @ApiResponse(responseCode = "201", description = "Operacion exitosa")
    @ApiResponse(responseCode = "404", description = "Producto o categoria inexistente", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorRespuesta.class)))
    @ApiResponse(responseCode = "400", description = "Datos invalidos, duplicados o eliminacion de categoria con productos", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorRespuesta.class)))
    @ApiResponse(responseCode = "500", description = "Error interno sin detalles sensibles", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorRespuesta.class)))
    @PostMapping
    public ResponseEntity<ProductoRespuesta> crear(@Valid @RequestBody ProductoSolicitud datos) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.crear(datos));
    }

    @Operation(summary = "Actualizar producto", description = "Requiere versionEsperada de la lectura original. Una version obsoleta devuelve 409 sin modificar datos.")
    @ApiResponse(responseCode = "200", description = "Operacion exitosa")
    @ApiResponse(responseCode = "404", description = "Producto o categoria inexistente", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorRespuesta.class)))
    @ApiResponse(responseCode = "400", description = "Datos invalidos, duplicados o eliminacion de categoria con productos", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorRespuesta.class)))
    @ApiResponse(responseCode = "500", description = "Error interno sin detalles sensibles", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorRespuesta.class)))
    @PutMapping("/{id}")
    public ProductoRespuesta actualizar(@Parameter(description = "ID positivo del recurso", example = "2") @PathVariable Long id, @Valid @RequestBody ProductoActualizacionSolicitud datos) {
        return servicio.actualizar(id, datos);
    }

    @Operation(summary = "Cambiar estado del producto", description = "Solo asigna activo sobre el producto actual bajo bloqueo. Conserva stock, precios y demas atributos.")
    @ApiResponse(responseCode = "200", description = "Estado aplicado; incluye version actual")
    @ApiResponse(responseCode = "400", description = "ID o activo invalido")
    @ApiResponse(responseCode = "404", description = "Producto inexistente")
    @PutMapping("/{id}/estado")
    public ProductoRespuesta cambiarEstado(@PathVariable Long id, @Valid @RequestBody EstadoProductoSolicitud datos) {
        return servicio.cambiarEstado(id, datos);
    }

    @Operation(summary = "Eliminar producto")
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
