package cl.gaselvolcan.ms_catalogo.controlador;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import cl.gaselvolcan.ms_catalogo.dto.*;
import cl.gaselvolcan.ms_catalogo.exception.*;
import cl.gaselvolcan.ms_catalogo.servicio.CategoriaService;

// Pruebas HTTP simuladas: sin servidor, MySQL ni otros microservicios.
class CategoriaControllerTest {
    private CategoriaService servicio;
    private MockMvc mvc;
    private static final String RUTA = "/api/v1/categorias";
    private static final String DATOS = """
            {"nombre":"Prueba","descripcion":"Descripcion"}
            """;
    private final CategoriaRespuesta respuesta = new CategoriaRespuesta(2L, "Prueba", "Descripcion");

    @BeforeEach void preparar() {
        servicio = mock(CategoriaService.class);
        mvc = MockMvcBuilders.standaloneSetup(new CategoriaController(servicio))
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
    @Test void categoriaConProductosResponde400AlEliminar() throws Exception {
        doThrow(new SolicitudInvalidaException("Tiene productos asociados")).when(servicio).eliminar(2L);
        mvc.perform(delete(RUTA + "/2")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Tiene productos asociados"));
    }

}
