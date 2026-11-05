package cl.gaselvolcan.ms_catalogo.repositorio;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import cl.gaselvolcan.ms_catalogo.modelo.Categoria;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {
    Optional<Categoria> findByNombre(String nombre);
    boolean existsByNombre(String nombre);
    boolean existsByNombreAndIdNot(String nombre, Long id);
}
