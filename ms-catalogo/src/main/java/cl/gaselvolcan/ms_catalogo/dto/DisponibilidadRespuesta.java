package cl.gaselvolcan.ms_catalogo.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Disponibilidad informativa; no reserva ni descuenta existencias.")
public record DisponibilidadRespuesta(
        @Schema(description = "ID persistente del producto", example = "2") Long productoId,
        @Schema(description = "Codigo unico original", example = "CL002") String codigo,
        @Schema(description = "Unidades solicitadas; entero positivo", example = "3", minimum = "1") Integer cantidadSolicitada,
        @Schema(description = "Existencias actuales", example = "200", minimum = "0") Integer stock,
        @Schema(description = "Producto habilitado para venta") Boolean activo,
        @Schema(description = "Verdadero solo si esta activo y stock >= cantidad solicitada") boolean disponible) {
}
