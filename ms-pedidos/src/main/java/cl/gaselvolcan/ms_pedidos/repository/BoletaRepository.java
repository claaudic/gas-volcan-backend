package cl.gaselvolcan.ms_pedidos.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import cl.gaselvolcan.ms_pedidos.model.Boleta;

public interface BoletaRepository extends JpaRepository<Boleta, Long> {
    @Override
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "detalles")
    java.util.Optional<Boleta> findById(Long id);
    List<Boleta> findByClienteId(Long clienteId);
    List<Boleta> findByRepartidorId(Long repartidorId);
}
