package cl.gaselvolcan.ms_catalogo.service;

import org.springframework.stereotype.Component;
import jakarta.validation.Validator;
import cl.gaselvolcan.ms_catalogo.exception.SolicitudInvalidaException;

// Valida tambien las llamadas al servicio que no provienen de HTTP.
@Component
public class ValidadorCatalogo {
    private final Validator validator;

    public ValidadorCatalogo(Validator validator) { this.validator = validator; }

    public void validar(Object datos) {
        if (datos == null) throw new SolicitudInvalidaException("Los datos son obligatorios.");
        var errores = validator.validate(datos);
        if (!errores.isEmpty()) {
            String mensaje = errores.stream()
                    .map(error -> error.getPropertyPath() + ": " + error.getMessage())
                    .sorted().findFirst().orElseThrow();
            throw new SolicitudInvalidaException(mensaje);
        }
    }

    public void validarId(Long id) {
        if (id == null || id <= 0) throw new SolicitudInvalidaException("El id debe ser positivo.");
    }
}
