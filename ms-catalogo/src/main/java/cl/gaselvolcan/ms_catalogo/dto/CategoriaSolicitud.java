package cl.gaselvolcan.ms_catalogo.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos de entrada validados")
public record CategoriaSolicitud(
        @NotBlank(message = "El nombre es obligatorio.") @Size(max = 255) @Schema(description = "Nombre del recurso") String nombre,
        @Size(max = 255) @Schema(description = "Descripcion opcional") String descripcion) {
}
