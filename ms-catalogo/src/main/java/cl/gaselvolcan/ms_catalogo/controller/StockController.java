package cl.gaselvolcan.ms_catalogo.controller;

import io.swagger.v3.oas.annotations.Operation;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import cl.gaselvolcan.ms_catalogo.dto.*;
import cl.gaselvolcan.ms_catalogo.service.StockService;

@RestController
@RequestMapping("/internal/v1/stock/descuentos")
public class StockController {
    private final StockService servicio;
    public StockController(StockService servicio) { this.servicio = servicio; }
    @Operation(summary = "Descontar stock de forma atomica")
    @PostMapping
    public ResponseEntity<DescuentoRespuesta> descontar(@RequestHeader("Idempotency-Key") UUID clave,
            @Valid @RequestBody DescuentoSolicitud solicitud) {
        var resultado = servicio.descontar(clave, solicitud);
        return ResponseEntity.status(resultado.codigoResultado()).body(resultado);
    }
    @Operation(summary = "Consultar operacion durable de stock")
    @GetMapping("/{operacionId}")
    public DescuentoRespuesta consultar(@PathVariable UUID operacionId) { return servicio.consultar(operacionId); }
    @Operation(summary = "Restituir stock de una operacion")
    @PutMapping("/{operacionId}/compensacion")
    public DescuentoRespuesta compensar(@PathVariable UUID operacionId) { return servicio.compensar(operacionId); }
}
