package cl.gaselvolcan.ms_pedidos.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import cl.gaselvolcan.ms_pedidos.dto.*;
import cl.gaselvolcan.ms_pedidos.exception.*;
import cl.gaselvolcan.ms_pedidos.model.*;
import cl.gaselvolcan.ms_pedidos.repository.BoletaRepository;
import cl.gaselvolcan.ms_pedidos.service.BoletaService;

// Servicio real + cliente REST real con transporte HTTP simulado. Sin catalogo ni MySQL.
class CatalogoIntegrationTest {
    private static final String BASE = "http://catalogo.test:8082/api/v1/productos/";
    private static ValidatorFactory factory;
    private MockRestServiceServer servidor;
    private CatalogoClient cliente;
    private BoletaRepository repositorio;
    private BoletaService servicio;
    private Boleta existente;

    @BeforeAll static void iniciar() { factory = Validation.buildDefaultValidatorFactory(); }
    @AfterAll static void cerrar() { factory.close(); }
    @BeforeEach void preparar() {
        var builder = RestClient.builder().baseUrl("http://catalogo.test:8082");
        servidor = MockRestServiceServer.bindTo(builder).build();
        cliente = new CatalogoClient(builder.build());
        repositorio = mock(BoletaRepository.class);
        var manager = mock(org.springframework.transaction.PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new org.springframework.transaction.support.SimpleTransactionStatus());
        servicio = new BoletaService(repositorio, factory.getValidator(), cliente, manager);
        existente = new Boleta(10L, "Original", "Comuna", "PENDIENTE", null,
                LocalDateTime.of(2026, 1, 1, 12, 0), BigDecimal.TEN);
        existente.setId(1L);
        existente.agregarDetalle(new DetalleBoleta(9L, 1, BigDecimal.TEN));
        when(repositorio.findById(1L)).thenReturn(Optional.of(existente));
        when(repositorio.saveAndFlush(any())).thenAnswer(c -> {
            Boleta boleta = c.getArgument(0); boleta.setId(1L); return boleta;
        });
    }
    @AfterEach void verificarSolicitudes() { servidor.verify(); }
    private BoletaSolicitud datos(DetalleBoletaSolicitud... detalles) {
        return new BoletaSolicitud(10L, "Nueva", "Comuna", "PENDIENTE", null,
                LocalDateTime.of(2026, 1, 1, 12, 0), List.of(detalles));
    }
    private String producto(long id, boolean activo, String precio, String oferta) {
        return "{\"id\":" + id + ",\"codigo\":\"P" + id + "\",\"activo\":" + activo
                + ",\"precioResidencial\":" + precio + ",\"precioOferta\":" + oferta
                + ",\"precioComercial\":999,\"categoria\":{\"id\":1},\"nombre\":\"Prueba\"}";
    }
    private String disponibilidad(long id, int cantidad, int stock, boolean activo) {
        return "{\"productoId\":" + id + ",\"codigo\":\"P" + id + "\",\"cantidadSolicitada\":" + cantidad
                + ",\"stock\":" + stock + ",\"activo\":" + activo
                + ",\"disponible\":" + (activo && stock >= cantidad) + "}";
    }
    private void esperarProducto(long id, String json) {
        servidor.expect(requestTo(BASE + id)).andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }
    private void esperarStock(long id, int cantidad, String json) {
        servidor.expect(requestTo(BASE + id + "/disponibilidad?cantidad=" + cantidad))
                .andExpect(method(HttpMethod.GET)).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }
    private void intacta() {
        assertEquals("Original", existente.getDireccion());
        assertEquals(BigDecimal.TEN, existente.getTotal());
        assertEquals(9L, existente.getDetalles().getFirst().getProductoId());
        verify(repositorio, never()).saveAndFlush(any());
    }
    @Test void creaConDosProductosYPreciosVigentesDelCatalogo() {
        esperarProducto(1, producto(1, true, "19.99", "15.50"));
        esperarStock(1, 2, disponibilidad(1, 2, 10, true));
        esperarProducto(2, producto(2, true, "3.10", "null"));
        esperarStock(2, 3, disponibilidad(2, 3, 10, true));
        var respuesta = servicio.cotizar(datos(new DetalleBoletaSolicitud(1L, 2), new DetalleBoletaSolicitud(2L, 3)));
        assertEquals(new BigDecimal("40.30"), respuesta.total());
        assertEquals(new BigDecimal("15.50"), respuesta.detalles().getFirst().precio());
        assertEquals(new BigDecimal("3.10"), respuesta.detalles().get(1).precio());
    }

    @Test void lecturaDeProductoToleraVersionAdicionalSinCambiarSuContrato() {
        String json = producto(1, true, "19.99", "15.50");
        esperarProducto(1, json.substring(0, json.length() - 1) + ",\"version\":7}");
        var respuesta = cliente.consultarProducto(1L);
        assertEquals(1L, respuesta.id());
        assertEquals(new BigDecimal("19.99"), respuesta.precioResidencial());
        assertEquals(new BigDecimal("15.50"), respuesta.precioOferta());
        assertTrue(respuesta.activo());
    }
    @Test void putConsultaOtraVezPreciosYCantidadesActuales() {
        esperarProducto(1, producto(1, true, "20", "null"));
        esperarStock(1, 2, disponibilidad(1, 2, 10, true));
        var respuesta = servicio.actualizar(1L, datos(new DetalleBoletaSolicitud(1L, 2)));
        assertEquals(new BigDecimal("40.00"), respuesta.total());
        assertEquals(1L, respuesta.detalles().getFirst().productoId());
    }
    @Test void productoInexistente404NoGuarda() {
        servidor.expect(requestTo(BASE + 1)).andRespond(withResourceNotFound());
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.cotizar(datos(new DetalleBoletaSolicitud(1L, 1))));
        intacta();
    }
    @Test void productoInactivoNoConsultaDisponibilidadNiGuarda() {
        esperarProducto(1, producto(1, false, "10", "null"));
        assertThrows(SolicitudInvalidaException.class, () -> servicio.actualizar(1L, datos(new DetalleBoletaSolicitud(1L, 1))));
        intacta();
    }
    @Test void stockInsuficienteNoAlteraBoletaExistente() {
        esperarProducto(1, producto(1, true, "10", "null"));
        esperarStock(1, 3, disponibilidad(1, 3, 2, true));
        assertThrows(SolicitudInvalidaException.class, () -> servicio.actualizar(1L, datos(new DetalleBoletaSolicitud(1L, 3))));
        intacta();
    }
    @Test void acumulaCantidadesRepetidasParaEvitarEludirStock() {
        esperarProducto(1, producto(1, true, "10", "null"));
        esperarStock(1, 4, disponibilidad(1, 4, 3, true));
        assertThrows(SolicitudInvalidaException.class, () -> servicio.cotizar(datos(
                new DetalleBoletaSolicitud(1L, 2), new DetalleBoletaSolicitud(1L, 2))));
        intacta();
    }
    @Test void repetidosUsanUnaSolaConsultaYElMismoPrecio() {
        esperarProducto(1, producto(1, true, "10", "null"));
        esperarStock(1, 4, disponibilidad(1, 4, 4, true));
        var respuesta = servicio.cotizar(datos(new DetalleBoletaSolicitud(1L, 2), new DetalleBoletaSolicitud(1L, 2)));
        assertEquals(new BigDecimal("40.00"), respuesta.total());
        assertEquals(2, respuesta.detalles().size());
    }
    @Test void cantidadAcumuladaFueraDeRangoNoLlamaCatalogo() {
        assertThrows(SolicitudInvalidaException.class, () -> servicio.cotizar(datos(
                new DetalleBoletaSolicitud(1L, Integer.MAX_VALUE), new DetalleBoletaSolicitud(1L, 1))));
        intacta();
    }
    @Test void errorServidorSeTraduceEnCatalogoNoDisponible() {
        servidor.expect(requestTo(BASE + 1)).andRespond(withServerError());
        assertThrows(CatalogoNoDisponibleException.class, () -> servicio.actualizar(1L, datos(new DetalleBoletaSolicitud(1L, 1))));
        intacta();
    }
    @Test void timeoutNoExponeDetallesInternos() {
        servidor.expect(requestTo(BASE + 1)).andRespond(withException(new SocketTimeoutException("privado")));
        var error = assertThrows(CatalogoNoDisponibleException.class, () -> cliente.consultarProducto(1L));
        assertFalse(error.getMessage().contains("privado"));
    }
    @Test void estadoHttpInesperadoEsRespuestaInvalida() {
        servidor.expect(requestTo(BASE + 1)).andRespond(withStatus(HttpStatus.BAD_REQUEST));
        assertThrows(RespuestaCatalogoInvalidaException.class, () -> cliente.consultarProducto(1L));
    }
    @Test void jsonMalformadoEsRespuestaInvalida() {
        esperarProducto(1, "{sin-json");
        assertThrows(RespuestaCatalogoInvalidaException.class, () -> cliente.consultarProducto(1L));
    }
    @Test void cuerpoVacioEsRespuestaInvalida() {
        esperarProducto(1, "");
        assertThrows(RespuestaCatalogoInvalidaException.class, () -> cliente.consultarProducto(1L));
    }
    @Test void productoConOtroIdEsRespuestaInvalida() {
        esperarProducto(1, producto(2, true, "10", "null"));
        assertThrows(RespuestaCatalogoInvalidaException.class, () -> servicio.cotizar(datos(new DetalleBoletaSolicitud(1L, 1))));
        intacta();
    }
    @Test void precioAusenteNegativoOImprecisoNoSePersiste() {
        for (String precio : List.of("null", "-1", "1.001", "100000000000000000")) {
            servidor.reset();
            esperarProducto(1, producto(1, true, precio, "null"));
            assertThrows(RespuestaCatalogoInvalidaException.class, () -> servicio.cotizar(datos(new DetalleBoletaSolicitud(1L, 1))));
            servidor.verify();
        }
        intacta();
    }
    @Test void disponibilidadInconsistenteNoSePersiste() {
        esperarProducto(1, producto(1, true, "10", "null"));
        esperarStock(1, 3, disponibilidad(1, 3, 2, true).replace("false", "true"));
        assertThrows(RespuestaCatalogoInvalidaException.class, () -> servicio.cotizar(datos(new DetalleBoletaSolicitud(1L, 3))));
        intacta();
    }
    @Test void disponibilidadSinCampoDisponibleNoSeInterpretaComoStockInsuficiente() {
        esperarProducto(1, producto(1, true, "10", "null"));
        esperarStock(1, 1, "{\"productoId\":1,\"codigo\":\"P1\",\"cantidadSolicitada\":1,\"stock\":10,\"activo\":true}");
        assertThrows(RespuestaCatalogoInvalidaException.class, () -> servicio.cotizar(datos(new DetalleBoletaSolicitud(1L, 1))));
        intacta();
    }
    @Test void productoDesactivadoEntreConsultasNoSePersiste() {
        esperarProducto(1, producto(1, true, "10", "null"));
        esperarStock(1, 1, disponibilidad(1, 1, 10, false));
        assertThrows(SolicitudInvalidaException.class, () -> servicio.cotizar(datos(new DetalleBoletaSolicitud(1L, 1))));
        intacta();
    }
    @Test void falloSegundoProductoNoGuardaDetallesParciales() {
        esperarProducto(1, producto(1, true, "10", "null"));
        esperarStock(1, 1, disponibilidad(1, 1, 10, true));
        servidor.expect(requestTo(BASE + 2)).andRespond(withServiceUnavailable());
        assertThrows(CatalogoNoDisponibleException.class, () -> servicio.actualizar(1L, datos(
                new DetalleBoletaSolicitud(1L, 1), new DetalleBoletaSolicitud(2L, 1))));
        intacta();
    }
    @Test void consultarBoletasNoRequiereCatalogo() {
        when(repositorio.findAll()).thenReturn(List.of(existente));
        assertEquals(1, servicio.listar().size());
        servicio.buscarPorId(1L);
    }
}
