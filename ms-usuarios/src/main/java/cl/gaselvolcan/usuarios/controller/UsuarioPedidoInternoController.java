package cl.gaselvolcan.usuarios.controller;

import org.springframework.web.bind.annotation.*;
import cl.gaselvolcan.usuarios.service.UsuarioService;
import io.swagger.v3.oas.annotations.Hidden;

@Hidden
@RestController
@RequestMapping("/internal/v1/usuarios")
public class UsuarioPedidoInternoController {
    public record UsuarioPedido(Long id, String nombre, String correo, String telefono, String rol, boolean activo) {
        @Override public String toString() { return "UsuarioPedido[id=" + id + ", rol=" + rol + "]"; }
    }
    private final UsuarioService usuarios;
    public UsuarioPedidoInternoController(UsuarioService usuarios) { this.usuarios = usuarios; }
    @GetMapping("/{id}/pedido")
    public org.springframework.http.ResponseEntity<UsuarioPedido> consultar(@PathVariable Long id) {
        var u = usuarios.buscarPorId(id);
        return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(new UsuarioPedido(u.id(), String.join(" ", u.nombre(), u.apellidos()).trim(),
                u.correo(), u.telefono(), u.rol().name(), u.activo()));
    }
}
