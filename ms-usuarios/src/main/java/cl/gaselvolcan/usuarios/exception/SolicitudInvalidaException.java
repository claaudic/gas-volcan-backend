package cl.gaselvolcan.usuarios.exception;

// Se lanza cuando los datos no cumplen una regla del negocio → HTTP 400
public class SolicitudInvalidaException extends RuntimeException {

    public SolicitudInvalidaException(String mensaje) {
        super(mensaje);
    }
}
