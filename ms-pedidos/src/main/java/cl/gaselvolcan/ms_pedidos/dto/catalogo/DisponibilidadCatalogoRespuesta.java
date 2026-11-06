package cl.gaselvolcan.ms_pedidos.dto.catalogo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DisponibilidadCatalogoRespuesta(Long productoId, String codigo,
        Integer cantidadSolicitada, Integer stock, Boolean activo, Boolean disponible) {
}
