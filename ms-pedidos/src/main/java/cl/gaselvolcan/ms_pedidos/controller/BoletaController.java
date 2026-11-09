package cl.gaselvolcan.ms_pedidos.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.AssertTrue;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.gaselvolcan.ms_pedidos.config.UsuarioToken;
import cl.gaselvolcan.ms_pedidos.dto.BoletaRespuesta;
import cl.gaselvolcan.ms_pedidos.dto.BoletaSolicitud;
import cl.gaselvolcan.ms_pedidos.dto.EstadoBoletaSolicitud;
import cl.gaselvolcan.ms_pedidos.exception.SolicitudInvalidaException;
import cl.gaselvolcan.ms_pedidos.service.BoletaService;
import cl.gaselvolcan.ms_pedidos.service.CompraService.EstadoStock;
import cl.gaselvolcan.ms_pedidos.service.CompraService;

@RestController
@RequestMapping("/api/v1/boletas")
public class BoletaController {
    private final Validator validator;
    private final BoletaService servicio;
    private final CompraService compras;

    public BoletaController(BoletaService servicio, CompraService compras, Validator validator) {
        this.validator = validator;
        this.servicio = servicio; this.compras = compras;
    }

    @Operation(summary = "Listar boletas visibles para el usuario")
    @GetMapping
    public List<BoletaRespuesta> listar() { return servicio.listar(); }

    @Operation(summary = "Consultar boleta por ID")
    @GetMapping("/{id}")
    public BoletaRespuesta buscar(@PathVariable Long id) { return servicio.buscarPorId(id); }

    @Operation(summary = "Comprar con descuento de stock idempotente")
    @PostMapping
    public ResponseEntity<BoletaRespuesta> crear(@RequestHeader("Idempotency-Key") UUID clave,
            @Parameter(hidden = true) @AuthenticationPrincipal UsuarioToken usuario,
            @RequestBody BoletaSolicitud datos) {
        Long clienteId = usuario == null ? null : datos.clienteId();
        if (usuario != null && "CLIENTE".equals(usuario.rol())) {
            if (usuario.id() == null || usuario.id() <= 0)
                throw new AccessDeniedException("Se requiere un token con ID valido.");
            clienteId = usuario.id();
        }
        datos = new BoletaSolicitud(clienteId, datos.direccion(), datos.comuna(), datos.estado(),
                datos.repartidorId(), datos.fecha(), datos.detalles(), datos.nombreInvitado(), datos.correoInvitado(), datos.telefonoInvitado());
        // Primero informar datos obligatorios ausentes; despues formato y limites.
        validator.validate(datos).stream()
                .sorted(Comparator.comparing((ConstraintViolation<BoletaSolicitud> error) ->
                        !(error.getConstraintDescriptor().getAnnotation() instanceof AssertTrue))
                        .thenComparing(error -> error.getPropertyPath().toString())
                        .thenComparing(ConstraintViolation::getMessage))
                .findFirst().ifPresent(error -> { throw new SolicitudInvalidaException(error.getMessage()); });
        return ResponseEntity.status(HttpStatus.CREATED).body(compras.crear(clave, datos));
    }

    @Operation(summary = "Consultar estado durable del stock")
    @GetMapping("/{id}/stock")
    public EstadoStock estadoStock(@PathVariable Long id) {
        return compras.estadoStock(id);
    }

    @Operation(summary = "Reemplazar boleta historica pendiente")
    @PutMapping("/{id}")
    public BoletaRespuesta actualizar(@PathVariable Long id, @Valid @RequestBody BoletaSolicitud datos) {
        return servicio.actualizar(id, datos);
    }

    @Operation(summary = "Cambiar estado comercial o cancelar boleta")
    @PutMapping("/{id}/estado")
    public BoletaRespuesta actualizarEstado(@PathVariable Long id, @Valid @RequestBody EstadoBoletaSolicitud datos) {
        return servicio.actualizarEstado(id, datos.estado());
    }

}
