package cl.gaselvolcan.ms_catalogo.servicio;

import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cl.gaselvolcan.ms_catalogo.dto.*;
import cl.gaselvolcan.ms_catalogo.exception.*;
import cl.gaselvolcan.ms_catalogo.modelo.Categoria;
import cl.gaselvolcan.ms_catalogo.repositorio.*;

@Service
@Transactional
public class CategoriaService {
    private final CategoriaRepository repositorio;
    private final ProductoRepository productos;
    private final ValidadorCatalogo validador;

    public CategoriaService(CategoriaRepository repositorio, ProductoRepository productos, ValidadorCatalogo validador) {
        this.repositorio = repositorio;
        this.productos = productos;
        this.validador = validador;
    }

    @Transactional(readOnly = true)
    public List<CategoriaRespuesta> listar() {
        return repositorio.findAll().stream().map(CategoriaRespuesta::desde).toList();
    }

    @Transactional(readOnly = true)
    public CategoriaRespuesta buscarPorId(Long id) { return CategoriaRespuesta.desde(obtener(id)); }

    public CategoriaRespuesta crear(CategoriaSolicitud datos) {
        validador.validar(datos);
        String nombre = datos.nombre().trim();
        if (repositorio.existsByNombre(nombre)) throw new SolicitudInvalidaException("Ya existe una categoria con ese nombre.");
        return guardar(new Categoria(nombre, datos.descripcion()));
    }

    public CategoriaRespuesta actualizar(Long id, CategoriaSolicitud datos) {
        Categoria categoria = obtener(id);
        validador.validar(datos);
        String nombre = datos.nombre().trim();
        if (repositorio.existsByNombreAndIdNot(nombre, id)) throw new SolicitudInvalidaException("Ya existe una categoria con ese nombre.");
        categoria.setNombre(nombre);
        categoria.setDescripcion(datos.descripcion());
        return guardar(categoria);
    }

    public void eliminar(Long id) {
        Categoria categoria = obtener(id);
        if (productos.existsByCategoriaId(id)) throw new SolicitudInvalidaException("No puedes eliminar una categoria con productos asociados.");
        try {
            repositorio.delete(categoria);
            repositorio.flush();
        } catch (DataIntegrityViolationException error) {
            // La FK protege tambien si se asocia un producto despues de la comprobacion.
            throw new SolicitudInvalidaException("No se puede eliminar la categoria porque tiene datos asociados.");
        }
    }

    private Categoria obtener(Long id) {
        validador.validarId(id);
        return repositorio.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("No existe una categoria con id " + id + "."));
    }

    private CategoriaRespuesta guardar(Categoria categoria) {
        try { return CategoriaRespuesta.desde(repositorio.saveAndFlush(categoria)); }
        catch (DataIntegrityViolationException error) {
            throw new SolicitudInvalidaException("No se pudo guardar la categoria. Revisa que el nombre no este duplicado.");
        }
    }
}
