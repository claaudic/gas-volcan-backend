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
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = {
        "jwt.secret=" + StockIntegrationTest.USUARIOS,
        "internal.jwt.secret=" + StockIntegrationTest.INTERNA,
        "spring.datasource.url=jdbc:h2:mem:stock-operaciones;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never", "catalogo.carga-inicial.enabled=false", "spring.jpa.show-sql=false"
})
class StockIntegrationTest {
    static final String USUARIOS = "clave-usuarios-sintetica-para-tests-de-stock-123456";
    static final String INTERNA = "clave-interna-sintetica-para-tests-de-stock-123456";
    private static final String RUTA = "/internal/v1/stock/descuentos";
    @Autowired StockService servicio;
    @Autowired ProductoRepository productos;
    @Autowired OperacionStockRepository operaciones;
    @Autowired CategoriaRepository categorias;
    @Autowired WebApplicationContext contexto;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    private Long uno, dos;
    private MockMvc mvc;

    @BeforeEach void preparar() {
        operaciones.deleteAll(); productos.deleteAll(); categorias.deleteAll();
        var categoria = categorias.saveAndFlush(new Categoria("Stock", null));
        uno = productos.saveAndFlush(new Producto("A", "Uno", null, BigDecimal.TEN, BigDecimal.TEN, 10, categoria)).getId();
        dos = productos.saveAndFlush(new Producto("B", "Dos", null, BigDecimal.TEN, BigDecimal.TEN, 5, categoria)).getId();
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
    }
    private DescuentoSolicitud solicitud(int a, int b) {
        return new DescuentoSolicitud(List.of(new DescuentoSolicitud.Linea(uno, a), new DescuentoSolicitud.Linea(dos, b)));
    }
    private int stock(Long id) { return productos.findById(id).orElseThrow().getStock(); }
    private String token(String scope) {
        return "Bearer " + Jwts.builder().issuer("ms-pedidos").subject("ms-pedidos")
                .audience().add("ms-catalogo").and().issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(60))).claim("scope", scope)
                .signWith(Keys.hmacShaKeyFor(INTERNA.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256).compact();
    }
    private String cuerpo(int a, int b) {
        return "{\"productos\":[{\"productoId\":" + uno + ",\"cantidad\":" + a
                + "},{\"productoId\":" + dos + ",\"cantidad\":" + b + "}]}";
    }

    @Test void multiproductoAcumulaRepetidosYPersisteResultado() {
        UUID id = UUID.randomUUID();
        var entrada = new DescuentoSolicitud(List.of(new DescuentoSolicitud.Linea(dos, 2),
                new DescuentoSolicitud.Linea(uno, 1), new DescuentoSolicitud.Linea(uno, 2)));
        assertEquals("APLICADO", servicio.descontar(id, entrada).estado());
        assertEquals(7, stock(uno)); assertEquals(3, stock(dos));
        var resultado = servicio.consultar(id);
        assertEquals(2, resultado.productos().size()); assertNotNull(resultado.creada());
        assertEquals(resultado, servicio.descontar(id, solicitud(3, 2)));
        assertEquals(7, stock(uno)); assertEquals(1, operaciones.count());
    }

    @Test void faltaDeStockNoDescuentaNingunProductoYElRechazoEsDurable() {
        UUID id = UUID.randomUUID();
        assertEquals(409, servicio.descontar(id, solicitud(2, 6)).codigoResultado());
        assertEquals("RECHAZADO", servicio.consultar(id).estado());
        assertEquals(10, stock(uno)); assertEquals(5, stock(dos));
        var p = productos.findById(dos).orElseThrow(); p.setStock(20); productos.saveAndFlush(p);
        assertEquals(409, servicio.descontar(id, solicitud(2, 6)).codigoResultado());
        assertEquals(20, stock(dos));
        assertThrows(ConflictoStockException.class, () -> servicio.compensar(id));
    }

    @Test void inactivoOInexistenteNoProduceDescuentoParcial() {
        var p = productos.findById(dos).orElseThrow(); p.setActivo(false); productos.saveAndFlush(p);
        assertEquals(400, servicio.descontar(UUID.randomUUID(), solicitud(1, 1)).codigoResultado());
        var entrada = new DescuentoSolicitud(List.of(new DescuentoSolicitud.Linea(uno, 1),
                new DescuentoSolicitud.Linea(Long.MAX_VALUE, 1)));
        assertEquals(404, servicio.descontar(UUID.randomUUID(), entrada).codigoResultado());
        assertEquals(10, stock(uno)); assertEquals(5, stock(dos));
    }

    @Test void claveConOtroContenidoEsConflicto() {
        UUID id = UUID.randomUUID(); servicio.descontar(id, solicitud(1, 1));
        assertThrows(ConflictoStockException.class, () -> servicio.descontar(id, solicitud(2, 1)));
        assertEquals(9, stock(uno)); assertEquals(4, stock(dos));
    }

    @Test void compensacionEsIdempotenteYNoSeReactivaConReintento() {
        UUID id = UUID.randomUUID(); servicio.descontar(id, solicitud(2, 3));
        var primera = servicio.compensar(id);
        assertEquals("COMPENSADO", primera.estado()); assertNotNull(primera.compensada());
        assertEquals(primera, servicio.compensar(id));
        assertEquals(primera, servicio.descontar(id, solicitud(2, 3)));
        assertEquals(10, stock(uno)); assertEquals(5, stock(dos));
    }

    @Test void compensacionConProductoEliminadoNoRestituyeParcialmente() {
        UUID id = UUID.randomUUID(); servicio.descontar(id, solicitud(2, 2));
        productos.deleteById(dos);
        assertThrows(ConflictoStockException.class, () -> servicio.compensar(id));
        assertEquals(8, stock(uno)); assertEquals("APLICADO", servicio.consultar(id).estado());
    }

    @Test void compensacionNoDesbordaStockNiLoRestituyeParcialmente() {
        UUID id = UUID.randomUUID(); servicio.descontar(id, solicitud(2, 2));
        var p = productos.findById(dos).orElseThrow(); p.setStock(Integer.MAX_VALUE); productos.saveAndFlush(p);
        assertThrows(ConflictoStockException.class, () -> servicio.compensar(id));
        assertEquals(8, stock(uno)); assertEquals("APLICADO", servicio.consultar(id).estado());
    }

    private <T> List<T> simultaneas(Callable<T> a, Callable<T> b) throws Exception {
        var ejecutor = Executors.newFixedThreadPool(2);
        var listos = new CountDownLatch(2); var inicio = new CountDownLatch(1);
        try {
            var tareas = List.of(a, b).stream().map(t -> ejecutor.submit(() -> {
                listos.countDown(); if (!inicio.await(10, TimeUnit.SECONDS)) throw new IllegalStateException();
                return t.call();
            })).toList();
            assertTrue(listos.await(10, TimeUnit.SECONDS)); inicio.countDown();
            return List.of(tareas.get(0).get(20, TimeUnit.SECONDS), tareas.get(1).get(20, TimeUnit.SECONDS));
        } finally { inicio.countDown(); ejecutor.shutdownNow(); }
    }

    @Test void comprasSimultaneasNoDejanStockNegativo() throws Exception {
        var resultados = simultaneas(() -> servicio.descontar(UUID.randomUUID(), solicitud(7, 3)),
                () -> servicio.descontar(UUID.randomUUID(), solicitud(7, 3)));
        assertEquals(Set.of("APLICADO", "RECHAZADO"), new HashSet<>(resultados.stream().map(DescuentoRespuesta::estado).toList()));
        assertEquals(3, stock(uno)); assertEquals(2, stock(dos));
    }

    @Test void reintentosSimultaneosDescuentanUnaSolaVez() throws Exception {
        UUID id = UUID.randomUUID();
        var r = simultaneas(() -> servicio.descontar(id, solicitud(2, 2)), () -> servicio.descontar(id, solicitud(2, 2)));
        assertEquals(r.get(0), r.get(1)); assertEquals(1, operaciones.count());
        assertEquals(8, stock(uno)); assertEquals(3, stock(dos));
    }

    @Test void compensacionesSimultaneasRestituyenUnaSolaVez() throws Exception {
        UUID id = UUID.randomUUID(); servicio.descontar(id, solicitud(2, 2));
        var r = simultaneas(() -> servicio.compensar(id), () -> servicio.compensar(id));
        assertEquals(r.get(0), r.get(1)); assertEquals(10, stock(uno)); assertEquals(5, stock(dos));
    }

    @Test void edicionAdministrativaObsoletaNoPisaElDescuento() {
        var anterior = productos.findById(uno).orElseThrow();
        servicio.descontar(UUID.randomUUID(), solicitud(2, 1));
        anterior.setStock(100);
        assertThrows(org.springframework.dao.OptimisticLockingFailureException.class, () -> productos.saveAndFlush(anterior));
        assertEquals(8, stock(uno));
    }

    @Test void falloDeBaseAlGuardarRevierteProductosYRegistroCompleto() {
        UUID id = UUID.randomUUID();
        // Restriccion exclusiva de esta BD efimera: falla el flush del segundo producto.
        jdbc.execute("alter table productos add constraint prueba_rollback check (id <> " + dos + " or stock >= 5)");
        try {
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> servicio.descontar(id, solicitud(2, 2)));
            assertEquals(10, stock(uno)); assertEquals(5, stock(dos));
            assertFalse(operaciones.existsById(id.toString()));
        } finally {
            jdbc.execute("alter table productos drop constraint prueba_rollback");
        }
        assertEquals("APLICADO", servicio.descontar(id, solicitud(2, 2)).estado());
        assertEquals(8, stock(uno)); assertEquals(3, stock(dos));
    }

    @Test void httpRegistraYConsultaRechazosSinMutarStock() throws Exception {
        String auth = token("stock:descontar stock:consultar stock:compensar");
        UUID id = UUID.randomUUID();
        mvc.perform(post(RUTA).header("Authorization", auth).header("Idempotency-Key", id)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo(2, 6)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.estado").value("RECHAZADO"));
        mvc.perform(get(RUTA + "/" + id).header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.codigoResultado").value(409));
        mvc.perform(put(RUTA + "/" + id + "/compensacion").header("Authorization", auth))
                .andExpect(status().isConflict());
        mvc.perform(post(RUTA).header("Authorization", auth).header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"productos\":[{\"productoId\":9223372036854775807,\"cantidad\":1}]}"))
                .andExpect(status().isNotFound());
        assertEquals(10, stock(uno)); assertEquals(5, stock(dos));
    }

    @Test void httpValidaClavesCantidadesConflictosYConsulta() throws Exception {
        String auth = token("stock:descontar stock:consultar stock:compensar"); UUID id = UUID.randomUUID();
        mvc.perform(post(RUTA).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(cuerpo(1, 1)))
                .andExpect(status().isBadRequest());
        mvc.perform(post(RUTA).header("Authorization", auth).header("Idempotency-Key", "invalida")
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo(1, 1))).andExpect(status().isBadRequest());
        for (String cuerpo : List.of(cuerpo(0, 1), cuerpo(-1, 1), "{\"productos\":[]}", "{\"productos\":[null]}")) {
            mvc.perform(post(RUTA).header("Authorization", auth).header("Idempotency-Key", UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isBadRequest());
        }
        mvc.perform(post(RUTA).header("Authorization", auth).header("Idempotency-Key", id)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo(1, 1))).andExpect(status().isOk());
        mvc.perform(post(RUTA).header("Authorization", auth).header("Idempotency-Key", id)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo(2, 1))).andExpect(status().isConflict());
        mvc.perform(get(RUTA + "/" + id).header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("APLICADO"));
        mvc.perform(get(RUTA + "/" + UUID.randomUUID()).header("Authorization", auth)).andExpect(status().isNotFound());
        mvc.perform(put(RUTA + "/" + id + "/compensacion").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("COMPENSADO"));
        assertEquals(10, stock(uno)); assertEquals(5, stock(dos));
    }

    @Test void httpExigeIdentidadInternaYScopes() throws Exception {
        String admin = "Bearer " + Jwts.builder().subject("admin@example.test").claim("rol", "ADMINISTRADOR")
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(USUARIOS.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256).compact();
        for (String auth : List.of("", admin)) {
            mvc.perform(post(RUTA).header("Authorization", auth).header("Idempotency-Key", UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON).content(cuerpo(1, 1))).andExpect(status().isUnauthorized());
        }
        mvc.perform(post(RUTA).header("Authorization", token("stock:consultar")).header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo(1, 1))).andExpect(status().isForbidden());
        mvc.perform(get(RUTA + "/" + UUID.randomUUID()).header("Authorization", token("stock:descontar")))
                .andExpect(status().isForbidden());
        mvc.perform(put(RUTA + "/" + UUID.randomUUID() + "/compensacion").header("Authorization", token("stock:consultar")))
                .andExpect(status().isForbidden());
        assertEquals(0, operaciones.count()); assertEquals(10, stock(uno));
    }
}
