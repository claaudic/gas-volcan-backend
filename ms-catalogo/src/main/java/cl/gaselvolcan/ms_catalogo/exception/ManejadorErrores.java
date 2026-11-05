package cl.gaselvolcan.ms_catalogo.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

// Atrapa los errores de todos los controladores y los responde en JSON,
// siempre con el mismo formato: { "estado": ..., "mensaje": "..." }
// Así el frontend siempre puede leer "mensaje", pase lo que pase.
@RestControllerAdvice
public class ManejadorErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    // --- Errores del negocio (los lanza el servicio) ---

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorRespuesta> noEncontrado(RecursoNoEncontradoException error) {
        return responder(HttpStatus.NOT_FOUND, error.getMessage());
    }

    @ExceptionHandler(SolicitudInvalidaException.class)
    public ResponseEntity<ErrorRespuesta> solicitudInvalida(SolicitudInvalidaException error) {
        return responder(HttpStatus.BAD_REQUEST, error.getMessage());
    }

    // --- Errores de la petición (los detecta Spring antes de llegar al servicio) ---

    // Falla alguna anotación de los DTO (@NotBlank, @NotNull, @Size...).
    // Se devuelve el mensaje del primer campo con error.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorRespuesta> datosInvalidos(MethodArgumentNotValidException error) {
        String mensaje = error.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(campo -> campo.getDefaultMessage())
                .orElse("Los datos enviados no son válidos.");
        return responder(HttpStatus.BAD_REQUEST, mensaje);
    }

    // El cuerpo no es un JSON válido, o trae un valor de otro tipo (por ejemplo, un precio con texto)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorRespuesta> jsonInvalido(HttpMessageNotReadableException error) {
        return responder(HttpStatus.BAD_REQUEST,
                "El JSON no es válido. Revisa el formato y que cada valor sea del tipo correcto.");
    }

    // /api/v1/productos/abc: el id debía ser un número
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorRespuesta> tipoIncorrecto(MethodArgumentTypeMismatchException error) {
        return responder(HttpStatus.BAD_REQUEST,
                "El valor '" + error.getValue() + "' no es válido para " + error.getName() + ".");
    }

    // Por ejemplo, PATCH en una ruta que solo acepta GET, PUT o DELETE
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorRespuesta> metodoNoPermitido(HttpRequestMethodNotSupportedException error) {
        return responder(HttpStatus.METHOD_NOT_ALLOWED,
                "El método " + error.getMethod() + " no está permitido en esta ruta.");
    }

    // Se envió algo que no es JSON (falta Content-Type: application/json)
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorRespuesta> formatoNoSoportado(HttpMediaTypeNotSupportedException error) {
        return responder(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "El contenido debe enviarse como JSON.");
    }

    // La ruta no existe
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorRespuesta> rutaNoExiste(NoResourceFoundException error) {
        return responder(HttpStatus.NOT_FOUND, "La ruta /" + error.getResourcePath() + " no existe.");
    }

    // --- Cualquier otro error inesperado ---
    // Se registra completo en la consola, pero al cliente no se le muestran detalles internos.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorRespuesta> errorInesperado(Exception error) {
        log.error("Error inesperado", error);
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado. Intenta de nuevo.");
    }

    private ResponseEntity<ErrorRespuesta> responder(HttpStatus estado, String mensaje) {
        return ResponseEntity.status(estado).body(new ErrorRespuesta(estado.value(), mensaje));
    }
}
