package cl.gaselvolcan.ms_pedidos.service;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import cl.gaselvolcan.ms_pedidos.client.UsuariosPedidosClient;
import cl.gaselvolcan.ms_pedidos.dto.*;
import cl.gaselvolcan.ms_pedidos.model.*;
import cl.gaselvolcan.ms_pedidos.repository.BoletaRepository;
import cl.gaselvolcan.ms_pedidos.exception.*;

@Service
public class GestionBoletaService {
    private final BoletaRepository repositorio;
    private final BoletaService boletas;
    private final UsuariosPedidosClient usuarios;
    private final TransactionTemplate tx;
    public GestionBoletaService(BoletaRepository repositorio, BoletaService boletas,
            UsuariosPedidosClient usuarios, PlatformTransactionManager manager) {
        this.repositorio = repositorio; this.boletas = boletas; this.usuarios = usuarios;
        tx = new TransactionTemplate(manager);
    }
    private void exigirRol(String... roles) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getAuthorities().stream().noneMatch(
                a -> Arrays.stream(roles).anyMatch(r -> a.getAuthority().equals("ROLE_" + r))))
            throw new AccessDeniedException("No tienes permiso para realizar esta accion.");
    }
    private record Lectura(BoletaRespuesta boleta, Long version, GestionBoletaRespuesta.Comprador invitado) {}
    private Lectura leer(Long id) {
        boletas.buscarPorId(id); // Valida propietario o repartidor asignado antes de consultar contacto.
        var b = repositorio.findById(id).orElseThrow();
        var invitado = b.getClienteId() == null ? new GestionBoletaRespuesta.Comprador(
                b.getNombreInvitado(), b.getCorreoInvitado(), b.getTelefonoInvitado()) : null;
        return new Lectura(BoletaRespuesta.desde(b), b.getVersion(), invitado);
    }
    private GestionBoletaRespuesta completar(Lectura lectura, Map<Long, UsuariosPedidosClient.UsuarioPedido> cache) {
        var b = lectura.boleta();
        var cliente = lectura.invitado();
        if (b.clienteId() != null) {
            var u = cache.computeIfAbsent(b.clienteId(), usuarios::consultar);
            cliente = new GestionBoletaRespuesta.Comprador(u.nombre(), u.correo(), u.telefono());
        }
        return new GestionBoletaRespuesta(b, lectura.version(), cliente,
                new GestionBoletaRespuesta.Entrega(b.direccion(), b.comuna()));
    }
    public List<GestionBoletaRespuesta> listar() {
        exigirRol("ADMINISTRADOR", "DESPACHADORA", "REPARTIDOR", "CLIENTE");
        var lecturas = tx.execute(s -> boletas.listar().stream().map(b -> leer(b.id())).toList());
        var cache = new HashMap<Long, UsuariosPedidosClient.UsuarioPedido>();
        return lecturas.stream().map(l -> completar(l, cache)).toList();
    }
    public GestionBoletaRespuesta consultar(Long id) {
        exigirRol("ADMINISTRADOR", "DESPACHADORA", "REPARTIDOR", "CLIENTE");
        var lectura = tx.execute(s -> leer(id));
        return completar(lectura, new HashMap<>());
    }
    private Boleta bloquear(Long id) {
        if (id == null || id <= 0) throw new SolicitudInvalidaException("El id debe ser positivo.");
        return repositorio.bloquear(id).orElseThrow(() -> new RecursoNoEncontradoException("No existe la boleta solicitada."));
    }
    private void version(Boleta b, Long esperada) {
        if (esperada == null || esperada < 0) throw new SolicitudInvalidaException("La version esperada es obligatoria y no negativa.");
        if (!Objects.equals(b.getVersion(), esperada)) throw new ConflictoGestionBoletaException("El pedido cambio. Consulta su version actual antes de modificarlo.");
    }
    private VersionBoletaRespuesta respuesta(Boleta b) { return new VersionBoletaRespuesta(BoletaRespuesta.desde(b), b.getVersion()); }
    public VersionBoletaRespuesta asignar(Long id, AsignacionRepartidorSolicitud datos) {
        exigirRol("ADMINISTRADOR", "DESPACHADORA");
        // Validación externa antes del bloqueo; no se realiza HTTP dentro de la transacción.
        if (datos.repartidorId() != null) {
            var u = usuarios.consultar(datos.repartidorId());
            if (!"REPARTIDOR".equals(u.rol()) || !Boolean.TRUE.equals(u.activo()))
                throw new SolicitudInvalidaException("Se requiere un repartidor activo.");
        }
        return tx.execute(s -> {
            var b = bloquear(id); version(b, datos.versionEsperada());
            if (!EstadoBoleta.PENDIENTE.name().equals(b.getEstado()))
                throw new ConflictoGestionBoletaException("Solo se puede cambiar el repartidor de un pedido pendiente.");
            if (!Objects.equals(b.getRepartidorId(), datos.repartidorId())) {
                b.setRepartidorId(datos.repartidorId()); repositorio.saveAndFlush(b);
            }
            return respuesta(b);
        });
    }
    public VersionBoletaRespuesta anular(Long id, AnulacionBoletaSolicitud datos) {
        exigirRol("ADMINISTRADOR");
        return tx.execute(s -> {
            var b = bloquear(id);
            // Repetir una anulación confirmada no programa otra compensación.
            if (EstadoBoleta.CANCELADO.name().equals(b.getEstado())) return respuesta(b);
            version(b, datos.versionEsperada());
            if (!EstadoBoleta.PENDIENTE.name().equals(b.getEstado()))
                throw new ConflictoGestionBoletaException("Solo se puede anular un pedido pendiente.");
            boletas.actualizarEstado(id, EstadoBoleta.CANCELADO);
            return respuesta(b);
        });
    }
}
