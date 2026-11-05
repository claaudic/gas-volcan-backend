package cl.gaselvolcan.ms_catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import cl.gaselvolcan.ms_catalogo.repositorio.CategoriaRepository;
import cl.gaselvolcan.ms_catalogo.repositorio.ProductoRepository;

// Las propiedades de la prueba prevalecen sobre la conexion MySQL local.
// create-drop solo opera sobre esta base H2 efimera en memoria.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:catalogo-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "catalogo.carga-inicial.enabled=false",
        "catalogo.cors.allowed-origins=http://localhost:5173,http://127.0.0.1:5173"
})
class MsCatalogoApplicationTests {
    @Autowired private CategoriaRepository categorias;
    @Autowired private ProductoRepository productos;

    @Test
    void contextLoadsSinMysqlNiCargaInicial() {
        assertEquals(0, categorias.count());
        assertEquals(0, productos.count());
    }
}
