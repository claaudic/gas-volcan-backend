package cl.gaselvolcan.usuarios.exception;

// Login o token incorrecto → HTTP 401
public class CredencialesInvalidasException extends RuntimeException {

    public CredencialesInvalidasException(String mensaje) {
        super(mensaje);
    }
}
