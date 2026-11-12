package cl.gaselvolcan.ms_pedidos.dto;

public record VersionBoletaRespuesta(BoletaRespuesta boleta, Long version) {
    @Override public String toString() { return "VersionBoletaRespuesta[id=" + boleta.id() + ", version=" + version + "]"; }
}
