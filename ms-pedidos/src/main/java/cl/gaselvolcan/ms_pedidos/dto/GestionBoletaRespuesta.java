package cl.gaselvolcan.ms_pedidos.dto;

public record GestionBoletaRespuesta(BoletaRespuesta boleta, Long version, Comprador cliente, Entrega entrega) {
    @Override public String toString() { return "GestionBoletaRespuesta[id=" + boleta.id() + ", version=" + version + "]"; }
    public record Comprador(String nombre, String correo, String telefono) {
        @Override public String toString() { return "Comprador[datos reservados]"; }
    }
    public record Entrega(String direccion, String comuna) {}
}
