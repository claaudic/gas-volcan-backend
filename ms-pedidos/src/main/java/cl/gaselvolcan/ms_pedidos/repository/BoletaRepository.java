package cl.gaselvolcan.ms_pedidos.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import cl.gaselvolcan.ms_pedidos.model.Boleta;

public interface BoletaRepository extends JpaRepository<Boleta, Long> {
    List<Boleta> findByClienteId(Long clienteId);
}
