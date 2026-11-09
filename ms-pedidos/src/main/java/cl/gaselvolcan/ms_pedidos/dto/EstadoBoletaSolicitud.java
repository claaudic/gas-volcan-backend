package cl.gaselvolcan.ms_pedidos.dto;

import cl.gaselvolcan.ms_pedidos.model.EstadoBoleta;
import jakarta.validation.constraints.NotNull;

public record EstadoBoletaSolicitud(@NotNull EstadoBoleta estado) {
}
