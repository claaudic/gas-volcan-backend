package cl.gaselvolcan.ms_pedidos.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "boletas")
public class Boleta {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @jakarta.persistence.Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private Long version;

    @Column(name = "operacion_stock_id", length = 36, unique = true)
    private String operacionStockId;
    public String getOperacionStockId() { return operacionStockId; }
    public void setOperacionStockId(String id) { operacionStockId = id; }

    @Size(max = 150)
    @Column(length = 150)
    private String nombreInvitado;
    @jakarta.validation.constraints.Email
    @Size(max = 254)
    @Column(length = 254)
    private String correoInvitado;
    @jakarta.validation.constraints.Pattern(regexp = "[+0-9() -]{6,30}")
    @Column(length = 30)
    private String telefonoInvitado;
    public String getNombreInvitado() { return nombreInvitado; }
    public void setNombreInvitado(String valor) { nombreInvitado = valor; }
    public String getCorreoInvitado() { return correoInvitado; }
    public void setCorreoInvitado(String valor) { correoInvitado = valor; }
    public String getTelefonoInvitado() { return telefonoInvitado; }
    public void setTelefonoInvitado(String valor) { telefonoInvitado = valor; }

    // Referencia externa: no existe una FK ni relacion JPA a ms-usuarios.
    @Positive
    @Column(name = "cliente_id")
    private Long clienteId;

    @NotBlank
    @Size(max = 300)
    @Column(nullable = false, length = 300)
    private String direccion;

    @NotBlank
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String comuna;

    // Se conserva la columna de texto para no alterar el esquema existente.
    @NotBlank
    @Size(max = 50)
    @Column(nullable = false, length = 50)
    private String estado = EstadoBoleta.PENDIENTE.name();

    @Positive
    @Column(name = "repartidor_id")
    private Long repartidorId;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime fecha;

    @NotNull
    @DecimalMin("0.0")
    @Digits(integer = 17, fraction = 2)
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal total;

    @NotEmpty
    @OneToMany(mappedBy = "boleta", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<@Valid DetalleBoleta> detalles = new ArrayList<>();

    public Boleta() {
    }

    public Boleta(Long clienteId, String direccion, String comuna, String estado,
                  Long repartidorId, LocalDateTime fecha, BigDecimal total) {
        this.clienteId = clienteId;
        this.direccion = direccion;
        this.comuna = comuna;
        this.estado = estado;
        this.repartidorId = repartidorId;
        this.fecha = fecha;
        this.total = total;
    }

    // Mantiene ambos extremos de la relacion; no calcula precios ni cambia estados.
    public void agregarDetalle(DetalleBoleta detalle) {
        Objects.requireNonNull(detalle, "El detalle es obligatorio.");
        if (detalle.getBoleta() != null && detalle.getBoleta() != this) {
            throw new IllegalArgumentException("El detalle ya pertenece a otra boleta.");
        }
        if (!detalles.contains(detalle)) {
            detalles.add(detalle);
            detalle.setBoleta(this);
        }
    }

    public void quitarDetalle(DetalleBoleta detalle) {
        if (detalles.remove(detalle)) {
            detalle.setBoleta(null);
        }
    }

    public Long getId() { return id; }
    public Long getVersion() { return version; }
    public void setId(Long id) { this.id = id; }
    public Long getClienteId() { return clienteId; }
    public void setClienteId(Long clienteId) { this.clienteId = clienteId; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
    public String getComuna() { return comuna; }
    public void setComuna(String comuna) { this.comuna = comuna; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public Long getRepartidorId() { return repartidorId; }
    public void setRepartidorId(Long repartidorId) { this.repartidorId = repartidorId; }
    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public List<DetalleBoleta> getDetalles() { return Collections.unmodifiableList(detalles); }
}
