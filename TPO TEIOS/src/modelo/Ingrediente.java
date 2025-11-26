package modelo;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Producto simple de un único componente.
 * Clase final que extiende Producto.
 */
public final class Ingrediente extends Producto {

    // Unidad de medida del ingrediente (por ejemplo KILO, LITRO)
    private final UnidadMedida unidad;

    // Costo por cada unidad de medida
    private final BigDecimal costoPorUnidad;

    // Constructor: valida argumentos, delega campos comunes a la superclase
    public Ingrediente(String id, String nombre, int stockActual, int stockMinimo,
                       UnidadMedida unidad, BigDecimal costoPorUnidad) {
        super(id, nombre, stockActual, stockMinimo);
        this.unidad = Objects.requireNonNull(unidad);
        this.costoPorUnidad = Objects.requireNonNull(costoPorUnidad);
        // Valida que el costo no sea negativo
        if (costoPorUnidad.signum() < 0) throw new IllegalArgumentException("costoPorUnidad");
    }

    // Getter: devuelve la unidad de medida
    public UnidadMedida getUnidad() { return unidad; }

    // Getter: devuelve el costo por unidad
    public BigDecimal getCostoPorUnidad() { return costoPorUnidad; }

    // Implementación del precio: en este caso sencillo devuelve el costo por unidad
    @Override public BigDecimal precio() { return costoPorUnidad; }

}
