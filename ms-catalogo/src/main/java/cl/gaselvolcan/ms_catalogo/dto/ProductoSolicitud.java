package cl.gaselvolcan.ms_catalogo.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import jakarta.validation.constraints.*;

@Schema(description = "Datos de entrada validados")
public record ProductoSolicitud(
        @NotBlank(message = "El codigo es obligatorio.") @Size(max = 255) @Schema(description = "Codigo unico del producto") String codigo,
        @NotBlank(message = "El nombre es obligatorio.") @Size(max = 255) @Schema(description = "Nombre del recurso") String nombre,
        @Size(max = 255) @Schema(description = "Descripcion opcional") String descripcion,
        @NotNull @DecimalMin("0.0") @Digits(integer = 17, fraction = 2) @Schema(description = "Precio base residencial en CLP; no negativo; hasta 17 enteros y 2 decimales") BigDecimal precioResidencial,
        @NotNull @DecimalMin("0.0") @Digits(integer = 17, fraction = 2) @Schema(description = "Precio base comercial en CLP; no negativo; hasta 17 enteros y 2 decimales") BigDecimal precioComercial,
        @NotNull @Min(0) @Schema(description = "Existencias; entero no negativo") Integer stock,
        @NotNull @Schema(description = "Habilitado para venta") Boolean activo,
        @NotNull @Positive @Schema(description = "ID positivo de una categoria existente") Long categoriaId,
        @DecimalMin("0.0") @Digits(integer = 17, fraction = 2) @Schema(description = "Precio de oferta en CLP; nulo indica sin oferta; hasta 17 enteros y 2 decimales") BigDecimal precioOferta,
        @Size(max = 255) @Schema(description = "Texto opcional de la oferta") String etiquetaOferta,
        @Min(0) @Schema(description = "Umbral opcional; entero no negativo") Integer stockCritico,
        @NotBlank @Size(max = 255) @Schema(description = "Unidad de venta obligatoria, por ejemplo Unidad o Kit") String unidad,
        @Size(max = 2048) @Schema(description = "Ruta opcional de imagen; permite cadena vacia") String imagen) {
    // Conserva las llamadas Java anteriores; la API recibe la unidad explicitamente.
    public ProductoSolicitud(String codigo, String nombre, String descripcion,
            BigDecimal precioResidencial, BigDecimal precioComercial, Integer stock,
            Boolean activo, Long categoriaId) {
        this(codigo, nombre, descripcion, precioResidencial, precioComercial, stock,
                activo, categoriaId, null, null, null, "Unidad", "");
    }
}
