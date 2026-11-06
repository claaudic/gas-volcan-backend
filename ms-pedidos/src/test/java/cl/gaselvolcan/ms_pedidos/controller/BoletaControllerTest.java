package cl.gaselvolcan.ms_pedidos.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import cl.gaselvolcan.ms_pedidos.dto.*;
import cl.gaselvolcan.ms_pedidos.exception.*;
import cl.gaselvolcan.ms_pedidos.service.BoletaService;

class BoletaControllerTest {
    private static final String RUTA = "/api/v1/boletas";
    private static final String DATOS = """
            {"clienteId":10,"direccion":"Direccion 123","comuna":"Comuna","estado":"ESTADO_PRUEBA",
             "repartidorId":null,"fecha":"2026-01-01T12:00:00",
             "detalles":[{"productoId":91001,"cantidad":2,"precio":19.99},
                         {"productoId":91002,"cantidad":3,"precio":3.10}]}
            """;
    private BoletaService servicio;
    private MockMvc mvc;
    private final BoletaRespuesta respuesta = new BoletaRespuesta(1L, 10L, "Direccion 123", "Comuna", "ESTADO_PRUEBA",
            null, LocalDateTime.of(2026, 1, 1, 12, 0), new BigDecimal("49.28"),
            List.of(new DetalleBoletaRespuesta(1L, 91001L, 2, new BigDecimal("19.99")),
                    new DetalleBoletaRespuesta(2L, 91002L, 3, new BigDecimal("3.10"))));

    @BeforeEach void preparar() {
        servicio = mock(BoletaService.class);
        mvc = MockMvcBuilders.standaloneSetup(new BoletaController(servicio))
                .setControllerAdvice(new ManejadorErrores()).build();
    }
    @Test void listar200() throws Exception {
        when(servicio.listar()).thenReturn(List.of(respuesta));
        mvc.perform(get(RUTA)).andExpect(status().isOk()).andExpect(jsonPath("$[0].total").value(49.28));
    }
    @Test void listarVacio200() throws Exception {
        when(servicio.listar()).thenReturn(List.of());
        mvc.perform(get(RUTA)).andExpect(status().isOk()).andExpect(content().json("[]"));
    }
    @Test void buscar200() throws Exception {
        when(servicio.buscarPorId(1L)).thenReturn(respuesta);
        mvc.perform(get(RUTA + "/1")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.detalles.length()").value(2))
                .andExpect(jsonPath("$.detalles[0].boleta").doesNotExist());
    }
    @Test void crear201ConDtoCompleto() throws Exception {
        when(servicio.crear(any())).thenReturn(respuesta);
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(DATOS))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.total").value(49.28));
        verify(servicio).crear(argThat(d -> d.clienteId() == 10L && d.detalles().size() == 2));
    }
    @Test void actualizar200() throws Exception {
        when(servicio.actualizar(eq(1L), any())).thenReturn(respuesta);
        mvc.perform(put(RUTA + "/1").contentType(MediaType.APPLICATION_JSON).content(DATOS))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(49.28));
        verify(servicio).actualizar(eq(1L), any());
    }
    @Test void eliminar204SinCuerpo() throws Exception {
        mvc.perform(delete(RUTA + "/1")).andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(servicio).eliminar(1L);
    }
    @Test void recursosInexistentes404() throws Exception {
        when(servicio.buscarPorId(99L)).thenThrow(new RecursoNoEncontradoException("No existe"));
        when(servicio.actualizar(eq(99L), any())).thenThrow(new RecursoNoEncontradoException("No existe"));
        doThrow(new RecursoNoEncontradoException("No existe")).when(servicio).eliminar(99L);
        for (var request : List.of(get(RUTA + "/99"), put(RUTA + "/99").contentType(MediaType.APPLICATION_JSON).content(DATOS), delete(RUTA + "/99"))) {
            mvc.perform(request).andExpect(status().isNotFound()).andExpect(jsonPath("$.estado").value(404))
                    .andExpect(jsonPath("$.mensaje").value("No existe"));
        }
    }
    @Test void validaDtoEnPostYPutAntesDelServicio() throws Exception {
        var invalidos = List.of(
                DATOS.replace("\"clienteId\":10", "\"clienteId\":null"),
                DATOS.replace("Direccion 123", " "), DATOS.replace("Comuna", " "),
                DATOS.replace("ESTADO_PRUEBA", " "),
                DATOS.replace("2026-01-01T12:00:00", "fecha-invalida"),
                DATOS.replace("\"repartidorId\":null", "\"repartidorId\":-1"),
                DATOS.replace("\"cantidad\":2", "\"cantidad\":0"),
                DATOS.replace("\"cantidad\":2", "\"cantidad\":null"),
                DATOS.replace("91001", "0"), "{}",
                "{\"clienteId\":10,\"direccion\":\"D\",\"comuna\":\"C\",\"estado\":\"E\",\"fecha\":\"2026-01-01T12:00:00\",\"detalles\":[]}",
                "{\"clienteId\":10,\"direccion\":\"D\",\"comuna\":\"C\",\"estado\":\"E\",\"fecha\":\"2026-01-01T12:00:00\",\"detalles\":[null]}");
        for (String datos : invalidos) {
            for (var request : List.of(post(RUTA), put(RUTA + "/1"))) {
                mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(datos))
                        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.estado").value(400))
                        .andExpect(jsonPath("$.mensaje").isNotEmpty());
            }
        }
        verifyNoInteractions(servicio);
    }
    @Test void jsonMalformadoOCuerpoAusente400() throws Exception {
        for (String datos : List.of("{", "", "null")) {
            mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(datos))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.estado").value(400));
        }
        verifyNoInteractions(servicio);
    }
    @Test void idNoNumerico400() throws Exception {
        mvc.perform(get(RUTA + "/abc")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.estado").value(400));
        verifyNoInteractions(servicio);
    }
    @Test void reglaNegocio400() throws Exception {
        when(servicio.crear(any())).thenThrow(new SolicitudInvalidaException("Total excesivo"));
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(DATOS))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.mensaje").value("Total excesivo"));
    }
    @Test void integridad400SinFiltrarDetalleInterno() throws Exception {
        when(servicio.crear(any())).thenThrow(new DataIntegrityViolationException("SQL interno"));
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(DATOS))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.estado").value(400))
                .andExpect(jsonPath("$.mensaje").value("No se pudo completar la operacion por una restriccion de integridad."));
    }
    @Test void metodoNoPermitido405() throws Exception {
        mvc.perform(patch(RUTA + "/1")).andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.estado").value(405));
    }
    @Test void contenidoNoJson415() throws Exception {
        mvc.perform(post(RUTA).contentType(MediaType.TEXT_PLAIN).content("texto"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.estado").value(415));
    }
    @Test void errorInesperado500SinDetalleInterno() throws Exception {
        when(servicio.listar()).thenThrow(new IllegalStateException("detalle privado"));
        mvc.perform(get(RUTA)).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.estado").value(500));
    }
    @Test void catalogoNoDisponible503() throws Exception {
        when(servicio.crear(any())).thenThrow(new CatalogoNoDisponibleException("Catalogo no disponible"));
        mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(DATOS))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.estado").value(503));
    }
    @Test void respuestaCatalogoInvalida502EnPut() throws Exception {
        when(servicio.actualizar(eq(1L), any())).thenThrow(new RespuestaCatalogoInvalidaException("Respuesta invalida"));
        mvc.perform(put(RUTA + "/1").contentType(MediaType.APPLICATION_JSON).content(DATOS))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.estado").value(502));
    }
    @Test void preciosAntiguosSeIgnoranYNoSonObligatorios() throws Exception {
        when(servicio.crear(any())).thenReturn(respuesta);
        for (String datos : List.of(DATOS.replace("19.99", "-1"), DATOS.replace("19.99", "null"),
                DATOS.replace(",\"precio\":19.99", "").replace(",\"precio\":3.10", ""))) {
            mvc.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content(datos)).andExpect(status().isCreated());
        }
    }

}
