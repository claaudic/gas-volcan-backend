package cl.gaselvolcan.ms_pedidos.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.constraints.*;
import java.util.Map;
import java.util.Set;

public record AsignacionRepartidorSolicitud(@Positive Long repartidorId, @NotNull @PositiveOrZero Long versionEsperada) {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static AsignacionRepartidorSolicitud crear(Map<String, Object> datos) {
        if (datos == null || !datos.keySet().equals(Set.of("repartidorId", "versionEsperada"))) throw new IllegalArgumentException("Campos de asignacion invalidos.");
        return new AsignacionRepartidorSolicitud(entero(datos.get("repartidorId")), entero(datos.get("versionEsperada")));
    }
    static Long entero(Object valor) {
        if (valor == null) return null;
        if (valor instanceof Integer i) return i.longValue();
        if (valor instanceof Long l) return l;
        throw new IllegalArgumentException("El valor debe ser un entero JSON exacto.");
    }
}
