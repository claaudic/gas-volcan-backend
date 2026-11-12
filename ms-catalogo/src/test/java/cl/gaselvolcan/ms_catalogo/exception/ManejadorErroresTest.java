package cl.gaselvolcan.ms_catalogo.exception;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ManejadorErroresTest {
    private final ManejadorErrores manejador = new ManejadorErrores();
    @Test void versionObsoletaSeConvierteEn409() {
        var respuesta = manejador.versionObsoleta(new ConflictoVersionProductoException());
        assertEquals(409, respuesta.getStatusCode().value());
        assertEquals(409, respuesta.getBody().estado());
        assertTrue(respuesta.getBody().mensaje().contains("Recarga"));
    }

    @Test void solicitudInvalidaSeConvierteEn400() {
        var respuesta = manejador.solicitudInvalida(new SolicitudInvalidaException("Codigo duplicado"));
        assertEquals(400, respuesta.getStatusCode().value());
        assertEquals(new ErrorRespuesta(400, "Codigo duplicado"), respuesta.getBody());
    }
    @Test void recursoInexistenteSeConvierteEn404() {
        var respuesta = manejador.noEncontrado(new RecursoNoEncontradoException("No existe"));
        assertEquals(404, respuesta.getStatusCode().value());
        assertEquals(new ErrorRespuesta(404, "No existe"), respuesta.getBody());
    }
}
