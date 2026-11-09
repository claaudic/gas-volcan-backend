package cl.gaselvolcan.ms_pedidos.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// El precio enviado por clientes antiguos se ignora: solamente catalogo fija el precio.
@JsonIgnoreProperties("precio")
public record DetalleBoletaSolicitud(
        @NotNull(message = "El producto del detalle es obligatorio.")
        @Positive(message = "El identificador del producto debe ser positivo.") Long productoId,
        @NotNull(message = "La cantidad del detalle es obligatoria.")
        @Positive(message = "La cantidad del detalle debe ser positiva.") Integer cantidad) {
}
