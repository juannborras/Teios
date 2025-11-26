package test;

import dao.*;
import modelo.*;
import servicio.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class test {
    private static final Logger LOG = Logger.getLogger("test");
    public static void main(String[] args) {
        try {
            // === Directorio de datos (un solo Path para cada repo) ===

            Path dataDir = Path.of("./data");
            Files.createDirectories(dataDir);

            IRepositorioProducto repoProductos = new RepositorioProductoCSV(dataDir.resolve("productos.csv"));
            IRepositorioVenta repoVentas = new RepositorioVentaCSV(repoProductos, dataDir);
            IRepositorioPedido repoPedidos = new RepositorioPedidoCSV(dataDir);
            // ⛳️ PROVEEDORES: apuntar al archivo, no al directorio
            IRepositorioProveedor repoProveedores = new RepositorioProveedorCSV(
                    dataDir.resolve("proveedores.csv")
            );

            // Servicio de pedidos (declarado con la clase concreta para usar métodos concretos)
            ServicioPedidosCSV svcPedidos = new ServicioPedidosCSV(repoProductos, repoPedidos, repoProveedores);

            // Config de mapeo producto→proveedor (csv en data/producto_proveedor.csv)
            ProveedorProductoConfig provCfg = new ProveedorProductoConfig(dataDir.resolve("producto_proveedor.csv"));

            // Servicio de inventario (inyecto pedidos + mapeo)
            IServicioInventario svcInv = new ServicioInventarioCSV(repoProductos, repoVentas, svcPedidos, provCfg);



            // === 1) STOCK INICIAL ===
            System.out.println("== STOCK INICIAL ==");
            for (Producto p : repoProductos.listar()) {
                System.out.printf("%s (%s): %d / min %d%n",
                        p.getNombre(), p.id(), p.getStockActual(), p.getStockMinimo());
            }

            // Tomo dos productos por id (ajustá si tus IDs difieren)
            Producto pan   = repoProductos.buscar("PAN").orElseThrow();
            Producto queso = repoProductos.buscar("QUESO").orElseThrow();

            // === 2) REGISTRAR VENTA ===
            List<ItemVenta> items = List.of(
                    new ItemVenta(pan.id(), 3),
                    new ItemVenta(queso.id(), 2)
            );
            svcInv.registrarVenta(items);
            System.out.println("\nVenta registrada (3 PAN, 2 QUESO)");

            // Verifico stock tras la venta
            pan   = repoProductos.buscar("PAN").orElseThrow();
            queso = repoProductos.buscar("QUESO").orElseThrow();
            System.out.printf("PAN stock: %d%n", pan.getStockActual());
            System.out.printf("QUESO stock: %d%n", queso.getStockActual());

            // === 3) REPONER SI CORRESPONDE ===
            svcInv.reponerSiCorresponde();
            System.out.println("\nReposición ejecutada. Pedidos actuales:");
            for (PedidoReposicion pr : svcPedidos.listarPedidos()) {
                System.out.printf("- %s [%s] items=%d%n", pr.id(), pr.getEstado(), pr.getItems().size());
            }


            // === 4) ENVIAR TODOS LOS PENDIENTES CON ITEMS (SIN DUPLICADOS) ===
            // deduplicar usando un LinkedHashMap para preservar orden
            Map<String, PedidoReposicion> pendientesMap = new LinkedHashMap<>();
            for (PedidoReposicion p : svcPedidos.listarPedidos()) {
                if (p.getEstado() == EstadoPedido.PENDIENTE && !p.getItems().isEmpty()) {
                    pendientesMap.putIfAbsent(p.id(), p);
                }
            }

            if (pendientesMap.isEmpty()) {
                System.out.println("No hay pedidos PENDIENTES con items para enviar.");
            } else {
                for (PedidoReposicion p : pendientesMap.values()) {
                    svcPedidos.marcarPedidoEnviado(p.id());
                    System.out.println("Pedido ENVIADO: " + p.id());
                }

                // === 5) RECIBIR TODOS LOS ENVIADOS (en lote, visible) ===
                System.out.println("\n== Recibiendo TODOS los pedidos ENVIADOS ==\n");

                int recibidos = svcPedidos.recibirTodosEnviados(LocalDate.now());

                System.out.println("✅ Pedidos recibidos en lote: " + recibidos);

                // Verificar stocks de ejemplo
                Producto panCheck   = repoProductos.buscar("PAN").orElse(null);
                Producto quesoCheck = repoProductos.buscar("QUESO").orElse(null);
                if (panCheck != null)   System.out.printf("PAN stock tras recibir: %d%n", panCheck.getStockActual());
                if (quesoCheck != null) System.out.printf("QUESO stock tras recibir: %d%n", quesoCheck.getStockActual());
            }


            // === 6) LISTAR VENTAS ===
            System.out.println("\n== Ventas registradas ==");
            for (Venta v : repoVentas.listar()) {
                // Usa getters reales: getId()/getFecha()/getLineas()/getTotal()
                System.out.printf("%s - %s - items=%d, total=%s%n",
                        v.id(), v.getFecha(), v.getLineas().size(), v.getTotal());
            }

            System.out.println("\nOK: flujo de prueba completado.");
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "ERROR en el flujo de prueba", e);
            System.err.println("ERROR en el flujo de prueba: " + e.getMessage());
        }
    }
}