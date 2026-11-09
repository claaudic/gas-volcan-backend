package cl.gaselvolcan.ms_pedidos.model;

import java.time.Instant;
import jakarta.persistence.*;
import org.springframework.data.domain.Persistable;

@Entity @Table(name = "operaciones_compra")
public class OperacionCompra implements Persistable<String> {
    @Id @Column(length = 36) private String id;
    // Sin longitud explicita, @Column usa 255 y MySQLDialect selecciona TINYTEXT.
    // La capacidad LONG32 fuerza LONGTEXT en MySQL y conserva el CLOB de H2.
    @Lob @Column(nullable = false, length = Integer.MAX_VALUE) private String solicitud;
    @Lob private String cotizacion;
    @Column(nullable = false, length = 40) private String estado = "REGISTRADA";
    private Long boletaId;
    @Column(length = 36) private String trabajador;
    private Instant vence;
    @Column(nullable = false) private Instant creada = Instant.now();
    @Column(nullable = false) private Instant ultimaRevision = Instant.now();
    @Column(length = 255) private String mensaje;
    @Transient private boolean nueva = true;
    protected OperacionCompra() {}
    public OperacionCompra(String id, String solicitud) { this.id = id; this.solicitud = solicitud; }
    @Override public String getId() { return id; }
    @Override public boolean isNew() { return nueva; }
    @PostLoad @PostPersist private void cargada() { nueva = false; }
    public String getSolicitud() { return solicitud; }
    public String getCotizacion() { return cotizacion; }
    public void setCotizacion(String c) { cotizacion = c; }
    public String getEstado() { return estado; }
    public void setEstado(String e) { estado = e; }
    public Long getBoletaId() { return boletaId; }
    public void setBoletaId(Long id) { boletaId = id; }
    public String getTrabajador() { return trabajador; }
    public Instant getVence() { return vence; }
    public void tomar(String dueno) { trabajador = dueno; ultimaRevision = Instant.now(); vence = ultimaRevision.plusSeconds(30); }
    public void liberar() { trabajador = null; vence = null; }
    public String getMensaje() { return mensaje; }
    public void setMensaje(String m) { mensaje = m; }
}
