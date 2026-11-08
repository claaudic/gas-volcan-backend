package cl.gaselvolcan.ms_pedidos.model;

public enum EstadoBoleta {
    PENDIENTE, EN_CAMINO, ENTREGADO, CANCELADO;

    public boolean permite(EstadoBoleta destino) {
        return (this == PENDIENTE && (destino == EN_CAMINO || destino == CANCELADO))
                || (this == EN_CAMINO && destino == ENTREGADO);
    }
}
