package cl.gaselvolcan.usuarios.excepcion;

// Formato de error acordado con Nicolás: { "estado": 404, "mensaje": "..." }
public record ErrorRespuesta(int estado, String mensaje) {
}
