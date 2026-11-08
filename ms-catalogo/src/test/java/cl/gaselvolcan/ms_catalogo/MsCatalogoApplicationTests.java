package cl.gaselvolcan.ms_catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import cl.gaselvolcan.ms_catalogo.repository.CategoriaRepository;
import cl.gaselvolcan.ms_catalogo.repository.ProductoRepository;

// Las propiedades de la prueba prevalecen sobre la conexion MySQL local.
// create-drop solo opera sobre esta base H2 efimera en memoria.
@org.springframework.test.context.TestPropertySource(properties = "internal.jwt.secret=clave-interna-sintetica-exclusiva-de-pruebas-123456")
@SpringBootTest(properties = {
        // Clave solo para pruebas (la real va en el .env): la pide la seguridad JWT
        "jwt.secret=clave-solo-para-las-pruebas-de-gas-el-volcan",
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
    @Autowired private WebApplicationContext contexto;
    @Autowired private CategoriaRepository categorias;
    @Autowired private ProductoRepository productos;

    @Test
    void contextLoadsSinMysqlNiCargaInicial() {
        assertEquals(0, categorias.count());
        assertEquals(0, productos.count());
    }
    @Test
    void swaggerYOpenApiFuncionanConLosPaquetesNuevos() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(contexto).build();
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/categorias'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/productos'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/productos/{id}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/v1/productos/{id}'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/v1/productos/{id}/disponibilidad'].get").exists())
                .andExpect(jsonPath("$.components.schemas.ProductoRespuesta").exists());
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/swagger-ui/swagger-ui-bundle.js")).andExpect(status().isOk());
    }
}
