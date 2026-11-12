package cl.gaselvolcan.ms_pedidos.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.transaction.support.TransactionTemplate;
import cl.gaselvolcan.ms_pedidos.client.*;
import cl.gaselvolcan.ms_pedidos.config.UsuarioToken;
import cl.gaselvolcan.ms_pedidos.dto.*;
import cl.gaselvolcan.ms_pedidos.exception.RecursoNoEncontradoException;
import cl.gaselvolcan.ms_pedidos.exception.CatalogoNoDisponibleException;
import cl.gaselvolcan.ms_pedidos.model.*;
import cl.gaselvolcan.ms_pedidos.repository.BoletaRepository;
import cl.gaselvolcan.ms_pedidos.service.GestionBoletaService;

@SpringBootTest(properties = {
    "jwt.secret=clave-sintetica-gestion-boletas-1234567890", "internal.jwt.secret=clave-sintetica-interna-123456789012345",
    "spring.config.import=", "spring.datasource.url=jdbc:h2:mem:gestion-boletas;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never", "spring.jpa.show-sql=false",
    "pedidos.recuperacion.enabled=false"
})
class GestionBoletaIntegrationTest {
    @Autowired WebApplicationContext contexto;
    @Autowired BoletaRepository repositorio;
    @Autowired GestionBoletaService gestion;
    @Autowired org.springframework.transaction.PlatformTransactionManager manager;
    @MockitoBean UsuariosPedidosClient usuarios;
    @MockitoBean StockInternoClient stock;
    MockMvc mvc;
    Boleta boleta;
    static UsernamePasswordAuthenticationToken identidad(String rol, long id) {
        return new UsernamePasswordAuthenticationToken(new UsuarioToken(id, "persona@example.test", rol), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + rol)));
    }
    @BeforeEach void preparar() {
        repositorio.deleteAll();
        boleta = new Boleta(10L, "Direccion 123", "Comuna", "PENDIENTE", 4L,
                LocalDateTime.of(2026, 1, 1, 12, 0), new BigDecimal("12.50"));
        boleta.agregarDetalle(new DetalleBoleta(1001L, 2, new BigDecimal("6.25")));
        boleta = repositorio.saveAndFlush(boleta);
        when(usuarios.consultar(anyLong())).thenAnswer(c -> {
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            Long id = c.getArgument(0);
            return new UsuariosPedidosClient.UsuarioPedido(id, "Persona Prueba", "persona@example.test", "+56912345678",
                    id == 10L ? "CLIENTE" : "REPARTIDOR", true);
        });
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
    }
    @AfterEach void limpiar() { SecurityContextHolder.clearContext(); }
    String ruta(String fin) { return "/api/v1/boletas/" + boleta.getId() + "/" + fin; }
    String asignacion(long version) { return "{\"repartidorId\":5,\"versionEsperada\":" + version + "}"; }
    @Test void asignaConVersionCeroSinModificarDetallePrecioEntregaNiStock() throws Exception {
        assertEquals(0L, boleta.getVersion());
        mvc.perform(put(ruta("repartidor")).with(authentication(identidad("DESPACHADORA", 2)))
                .contentType(MediaType.APPLICATION_JSON).content(asignacion(0)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.boleta.repartidorId").value(5))
                .andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.boleta.total").value(12.50))
                .andExpect(jsonPath("$.boleta.detalles[0].cantidad").value(2))
                .andExpect(jsonPath("$.boleta.direccion").value("Direccion 123"))
                .andExpect(jsonPath("$.boleta.estado").value("PENDIENTE"));
        verifyNoInteractions(stock);
    }
    @Test void rechazaVersionObsoletaYConservaAsignacion() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(identidad("ADMINISTRADOR", 1));
        gestion.asignar(boleta.getId(), new AsignacionRepartidorSolicitud(5L, 0L));
        mvc.perform(put(ruta("repartidor")).with(authentication(identidad("ADMINISTRADOR", 1)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"repartidorId\":6,\"versionEsperada\":0}"))
                .andExpect(status().isConflict());
        assertEquals(5L, repositorio.findById(boleta.getId()).orElseThrow().getRepartidorId());
    }
    @Test void permiteRetirarAsignacionConNullExplicito() throws Exception {
        mvc.perform(put(ruta("repartidor")).with(authentication(identidad("ADMINISTRADOR", 1)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"repartidorId\":null,\"versionEsperada\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.boleta.repartidorId").isEmpty());
    }
    @ParameterizedTest @ValueSource(strings = {"{}", "{\"repartidorId\":5}", "{\"versionEsperada\":0}",
            "{\"repartidorId\":-1,\"versionEsperada\":0}", "{\"repartidorId\":5,\"versionEsperada\":-1}",
            "{\"repartidorId\":5,\"versionEsperada\":null}", "{\"repartidorId\":5,\"versionEsperada\":0.5}",
            "{\"repartidorId\":5,\"versionEsperada\":\"0\"}", "{\"repartidorId\":5,\"versionEsperada\":0,\"stock\":7}"})
    void entradasInvalidasNoEscriben(String cuerpo) throws Exception {
        mvc.perform(put(ruta("repartidor")).with(authentication(identidad("ADMINISTRADOR", 1)))
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo)).andExpect(status().isBadRequest());
        assertEquals(0L, repositorio.findById(boleta.getId()).orElseThrow().getVersion());
        verifyNoInteractions(stock);
    }
    @Test void rechazaRepartidorInactivoOConOtroRol() throws Exception {
        when(usuarios.consultar(5L)).thenReturn(new UsuariosPedidosClient.UsuarioPedido(5L, "Persona", "p@example.test", null, "CLIENTE", true));
        mvc.perform(put(ruta("repartidor")).with(authentication(identidad("ADMINISTRADOR", 1)))
                .contentType(MediaType.APPLICATION_JSON).content(asignacion(0))).andExpect(status().isBadRequest());
        when(usuarios.consultar(5L)).thenReturn(new UsuariosPedidosClient.UsuarioPedido(5L, "Persona", "p@example.test", null, "REPARTIDOR", false));
        mvc.perform(put(ruta("repartidor")).with(authentication(identidad("ADMINISTRADOR", 1)))
                .contentType(MediaType.APPLICATION_JSON).content(asignacion(0))).andExpect(status().isBadRequest());
    }
    @Test void exigeAutenticacionYRestringeFuncionesPorRol() throws Exception {
        mvc.perform(get(ruta("gestion"))).andExpect(status().isUnauthorized());
        mvc.perform(put(ruta("repartidor")).with(authentication(identidad("REPARTIDOR", 4)))
                .contentType(MediaType.APPLICATION_JSON).content(asignacion(0))).andExpect(status().isForbidden());
        mvc.perform(put(ruta("anulacion")).with(authentication(identidad("REPARTIDOR", 4)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"versionEsperada\":0}")).andExpect(status().isForbidden());
        mvc.perform(get(ruta("gestion")).with(authentication(identidad("OTRO", 2)))).andExpect(status().isForbidden());
    }
    @Test void entregaContactoSoloAlPropietarioOAlRepartidorAsignado() throws Exception {
        mvc.perform(get(ruta("gestion")).with(authentication(identidad("REPARTIDOR", 8)))).andExpect(status().isForbidden());
        mvc.perform(get(ruta("gestion")).with(authentication(identidad("CLIENTE", 11)))).andExpect(status().isForbidden());
        verifyNoInteractions(usuarios);
        for (var actor : List.of(identidad("CLIENTE", 10), identidad("REPARTIDOR", 4), identidad("DESPACHADORA", 2), identidad("ADMINISTRADOR", 1))) {
            mvc.perform(get(ruta("gestion")).with(authentication(actor))).andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.cliente.nombre").value("Persona Prueba"))
                    .andExpect(jsonPath("$.entrega.direccion").value("Direccion 123"))
                    .andExpect(jsonPath("$.cliente.run").doesNotExist());
        }
    }
    @Test void invitadosSeConsultanDesdeElPedidoSinDependerDeUsuarios() throws Exception {
        boleta.setClienteId(null); boleta.setNombreInvitado("Invitado Prueba");
        boleta.setCorreoInvitado("invitado@example.test"); boleta.setTelefonoInvitado("+56912345678");
        repositorio.saveAndFlush(boleta);
        mvc.perform(get(ruta("gestion")).with(authentication(identidad("REPARTIDOR", 4))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cliente.nombre").value("Invitado Prueba"))
                .andExpect(jsonPath("$.cliente.telefono").value("+56912345678"));
        verifyNoInteractions(usuarios);
    }
    @Test void listadoRespetaAsignacionYNoUsaClientesDeOtrosPedidos() throws Exception {
        mvc.perform(get("/api/v1/boletas/gestion").with(authentication(identidad("REPARTIDOR", 8))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        verifyNoInteractions(usuarios);
    }
    @Test void despachadoraPuedeAnularUnPedidoPendiente() throws Exception {
        mvc.perform(put(ruta("anulacion")).with(authentication(identidad("DESPACHADORA", 2)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"versionEsperada\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.boleta.estado").value("CANCELADO"));
    }
    @Test void cuentaBorradaNoImpideVerElListado() throws Exception {
        when(usuarios.consultar(10L)).thenThrow(new RecursoNoEncontradoException("No existe la cuenta asociada al pedido."));
        mvc.perform(get("/api/v1/boletas/gestion").with(authentication(identidad("DESPACHADORA", 2))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].cliente.nombre").value("Cuenta eliminada"))
                .andExpect(jsonPath("$[0].cliente.correo").doesNotExist());
    }
    @Test void usuariosApagadoNoImpideVerElListado() throws Exception {
        when(usuarios.consultar(10L)).thenThrow(new CatalogoNoDisponibleException("Usuarios no disponible"));
        mvc.perform(get("/api/v1/boletas/gestion").with(authentication(identidad("ADMINISTRADOR", 1))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].cliente.nombre").value("Datos del cliente no disponibles"));
    }
    @Test void anulacionLogicaEsRepetibleYNoBorraElHistorial() throws Exception {
        for (int n = 0; n < 2; n++) mvc.perform(put(ruta("anulacion")).with(authentication(identidad("ADMINISTRADOR", 1)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"versionEsperada\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.boleta.estado").value("CANCELADO"));
        assertEquals(1, repositorio.count()); verifyNoInteractions(stock);
    }
    @ParameterizedTest @ValueSource(strings = {"EN_CAMINO", "ENTREGADO"})
    void anulacionNoOcultaPedidosEnCursoNiEntregados(String estado) throws Exception {
        boleta.setEstado(estado); boleta = repositorio.saveAndFlush(boleta);
        mvc.perform(put(ruta("anulacion")).with(authentication(identidad("ADMINISTRADOR", 1)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"versionEsperada\":" + boleta.getVersion() + "}"))
                .andExpect(status().isConflict());
        assertEquals(estado, repositorio.findById(boleta.getId()).orElseThrow().getEstado());
        assertEquals(1, repositorio.count()); verifyNoInteractions(stock);
    }
    @Test void anulacionObsoletaNoCambiaEstado() throws Exception {
        boleta.setDireccion("Direccion actual 456"); repositorio.saveAndFlush(boleta);
        mvc.perform(put(ruta("anulacion")).with(authentication(identidad("ADMINISTRADOR", 1)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"versionEsperada\":0}"))
                .andExpect(status().isConflict());
        assertEquals("PENDIENTE", repositorio.findById(boleta.getId()).orElseThrow().getEstado());
    }
    @Test void documentacionIncluyeLosNuevosContratosSinCambiarLosExistentes() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/boletas/{id}/repartidor'].put").exists())
                .andExpect(jsonPath("$.paths['/api/v1/boletas/{id}/anulacion'].put").exists())
                .andExpect(jsonPath("$.paths['/api/v1/boletas/gestion'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/boletas'].post").exists());
    }
    @ParameterizedTest @ValueSource(strings = {"EN_CAMINO", "ENTREGADO", "CANCELADO"})
    void asignacionNoReabreEstadosCerrados(String estado) throws Exception {
        boleta.setEstado(estado); boleta = repositorio.saveAndFlush(boleta);
        mvc.perform(put(ruta("repartidor")).with(authentication(identidad("ADMINISTRADOR", 1)))
                .contentType(MediaType.APPLICATION_JSON).content(asignacion(boleta.getVersion()))).andExpect(status().isConflict());
    }
    @Test void pedidoInexistenteEs404YFalloDeUsuariosEs503SinCambiarDatos() throws Exception {
        mvc.perform(get("/api/v1/boletas/999999/gestion").with(authentication(identidad("ADMINISTRADOR", 1)))).andExpect(status().isNotFound());
        when(usuarios.consultar(5L)).thenThrow(new cl.gaselvolcan.ms_pedidos.exception.CatalogoNoDisponibleException("Usuarios no disponible"));
        mvc.perform(put(ruta("repartidor")).with(authentication(identidad("ADMINISTRADOR", 1)))
                .contentType(MediaType.APPLICATION_JSON).content(asignacion(0))).andExpect(status().isServiceUnavailable());
        assertEquals(4L, repositorio.findById(boleta.getId()).orElseThrow().getRepartidorId());
    }
    @Test void bloqueoImpideAsignarSobreUnCambioDeEstadoConcurrente() throws Exception {
        var bloqueada = new CountDownLatch(1); var continuar = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var cambio = executor.submit(() -> new TransactionTemplate(manager).execute(s -> {
                var b = repositorio.bloquear(boleta.getId()).orElseThrow(); bloqueada.countDown();
                try { assertTrue(continuar.await(5, TimeUnit.SECONDS)); } catch (InterruptedException e) { throw new RuntimeException(e); }
                b.setEstado("EN_CAMINO"); return repositorio.saveAndFlush(b);
            }));
            assertTrue(bloqueada.await(5, TimeUnit.SECONDS));
            var asignar = executor.submit(() -> {
                SecurityContextHolder.getContext().setAuthentication(identidad("ADMINISTRADOR", 1));
                try { return gestion.asignar(boleta.getId(), new AsignacionRepartidorSolicitud(5L, 0L)); }
                finally { SecurityContextHolder.clearContext(); }
            });
            continuar.countDown(); cambio.get(5, TimeUnit.SECONDS);
            var error = assertThrows(ExecutionException.class, () -> asignar.get(5, TimeUnit.SECONDS));
            assertInstanceOf(cl.gaselvolcan.ms_pedidos.exception.ConflictoGestionBoletaException.class, error.getCause());
            assertEquals(4L, repositorio.findById(boleta.getId()).orElseThrow().getRepartidorId());
        } finally { continuar.countDown(); executor.shutdownNow(); }
    }
}
