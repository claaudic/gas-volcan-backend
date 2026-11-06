package cl.gaselvolcan.usuarios.exception;

// Se lanza cuando lo que se busca no existe → HTTP 404
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
