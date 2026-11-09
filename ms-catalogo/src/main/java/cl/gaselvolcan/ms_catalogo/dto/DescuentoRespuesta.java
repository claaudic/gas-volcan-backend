package cl.gaselvolcan.ms_catalogo.dto;

import java.time.Instant;
import java.util.List;

public record DescuentoRespuesta(String operacionId, String estado, List<DescuentoSolicitud.Linea> productos,
        int codigoResultado, String mensaje, Instant creada, Instant compensada) {}
