package cl.gaselvolcan.ms_pedidos.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import cl.gaselvolcan.ms_pedidos.client.*;
import cl.gaselvolcan.ms_pedidos.dto.*;
import cl.gaselvolcan.ms_pedidos.dto.catalogo.*;
import cl.gaselvolcan.ms_pedidos.exception.*;
import cl.gaselvolcan.ms_pedidos.model.*;
import cl.gaselvolcan.ms_pedidos.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import jakarta.validation.Validator;

@SpringBootTest(properties = {
        "jwt.secret=clave-solo-para-las-pruebas-de-gas-el-volcan",
        "internal.jwt.secret=clave-interna-sintetica-exclusiva-de-pruebas-123456",
        "pedidos.recuperacion.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:compras-durables;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never", "spring.jpa.show-sql=false"
})
class CompraIntegrationTest {
    @Autowired CompraService compras;
    @Autowired BoletaService servicio;
    @Autowired OperacionCompraRepository operaciones;
    @Autowired BoletaRepository boletas;
    @Autowired PlatformTransactionManager manager;
    @Autowired Validator validator;
    @Autowired WebApplicationContext contexto;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @MockitoBean CatalogoClient catalogo;
    @MockitoBean StockInternoClient stock;
    private final Map<UUID, StockInternoClient.Resultado> remoto = new ConcurrentHashMap<>();
    private AtomicInteger descuentos, compensaciones;
    private MockMvc mvc;

    @BeforeEach void preparar() {
        operaciones.deleteAll(); boletas.deleteAll(); remoto.clear();
        descuentos = new AtomicInteger(); compensaciones = new AtomicInteger();
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
        when(catalogo.consultarProducto(anyLong())).thenAnswer(c -> {
            sinTx(); Long id = c.getArgument(0);
            return new ProductoCatalogoRespuesta(id, "P" + id, true, new BigDecimal("10.00"), new BigDecimal("8.00"));
        });
        when(catalogo.consultarDisponibilidad(anyLong(), anyInt())).thenAnswer(c -> {
            sinTx(); return new DisponibilidadCatalogoRespuesta(c.getArgument(0), "P" + c.getArgument(0), c.getArgument(1), 100, true, true);
        });
        when(stock.consultar(any())).thenAnswer(c -> { sinTx(); return remoto.get(c.getArgument(0)); });
        doAnswer(c -> {
            sinTx(); UUID id = c.getArgument(0); List<StockInternoClient.Linea> lineas = c.getArgument(1);
            return remoto.computeIfAbsent(id, k -> { descuentos.incrementAndGet(); return new StockInternoClient.Resultado(
                    id.toString(), "APLICADO", List.copyOf(lineas), 200, "ok"); });
        }).when(stock).descontar(any(), any());
        when(stock.compensar(any())).thenAnswer(c -> {
            sinTx(); UUID id = c.getArgument(0);
            return remoto.compute(id, (k, anterior) -> {
                if (anterior == null) throw new CatalogoNoDisponibleException("desconocido");
                if ("COMPENSADO".equals(anterior.estado())) return anterior;
                compensaciones.incrementAndGet(); return new StockInternoClient.Resultado(id.toString(), "COMPENSADO", anterior.productos(), 200, "ok");
            });
        });
    }
    private void sinTx() { assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "HTTP dentro de una transaccion"); }
    private BoletaSolicitud datos() {
        return new BoletaSolicitud(10L, "D", "C", null, 11L, LocalDateTime.of(2026, 1, 1, 12, 0),
                List.of(new DetalleBoletaSolicitud(1L, 2), new DetalleBoletaSolicitud(2L, 3)));
    }
    private CompraService reiniciarCoordinador() { return new CompraService(operaciones, boletas, servicio, stock, validator, manager); }
    private String estado(UUID id) { return operaciones.findById(id.toString()).orElseThrow().getEstado(); }
    private String cuerpo() { return """
            {"clienteId":10,"direccion":"D","comuna":"C","repartidorId":11,"fecha":"2026-01-01T12:00:00",
             "detalles":[{"productoId":1,"cantidad":2},{"productoId":2,"cantidad":3}]}
            """; }

    private org.springframework.test.web.servlet.request.RequestPostProcessor cliente(Long id) {
        var usuario = new cl.gaselvolcan.ms_pedidos.config.UsuarioToken(id, "cliente@example.test", "CLIENTE");
        return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(usuario, null,
                        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CLIENTE"))));
    }

    @Test void identidadDelTokenImpideSuplantacionYFiltraBoletas() throws Exception {
        UUID clave = UUID.randomUUID();
        mvc.perform(post("/api/v1/boletas").with(cliente(77L)).header("Idempotency-Key", clave)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.clienteId").value(77));
        long id = boletas.findAll().getFirst().getId();
        mvc.perform(get("/api/v1/boletas").with(cliente(10L))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/boletas").with(cliente(77L))).andExpect(status().isOk()).andExpect(jsonPath("$[0].clienteId").value(77));
        for (String ruta : List.of("/api/v1/boletas/" + id, "/api/v1/boletas/" + id + "/stock"))
            mvc.perform(get(ruta).with(cliente(10L))).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/boletas/" + id).with(cliente(10L)).contentType(MediaType.APPLICATION_JSON).content(cuerpo()))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/boletas/" + id + "/estado").with(cliente(10L)).contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"CANCELADO\"}"))
                .andExpect(status().isForbidden());
    }

    @Test void invitadoValidoPersisteContactoYReintentaSinDuplicar() throws Exception {
        UUID clave = UUID.randomUUID();
        String cuerpo = cuerpo().replace("\"clienteId\":10", "\"clienteId\":999,\"nombreInvitado\":\"Prueba Invitado\",\"correoInvitado\":\"invitado@example.test\",\"telefonoInvitado\":\"123456789\"");
        for (int i = 0; i < 2; i++) mvc.perform(post("/api/v1/boletas").header("Idempotency-Key", clave)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isCreated()).andExpect(jsonPath("$.clienteId").isEmpty());
        var b = boletas.findAll().getFirst();
        assertNull(b.getClienteId()); assertEquals("Prueba Invitado", b.getNombreInvitado());
        assertEquals("invitado@example.test", b.getCorreoInvitado()); assertEquals("123456789", b.getTelefonoInvitado());
        assertEquals(1, descuentos.get());
        servicio.actualizarEstado(b.getId(), EstadoBoleta.CANCELADO); compras.recuperarPendientes();
        assertEquals(1, compensaciones.get());
    }

    @Test void invitadoPendienteRecuperaContactoDesdeSolicitudDurable() {
        var base = datos();
        var invitado = new BoletaSolicitud(null, base.direccion(), base.comuna(), base.estado(), base.repartidorId(),
                base.fecha(), base.detalles(), "Invitado prueba", "invitado@example.test", "123456789");
        doThrow(new CatalogoNoDisponibleException("timeout")).when(stock).consultar(any());
        UUID clave = UUID.randomUUID();
        assertThrows(CatalogoNoDisponibleException.class, () -> compras.crear(clave, invitado));
        doAnswer(c -> remoto.get(c.getArgument(0))).when(stock).consultar(any());
        reiniciarCoordinador().recuperarPendientes();
        assertEquals("CONFIRMADA", estado(clave));
        var b = boletas.findAll().getFirst();
        assertNull(b.getClienteId()); assertEquals(invitado.correoInvitado(), b.getCorreoInvitado());
        assertEquals(invitado.nombreInvitado(), b.getNombreInvitado()); assertEquals(invitado.telefonoInvitado(), b.getTelefonoInvitado());
    }

    @Test void clienteSinIdEnTokenNoPuedeComprar() throws Exception {
        mvc.perform(post("/api/v1/boletas").with(cliente(null)).header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo())).andExpect(status().isForbidden());
        assertEquals(0, descuentos.get());
    }

    static Stream<Arguments> comprasInvalidas() {
        return Stream.of(
                arguments(false, "nombreInvitado", null, "El nombre del invitado es obligatorio."),
                arguments(false, "nombreInvitado", " ", "El nombre del invitado es obligatorio."),
                arguments(false, "nombreInvitado", "x".repeat(151), "El nombre del invitado no puede superar 150 caracteres."),
                arguments(false, "correoInvitado", null, "El correo del invitado es obligatorio."),
                arguments(false, "correoInvitado", " ", "El correo del invitado es obligatorio."),
                arguments(false, "correoInvitado", "correo-invalido", "El correo del invitado no es válido."),
                arguments(false, "telefonoInvitado", null, "El teléfono del invitado es obligatorio."),
                arguments(false, "telefonoInvitado", " ", "El teléfono del invitado es obligatorio."),
                arguments(false, "telefonoInvitado", "telefono-invalido", "El teléfono del invitado no es válido."),
                arguments(false, "direccion", " ", "La dirección es obligatoria."),
                arguments(false, "direccion", null, "La dirección es obligatoria."),
                arguments(true, "direccion", " ", "La dirección es obligatoria."),
                arguments(true, "direccion", null, "La dirección es obligatoria."),
                arguments(true, "comuna", " ", "La comuna es obligatoria."),
                arguments(true, "fecha", null, "La fecha es obligatoria."),
                arguments(true, "detalles", List.of(), "La compra debe incluir al menos un detalle."),
                arguments(true, "correoInvitado", "correo-invalido", "El correo del invitado no es válido."));
    }

    @ParameterizedTest
    @MethodSource("comprasInvalidas")
    void compraInvalidaInformaCampoSinDescontarStock(boolean autenticado, String campo, Object valor, String mensaje) throws Exception {
        var json = tools.jackson.databind.json.JsonMapper.builder().build();
        Map<?, ?> original = json.readValue(cuerpo(), Map.class);
        var contenido = new LinkedHashMap<String, Object>();
        original.forEach((clave, dato) -> contenido.put((String) clave, dato));
        if (!autenticado) {
            contenido.put("nombreInvitado", "Invitado sintetico");
            contenido.put("correoInvitado", "invitado@example.test");
            contenido.put("telefonoInvitado", "123456789");
        }
        contenido.put(campo, valor);
        var peticion = post("/api/v1/boletas").header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(contenido));
        if (autenticado) peticion.with(cliente(10L));
        mvc.perform(peticion).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.estado").value(400)).andExpect(jsonPath("$.mensaje").value(mensaje));
        assertEquals(0, descuentos.get()); assertEquals(0, boletas.count()); assertEquals(0, operaciones.count());
        verifyNoInteractions(stock, catalogo);
    }

    @Test void invitadoSinContactoNoDescuentaStock() throws Exception {
        mvc.perform(post("/api/v1/boletas").header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo())).andExpect(status().isBadRequest());
        assertEquals(0, operaciones.count()); assertEquals(0, descuentos.get());
    }

    @Test void persisteSolicitudValidaMayorA255BytesSinTruncarla() {
        var solicitud = new BoletaSolicitud(10L, "Dirección de prueba ".repeat(12), "Comuna de prueba",
                "PENDIENTE", 11L, datos().fecha(), datos().detalles());
        assertTrue(validator.validate(solicitud).isEmpty());
        String contenido = tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(solicitud);
        assertTrue(contenido.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 255);
        String id = UUID.randomUUID().toString();
        new TransactionTemplate(manager).executeWithoutResult(s ->
                operaciones.saveAndFlush(new OperacionCompra(id, contenido)));
        // Lectura JDBC y una nueva transaccion: no comprobar solo el objeto en memoria.
        assertEquals(contenido, jdbc.queryForObject("select solicitud from operaciones_compra where id = ?", String.class, id));
        assertEquals(contenido, operaciones.findById(id).orElseThrow().getSolicitud());
        verifyNoInteractions(stock, catalogo);
    }

    @Test void compraMultiproductoFijaOfertasYPersisteUnaSolaBoleta() {
        UUID id = UUID.randomUUID(); var primera = compras.crear(id, datos());
        assertEquals(new BigDecimal("40.00"), primera.total()); assertEquals("PENDIENTE", primera.estado());
        assertEquals("CONFIRMADA", estado(id)); assertEquals(1, descuentos.get());
        assertEquals(primera, compras.crear(id, datos())); assertEquals(1, boletas.count());
        verify(catalogo, times(2)).consultarProducto(anyLong());
    }

    @Test void mismaClaveConOtraSolicitudEs409SinDescuentoAdicional() {
        UUID id = UUID.randomUUID(); compras.crear(id, datos());
        var otra = new BoletaSolicitud(10L, "Otra", "C", null, 11L, datos().fecha(), datos().detalles());
        assertThrows(ConflictoCompraException.class, () -> compras.crear(id, otra)); assertEquals(1, descuentos.get());
    }

    @Test void stockInsuficienteSeTraduceA400SinBoletaConfirmada() throws Exception {
        doAnswer(c -> {
            sinTx(); return new StockInternoClient.Resultado(c.getArgument(0).toString(), "RECHAZADO", c.getArgument(1), 409, "insuficiente");
        }).when(stock).descontar(any(), any());
        UUID id = UUID.randomUUID();
        mvc.perform(post("/api/v1/boletas").with(cliente(10L)).header("Idempotency-Key", id).contentType(MediaType.APPLICATION_JSON).content(cuerpo()))
                .andExpect(status().isBadRequest());
        assertEquals("RECHAZADA", estado(id)); assertEquals(0, boletas.count());
        assertThrows(SolicitudInvalidaException.class, () -> compras.crear(id, datos()));
        verify(stock, times(1)).descontar(any(), any());
    }

    @Test void timeoutTrasDescuentoConsultaLaMismaClaveYConfirma() {
        doAnswer(c -> {
            sinTx(); UUID id = c.getArgument(0); descuentos.incrementAndGet();
            remoto.put(id, new StockInternoClient.Resultado(id.toString(), "APLICADO", c.getArgument(1), 200, "ok"));
            throw new CatalogoNoDisponibleException("timeout despues de aplicar");
        }).when(stock).descontar(any(), any());
        UUID id = UUID.randomUUID(); assertNotNull(compras.crear(id, datos()).id());
        assertEquals(1, descuentos.get()); verify(stock, times(2)).consultar(id);
    }

    @Test void resultadoDesconocidoSeRecuperaConDatosDurablesYPrecioOriginal() {
        doThrow(new CatalogoNoDisponibleException("timeout")).when(stock).consultar(any());
        UUID id = UUID.randomUUID(); assertThrows(CatalogoNoDisponibleException.class, () -> compras.crear(id, datos()));
        assertEquals("COTIZADA", estado(id)); assertEquals(0, boletas.count());
        doAnswer(c -> { sinTx(); return remoto.get(c.getArgument(0)); }).when(stock).consultar(any());
        var nuevo = reiniciarCoordinador(); nuevo.recuperarPendientes();
        assertEquals("CONFIRMADA", estado(id)); assertEquals(new BigDecimal("40.00"), nuevo.crear(id, datos()).total());
        verify(catalogo, times(2)).consultarProducto(anyLong()); assertEquals(1, descuentos.get());
    }

    @Test void falloDePersistenciaPosteriorAlDescuentoConservaRecuperacion() {
        UUID id = UUID.randomUUID();
        jdbc.execute("alter table boletas add constraint prueba_persistencia check (total < 0)");
        try {
            assertThrows(CatalogoNoDisponibleException.class, () -> compras.crear(id, datos()));
        } finally { jdbc.execute("alter table boletas drop constraint prueba_persistencia"); }
        assertEquals("COTIZADA", estado(id)); assertEquals(1, descuentos.get()); assertEquals(0, boletas.count());
        reiniciarCoordinador().recuperarPendientes();
        assertEquals("CONFIRMADA", estado(id)); assertEquals(1, boletas.count()); assertEquals(1, descuentos.get());
    }

    @Test void cancelacionEsDurableYCompensaUnaVezSinReactivar() {
        UUID id = UUID.randomUUID(); var b = compras.crear(id, datos());
        servicio.actualizarEstado(b.id(), EstadoBoleta.CANCELADO);
        assertEquals("COMPENSACION_PENDIENTE", estado(id)); assertFalse(compras.estadoStock(b.id()).stockRestituido());
        servicio.actualizarEstado(b.id(), EstadoBoleta.CANCELADO);
        reiniciarCoordinador().recuperarPendientes(); compras.recuperarPendientes();
        assertEquals("COMPENSADA", estado(id)); assertEquals(1, compensaciones.get());
        assertTrue(compras.estadoStock(b.id()).stockRestituido());
        assertThrows(ConflictoCompraException.class, () -> compras.crear(id, datos()));
        assertEquals("CANCELADO", servicio.buscarPorId(b.id()).estado()); assertEquals(1, descuentos.get());
    }

    @Test void compensacionConTimeoutNoSeDeclaraCompletadaYSeRecupera() {
        UUID id = UUID.randomUUID(); var b = compras.crear(id, datos()); servicio.actualizarEstado(b.id(), EstadoBoleta.CANCELADO);
        doThrow(new CatalogoNoDisponibleException("timeout")).when(stock).compensar(id);
        compras.recuperarPendientes(); assertEquals("COMPENSACION_PENDIENTE", estado(id));
        assertFalse(compras.estadoStock(b.id()).stockRestituido());
        remoto.put(id, new StockInternoClient.Resultado(id.toString(), "COMPENSADO", remoto.get(id).productos(), 200, "ok"));
        reiniciarCoordinador().recuperarPendientes(); assertEquals("COMPENSADA", estado(id));
    }

    @Test void compensacionCon404ConservaIntencionNoReenviaDescuento() {
        UUID id = UUID.randomUUID(); var b = compras.crear(id, datos()); servicio.actualizarEstado(b.id(), EstadoBoleta.CANCELADO);
        remoto.clear(); compras.recuperarPendientes();
        assertEquals("COMPENSACION_PENDIENTE", estado(id)); assertFalse(compras.estadoStock(b.id()).stockRestituido());
        verify(stock, times(1)).descontar(any(), any()); verify(stock, never()).compensar(any());
    }

    @Test void noSeEditaUnaBoletaConDescuentoPeroSeConservanLasAntiguas() {
        var b = compras.crear(UUID.randomUUID(), datos()); clearInvocations(catalogo);
        assertThrows(SolicitudInvalidaException.class, () -> servicio.actualizar(b.id(), datos()));
        verifyNoInteractions(catalogo);
        var antigua = new Boleta(10L, "D", "C", "PENDIENTE", null, datos().fecha(), BigDecimal.TEN);
        antigua.agregarDetalle(new DetalleBoleta(1L, 1, BigDecimal.TEN)); var id = boletas.saveAndFlush(antigua).getId();
        servicio.actualizarEstado(id, EstadoBoleta.CANCELADO); compras.recuperarPendientes();
        assertEquals("SIN_REGISTRO", compras.estadoStock(id).estadoTecnico()); assertEquals(0, compensaciones.get());
    }

    @Test void httpExigeClaveEstableYReintentaSinDuplicar() throws Exception {
        mvc.perform(post("/api/v1/boletas").with(cliente(10L)).contentType(MediaType.APPLICATION_JSON).content(cuerpo())).andExpect(status().isBadRequest());
        UUID id = UUID.randomUUID();
        for (int i = 0; i < 2; i++) mvc.perform(post("/api/v1/boletas").with(cliente(10L)).header("Idempotency-Key", id)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.total").value(40.00));
        assertEquals(1, boletas.count()); assertEquals(1, descuentos.get());
    }

    @Test void productosRepetidosYOrdenDistintoConservanLaMismaCompra() {
        UUID id = UUID.randomUUID(); var original = compras.crear(id, datos());
        var repetida = new BoletaSolicitud(10L, " D ", "C", "PENDIENTE", 11L, datos().fecha(), List.of(
                new DetalleBoletaSolicitud(2L, 3), new DetalleBoletaSolicitud(1L, 1), new DetalleBoletaSolicitud(1L, 1)));
        assertEquals(original, compras.crear(id, repetida)); assertEquals(1, descuentos.get()); assertEquals(1, boletas.count());
    }

    @Test void solicitudConcurrenteNoDuplicaConfirmacionNiDescuento() throws Exception {
        UUID id = UUID.randomUUID(); var dentro = new CountDownLatch(1); var seguir = new CountDownLatch(1);
        doAnswer(c -> {
            sinTx(); dentro.countDown(); assertTrue(seguir.await(15, TimeUnit.SECONDS));
            descuentos.incrementAndGet(); var r = new StockInternoClient.Resultado(id.toString(), "APLICADO", c.getArgument(1), 200, "ok");
            remoto.put(id, r); return r;
        }).when(stock).descontar(eq(id), any());
        var ejecutor = Executors.newSingleThreadExecutor();
        try {
            var primera = ejecutor.submit(() -> compras.crear(id, datos()));
            assertTrue(dentro.await(10, TimeUnit.SECONDS));
            assertThrows(CatalogoNoDisponibleException.class, () -> compras.crear(id, datos()));
            assertEquals(0, boletas.count()); seguir.countDown();
            var respuesta = primera.get(10, TimeUnit.SECONDS);
            assertEquals(respuesta, compras.crear(id, datos())); assertEquals(1, boletas.count()); assertEquals(1, descuentos.get());
        } finally { seguir.countDown(); ejecutor.shutdownNow(); }
    }

    @Test void leasePersistidoTrasReinicioNoAutorizaOtroTrabajadorHastaVencer() {
        UUID id = UUID.randomUUID();
        doThrow(new CatalogoNoDisponibleException("caida")).when(stock).consultar(any());
        assertThrows(CatalogoNoDisponibleException.class, () -> compras.crear(id, datos()));
        new TransactionTemplate(manager).executeWithoutResult(s -> operaciones.bloquear(id.toString()).orElseThrow().tomar("trabajador-caido"));
        doAnswer(c -> remoto.get(c.getArgument(0))).when(stock).consultar(any());
        reiniciarCoordinador().recuperarPendientes(); assertEquals(0, boletas.count());
        jdbc.update("update operaciones_compra set vence = ? where id = ?", java.sql.Timestamp.from(Instant.now().minusSeconds(1)), id.toString());
        reiniciarCoordinador().recuperarPendientes(); assertEquals(1, boletas.count()); assertEquals(1, descuentos.get());
    }

    @Test void trabajadorAntiguoNoConfirmaDespuesDeRecuperacionYCompensacion() throws Exception {
        UUID id = UUID.randomUUID(); var aplicado = new CountDownLatch(1); var seguir = new CountDownLatch(1);
        doAnswer(c -> {
            sinTx(); remoto.put(id, new StockInternoClient.Resultado(id.toString(), "APLICADO", c.getArgument(1), 200, "ok"));
            descuentos.incrementAndGet(); aplicado.countDown();
            assertTrue(seguir.await(15, TimeUnit.SECONDS)); return remoto.get(id);
        }).when(stock).descontar(eq(id), any());
        var ejecutor = Executors.newSingleThreadExecutor();
        try {
            var viejo = ejecutor.submit(() -> { try { compras.crear(id, datos()); return false; } catch (ConflictoCompraException e) { return true; } });
            assertTrue(aplicado.await(10, TimeUnit.SECONDS));
            jdbc.update("update operaciones_compra set vence = ? where id = ?", java.sql.Timestamp.from(Instant.now().minusSeconds(1)), id.toString());
            reiniciarCoordinador().recuperarPendientes();
            Long boleta = operaciones.findById(id.toString()).orElseThrow().getBoletaId(); assertNotNull(boleta);
            servicio.actualizarEstado(boleta, EstadoBoleta.CANCELADO); reiniciarCoordinador().recuperarPendientes();
            seguir.countDown(); assertTrue(viejo.get(10, TimeUnit.SECONDS));
            assertEquals("COMPENSADA", estado(id)); assertEquals("CANCELADO", servicio.buscarPorId(boleta).estado());
            assertEquals(1, descuentos.get()); assertEquals(1, compensaciones.get()); assertEquals(1, boletas.count());
        } finally { seguir.countDown(); ejecutor.shutdownNow(); }
    }
}
