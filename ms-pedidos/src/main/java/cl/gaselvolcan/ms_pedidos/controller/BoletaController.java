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
    private final jakarta.validation.Validator validator;
    private final BoletaService servicio;
    private final cl.gaselvolcan.ms_pedidos.service.CompraService compras;

    public BoletaController(BoletaService servicio, cl.gaselvolcan.ms_pedidos.service.CompraService compras, jakarta.validation.Validator validator) {
        this.validator = validator;
        this.servicio = servicio; this.compras = compras;
    }

    @GetMapping
    public List<BoletaRespuesta> listar() { return servicio.listar(); }

    @GetMapping("/{id}")
    public BoletaRespuesta buscar(@PathVariable Long id) { return servicio.buscarPorId(id); }

    @PostMapping
    public ResponseEntity<BoletaRespuesta> crear(@org.springframework.web.bind.annotation.RequestHeader("Idempotency-Key") java.util.UUID clave,
            @org.springframework.security.core.annotation.AuthenticationPrincipal cl.gaselvolcan.ms_pedidos.config.UsuarioToken usuario,
            @RequestBody BoletaSolicitud datos) {
        Long clienteId = usuario == null ? null : datos.clienteId();
        if (usuario != null && "CLIENTE".equals(usuario.rol())) {
            if (usuario.id() == null || usuario.id() <= 0)
                throw new org.springframework.security.access.AccessDeniedException("Se requiere un token con ID valido.");
            clienteId = usuario.id();
        }
        datos = new BoletaSolicitud(clienteId, datos.direccion(), datos.comuna(), datos.estado(),
                datos.repartidorId(), datos.fecha(), datos.detalles(), datos.nombreInvitado(), datos.correoInvitado(), datos.telefonoInvitado());
        if (!validator.validate(datos).isEmpty()) throw new cl.gaselvolcan.ms_pedidos.exception.SolicitudInvalidaException("Datos de compra incompletos o invalidos.");
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
