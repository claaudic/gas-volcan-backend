package cl.gaselvolcan.ms_catalogo.dto;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record DescuentoSolicitud(@NotEmpty @Size(max = 500) List<@NotNull @Valid Linea> productos) {
    public record Linea(@NotNull @Positive Long productoId, @NotNull @Positive Integer cantidad) {}
}
