package cl.gaselvolcan.ms_catalogo.repositorio;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import cl.gaselvolcan.ms_catalogo.modelo.Producto;

public interface ProductoRepository extends JpaRepository<Producto, Long> {
    Optional<Producto> findByCodigo(String codigo);
    List<Producto> findByNombre(String nombre);
    boolean existsByCodigo(String codigo);
    boolean existsByCodigoAndIdNot(String codigo, Long id);
    boolean existsByCategoriaId(Long categoriaId);
    List<Producto> findByCategoriaId(Long categoriaId);
}
