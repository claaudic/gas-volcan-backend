package cl.gaselvolcan.ms_pedidos.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import cl.gaselvolcan.ms_pedidos.model.Boleta;

// Evita serializar entidades JPA o exponer la referencia circular detalle.boleta.
public record BoletaRespuesta(Long id, Long clienteId, String direccion, String comuna,
        String estado, Long repartidorId, LocalDateTime fecha, BigDecimal total,
        List<DetalleBoletaRespuesta> detalles) {
    public static BoletaRespuesta desde(Boleta boleta) {
        return new BoletaRespuesta(boleta.getId(), boleta.getClienteId(), boleta.getDireccion(),
                boleta.getComuna(), boleta.getEstado(), boleta.getRepartidorId(), boleta.getFecha(),
                boleta.getTotal(), boleta.getDetalles().stream().map(DetalleBoletaRespuesta::desde).toList());
    }
}
