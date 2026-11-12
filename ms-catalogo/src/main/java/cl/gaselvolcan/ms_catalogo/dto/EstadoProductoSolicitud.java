package cl.gaselvolcan.ms_catalogo.dto;

import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Estado explicito; nunca modifica stock ni otros campos")
public record EstadoProductoSolicitud(@NotNull Boolean activo) {}
