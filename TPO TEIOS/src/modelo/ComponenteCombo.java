package modelo;

import java.util.Objects;

/**
 * Componente de un combo: referencia a un producto y la cantidad requerida.
 * Clase inmutable que guarda un producto y cuántas unidades de ese producto forman parte del combo.
 */

public final class ComponenteCombo {

    // Producto incluido en el componente del combo (no puede ser null)
    private final Producto producto;

    // Cantidad de unidades de ese producto (debe ser > 0)
    private final int cantidad;

    /**
     * Constructor: valida y asigna los campos.
     * - producto: no puede ser null (Objects.requireNonNull).
     * - cantidad: debe ser mayor que cero, lanza IllegalArgumentException si no.
     */
    public ComponenteCombo(Producto producto, int cantidad) {
        this.producto = Objects.requireNonNull(producto);
        if (cantidad <= 0) throw new IllegalArgumentException("cantidad");
        this.cantidad = cantidad;
    }

    // Getter: devuelve el producto asociado al componente
    public Producto producto() { return producto; }

    // Getter: devuelve la cantidad de unidades de dicho producto
    public int cantidad() { return cantidad; }
}
