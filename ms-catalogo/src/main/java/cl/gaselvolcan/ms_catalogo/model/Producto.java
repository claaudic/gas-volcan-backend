package cl.gaselvolcan.ms_catalogo.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

@Entity
@Table(name = "productos")
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Evita que un PUT administrativo basado en una lectura anterior pise el stock.
    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private Long version;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false, unique = true)
    private String codigo;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false)
    private String nombre;

    @Size(max = 255)
    private String descripcion;

    @NotNull
    @DecimalMin("0.0")
    @Digits(integer = 17, fraction = 2)
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal precioResidencial;

    @NotNull
    @DecimalMin("0.0")
    @Digits(integer = 17, fraction = 2)
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal precioComercial;

    @NotNull
    @Min(0)
    @Column(nullable = false)
    private Integer stock;

    @NotNull
    @Column(nullable = false)
    private Boolean activo = true;

    @NotNull
    @ManyToOne(optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @DecimalMin("0.0")
    @Digits(integer = 17, fraction = 2)
    @Column(precision = 19, scale = 2)
    private BigDecimal precioOferta;

    @Size(max = 255)
    private String etiquetaOferta;

    @Min(0)
    private Integer stockCritico;

    @NotBlank
    @Size(max = 255)
    private String unidad = "Unidad";

    @Size(max = 2048)
    @Column(length = 2048)
    private String imagen = "";

    public Producto() {
    }

    public Producto(String codigo, String nombre, String descripcion, BigDecimal precioResidencial,
                    BigDecimal precioComercial, Integer stock, Categoria categoria) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.precioResidencial = precioResidencial;
        this.precioComercial = precioComercial;
        this.stock = stock;
        this.categoria = categoria;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public BigDecimal getPrecioResidencial() { return precioResidencial; }
    public void setPrecioResidencial(BigDecimal precioResidencial) { this.precioResidencial = precioResidencial; }

    public BigDecimal getPrecioComercial() { return precioComercial; }
    public void setPrecioComercial(BigDecimal precioComercial) { this.precioComercial = precioComercial; }

    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }

    public Boolean getActivo() { return activo; }
    public void setActivo(Boolean activo) { this.activo = activo; }

    public Categoria getCategoria() { return categoria; }
    public void setCategoria(Categoria categoria) { this.categoria = categoria; }

    public BigDecimal getPrecioOferta() { return precioOferta; }
    public void setPrecioOferta(BigDecimal precioOferta) { this.precioOferta = precioOferta; }

    public String getEtiquetaOferta() { return etiquetaOferta; }
    public void setEtiquetaOferta(String etiquetaOferta) { this.etiquetaOferta = etiquetaOferta; }

    public Integer getStockCritico() { return stockCritico; }
    public void setStockCritico(Integer stockCritico) { this.stockCritico = stockCritico; }

    public String getUnidad() { return unidad; }
    public void setUnidad(String unidad) { this.unidad = unidad; }

    public String getImagen() { return imagen; }
    public void setImagen(String imagen) { this.imagen = imagen; }
}
