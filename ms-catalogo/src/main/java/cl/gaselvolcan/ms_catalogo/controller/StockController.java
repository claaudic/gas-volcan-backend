package cl.gaselvolcan.ms_catalogo.controller;

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
    @PostMapping
    public ResponseEntity<DescuentoRespuesta> descontar(@RequestHeader("Idempotency-Key") UUID clave,
            @Valid @RequestBody DescuentoSolicitud solicitud) {
        var resultado = servicio.descontar(clave, solicitud);
        return ResponseEntity.status(resultado.codigoResultado()).body(resultado);
    }
    @GetMapping("/{operacionId}")
    public DescuentoRespuesta consultar(@PathVariable UUID operacionId) { return servicio.consultar(operacionId); }
    @PutMapping("/{operacionId}/compensacion")
    public DescuentoRespuesta compensar(@PathVariable UUID operacionId) { return servicio.compensar(operacionId); }
}
