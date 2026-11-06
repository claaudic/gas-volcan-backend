package cl.gaselvolcan.ms_pedidos.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// El precio enviado por clientes antiguos se ignora: solamente catalogo fija el precio.
@JsonIgnoreProperties("precio")
public record DetalleBoletaSolicitud(
        @NotNull @Positive Long productoId,
        @NotNull @Positive Integer cantidad) {
}
