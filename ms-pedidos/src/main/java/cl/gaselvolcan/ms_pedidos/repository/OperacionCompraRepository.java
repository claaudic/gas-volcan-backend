package cl.gaselvolcan.ms_pedidos.repository;

import java.time.Instant;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import cl.gaselvolcan.ms_pedidos.model.OperacionCompra;

public interface OperacionCompraRepository extends JpaRepository<OperacionCompra, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OperacionCompra o where o.id = :id")
    Optional<OperacionCompra> bloquear(@Param("id") String id);
    @Query("select o.id from OperacionCompra o where o.estado in ('REGISTRADA','COTIZADA','COMPENSACION_PENDIENTE') and (o.vence is null or o.vence < :ahora) order by o.ultimaRevision, o.creada")
    List<String> recuperables(@Param("ahora") Instant ahora, org.springframework.data.domain.Pageable limite);
}
