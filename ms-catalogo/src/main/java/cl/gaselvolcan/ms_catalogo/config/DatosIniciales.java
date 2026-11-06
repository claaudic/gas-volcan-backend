package cl.gaselvolcan.ms_catalogo.config;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashSet;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import cl.gaselvolcan.ms_catalogo.dto.ProductoSolicitud;
import cl.gaselvolcan.ms_catalogo.model.Categoria;
import cl.gaselvolcan.ms_catalogo.repository.CategoriaRepository;
import cl.gaselvolcan.ms_catalogo.repository.ProductoRepository;
import cl.gaselvolcan.ms_catalogo.service.ProductoService;
import cl.gaselvolcan.ms_catalogo.service.ValidadorCatalogo;

// Carga automatica de faltantes; desactivar con catalogo.carga-inicial.enabled=false.
@Component
@ConditionalOnProperty(name = "catalogo.carga-inicial.enabled", havingValue = "true", matchIfMissing = true)
public class DatosIniciales implements CommandLineRunner {
    private final CategoriaRepository categorias;
    private final ProductoRepository productos;
    private final ProductoService servicio;
    private final ValidadorCatalogo validador;

    public DatosIniciales(CategoriaRepository categorias, ProductoRepository productos,
            ProductoService servicio, ValidadorCatalogo validador) {
        this.categorias = categorias;
        this.productos = productos;
        this.servicio = servicio;
        this.validador = validador;
    }

    public record ProductoInicial(Long id, String codigo, String categoria, String nombre,
            String descripcion, String unidad, BigDecimal precioResidencial, BigDecimal precioComercial,
            Integer stock, Integer stockCritico, String imagen, Boolean activo,
            BigDecimal precioOferta, String etiquetaOferta) {
        public ProductoSolicitud solicitud(Long categoriaId) {
            return new ProductoSolicitud(codigo, nombre, descripcion, precioResidencial, precioComercial,
                    stock, activo, categoriaId, precioOferta, etiquetaOferta, stockCritico, unidad, imagen);
        }
    }

    public static ProductoInicial[] leerCatalogo() throws IOException {
        try (var entrada = new ClassPathResource("catalogo-original.json").getInputStream()) {
            return JsonMapper.builder().build().readValue(entrada, ProductoInicial[].class);
        }
    }

    @Override
    @Transactional
    public void run(String... args) throws IOException {
        var catalogo = leerCatalogo();
        var codigos = new HashSet<String>();
        // Validar todo antes de realizar la primera escritura.
        for (var producto : catalogo) {
            validador.validar(producto.solicitud(1L));
            if (producto.categoria() == null || producto.categoria().isBlank()
                    || producto.categoria().length() > 255 || !codigos.add(producto.codigo())) {
                throw new IllegalStateException("Catalogo inicial inconsistente.");
            }
        }
        for (var producto : catalogo) {
            var categoria = categorias.findByNombre(producto.categoria())
                    .orElseGet(() -> categorias.saveAndFlush(new Categoria(producto.categoria(), null)));
            // No se actualizan registros existentes, incluidos precios, stock y ofertas.
            if (!productos.existsByCodigo(producto.codigo())) {
                servicio.crear(producto.solicitud(categoria.getId()));
            }
        }
    }
}
