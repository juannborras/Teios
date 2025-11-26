package ui;

import dao.IRepositorioProducto;
import dao.IRepositorioVenta;
import modelo.Combo;
import modelo.LineaVenta;
import modelo.Producto;
import modelo.Venta;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Panel que muestra el historial de ventas y al seleccionar una venta muestra sus líneas.
 */
public class VentasListPanel extends JPanel implements Refreshable {
    private final IRepositorioVenta repoVentas;
    private final IRepositorioProducto repoProductos;
    private JTable tblVentas;
    private JTable tblLineas;
    private VentasTableModel ventasModel;
    private LineasTableModel lineasModel;
    private JButton btnVerComposicion; // para la linea seleccionada
    private JButton btnRecargar; // nuevo

    public VentasListPanel(IRepositorioVenta repoVentas, IRepositorioProducto repoProductos) {
        this.repoVentas = repoVentas;
        this.repoProductos = repoProductos;
        initUI();
        cargarVentas();
    }

    private void initUI(){
        setLayout(new BorderLayout(6,6));
        ventasModel = new VentasTableModel();
        lineasModel = new LineasTableModel();

        tblVentas = new JTable(ventasModel);
        tblLineas = new JTable(lineasModel);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(tblVentas), new JScrollPane(tblLineas));
        split.setResizeWeight(0.6);
        add(split, BorderLayout.CENTER);

        JPanel topButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnVerComposicion = new JButton("Ver Composición");
        btnRecargar = new JButton("Recargar");
        topButtons.add(btnRecargar);
        topButtons.add(btnVerComposicion);
        add(topButtons, BorderLayout.NORTH);

        tblVentas.getSelectionModel().addListSelectionListener(e -> onVentaSelected());
        btnVerComposicion.addActionListener(e -> mostrarComposicionLineaSeleccionada());
        btnRecargar.addActionListener(e -> cargarVentas());
    }

    @Override
    public void refresh(){
        cargarVentas();
    }

    public void cargarVentas(){
        ventasModel.setData(repoVentas.listar());
    }

    private void onVentaSelected(){
        int r = tblVentas.getSelectedRow();
        if (r < 0) { lineasModel.setData(List.of()); return; }
        Venta v = ventasModel.getAt(r);
        lineasModel.setData(v.getLineas());
    }

    private void mostrarComposicionLineaSeleccionada(){
        int r = tblLineas.getSelectedRow();
        if (r < 0) { JOptionPane.showMessageDialog(this, "Seleccione una línea"); return; }
        LineaVenta lv = lineasModel.getAt(r);
        Producto prod = repoProductos.buscar(lv.getProducto().id()).orElse(null);
        if (prod == null) { JOptionPane.showMessageDialog(this, "Producto no encontrado en repositorio"); return; }
        if (!(prod instanceof Combo cb)) { JOptionPane.showMessageDialog(this, "La línea seleccionada no corresponde a un Combo"); return; }
        StringBuilder sb = new StringBuilder();
        sb.append("Combo: ").append(cb.getNombre()).append("\n\n");
        BigDecimal total = BigDecimal.ZERO;
        for (var c : cb.componentes()){
            BigDecimal pu = c.producto().precio();
            BigDecimal sub = pu.multiply(BigDecimal.valueOf(c.cantidad()));
            sb.append(String.format("%s - %s x %d = %s\n", c.producto().id(), c.producto().getNombre(), c.cantidad(), sub.toPlainString()));
            total = total.add(sub);
        }
        sb.append("\nPrecio total: ").append(total.toPlainString());
        JOptionPane.showMessageDialog(this, sb.toString(), "Composición Combo", JOptionPane.INFORMATION_MESSAGE);
    }

    private static class VentasTableModel extends AbstractTableModel{
        private final String[] cols = {"ID","Fecha","Total"};
        private List<Venta> data = new ArrayList<>();
        void setData(List<Venta> ventas){ this.data = new ArrayList<>(ventas); fireTableDataChanged(); }
        Venta getAt(int r){ return data.get(r); }
        @Override public int getRowCount(){ return data.size(); }
        @Override public int getColumnCount(){ return cols.length; }
        @Override public String getColumnName(int c){ return cols[c]; }
        @Override public Object getValueAt(int r,int c){ var v = data.get(r); return switch(c){ case 0 -> v.id(); case 1 -> v.getFecha(); default -> v.getTotal(); }; }
    }

    private class LineasTableModel extends AbstractTableModel{
        private final String[] cols = {"ProductoId","Nombre","Cantidad","PrecioUnitario","Subtotal"};
        private List<LineaVenta> data = new ArrayList<>();
        void setData(List<LineaVenta> lineas){ this.data = new ArrayList<>(lineas); fireTableDataChanged(); }
        LineaVenta getAt(int r){ return data.get(r); }
        @Override public int getRowCount(){ return data.size(); }
        @Override public int getColumnCount(){ return cols.length; }
        @Override public String getColumnName(int c){ return cols[c]; }
        @Override public Object getValueAt(int r,int c){ var l = data.get(r); switch(c){ case 0: return l.getProducto().id(); case 1: return l.getProducto().getNombre(); case 2: return l.getCantidad(); case 3: return l.getPrecioUnitario(); default: return l.subtotal(); } }
    }
}
