package cl.gaselvolcan.ms_catalogo.exception;

public class ConflictoVersionProductoException extends RuntimeException {
    public ConflictoVersionProductoException() {
        super("El producto cambio desde su lectura. Recarga sus datos antes de volver a editar.");
    }
}
