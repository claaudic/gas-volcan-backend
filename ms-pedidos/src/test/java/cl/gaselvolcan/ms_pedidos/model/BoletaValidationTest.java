package cl.gaselvolcan.ms_pedidos.model;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BoletaValidationTest {
    private static ValidatorFactory factory;
    @BeforeAll static void iniciar() { factory = Validation.buildDefaultValidatorFactory(); }
    @AfterAll static void cerrar() { factory.close(); }

    private Boleta boleta() {
        var boleta = new Boleta(10L, "Direccion de prueba 123", "Comuna de prueba",
                "ESTADO_PRUEBA", null, LocalDateTime.of(2026, 1, 1, 12, 0), new BigDecimal("25.50"));
        boleta.agregarDetalle(new DetalleBoleta(91001L, 2, new BigDecimal("10.00")));
        boleta.agregarDetalle(new DetalleBoleta(91002L, 1, new BigDecimal("5.50")));
        return boleta;
    }

    @Test void aceptaDosProductosYRepartidorOpcional() {
        var boleta = boleta();
        assertTrue(factory.getValidator().validate(boleta).isEmpty());
        boleta.setRepartidorId(20L);
        assertTrue(factory.getValidator().validate(boleta).isEmpty());
    }

    @Test void rechazaCamposObligatoriosEIdsInvalidos() {
        var boleta = boleta();
        boleta.setClienteId(0L);
        boleta.setRepartidorId(-1L);
        boleta.setDireccion(" ");
        boleta.setComuna(" ");
        boleta.setEstado(" ");
        boleta.setFecha(null);
        boleta.setTotal(null);
        var campos = factory.getValidator().validate(boleta).stream()
                .map(v -> v.getPropertyPath().toString()).toList();
        assertTrue(campos.containsAll(List.of("clienteId", "repartidorId", "direccion", "comuna", "estado", "fecha", "total")));
        boleta.setClienteId(null);
        assertFalse(factory.getValidator().validateProperty(boleta, "clienteId").isEmpty());
    }

    @Test void validaLimitesDeTextos() {
        var boleta = boleta();
        boleta.setDireccion("x".repeat(301));
        boleta.setComuna("x".repeat(101));
        boleta.setEstado("x".repeat(51));
        assertEquals(3, factory.getValidator().validate(boleta).size());
    }

    @Test void rechazaImportesNegativosExcesivosONoRepresentables() {
        var boleta = boleta();
        for (var importe : List.of(new BigDecimal("-1"), new BigDecimal("1.001"), new BigDecimal("100000000000000000"))) {
            boleta.setTotal(importe);
            boleta.getDetalles().getFirst().setPrecio(importe);
            assertFalse(factory.getValidator().validateProperty(boleta, "total").isEmpty());
            assertFalse(factory.getValidator().validateProperty(boleta.getDetalles().getFirst(), "precio").isEmpty());
        }
    }

    @Test void validaDetallesEnCascada() {
        var boleta = boleta();
        var detalle = boleta.getDetalles().getFirst();
        detalle.setProductoId(null);
        detalle.setCantidad(0);
        detalle.setPrecio(null);
        var campos = factory.getValidator().validate(boleta).stream()
                .map(v -> v.getPropertyPath().toString()).toList();
        assertTrue(campos.containsAll(List.of("detalles[0].productoId", "detalles[0].cantidad", "detalles[0].precio")));
        detalle.setProductoId(-1L);
        detalle.setCantidad(null);
        assertFalse(factory.getValidator().validateProperty(detalle, "productoId").isEmpty());
        assertFalse(factory.getValidator().validateProperty(detalle, "cantidad").isEmpty());
    }

    @Test void exigeAlMenosUnDetalleYBoletaEnCadaLinea() {
        var boleta = boleta();
        for (var detalle : List.copyOf(boleta.getDetalles())) boleta.quitarDetalle(detalle);
        assertFalse(factory.getValidator().validateProperty(boleta, "detalles").isEmpty());
        var detalle = new DetalleBoleta(91001L, 1, BigDecimal.ZERO);
        assertFalse(factory.getValidator().validateProperty(detalle, "boleta").isEmpty());
    }

    @Test void mantieneRelacionBidireccionalSinCompartirDetalles() {
        var origen = boleta();
        var destino = boleta();
        var detalle = origen.getDetalles().getFirst();
        assertSame(origen, detalle.getBoleta());
        assertThrows(IllegalArgumentException.class, () -> destino.agregarDetalle(detalle));
        origen.quitarDetalle(detalle);
        assertNull(detalle.getBoleta());
        destino.agregarDetalle(detalle);
        assertSame(destino, detalle.getBoleta());
    }
}
