package servicio;

import dao.*;
import modelo.*;

import java.time.LocalDate;
import java.util.*;

public class ServicioPedidos implements IServicioPedidos {

    private final IRepositorioProducto repoProductos;
    private final IRepositorioPedido repoPedidos;
    private final IRepositorioProveedor repoProveedores;

    public ServicioPedidos(IRepositorioProducto repoProductos,
                              IRepositorioPedido repoPedidos,
                              IRepositorioProveedor repoProveedores) {
        this.repoProductos = Objects.requireNonNull(repoProductos);
        this.repoPedidos = Objects.requireNonNull(repoPedidos);
        this.repoProveedores = Objects.requireNonNull(repoProveedores);
    }

    @Override
    public PedidoReposicion crearPedido(String proveedorId) {
        var prov = repoProveedores.buscar(proveedorId).orElseThrow(() ->
                new IllegalArgumentException("Proveedor inexistente: " + proveedorId));
        var pedido = new PedidoReposicion("P-" + UUID.randomUUID(), prov);
        repoPedidos.guardar(pedido);
        return pedido;
    }

    @Override
    public void agregarItemPedido(String pedidoId, String productoId, int cantidad) {
        Objects.requireNonNull(pedidoId);
        Objects.requireNonNull(productoId);
        if (cantidad <= 0) throw new IllegalArgumentException("cantidad debe ser > 0");

        // Validaciones de existencia
        repoPedidos.buscar(pedidoId).orElseThrow(
                () -> new IllegalArgumentException("Pedido inexistente: " + pedidoId));
        repoProductos.buscar(productoId).orElseThrow(
                () -> new IllegalArgumentException("Producto inexistente: " + productoId));

        // Y listo: solo persistencia cruda
        repoPedidos.agregarItem(pedidoId, productoId, cantidad);
    }

    @Override
    public void enviarPedido(String id) {
        Objects.requireNonNull(id, "id es obligatorio");
        marcarPedidoEnviado(id);
    }


    @Override public void marcarPedidoEnviado(String pedidoId) {
        var p = cargarCompleto(pedidoId);
        p.marcarEnviado(LocalDate.now());
        repoPedidos.guardarCabecera(p);
    }

    @Override public void marcarPedidoRecibido(String pedidoId) {
        var p = cargarCompleto(pedidoId);
        p.marcarRecibido(LocalDate.now());
        repoPedidos.guardarCabecera(p);
    }

    @Override
    public void recibirPedido(String id, LocalDate fecha) {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(fecha, "fecha es obligatoria");

        // Pedido completo (cabecera + items)
        var pedido = cargarCompleto(id);

        // Sumar stock según los items del pedido
        for (var entry : pedido.getItems().entrySet()) {
            var producto = entry.getKey();
            int cantidad = entry.getValue();

            producto.incrementarStock(cantidad);     // o el método que uses
            repoProductos.guardar(producto);     // persistís el nuevo stock
        }

        // Marcar recibido y persistir SOLO la cabecera
        pedido.marcarRecibido(LocalDate.now());
        repoPedidos.guardarCabecera(pedido);
    }

    public int recibirTodosEnviados(java.time.LocalDate fecha) {
        int recibidos = 0;
        for (var p : listarPedidos()) {
            if (p.getEstado() == EstadoPedido.ENVIADO && !p.getItems().isEmpty()) {
                recibirPedido(p.id(), fecha);
                recibidos++;
            }
        }
        return recibidos;
    }

    private PedidoReposicion cargarCompleto(String id) {
        var pedEnc = repoPedidos.buscar(id).orElseThrow();

        // proveedor real
        var prov = repoProveedores.buscar(pedEnc.getProveedor().id()).orElse(pedEnc.getProveedor());

        // reconstruyo vacío de items, preservando fechas y estado actuales
        var ped = PedidoReposicion.reconstruir(
                pedEnc.id(),
                prov,
                pedEnc.getFechaCreacion(),
                pedEnc.getFechaEnvio(),
                pedEnc.getFechaRecepcion(),
                pedEnc.getEstado()
        );

        // hidrato items: productoId -> cantidad
        var itemsCrudos = repoPedidos.obtenerItems(id); //
        for (var e : itemsCrudos.entrySet()) {
            var prodId = e.getKey();
            var cant   = e.getValue();
            repoProductos.buscar(prodId).ifPresent(prodReal -> ped.agregarProducto(prodReal, cant));
        }

        return ped;
    }

    @Override
    public List<PedidoReposicion> listarPedidos() {
        var encabezados = repoPedidos.listar(); // solo headers (sin items)
        List<PedidoReposicion> completos = new ArrayList<>();

        for (var ped : encabezados) {
            // 1) hidratar proveedor real
            var proveedorReal = repoProveedores.buscar(ped.getProveedor().id())
                    .orElse(ped.getProveedor());

            // 2) reconstruir pedido (sin items todavía), preservando fechas y estado
            var p = PedidoReposicion.reconstruir(
                    ped.id(),
                    proveedorReal,
                    ped.getFechaCreacion(),
                    ped.getFechaEnvio(),
                    ped.getFechaRecepcion(),
                    ped.getEstado()
            );

            // 3) traer items crudos del repo y convertirlos a Productos reales
            var itemsCrudos = repoPedidos.obtenerItems(p.id()); // productoId -> cantidad
            for (var e : itemsCrudos.entrySet()) {
                var prodId = e.getKey();
                var cant   = e.getValue();
                repoProductos.buscar(prodId).ifPresent(prodReal -> p.agregarProducto(prodReal, cant));
            }

            completos.add(p);
        }
        return completos;
    }
}
