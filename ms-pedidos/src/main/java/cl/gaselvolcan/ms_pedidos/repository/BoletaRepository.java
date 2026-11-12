package cl.gaselvolcan.ms_pedidos.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import cl.gaselvolcan.ms_pedidos.model.Boleta;

public interface BoletaRepository extends JpaRepository<Boleta, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select b from Boleta b where b.id = :id")
    java.util.Optional<Boleta> bloquear(@org.springframework.data.repository.query.Param("id") Long id);
    @Override
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "detalles")
    java.util.Optional<Boleta> findById(Long id);
    List<Boleta> findByClienteId(Long clienteId);
    List<Boleta> findByRepartidorId(Long repartidorId);
}
