package cl.gaselvolcan.ms_catalogo.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataIntegrityViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import cl.gaselvolcan.ms_catalogo.dto.*;
import cl.gaselvolcan.ms_catalogo.exception.*;
import cl.gaselvolcan.ms_catalogo.model.*;
import cl.gaselvolcan.ms_catalogo.repository.*;

class CatalogoServiceTest {
    static ValidatorFactory factory;
    CategoriaRepository categorias;
    ProductoRepository productos;
    CategoriaService categoriaService;
    ProductoService productoService;
    Categoria categoria;
    Producto producto;

    @BeforeAll static void iniciar() { factory = Validation.buildDefaultValidatorFactory(); }
    @AfterAll static void cerrar() { factory.close(); }

    @BeforeEach void preparar() {
        categorias = mock(CategoriaRepository.class);
        productos = mock(ProductoRepository.class);
        var validador = new ValidadorCatalogo(factory.getValidator());
        categoriaService = new CategoriaService(categorias, productos, validador);
        productoService = new ProductoService(productos, categorias, validador);
        categoria = new Categoria("Prueba", "Descripcion");
        categoria.setId(1L);
        producto = new Producto("TEST", "Prueba", null, BigDecimal.TEN, BigDecimal.ONE, 5, categoria);
        producto.setId(2L);
        when(categorias.findById(1L)).thenReturn(Optional.of(categoria));
        when(productos.findById(2L)).thenReturn(Optional.of(producto));
        when(categorias.saveAndFlush(any(Categoria.class))).thenAnswer(call -> call.getArgument(0));
        when(productos.saveAndFlush(any(Producto.class))).thenAnswer(call -> call.getArgument(0));
    }

    ProductoSolicitud solicitud(String codigo, BigDecimal residencial, BigDecimal comercial, Integer stock, Boolean activo, Long categoriaId) {
        return new ProductoSolicitud(codigo, " Nombre ", "Descripcion", residencial, comercial, stock, activo, categoriaId);
    }
    ProductoSolicitud solicitud(String codigo) { return solicitud(codigo, BigDecimal.TEN, BigDecimal.ONE, 0, true, 1L); }

    @Test void crearCategoriaLimpiaNombreYDevuelveDto() {
        var respuesta = categoriaService.crear(new CategoriaSolicitud(" Nueva ", "Descripcion"));
        assertEquals("Nueva", respuesta.nombre());
        verify(categorias).existsByNombre("Nueva");
        verify(categorias).saveAndFlush(any(Categoria.class));
    }
    @Test void actualizarCategoriaPermiteSuPropioNombre() {
        var respuesta = categoriaService.actualizar(1L, new CategoriaSolicitud("Prueba", "Cambio"));
        assertEquals("Cambio", respuesta.descripcion());
        verify(categorias).existsByNombreAndIdNot("Prueba", 1L);
    }
    @Test void rechazaNombresDeCategoriaDuplicados() {
        when(categorias.existsByNombre("Prueba")).thenReturn(true);
        when(categorias.existsByNombreAndIdNot("Prueba", 1L)).thenReturn(true);
        assertThrows(SolicitudInvalidaException.class, () -> categoriaService.crear(new CategoriaSolicitud("Prueba", null)));
        assertThrows(SolicitudInvalidaException.class, () -> categoriaService.actualizar(1L, new CategoriaSolicitud("Prueba", null)));
        verify(categorias, never()).saveAndFlush(any());
    }
    @Test void rechazaCategoriaInvalida() {
        assertThrows(SolicitudInvalidaException.class, () -> categoriaService.crear(new CategoriaSolicitud(" ", null)));
        assertThrows(SolicitudInvalidaException.class, () -> categoriaService.actualizar(1L, new CategoriaSolicitud("x".repeat(256), null)));
        verify(categorias, never()).saveAndFlush(any());
    }
    @Test void impideEliminarCategoriaConProductosInclusoInactivos() {
        when(productos.existsByCategoriaId(1L)).thenReturn(true);
        assertThrows(SolicitudInvalidaException.class, () -> categoriaService.eliminar(1L));
        verify(categorias, never()).delete(any());
    }
    @Test void eliminaCategoriaSinProductos() {
        categoriaService.eliminar(1L);
        verify(categorias).delete(categoria);
        verify(categorias).flush();
    }
    @Test void manejaAsociacionConcurrenteAlEliminarCategoria() {
        doThrow(new DataIntegrityViolationException("FK")).when(categorias).flush();
        assertThrows(SolicitudInvalidaException.class, () -> categoriaService.eliminar(1L));
    }
    @Test void manejaNombreDuplicadoConcurrente() {
        when(categorias.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique"));
        assertThrows(SolicitudInvalidaException.class, () -> categoriaService.crear(new CategoriaSolicitud("Prueba", null)));
    }
    @Test void crearProductoConservaPreciosStockYRelacion() {
        var respuesta = productoService.crear(solicitud(" TEST "));
        assertEquals("TEST", respuesta.codigo());
        assertEquals("Nombre", respuesta.nombre());
        assertEquals(BigDecimal.TEN, respuesta.precioResidencial());
        assertEquals(BigDecimal.ONE, respuesta.precioComercial());
        assertEquals(0, respuesta.stock());
        assertTrue(respuesta.activo());
        assertEquals(1L, respuesta.categoria().id());
    }
    @Test void actualizarProductoPermiteCodigoPropioYCambiaCategoria() {
        var otra = new Categoria("Otra", null); otra.setId(3L);
        when(categorias.findById(3L)).thenReturn(Optional.of(otra));
        var respuesta = productoService.actualizar(2L, solicitud("TEST", BigDecimal.ZERO, BigDecimal.TEN, 8, false, 3L));
        assertEquals(2L, respuesta.id());
        assertEquals(3L, respuesta.categoria().id());
        assertEquals(8, respuesta.stock());
        assertFalse(respuesta.activo());
        verify(productos).existsByCodigoAndIdNot("TEST", 2L);
    }
    @Test void rechazaCodigoDuplicadoAlCrearYActualizar() {
        when(productos.existsByCodigo("TEST")).thenReturn(true);
        when(productos.existsByCodigoAndIdNot("TEST", 2L)).thenReturn(true);
        assertThrows(SolicitudInvalidaException.class, () -> productoService.crear(solicitud("TEST")));
        assertThrows(SolicitudInvalidaException.class, () -> productoService.actualizar(2L, solicitud("TEST")));
        verify(productos, never()).saveAndFlush(any());
    }
    @Test void manejaCodigoDuplicadoConcurrenteAlCrearYActualizar() {
        when(productos.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique"));
        assertThrows(SolicitudInvalidaException.class, () -> productoService.crear(solicitud("TEST")));
        assertThrows(SolicitudInvalidaException.class, () -> productoService.actualizar(2L, solicitud("TEST")));
    }
    @Test void rechazaCategoriaInexistenteAlCrearYActualizarProducto() {
        var datos = solicitud("TEST", BigDecimal.ONE, BigDecimal.TEN, 1, true, 99L);
        assertThrows(RecursoNoEncontradoException.class, () -> productoService.crear(datos));
        assertThrows(RecursoNoEncontradoException.class, () -> productoService.actualizar(2L, datos));
        verify(productos, never()).saveAndFlush(any());
        assertEquals("Prueba", producto.getNombre());
    }
    @Test void rechazaPreciosNegativosNulosYConMasDeDosDecimales() {
        for (BigDecimal precio : new BigDecimal[] {null, new BigDecimal("-1"), new BigDecimal("1.001"), new BigDecimal("100000000000000000")}) {
            assertThrows(SolicitudInvalidaException.class, () -> productoService.crear(solicitud("TEST", precio, BigDecimal.ONE, 1, true, 1L)));
            assertThrows(SolicitudInvalidaException.class, () -> productoService.actualizar(2L, solicitud("TEST", BigDecimal.ONE, precio, 1, true, 1L)));
        }
        verify(productos, never()).saveAndFlush(any());
    }
    @Test void rechazaStockNegativoONulo() {
        for (Integer stock : new Integer[] {-1, null}) {
            assertThrows(SolicitudInvalidaException.class, () -> productoService.crear(solicitud("TEST", BigDecimal.ONE, BigDecimal.ONE, stock, true, 1L)));
        }
        verify(productos, never()).saveAndFlush(any());
    }
    @Test void rechazaCamposObligatoriosYDatosNulos() {
        assertThrows(SolicitudInvalidaException.class, () -> productoService.crear(null));
        assertThrows(SolicitudInvalidaException.class, () -> categoriaService.crear(null));
        assertThrows(SolicitudInvalidaException.class, () -> productoService.crear(solicitud(" ")));
        assertThrows(SolicitudInvalidaException.class, () -> productoService.crear(new ProductoSolicitud("TEST", " ", null, BigDecimal.ONE, BigDecimal.ONE, 1, true, 1L)));
        assertThrows(SolicitudInvalidaException.class, () -> productoService.crear(solicitud("TEST", BigDecimal.ONE, BigDecimal.ONE, 1, null, 1L)));
        assertThrows(SolicitudInvalidaException.class, () -> productoService.crear(solicitud("TEST", BigDecimal.ONE, BigDecimal.ONE, 1, true, null)));
        verify(productos, never()).saveAndFlush(any());
    }
    @Test void recursosInexistentesEnBuscarActualizarYEliminar() {
        assertThrows(RecursoNoEncontradoException.class, () -> productoService.buscarPorId(99L));
        assertThrows(RecursoNoEncontradoException.class, () -> productoService.actualizar(99L, solicitud("TEST")));
        assertThrows(RecursoNoEncontradoException.class, () -> productoService.eliminar(99L));
        assertThrows(RecursoNoEncontradoException.class, () -> categoriaService.buscarPorId(99L));
        assertThrows(RecursoNoEncontradoException.class, () -> categoriaService.actualizar(99L, new CategoriaSolicitud("Prueba", null)));
        assertThrows(RecursoNoEncontradoException.class, () -> categoriaService.eliminar(99L));
    }
    @Test void rechazaIdsInvalidos() {
        for (Long id : new Long[] {null, 0L, -1L}) {
            assertThrows(SolicitudInvalidaException.class, () -> productoService.buscarPorId(id));
            assertThrows(SolicitudInvalidaException.class, () -> categoriaService.eliminar(id));
        }
    }
    @Test void listarYBuscarDevuelvenDto() {
        when(categorias.findAll()).thenReturn(List.of(categoria));
        when(productos.findAll()).thenReturn(List.of(producto));
        assertEquals(categoriaService.buscarPorId(1L), categoriaService.listar().getFirst());
        assertEquals(productoService.buscarPorId(2L), productoService.listar().getFirst());
    }
    @Test void eliminaProductoExistente() {
        productoService.eliminar(2L);
        verify(productos).delete(producto);
        verify(productos).flush();
    }
    @Test void nuevosCamposSeGuardanYSeDevuelvenAlCrearYActualizar() {
        var datos = new ProductoSolicitud("TEST", "Prueba", "Descripcion", BigDecimal.TEN, BigDecimal.TEN,
                7, true, 1L, new BigDecimal("8.50"), "Oferta", 2, "Kit", "/img/test.jpg");
        for (var respuesta : List.of(productoService.crear(datos), productoService.actualizar(2L, datos))) {
            assertEquals(new BigDecimal("8.50"), respuesta.precioOferta());
            assertEquals("Oferta", respuesta.etiquetaOferta());
            assertEquals(2, respuesta.stockCritico());
            assertEquals("Kit", respuesta.unidad());
            assertEquals("/img/test.jpg", respuesta.imagen());
        }
        var sinOferta = productoService.actualizar(2L, solicitud("TEST"));
        assertNull(sinOferta.precioOferta());
        assertNull(sinOferta.etiquetaOferta());
    }
    @Test void validaNuevosCamposDelDtoYEntidad() {
        var validator = factory.getValidator();
        var datos = new ProductoSolicitud("TEST", "Prueba", null, BigDecimal.TEN, BigDecimal.ONE,
                1, true, 1L, new BigDecimal("-1"), "x".repeat(256), -1, " ", "x".repeat(2049));
        assertThrows(SolicitudInvalidaException.class, () -> productoService.crear(datos));
        var campos = validator.validate(datos).stream().map(v -> v.getPropertyPath().toString()).toList();
        assertTrue(campos.containsAll(List.of("precioOferta", "etiquetaOferta", "stockCritico", "unidad", "imagen")));
        producto.setPrecioOferta(new BigDecimal("1.001"));
        producto.setStockCritico(-1);
        producto.setUnidad(" ");
        assertFalse(validator.validateProperty(producto, "precioOferta").isEmpty());
        assertFalse(validator.validateProperty(producto, "stockCritico").isEmpty());
        assertFalse(validator.validateProperty(producto, "unidad").isEmpty());
    }
    @Test void stockSuficienteEIgualDisponibleSinEscrituras() {
        assertTrue(productoService.consultarDisponibilidad(2L, 1).disponible());
        var limite = productoService.consultarDisponibilidad(2L, 5);
        assertTrue(limite.disponible());
        assertEquals("TEST", limite.codigo());
        assertEquals(5, limite.stock());
        assertEquals(5, limite.cantidadSolicitada());
        verify(productos, never()).saveAndFlush(any());
        assertEquals(5, producto.getStock());
    }
    @Test void stockInsuficienteOCeroNoDisponible() {
        assertFalse(productoService.consultarDisponibilidad(2L, 6).disponible());
        producto.setStock(0);
        assertFalse(productoService.consultarDisponibilidad(2L, 1).disponible());
    }
    @Test void productoInactivoNoDisponibleAunqueTengaStock() {
        producto.setActivo(false);
        assertFalse(productoService.consultarDisponibilidad(2L, 1).disponible());
    }
    @Test void disponibilidadValidaCantidadEId() {
        for (Integer cantidad : new Integer[] {null, 0, -1}) {
            assertThrows(SolicitudInvalidaException.class, () -> productoService.consultarDisponibilidad(2L, cantidad));
        }
        assertThrows(SolicitudInvalidaException.class, () -> productoService.consultarDisponibilidad(0L, 1));
        assertThrows(RecursoNoEncontradoException.class, () -> productoService.consultarDisponibilidad(99L, 1));
    }
}
