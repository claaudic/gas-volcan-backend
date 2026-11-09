package cl.gaselvolcan.ms_pedidos.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

// No se recibe total: siempre se calcula en el backend. PUT reemplaza la boleta completa.
@JsonIgnoreProperties("total")
public record BoletaSolicitud(
        @Positive(message = "El identificador del cliente debe ser positivo.") Long clienteId,
        @NotBlank(message = "La dirección es obligatoria.") @Size(max = 300, message = "La dirección no puede superar 300 caracteres.") String direccion,
        @NotBlank(message = "La comuna es obligatoria.") @Size(max = 100, message = "La comuna no puede superar 100 caracteres.") String comuna,
        @Pattern(regexp = "PENDIENTE|EN_CAMINO|ENTREGADO|CANCELADO", message = "El estado de la boleta no es válido.") String estado,
        @Positive(message = "El identificador del repartidor debe ser positivo.") Long repartidorId,
        @NotNull(message = "La fecha es obligatoria.") LocalDateTime fecha,
        @NotEmpty(message = "La compra debe incluir al menos un detalle.") List<@NotNull(message = "El detalle de la compra es obligatorio.") @Valid DetalleBoletaSolicitud> detalles,
        @JsonInclude(Include.NON_NULL) @Size(max = 150, message = "El nombre del invitado no puede superar 150 caracteres.") String nombreInvitado,
        @JsonInclude(Include.NON_NULL) @Email(message = "El correo del invitado no es válido.") @Size(max = 254, message = "El correo del invitado no puede superar 254 caracteres.") String correoInvitado,
        @JsonInclude(Include.NON_NULL) @Pattern(regexp = "[+0-9() -]{6,30}", message = "El teléfono del invitado no es válido.") String telefonoInvitado) {
    public BoletaSolicitud(Long clienteId, String direccion, String comuna, String estado, Long repartidorId,
            LocalDateTime fecha, List<DetalleBoletaSolicitud> detalles) {
        this(clienteId, direccion, comuna, estado, repartidorId, fecha, detalles, null, null, null);
    }

    @JsonIgnore
    @AssertTrue(message = "El nombre del invitado es obligatorio.")
    public boolean isNombreInvitadoInformado() {
        return clienteId != null || informado(nombreInvitado);
    }

    @JsonIgnore
    @AssertTrue(message = "El correo del invitado es obligatorio.")
    public boolean isCorreoInvitadoInformado() {
        return clienteId != null || informado(correoInvitado);
    }

    @JsonIgnore
    @AssertTrue(message = "El teléfono del invitado es obligatorio.")
    public boolean isTelefonoInvitadoInformado() {
        return clienteId != null || informado(telefonoInvitado);
    }

    private static boolean informado(String valor) {
        return valor != null && !valor.isBlank();
    }
}
