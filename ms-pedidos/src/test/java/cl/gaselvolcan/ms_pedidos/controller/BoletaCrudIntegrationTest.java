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
@org.springframework.test.context.TestPropertySource(properties = "internal.jwt.secret=clave-interna-sintetica-exclusiva-de-pruebas-123456")
@SpringBootTest(properties = {
        // Clave solo para pruebas (la real va en el .env): la pide la seguridad JWT
        "jwt.secret=clave-solo-para-las-pruebas-de-gas-el-volcan",
        "spring.datasource.url=jdbc:h2:mem:pedidos-api-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never",
        "spring.jpa.show-sql=false"
})
class BoletaCrudIntegrationTest {
    private static final String RUTA = "/api/v1/boletas";
    private static final String DATOS = """
            {"clienteId":10,"direccion":"Direccion 123","comuna":"Comuna","estado":"PENDIENTE",
             "repartidorId":null,"fecha":"2026-01-01T12:00:00","total":0,
             "detalles":[{"productoId":91001,"cantidad":2,"precio":19.99},
                         {"productoId":91002,"cantidad":3,"precio":3.10}]}
            """;
    @MockitoBean private CatalogoClient catalogo;
    @BeforeEach void prepararCatalogo() {
        boletas.deleteAll();
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

    // Casos del CRUD legado: no inventar un descuento para boletas historicas.
    private long crearLegada() {
        var b = new cl.gaselvolcan.ms_pedidos.model.Boleta(10L, "Direccion 123", "Comuna", "PENDIENTE", null,
                java.time.LocalDateTime.of(2026, 1, 1, 12, 0), new BigDecimal("49.28"));
        b.agregarDetalle(new cl.gaselvolcan.ms_pedidos.model.DetalleBoleta(91001L, 2, new BigDecimal("19.99")));
        b.agregarDetalle(new cl.gaselvolcan.ms_pedidos.model.DetalleBoleta(91002L, 3, new BigDecimal("3.10")));
        return boletas.saveAndFlush(b).getId();
    }

    @Test void crudCompletoRecalculaTotalYNoDejaHuerfanos() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(contexto).build();
        long id = crearLegada();
        entityManager.clear();
        assertEquals(0, new BigDecimal("49.28").compareTo(boletas.findById(id).orElseThrow().getTotal()));
        var idsViejos = detalles.findByBoletaId(id).stream().map(d -> d.getId()).toList();
        mvc.perform(get(RUTA + "/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.detalles.length()").value(2));
        mvc.perform(get(RUTA)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        String actualizado = """
                {"clienteId":11,"direccion":"Otra direccion","comuna":"Otra comuna","estado":"PENDIENTE",
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
        mvc.perform(delete(RUTA + "/" + id)).andExpect(status().isMethodNotAllowed());
        entityManager.clear();
        assertTrue(boletas.existsById(id));
        assertEquals(1, detalles.findByBoletaId(id).size());
        mvc.perform(get(RUTA + "/" + id)).andExpect(status().isOk());
    }

    @Test void endpointEstadoPersisteSinRecalcularYRechazaTransicionesInvalidas() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(contexto).build();
        long id = crearLegada();
        entityManager.clear();
        var lineas = detalles.findByBoletaId(id).stream().map(d -> d.getId()).toList();
        clearInvocations(catalogo);
        for (String estado : java.util.List.of("EN_CAMINO", "ENTREGADO")) {
            mvc.perform(put(RUTA + "/" + id + "/estado").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"estado\":\"" + estado + "\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value(estado))
                    .andExpect(jsonPath("$.total").value(49.28));
            entityManager.clear();
            assertEquals(estado, boletas.findById(id).orElseThrow().getEstado());
            assertEquals(lineas, detalles.findByBoletaId(id).stream().map(d -> d.getId()).toList());
        }
        for (String cuerpo : java.util.List.of("{\"estado\":\"PENDIENTE\"}", "{\"estado\":\"DESCONOCIDO\"}", "{}", "{\"estado\":null}")) {
            mvc.perform(put(RUTA + "/" + id + "/estado").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.estado").value(400));
        }
        entityManager.clear();
        assertEquals("ENTREGADO", boletas.findById(id).orElseThrow().getEstado());
        verifyNoInteractions(catalogo);
    }

    @Test void totalDesbordadoNoCreaBoletasParciales() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(contexto).build();
        when(catalogo.consultarProducto(91001L)).thenReturn(new ProductoCatalogoRespuesta(
                91001L, "P91001", true, new BigDecimal("99999999999999999.99"), null));
        long antesBoletas = boletas.count();
        long antesDetalles = detalles.count();
        mvc.perform(post(RUTA).header("Idempotency-Key", java.util.UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content(DATOS.replace("19.99", "99999999999999999.99")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.estado").value(400));
        entityManager.clear();
        assertEquals(antesBoletas, boletas.count());
        assertEquals(antesDetalles, detalles.count());
    }
}
