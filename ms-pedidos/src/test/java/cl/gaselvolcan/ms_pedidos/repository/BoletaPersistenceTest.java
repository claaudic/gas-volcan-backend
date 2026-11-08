package cl.gaselvolcan.ms_pedidos.repository;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import javax.sql.DataSource;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import cl.gaselvolcan.ms_pedidos.model.Boleta;
import cl.gaselvolcan.ms_pedidos.model.DetalleBoleta;

// Toda escritura queda en H2 en memoria; cada prueba revierte su transaccion.
@SpringBootTest(properties = {
        // Clave solo para pruebas (la real va en el .env): la pide la seguridad JWT
        "jwt.secret=clave-solo-para-las-pruebas-de-gas-el-volcan",
        "spring.datasource.url=jdbc:h2:mem:pedidos-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never",
        "spring.jpa.show-sql=false"
})
@Transactional
class BoletaPersistenceTest {
    @Autowired private BoletaRepository boletas;
    @Autowired private DetalleBoletaRepository detalles;
    @Autowired private EntityManager entityManager;
    @Autowired private DataSource dataSource;

    private Boleta guardar() {
        var boleta = new Boleta(10L, "Direccion de prueba 123", "Comuna de prueba",
                "ESTADO_PRUEBA", null, LocalDateTime.of(2026, 1, 1, 12, 0), new BigDecimal("25.50"));
        boleta.agregarDetalle(new DetalleBoleta(91001L, 2, new BigDecimal("10.00")));
        boleta.agregarDetalle(new DetalleBoleta(91002L, 1, new BigDecimal("5.50")));
        return boletas.saveAndFlush(boleta);
    }

    @Test void persisteYConsultaDosProductosSinUsuariosNiCatalogo() {
        var id = guardar().getId();
        entityManager.clear();
        var boleta = boletas.findById(id).orElseThrow();
        assertEquals(10L, boleta.getClienteId());
        assertNull(boleta.getRepartidorId());
        assertEquals(2, boleta.getDetalles().size());
        assertEquals(0, new BigDecimal("25.50").compareTo(boleta.getTotal()));
        assertEquals(2, detalles.findByBoletaId(id).size());
        var linea = boleta.getDetalles().stream().filter(d -> d.getProductoId().equals(91002L)).findFirst().orElseThrow();
        assertNotNull(linea.getId());
        assertEquals(1, linea.getCantidad());
        assertEquals(0, new BigDecimal("5.50").compareTo(linea.getPrecio()));
        assertEquals(1, boletas.findByClienteId(10L).size());
        assertTrue(boletas.findByClienteId(99L).isEmpty());
    }

    @Test void fkSoloApuntaABoletaYLosImportesSonDecimales() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertTrue(connection.getMetaData().getURL().startsWith("jdbc:h2:mem:"));
            try (var keys = connection.getMetaData().getImportedKeys(null, "PUBLIC", "DETALLES_BOLETA")) {
                assertTrue(keys.next());
                assertEquals("BOLETA_ID", keys.getString("FKCOLUMN_NAME"));
                assertEquals("BOLETAS", keys.getString("PKTABLE_NAME"));
                assertFalse(keys.next());
            }
            try (var keys = connection.getMetaData().getImportedKeys(null, "PUBLIC", "BOLETAS")) {
                assertFalse(keys.next());
            }
            try (var columns = connection.getMetaData().getColumns(null, "PUBLIC", "DETALLES_BOLETA", "PRECIO")) {
                assertTrue(columns.next());
                assertEquals(19, columns.getInt("COLUMN_SIZE"));
                assertEquals(2, columns.getInt("DECIMAL_DIGITS"));
            }
        }
    }

    @Test void quitarDetalleEliminaSoloLaLineaHuerfana() {
        var boleta = guardar();
        var linea = boleta.getDetalles().getFirst();
        var idDetalle = linea.getId();
        boleta.quitarDetalle(linea);
        boletas.flush();
        entityManager.clear();
        assertFalse(detalles.existsById(idDetalle));
        assertEquals(1, boletas.findById(boleta.getId()).orElseThrow().getDetalles().size());
    }

    @Test void eliminarBoletaNoDejaDetallesHuerfanos() {
        var boleta = guardar();
        var id = boleta.getId();
        boletas.delete(boleta);
        boletas.flush();
        entityManager.clear();
        assertFalse(boletas.existsById(id));
        assertTrue(detalles.findByBoletaId(id).isEmpty());
    }
}
