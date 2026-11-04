package cl.gaselvolcan.usuarios.controlador;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import cl.gaselvolcan.usuarios.dto.UsuarioRespuesta;
import cl.gaselvolcan.usuarios.excepcion.ManejadorErrores;
import cl.gaselvolcan.usuarios.excepcion.RecursoNoEncontradoException;
import cl.gaselvolcan.usuarios.modelo.Rol;
import cl.gaselvolcan.usuarios.servicio.UsuarioService;

// Pruebas de las rutas: que cada caso responda el código HTTP correcto
// y que los errores vengan SIEMPRE como { "estado": ..., "mensaje": "..." }.
// MockMvc simula las peticiones HTTP sin encender el servidor.
class UsuarioControllerTest {

    private UsuarioService servicio;
    private MockMvc mvc;

    private static final String ANA = """
            {"nombre":"Ana","apellidos":"Soto","correo":"ana@gmail.com","contrasena":"Ana1234","rol":"CLIENTE"}
            """;

    @BeforeEach
    void preparar() {
        servicio = mock(UsuarioService.class);
        mvc = MockMvcBuilders.standaloneSetup(new UsuarioController(servicio))
                .setControllerAdvice(new ManejadorErrores())
                .build();
    }

    @Test
    void crearResponde201() throws Exception {
        when(servicio.crear(any())).thenReturn(new UsuarioRespuesta(6L, null, "Ana", "Soto",
                "ana@gmail.com", Rol.CLIENTE, null, null, null, true));

        mvc.perform(post("/api/v1/usuarios").contentType(MediaType.APPLICATION_JSON).content(ANA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.correo").value("ana@gmail.com"))
                .andExpect(jsonPath("$.contrasena").doesNotExist());
    }

    @Test
    void datosInvalidosResponden400ConMensaje() throws Exception {
        mvc.perform(post("/api/v1/usuarios").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\",\"apellidos\":\"Soto\",\"correo\":\"ana@gmail.com\",\"rol\":\"CLIENTE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.estado").value(400))
                .andExpect(jsonPath("$.mensaje").value("El nombre es obligatorio."));
    }

    @Test
    void rolQueNoExisteResponde400() throws Exception {
        mvc.perform(post("/api/v1/usuarios").contentType(MediaType.APPLICATION_JSON)
                        .content(ANA.replace("CLIENTE", "VENDEDOR")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.estado").value(400));
    }

    @Test
    void idQueNoExisteResponde404() throws Exception {
        when(servicio.buscarPorId(99L)).thenThrow(new RecursoNoEncontradoException("No existe un usuario con id 99."));

        mvc.perform(get("/api/v1/usuarios/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("No existe un usuario con id 99."));
    }

    @Test
    void idConLetrasResponde400EnJson() throws Exception {
        mvc.perform(get("/api/v1/usuarios/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.estado").value(400))
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    void metodoNoPermitidoResponde405EnJson() throws Exception {
        mvc.perform(patch("/api/v1/usuarios/1"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.estado").value(405));
    }

    @Test
    void contenidoQueNoEsJsonResponde415EnJson() throws Exception {
        mvc.perform(post("/api/v1/usuarios").contentType(MediaType.TEXT_PLAIN).content("hola"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.estado").value(415));
    }

    @Test
    void errorInesperadoResponde500SinDetallesInternos() throws Exception {
        when(servicio.listar()).thenThrow(new IllegalStateException("detalle interno"));

        mvc.perform(get("/api/v1/usuarios"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.mensaje").value("Ocurrió un error inesperado. Intenta de nuevo."));
    }

    @Test
    void eliminarResponde204() throws Exception {
        mvc.perform(delete("/api/v1/usuarios/1").principal(() -> "admin@gaselvolcan.cl"))
                .andExpect(status().isNoContent());
    }
}
