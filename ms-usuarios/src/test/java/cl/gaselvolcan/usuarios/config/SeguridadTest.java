package cl.gaselvolcan.usuarios.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import cl.gaselvolcan.usuarios.controller.AuthController;
import cl.gaselvolcan.usuarios.controller.UsuarioController;
import cl.gaselvolcan.usuarios.model.Rol;
import cl.gaselvolcan.usuarios.model.Usuario;
import cl.gaselvolcan.usuarios.service.AuthService;
import cl.gaselvolcan.usuarios.service.JwtService;
import cl.gaselvolcan.usuarios.service.UsuarioService;

// Pruebas de SeguridadConfig con tokens de verdad: sin token → 401,
// rol equivocado → 403, rol correcto → 200. Los servicios son falsos (sin MySQL).
@WebMvcTest(controllers = { UsuarioController.class, AuthController.class },
        properties = "jwt.secret=clave-solo-para-las-pruebas-de-seguridad-2026")
@Import({ SeguridadConfig.class, JwtService.class })
class SeguridadTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwt;

    @MockitoBean
    private UsuarioService usuarios;

    @MockitoBean
    private AuthService auth;

    // Arma el encabezado "Bearer <token>" de una cuenta con ese rol
    private String token(String correo, Rol rol) {
        return "Bearer " + jwt.generar(new Usuario("Prueba", "Prueba", correo, "x", rol));
    }

    private String admin() {
        return token("admin@gaselvolcan.cl", Rol.ADMINISTRADOR);
    }

    private String despachadora() {
        return token("despachadora@gaselvolcan.cl", Rol.DESPACHADORA);
    }

    private String cliente() {
        return token("camila@gmail.com", Rol.CLIENTE);
    }

    // --- 401: sin sesión ---

    @Test
    void sinTokenResponde401EnJson() throws Exception {
        mvc.perform(get("/api/v1/usuarios"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.estado").value(401))
                .andExpect(jsonPath("$.mensaje").value("Debes iniciar sesión para continuar."));
    }

    @Test
    void conTokenFalsoResponde401() throws Exception {
        mvc.perform(get("/api/v1/usuarios").header(HttpHeaders.AUTHORIZATION, "Bearer token-inventado"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("El token no es válido o ya venció. Inicia sesión de nuevo."));
    }

    // --- 403: rol equivocado ---

    @Test
    void unClienteNoPuedeVerLaListaDeUsuarios() throws Exception {
        mvc.perform(get("/api/v1/usuarios").header(HttpHeaders.AUTHORIZATION, cliente()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.estado").value(403))
                .andExpect(jsonPath("$.mensaje").value("No tienes permiso para realizar esta acción."));
    }

    @Test
    void laDespachadoraNoPuedeCrearUsuarios() throws Exception {
        mvc.perform(post("/api/v1/usuarios").header(HttpHeaders.AUTHORIZATION, despachadora())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unClienteNoPuedeVerLosRepartidores() throws Exception {
        mvc.perform(get("/api/v1/usuarios/repartidores").header(HttpHeaders.AUTHORIZATION, cliente()))
                .andExpect(status().isForbidden());
    }

    @Test
    void soloElClientePuedeEliminarSuPropiaCuenta() throws Exception {
        mvc.perform(delete("/api/v1/usuarios/yo").header(HttpHeaders.AUTHORIZATION, despachadora()))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/v1/usuarios/yo").header(HttpHeaders.AUTHORIZATION, cliente()))
                .andExpect(status().isNoContent());
        verify(usuarios).eliminarCuentaPropia("camila@gmail.com");
    }

    // --- 200: rol correcto ---

    @Test
    void elAdministradorVeLaListaDeUsuarios() throws Exception {
        when(usuarios.listar()).thenReturn(List.of());

        mvc.perform(get("/api/v1/usuarios").header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isOk());
    }

    @Test
    void laDespachadoraVeLosRepartidores() throws Exception {
        when(usuarios.listarRepartidores()).thenReturn(List.of());

        mvc.perform(get("/api/v1/usuarios/repartidores").header(HttpHeaders.AUTHORIZATION, despachadora()))
                .andExpect(status().isOk());
    }

    @Test
    void cadaUsuarioVeSuPerfilConElCorreoDeSuToken() throws Exception {
        mvc.perform(get("/api/v1/usuarios/yo").header(HttpHeaders.AUTHORIZATION, despachadora()))
                .andExpect(status().isOk());
        verify(usuarios).buscarPorCorreo("despachadora@gaselvolcan.cl");
    }

    @Test
    void elAdministradorEliminaConSuCorreoParaRevisarLaReglaDeSuCuenta() throws Exception {
        mvc.perform(delete("/api/v1/usuarios/5").header(HttpHeaders.AUTHORIZATION, admin()))
                .andExpect(status().isNoContent());
        verify(usuarios).eliminar(eq(5L), eq("admin@gaselvolcan.cl"));
    }

    // --- Rutas públicas y CORS ---

    @Test
    void elLoginNoPideToken() throws Exception {
        when(auth.login(any())).thenReturn(null);

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"admin@gaselvolcan.cl\",\"contrasena\":\"Admin1234\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void laRutaDeSaludNoPideToken() throws Exception {
        // En esta prueba Actuator no está cargado (404), pero lo importante es que la
        // seguridad NO la bloquea: no responde 401 ni 403
        mvc.perform(get("/actuator/health"))
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertTrue(
                        result.getResponse().getStatus() != 401 && result.getResponse().getStatus() != 403));
    }

    @Test
    void reactEnElPuerto5173PuedeLlamarALaApi() throws Exception {
        mvc.perform(options("/api/v1/usuarios")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
    }

    @Test
    void otraPaginaNoPuedeLlamarALaApi() throws Exception {
        mvc.perform(options("/api/v1/usuarios")
                        .header(HttpHeaders.ORIGIN, "http://pagina-extrana.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }
}
