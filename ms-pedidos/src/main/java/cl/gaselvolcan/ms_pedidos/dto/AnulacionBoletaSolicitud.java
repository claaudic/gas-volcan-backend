package cl.gaselvolcan.ms_pedidos.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.constraints.*;
import java.util.Map;
import java.util.Set;

public record AnulacionBoletaSolicitud(@NotNull @PositiveOrZero Long versionEsperada) {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static AnulacionBoletaSolicitud crear(Map<String, Object> datos) {
        if (datos == null || !datos.keySet().equals(Set.of("versionEsperada"))) throw new IllegalArgumentException("Campos de anulacion invalidos.");
        return new AnulacionBoletaSolicitud(AsignacionRepartidorSolicitud.entero(datos.get("versionEsperada")));
    }
}
