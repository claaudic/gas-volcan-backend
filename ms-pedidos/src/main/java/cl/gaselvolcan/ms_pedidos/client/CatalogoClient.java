package cl.gaselvolcan.ms_pedidos.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import cl.gaselvolcan.ms_pedidos.dto.catalogo.*;
import cl.gaselvolcan.ms_pedidos.exception.*;

@Component
public class CatalogoClient {
    private final RestClient http;

    public CatalogoClient(@Qualifier("catalogoRestClient") RestClient http) {
        this.http = http;
    }

    public ProductoCatalogoRespuesta consultarProducto(Long id) {
        return consultar("/api/v1/productos/{id}", ProductoCatalogoRespuesta.class, id);
    }

    public DisponibilidadCatalogoRespuesta consultarDisponibilidad(Long id, int cantidad) {
        return consultar("/api/v1/productos/{id}/disponibilidad?cantidad={cantidad}",
                DisponibilidadCatalogoRespuesta.class, id, cantidad);
    }

    private <T> T consultar(String ruta, Class<T> tipo, Object... parametros) {
        try {
            T respuesta = http.get().uri(ruta, parametros).accept(MediaType.APPLICATION_JSON).retrieve()
                    .onStatus(estado -> estado.value() != 200, (peticion, respuestaHttp) -> {
                        int estado = respuestaHttp.getStatusCode().value();
                        if (estado == 404) {
                            throw new RecursoNoEncontradoException("No existe el producto solicitado en catalogo.");
                        }
                        if (estado >= 500) {
                            throw new CatalogoNoDisponibleException("El catalogo no esta disponible. Intenta nuevamente.");
                        }
                        throw new RespuestaCatalogoInvalidaException("El catalogo devolvio un estado HTTP inesperado.");
                    }).body(tipo);
            if (respuesta == null) {
                throw new RespuestaCatalogoInvalidaException("El catalogo devolvio una respuesta vacia.");
            }
            return respuesta;
        } catch (ResourceAccessException error) {
            throw new CatalogoNoDisponibleException("No fue posible conectar con el catalogo dentro del tiempo de espera.");
        } catch (RestClientException error) {
            throw new RespuestaCatalogoInvalidaException("No se pudo interpretar la respuesta del catalogo.");
        }
    }
}
