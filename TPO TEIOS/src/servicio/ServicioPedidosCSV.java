package servicio;

import dao.*;
import modelo.*;

import java.util.*;

public class ServicioPedidosCSV implements IServicioPedidos {

    private final IRepositorioProducto repoProductos;
    private final IRepositorioPedido repoPedidos;
    private final IRepositorioProveedor repoProveedores;

    public ServicioPedidosCSV(IRepositorioProducto repoProductos,
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
        var pedido = repoPedidos.buscar(pedidoId).orElseThrow(() ->
                new IllegalArgumentException("Pedido inexistente: " + pedidoId));
        var prod = repoProductos.buscar(productoId).orElseThrow(() ->
                new IllegalArgumentException("Producto inexistente: " + productoId));

        // 1) Estado en memoria (útil si luego seguís usando el objeto pedido)
        pedido.agregarProducto(prod, cantidad);

        // 2) Asegurar cabecera (si recién se creó el pedido)
        repoPedidos.guardar(pedido); // upsert SOLO de cabecera/estado (no items)

        // 3) 🔑 Persistir ítem en CSV (esta era la pieza que faltaba)
        repoPedidos.agregarItem(pedidoId, productoId, cantidad);
    }

    @Override
    public void enviarPedido(String id) {
        Objects.requireNonNull(id, "id es obligatorio");
        marcarPedidoEnviado(id);
    }


    @Override public void marcarPedidoEnviado(String pedidoId) {
        var p = cargarCompleto(pedidoId);
        p.marcarEnviado();
        repoPedidos.guardar(p);
    }

    @Override public void marcarPedidoRecibido(String pedidoId) {
        var p = cargarCompleto(pedidoId);
        p.marcarRecibido();
        repoPedidos.guardar(p);
    }

    @Override
    public void recibirPedido(String id, java.time.LocalDate fecha) {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(fecha, "fecha es obligatoria");

        // ✅ Cargar pedido COMPLETO (proveedor real + ítems hidratados)
        var pedido = cargarCompleto(id);

        if (pedido.getEstado() != EstadoPedido.ENVIADO) {
            throw new IllegalStateException("Solo se puede recibir un pedido en estado ENVIADO");
        }

        // ✅ Sumar stock por cada ítem y persistir producto
        pedido.getItems().forEach((producto, cantidad) -> {
            producto.incrementarStock(cantidad);
            repoProductos.guardar(producto); // RepositorioProductoCSV ya reescribe el CSV completo
        });

        // ✅ Cambiar estado y persistir cabecera con UPSERT
        pedido.marcarRecibido();
        repoPedidos.guardar(pedido);
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

        // reconstruyo vacío de items
        var ped = PedidoReposicion.reconstruir(pedEnc.id(), prov, pedEnc.getFechaCreacion(), pedEnc.getEstado());

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

            // 2) reconstruir pedido (sin items todavía)
            var p = PedidoReposicion.reconstruir(
                    ped.id(), proveedorReal, ped.getFechaCreacion(), ped.getEstado()
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
