package cl.gaselvolcan.ms_pedidos.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.math.BigDecimal;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.mockito.Mockito.*;
import cl.gaselvolcan.ms_pedidos.client.CatalogoClient;
import cl.gaselvolcan.ms_pedidos.dto.catalogo.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import cl.gaselvolcan.ms_pedidos.repository.BoletaRepository;
import cl.gaselvolcan.ms_pedidos.repository.DetalleBoletaRepository;

// HTTP simulado + servicio + JPA reales, todo aislado en H2 en memoria.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:pedidos-api-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never",
        "spring.jpa.show-sql=false"
})
@Transactional
class BoletaCrudIntegrationTest {
    private static final String RUTA = "/api/v1/boletas";
    private static final String DATOS = """
            {"clienteId":10,"direccion":"Direccion 123","comuna":"Comuna","estado":"ESTADO_PRUEBA",
             "repartidorId":null,"fecha":"2026-01-01T12:00:00","total":0,
             "detalles":[{"productoId":91001,"cantidad":2,"precio":19.99},
                         {"productoId":91002,"cantidad":3,"precio":3.10}]}
            """;
    @MockitoBean private CatalogoClient catalogo;
    @BeforeEach void prepararCatalogo() {
        when(catalogo.consultarProducto(anyLong())).thenAnswer(c -> {
            Long id = c.getArgument(0);
            BigDecimal precio = id == 91001L ? new BigDecimal("19.99") : id == 91002L ? new BigDecimal("3.10") : new BigDecimal("4.00");
            return new ProductoCatalogoRespuesta(id, "P" + id, true, precio, null);
        });
        when(catalogo.consultarDisponibilidad(anyLong(), anyInt())).thenAnswer(c ->
                new DisponibilidadCatalogoRespuesta(c.getArgument(0), "P" + c.getArgument(0), c.getArgument(1), 100, true, true));
    }
    @Autowired private WebApplicationContext contexto;
    @Autowired private BoletaRepository boletas;
    @Autowired private DetalleBoletaRepository detalles;
    @Autowired private EntityManager entityManager;

    @Test void crudCompletoRecalculaTotalYNoDejaHuerfanos() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(contexto).build();
        var resultado = mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(DATOS.replace("19.99", "0")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.total").value(49.28))
                .andExpect(jsonPath("$.detalles.length()").value(2)).andReturn();
        long id = JsonMapper.builder().build().readTree(resultado.getResponse().getContentAsString()).get("id").asLong();
        entityManager.clear();
        assertEquals(0, new BigDecimal("49.28").compareTo(boletas.findById(id).orElseThrow().getTotal()));
        var idsViejos = detalles.findByBoletaId(id).stream().map(d -> d.getId()).toList();
        mvc.perform(get(RUTA + "/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.detalles.length()").value(2));
        mvc.perform(get(RUTA)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        String actualizado = """
                {"clienteId":11,"direccion":"Otra direccion","comuna":"Otra comuna","estado":"OTRO_ESTADO",
                 "repartidorId":20,"fecha":"2026-02-01T10:00:00","total":999999,
                 "detalles":[{"productoId":91003,"cantidad":3,"precio":4.00}]}
                """;
        mvc.perform(put(RUTA + "/" + id).contentType(MediaType.APPLICATION_JSON).content(actualizado))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.total").value(12.0)).andExpect(jsonPath("$.detalles.length()").value(1))
                .andExpect(jsonPath("$.repartidorId").value(20));
        entityManager.clear();
        assertEquals(1, detalles.findByBoletaId(id).size());
        for (Long anterior : idsViejos) assertFalse(detalles.existsById(anterior));
        mvc.perform(delete(RUTA + "/" + id)).andExpect(status().isNoContent());
        entityManager.clear();
        assertFalse(boletas.existsById(id));
        assertTrue(detalles.findByBoletaId(id).isEmpty());
        mvc.perform(get(RUTA + "/" + id)).andExpect(status().isNotFound()).andExpect(jsonPath("$.estado").value(404));
    }

    @Test void totalDesbordadoNoCreaBoletasParciales() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(contexto).build();
        when(catalogo.consultarProducto(91001L)).thenReturn(new ProductoCatalogoRespuesta(
                91001L, "P91001", true, new BigDecimal("99999999999999999.99"), null));
        long antesBoletas = boletas.count();
        long antesDetalles = detalles.count();
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                        .content(DATOS.replace("19.99", "99999999999999999.99")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.estado").value(400));
        entityManager.clear();
        assertEquals(antesBoletas, boletas.count());
        assertEquals(antesDetalles, detalles.count());
    }
}
