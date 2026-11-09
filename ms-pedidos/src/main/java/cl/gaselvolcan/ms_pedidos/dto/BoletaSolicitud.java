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
        @Positive Long clienteId,
        @NotBlank @Size(max = 300) String direccion,
        @NotBlank @Size(max = 100) String comuna,
        @Pattern(regexp = "PENDIENTE|EN_CAMINO|ENTREGADO|CANCELADO") String estado,
        @Positive Long repartidorId,
        @NotNull LocalDateTime fecha,
        @NotEmpty List<@NotNull @Valid DetalleBoletaSolicitud> detalles,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) @Size(max = 150) String nombreInvitado,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) @jakarta.validation.constraints.Email @Size(max = 254) String correoInvitado,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) @Pattern(regexp = "[+0-9() -]{6,30}") String telefonoInvitado) {
    public BoletaSolicitud(Long clienteId, String direccion, String comuna, String estado, Long repartidorId,
            LocalDateTime fecha, List<DetalleBoletaSolicitud> detalles) {
        this(clienteId, direccion, comuna, estado, repartidorId, fecha, detalles, null, null, null);
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    @jakarta.validation.constraints.AssertTrue(message = "Nombre, correo y telefono son obligatorios para invitados")
    public boolean isIdentidadValida() {
        return clienteId != null || (nombreInvitado != null && !nombreInvitado.isBlank()
                && correoInvitado != null && !correoInvitado.isBlank()
                && telefonoInvitado != null && !telefonoInvitado.isBlank());
    }
}
