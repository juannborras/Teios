package modelo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Producto compuesto por otros productos.
 * El precio se calcula sumando el precio de cada componente multiplicado por su cantidad.
 *
 * Observación: un Combo no mantiene stock propio; su disponibilidad depende del stock de sus componentes.
 */
public final class Combo extends Producto {

    // Lista mutable interna de componentes del combo.
    private final List<ComponenteCombo> componentes = new ArrayList<>();

    /**
     * Nuevo constructor: los Combos no tienen stock propio, por eso el stock
     * se inicializa a 0 y no expone campos de stock al crear/guardar.
     *
     * @param id identificador del combo
     * @param nombre nombre legible del combo
     */
    public Combo(String id, String nombre) {
        super(id, nombre, 0, 0);
    }

    /**
     * Agrega un componente al combo.
     */
    public void agregarComponente(Producto producto, int cantidad) {
        componentes.add(new ComponenteCombo(producto, cantidad));
    }

    /**
     * Devuelve una vista inmutable de los componentes del combo.
     */
    public List<ComponenteCombo> componentes() {
        return List.copyOf(componentes);
    }

    @Override
    public BigDecimal precio() {
        return componentes.stream()
                .map(c -> c.producto().precio().multiply(BigDecimal.valueOf(c.cantidad())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public void descontarStock(int cantidad) throws excepciones.StockInsuficienteException {
        // prevalidación: comprobar que cada componente tiene stock suficiente
        for (var c : componentes) {
            if (c.producto().getStockActual() < c.cantidad() * cantidad)
                throw new excepciones.StockInsuficienteException(
                        c.producto().id(), c.cantidad() * cantidad, c.producto().getStockActual());
        }
        // aplicar: descontar efectivamente el stock de cada componente
        for (var c : componentes) c.producto().descontarStock(c.cantidad() * cantidad);
    }
}
