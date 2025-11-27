package modelo;
//clase abstracta que sirve como base. Polimorfismo sobre precio() y descuento de stock

import excepciones.StockInsuficienteException;
import java.util.Objects;

public abstract class Producto implements IIdentificable<String>, IPrecio {

    private final String id;
    private String nombre;
    private int stockActual;
    private int stockMinimo;

    protected Producto(String id, String nombre, int stockActual, int stockMinimo) {
        this.id = Objects.requireNonNull(id);
        this.nombre = Objects.requireNonNull(nombre);
        if (stockActual < 0 || stockMinimo < 0) throw new IllegalArgumentException("Stock negativo");
        this.stockActual = stockActual;
        this.stockMinimo = stockMinimo;
    }

    @Override public String id() { return id; }
    public String getNombre() { return nombre; }
    public void setNombre(String n) { this.nombre = Objects.requireNonNull(n); }
    public int getStockActual() { return stockActual; }
    public int getStockMinimo() { return stockMinimo; }
    public void setStockMinimo(int m) { if (m < 0) throw new IllegalArgumentException(); this.stockMinimo = m; }


    public void descontarStock(int cantidad) throws StockInsuficienteException {
        if (cantidad <= 0) throw new IllegalArgumentException("cantidad");
        if (stockActual < cantidad) throw new StockInsuficienteException(id, cantidad, stockActual);
        stockActual -= cantidad;
    }

    public void incrementarStock(int cantidad) {
        if (cantidad <= 0) throw new IllegalArgumentException("cantidad");
        stockActual += cantidad;
    }

    //Readecuacion para validar stock correctamente en los ingredientes de cada combo
    public boolean tieneStockSuficiente(int cantidad) {
        if (cantidad <= 0) throw new IllegalArgumentException("cantidad");
        return stockActual >= cantidad;
    }


}
