package cl.gaselvolcan.usuarios.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.gaselvolcan.usuarios.dto.UsuarioRespuesta;
import cl.gaselvolcan.usuarios.service.UsuarioService;
import io.swagger.v3.oas.annotations.Hidden;

// Ruta interna que usa ms-pedidos para mostrar quién compró un pedido.
// No está en Swagger (@Hidden) ni en el Gateway; la protege FiltroPedidosInterno.
@Hidden
@RestController
@RequestMapping("/internal/v1/usuarios")
public class UsuarioPedidoInternoController {

    // Solo los datos que necesita un pedido (sin RUN, dirección ni contraseña)
    public record UsuarioPedido(Long id, String nombre, String correo, String telefono, String rol, boolean activo) {
        @Override
        public String toString() {
            return "UsuarioPedido[id=" + id + ", rol=" + rol + "]";
        }
    }

    private final UsuarioService usuarios;

    public UsuarioPedidoInternoController(UsuarioService usuarios) {
        this.usuarios = usuarios;
    }

    @GetMapping("/{id}/pedido")
    public ResponseEntity<UsuarioPedido> consultar(@PathVariable Long id) {
        UsuarioRespuesta usuario = usuarios.buscarPorId(id);
        String nombre = String.join(" ", usuario.nombre(), usuario.apellidos()).trim();

        // no-store: datos personales, que no queden guardados en ninguna caché
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new UsuarioPedido(usuario.id(), nombre, usuario.correo(), usuario.telefono(),
                        usuario.rol().name(), usuario.activo()));
    }
}
