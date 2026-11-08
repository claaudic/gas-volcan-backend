package cl.gaselvolcan.ms_pedidos.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.*;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import cl.gaselvolcan.ms_pedidos.dto.*;
import cl.gaselvolcan.ms_pedidos.exception.*;
import cl.gaselvolcan.ms_pedidos.model.*;
import cl.gaselvolcan.ms_pedidos.repository.BoletaRepository;

class BoletaServiceTest {
    private cl.gaselvolcan.ms_pedidos.client.CatalogoClient catalogo;

    private static ValidatorFactory factory;
    private BoletaRepository repositorio;
    private BoletaService servicio;
    private Boleta existente;
    @BeforeAll static void iniciar() { factory = Validation.buildDefaultValidatorFactory(); }
    @AfterAll static void cerrar() { factory.close(); }
    @BeforeEach void preparar() {
        repositorio = mock(BoletaRepository.class);
        catalogo = mock(cl.gaselvolcan.ms_pedidos.client.CatalogoClient.class);
        when(catalogo.consultarProducto(anyLong())).thenAnswer(c -> {
            Long id = c.getArgument(0);
            BigDecimal precio = id == 91001L ? new BigDecimal("19.99") : id == 91002L ? new BigDecimal("3.10") : BigDecimal.ZERO;
            return new cl.gaselvolcan.ms_pedidos.dto.catalogo.ProductoCatalogoRespuesta(id, "P" + id, true, precio, null);
        });
        when(catalogo.consultarDisponibilidad(anyLong(), anyInt())).thenAnswer(c ->
                new cl.gaselvolcan.ms_pedidos.dto.catalogo.DisponibilidadCatalogoRespuesta(
                        c.getArgument(0), "P" + c.getArgument(0), c.getArgument(1), Integer.MAX_VALUE, true, true));
        servicio = new BoletaService(repositorio, factory.getValidator(), catalogo);
        existente = new Boleta(10L, "Direccion anterior", "Comuna anterior", "PENDIENTE",
                null, LocalDateTime.of(2026, 1, 1, 12, 0), BigDecimal.TEN);
        existente.setId(1L);
        existente.agregarDetalle(new DetalleBoleta(100L, 1, BigDecimal.TEN));
        when(repositorio.findById(1L)).thenReturn(Optional.of(existente));
        when(repositorio.saveAndFlush(any(Boleta.class))).thenAnswer(c -> {
            Boleta boleta = c.getArgument(0); if (boleta.getId() == null) boleta.setId(1L); return boleta;
        });
    }
    private List<DetalleBoletaSolicitud> lineas() {
        return List.of(new DetalleBoletaSolicitud(91001L, 2),
                new DetalleBoletaSolicitud(91002L, 3));
    }
    private BoletaSolicitud datos(List<DetalleBoletaSolicitud> detalles) {
        return new BoletaSolicitud(10L, " Direccion 123 ", " Comuna ", "PENDIENTE",
                null, LocalDateTime.of(2026, 1, 1, 12, 0), detalles);
    }

    @Test void creaConDosProductosYCalculaImporteExacto() {
        var respuesta = servicio.crear(datos(lineas()));
        assertEquals(new BigDecimal("49.28"), respuesta.total());
        assertEquals(2, respuesta.detalles().size());
        assertEquals(91001L, respuesta.detalles().getFirst().productoId());
        assertEquals("Direccion 123", respuesta.direccion());
        assertEquals("Comuna", respuesta.comuna());
        assertEquals("PENDIENTE", respuesta.estado());
        assertNull(respuesta.repartidorId());
        verify(repositorio).saveAndFlush(argThat(b -> b.getDetalles().stream().allMatch(d -> d.getBoleta() == b)));
    }
    @Test void creaPendienteSinEstadoYRechazaOtroEstadoInicial() {
        assertEquals("PENDIENTE", servicio.crear(new BoletaSolicitud(10L, "D", "C", null,
                null, LocalDateTime.now(), lineas())).estado());
        for (String estado : List.of("EN_CAMINO", "ENTREGADO", "CANCELADO")) {
            assertThrows(SolicitudInvalidaException.class, () -> servicio.crear(
                    new BoletaSolicitud(10L, "D", "C", estado, null, LocalDateTime.now(), lineas())));
        }
    }

    @Test void compruebaTodasLasTransicionesSinCambiarImportesNiDetalles() {
        var detalle = existente.getDetalles().getFirst();
        for (EstadoBoleta origen : EstadoBoleta.values()) {
            for (EstadoBoleta destino : EstadoBoleta.values()) {
                existente.setEstado(origen.name());
                clearInvocations(repositorio);
                boolean permitida = (origen == EstadoBoleta.PENDIENTE
                        && (destino == EstadoBoleta.EN_CAMINO || destino == EstadoBoleta.CANCELADO))
                        || (origen == EstadoBoleta.EN_CAMINO && destino == EstadoBoleta.ENTREGADO);
                if (permitida) {
                    assertEquals(destino.name(), servicio.actualizarEstado(1L, destino).estado());
                    verify(repositorio).saveAndFlush(existente);
                } else {
                    assertThrows(SolicitudInvalidaException.class, () -> servicio.actualizarEstado(1L, destino));
                    assertEquals(origen.name(), existente.getEstado());
                    verify(repositorio, never()).saveAndFlush(any());
                }
                assertEquals(BigDecimal.TEN, existente.getTotal());
                assertSame(detalle, existente.getDetalles().getFirst());
                assertEquals(BigDecimal.TEN, detalle.getPrecio());
                assertEquals(1, detalle.getCantidad());
                assertEquals("Direccion anterior", existente.getDireccion());
            }
        }
        verifyNoInteractions(catalogo);
    }

    @Test void putGeneralRechazaEstadosNoPendientesAntesDeConsultarCatalogo() {
        for (String estado : List.of("EN_CAMINO", "ENTREGADO", "CANCELADO")) {
            existente.setEstado(estado);
            var detalle = existente.getDetalles().getFirst();
            for (String solicitado : new String[] {estado, "PENDIENTE", null}) {
                assertThrows(SolicitudInvalidaException.class, () -> servicio.actualizar(1L,
                        new BoletaSolicitud(10L, "Cambio", "C", solicitado, 22L, LocalDateTime.now(), lineas())));
                assertEquals(estado, existente.getEstado());
                assertEquals("Direccion anterior", existente.getDireccion());
                assertEquals(BigDecimal.TEN, existente.getTotal());
                assertSame(detalle, existente.getDetalles().getFirst());
                assertNull(existente.getRepartidorId());
            }
        }
        verifyNoInteractions(catalogo);
        verify(repositorio, never()).saveAndFlush(any());
    }

    @Test void putGeneralNoPermiteCambiarEstado() {
        assertThrows(SolicitudInvalidaException.class, () -> servicio.actualizar(1L,
                new BoletaSolicitud(10L, "D", "C", "ENTREGADO", null, LocalDateTime.now(), lineas())));
        assertEquals("PENDIENTE", existente.getEstado());
        verifyNoInteractions(catalogo);
        verify(repositorio, never()).saveAndFlush(any());
    }

    @Test void estadoNuloOBoletaInexistenteNoSeGuarda() {
        assertThrows(SolicitudInvalidaException.class, () -> servicio.actualizarEstado(1L, null));
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.actualizarEstado(99L, EstadoBoleta.EN_CAMINO));
        verify(repositorio, never()).saveAndFlush(any());
    }

    @Test void guardaRepartidorYFechaRecibidos() {
        var fecha = LocalDateTime.of(2026, 2, 3, 10, 30);
        var respuesta = servicio.crear(new BoletaSolicitud(10L, "Direccion", "Comuna", "PENDIENTE", 20L, fecha, lineas()));
        assertEquals(20L, respuesta.repartidorId());
        assertEquals(fecha, respuesta.fecha());
    }
    @Test void admitePrecioCeroYMasDeDosProductos() {
        var respuesta = servicio.crear(datos(List.of(
                new DetalleBoletaSolicitud(1L, 1),
                new DetalleBoletaSolicitud(2L, 1),
                new DetalleBoletaSolicitud(3L, 1))));
        assertEquals(new BigDecimal("0.00"), respuesta.total());
        assertEquals(3, respuesta.detalles().size());
    }
    @Test void actualizarReemplazaLineasYRecalculaTotal() {
        var anterior = existente.getDetalles().getFirst();
        var respuesta = servicio.actualizar(1L, datos(lineas()));
        assertEquals(1L, respuesta.id());
        assertEquals(new BigDecimal("49.28"), respuesta.total());
        assertEquals(2, respuesta.detalles().size());
        assertNull(anterior.getBoleta());
        assertFalse(existente.getDetalles().contains(anterior));
    }
    @Test void listarYBuscarDevuelvenDto() {
        when(repositorio.findAll()).thenReturn(List.of(existente));
        assertEquals(servicio.buscarPorId(1L), servicio.listar().getFirst());
        assertEquals(100L, servicio.buscarPorId(1L).detalles().getFirst().productoId());
    }
    @Test void listarVacio() {
        when(repositorio.findAll()).thenReturn(List.of());
        assertTrue(servicio.listar().isEmpty());
    }
    @Test void buscarActualizarInexistentes() {
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.buscarPorId(99L));
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.actualizar(99L, datos(lineas())));
        verify(repositorio, never()).saveAndFlush(any());
        verify(repositorio, never()).delete(any());
    }
    @Test void idsInvalidosNoConsultanRepositorio() {
        for (Long id : new Long[] {null, 0L, -1L}) {
            assertThrows(SolicitudInvalidaException.class, () -> servicio.buscarPorId(id));
            assertThrows(SolicitudInvalidaException.class, () -> servicio.actualizar(id, datos(lineas())));
        }
        verifyNoInteractions(repositorio);
    }
    @Test void rechazaSolicitudNulaYDetallesVaciosNulosONulosDentroDeLista() {
        assertThrows(SolicitudInvalidaException.class, () -> servicio.crear(null));
        assertThrows(SolicitudInvalidaException.class, () -> servicio.crear(datos(null)));
        assertThrows(SolicitudInvalidaException.class, () -> servicio.crear(datos(List.of())));
        assertThrows(SolicitudInvalidaException.class, () -> servicio.crear(datos(Arrays.asList((DetalleBoletaSolicitud) null))));
        verify(repositorio, never()).saveAndFlush(any());
    }
    @Test void rechazaCantidadesEIdsDeProductoInvalidos() {
        var invalidas = List.of(
                new DetalleBoletaSolicitud(0L, 1),
                new DetalleBoletaSolicitud(null, 1),
                new DetalleBoletaSolicitud(1L, 0),
                new DetalleBoletaSolicitud(1L, -1),
                new DetalleBoletaSolicitud(1L, null));
        for (var linea : invalidas) {
            assertThrows(SolicitudInvalidaException.class, () -> servicio.crear(datos(List.of(linea))));
        }
        verify(repositorio, never()).saveAndFlush(any());
    }
    @Test void rechazaCabeceraInvalidaAntesDeGuardar() {
        assertThrows(SolicitudInvalidaException.class, () -> servicio.crear(
                new BoletaSolicitud(null, " ", " ", " ", -1L, null, lineas())));
        verify(repositorio, never()).saveAndFlush(any());
    }
    @Test void detectaTotalFueraDePrecisionAntesDeAlterarLaBoleta() {
        when(catalogo.consultarProducto(1L)).thenReturn(
                new cl.gaselvolcan.ms_pedidos.dto.catalogo.ProductoCatalogoRespuesta(1L, "P1", true,
                        new BigDecimal("99999999999999999.99"), null));
        var enorme = datos(List.of(new DetalleBoletaSolicitud(1L, 2)));
        assertThrows(SolicitudInvalidaException.class, () -> servicio.crear(enorme));
        assertThrows(SolicitudInvalidaException.class, () -> servicio.actualizar(1L, enorme));
        assertEquals("Direccion anterior", existente.getDireccion());
        assertEquals(BigDecimal.TEN, existente.getTotal());
        assertEquals(1, existente.getDetalles().size());
        verify(repositorio, never()).saveAndFlush(any());
    }
}
