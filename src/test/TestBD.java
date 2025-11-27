package test;

import dao.*;
import excepciones.EntidadNoEncontradaException;
import excepciones.StockInsuficienteException;
import modelo.*;
import servicio.*;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class TestBD {

    public static void main(String[] args) {
        try {
            // === 0) REPOSITORIOS CONTRA BD ===
            IRepositorioProducto repoProductos      = new RepositorioProductoJDBC();
            IRepositorioVenta repoVentas            = new RepositorioVentaJDBC();
            IRepositorioPedido repoPedidos          = new RepositorioPedidoJDBC();
            IRepositorioProveedor repoProveedores   = new RepositorioProveedorJDBC();

            // Config de mapeo producto → proveedor (sigue leyendo el CSV de config)
            Path dataDir = Path.of("./data");
            ProveedorProductoConfig provCfg = new ProveedorProductoConfig(
                    dataDir.resolve("producto_proveedor.csv")
            );

            // Servicios usando repos JDBC
            IServicioPedidos svcPedidos = new ServicioPedidos(
                    repoProductos, repoPedidos, repoProveedores
            );
            IServicioInventario svcInv = new ServicioInventario(
                    repoProductos, repoVentas, svcPedidos, provCfg
            );

            System.out.println("======== TEST BD TEIO'S ========");

            // ==========================
            // 1) STOCK INICIAL
            // ==========================
            System.out.println("\n== STOCK INICIAL (BD) ==");
            List<Producto> productos = repoProductos.listar();
            for (Producto p : productos) {
                System.out.printf("%s (%s): stock=%d, min=%d%n",
                        p.getNombre(), p.id(), p.getStockActual(), p.getStockMinimo());
            }

            if (productos.size() < 2) {
                throw new IllegalStateException("Se necesitan al menos 2 productos para el test");
            }

            // Tomo dos productos cualquiera (los dos primeros)
            Producto prodA = productos.get(0);
            Producto prodB = productos.get(1);

            int stockAInicial = prodA.getStockActual();
            int stockBInicial = prodB.getStockActual();

            // ==========================
            // 2) TEST: REGISTRAR VENTA
            // ==========================
            System.out.println("\n== REGISTRAR VENTA ==");
            int cantA = Math.min(2, stockAInicial); // por si el stock es bajo
            int cantB = Math.min(1, stockBInicial);

            if (cantA == 0 || cantB == 0) {
                System.out.println("Algún producto sin stock, la venta no se puede probar bien.");
            } else {
                List<ItemVenta> items = List.of(
                        new ItemVenta(prodA.id(), cantA),
                        new ItemVenta(prodB.id(), cantB)
                );

                try {
                    svcInv.registrarVenta(items);
                    System.out.printf("Venta registrada: %d x %s, %d x %s%n",
                            cantA, prodA.getNombre(), cantB, prodB.getNombre());
                } catch (EntidadNoEncontradaException | StockInsuficienteException e) {
                    e.printStackTrace();
                    throw new RuntimeException("Fallo al registrar la venta", e);
                }

                // Recargar productos desde BD
                Producto prodAPost = repoProductos.buscar(prodA.id()).orElseThrow();
                Producto prodBPost = repoProductos.buscar(prodB.id()).orElseThrow();

                int esperadoA = stockAInicial - cantA;
                int esperadoB = stockBInicial - cantB;

                System.out.printf("Stock %s antes=%d, después=%d (esperado=%d)%n",
                        prodA.getNombre(), stockAInicial, prodAPost.getStockActual(), esperadoA);
                System.out.printf("Stock %s antes=%d, después=%d (esperado=%d)%n",
                        prodB.getNombre(), stockBInicial, prodBPost.getStockActual(), esperadoB);

                if (prodAPost.getStockActual() != esperadoA ||
                        prodBPost.getStockActual() != esperadoB) {
                    throw new AssertionError("❌ ERROR: el descuento de stock por venta no se reflejó bien en la BD");
                } else {
                    System.out.println("✅ OK: descuento de stock persistido correctamente en la BD");
                }

                // Ver ventas en BD
                System.out.println("\n== VENTAS EN BD ==");
                List<Venta> ventas = repoVentas.listar();
                for (Venta v : ventas) {
                    System.out.printf("%s - %s - items=%d, total=%s%n",
                            v.id(), v.getFecha(), v.getLineas().size(), v.getTotal());
                }
            }

            // ==========================
            // 3) TEST: CREAR + RECIBIR PEDIDO
            // ==========================
            System.out.println("\n== CREAR Y RECIBIR PEDIDO ==");
            List<Proveedor> proveedores = repoProveedores.listar();
            if (proveedores.isEmpty()) {
                throw new IllegalStateException("No hay proveedores en la BD, no se puede probar pedidos");
            }
            Proveedor proveedor = proveedores.get(0);

            // Tomo un producto cualquiera para reponer
            Producto prodPedido = repoProductos.listar().get(1);
            int stockAntesPedido = prodPedido.getStockActual();
            int cantPedido = 5;

            // Crear pedido
            PedidoReposicion pedido = svcPedidos.crearPedido(proveedor.id());
            svcPedidos.agregarItemPedido(pedido.id(), prodPedido.id(), cantPedido);
            System.out.printf("Pedido creado: %s → proveedor %s, %d x %s%n",
                    pedido.id(), proveedor.getNombre(), cantPedido, prodPedido.getNombre());

            // Enviar y recibir

            svcPedidos.marcarPedidoEnviado(pedido.id());
            // usar implementación concreta para sumar stock (si está disponible)
            if (svcPedidos instanceof ServicioPedidos sp) {
                sp.recibirPedido(pedido.id(), LocalDate.now());
            } else {
                svcPedidos.marcarPedidoRecibido(pedido.id());
            }

            // Recargar producto desde BD
            Producto prodDespuesPedido = repoProductos.buscar(prodPedido.id()).orElseThrow();

            // Obtener las cantidades REALES del pedido desde la BD
            Map<String, Integer> itemsBD = repoPedidos.obtenerItems(pedido.id());
            int totalCantidadPedido = itemsBD.values().stream().mapToInt(Integer::intValue).sum();

            int esperadoDespuesPedido = stockAntesPedido + totalCantidadPedido;

            System.out.printf("Stock %s antes pedido=%d, después pedido=%d (esperado=%d, segun BD)%n",
                    prodPedido.getNombre(), stockAntesPedido,
                    prodDespuesPedido.getStockActual(), esperadoDespuesPedido);

            if (prodDespuesPedido.getStockActual() != esperadoDespuesPedido) {
                throw new AssertionError("❌ ERROR: la recepción del pedido no es consistente con los items guardados en BD");
            } else {
                System.out.println("✅ OK: recepción de pedido persistida correctamente en la BD y consistente con item_pedido");
            }


            // Listar pedidos finales
            System.out.println("\n== PEDIDOS EN BD ==");
            for (PedidoReposicion pr : svcPedidos.listarPedidos()) {
                System.out.printf("%s - proveedor=%s - estado=%s - items=%d%n",
                        pr.id(),
                        pr.getProveedor().id(),
                        pr.getEstado(),
                        pr.getItems().size());
            }

            System.out.println("\n🎉 TEST COMPLETO: persistencia en BD funcionando correctamente.");
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("\n💥 ERROR en el flujo de prueba: " + e.getMessage());
        }
    }
}
