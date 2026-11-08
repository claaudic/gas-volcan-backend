package cl.gaselvolcan.ms_pedidos.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;

import cl.gaselvolcan.ms_pedidos.client.CatalogoClient;
import cl.gaselvolcan.ms_pedidos.model.Boleta;
import cl.gaselvolcan.ms_pedidos.model.DetalleBoleta;
import cl.gaselvolcan.ms_pedidos.repository.BoletaRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

// Filtros JWT, controlador, servicio y persistencia reales. Catalogo no debe intervenir.
@SpringBootTest(properties = {
        "jwt.secret=" + PermisosRepartidorIntegrationTest.CLAVE,
        "spring.datasource.url=jdbc:h2:mem:permisos-repartidor;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never",
        "spring.jpa.show-sql=false"
})
@Transactional
class PermisosRepartidorIntegrationTest {
    static final String CLAVE = "clave-solo-para-las-pruebas-de-gas-el-volcan";
    private static final String RUTA = "/api/v1/boletas";
    private static final String DATOS = """
            {"clienteId":10,"direccion":"D","comuna":"C","estado":"PENDIENTE",
             "repartidorId":22,"fecha":"2026-01-01T12:00:00",
             "detalles":[{"productoId":100,"cantidad":1}]}
            """;
    @Autowired WebApplicationContext contexto;
    @Autowired BoletaRepository repositorio;
    @Autowired EntityManager em;
    @MockitoBean CatalogoClient catalogo;
    private MockMvc mvc;
    private Long propia, ajena, sinAsignar;

    @BeforeEach void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
        propia = guardar(11L);
        ajena = guardar(22L);
        sinAsignar = guardar(null);
        em.clear();
    }

    private Long guardar(Long repartidorId) {
        var boleta = new Boleta(10L, "D", "C", "PENDIENTE", repartidorId,
                LocalDateTime.of(2026, 1, 1, 12, 0), new BigDecimal("25.00"));
        boleta.agregarDetalle(new DetalleBoleta(100L, 1, new BigDecimal("25.00")));
        return repositorio.saveAndFlush(boleta).getId();
    }

    private String token(Long id, String rol, boolean vencido) {
        var builder = Jwts.builder().subject("usuario@example.test").claim("rol", rol)
                .expiration(new Date(System.currentTimeMillis() + (vencido ? -60_000 : 600_000)));
        if (id != null) builder.claim("id", id);
        return "Bearer " + builder.signWith(Keys.hmacShaKeyFor(CLAVE.getBytes(StandardCharsets.UTF_8)),
                Jwts.SIG.HS256).compact();
    }

    private String repartidor(long id) { return token(id, "REPARTIDOR", false); }

    @Test void cadaRepartidorVeSoloSusBoletasYUnTerceroRecibeListaVacia() throws Exception {
        for (long id : new long[] {11, 22}) {
            mvc.perform(get(RUTA).header("Authorization", repartidor(id)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].repartidorId").value(id));
        }
        mvc.perform(get(RUTA).header("Authorization", repartidor(33)))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test void consultaPropiaPeroNoAjenaNiSinAsignar() throws Exception {
        mvc.perform(get(RUTA + "/" + propia).header("Authorization", repartidor(11)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(propia));
        for (Long id : new Long[] {ajena, sinAsignar}) {
            mvc.perform(get(RUTA + "/" + id).header("Authorization", repartidor(11)))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.estado").value(403));
        }
    }

    @Test void estadoPropioRespetaTransicionesYConservaDetallesEImportes() throws Exception {
        Long detalleId = repositorio.findById(propia).orElseThrow().getDetalles().getFirst().getId();
        for (String estado : new String[] {"EN_CAMINO", "ENTREGADO"}) {
            mvc.perform(put(RUTA + "/" + propia + "/estado").header("Authorization", repartidor(11))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"" + estado + "\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value(estado));
        }
        mvc.perform(put(RUTA + "/" + propia + "/estado").header("Authorization", repartidor(11))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"PENDIENTE\"}"))
                .andExpect(status().isBadRequest());
        em.clear();
        var boleta = repositorio.findById(propia).orElseThrow();
        assertEquals("ENTREGADO", boleta.getEstado());
        assertEquals(new BigDecimal("25.00"), boleta.getTotal());
        assertEquals(detalleId, boleta.getDetalles().getFirst().getId());
        assertEquals(new BigDecimal("25.00"), boleta.getDetalles().getFirst().getPrecio());
        verifyNoInteractions(catalogo);
    }

    @Test void noCambiaEstadoAjenoNiSinAsignar() throws Exception {
        for (Long id : new Long[] {ajena, sinAsignar}) {
            mvc.perform(put(RUTA + "/" + id + "/estado").header("Authorization", repartidor(11))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"EN_CAMINO\"}"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.estado").value(403));
        }
        em.clear();
        assertEquals("PENDIENTE", repositorio.findById(ajena).orElseThrow().getEstado());
        assertEquals("PENDIENTE", repositorio.findById(sinAsignar).orElseThrow().getEstado());
        verifyNoInteractions(catalogo);
    }

    @Test void noReemplazaNiEliminaBoletasAunqueSeanPropias() throws Exception {
        for (Long id : new Long[] {propia, ajena, sinAsignar}) {
            mvc.perform(put(RUTA + "/" + id).header("Authorization", repartidor(11))
                            .contentType(MediaType.APPLICATION_JSON).content(DATOS))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.estado").value(403));
            mvc.perform(delete(RUTA + "/" + id).header("Authorization", repartidor(11)))
                    .andExpect(status().isForbidden());
        }
        em.clear();
        assertEquals(3, repositorio.count());
        assertEquals(11L, repositorio.findById(propia).orElseThrow().getRepartidorId());
        verifyNoInteractions(catalogo);
    }

    @Test void repartidorSinIdValidoNoPuedeAcceder() throws Exception {
        for (Long id : new Long[] {null, 0L, -1L}) {
            String token = token(id, "REPARTIDOR", false);
            mvc.perform(get(RUTA).header("Authorization", token)).andExpect(status().isForbidden());
            mvc.perform(get(RUTA + "/" + propia).header("Authorization", token)).andExpect(status().isForbidden());
            mvc.perform(put(RUTA + "/" + propia + "/estado").header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"EN_CAMINO\"}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test void sinSesionOTokenInvalidoResponde401EnTodasLasOperacionesProtegidas() throws Exception {
        for (String token : new String[] {"", "Bearer invalido", token(11L, "REPARTIDOR", true)}) {
            for (var peticion : java.util.List.of(get(RUTA), get(RUTA + "/" + propia),
                    put(RUTA + "/" + propia).contentType(MediaType.APPLICATION_JSON).content(DATOS),
                    put(RUTA + "/" + propia + "/estado").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"estado\":\"EN_CAMINO\"}"), delete(RUTA + "/" + propia))) {
                if (!token.isEmpty()) peticion.header("Authorization", token);
                mvc.perform(peticion).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.estado").value(401));
            }
        }
    }

    @Test void putGeneralRechazaEstadosNoPendientesConSeguridadYPersistenciaReales() throws Exception {
        for (String estado : new String[] {"EN_CAMINO", "ENTREGADO", "CANCELADO"}) {
            var original = repositorio.findById(propia).orElseThrow();
            original.setEstado(estado);
            repositorio.saveAndFlush(original);
            Long detalleId = original.getDetalles().getFirst().getId();
            em.clear();
            for (String rol : new String[] {"ADMINISTRADOR", "DESPACHADORA", "REPARTIDOR", "CLIENTE"}) {
                for (String cuerpo : new String[] {DATOS, DATOS.replace("PENDIENTE", estado),
                        DATOS.replace("\"estado\":\"PENDIENTE\",", "")}) {
                    mvc.perform(put(RUTA + "/" + propia).header("Authorization", token(11L, rol, false))
                                    .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                            .andExpect(status().is(rol.equals("ADMINISTRADOR") || rol.equals("DESPACHADORA") ? 400 : 403));
                }
            }
            em.clear();
            var boleta = repositorio.findById(propia).orElseThrow();
            assertEquals(estado, boleta.getEstado());
            assertEquals(11L, boleta.getRepartidorId());
            assertEquals(new BigDecimal("25.00"), boleta.getTotal());
            assertEquals(detalleId, boleta.getDetalles().getFirst().getId());
        }
        verifyNoInteractions(catalogo);
    }

    @Test void putPendienteAutorizadoConservaCalculoDeOfertasYReemplazoDeDetalles() throws Exception {
        org.mockito.Mockito.when(catalogo.consultarProducto(100L)).thenReturn(
                new cl.gaselvolcan.ms_pedidos.dto.catalogo.ProductoCatalogoRespuesta(
                        100L, "P100", true, new BigDecimal("30.00"), new BigDecimal("20.00")));
        org.mockito.Mockito.when(catalogo.consultarDisponibilidad(100L, 1)).thenReturn(
                new cl.gaselvolcan.ms_pedidos.dto.catalogo.DisponibilidadCatalogoRespuesta(
                        100L, "P100", 1, 10, true, true));
        for (String rol : new String[] {"ADMINISTRADOR", "DESPACHADORA"}) {
            Long id = rol.equals("ADMINISTRADOR") ? propia : ajena;
            mvc.perform(put(RUTA + "/" + id).header("Authorization", token(99L, rol, false))
                            .contentType(MediaType.APPLICATION_JSON).content(DATOS))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("PENDIENTE"))
                    .andExpect(jsonPath("$.total").value(20.00))
                    .andExpect(jsonPath("$.detalles[0].precio").value(20.00));
            em.clear();
            assertEquals(new BigDecimal("20.00"), repositorio.findById(id).orElseThrow().getTotal());
        }
    }

    @Test void deleteRespetaSeguridadYConservaBoletasYDetallesEnTodosLosEstados() throws Exception {
        for (String estado : new String[] {"PENDIENTE", "EN_CAMINO", "ENTREGADO", "CANCELADO"}) {
            var original = repositorio.findById(propia).orElseThrow();
            original.setEstado(estado);
            repositorio.saveAndFlush(original);
            Long detalleId = original.getDetalles().getFirst().getId();
            em.clear();
            for (String rol : new String[] {"ADMINISTRADOR", "DESPACHADORA", "REPARTIDOR", "CLIENTE", ""}) {
                var peticion = delete(RUTA + "/" + propia);
                if (!rol.isEmpty()) peticion.header("Authorization", token(11L, rol, false));
                int esperado = rol.isEmpty() ? 401 : rol.equals("ADMINISTRADOR") ? 405 : 403;
                var resultado = mvc.perform(peticion).andExpect(status().is(esperado))
                        .andExpect(jsonPath("$.estado").value(esperado)).andReturn();
                if (esperado == 405) {
                    var permitidos = java.util.Arrays.stream(resultado.getResponse().getHeader("Allow").split(","))
                            .map(String::trim).collect(java.util.stream.Collectors.toSet());
                    assertTrue(permitidos.contains("GET"));
                    assertTrue(permitidos.contains("PUT"));
                    assertFalse(permitidos.contains("DELETE"));
                }
            }
            em.clear();
            var conservada = repositorio.findById(propia).orElseThrow();
            assertEquals(estado, conservada.getEstado());
            assertEquals(new BigDecimal("25.00"), conservada.getTotal());
            assertEquals(detalleId, conservada.getDetalles().getFirst().getId());
            assertEquals(3, repositorio.count());
        }
        mvc.perform(delete(RUTA + "/999999").header("Authorization", token(99L, "ADMINISTRADOR", false)))
                .andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(catalogo);
    }

    @Test void cancelarConservaLaBoletaSinRestituirStock() throws Exception {
        mvc.perform(put(RUTA + "/" + propia + "/estado").header("Authorization", token(99L, "ADMINISTRADOR", false))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"CANCELADO\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CANCELADO"));
        em.clear();
        assertEquals("CANCELADO", repositorio.findById(propia).orElseThrow().getEstado());
        assertEquals(1, repositorio.findById(propia).orElseThrow().getDetalles().size());
        verifyNoInteractions(catalogo);
    }

    @Test void administradorYDespachadoraConservanConsultaGlobalYCambioDeEstado() throws Exception {
        for (String rol : new String[] {"ADMINISTRADOR", "DESPACHADORA"}) {
            String token = token(99L, rol, false);
            mvc.perform(get(RUTA).header("Authorization", token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
            mvc.perform(get(RUTA + "/" + sinAsignar).header("Authorization", token)).andExpect(status().isOk());
            Long id = rol.equals("ADMINISTRADOR") ? propia : ajena;
            mvc.perform(put(RUTA + "/" + id + "/estado").header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"EN_CAMINO\"}"))
                    .andExpect(status().isOk());
        }
        verifyNoInteractions(catalogo);
    }
}
