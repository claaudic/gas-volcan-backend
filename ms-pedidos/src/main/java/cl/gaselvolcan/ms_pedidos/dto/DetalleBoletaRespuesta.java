package cl.gaselvolcan.ms_pedidos.dto;

import java.math.BigDecimal;
import cl.gaselvolcan.ms_pedidos.model.DetalleBoleta;

public record DetalleBoletaRespuesta(Long id, Long productoId, Integer cantidad, BigDecimal precio) {
    public static DetalleBoletaRespuesta desde(DetalleBoleta detalle) {
        return new DetalleBoletaRespuesta(detalle.getId(), detalle.getProductoId(),
                detalle.getCantidad(), detalle.getPrecio());
    }
}
