package cl.gaselvolcan.usuarios.config;

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
    "spring.config.import=", "spring.datasource.url=jdbc:h2:mem:swagger-usuarios;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
    "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never",
    "spring.jpa.show-sql=false", "catalogo.carga-inicial.enabled=false", "pedidos.recuperacion.enabled=false"
})
class OpenApiIntegrationTest {
    @Autowired WebApplicationContext context;
    MockMvc mvc;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    DatosIniciales cargaInicial;
    @BeforeEach void preparar() { mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }

    @Test void uiYRecursosSonPublicos() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/swagger-ui/swagger-ui-bundle.js")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isOk());
    }

    @Test
    void todasLasReferenciasResuelven() throws Exception {
        String ruta = "/v3/api-docs";
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
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/auth/registro'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/renovar'].post.security[0].bearerJWT").exists())
                .andExpect(jsonPath("$.paths['/api/v1/usuarios'].get.security[0].bearerJWT").exists())
                .andExpect(jsonPath("$.paths['/api/v1/usuarios/yo'].delete.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/usuarios/{id}'].put.requestBody").exists())
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

    @Autowired cl.gaselvolcan.usuarios.repository.UsuarioRepository usuarios;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder password;

    @Test @org.springframework.transaction.annotation.Transactional
    void loginPublicoYBearerConservanPermisos() throws Exception {
        usuarios.saveAndFlush(new cl.gaselvolcan.usuarios.model.Usuario("Prueba", "Sintetica", "swagger@example.test",
                password.encode("test1234"), cl.gaselvolcan.usuarios.model.Rol.CLIENTE));
        String login = mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content("{\"correo\":\"swagger@example.test\",\"contrasena\":\"test1234\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = JsonMapper.builder().build().readTree(login).path("token").asText();
        mvc.perform(get("/api/v1/usuarios/yo").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/usuarios").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/usuarios/yo")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/usuarios/yo").header("Authorization", "Bearer invalido")).andExpect(status().isUnauthorized());
    }
}
