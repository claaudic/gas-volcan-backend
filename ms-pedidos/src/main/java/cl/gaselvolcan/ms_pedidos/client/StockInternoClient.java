package cl.gaselvolcan.ms_pedidos.client;

import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import cl.gaselvolcan.ms_pedidos.exception.CatalogoNoDisponibleException;

@Component
public class StockInternoClient {
    public record Linea(Long productoId, Integer cantidad) {}
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record Resultado(String operacionId, String estado, List<Linea> productos,
            Integer codigoResultado, String mensaje) {}
    private final RestClient http;
    public StockInternoClient(@Qualifier("catalogoInternoRestClient") RestClient http) { this.http = http; }
    public Resultado consultar(UUID id) { return llamar("GET", id, null); }
    public Resultado descontar(UUID id, List<Linea> lineas) { return llamar("POST", id, lineas); }
    public Resultado compensar(UUID id) { return llamar("PUT", id, null); }
    private Resultado llamar(String metodo, UUID id, List<Linea> lineas) {
        try {
            var peticion = http.method(org.springframework.http.HttpMethod.valueOf(metodo))
                    .uri("/internal/v1/stock/descuentos" + (metodo.equals("POST") ? "" : "/" + id)
                            + (metodo.equals("PUT") ? "/compensacion" : "")).accept(org.springframework.http.MediaType.APPLICATION_JSON);
            if (metodo.equals("POST")) peticion.header("Idempotency-Key", id.toString()).body(Map.of("productos", lineas));
            return peticion.exchange((q, r) -> {
                int codigo = r.getStatusCode().value();
                if (metodo.equals("GET") && codigo == 404) return null;
                Resultado resultado = r.bodyTo(Resultado.class);
                // Un 409 sin resultado de operacion no es un rechazo acreditado de stock.
                if (resultado == null || !id.toString().equals(resultado.operacionId())
                        || resultado.estado() == null || resultado.productos() == null
                        || resultado.codigoResultado() == null
                        || !(Set.of("APLICADO", "RECHAZADO", "COMPENSADO").contains(resultado.estado()))
                        || (codigo != 200 && !(metodo.equals("POST") && codigo == resultado.codigoResultado()
                                && "RECHAZADO".equals(resultado.estado()) && Set.of(400, 404, 409).contains(codigo)))) {
                    throw new CatalogoNoDisponibleException("Resultado de stock desconocido; la operacion se recuperara con la misma clave.");
                }
                return resultado;
            });
        } catch (RestClientException | IllegalArgumentException error) {
            throw new CatalogoNoDisponibleException("No se pudo acreditar el resultado de stock. Reintenta con la misma clave.");
        }
    }
}
