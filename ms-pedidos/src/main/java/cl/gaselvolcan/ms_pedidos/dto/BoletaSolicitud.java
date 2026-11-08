package cl.gaselvolcan.ms_pedidos.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

// No se recibe total: siempre se calcula en el backend. PUT reemplaza la boleta completa.
@JsonIgnoreProperties("total")
public record BoletaSolicitud(
        @NotNull @Positive Long clienteId,
        @NotBlank @Size(max = 300) String direccion,
        @NotBlank @Size(max = 100) String comuna,
        @Pattern(regexp = "PENDIENTE|EN_CAMINO|ENTREGADO|CANCELADO") String estado,
        @Positive Long repartidorId,
        @NotNull LocalDateTime fecha,
        @NotEmpty List<@NotNull @Valid DetalleBoletaSolicitud> detalles) {
}
