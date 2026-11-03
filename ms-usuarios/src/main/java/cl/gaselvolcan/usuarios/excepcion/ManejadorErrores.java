package cl.gaselvolcan.usuarios.excepcion;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Atrapa los errores de todos los controladores y los responde en JSON,
// siempre con el mismo formato: { "estado": ..., "mensaje": "..." }
@RestControllerAdvice
public class ManejadorErrores {

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorRespuesta> noEncontrado(RecursoNoEncontradoException error) {
        return responder(HttpStatus.NOT_FOUND, error.getMessage());
    }

    @ExceptionHandler(SolicitudInvalidaException.class)
    public ResponseEntity<ErrorRespuesta> solicitudInvalida(SolicitudInvalidaException error) {
        return responder(HttpStatus.BAD_REQUEST, error.getMessage());
    }

    // Falla alguna anotación de UsuarioSolicitud (@NotBlank, @Email, @Size...).
    // Se devuelve el mensaje del primer campo con error.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorRespuesta> datosInvalidos(MethodArgumentNotValidException error) {
        String mensaje = error.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(campo -> campo.getDefaultMessage())
                .orElse("Los datos enviados no son válidos.");
        return responder(HttpStatus.BAD_REQUEST, mensaje);
    }

    // El JSON viene mal escrito o con un rol que no existe.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorRespuesta> jsonInvalido(HttpMessageNotReadableException error) {
        return responder(HttpStatus.BAD_REQUEST,
                "El JSON no es válido. Revisa el formato y que el rol sea ADMINISTRADOR, DESPACHADORA, REPARTIDOR o CLIENTE.");
    }

    private ResponseEntity<ErrorRespuesta> responder(HttpStatus estado, String mensaje) {
        return ResponseEntity.status(estado).body(new ErrorRespuesta(estado.value(), mensaje));
    }
}
