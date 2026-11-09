package cl.gaselvolcan.ms_pedidos.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import cl.gaselvolcan.ms_pedidos.client.CatalogoClient;
import cl.gaselvolcan.ms_pedidos.dto.catalogo.*;
import cl.gaselvolcan.ms_pedidos.exception.RespuestaCatalogoInvalidaException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.Validator;
import cl.gaselvolcan.ms_pedidos.dto.BoletaRespuesta;
import cl.gaselvolcan.ms_pedidos.dto.BoletaSolicitud;
import cl.gaselvolcan.ms_pedidos.exception.RecursoNoEncontradoException;
import cl.gaselvolcan.ms_pedidos.exception.SolicitudInvalidaException;
import cl.gaselvolcan.ms_pedidos.model.Boleta;
import cl.gaselvolcan.ms_pedidos.model.DetalleBoleta;
import cl.gaselvolcan.ms_pedidos.model.EstadoBoleta;
import cl.gaselvolcan.ms_pedidos.repository.BoletaRepository;
import cl.gaselvolcan.ms_pedidos.config.UsuarioToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

@Service
@Transactional
public class BoletaService {
    private static final BigDecimal LIMITE_TOTAL = new BigDecimal("100000000000000000");
    private final BoletaRepository repositorio;
    private final Validator validator;
    private final org.springframework.transaction.support.TransactionTemplate tx;

    private final CatalogoClient catalogo;
    @org.springframework.beans.factory.annotation.Autowired
    private cl.gaselvolcan.ms_pedidos.repository.OperacionCompraRepository compras;

    public BoletaService(BoletaRepository repositorio, Validator validator, CatalogoClient catalogo,
            org.springframework.transaction.PlatformTransactionManager manager) {
        this.catalogo = catalogo;
        this.repositorio = repositorio;
        this.validator = validator;
        tx = new org.springframework.transaction.support.TransactionTemplate(manager);
        tx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(readOnly = true)
    public List<BoletaRespuesta> listar() {
        Long clienteId = clienteAutenticado();
        if (clienteId != null) return repositorio.findByClienteId(clienteId).stream().map(BoletaRespuesta::desde).toList();
        Long repartidorId = repartidorAutenticado();
        if (repartidorId != null) {
            return repositorio.findByRepartidorId(repartidorId).stream().map(BoletaRespuesta::desde).toList();
        }
        return repositorio.findAll().stream().map(BoletaRespuesta::desde).toList();
    }

    @Transactional(readOnly = true)
    public BoletaRespuesta buscarPorId(Long id) {
        Long repartidorId = repartidorAutenticado();
        Boleta boleta = obtener(id);
        verificarAsignacion(boleta, repartidorId);
        return BoletaRespuesta.desde(boleta);
    }

    // Cotizacion sin persistencia ni transaccion durante las consultas HTTP.
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public BoletaRespuesta cotizar(BoletaSolicitud datos) {
        if (datos != null && datos.estado() != null && !EstadoBoleta.PENDIENTE.name().equals(datos.estado())) {
            throw new SolicitudInvalidaException("La boleta debe crearse en estado PENDIENTE.");
        }
        var precios = validarYConsultarPrecios(datos);
        BigDecimal total = calcularTotal(datos, precios);
        Boleta boleta = new Boleta();
        copiar(datos, total, precios, boleta);
        return BoletaRespuesta.desde(boleta);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public BoletaRespuesta actualizar(Long id, BoletaSolicitud datos) {
        if (repartidorAutenticado() != null) {
            throw new AccessDeniedException("El repartidor no puede reemplazar una boleta.");
        }
        tx.executeWithoutResult(s -> validarEdicion(obtener(id), datos));
        var precios = validarYConsultarPrecios(datos);
        BigDecimal total = calcularTotal(datos, precios);
        return tx.execute(s -> {
            Boleta boleta = obtener(id);
            validarEdicion(boleta, datos);
            copiar(datos, total, precios, boleta);
            return BoletaRespuesta.desde(repositorio.saveAndFlush(boleta));
        });
    }

    private void validarEdicion(Boleta boleta, BoletaSolicitud datos) {
        verificarCliente(boleta);
        if (clienteAutenticado() != null) throw new AccessDeniedException("El cliente no puede reemplazar boletas.");
        if (boleta.getOperacionStockId() != null) {
            throw new SolicitudInvalidaException("La boleta con descuento acreditado no admite edicion general; usa el endpoint de estado.");
        }
        if (!EstadoBoleta.PENDIENTE.name().equals(boleta.getEstado())) {
            throw new SolicitudInvalidaException("Solo se puede modificar una boleta en estado PENDIENTE.");
        }
        if (datos != null && datos.estado() != null && !datos.estado().equals(boleta.getEstado())) {
            throw new SolicitudInvalidaException("Usa el endpoint de estado para cambiar el estado de la boleta.");
        }
    }

    public BoletaRespuesta actualizarEstado(Long id, EstadoBoleta destino) {
        Long repartidorId = repartidorAutenticado();
        Boleta boleta = obtener(id);
        verificarAsignacion(boleta, repartidorId);
        if (destino == EstadoBoleta.CANCELADO && "CANCELADO".equals(boleta.getEstado())
                && boleta.getOperacionStockId() != null) return BoletaRespuesta.desde(boleta);
        EstadoBoleta actual;
        try {
            actual = EstadoBoleta.valueOf(boleta.getEstado());
        } catch (IllegalArgumentException | NullPointerException error) {
            throw new SolicitudInvalidaException("El estado actual de la boleta no es valido.");
        }
        if (destino == null || !actual.permite(destino)) {
            throw new SolicitudInvalidaException("Transicion de estado no permitida: " + actual + " -> " + destino + ".");
        }
        boleta.setEstado(destino.name());
        if (destino == EstadoBoleta.CANCELADO && boleta.getOperacionStockId() != null) {
            var compra = compras.bloquear(boleta.getOperacionStockId()).orElseThrow(
                    () -> new IllegalStateException("Falta el registro de stock de la boleta."));
            if (!"CONFIRMADA".equals(compra.getEstado()) || !boleta.getId().equals(compra.getBoletaId())) {
                throw new SolicitudInvalidaException("La operacion de compra no admite cancelacion en este momento.");
            }
            compra.setEstado("COMPENSACION_PENDIENTE");
            compra.liberar();
        }
        return BoletaRespuesta.desde(repositorio.saveAndFlush(boleta));
    }

    // JWT define id como Usuario.id; repartidorId es esa referencia externa.
    // La autenticacion HTTP sigue a cargo de SeguridadConfig y FiltroJwt.
    private Long repartidorAutenticado() {
        var autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion == null || autenticacion.getAuthorities().stream()
                .noneMatch(a -> "ROLE_REPARTIDOR".equals(a.getAuthority()))) {
            return null;
        }
        if (!(autenticacion.getPrincipal() instanceof UsuarioToken usuario)
                || usuario.id() == null || usuario.id() <= 0) {
            throw new AccessDeniedException("El repartidor necesita un token con un ID valido. Inicia sesion nuevamente.");
        }
        return usuario.id();
    }

    private Long clienteAutenticado() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities().stream().noneMatch(a -> "ROLE_CLIENTE".equals(a.getAuthority()))) return null;
        if (!(auth.getPrincipal() instanceof UsuarioToken usuario) || usuario.id() == null || usuario.id() <= 0)
            throw new AccessDeniedException("Se requiere un token con ID valido.");
        return usuario.id();
    }

    private void verificarCliente(Boleta boleta) {
        Long cliente = clienteAutenticado();
        if (cliente != null && !cliente.equals(boleta.getClienteId()))
            throw new AccessDeniedException("La boleta pertenece a otro cliente.");
    }

    private void verificarAsignacion(Boleta boleta, Long repartidorId) {
        verificarCliente(boleta);
        if (repartidorId != null && !repartidorId.equals(boleta.getRepartidorId())) {
            throw new AccessDeniedException("La boleta no esta asignada al repartidor autenticado.");
        }
    }

    private Boleta obtener(Long id) {
        if (id == null || id <= 0) {
            throw new SolicitudInvalidaException("El id debe ser positivo.");
        }
        return repositorio.findById(id).orElseThrow(
                () -> new RecursoNoEncontradoException("No existe una boleta con id " + id + "."));
    }

    private Map<Long, BigDecimal> validarYConsultarPrecios(BoletaSolicitud datos) {
        if (datos == null) {
            throw new SolicitudInvalidaException("Los datos de la boleta son obligatorios.");
        }
        var errores = validator.validate(datos);
        if (!errores.isEmpty()) {
            String mensaje = errores.stream().map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .sorted().findFirst().orElseThrow();
            throw new SolicitudInvalidaException(mensaje);
        }
        // Acumular cantidades por ID impide eludir stock repartiendo un producto en varias lineas.
        Map<Long, Integer> cantidades = new LinkedHashMap<>();
        for (var detalle : datos.detalles()) {
            try {
                cantidades.merge(detalle.productoId(), detalle.cantidad(), Math::addExact);
            } catch (ArithmeticException error) {
                throw new SolicitudInvalidaException("La cantidad acumulada del producto excede el limite permitido.");
            }
        }
        Map<Long, BigDecimal> precios = new LinkedHashMap<>();
        cantidades.forEach((id, cantidad) -> {
            var producto = catalogo.consultarProducto(id);
            if (producto == null || !id.equals(producto.id()) || producto.codigo() == null
                    || producto.codigo().isBlank() || producto.activo() == null) {
                throw respuestaInvalida();
            }
            if (!producto.activo()) {
                throw new SolicitudInvalidaException("El producto " + id + " esta inactivo.");
            }
            validarPrecio(producto.precioResidencial());
            if (producto.precioOferta() != null) validarPrecio(producto.precioOferta());
            var disponibilidad = catalogo.consultarDisponibilidad(id, cantidad);
            validarDisponibilidad(id, cantidad, producto.codigo(), disponibilidad);
            // Tarifa actual de la tienda: oferta publicada o, si no existe, residencial.
            precios.put(id, producto.precioOferta() != null ? producto.precioOferta() : producto.precioResidencial());
        });
        return precios;
    }

    private void validarDisponibilidad(Long id, int cantidad, String codigo, DisponibilidadCatalogoRespuesta d) {
        if (d == null || !id.equals(d.productoId()) || !codigo.equals(d.codigo())
                || !Integer.valueOf(cantidad).equals(d.cantidadSolicitada()) || d.stock() == null
                || d.stock() < 0 || d.activo() == null || d.disponible() == null
                || d.disponible() != (d.activo() && d.stock() >= cantidad)) {
            throw respuestaInvalida();
        }
        if (!d.activo()) throw new SolicitudInvalidaException("El producto " + id + " esta inactivo.");
        if (!d.disponible()) throw new SolicitudInvalidaException("Stock insuficiente para el producto " + id + ".");
    }

    private void validarPrecio(BigDecimal precio) {
        if (precio == null || precio.signum() < 0 || precio.compareTo(LIMITE_TOTAL) >= 0
                || Math.max(0, precio.stripTrailingZeros().scale()) > 2) {
            throw respuestaInvalida();
        }
    }

    private RespuestaCatalogoInvalidaException respuestaInvalida() {
        return new RespuestaCatalogoInvalidaException("El catalogo devolvio datos incompletos o inconsistentes.");
    }

    private BigDecimal calcularTotal(BoletaSolicitud datos, Map<Long, BigDecimal> precios) {
        BigDecimal total = datos.detalles().stream()
                .map(d -> precios.get(d.productoId()).multiply(BigDecimal.valueOf(d.cantidad())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(LIMITE_TOTAL) >= 0) {
            throw new SolicitudInvalidaException("El total excede el importe maximo permitido.");
        }
        return total.setScale(2, RoundingMode.UNNECESSARY);
    }

    private void copiar(BoletaSolicitud datos, BigDecimal total, Map<Long, BigDecimal> precios, Boleta boleta) {
        boleta.setClienteId(datos.clienteId());
        boleta.setNombreInvitado(datos.nombreInvitado());
        boleta.setCorreoInvitado(datos.correoInvitado());
        boleta.setTelefonoInvitado(datos.telefonoInvitado());
        boleta.setDireccion(datos.direccion().trim());
        boleta.setComuna(datos.comuna().trim());
        boleta.setRepartidorId(datos.repartidorId());
        boleta.setFecha(datos.fecha());
        boleta.setTotal(total);
        for (var detalle : List.copyOf(boleta.getDetalles())) {
            boleta.quitarDetalle(detalle);
        }
        for (var linea : datos.detalles()) {
            boleta.agregarDetalle(new DetalleBoleta(linea.productoId(), linea.cantidad(), precios.get(linea.productoId()).setScale(2, RoundingMode.UNNECESSARY)));
        }
    }
}
