package cl.gaselvolcan.ms_catalogo.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import cl.gaselvolcan.ms_catalogo.modelo.Producto;

@Schema(description = "Datos devueltos por la API")
public record ProductoRespuesta(@Schema(description = "ID persistente generado por MySQL") Long id, @Schema(description = "Codigo unico del producto") String codigo, @Schema(description = "Nombre del recurso") String nombre, @Schema(description = "Descripcion opcional") String descripcion,
        @Schema(description = "Precio base residencial en CLP; no negativo; hasta 17 enteros y 2 decimales") BigDecimal precioResidencial, @Schema(description = "Precio base comercial en CLP; no negativo; hasta 17 enteros y 2 decimales") BigDecimal precioComercial, @Schema(description = "Existencias; entero no negativo") Integer stock, @Schema(description = "Habilitado para venta") Boolean activo,
        @Schema(description = "Categoria asociada") CategoriaRespuesta categoria, @Schema(description = "Precio de oferta en CLP; nulo indica sin oferta; hasta 17 enteros y 2 decimales") BigDecimal precioOferta, @Schema(description = "Texto opcional de la oferta") String etiquetaOferta,
        @Schema(description = "Umbral opcional; entero no negativo") Integer stockCritico, @Schema(description = "Unidad de venta obligatoria, por ejemplo Unidad o Kit") String unidad, @Schema(description = "Ruta opcional de imagen; permite cadena vacia") String imagen) {
    public ProductoRespuesta(Long id, String codigo, String nombre, String descripcion,
            BigDecimal precioResidencial, BigDecimal precioComercial, Integer stock,
            Boolean activo, CategoriaRespuesta categoria) {
        this(id, codigo, nombre, descripcion, precioResidencial, precioComercial, stock,
                activo, categoria, null, null, null, "Unidad", "");
    }
    public static ProductoRespuesta desde(Producto producto) {
        return new ProductoRespuesta(producto.getId(), producto.getCodigo(), producto.getNombre(),
                producto.getDescripcion(), producto.getPrecioResidencial(), producto.getPrecioComercial(),
                producto.getStock(), producto.getActivo(), CategoriaRespuesta.desde(producto.getCategoria()),
                producto.getPrecioOferta(), producto.getEtiquetaOferta(), producto.getStockCritico(),
                producto.getUnidad(), producto.getImagen());
    }
}
