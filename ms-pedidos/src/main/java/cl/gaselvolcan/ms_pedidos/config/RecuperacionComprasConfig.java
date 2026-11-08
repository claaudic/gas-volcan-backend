package cl.gaselvolcan.ms_pedidos.config;

import cl.gaselvolcan.ms_pedidos.service.CompraService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration @EnableScheduling
public class RecuperacionComprasConfig {
    private final CompraService compras;
    private final boolean habilitada;
    public RecuperacionComprasConfig(CompraService compras,
            @Value("${pedidos.recuperacion.enabled:true}") boolean habilitada) {
        this.compras = compras; this.habilitada = habilitada;
    }
    @Scheduled(fixedDelayString = "${pedidos.recuperacion.intervalo-ms:30000}", initialDelayString = "${pedidos.recuperacion.intervalo-ms:30000}")
    public void recuperar() { if (habilitada) compras.recuperarPendientes(); }
}
