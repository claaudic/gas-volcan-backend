package cl.gaselvolcan.ms_pedidos.controller;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import cl.gaselvolcan.ms_pedidos.dto.BoletaRespuesta;
import cl.gaselvolcan.ms_pedidos.dto.BoletaSolicitud;
import cl.gaselvolcan.ms_pedidos.dto.EstadoBoletaSolicitud;
import cl.gaselvolcan.ms_pedidos.service.BoletaService;

@RestController
@RequestMapping("/api/v1/boletas")
public class BoletaController {
    private final BoletaService servicio;
    private final cl.gaselvolcan.ms_pedidos.service.CompraService compras;

    public BoletaController(BoletaService servicio, cl.gaselvolcan.ms_pedidos.service.CompraService compras) {
        this.servicio = servicio; this.compras = compras;
    }

    @GetMapping
    public List<BoletaRespuesta> listar() { return servicio.listar(); }

    @GetMapping("/{id}")
    public BoletaRespuesta buscar(@PathVariable Long id) { return servicio.buscarPorId(id); }

    @PostMapping
    public ResponseEntity<BoletaRespuesta> crear(@org.springframework.web.bind.annotation.RequestHeader("Idempotency-Key") java.util.UUID clave,
            @Valid @RequestBody BoletaSolicitud datos) {
        return ResponseEntity.status(HttpStatus.CREATED).body(compras.crear(clave, datos));
    }

    @GetMapping("/{id}/stock")
    public cl.gaselvolcan.ms_pedidos.service.CompraService.EstadoStock estadoStock(@PathVariable Long id) {
        return compras.estadoStock(id);
    }

    @PutMapping("/{id}")
    public BoletaRespuesta actualizar(@PathVariable Long id, @Valid @RequestBody BoletaSolicitud datos) {
        return servicio.actualizar(id, datos);
    }

    @PutMapping("/{id}/estado")
    public BoletaRespuesta actualizarEstado(@PathVariable Long id, @Valid @RequestBody EstadoBoletaSolicitud datos) {
        return servicio.actualizarEstado(id, datos.estado());
    }

}
