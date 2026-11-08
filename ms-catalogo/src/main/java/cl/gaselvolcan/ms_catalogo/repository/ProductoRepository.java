package cl.gaselvolcan.ms_catalogo.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import cl.gaselvolcan.ms_catalogo.model.Producto;

public interface ProductoRepository extends JpaRepository<Producto, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Producto p where p.id = :id")
    Optional<Producto> bloquear(@org.springframework.data.repository.query.Param("id") Long id);
    Optional<Producto> findByCodigo(String codigo);
    List<Producto> findByNombre(String nombre);
    boolean existsByCodigo(String codigo);
    boolean existsByCodigoAndIdNot(String codigo, Long id);
    boolean existsByCategoriaId(Long categoriaId);
    List<Producto> findByCategoriaId(Long categoriaId);
}
