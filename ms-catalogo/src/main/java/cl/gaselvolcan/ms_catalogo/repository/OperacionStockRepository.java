package cl.gaselvolcan.ms_catalogo.repository;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import cl.gaselvolcan.ms_catalogo.model.OperacionStock;

public interface OperacionStockRepository extends JpaRepository<OperacionStock, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OperacionStock o where o.id = :id")
    Optional<OperacionStock> bloquear(@Param("id") String id);
}
