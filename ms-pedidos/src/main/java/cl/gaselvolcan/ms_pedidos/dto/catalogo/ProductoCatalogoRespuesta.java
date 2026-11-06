package cl.gaselvolcan.ms_pedidos.dto.catalogo;

import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductoCatalogoRespuesta(Long id, String codigo, Boolean activo,
        BigDecimal precioResidencial, BigDecimal precioOferta) {
}
