package modelo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Representa una venta inmutable:
  - Identificador único (`id`).
  - Fecha/hora de la venta (`fecha`).
  - Lista de líneas de venta (`lineas`) que describen productos, cantidades y precios.

 * La clase es final e inmutable desde la API pública: los campos son `final`
   y la lista de líneas se expone como copia inmutable.
 */
public final class Venta implements IIdentificable<String> {

    // Identificador de la venta (no nulo).
    private final String id;

    // Marca temporal de la venta (no nulo).
    private final LocalDateTime fecha;

    // Lista inmutable de líneas de venta. Se crea con `List.copyOf` para protegerla.
    private final List<LineaVenta> lineas;

    /**
     * Constructor privado que realiza validaciones básicas y defensivas.
      - `id` y `fecha` deben ser no nulos.
      - `lineas` no puede ser nula ni vacía (una venta sin líneas no tiene sentido).
      - La lista se copia inmutablemente para mantener la inmutabilidad del objeto.
     */
    private Venta(String id, LocalDateTime fecha, List<LineaVenta> lineas) {
        this.id = Objects.requireNonNull(id);
        this.fecha = Objects.requireNonNull(fecha);
        if (lineas == null || lineas.isEmpty()) throw new IllegalArgumentException("Venta sin líneas");
        this.lineas = List.copyOf(lineas);
    }

     //Accesor del identificador (cumple la interfaz `IIdentificable`).
    @Override public String id() { return id; }

    //devuelve la fecha/hora de la venta
    public LocalDateTime getFecha() { return fecha; }

    /**
     * Devuelve la lista inmutable de líneas de venta.
      - Retorna la referencia inmutable creada en el constructor.
     */
    public List<LineaVenta> getLineas() { return lineas; }

    /**
     * Calcula el total de la venta sumando los subtotales de cada línea.
      - Usa `reduce` con `BigDecimal.ZERO` como identidad para evitar nulos.
     */
    public BigDecimal getTotal() {
        return lineas.stream().map(LineaVenta::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Fábrica estática solicitada por el diagrama.
      - `items`: lista de `ItemVenta` (id de producto + cantidad) que definen la venta.
      - `finder`: función que, dado un `productoId`, devuelve el `Producto` correspondiente
         o `null` si no existe.

     * Flujo:
      1. Valida que `items` no sea null.
      2. Para cada `ItemVenta` obtiene el `Producto` usando `finder`.
         - Si `finder` devuelve `null` se lanza `NullPointerException` con mensaje indicando el producto inexistente.
      3. Crea una `LineaVenta` por cada `ItemVenta` usando el precio actual del producto (`p.precio()`).
      4. Genera un id simple de venta (`V-` + timestamp) y la fecha actual.
      5. Devuelve la nueva instancia de `Venta`.

     * Observaciones:
      - La fábrica asume que el `precio()` del producto es el precio unitario a aplicar.
      - El `finder` permite desacoplar la construcción de la venta del repositorio/almacenamiento.
     */
    public static Venta desde(List<ItemVenta> items, java.util.function.Function<String, Producto> finder) {
        Objects.requireNonNull(items);
        List<LineaVenta> lineas = new ArrayList<>();
        for (var it : items) {
            Producto p = Objects.requireNonNull(finder.apply(it.productoId()),
                    () -> "Producto inexistente: " + it.productoId());
            lineas.add(new LineaVenta(p, it.cantidad(), p.precio()));
        }
        String id = "V-" + System.currentTimeMillis();
        return new Venta(id, LocalDateTime.now(), lineas);
    }

    // metodo publico para reconstruir una venta a partir de sus datos completos
    public static Venta reconstruir(String id, LocalDateTime fecha, List<LineaVenta> lineas) {
        return new Venta(id, fecha, lineas);
    }


}
