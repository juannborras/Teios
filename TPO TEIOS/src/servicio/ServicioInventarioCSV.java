package servicio;

import dao.*;
import excepciones.EntidadNoEncontradaException;
import excepciones.StockInsuficienteException;
import modelo.*;

import java.util.*;
import java.util.stream.Collectors;

public class ServicioInventarioCSV implements IServicioInventario {

    private final IRepositorioProducto repoProductos;
    private final IRepositorioVenta repoVentas;

    private final ProveedorProductoConfig proveedorConfig;
    private final IServicioPedidos svcPedidos;

    public ServicioInventarioCSV(IRepositorioProducto repoProductos,
                                 IRepositorioVenta repoVentas,
                                 IServicioPedidos svcPedidos,
                                 ProveedorProductoConfig proveedorConfig) {
        this.repoProductos = Objects.requireNonNull(repoProductos);
        this.repoVentas = Objects.requireNonNull(repoVentas);
        this.svcPedidos = Objects.requireNonNull(svcPedidos);
        this.proveedorConfig = Objects.requireNonNull(proveedorConfig);
    }

    @Override
    public void altaProducto(Producto p) {
        repoProductos.guardar(p);
    }

    @Override
    public void bajaProducto(String id) {
        repoProductos.eliminar(id);
    }

    @Override
    public void modificarProducto(Producto p) {
        repoProductos.guardar(p);
    }

    @Override
    public List<Producto> listarProductos() {
        return repoProductos.listar();
    }

    @Override
    public void registrarVenta(List<ItemVenta> items)
            throws EntidadNoEncontradaException, StockInsuficienteException {
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("Venta sin items");

        Map<String, Producto> porId = repoProductos.listar().stream()
                .collect(Collectors.toMap(Producto::id, p -> p));

        // prevalidación
        for (ItemVenta it : items) {
            var p = porId.get(it.productoId());
            if (p == null) throw new EntidadNoEncontradaException("Producto " + it.productoId() + " inexistente");
            if (p instanceof Combo combo) {
                // validar stock en cada componente
                for (var comp : combo.componentes()) {
                    int needed = comp.cantidad() * it.cantidad();
                    if (comp.producto().getStockActual() < needed)
                        throw new StockInsuficienteException(comp.producto().id(), needed, comp.producto().getStockActual());
                }
            } else {
                if (p.getStockActual() < it.cantidad())
                    throw new StockInsuficienteException(p.id(), it.cantidad(), p.getStockActual());
            }
        }

        // aplicar descuentos
        for (ItemVenta it : items) {
            var p = porId.get(it.productoId());
            if (p instanceof Combo combo) combo.descontarStock(it.cantidad());
            else p.descontarStock(it.cantidad());
        }

        // construir venta con precios vigentes
        var venta = Venta.desde(items, porId::get);
        repoVentas.guardar(venta);

        // persistir productos actualizados
        porId.values().forEach(repoProductos::guardar);
    }

    @Override
    public void reponerSiCorresponde() {
        for (var p : repoProductos.listar()) {
            reponerSiCorresponde(p);
        }
    }

    @Override
    public void reponerSiCorresponde(Producto producto) {
        if (producto.getStockActual() >= producto.getStockMinimo()) return;

        int faltante = producto.getStockMinimo() - producto.getStockActual();
        var provOpt = proveedorConfig.proveedorDe(producto.id());
        if (provOpt.isEmpty()) return;

        var pedido = svcPedidos.crearPedido(provOpt.get());
        svcPedidos.agregarItemPedido(pedido.id(), producto.id(), faltante);
    }

}
