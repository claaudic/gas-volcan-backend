package cl.gaselvolcan.ms_pedidos.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import cl.gaselvolcan.ms_pedidos.config.CatalogoInternoConfig;
import cl.gaselvolcan.ms_pedidos.exception.CatalogoNoDisponibleException;

class StockInternoClientTest {
    private static final String BASE = "http://catalogo.test:8082/internal/v1/stock/descuentos";
    private MockRestServiceServer servidor;
    private StockInternoClient cliente;
    private final UUID id = UUID.randomUUID();
    @BeforeEach void preparar() {
        var tokens = new TokenServicioCatalogo("clave-interna-sintetica-exclusiva-de-pruebas-123456", "clave-usuarios-sintetica-exclusiva-de-pruebas-123456");
        var builder = new CatalogoInternoConfig().catalogoInternoRestClient(tokens, URI.create("http://catalogo.test:8082"),
                Duration.ofSeconds(1), Duration.ofSeconds(1)).mutate();
        servidor = MockRestServiceServer.bindTo(builder).build(); cliente = new StockInternoClient(builder.build());
    }
    @AfterEach void verificar() { servidor.verify(); }
    private String respuesta(String estado, int codigo) {
        return "{\"operacionId\":\"" + id + "\",\"estado\":\"" + estado
                + "\",\"productos\":[{\"productoId\":1,\"cantidad\":2}],\"codigoResultado\":" + codigo
                + ",\"mensaje\":\"ok\",\"creada\":\"2026-01-01T12:00:00Z\",\"compensada\":null}";
    }
    @Test void enviaContratoAutenticadoYLeeResultadoCompletoDelCatalogo() {
        servidor.expect(requestTo(BASE)).andExpect(method(HttpMethod.POST)).andExpect(header("Idempotency-Key", id.toString()))
                .andExpect(r -> assertTrue(r.getHeaders().getFirst("Authorization").startsWith("Bearer ")))
                .andExpect(content().json("{\"productos\":[{\"productoId\":1,\"cantidad\":2}]}"))
                .andRespond(withSuccess(respuesta("APLICADO", 200), MediaType.APPLICATION_JSON));
        assertEquals("APLICADO", cliente.descontar(id, List.of(new StockInternoClient.Linea(1L, 2))).estado());
    }
    @Test void consultaYCompensaConElMismoIdentificador() {
        servidor.expect(requestTo(BASE + "/" + id)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(respuesta("APLICADO", 200), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(BASE + "/" + id + "/compensacion")).andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess(respuesta("COMPENSADO", 200), MediaType.APPLICATION_JSON));
        assertEquals("APLICADO", cliente.consultar(id).estado());
        assertEquals("COMPENSADO", cliente.compensar(id).estado());
    }
    @Test void rechazo409DeStockSeLeeComoResultadoNoComoTimeout() {
        servidor.expect(requestTo(BASE)).andRespond(withStatus(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON).body(respuesta("RECHAZADO", 409)));
        assertEquals("RECHAZADO", cliente.descontar(id, List.of(new StockInternoClient.Linea(1L, 2))).estado());
    }
    @Test void consulta404NoPruebaCompensacionYErroresNoSeConfundenConResultados() {
        servidor.expect(requestTo(BASE + "/" + id)).andRespond(withResourceNotFound());
        servidor.expect(requestTo(BASE + "/" + id + "/compensacion")).andRespond(withResourceNotFound());
        assertNull(cliente.consultar(id)); assertThrows(CatalogoNoDisponibleException.class, () -> cliente.compensar(id));
    }
    @Test void timeoutYFirmaRechazadaConservanResultadoDesconocido() {
        servidor.expect(requestTo(BASE)).andRespond(withException(new java.net.SocketTimeoutException("detalle privado")));
        servidor.expect(requestTo(BASE)).andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                .body("{\"estado\":401,\"mensaje\":\"token invalido\"}"));
        for (int i = 0; i < 2; i++) assertThrows(CatalogoNoDisponibleException.class,
                () -> cliente.descontar(id, List.of(new StockInternoClient.Linea(1L, 2))));
    }
    @Test void otroIdOCuerpoIncompletoNoAcreditanUnDescuento() {
        servidor.expect(requestTo(BASE + "/" + id)).andRespond(withSuccess(respuesta("APLICADO", 200)
                .replace(id.toString(), UUID.randomUUID().toString()), MediaType.APPLICATION_JSON));
        servidor.expect(requestTo(BASE + "/" + id)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertThrows(CatalogoNoDisponibleException.class, () -> cliente.consultar(id));
        assertThrows(CatalogoNoDisponibleException.class, () -> cliente.consultar(id));
    }
}
