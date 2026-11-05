package cl.gaselvolcan.ms_catalogo.config;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import cl.gaselvolcan.ms_catalogo.controlador.CategoriaController;
import cl.gaselvolcan.ms_catalogo.controlador.ProductoController;
import cl.gaselvolcan.ms_catalogo.exception.ManejadorErrores;
import cl.gaselvolcan.ms_catalogo.exception.RecursoNoEncontradoException;
import cl.gaselvolcan.ms_catalogo.servicio.CategoriaService;
import cl.gaselvolcan.ms_catalogo.servicio.ProductoService;

// Contexto MVC real con la configuracion central; sin MySQL ni Spring Boot completo.
class CorsConfigTest {
    private AnnotationConfigWebApplicationContext contexto;
    private MockMvc mvc;

    @Configuration
    @EnableWebMvc
    @Import({CorsConfig.class, CategoriaController.class, ProductoController.class, ManejadorErrores.class})
    static class ConfiguracionPrueba {
        @Bean CategoriaService categorias() { return mock(CategoriaService.class); }
        @Bean ProductoService productos() { return mock(ProductoService.class); }
    }

    private void iniciar(String origenes) {
        contexto = new AnnotationConfigWebApplicationContext();
        contexto.setServletContext(new MockServletContext());
        TestPropertySourceUtils.addPropertiesFilesToEnvironment(contexto, "classpath:application.properties");
        // Fijar valores para que las pruebas no dependan de variables del equipo.
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(contexto,
                "catalogo.cors.allowed-origins=" + origenes);
        contexto.register(ConfiguracionPrueba.class);
        contexto.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(contexto).build();
    }

    private void iniciarDesarrollo() {
        iniciar("http://localhost:5173,http://127.0.0.1:5173");
    }

    @AfterEach void cerrar() { if (contexto != null) contexto.close(); }

    @Test void permiteAmbosOrigenesEnAmbasApis() throws Exception {
        iniciarDesarrollo();
        for (String origen : new String[] {"http://localhost:5173", "http://127.0.0.1:5173"}) {
            for (String ruta : new String[] {"/api/v1/categorias", "/api/v1/productos"}) {
                mvc.perform(get(ruta).header("Origin", origen))
                        .andExpect(status().isOk())
                        .andExpect(header().string("Access-Control-Allow-Origin", origen))
                        .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
            }
        }
    }

    @Test void preflightPermiteCrudYEncabezadosJson() throws Exception {
        iniciarDesarrollo();
        for (String origen : new String[] {"http://localhost:5173", "http://127.0.0.1:5173"}) {
            for (String ruta : new String[] {"/api/v1/categorias/1", "/api/v1/productos/1"}) {
                for (String metodo : new String[] {"GET", "POST", "PUT", "DELETE", "OPTIONS"}) {
                    mvc.perform(options(ruta).header("Origin", origen)
                                    .header("Access-Control-Request-Method", metodo)
                                    .header("Access-Control-Request-Headers", "content-type,authorization"))
                            .andExpect(status().isOk())
                            .andExpect(header().string("Access-Control-Allow-Origin", origen))
                            .andExpect(header().string("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS"))
                            .andExpect(header().exists("Access-Control-Allow-Headers"));
                }
            }
        }
        verifyNoInteractions(contexto.getBean(ProductoService.class), contexto.getBean(CategoriaService.class));
    }

    @Test void rechazaOrigenesNoAutorizadosAntesDelServicio() throws Exception {
        iniciarDesarrollo();
        for (String origen : new String[] {"https://otro.example", "http://localhost:5174", "http://localhost:5173.ejemplo.com"}) {
            mvc.perform(get("/api/v1/productos").header("Origin", origen))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
            mvc.perform(options("/api/v1/categorias").header("Origin", origen)
                            .header("Access-Control-Request-Method", "POST"))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        }
        verifyNoInteractions(contexto.getBean(ProductoService.class), contexto.getBean(CategoriaService.class));
    }

    @Test void rechazaMetodoYEncabezadoNoPermitidos() throws Exception {
        iniciarDesarrollo();
        mvc.perform(options("/api/v1/productos/1").header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "PATCH"))
                .andExpect(status().isForbidden());
        mvc.perform(options("/api/v1/productos").header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "X-No-Permitido"))
                .andExpect(status().isForbidden());
    }

    @Test void permiteSustituirOrigenesDesdePropiedades() throws Exception {
        iniciar(" https://tienda.example , https://tienda.example ");
        mvc.perform(get("/api/v1/productos").header("Origin", "https://tienda.example"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://tienda.example"));
        mvc.perform(get("/api/v1/productos").header("Origin", "http://localhost:5173"))
                .andExpect(status().isForbidden());
    }

    @Test void conservaCorsEnErroresYEndpointStock() throws Exception {
        iniciarDesarrollo();
        when(contexto.getBean(ProductoService.class).consultarDisponibilidad(99L, 1))
                .thenThrow(new RecursoNoEncontradoException("No existe"));
        mvc.perform(get("/api/v1/productos/99/disponibilidad").header("Origin", "http://localhost:5173"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.estado").value(404))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test void solicitudesSinOriginSiguenFuncionando() throws Exception {
        iniciarDesarrollo();
        mvc.perform(get("/api/v1/categorias")).andExpect(status().isOk())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test void impideComodinesYListaVacia() {
        assertThrows(IllegalArgumentException.class, () -> new CorsConfig("*"));
        assertThrows(IllegalArgumentException.class, () -> new CorsConfig("https://*.example"));
        assertThrows(IllegalArgumentException.class, () -> new CorsConfig(" , "));
    }
}
