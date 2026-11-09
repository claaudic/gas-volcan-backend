package cl.gaselvolcan.ms_catalogo.service;

import java.util.*;
import jakarta.validation.Validator;
import cl.gaselvolcan.ms_catalogo.dto.*;
import cl.gaselvolcan.ms_catalogo.model.*;
import cl.gaselvolcan.ms_catalogo.repository.*;
import cl.gaselvolcan.ms_catalogo.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class StockService {
    private final OperacionStockRepository operaciones;
    private final ProductoRepository productos;
    private final Validator validator;
    private final TransactionTemplate tx;

    public StockService(OperacionStockRepository operaciones, ProductoRepository productos,
            Validator validator, PlatformTransactionManager transacciones) {
        this.operaciones = operaciones; this.productos = productos; this.validator = validator;
        tx = new TransactionTemplate(transacciones);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public DescuentoRespuesta descontar(UUID clave, DescuentoSolicitud solicitud) {
        if (clave == null || solicitud == null || !validator.validate(solicitud).isEmpty()) {
            throw new SolicitudInvalidaException("Clave y productos con cantidades positivas son obligatorios (maximo 500 lineas).");
        }
        Map<Long, Integer> normalizada = new TreeMap<>();
        try {
            solicitud.productos().forEach(l -> normalizada.merge(l.productoId(), l.cantidad(), Math::addExact));
        } catch (ArithmeticException error) {
            throw new SolicitudInvalidaException("La cantidad acumulada excede el limite permitido.");
        }
        String id = clave.toString();
        try {
            return tx.execute(s -> {
                var anterior = operaciones.bloquear(id);
                if (anterior.isPresent()) return repetir(anterior.get(), normalizada);
                // La PK serializa llamadas simultaneas con la misma clave.
                var operacion = operaciones.saveAndFlush(new OperacionStock(id, normalizada));
                Map<Long, Producto> bloqueados = new TreeMap<>();
                for (var linea : normalizada.entrySet()) {
                    var producto = productos.bloquear(linea.getKey());
                    if (producto.isEmpty()) return rechazar(operacion, 404, "Producto inexistente: " + linea.getKey());
                    var p = producto.get();
                    if (!Boolean.TRUE.equals(p.getActivo())) return rechazar(operacion, 400, "Producto inactivo: " + linea.getKey());
                    if (p.getStock() < linea.getValue()) return rechazar(operacion, 409, "Stock insuficiente: " + linea.getKey());
                    bloqueados.put(linea.getKey(), p);
                }
                // Solo mutar despues de validar y bloquear TODAS las lineas, en orden de ID.
                normalizada.forEach((productoId, cantidad) -> {
                    var p = bloqueados.get(productoId); p.setStock(p.getStock() - cantidad);
                });
                return respuesta(operacion);
            });
        } catch (DataIntegrityViolationException colision) {
            // La transaccion fallida ya termino. Leer el resultado en otra transaccion.
            return tx.execute(s -> {
                var anterior = operaciones.bloquear(id);
                if (anterior.isEmpty()) throw colision;
                return repetir(anterior.get(), normalizada);
            });
        }
    }

    public DescuentoRespuesta consultar(UUID id) {
        return tx.execute(s -> respuesta(obtener(id)));
    }

    public DescuentoRespuesta compensar(UUID id) {
        return tx.execute(s -> {
            var operacion = operaciones.bloquear(id.toString()).orElseThrow(
                    () -> new RecursoNoEncontradoException("Operacion de stock inexistente."));
            if ("COMPENSADO".equals(operacion.getEstado())) return respuesta(operacion);
            if (!"APLICADO".equals(operacion.getEstado())) throw new ConflictoStockException("Solo se compensa un descuento aplicado.");
            Map<Long, Producto> bloqueados = new TreeMap<>();
            for (var linea : new TreeMap<>(operacion.getProductos()).entrySet()) {
                var p = productos.bloquear(linea.getKey()).orElseThrow(
                        () -> new ConflictoStockException("No se puede restituir un producto eliminado."));
                if ((long) p.getStock() + linea.getValue() > Integer.MAX_VALUE) {
                    throw new ConflictoStockException("La restitucion excede el limite de stock.");
                }
                bloqueados.put(linea.getKey(), p);
            }
            operacion.getProductos().forEach((productoId, cantidad) -> {
                var p = bloqueados.get(productoId); p.setStock(p.getStock() + cantidad);
            });
            operacion.compensar();
            return respuesta(operacion);
        });
    }

    private OperacionStock obtener(UUID id) {
        return operaciones.findById(id.toString()).orElseThrow(
                () -> new RecursoNoEncontradoException("Operacion de stock inexistente."));
    }
    private DescuentoRespuesta repetir(OperacionStock operacion, Map<Long, Integer> solicitud) {
        if (!operacion.getProductos().equals(solicitud)) throw new ConflictoStockException("La clave de idempotencia ya tiene otro contenido.");
        return respuesta(operacion); // Incluye COMPENSADO: nunca volver a descontar.
    }
    private DescuentoRespuesta rechazar(OperacionStock operacion, int codigo, String mensaje) {
        operacion.resultado("RECHAZADO", codigo, mensaje); return respuesta(operacion);
    }
    private DescuentoRespuesta respuesta(OperacionStock o) {
        return new DescuentoRespuesta(o.getId(), o.getEstado(), new TreeMap<>(o.getProductos()).entrySet().stream()
                .map(e -> new DescuentoSolicitud.Linea(e.getKey(), e.getValue())).toList(),
                o.getCodigoResultado(), o.getMensaje(), o.getCreada(), o.getCompensada());
    }
}
