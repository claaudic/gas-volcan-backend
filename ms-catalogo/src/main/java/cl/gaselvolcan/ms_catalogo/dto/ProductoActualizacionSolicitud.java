package cl.gaselvolcan.ms_catalogo.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

@Schema(description = "Edicion completa; versionEsperada corresponde a la lectura original del administrador")
public record ProductoActualizacionSolicitud(
        @NotBlank(message = "El codigo es obligatorio.") @Size(max = 255) String codigo,
        @NotBlank(message = "El nombre es obligatorio.") @Size(max = 255) String nombre,
        @Size(max = 255) String descripcion,
        @NotNull @DecimalMin("0.0") @Digits(integer = 17, fraction = 2) BigDecimal precioResidencial,
        @NotNull @DecimalMin("0.0") @Digits(integer = 17, fraction = 2) BigDecimal precioComercial,
        @NotNull @Min(0) Integer stock,
        @NotNull Boolean activo,
        @NotNull @Positive Long categoriaId,
        @DecimalMin("0.0") @Digits(integer = 17, fraction = 2) BigDecimal precioOferta,
        @Size(max = 255) String etiquetaOferta,
        @Min(0) Integer stockCritico,
        @NotBlank @Size(max = 255) String unidad,
        @Size(max = 2048) String imagen,
        @NotNull @Min(0) @Schema(description = "Version leida; 0 es valida", minimum = "0", type = "integer", format = "int64")
        @JsonDeserialize(using = VersionEntera.class) Long versionEsperada) {
    public ProductoActualizacionSolicitud(ProductoSolicitud datos, Long versionEsperada) {
        this(datos.codigo(), datos.nombre(), datos.descripcion(), datos.precioResidencial(), datos.precioComercial(),
                datos.stock(), datos.activo(), datos.categoriaId(), datos.precioOferta(), datos.etiquetaOferta(),
                datos.stockCritico(), datos.unidad(), datos.imagen(), versionEsperada);
    }
    public ProductoSolicitud datos() {
        return new ProductoSolicitud(codigo, nombre, descripcion, precioResidencial, precioComercial,
                stock, activo, categoriaId, precioOferta, etiquetaOferta, stockCritico, unidad, imagen);
    }
    // Rechazar fracciones y cadenas: no permitir que Jackson trunque una version.
    public static class VersionEntera extends ValueDeserializer<Long> {
        @Override public Long deserialize(JsonParser parser, DeserializationContext contexto) {
            if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
                return contexto.reportInputMismatch(Long.class, "versionEsperada debe ser un entero JSON.");
            }
            return parser.getLongValue();
        }
    }
}
