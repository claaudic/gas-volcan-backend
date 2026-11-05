package cl.gaselvolcan.ms_catalogo.controlador;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import cl.gaselvolcan.ms_catalogo.dto.*;
import cl.gaselvolcan.ms_catalogo.exception.*;
import cl.gaselvolcan.ms_catalogo.servicio.ProductoService;

// Pruebas HTTP simuladas: sin servidor, MySQL ni otros microservicios.
class ProductoControllerTest {
    private ProductoService servicio;
    private MockMvc mvc;
    private static final String RUTA = "/api/v1/productos";
    private static final String DATOS = """
            {"codigo":"TEST","nombre":"Prueba","descripcion":"Descripcion","precioResidencial":10.00,"precioComercial":9.00,"stock":5,"activo":true,"categoriaId":1,"unidad":"Unidad","imagen":""}
            """;
    private final ProductoRespuesta respuesta = new ProductoRespuesta(2L, "TEST", "Prueba", "Descripcion", new BigDecimal("10.00"), new BigDecimal("9.00"), 5, true, new CategoriaRespuesta(1L, "Prueba", "Descripcion"));

    @BeforeEach void preparar() {
        servicio = mock(ProductoService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ProductoController(servicio))
                .setControllerAdvice(new ManejadorErrores()).build();
    }

    @Test void listarResponde200ConListaJson() throws Exception {
        when(servicio.listar()).thenReturn(List.of(respuesta));
        mvc.perform(get(RUTA)).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].nombre").value("Prueba"));
        verify(servicio).listar();
    }
    @Test void listarSinDatosResponde200ConListaVacia() throws Exception {
        when(servicio.listar()).thenReturn(List.of());
        mvc.perform(get(RUTA)).andExpect(status().isOk()).andExpect(content().json("[]"));
    }
    @Test void buscarResponde200() throws Exception {
        when(servicio.buscarPorId(2L)).thenReturn(respuesta);
        mvc.perform(get(RUTA + "/2")).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2));
        verify(servicio).buscarPorId(2L);
    }
    @Test void crearResponde201YDelegaDto() throws Exception {
        when(servicio.crear(any())).thenReturn(respuesta);
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(DATOS))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.nombre").value("Prueba"));
        verify(servicio).crear(argThat(datos -> datos.nombre().equals("Prueba")));
    }
    @Test void actualizarResponde200YDelegaIdYDto() throws Exception {
        when(servicio.actualizar(eq(2L), any())).thenReturn(respuesta);
        mvc.perform(put(RUTA + "/2").contentType(MediaType.APPLICATION_JSON).content(DATOS))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(2));
        verify(servicio).actualizar(eq(2L), argThat(datos -> datos.nombre().equals("Prueba")));
    }
    @Test void eliminarResponde204SinCuerpo() throws Exception {
        mvc.perform(delete(RUTA + "/2")).andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(servicio).eliminar(2L);
    }
    @Test void buscarActualizarYEliminarInexistenteResponden404() throws Exception {
        when(servicio.buscarPorId(99L)).thenThrow(new RecursoNoEncontradoException("No existe"));
        when(servicio.actualizar(eq(99L), any())).thenThrow(new RecursoNoEncontradoException("No existe"));
        doThrow(new RecursoNoEncontradoException("No existe")).when(servicio).eliminar(99L);
        for (var solicitud : List.of(get(RUTA + "/99"), put(RUTA + "/99").contentType(MediaType.APPLICATION_JSON).content(DATOS), delete(RUTA + "/99"))) {
            mvc.perform(solicitud).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.estado").value(404)).andExpect(jsonPath("$.mensaje").value("No existe"));
        }
    }
    @Test void duplicadosAlCrearYActualizarResponden400() throws Exception {
        when(servicio.crear(any())).thenThrow(new SolicitudInvalidaException("Duplicado"));
        when(servicio.actualizar(eq(2L), any())).thenThrow(new SolicitudInvalidaException("Duplicado"));
        for (var solicitud : List.of(post(RUTA), put(RUTA + "/2"))) {
            mvc.perform(solicitud.contentType(MediaType.APPLICATION_JSON).content(DATOS))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.estado").value(400))
                    .andExpect(jsonPath("$.mensaje").value("Duplicado"));
        }
    }
    @Test void nombreVacioEnPostYPutResponde400SinLlamarServicio() throws Exception {
        for (var solicitud : List.of(post(RUTA), put(RUTA + "/2"))) {
            mvc.perform(solicitud.contentType(MediaType.APPLICATION_JSON).content(DATOS.replace("Prueba", " ")))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.estado").value(400))
                    .andExpect(jsonPath("$.mensaje").value("El nombre es obligatorio."));
        }
        verifyNoInteractions(servicio);
    }
    @Test void cuerpoVacioOJsonMalformadoResponde400() throws Exception {
        for (String datos : List.of("", "{", "{}")) {
            mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(datos))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.estado").value(400));
        }
        verifyNoInteractions(servicio);
    }
    @Test void idConLetrasResponde400() throws Exception {
        mvc.perform(get(RUTA + "/abc")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.estado").value(400));
        verifyNoInteractions(servicio);
    }
    @Test void metodoNoPermitidoResponde405() throws Exception {
        mvc.perform(patch(RUTA + "/2")).andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.estado").value(405));
    }
    @Test void contenidoNoJsonResponde415() throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.TEXT_PLAIN).content("texto"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.estado").value(415));
        verifyNoInteractions(servicio);
    }
    @Test void categoriaInexistenteAlCrearOActualizarResponde404() throws Exception {
        when(servicio.crear(any())).thenThrow(new RecursoNoEncontradoException("Categoria inexistente"));
        when(servicio.actualizar(eq(2L), any())).thenThrow(new RecursoNoEncontradoException("Categoria inexistente"));
        for (var solicitud : List.of(post(RUTA), put(RUTA + "/2"))) {
            mvc.perform(solicitud.contentType(MediaType.APPLICATION_JSON).content(DATOS))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.estado").value(404));
        }
    }
    @Test void camposInvalidosEnPostYPutResponden400SinLlamarServicio() throws Exception {
        var invalidos = List.of(
                DATOS.replace("TEST", " "),
                DATOS.replace("10.00", "-1"),
                DATOS.replace("9.00", "1.001"),
                DATOS.replace("10.00", "null"),
                DATOS.replace("9.00", "null"),
                DATOS.replace("10.00", "\"texto\""),
                DATOS.replace("\"stock\":5", "\"stock\":-1"),
                DATOS.replace("\"stock\":5", "\"stock\":null"),
                DATOS.replace("true", "null"),
                DATOS.replace("\"categoriaId\":1", "\"categoriaId\":0"),
                DATOS.replace("\"categoriaId\":1", "\"categoriaId\":null"));
        for (String datos : invalidos) {
            for (var solicitud : List.of(post(RUTA), put(RUTA + "/2"))) {
                mvc.perform(solicitud.contentType(MediaType.APPLICATION_JSON).content(datos))
                        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.estado").value(400));
            }
        }
        verifyNoInteractions(servicio);
    }
    @Test void respuestaIncluyePreciosStockYCategoria() throws Exception {
        when(servicio.buscarPorId(2L)).thenReturn(respuesta);
        mvc.perform(get(RUTA + "/2")).andExpect(status().isOk())
                .andExpect(jsonPath("$.precioResidencial").value(10.0))
                .andExpect(jsonPath("$.precioComercial").value(9.0))
                .andExpect(jsonPath("$.stock").value(5))
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.categoria.id").value(1));
    }

    @Test void recibeYDevuelveNuevosAtributos() throws Exception {
        var extendida = new ProductoRespuesta(2L, "TEST", "Prueba", "Descripcion",
                BigDecimal.TEN, BigDecimal.TEN, 5, true,
                new CategoriaRespuesta(1L, "Prueba", null), new BigDecimal("8.50"),
                "Oferta", 2, "Kit", "/img/test.jpg");
        when(servicio.crear(any())).thenReturn(extendida);
        String datos = DATOS.replace("\"unidad\":\"Unidad\"", "\"unidad\":\"Kit\"")
                .replace("\"imagen\":\"\"", "\"imagen\":\"/img/test.jpg\",\"precioOferta\":8.50,\"etiquetaOferta\":\"Oferta\",\"stockCritico\":2");
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(datos))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.precioOferta").value(8.5))
                .andExpect(jsonPath("$.etiquetaOferta").value("Oferta"))
                .andExpect(jsonPath("$.stockCritico").value(2))
                .andExpect(jsonPath("$.unidad").value("Kit"))
                .andExpect(jsonPath("$.imagen").value("/img/test.jpg"));
        verify(servicio).crear(argThat(d -> d.precioOferta().compareTo(new BigDecimal("8.50")) == 0
                && d.stockCritico() == 2 && d.unidad().equals("Kit")));
    }
    @Test void disponibilidad200YDelegaCantidad() throws Exception {
        when(servicio.consultarDisponibilidad(2L, 3)).thenReturn(new DisponibilidadRespuesta(2L, "TEST", 3, 5, true, true));
        mvc.perform(get(RUTA + "/2/disponibilidad").param("cantidad", "3"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.disponible").value(true))
                .andExpect(jsonPath("$.stock").value(5)).andExpect(jsonPath("$.cantidadSolicitada").value(3));
        verify(servicio).consultarDisponibilidad(2L, 3);
    }
    @Test void disponibilidadCantidadPorDefectoUno() throws Exception {
        when(servicio.consultarDisponibilidad(2L, 1)).thenReturn(new DisponibilidadRespuesta(2L, "TEST", 1, 0, true, false));
        mvc.perform(get(RUTA + "/2/disponibilidad")).andExpect(status().isOk())
                .andExpect(jsonPath("$.disponible").value(false));
        verify(servicio).consultarDisponibilidad(2L, 1);
    }
    @Test void disponibilidadInexistente404() throws Exception {
        when(servicio.consultarDisponibilidad(99L, 1)).thenThrow(new RecursoNoEncontradoException("No existe"));
        mvc.perform(get(RUTA + "/99/disponibilidad")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.estado").value(404));
    }
    @Test void disponibilidadCantidadInvalida400() throws Exception {
        when(servicio.consultarDisponibilidad(eq(2L), any())).thenThrow(new SolicitudInvalidaException("Cantidad invalida"));
        for (String cantidad : List.of("0", "-1", "abc", "1.5", "2147483648")) {
            mvc.perform(get(RUTA + "/2/disponibilidad").param("cantidad", cantidad))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.estado").value(400));
        }
    }
}
