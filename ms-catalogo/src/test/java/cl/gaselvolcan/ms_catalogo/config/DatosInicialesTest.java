package cl.gaselvolcan.ms_catalogo.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import cl.gaselvolcan.ms_catalogo.model.*;
import cl.gaselvolcan.ms_catalogo.repository.*;
import cl.gaselvolcan.ms_catalogo.service.*;

class DatosInicialesTest {
    static ValidatorFactory factory;
    CategoriaRepository categorias;
    ProductoRepository productos;
    DatosIniciales carga;
    Map<String, Categoria> guardadas;
    Map<String, Producto> catalogo;

    @BeforeAll static void iniciar() { factory = Validation.buildDefaultValidatorFactory(); }
    @AfterAll static void cerrar() { factory.close(); }
    @BeforeEach void preparar() {
        categorias = mock(CategoriaRepository.class);
        productos = mock(ProductoRepository.class);
        guardadas = new LinkedHashMap<>(); catalogo = new LinkedHashMap<>();
        when(categorias.findByNombre(anyString())).thenAnswer(c -> Optional.ofNullable(guardadas.get(c.getArgument(0))));
        when(categorias.saveAndFlush(any())).thenAnswer(c -> {
            Categoria categoria = c.getArgument(0);
            categoria.setId((long) guardadas.size() + 1);
            guardadas.put(categoria.getNombre(), categoria); return categoria;
        });
        when(categorias.findById(anyLong())).thenAnswer(c -> guardadas.values().stream().filter(v -> v.getId().equals(c.getArgument(0))).findFirst());
        when(productos.existsByCodigo(anyString())).thenAnswer(c -> catalogo.containsKey(c.getArgument(0)));
        when(productos.saveAndFlush(any())).thenAnswer(c -> {
            Producto producto = c.getArgument(0); catalogo.put(producto.getCodigo(), producto); return producto;
        });
        var validador = new ValidadorCatalogo(factory.getValidator());
        carga = new DatosIniciales(categorias, productos, new ProductoService(productos, categorias, validador), validador);
    }
    @Test void cargaExactamenteCuatroCategoriasYCatorceProductos() throws Exception {
        carga.run();
        assertEquals(Set.of("Cilindros de Gas", "Reguladores", "Mangueras y Conexiones", "Accesorios"), guardadas.keySet());
        assertEquals(14, catalogo.size());
        for (var original : DatosIniciales.leerCatalogo()) {
            Producto producto = catalogo.get(original.codigo());
            assertEquals(original.nombre(), producto.getNombre());
            assertEquals(original.descripcion(), producto.getDescripcion());
            assertEquals(original.categoria(), producto.getCategoria().getNombre());
            assertEquals(original.precioResidencial(), producto.getPrecioResidencial());
            assertEquals(original.precioComercial(), producto.getPrecioComercial());
            assertEquals(original.stock(), producto.getStock());
            assertEquals(original.precioOferta(), producto.getPrecioOferta());
            assertEquals(original.etiquetaOferta(), producto.getEtiquetaOferta());
            assertNull(producto.getStockCritico());
            assertEquals(original.unidad(), producto.getUnidad());
            assertEquals("", producto.getImagen());
            assertTrue(producto.getActivo());
        }
        assertEquals("Kit", catalogo.get("MG004").getUnidad());
        assertEquals(4, catalogo.values().stream().filter(v -> v.getPrecioOferta() != null).count());
    }
    @Test void recursoConservaCodigosPreciosStockYOfertasOriginales() throws Exception {
        var datos = DatosIniciales.leerCatalogo();
        String[] codigos = {"CL001","CL002","CL003","CL004","RG001","RG002","RG003","MG001","MG002","MG003","MG004","AC001","AC002","AC003"};
        int[] residencial = {6500,12000,16000,45000,8990,18990,14990,3990,6990,990,12990,12990,1490,19990};
        int[] comercial = {6000,11000,14500,40000,8200,17000,13500,3500,6200,800,11500,11000,1200,17000};
        int[] stock = {80,200,90,30,45,12,18,80,50,200,25,20,60,8};
        assertEquals(14, datos.length);
        for (int i=0; i<datos.length; i++) {
            assertEquals(codigos[i], datos[i].codigo());
            assertEquals(BigDecimal.valueOf(residencial[i]), datos[i].precioResidencial());
            assertEquals(BigDecimal.valueOf(comercial[i]), datos[i].precioComercial());
            assertEquals(stock[i], datos[i].stock());
        }
        assertEquals(new BigDecimal("10500"), datos[1].precioOferta());
        assertEquals(new BigDecimal("14500"), datos[2].precioOferta());
        assertEquals(new BigDecimal("10990"), datos[10].precioOferta());
        assertEquals(new BigDecimal("17990"), datos[13].precioOferta());
    }
    @Test void reiniciarNoDuplicaNiSobrescribeNingunCampo() throws Exception {
        carga.run();
        var existente = catalogo.get("CL002");
        existente.setPrecioResidencial(new BigDecimal("99999"));
        existente.setPrecioComercial(new BigDecimal("88888"));
        existente.setStock(3);
        existente.setPrecioOferta(new BigDecimal("77777"));
        existente.setActivo(false);
        carga.run();
        assertEquals(new BigDecimal("99999"), existente.getPrecioResidencial());
        assertEquals(new BigDecimal("88888"), existente.getPrecioComercial());
        assertEquals(3, existente.getStock());
        assertEquals(new BigDecimal("77777"), existente.getPrecioOferta());
        assertFalse(existente.getActivo());
        verify(productos, times(14)).saveAndFlush(any());
        verify(categorias, times(4)).saveAndFlush(any());
    }
    @Test void completaCatalogoParcialSinModificarExistentes() throws Exception {
        carga.run();
        catalogo.remove("AC003");
        carga.run();
        assertEquals(14, catalogo.size());
        verify(productos, times(15)).saveAndFlush(any());
        verify(categorias, times(4)).saveAndFlush(any());
    }
    @Test void habilitadoPorDefectoYDesactivable() {
        var condicion = DatosIniciales.class.getAnnotation(ConditionalOnProperty.class);
        assertEquals("catalogo.carga-inicial.enabled", condicion.name()[0]);
        assertEquals("true", condicion.havingValue());
        assertTrue(condicion.matchIfMissing());
    }
}
