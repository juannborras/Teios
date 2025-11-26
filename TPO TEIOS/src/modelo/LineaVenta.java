package modelo;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Representa una línea de venta: un producto concreto, la cantidad vendida
   y el precio unitario aplicado en la venta.

 * Es un objeto inmutable que actúa como valor: una vez construido no permite
   modificar sus campos. Proporciona un metodo para calcular el subtotal (precio unitario * cantidad).
 */

public class LineaVenta {

    // Producto asociado a esta línea (no puede ser null).
    private final Producto producto;

    // Cantidad de unidades vendidas en esta línea (debe ser > 0).
    private final int cantidad;

    // Precio por unidad aplicado en esta venta (no puede ser null).
    private final BigDecimal precioUnitario;

    /**
     * Constructor canonical: valida y asigna los campos.
      - producto: se valida con Objects.requireNonNull para evitar referencias nulas.
      - cantidad: debe ser mayor que cero; en caso contrario se lanza
        IllegalArgumentException con el nombre del parámetro.
      - precioUnitario: también se valida con Objects.requireNonNull.

     * Notas:
      - No se realizan comprobaciones sobre que precioUnitario sea negativo;
      - si se desea, añadir validación adicional (por ejemplo, precioUnitario.signum() >= 0).
      - La inmutabilidad se consigue declarando los campos como final y sin setters.
     */

    public LineaVenta(Producto producto, int cantidad, BigDecimal precioUnitario) {
        this.producto = Objects.requireNonNull(producto);
        if (cantidad <= 0) throw new IllegalArgumentException("cantidad");
        this.cantidad = cantidad;
        this.precioUnitario = Objects.requireNonNull(precioUnitario);
    }

    //Devuelve el producto asociado a esta línea de venta
    public Producto getProducto() { return producto; }

    //Devuelve la cantidad de unidades vendidas en esta línea
    public int getCantidad() { return cantidad; }


    //Devuelve el precio unitario aplicado en esta línea de venta
    public BigDecimal getPrecioUnitario() { return precioUnitario; }

    /**
     * Calcula y devuelve el subtotal de la línea:
     * precioUnitario * cantidad (como BigDecimal).
      - Se usa BigDecimal.valueOf(cantidad) para multiplicar sin pérdida de precisión.
      - No redondea el resultado;
     */

    public BigDecimal subtotal() { return precioUnitario.multiply(BigDecimal.valueOf(cantidad)); }

}
