package cl.gaselvolcan.ms_catalogo.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import cl.gaselvolcan.ms_catalogo.dto.*;
import cl.gaselvolcan.ms_catalogo.model.*;
import cl.gaselvolcan.ms_catalogo.repository.*;
import cl.gaselvolcan.ms_catalogo.exception.*;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = {
        "jwt.secret=" + ProductoConcurrenciaIntegrationTest.CLAVE,
        "internal.jwt.secret=clave-interna-sintetica-concurrencia-123456789",
        "spring.config.import=", "spring.datasource.url=jdbc:h2:mem:producto-admin-concurrencia;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never", "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=cl.gaselvolcan.ms_catalogo.service.ProductoConcurrenciaIntegrationTest$InspectorBloqueo",
        "catalogo.carga-inicial.enabled=false"
})
class ProductoConcurrenciaIntegrationTest {
    static final String CLAVE = "clave-usuarios-sintetica-concurrencia-123456789";
    @Autowired ProductoService servicio;
    @Autowired StockService stock;
    @Autowired ProductoRepository productos;
    @Autowired CategoriaRepository categorias;
    @Autowired OperacionStockRepository operaciones;
    @Autowired WebApplicationContext contexto;
    private Long id, categoriaId;
    private MockMvc mvc;
    private String ruta;

    @BeforeEach void preparar() {
        operaciones.deleteAll(); productos.deleteAll(); categorias.deleteAll();
        var categoria = categorias.saveAndFlush(new Categoria("Prueba", "Categoria original"));
        categoriaId = categoria.getId();
        var p = new Producto("ADMIN-TEST", "Original", "Descripcion original", new BigDecimal("100.00"), new BigDecimal("90.00"), 10, categoria);
        p.setPrecioOferta(new BigDecimal("80.00")); p.setEtiquetaOferta("Oferta"); p.setStockCritico(2);
        p.setUnidad("Kit"); p.setImagen("/img/test.jpg");
        id = productos.saveAndFlush(p).getId();
        ruta = "/api/v1/productos/" + id;
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
    }
    private ProductoRespuesta actual() { return servicio.buscarPorId(id); }
    private ProductoSolicitud datos() {
        return new ProductoSolicitud("ADMIN-TEST", "Editado", "Descripcion editada", new BigDecimal("110.00"),
                new BigDecimal("95.00"), 10, true, categoriaId, new BigDecimal("85.00"), "Oferta editada", 3, "Unidad", "/img/nueva.jpg");
    }
    private DescuentoSolicitud lineas() { return new DescuentoSolicitud(List.of(new DescuentoSolicitud.Linea(id, 2))); }
    private String token(String rol) {
        return "Bearer " + Jwts.builder().subject("admin@example.test").claim("rol", rol).claim("id", 7L)
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(CLAVE.getBytes(StandardCharsets.UTF_8))).compact();
    }
    private String cuerpo(Long version) {
        return """
                {"codigo":"ADMIN-TEST","nombre":"Editado","descripcion":"Descripcion editada",
                "precioResidencial":110,"precioComercial":95,"stock":10,"activo":true,"categoriaId":%d,
                "precioOferta":85,"etiquetaOferta":"Oferta editada","stockCritico":3,"unidad":"Unidad",
                "imagen":"/img/nueva.jpg","versionEsperada":%d}
                """.formatted(categoriaId, version);
    }
    @Test void versionInicialCeroPermiteEdicionYDevuelveVersionPosterior() throws Exception {
        assertEquals(0L, actual().version());
        mvc.perform(get(ruta)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(0));
        mvc.perform(put(ruta).header("Authorization", token("ADMINISTRADOR")).contentType(MediaType.APPLICATION_JSON).content(cuerpo(0L)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.nombre").value("Editado")).andExpect(jsonPath("$.precioOferta").value(85));
        assertEquals(1L, actual().version());
    }
    @Test void edicionHttpObsoletaNoSobrescribeStockNiOtrosCampos() throws Exception {
        var antes = actual();
        stock.descontar(UUID.randomUUID(), lineas());
        var despuesDeCompra = actual();
        assertEquals(8, despuesDeCompra.stock());
        assertTrue(despuesDeCompra.version() > antes.version());
        mvc.perform(put(ruta).header("Authorization", token("ADMINISTRADOR")).contentType(MediaType.APPLICATION_JSON).content(cuerpo(antes.version())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.estado").value(409));
        assertEquals(despuesDeCompra, actual());
    }
    @Test void compensacionTambienInvalidaUnaEdicionAnterior() {
        UUID operacion = UUID.randomUUID(); stock.descontar(operacion, lineas());
        var despuesDeCompra = actual();
        stock.compensar(operacion);
        var compensado = actual();
        assertEquals(10, compensado.stock());
        assertThrows(ConflictoVersionProductoException.class,
                () -> servicio.actualizar(id, new ProductoActualizacionSolicitud(datos(), despuesDeCompra.version())));
        assertEquals(compensado, actual());
    }
    @Test void versionesAusentesNegativasFraccionariasYNoNumericasDevuelven400() throws Exception {
        var antes = actual();
        List<String> cuerpos = new ArrayList<>();
        cuerpos.add(cuerpo(0L).replace(",\"versionEsperada\":0", ""));
        for (String valor : List.of("null", "-1", "0.5", "\"0\"", "\"invalida\"", "true", "9223372036854775808")) {
            cuerpos.add(cuerpo(0L).replace("\"versionEsperada\":0", "\"versionEsperada\":" + valor));
        }
        for (String cuerpo : cuerpos) mvc.perform(put(ruta).header("Authorization", token("ADMINISTRADOR"))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isBadRequest());
        assertEquals(antes, actual());
    }
    @Test void servicioTambienExigeVersionValida() {
        for (Long version : Arrays.asList(null, -1L)) assertThrows(SolicitudInvalidaException.class,
                () -> servicio.actualizar(id, new ProductoActualizacionSolicitud(datos(), version)));
    }
    @Test void estadoSoloCambiaActivoYVersion() throws Exception {
        var antes = actual();
        mvc.perform(put(ruta + "/estado").header("Authorization", token("ADMINISTRADOR"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false,\"stock\":999}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(false)).andExpect(jsonPath("$.version").value(1));
        var despues = actual();
        assertEquals(sinEstado(antes), sinEstado(despues));
        assertFalse(despues.activo());
        assertTrue(despues.version() > antes.version());
        var repetido = servicio.cambiarEstado(id, new EstadoProductoSolicitud(false));
        assertEquals(despues, repetido);
    }
    private List<Object> sinEstado(ProductoRespuesta p) {
        return Arrays.asList(p.id(), p.codigo(), p.nombre(), p.descripcion(), p.precioResidencial(), p.precioComercial(),
                p.stock(), p.categoria(), p.precioOferta(), p.etiquetaOferta(), p.stockCritico(), p.unidad(), p.imagen());
    }
    @Test void estadoAusenteONuloEs400YNoCambiaElProducto() throws Exception {
        var antes = actual();
        for (String cuerpo : List.of("{}", "{\"activo\":null}", "{\"activo\":{}}")) {
            mvc.perform(put(ruta + "/estado").header("Authorization", token("ADMINISTRADOR"))
                    .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isBadRequest());
        }
        assertEquals(antes, actual());
    }
    @Test void ambosPutsExigenAdministradorYJwtValido() throws Exception {
        for (String sufijo : List.of("", "/estado")) {
            String cuerpo = sufijo.isEmpty() ? cuerpo(0L) : "{\"activo\":false}";
            for (String auth : List.of("", "Bearer invalido")) {
                mvc.perform(put(ruta + sufijo).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                        .andExpect(status().isUnauthorized());
            }
            for (String rol : List.of("CLIENTE", "DESPACHADORA", "REPARTIDOR")) {
                mvc.perform(put(ruta + sufijo).header("Authorization", token(rol)).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                        .andExpect(status().isForbidden());
            }
        }
        assertEquals(0L, actual().version()); assertTrue(actual().activo());
    }
    @Test void idsInvalidosSon400EInexistentes404() throws Exception {
        for (String sufijo : List.of("", "/estado")) {
            String cuerpo = sufijo.isEmpty() ? cuerpo(0L) : "{\"activo\":false}";
            for (String identificador : List.of("0", "-1", "abc")) mvc.perform(put("/api/v1/productos/" + identificador + sufijo)
                    .header("Authorization", token("ADMINISTRADOR")).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isBadRequest());
            mvc.perform(put("/api/v1/productos/9223372036854775807" + sufijo).header("Authorization", token("ADMINISTRADOR"))
                    .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isNotFound());
        }
    }
    @Test void creacionNoExigeVersionYContratoGetLaExpone() throws Exception {
        String nuevo = cuerpo(0L).replace("ADMIN-TEST", "NUEVO").replace(",\"versionEsperada\":0", "");
        mvc.perform(post("/api/v1/productos").header("Authorization", token("ADMINISTRADOR"))
                .contentType(MediaType.APPLICATION_JSON).content(nuevo)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(0));
    }

    private static class Barrera {
        volatile Thread hilo;
        final CountDownLatch bloqueado = new CountDownLatch(1);
        final CountDownLatch liberar = new CountDownLatch(1);
    }
    public static class InspectorBloqueo implements StatementInspector {
        private static volatile Barrera barrera;
        @Override public String inspect(String sql) {
            Barrera actual = barrera;
            // La consulta FOR UPDATE ya adquirio el bloqueo antes de este UPDATE.
            if (actual != null && actual.hilo == Thread.currentThread() && sql.toLowerCase(Locale.ROOT).startsWith("update productos ")) {
                actual.bloqueado.countDown();
                try { if (!actual.liberar.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Barrera agotada"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }
            return sql;
        }
    }
    // Pausar dentro de la transaccion real, con bloqueo adquirido; nunca usar sleeps.
    private <A, B> B ordenadas(Supplier<A> primera, Supplier<B> segunda) throws Exception {
        var ejecutor = Executors.newFixedThreadPool(2);
        var barrera = new Barrera(); var iniciada = new CountDownLatch(1);
        InspectorBloqueo.barrera = barrera;
        try {
            var a = ejecutor.submit(() -> { barrera.hilo = Thread.currentThread(); return primera.get(); });
            assertTrue(barrera.bloqueado.await(10, TimeUnit.SECONDS));
            var b = ejecutor.submit(() -> { iniciada.countDown(); return segunda.get(); });
            assertTrue(iniciada.await(10, TimeUnit.SECONDS));
            assertFalse(a.isDone());
            barrera.liberar.countDown();
            a.get(20, TimeUnit.SECONDS);
            return b.get(20, TimeUnit.SECONDS);
        } finally { barrera.liberar.countDown(); ejecutor.shutdownNow(); InspectorBloqueo.barrera = null; }
    }
    @Test void descuentoAntesDeDesactivarConservaStockDescontado() throws Exception {
        ordenadas(() -> stock.descontar(UUID.randomUUID(), lineas()), () -> servicio.cambiarEstado(id, new EstadoProductoSolicitud(false)));
        assertEquals(8, actual().stock()); assertFalse(actual().activo());
    }
    @Test void desactivarAntesDelDescuentoImpideLaCompra() throws Exception {
        var resultado = ordenadas(() -> servicio.cambiarEstado(id, new EstadoProductoSolicitud(false)),
                () -> stock.descontar(UUID.randomUUID(), lineas()));
        assertEquals("RECHAZADO", resultado.estado()); assertEquals(10, actual().stock()); assertFalse(actual().activo());
    }
    @Test void compensacionAntesDelEstadoConservaStockRestituido() throws Exception {
        UUID operacion = UUID.randomUUID(); stock.descontar(operacion, lineas());
        ordenadas(() -> stock.compensar(operacion), () -> servicio.cambiarEstado(id, new EstadoProductoSolicitud(false)));
        assertEquals(10, actual().stock()); assertFalse(actual().activo());
    }
    @Test void desactivarAntesDeCompensarPermiteRestituirStock() throws Exception {
        UUID operacion = UUID.randomUUID(); stock.descontar(operacion, lineas());
        var resultado = ordenadas(() -> servicio.cambiarEstado(id, new EstadoProductoSolicitud(false)), () -> stock.compensar(operacion));
        assertEquals("COMPENSADO", resultado.estado()); assertEquals(10, actual().stock()); assertFalse(actual().activo());
    }
}
