package cl.gaselvolcan.ms_catalogo.service;

import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cl.gaselvolcan.ms_catalogo.dto.*;
import cl.gaselvolcan.ms_catalogo.exception.*;
import cl.gaselvolcan.ms_catalogo.model.*;
import cl.gaselvolcan.ms_catalogo.repository.*;

@Service
@Transactional
public class ProductoService {
    private final ProductoRepository repositorio;
    private final CategoriaRepository categorias;
    private final ValidadorCatalogo validador;

    public ProductoService(ProductoRepository repositorio, CategoriaRepository categorias, ValidadorCatalogo validador) {
        this.repositorio = repositorio;
        this.categorias = categorias;
        this.validador = validador;
    }

    @Transactional(readOnly = true)
    public List<ProductoRespuesta> listar() {
        return repositorio.findAll().stream().map(ProductoRespuesta::desde).toList();
    }

    @Transactional(readOnly = true)
    public ProductoRespuesta buscarPorId(Long id) { return ProductoRespuesta.desde(obtener(id)); }

    // Consulta informativa para ms-pedidos; no reserva ni modifica stock.
    @Transactional(readOnly = true)
    public DisponibilidadRespuesta consultarDisponibilidad(Long id, Integer cantidad) {
        if (cantidad == null || cantidad <= 0) {
            throw new SolicitudInvalidaException("La cantidad debe ser un entero positivo.");
        }
        Producto producto = obtener(id);
        return new DisponibilidadRespuesta(producto.getId(), producto.getCodigo(), cantidad,
                producto.getStock(), producto.getActivo(),
                Boolean.TRUE.equals(producto.getActivo()) && producto.getStock() >= cantidad);
    }

    public ProductoRespuesta crear(ProductoSolicitud datos) {
        validador.validar(datos);
        String codigo = datos.codigo().trim();
        if (repositorio.existsByCodigo(codigo)) throw new SolicitudInvalidaException("Ya existe un producto con ese codigo.");
        Producto producto = new Producto();
        copiar(datos, producto, codigo);
        return guardar(producto);
    }

    public ProductoRespuesta actualizar(Long id, ProductoSolicitud datos) {
        Producto producto = obtener(id);
        validador.validar(datos);
        String codigo = datos.codigo().trim();
        if (repositorio.existsByCodigoAndIdNot(codigo, id)) throw new SolicitudInvalidaException("Ya existe un producto con ese codigo.");
        copiar(datos, producto, codigo);
        return guardar(producto);
    }

    public void eliminar(Long id) {
        Producto producto = obtener(id);
        repositorio.delete(producto);
        repositorio.flush();
    }

    private void copiar(ProductoSolicitud datos, Producto producto, String codigo) {
        Categoria categoria = categorias.findById(datos.categoriaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe una categoria con id " + datos.categoriaId() + "."));
        producto.setCodigo(codigo);
        producto.setNombre(datos.nombre().trim());
        producto.setDescripcion(datos.descripcion());
        producto.setPrecioResidencial(datos.precioResidencial());
        producto.setPrecioComercial(datos.precioComercial());
        producto.setPrecioOferta(datos.precioOferta());
        producto.setEtiquetaOferta(datos.etiquetaOferta());
        producto.setStockCritico(datos.stockCritico());
        producto.setUnidad(datos.unidad().trim());
        producto.setImagen(datos.imagen());
        producto.setStock(datos.stock());
        producto.setActivo(datos.activo());
        producto.setCategoria(categoria);
    }

    private Producto obtener(Long id) {
        validador.validarId(id);
        return repositorio.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("No existe un producto con id " + id + "."));
    }

    private ProductoRespuesta guardar(Producto producto) {
        try { return ProductoRespuesta.desde(repositorio.saveAndFlush(producto)); }
        catch (DataIntegrityViolationException error) {
            // Incluye duplicados concurrentes y cambios concurrentes de la categoria.
            throw new SolicitudInvalidaException("No se pudo guardar el producto. Revisa que el codigo no este duplicado y la categoria exista.");
        }
    }
}
