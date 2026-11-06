package cl.gaselvolcan.ms_pedidos.model;

import java.math.BigDecimal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Entity
@Table(name = "detalles_boleta")
public class DetalleBoleta {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Solo identificador: sin relacion JPA ni FK hacia volcan_catalogo.
    @NotNull
    @Positive
    @Column(name = "producto_id", nullable = false)
    private Long productoId;

    @NotNull
    @Positive
    @Column(nullable = false)
    private Integer cantidad;

    // Precio unitario de la linea en el momento de emitir la boleta.
    @NotNull
    @DecimalMin("0.0")
    @Digits(integer = 17, fraction = 2)
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal precio;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "boleta_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_detalle_boleta"))
    private Boleta boleta;

    public DetalleBoleta() {
    }

    public DetalleBoleta(Long productoId, Integer cantidad, BigDecimal precio) {
        this.productoId = productoId;
        this.cantidad = cantidad;
        this.precio = precio;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProductoId() { return productoId; }
    public void setProductoId(Long productoId) { this.productoId = productoId; }
    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }
    public BigDecimal getPrecio() { return precio; }
    public void setPrecio(BigDecimal precio) { this.precio = precio; }
    public Boleta getBoleta() { return boleta; }
    public void setBoleta(Boleta boleta) { this.boleta = boleta; }
}
