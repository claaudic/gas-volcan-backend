package cl.gaselvolcan.ms_pedidos.service;

import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import cl.gaselvolcan.ms_pedidos.client.StockInternoClient;
import cl.gaselvolcan.ms_pedidos.dto.*;
import cl.gaselvolcan.ms_pedidos.model.*;
import cl.gaselvolcan.ms_pedidos.repository.*;
import cl.gaselvolcan.ms_pedidos.exception.*;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class CompraService {
    private static final Set<String> PENDIENTES = Set.of("REGISTRADA", "COTIZADA", "COMPENSACION_PENDIENTE");
    private final OperacionCompraRepository operaciones;
    private final BoletaRepository boletas;
    private final BoletaService servicio;
    private final StockInternoClient stock;
    private final Validator validator;
    private final TransactionTemplate tx;
    private final JsonMapper json = JsonMapper.builder().build();

    public CompraService(OperacionCompraRepository operaciones, BoletaRepository boletas, BoletaService servicio,
            StockInternoClient stock, Validator validator, PlatformTransactionManager manager) {
        this.operaciones = operaciones; this.boletas = boletas; this.servicio = servicio;
        this.stock = stock; this.validator = validator;
        tx = new TransactionTemplate(manager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public BoletaRespuesta crear(UUID clave, BoletaSolicitud solicitud) {
        if (clave == null) throw new SolicitudInvalidaException("Idempotency-Key es obligatorio.");
        String contenido = json.writeValueAsString(normalizar(solicitud));
        String id = clave.toString();
        try {
            tx.executeWithoutResult(s -> {
                var previa = operaciones.bloquear(id);
                if (previa.isPresent()) comparar(previa.get(), contenido);
                else operaciones.saveAndFlush(new OperacionCompra(id, contenido));
            });
        } catch (DataIntegrityViolationException colision) {
            tx.executeWithoutResult(s -> {
                var previa = operaciones.bloquear(id);
                if (previa.isEmpty()) throw colision;
                comparar(previa.get(), contenido);
            });
        }
        procesar(clave);
        return tx.execute(s -> {
            var o = operaciones.findById(id).orElseThrow();
            if ("CONFIRMADA".equals(o.getEstado())) return BoletaRespuesta.desde(boletas.findById(o.getBoletaId()).orElseThrow());
            if ("RECHAZADA".equals(o.getEstado())) throw new SolicitudInvalidaException(o.getMensaje());
            if (Set.of("COMPENSADA", "COMPENSACION_PENDIENTE").contains(o.getEstado())) {
                throw new ConflictoCompraException("La compra fue cancelada; no puede reactivarse.");
            }
            throw pendiente();
        });
    }

    private void comparar(OperacionCompra o, String contenido) {
        if (!o.getSolicitud().equals(contenido)) throw new ConflictoCompraException("Idempotency-Key tiene otro contenido.");
    }

    private BoletaSolicitud normalizar(BoletaSolicitud datos) {
        if (datos == null || !validator.validate(datos).isEmpty()
                || datos.detalles().size() > 500 || (datos.estado() != null && !"PENDIENTE".equals(datos.estado()))) {
            throw new SolicitudInvalidaException("Solicitud de compra invalida; se requieren de 1 a 500 lineas y estado inicial PENDIENTE.");
        }
        Map<Long, Integer> cantidades = new TreeMap<>();
        try { datos.detalles().forEach(d -> cantidades.merge(d.productoId(), d.cantidad(), Math::addExact)); }
        catch (ArithmeticException e) { throw new SolicitudInvalidaException("Cantidad acumulada fuera de rango."); }
        return new BoletaSolicitud(datos.clienteId(), datos.direccion().trim(), datos.comuna().trim(), "PENDIENTE",
                datos.repartidorId(), datos.fecha(), cantidades.entrySet().stream()
                        .map(e -> new DetalleBoletaSolicitud(e.getKey(), e.getValue())).toList());
    }

    private record Trabajo(String estado, String solicitud, String cotizacion) {}

    public void procesar(UUID clave) {
        String id = clave.toString(); String dueno = UUID.randomUUID().toString();
        Trabajo trabajo = tx.execute(s -> {
            var o = operaciones.bloquear(id).orElseThrow();
            if (!PENDIENTES.contains(o.getEstado()) || (o.getVence() != null && o.getVence().isAfter(Instant.now()))) return null;
            o.tomar(dueno); return new Trabajo(o.getEstado(), o.getSolicitud(), o.getCotizacion());
        });
        if (trabajo == null) return;
        try {
            String cotizacion = trabajo.cotizacion();
            if ("REGISTRADA".equals(trabajo.estado())) {
                var datos = json.readValue(trabajo.solicitud(), BoletaSolicitud.class);
                try { cotizacion = json.writeValueAsString(servicio.cotizar(datos)); }
                catch (SolicitudInvalidaException | RecursoNoEncontradoException rechazo) {
                    cambiar(id, dueno, "REGISTRADA", o -> { o.setEstado("RECHAZADA"); o.setMensaje("Compra rechazada al validar productos y disponibilidad."); });
                    return;
                }
                String fija = cotizacion;
                if (!cambiar(id, dueno, "REGISTRADA", o -> { o.setCotizacion(fija); o.setEstado("COTIZADA"); })) return;
            }
            var datos = json.readValue(trabajo.solicitud(), BoletaSolicitud.class);
            var lineas = datos.detalles().stream().map(d -> new StockInternoClient.Linea(d.productoId(), d.cantidad())).toList();
            boolean compensacion = "COMPENSACION_PENDIENTE".equals(trabajo.estado());
            StockInternoClient.Resultado resultado;
            try {
                resultado = stock.consultar(clave);
                if (compensacion) {
                    if (resultado == null) throw pendiente(); // Nunca dar por compensado un 404.
                    verificar(resultado, lineas);
                    if ("APLICADO".equals(resultado.estado())) resultado = stock.compensar(clave);
                } else if (resultado == null) resultado = stock.descontar(clave, lineas);
            } catch (RuntimeException desconocido) {
                resultado = stock.consultar(clave); // Si tambien falla, queda durablemente pendiente.
                if (resultado == null) throw pendiente();
            }
            verificar(resultado, lineas);
            if (compensacion) {
                if (!"COMPENSADO".equals(resultado.estado())) throw pendiente();
                cambiar(id, dueno, "COMPENSACION_PENDIENTE", o -> o.setEstado("COMPENSADA"));
            } else if ("APLICADO".equals(resultado.estado())) {
                var precioFijo = json.readValue(cotizacion, BoletaRespuesta.class);
                cambiar(id, dueno, "COTIZADA", o -> {
                    var b = new Boleta(precioFijo.clienteId(), precioFijo.direccion(), precioFijo.comuna(), "PENDIENTE",
                            precioFijo.repartidorId(), precioFijo.fecha(), precioFijo.total());
                    b.setOperacionStockId(id);
                    precioFijo.detalles().forEach(d -> b.agregarDetalle(new DetalleBoleta(d.productoId(), d.cantidad(), d.precio())));
                    var guardada = boletas.saveAndFlush(b);
                    o.setBoletaId(guardada.getId()); o.setEstado("CONFIRMADA");
                });
            } else if ("RECHAZADO".equals(resultado.estado())) {
                cambiar(id, dueno, "COTIZADA", o -> { o.setEstado("RECHAZADA"); o.setMensaje("Compra rechazada por catalogo: producto no disponible o stock insuficiente."); });
            } else {
                cambiar(id, dueno, "COTIZADA", o -> o.setEstado("COMPENSADA"));
            }
        } catch (RuntimeException error) {
            org.slf4j.LoggerFactory.getLogger(CompraService.class).warn("Compra {} pendiente de recuperacion ({})", id, error.getClass().getSimpleName());
        } finally {
            tx.executeWithoutResult(s -> operaciones.bloquear(id).ifPresent(o -> {
                if (dueno.equals(o.getTrabajador())) o.liberar();
            }));
        }
    }

    // Fence durable: un trabajador cuyo lease fue reemplazado no puede confirmar ni cerrar compensaciones.
    private boolean cambiar(String id, String dueno, String estado, Consumer<OperacionCompra> cambio) {
        return Boolean.TRUE.equals(tx.execute(s -> {
            var o = operaciones.bloquear(id).orElseThrow();
            if (!dueno.equals(o.getTrabajador()) || !estado.equals(o.getEstado())) return false;
            cambio.accept(o); return true;
        }));
    }

    private void verificar(StockInternoClient.Resultado r, List<StockInternoClient.Linea> esperado) {
        if (r == null || r.codigoResultado() == null || r.productos() == null || r.estado() == null) throw pendiente();
        var orden = Comparator.comparing(StockInternoClient.Linea::productoId);
        if (!r.productos().stream().sorted(orden).toList().equals(esperado)) throw pendiente();
        if (Set.of("APLICADO", "COMPENSADO").contains(r.estado())) {
            if (r.codigoResultado() != 200) throw pendiente();
        } else if (!"RECHAZADO".equals(r.estado()) || !Set.of(400, 404, 409).contains(r.codigoResultado())) throw pendiente();
    }

    public void recuperarPendientes() {
        var ids = tx.execute(s -> operaciones.recuperables(Instant.now(), org.springframework.data.domain.PageRequest.of(0, 50)));
        for (String id : ids) {
            try { procesar(UUID.fromString(id)); }
            catch (RuntimeException e) { org.slf4j.LoggerFactory.getLogger(CompraService.class).warn("No se pudo recuperar compra {}", id); }
        }
    }

    public record EstadoStock(String operacionId, String estadoTecnico, boolean stockRestituido) {}
    public EstadoStock estadoStock(Long id) {
        servicio.buscarPorId(id); // Conserva los permisos por repartidor.
        return tx.execute(s -> {
            var b = boletas.findById(id).orElseThrow();
            if (b.getOperacionStockId() == null) return new EstadoStock(null, "SIN_REGISTRO", false);
            var o = operaciones.findById(b.getOperacionStockId()).orElseThrow();
            return new EstadoStock(o.getId(), o.getEstado(), "COMPENSADA".equals(o.getEstado()));
        });
    }
    private CatalogoNoDisponibleException pendiente() {
        return new CatalogoNoDisponibleException("Compra pendiente de recuperacion. Reintenta con el mismo Idempotency-Key y contenido.");
    }
}
