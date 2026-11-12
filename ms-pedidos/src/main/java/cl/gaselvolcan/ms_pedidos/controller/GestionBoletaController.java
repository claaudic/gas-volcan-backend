package cl.gaselvolcan.ms_pedidos.controller;

import java.util.List;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.Operation;
import cl.gaselvolcan.ms_pedidos.dto.*;
import cl.gaselvolcan.ms_pedidos.service.GestionBoletaService;

@RestController
@RequestMapping("/api/v1/boletas")
public class GestionBoletaController {
    private final GestionBoletaService gestion;
    public GestionBoletaController(GestionBoletaService gestion) { this.gestion = gestion; }
    @Operation(summary = "Listar pedidos visibles con contacto y version de gestion")
    @GetMapping("/gestion")
    public ResponseEntity<List<GestionBoletaRespuesta>> listar() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(gestion.listar());
    }
    @Operation(summary = "Consultar pedido autorizado con contacto y version de gestion")
    @GetMapping("/{id}/gestion")
    public ResponseEntity<GestionBoletaRespuesta> consultar(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(gestion.consultar(id));
    }
    @Operation(summary = "Asignar, cambiar o retirar repartidor de un pedido pendiente sin alterar stock")
    @PutMapping("/{id}/repartidor")
    public VersionBoletaRespuesta asignar(@PathVariable Long id, @Valid @RequestBody AsignacionRepartidorSolicitud datos) {
        return gestion.asignar(id, datos);
    }
    @Operation(summary = "Anular pedido pendiente conservando historial e idempotencia; compensacion de stock recuperable")
    @PutMapping("/{id}/anulacion")
    public VersionBoletaRespuesta anular(@PathVariable Long id, @Valid @RequestBody AnulacionBoletaSolicitud datos) {
        return gestion.anular(id, datos);
    }
}
