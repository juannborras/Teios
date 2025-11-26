package ui;

import dao.IRepositorioPedido;
import dao.IRepositorioProducto;
import dao.IRepositorioProveedor;
import modelo.EstadoPedido;
import modelo.PedidoReposicion;
import modelo.Producto;
import servicio.IServicioPedidos;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Panel para gestionar pedidos de reposición:
 * - Listar pedidos
 * - Crear pedido nuevo
 * - Agregar items
 * - Enviar y recibir pedidos
 */
public class PedidosPanel extends JPanel {
    private final IRepositorioProducto repoProductos;
    private final IRepositorioPedido repoPedidos;
    private final IRepositorioProveedor repoProveedores;
    private final IServicioPedidos svcPedidos;

    private PedidosTableModel pedidosModel;
    private JTable tablaPedidos;
    private JButton btnCrear;
    private JButton btnAgregarItem;
    private JButton btnEnviar;
    private JButton btnRecibir;
    private JButton btnEnviarPendientes;
    private JButton btnRecibirEnviados;

    public PedidosPanel(IRepositorioProducto repoProductos,
                        IRepositorioPedido repoPedidos,
                        IRepositorioProveedor repoProveedores,
                        IServicioPedidos svcPedidos) {
        this.repoProductos = repoProductos;
        this.repoPedidos = repoPedidos;
        this.repoProveedores = repoProveedores;
        this.svcPedidos = svcPedidos;
        initUI();
        cargarPedidos();
    }

    private void initUI(){
        setLayout(new BorderLayout(8,8));
        pedidosModel = new PedidosTableModel();
        tablaPedidos = new JTable(pedidosModel);
        add(new JScrollPane(tablaPedidos), BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btnCrear = new JButton("Crear Pedido");
        btnAgregarItem = new JButton("Agregar Item");
        btnEnviar = new JButton("Marcar Enviado");
        btnRecibir = new JButton("Marcar Recibido");
        btnEnviarPendientes = new JButton("Enviar PENDIENTES");
        btnRecibirEnviados = new JButton("Recibir ENVIADOS");
        actions.add(btnCrear);
        actions.add(btnAgregarItem);
        actions.add(btnEnviar);
        actions.add(btnRecibir);
        actions.add(btnEnviarPendientes);
        actions.add(btnRecibirEnviados);
        add(actions, BorderLayout.SOUTH);

        btnCrear.addActionListener(e -> crearPedido());
        btnAgregarItem.addActionListener(e -> agregarItem());
        btnEnviar.addActionListener(e -> marcarEnviado());
        btnRecibir.addActionListener(e -> marcarRecibido());
        btnEnviarPendientes.addActionListener(e -> enviarPendientes());
        btnRecibirEnviados.addActionListener(e -> recibirEnviados());
    }

    private void cargarPedidos(){ pedidosModel.setData(svcPedidos.listarPedidos()); }

    private PedidoReposicion pedidoSeleccionado(){ int r = tablaPedidos.getSelectedRow(); return r>=0? pedidosModel.getAt(r): null; }

    private void crearPedido(){
        // elegir proveedor
        var proveedores = repoProveedores.listar();
        if (proveedores.isEmpty()) { JOptionPane.showMessageDialog(this, "No hay proveedores"); return; }
        var opciones = proveedores.stream().map(p -> p.id()+" - "+p.getNombre()).toArray(String[]::new);
        String sel = (String) JOptionPane.showInputDialog(this, "Seleccione proveedor", "Nuevo Pedido", JOptionPane.PLAIN_MESSAGE, null, opciones, opciones[0]);
        if (sel==null) return;
        String provId = sel.split(" ")[0];
        var pedido = svcPedidos.crearPedido(provId);
        JOptionPane.showMessageDialog(this, "Pedido creado: "+pedido.id());
        cargarPedidos();
    }

    private void agregarItem(){
        var pedido = pedidoSeleccionado();
        if (pedido==null){ JOptionPane.showMessageDialog(this, "Seleccione un pedido"); return; }
        if (pedido.getEstado()!= EstadoPedido.PENDIENTE){ JOptionPane.showMessageDialog(this, "Solo PENDIENTE"); return; }
        var productos = repoProductos.listar();
        if (productos.isEmpty()){ JOptionPane.showMessageDialog(this,"No hay productos"); return; }
        String[] opciones = productos.stream().map(p-> p.id()+" - "+p.getNombre()).toArray(String[]::new);
        String sel = (String) JOptionPane.showInputDialog(this, "Seleccione producto", "Agregar Item", JOptionPane.PLAIN_MESSAGE, null, opciones, opciones[0]);
        if (sel==null) return;
        String prodId = sel.split(" ")[0];
        String cantStr = JOptionPane.showInputDialog(this, "Cantidad", "1");
        if (cantStr==null) return; int cant;
        try { cant = Integer.parseInt(cantStr); if (cant<=0) throw new NumberFormatException(); }
        catch (NumberFormatException ex){ JOptionPane.showMessageDialog(this, "Cantidad inválida"); return; }
        svcPedidos.agregarItemPedido(pedido.id(), prodId, cant);
        cargarPedidos();
    }

    private void marcarEnviado(){
        var pedido = pedidoSeleccionado();
        if (pedido==null){ JOptionPane.showMessageDialog(this, "Seleccione un pedido"); return; }
        try {
            svcPedidos.marcarPedidoEnviado(pedido.id());
            cargarPedidos();
        } catch (Exception ex){ JOptionPane.showMessageDialog(this, ex.getMessage()); }
    }

    private void marcarRecibido(){
        var pedido = pedidoSeleccionado();
        if (pedido==null){ JOptionPane.showMessageDialog(this, "Seleccione un pedido"); return; }
        try {
            svcPedidos.recibirPedido(pedido.id(), LocalDate.now());
            cargarPedidos();
        } catch (Exception ex){ JOptionPane.showMessageDialog(this, ex.getMessage()); }
    }

    // === nuevas acciones en lote ===
    private void enviarPendientes(){
        var pedidos = svcPedidos.listarPedidos();
        int enviados = 0;
        for (var p : pedidos){
            if (p.getEstado()==EstadoPedido.PENDIENTE && !p.getItems().isEmpty()){
                try { svcPedidos.marcarPedidoEnviado(p.id()); enviados++; } catch (Exception ignored) {}
            }
        }
        JOptionPane.showMessageDialog(this, "Pedidos enviados: "+enviados);
        cargarPedidos();
    }

    private void recibirEnviados(){
        int recibidos = 0;
        if (svcPedidos instanceof servicio.ServicioPedidosCSV sp){
            recibidos = sp.recibirTodosEnviados(LocalDate.now());
        } else {
            for (var p : svcPedidos.listarPedidos()){
                if (p.getEstado()==EstadoPedido.ENVIADO && !p.getItems().isEmpty()){
                    try { svcPedidos.marcarPedidoRecibido(p.id()); recibidos++; } catch (Exception ignored) {}
                }
            }
        }
        JOptionPane.showMessageDialog(this, "Pedidos recibidos: "+recibidos);
        cargarPedidos();
    }

    // ==== Modelo tabla pedidos ====
    private static class PedidosTableModel extends AbstractTableModel {
        private final String[] cols = {"ID","Proveedor","Items","Estado"};
        private List<PedidoReposicion> data = new ArrayList<>();
        public void setData(List<PedidoReposicion> pedidos){ this.data = new ArrayList<>(pedidos); fireTableDataChanged(); }
        public PedidoReposicion getAt(int r){ return data.get(r); }
        @Override public int getRowCount(){ return data.size(); }
        @Override public int getColumnCount(){ return cols.length; }
        @Override public String getColumnName(int c){ return cols[c]; }
        @Override public Object getValueAt(int r,int c){ var p = data.get(r); return switch (c){
            case 0 -> p.id();
            case 1 -> p.getProveedor().getNombre();
            case 2 -> p.getItems().size();
            case 3 -> p.getEstado();
            default -> null;}; }
    }
}
