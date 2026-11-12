package cl.gaselvolcan.ms_catalogo.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = {
    "jwt.secret=clave-sintetica-openapi-usuarios-1234567890",
    "internal.jwt.secret=clave-sintetica-openapi-interna-1234567890",
    "spring.config.import=", "spring.datasource.url=jdbc:h2:mem:swagger-catalogo;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
    "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never",
    "spring.jpa.show-sql=false", "catalogo.carga-inicial.enabled=false", "pedidos.recuperacion.enabled=false"
})
class OpenApiIntegrationTest {
    @Autowired WebApplicationContext context;
    MockMvc mvc;
    @BeforeEach void preparar() { mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }

    @Test void uiYRecursosSonPublicos() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/swagger-ui/swagger-ui-bundle.js")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isOk());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"/v3/api-docs", "/v3/api-docs/stock-interno"})
    void todasLasReferenciasResuelven(String ruta) throws Exception {
        String json = mvc.perform(get(ruta)).andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.ErrorRespuesta.properties.estado.type").value("integer"))
                .andExpect(jsonPath("$.components.schemas.ErrorRespuesta.properties.mensaje.type").value("string"))
                .andReturn().getResponse().getContentAsString();
        var documento = JsonMapper.builder().build().readTree(json);
        assertTrue(verificarReferencias(documento, documento) > 0, "La prueba debe recorrer referencias reales");
    }

    private static int verificarReferencias(tools.jackson.databind.JsonNode documento, tools.jackson.databind.JsonNode nodo) {
        int cantidad = 0;
        var referencia = nodo.path("$ref");
        if (referencia.isString()) {
            String destino = referencia.asText();
            assertTrue(destino.startsWith("#/"), "Referencia externa no verificada: " + destino);
            assertFalse(documento.at(destino.substring(1)).isMissingNode(), "Referencia sin destino: " + destino);
            cantidad++;
        }
        for (var hijo : nodo.values()) cantidad += verificarReferencias(documento, hijo);
        return cantidad;
    }

    @Test void documentaContratosRealesYBearer() throws Exception {
        String body = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerJWT.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerJWT.scheme").value("bearer"))
                .andExpect(jsonPath("$.servers[0].url").value("/"))
                .andExpect(jsonPath("$.paths['/api/v1/productos'].get.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/productos'].post.security[0].bearerJWT").exists())
                .andExpect(jsonPath("$.paths['/api/v1/productos/{id}'].put.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/productos/{id}/estado'].put.security[0].bearerJWT").exists())
                .andExpect(jsonPath("$.paths['/api/v1/productos/{id}/estado'].put.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/productos/{id}/estado'].put.responses['404']").exists())
                .andExpect(jsonPath("$.components.schemas.ProductoRespuesta.properties.version").exists())
                .andExpect(jsonPath("$.components.schemas.ProductoActualizacionSolicitud.required",
                        org.hamcrest.Matchers.hasItem("versionEsperada")))
                .andExpect(jsonPath("$.components.schemas.ProductoActualizacionSolicitud.properties.versionEsperada.minimum").value(0))
                .andExpect(jsonPath("$.components.schemas.EstadoProductoSolicitud.required", org.hamcrest.Matchers.hasItem("activo")))
                .andExpect(jsonPath("$.paths['/api/v1/productos/{id}/disponibilidad'].get.parameters").isNotEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/categorias/{id}'].delete.responses['204']").exists())
                .andReturn().getResponse().getContentAsString();
        var root = JsonMapper.builder().build().readTree(body);
        assertTrue(root.path("paths").propertyNames().stream().allMatch(p -> p.startsWith("/api/v1/")));
        assertFalse(body.contains("nombreInvitadoInformado"));
        // Comparar con los mappings reales, no solo con una lista escrita a mano.
        var mappings = context.getBean("requestMappingHandlerMapping",
                org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping.class);
        mappings.getHandlerMethods().forEach((mapping, handler) -> mapping.getPatternValues().stream()
                .filter(path -> path.startsWith("/api/v1/"))
                .forEach(path -> mapping.getMethodsCondition().getMethods().forEach(method ->
                        assertTrue(root.path("paths").path(path).path(method.name().toLowerCase(java.util.Locale.ROOT)).isObject(),
                                method + " " + path + " falta en OpenAPI"))));
    }

    @Test void stockInternoTieneDocumentacionSeparada() throws Exception {
        mvc.perform(get("/v3/api-docs/stock-interno")).andExpect(status().isOk())
            .andExpect(jsonPath("$.components.securitySchemes.stockServicioJWT.scheme").value("bearer"))
            .andExpect(jsonPath("$.components.securitySchemes.bearerJWT").doesNotExist())
            .andExpect(jsonPath("$.paths['/internal/v1/stock/descuentos'].post.responses['503']").exists())
            .andExpect(jsonPath("$.paths['/internal/v1/stock/descuentos'].post.security[0].stockServicioJWT").exists())
            .andExpect(jsonPath("$.paths['/internal/v1/stock/descuentos'].post.responses['409'].content['application/json'].schema.oneOf").isNotEmpty())
            .andExpect(jsonPath("$.paths['/api/v1/productos']").doesNotExist());
        // Generar el grupo interno no debe alterar la especificacion publica.
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
            .andExpect(jsonPath("$.components.securitySchemes.bearerJWT.scheme").value("bearer"))
            .andExpect(jsonPath("$.paths['/internal/v1/stock/descuentos']").doesNotExist());
    }
}
