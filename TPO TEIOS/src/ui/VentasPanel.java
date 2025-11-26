package ui;

import dao.IRepositorioProducto;
import dao.IRepositorioVenta;
import excepciones.EntidadNoEncontradaException;
import excepciones.StockInsuficienteException;
import modelo.ItemVenta;
import modelo.Producto;
import modelo.Ingrediente;
import modelo.UnidadMedida;
import servicio.IServicioInventario;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

public class VentasPanel extends JPanel {
    private final IRepositorioProducto repoProductos;
    private final IRepositorioVenta repoVentas;
    private final IServicioInventario svcInventario;
    private final dao.IRepositorioProveedor repoProveedores;
    private final dao.ProveedorProductoConfig provCfg;

    private ProductosTableModel productosModel;
    private ItemsTableModel itemsModel;

    private JTable tablaProductos;
    private JTable tablaItems;
    private JSpinner spnCantidad;
    private JButton btnAgregar;
    private JButton btnConfirmar;
    private JButton btnReponerAuto;
    private JButton btnEliminarItem;
    private JButton btnCrearCombo;
    private JButton btnCrearIngrediente; // nuevo
    private JButton btnRecargar;
    private JButton btnVerComposicion;

    private JLabel lblTotal;
    private final NumberFormat currency = NumberFormat.getCurrencyInstance();

    public VentasPanel(IRepositorioProducto repoProductos,
                       IRepositorioVenta repoVentas,
                       IServicioInventario svcInventario,
                       dao.IRepositorioProveedor repoProveedores,
                       dao.ProveedorProductoConfig provCfg) {
        this.repoProductos = repoProductos;
        this.repoVentas = repoVentas;
        this.svcInventario = svcInventario;
        this.repoProveedores = repoProveedores;
        this.provCfg = provCfg;
        initUI();
        cargarProductos();
    }

    private void initUI() {
        setLayout(new BorderLayout(8,8));

        productosModel = new ProductosTableModel();
        itemsModel = new ItemsTableModel();

        tablaProductos = new JTable(productosModel);
        tablaItems = new JTable(itemsModel);

        spnCantidad = new JSpinner(new SpinnerNumberModel(1,1,999,1));
        btnAgregar = new JButton("Agregar");
        btnConfirmar = new JButton("Confirmar Venta");
        btnReponerAuto = new JButton("Reponer si corresponde");
        btnEliminarItem = new JButton("Eliminar Item");
        btnCrearCombo = new JButton("Crear Combo");
        btnCrearIngrediente = new JButton("Crear Ingrediente");
        btnRecargar = new JButton("Recargar");
        btnVerComposicion = new JButton("Ver Composición");

        lblTotal = new JLabel("Total: " + currency.format(BigDecimal.ZERO));

        JPanel left = new JPanel(new BorderLayout());
        left.add(new JLabel("Productos"), BorderLayout.NORTH);
        left.add(new JScrollPane(tablaProductos), BorderLayout.CENTER);

        JPanel addPanel = new JPanel();
        addPanel.add(new JLabel("Cantidad:"));
        addPanel.add(spnCantidad);
        addPanel.add(btnAgregar);
        addPanel.add(btnCrearCombo);
        addPanel.add(btnCrearIngrediente);
        JButton btnEliminarProducto = new JButton("Eliminar Producto");
        addPanel.add(btnEliminarProducto);
        addPanel.add(btnRecargar);
        addPanel.add(btnVerComposicion);
        left.add(addPanel, BorderLayout.SOUTH);

        JPanel right = new JPanel(new BorderLayout());
        right.add(new JLabel("Items Venta"), BorderLayout.NORTH);
        right.add(new JScrollPane(tablaItems), BorderLayout.CENTER);
        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        acciones.add(btnEliminarItem);
        acciones.add(btnReponerAuto);
        acciones.add(btnConfirmar);
        right.add(acciones, BorderLayout.SOUTH);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottom.add(lblTotal);
        right.add(bottom, BorderLayout.NORTH);

        add(left, BorderLayout.WEST);
        add(right, BorderLayout.CENTER);

        btnAgregar.addActionListener(e -> agregarItem());
        btnConfirmar.addActionListener(e -> confirmarVenta());
        btnReponerAuto.addActionListener(e -> reponerSiCorresponde());
        btnEliminarItem.addActionListener(e -> eliminarItemSeleccionado());
        btnCrearCombo.addActionListener(e -> new CreateComboDialog().setVisible(true));
        btnCrearIngrediente.addActionListener(e -> new CreateIngredienteDialog().setVisible(true));
        btnRecargar.addActionListener(e -> { cargarProductos(); actualizarTotal(); });
        btnVerComposicion.addActionListener(e -> mostrarComposicionProductoSeleccionado());
        btnEliminarProducto.addActionListener(e -> eliminarProductoSeleccionado());
    }

    private void mostrarComposicionProductoSeleccionado(){
        int row = tablaProductos.getSelectedRow();
        if (row < 0) { JOptionPane.showMessageDialog(this, "Seleccione un producto"); return; }
        Producto p = productosModel.getAt(row);
        if (!(p instanceof modelo.Combo cb)) { JOptionPane.showMessageDialog(this, "El producto seleccionado no es un Combo"); return; }
        StringBuilder sb = new StringBuilder();
        sb.append("Combo: ").append(cb.getNombre()).append("\n\n");
        BigDecimal total = BigDecimal.ZERO;
        for (var c : cb.componentes()){
            BigDecimal pu = c.producto().precio();
            BigDecimal sub = pu.multiply(BigDecimal.valueOf(c.cantidad()));
            sb.append(String.format("%s - %s x %d = %s\n", c.producto().id(), c.producto().getNombre(), c.cantidad(), currency.format(sub)));
            total = total.add(sub);
        }
        sb.append("\nPrecio total: ").append(currency.format(total));
        JOptionPane.showMessageDialog(this, sb.toString(), "Composición Combo", JOptionPane.INFORMATION_MESSAGE);
    }

    private void cargarProductos() {
        productosModel.setData(repoProductos.listar());
    }

    private void agregarItem() {
        int row = tablaProductos.getSelectedRow();
        if (row < 0) { JOptionPane.showMessageDialog(this, "Seleccione un producto"); return; }
        Producto p = productosModel.getAt(row);
        int cant = (Integer) spnCantidad.getValue();
        if (!(p instanceof modelo.Combo) && cant > p.getStockActual()) {
            JOptionPane.showMessageDialog(this, "Stock insuficiente");
            return;
        }
        itemsModel.addItem(new ItemVenta(p.id(), cant));
        actualizarTotal();
    }

    private void eliminarItemSeleccionado() {
        int row = tablaItems.getSelectedRow();
        if (row < 0) { JOptionPane.showMessageDialog(this, "Seleccione un item para eliminar"); return; }
        int opt = JOptionPane.showConfirmDialog(this, "¿Eliminar el item seleccionado?", "Confirmar", JOptionPane.YES_NO_OPTION);
        if (opt == JOptionPane.YES_OPTION) {
            itemsModel.removeAt(row);
            actualizarTotal();
        }
    }

    private void confirmarVenta() {
        List<ItemVenta> items = itemsModel.getItems();
        if (items.isEmpty()) { JOptionPane.showMessageDialog(this, "Sin items"); return; }
        try {
            svcInventario.registrarVenta(items);
            JOptionPane.showMessageDialog(this, "Venta registrada");
            itemsModel.clear();
            actualizarTotal();
            cargarProductos(); // actualizar stock
            // refrescar pestaña Historial si existe
            var tp = (JTabbedPane) SwingUtilities.getAncestorOfClass(JTabbedPane.class, this);
            if (tp != null) {
                for (int i=0;i<tp.getTabCount();i++){
                    var c = tp.getComponentAt(i);
                    if (c instanceof Refreshable r) r.refresh();
                }
            }
        } catch (EntidadNoEncontradaException | StockInsuficienteException ex) {
            JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void reponerSiCorresponde(){
        svcInventario.reponerSiCorresponde();
        JOptionPane.showMessageDialog(this, "Pedidos de reposición generados (si aplicaba)");
    }

    private void actualizarTotal(){
        BigDecimal total = BigDecimal.ZERO;
        for (var it: itemsModel.getItems()){
            var prodOpt = repoProductos.buscar(it.productoId());
            BigDecimal precio = prodOpt.map(Producto::precio).orElse(BigDecimal.ZERO);
            total = total.add(precio.multiply(BigDecimal.valueOf(it.cantidad())));
        }
        lblTotal.setText("Total: " + currency.format(total));
    }

    private void eliminarProductoSeleccionado(){
        int r = tablaProductos.getSelectedRow();
        if (r < 0) { JOptionPane.showMessageDialog(this, "Seleccione un producto para eliminar"); return; }
        Producto p = productosModel.getAt(r);
        int opt = JOptionPane.showConfirmDialog(this, "¿Eliminar producto " + p.id() + "? Esta acción es irreversible.", "Confirmar eliminación", JOptionPane.YES_NO_OPTION);
        if (opt != JOptionPane.YES_OPTION) return;
        repoProductos.eliminar(p.id());
        // si existe asociación en provCfg, quitarla también
        provCfg.desasociar(p.id());
        cargarProductos();
        JOptionPane.showMessageDialog(this, "Producto eliminado");
    }

    // ==== Modelos de tabla ====
    private static class ProductosTableModel extends AbstractTableModel {
        private final String[] cols = {"ID","Nombre","Stock","Min","Precio"};
        private List<Producto> data = new ArrayList<>();
        public void setData(List<Producto> productos){ this.data = new ArrayList<>(productos); fireTableDataChanged(); }
        public Producto getAt(int row){ return data.get(row); }
        @Override public int getRowCount(){ return data.size(); }
        @Override public int getColumnCount(){ return cols.length; }
        @Override public String getColumnName(int c){ return cols[c]; }
        @Override public Object getValueAt(int r,int c){
            var p = data.get(r);
            return switch (c){
                case 0 -> p.id();
                case 1 -> p.getNombre();
                case 2 -> p.getStockActual();
                case 3 -> p.getStockMinimo();
                case 4 -> (p instanceof modelo.Ingrediente ing ? ing.getCostoPorUnidad() : BigDecimal.ZERO);
                default -> null;
            };
        }
    }

    private class ItemsTableModel extends AbstractTableModel {
        private final String[] cols = {"ProductoId","Cantidad","PrecioUnitario","Subtotal"};
        private List<ItemVenta> items = new ArrayList<>();
        public void addItem(ItemVenta it){ items.add(it); fireTableDataChanged(); }
        public List<ItemVenta> getItems(){ return List.copyOf(items); }
        public void clear(){ items.clear(); fireTableDataChanged(); }
        public void removeAt(int index){ items.remove(index); fireTableDataChanged(); }
        @Override public int getRowCount(){ return items.size(); }
        @Override public int getColumnCount(){ return cols.length; }
        @Override public String getColumnName(int c){ return cols[c]; }
        @Override public Object getValueAt(int r,int c){ var it = items.get(r); switch(c){
            case 0: return it.productoId();
            case 1: return it.cantidad();
            case 2: {
                var prod = repoProductos.buscar(it.productoId()).orElse(null);
                BigDecimal precio = prod == null ? BigDecimal.ZERO : prod.precio();
                return currency.format(precio);
            }
            case 3: {
                var prod = repoProductos.buscar(it.productoId()).orElse(null);
                BigDecimal precio = prod == null ? BigDecimal.ZERO : prod.precio();
                return currency.format(precio.multiply(BigDecimal.valueOf(it.cantidad())));
            }
            default: return null;
        } }
    }

    // ===== Dialogo para crear Combos =====
    private class CreateComboDialog extends JDialog {
        private JTextField txtId;
        private JTextField txtNombre;
        private JComboBox<Producto> cbProductos;
        private JSpinner spnCompCantidad;
        private JButton btnAddComp;
        private JButton btnSave;
        private JTable tblComps;
        private CompsTableModel compsModel;
        private JLabel lblPrecioTotal; // nuevo

        CreateComboDialog(){
            super(SwingUtilities.getWindowAncestor(VentasPanel.this), "Crear Combo", ModalityType.APPLICATION_MODAL);
            init();
            pack();
            setLocationRelativeTo(VentasPanel.this);
        }

        private void init(){
            setLayout(new BorderLayout(6,6));
            JPanel top = new JPanel(new GridLayout(0,2,4,4));
            txtId = new JTextField(); txtNombre = new JTextField();
            top.add(new JLabel("ID:")); top.add(txtId);
            top.add(new JLabel("Nombre:")); top.add(txtNombre);
            add(top, BorderLayout.NORTH);

            // componente selector
            JPanel mid = new JPanel();
            cbProductos = new JComboBox<>();
            cbProductos.setRenderer(new DefaultListCellRenderer(){
                @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus){
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    if (value instanceof Producto p) setText(p.id() + " - " + p.getNombre());
                    return this;
                }
            });
            for (Producto p: repoProductos.listar()) if (p instanceof modelo.Ingrediente) cbProductos.addItem(p);
            spnCompCantidad = new JSpinner(new SpinnerNumberModel(1,1,999,1));
            btnAddComp = new JButton("Agregar Componente");
            mid.add(new JLabel("Producto:")); mid.add(cbProductos);
            mid.add(new JLabel("Cantidad:")); mid.add(spnCompCantidad);
            mid.add(btnAddComp);
            add(mid, BorderLayout.CENTER);

            compsModel = new CompsTableModel();
            tblComps = new JTable(compsModel);

            // Lista textual de componentes ya agregados (se sincroniza con la tabla)
            DefaultListModel<String> lstModel = new DefaultListModel<>();
            JList<String> lstComps = new JList<>(lstModel);
            lstComps.setVisibleRowCount(6);

            // Panel inferior con tabla y lista lado a lado
            JPanel southPanel = new JPanel(new BorderLayout(6,6));
            southPanel.add(new JScrollPane(tblComps), BorderLayout.CENTER);
            JPanel rightList = new JPanel(new BorderLayout());
            rightList.add(new JLabel("Componentes agregados:"), BorderLayout.NORTH);
            rightList.add(new JScrollPane(lstComps), BorderLayout.CENTER);
            JButton btnRemoveComp = new JButton("Eliminar Componente");
            rightList.add(btnRemoveComp, BorderLayout.SOUTH);
            southPanel.add(rightList, BorderLayout.EAST);
            add(southPanel, BorderLayout.SOUTH);

            JPanel south = new JPanel(new BorderLayout());
            lblPrecioTotal = new JLabel("Precio total: 0");
            JPanel rightSouth = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            btnSave = new JButton("Guardar Combo");
            rightSouth.add(btnSave);
            south.add(lblPrecioTotal, BorderLayout.WEST);
            south.add(rightSouth, BorderLayout.EAST);
            add(south, BorderLayout.PAGE_END);

            btnAddComp.addActionListener(e -> { addComponente(); actualizarPrecio(); rebuildList(lstModel); });
            btnSave.addActionListener(e -> saveCombo());

            btnRemoveComp.addActionListener(e -> {
                int sel = tblComps.getSelectedRow();
                if (sel < 0) sel = lstComps.getSelectedIndex();
                if (sel < 0) { JOptionPane.showMessageDialog(this, "Seleccione un componente en la tabla o la lista para eliminar"); return; }
                compsModel.removeAt(sel);
                rebuildList(lstModel);
                actualizarPrecio();
            });
        }

        private void rebuildList(DefaultListModel<String> lstModel){
            lstModel.clear();
            for (var r: compsModel.getAll()) lstModel.addElement(String.format("%s - %s x %d", r.prod().id(), r.prod().getNombre(), r.cant()));
        }

        private void addComponente(){
            Producto p = (Producto) cbProductos.getSelectedItem();
            if (p == null) { JOptionPane.showMessageDialog(this, "Seleccione un producto"); return; }
            int c = (Integer) spnCompCantidad.getValue();
            // Merge: si ya existe el componente, incrementar cantidad
            boolean merged = false;
            for (var r : compsModel.getAll()){
                if (r.prod().id().equals(p.id())){
                    // Rebuild rows: remove and add with sum
                    int idx = compsModel.getAll().indexOf(r);
                    int newQty = r.cant() + c;
                    compsModel.removeAt(idx);
                    compsModel.add(p, newQty);
                    merged = true;
                    break;
                }
            }
            if (!merged) compsModel.add(p, c);
            actualizarPrecio();
        }

        // Devuelve una descripción corta del último componente añadido (para mostrar en la lista)
        private String describeLastComponent(){
            var all = compsModel.getAll();
            if (all.isEmpty()) return "";
            var r = all.get(all.size()-1);
            return String.format("%s - %s x %d", r.prod().id(), r.prod().getNombre(), r.cant());
        }

        private void actualizarPrecio(){
            BigDecimal total = BigDecimal.ZERO;
            for (var r: compsModel.getAll()){
                total = total.add(r.prod().precio().multiply(BigDecimal.valueOf(r.cant())));
            }
            lblPrecioTotal.setText("Precio total: " + currency.format(total));
        }

        private void saveCombo(){
            String id = txtId.getText().trim();
            String nombre = txtNombre.getText().trim();
            if (id.isEmpty() || nombre.isEmpty()){ JOptionPane.showMessageDialog(this, "ID y Nombre requeridos"); return; }
            if (compsModel.isEmpty()){ JOptionPane.showMessageDialog(this, "Agregue al menos un componente"); return; }

            modelo.Combo combo = new modelo.Combo(id, nombre);
            for (var c: compsModel.getAll()) combo.agregarComponente(c.prod(), c.cant());
            try {
                repoProductos.guardar(combo);
                JOptionPane.showMessageDialog(this, "Combo guardado");
                cargarProductos();
                dispose();
            } catch (RuntimeException ex){
                JOptionPane.showMessageDialog(this, "Error al guardar combo: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        // modelo simple para componentes del combo
        private class CompsTableModel extends AbstractTableModel {
            private final String[] cols = {"ProductoId","Nombre","Cantidad"};
            private final List<CompRow> rows = new ArrayList<>();
            void add(Producto p,int c){ rows.add(new CompRow(p,c)); fireTableDataChanged(); }
            void removeAt(int index){ rows.remove(index); fireTableDataChanged(); }
            boolean isEmpty(){ return rows.isEmpty(); }
            List<CompRow> getAll(){ return List.copyOf(rows); }
            @Override public int getRowCount(){ return rows.size(); }
            @Override public int getColumnCount(){ return cols.length; }
            @Override public String getColumnName(int c){ return cols[c]; }
            @Override public Object getValueAt(int r,int c){ var rr = rows.get(r); return switch(c){ case 0 -> rr.prod().id(); case 1 -> rr.prod().getNombre(); default -> rr.cant(); }; }
        }
        private record CompRow(Producto prod,int cant){}
    }

    // ===== Dialogo para crear Ingrediente =====
    private class CreateIngredienteDialog extends JDialog {
        private JTextField txtId, txtNombre, txtCosto;
        private JSpinner spnStock, spnMin;
        private JComboBox<UnidadMedida> cbUnidad;
        private JButton btnSave;
        private JComboBox<modelo.Proveedor> cbProv; // nuevo

        CreateIngredienteDialog(){
            super(SwingUtilities.getWindowAncestor(VentasPanel.this), "Crear Ingrediente", ModalityType.APPLICATION_MODAL);
            init(); pack(); setLocationRelativeTo(VentasPanel.this);
        }

        private void init(){
            setLayout(new GridLayout(0,2,6,6));
            txtId = new JTextField(); txtNombre = new JTextField(); txtCosto = new JTextField();
            spnStock = new JSpinner(new SpinnerNumberModel(0,0,999999,1));
            spnMin = new JSpinner(new SpinnerNumberModel(0,0,999999,1));
            cbUnidad = new JComboBox<>(UnidadMedida.values());
            cbProv = new JComboBox<>(); // inicializar combo de proveedores
            // mostrar id y nombre en la lista (por defecto muestra toString() del objeto)
            cbProv.setRenderer(new DefaultListCellRenderer(){
                @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus){
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    if (value == null) {
                        setText("(sin proveedor)");
                    } else if (value instanceof modelo.Proveedor p) {
                        setText(p.id() + " - " + p.getNombre());
                    }
                    return this;
                }
            });
            add(new JLabel("ID:")); add(txtId);
            add(new JLabel("Nombre:")); add(txtNombre);
            add(new JLabel("Stock Actual:")); add(spnStock);
            add(new JLabel("Stock Minimo:")); add(spnMin);
            add(new JLabel("Unidad:")); add(cbUnidad);
            add(new JLabel("Costo por unidad:")); add(txtCosto);
            add(new JLabel("Proveedor:")); add(cbProv); // nuevo
            btnSave = new JButton("Guardar"); add(new JLabel()); add(btnSave);

            // cargar proveedores en el combo
            var provs = repoProveedores.listar();
            if (provs.isEmpty()) {
                // deja el combo con la opción nula para que el renderer muestre '(sin proveedor)'
                cbProv.addItem(null);
            } else {
                for (modelo.Proveedor prov : provs) cbProv.addItem(prov);
            }

            btnSave.addActionListener(e -> {
                try {
                    String id = txtId.getText().trim();
                    String nombre = txtNombre.getText().trim();
                    int stock = (Integer) spnStock.getValue();
                    int min = (Integer) spnMin.getValue();
                    UnidadMedida um = (UnidadMedida) cbUnidad.getSelectedItem();
                    BigDecimal costo = new BigDecimal(txtCosto.getText().trim());
                    if (id.isBlank() || nombre.isBlank()) { JOptionPane.showMessageDialog(this, "ID y Nombre requeridos"); return; }
                    Ingrediente ing = new Ingrediente(id, nombre, stock, min, um, costo);
                    repoProductos.guardar(ing);
                    // asociar con proveedor seleccionado
                    if (cbProv.getSelectedItem() instanceof modelo.Proveedor prov){
                        provCfg.asociar(id, prov.id());
                    }
                    JOptionPane.showMessageDialog(this, "Ingrediente creado");
                    cargarProductos();
                    dispose();
                } catch (Exception ex){ JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE); }
            });
        }
    }
}
