package cl.gaselvolcan.usuarios.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.gaselvolcan.usuarios.dto.LoginRespuesta;
import cl.gaselvolcan.usuarios.dto.LoginSolicitud;
import cl.gaselvolcan.usuarios.dto.RegistroSolicitud;
import cl.gaselvolcan.usuarios.dto.UsuarioRespuesta;
import cl.gaselvolcan.usuarios.service.AuthService;
import jakarta.validation.Valid;

// Rutas públicas de autenticación
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService servicio;

    public AuthController(AuthService servicio) {
        this.servicio = servicio;
    }

    // POST /api/v1/auth/login → 200 con el token, o 401
    @Operation(summary = "Iniciar sesion")
    @PostMapping("/login")
    public LoginRespuesta login(@Valid @RequestBody LoginSolicitud datos) {
        return servicio.login(datos);
    }

    // POST /api/v1/auth/renovar  (encabezado Authorization: Bearer <token>) → 200 con un token nuevo, o 401
    @Operation(summary = "Renovar token de usuario")
    @PostMapping("/renovar")
    public LoginRespuesta renovar(@Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String encabezado) {
        return servicio.renovar(encabezado);
    }

    // POST /api/v1/auth/registro → 201 con la cuenta nueva (siempre CLIENTE)
    @Operation(summary = "Registrar cuenta CLIENTE")
    @PostMapping("/registro")
    public ResponseEntity<UsuarioRespuesta> registrar(@Valid @RequestBody RegistroSolicitud datos) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.registrar(datos));
    }
}
