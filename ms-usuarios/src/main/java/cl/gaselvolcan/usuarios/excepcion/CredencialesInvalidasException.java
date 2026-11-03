package cl.gaselvolcan.usuarios.excepcion;

// Login o token incorrecto → HTTP 401
public class CredencialesInvalidasException extends RuntimeException {

    public CredencialesInvalidasException(String mensaje) {
        super(mensaje);
    }
}
