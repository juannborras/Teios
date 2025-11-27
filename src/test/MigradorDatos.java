package test;

import dao.*;
import modelo.*;
import servicio.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class MigradorDatos {

    public static void main(String[] args) {
        try {
            // Carpeta donde ya TENÉS los CSV
            Path dataDir = Path.of("./data");
            Files.createDirectories(dataDir);

            // === REPOS CSV (LECTURA) ===
            IRepositorioProducto   repoProdCsv   = new RepositorioProductoCSV(dataDir.resolve("productos.csv"));
            IRepositorioVenta      repoVentaCsv  = new RepositorioVentaCSV(repoProdCsv, dataDir);
            IRepositorioPedido     repoPedCsv    = new RepositorioPedidoCSV(dataDir);
            IRepositorioProveedor  repoProvCsv   = new RepositorioProveedorCSV(dataDir.resolve("proveedores.csv"));

            // === REPOS JDBC (ESCRITURA) ===
            IRepositorioProducto   repoProdDb   = new RepositorioProductoJDBC();
            IRepositorioVenta      repoVentaDb  = new RepositorioVentaJDBC();
            IRepositorioPedido     repoPedDb    = new RepositorioPedidoJDBC();
            IRepositorioProveedor  repoProvDb   = new RepositorioProveedorJDBC();

            // ==========================
            // 1) MIGRAR PROVEEDORES
            // ==========================
            System.out.println("Migrando PROVEEDORES...");
            int countProv = 0;
            for (Proveedor p : repoProvCsv.listar()) {
                repoProvDb.guardar(p);
                countProv++;
            }
            System.out.println("Proveedores migrados: " + countProv);

            // ==========================
            // 2) MIGRAR PRODUCTOS
            // ==========================
            System.out.println("Migrando PRODUCTOS...");
            int countProd = 0;
            for (Producto p : repoProdCsv.listar()) {
                repoProdDb.guardar(p);
                countProd++;
            }
            System.out.println("Productos migrados: " + countProd);

            // ==========================
            // 3) MIGRAR PEDIDOS + ITEMS
            // ==========================
            System.out.println("Migrando PEDIDOS...");
            int countPed = 0;
            for (PedidoReposicion pedido : repoPedCsv.listar()) {
                // Guarda cabecera
                repoPedDb.guardarCabecera(pedido);
                // Trae items crudos desde CSV y los inserta en la BD
                Map<String,Integer> items = repoPedCsv.obtenerItems(pedido.id());
                for (var entry : items.entrySet()) {
                    String prodId = entry.getKey();
                    int cantidad = entry.getValue();
                    repoPedDb.agregarItem(pedido.id(), prodId, cantidad);
                }
                countPed++;
            }
            System.out.println("Pedidos migrados: " + countPed);

            // ==========================
            // 4) MIGRAR VENTAS + LÍNEAS
            // ==========================
            System.out.println("Migrando VENTAS...");
            int countVentas = 0;
            for (Venta v : repoVentaCsv.listar()) {
                repoVentaDb.guardar(v);
                countVentas++;
            }
            System.out.println("Ventas migradas: " + countVentas);

            System.out.println("\n✅ Migración completada OK.");
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("❌ Error durante la migración: " + e.getMessage());
        }
    }
}
