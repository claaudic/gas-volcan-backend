package cl.gaselvolcan.ms_catalogo.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import cl.gaselvolcan.ms_catalogo.repository.CategoriaRepository;
import cl.gaselvolcan.ms_catalogo.repository.ProductoRepository;

// Prueba real de JPA e inicializador, exclusivamente en H2 efimera.
@SpringBootTest(properties = {
        // Clave solo para pruebas (la real va en el .env): la pide la seguridad JWT
        "jwt.secret=clave-solo-para-las-pruebas-de-gas-el-volcan",
        "spring.datasource.url=jdbc:h2:mem:catalogo-carga-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never",
        "CATALOGO_CARGA_INICIAL_ENABLED=true"
})
class CargaInicialIntegrationTest {
    @Autowired private CategoriaRepository categorias;
    @Autowired private ProductoRepository productos;
    @Autowired private DatosIniciales carga;
    @Autowired private WebApplicationContext contexto;

    @Test
    void cargaAutomaticaNoDuplicaNiSobrescribeYExponeLasRutas() throws Exception {
        assertEquals(4, categorias.count());
        assertEquals(14, productos.count());
        assertEquals(4, productos.findAll().stream().filter(p -> p.getPrecioOferta() != null).count());
        var producto = productos.findByCodigo("CL002").orElseThrow();
        Long id = producto.getId();
        producto.setStock(7);
        producto.setPrecioResidencial(new BigDecimal("12345"));
        producto.setPrecioComercial(new BigDecimal("11234"));
        productos.saveAndFlush(producto);
        carga.run();
        assertEquals(4, categorias.count());
        assertEquals(14, productos.count());
        var conservado = productos.findByCodigo("CL002").orElseThrow();
        assertEquals(id, conservado.getId());
        assertEquals(7, conservado.getStock());
        assertEquals(0, new BigDecimal("12345").compareTo(conservado.getPrecioResidencial()));
        assertEquals(0, new BigDecimal("11234").compareTo(conservado.getPrecioComercial()));
        var mvc = MockMvcBuilders.webAppContextSetup(contexto).build();
        mvc.perform(get("/api/v1/categorias")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));
        mvc.perform(get("/api/v1/productos")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(14));
        mvc.perform(get("/api/v1/productos/" + id + "/disponibilidad").param("cantidad", "7"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.disponible").value(true));
    }
}
