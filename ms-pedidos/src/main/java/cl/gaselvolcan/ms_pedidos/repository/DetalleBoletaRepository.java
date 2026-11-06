package cl.gaselvolcan.ms_pedidos.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import cl.gaselvolcan.ms_pedidos.model.DetalleBoleta;

public interface DetalleBoletaRepository extends JpaRepository<DetalleBoleta, Long> {
    List<DetalleBoleta> findByBoletaId(Long boletaId);
}
