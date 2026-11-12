package cl.gaselvolcan.ms_catalogo.model;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.TreeMap;
import jakarta.persistence.*;

@Entity
@Table(name = "operaciones_stock")
public class OperacionStock implements org.springframework.data.domain.Persistable<String> {
    @Id @Column(length = 36) private String id;
    @Column(nullable = false, length = 20) private String estado;
    @ElementCollection
    @CollectionTable(name = "operacion_stock_productos", joinColumns = @JoinColumn(name = "operacion_id"))
    @MapKeyColumn(name = "producto_id")
    @Column(name = "cantidad", nullable = false)
    private Map<Long, Integer> productos = new TreeMap<>();
    @Column(nullable = false) private int codigoResultado;
    @Column(nullable = false, length = 255) private String mensaje;
    @Column(nullable = false) private Instant creada;
    private Instant compensada;
    @Transient private boolean nueva = true;
    @Override public boolean isNew() { return nueva; }
    @PostLoad @PostPersist private void persistida() { nueva = false; }

    protected OperacionStock() {}
    public OperacionStock(String id, Map<Long, Integer> productos) {
        this.id = id; this.productos.putAll(productos); this.creada = Instant.now().truncatedTo(ChronoUnit.MICROS);
        resultado("APLICADO", 200, "Descuento aplicado.");
    }
    public void resultado(String estado, int codigo, String mensaje) {
        this.estado = estado; this.codigoResultado = codigo; this.mensaje = mensaje;
    }
    public void compensar() {
        resultado("COMPENSADO", 200, "Stock restituido."); compensada = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
    public String getId() { return id; }
    public String getEstado() { return estado; }
    public Map<Long, Integer> getProductos() { return productos; }
    public int getCodigoResultado() { return codigoResultado; }
    public String getMensaje() { return mensaje; }
    public Instant getCreada() { return creada; }
    public Instant getCompensada() { return compensada; }
}
