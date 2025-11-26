package ui;

import dao.*;
import modelo.*;
import servicio.*;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;

/**
 * JFrame principal que orquesta la aplicación Swing.
 * Provee tabs para: Registrar Venta y Gestionar Pedidos de Reposición.
 */
public class MainApp extends JFrame {
    public MainApp(Path dataDir) {
        super("Sistema Inventario & Ventas");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000, 650);
        setLocationRelativeTo(null);

        // Inicializar repositorios (variables locales, no campos)
        IRepositorioProducto repoProductos = new RepositorioProductoCSV(dataDir.resolve("productos.csv"));
        IRepositorioVenta repoVentas = new RepositorioVentaCSV(repoProductos, dataDir);
        IRepositorioPedido repoPedidos = new RepositorioPedidoCSV(dataDir);
        IRepositorioProveedor repoProveedores = new RepositorioProveedorCSV(dataDir.resolve("proveedores.csv"));
        ProveedorProductoConfig provCfg = new ProveedorProductoConfig(dataDir.resolve("producto_proveedor.csv"));

        IServicioPedidos svcPedidos = new ServicioPedidosCSV(repoProductos, repoPedidos, repoProveedores);
        IServicioInventario svcInventario = new ServicioInventarioCSV(repoProductos, repoVentas, svcPedidos, provCfg);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Ventas", new VentasPanel(repoProductos, repoVentas, svcInventario, repoProveedores, provCfg));
        tabs.addTab("Pedidos", new PedidosPanel(repoProductos, repoPedidos, repoProveedores, svcPedidos));
        tabs.addTab("Historial Ventas", new VentasListPanel(repoVentas, repoProductos));

        setContentPane(tabs);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MainApp(Path.of("./data")).setVisible(true));
    }
}
