package modelo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

/**
 * Representa una promoción o descuento aplicable a uno o varios productos.
 * Por simplicidad, es siempre un porcentaje de descuento (0.00 a 1.00).
 */
public final class Promocion {

    private final String id;
    private final String nombre;
    private final BigDecimal porcentajeDescuento;   // ej: 0.10 = 10%
    private final LocalDate fechaInicio;
    private final LocalDate fechaFin;
    private final Set<String> idsProductosAplicados; // IDs de Producto a los que aplica

    public Promocion(String id,
                     String nombre,
                     BigDecimal porcentajeDescuento,
                     LocalDate fechaInicio,
                     LocalDate fechaFin,
                     Set<String> idsProductosAplicados) {

        this.id = Objects.requireNonNull(id, "id es obligatorio");
        this.nombre = Objects.requireNonNull(nombre, "nombre es obligatorio");
        this.porcentajeDescuento = Objects.requireNonNull(porcentajeDescuento, "porcentajeDescuento es obligatorio");
        this.fechaInicio = Objects.requireNonNull(fechaInicio, "fechaInicio es obligatoria");
        this.fechaFin = Objects.requireNonNull(fechaFin, "fechaFin es obligatoria");
        this.idsProductosAplicados = Set.copyOf(Objects.requireNonNull(idsProductosAplicados, "idsProductosAplicados"));

        if (porcentajeDescuento.compareTo(BigDecimal.ZERO) < 0
                || porcentajeDescuento.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("porcentajeDescuento debe estar entre 0 y 1");
        }
        if (fechaFin.isBefore(fechaInicio)) {
            throw new IllegalArgumentException("fechaFin no puede ser anterior a fechaInicio");
        }
    }

    // ===== Getters básicos =====

    public String id() { return id; }

    public String nombre() { return nombre; }

    public BigDecimal porcentajeDescuento() { return porcentajeDescuento; }

    public LocalDate fechaInicio() { return fechaInicio; }

    public LocalDate fechaFin() { return fechaFin; }

    public Set<String> idsProductosAplicados() { return idsProductosAplicados; }

    // ===== Lógica de negocio =====

    /** Indica si la promo está vigente en una fecha dada. */
    public boolean estaVigente(LocalDate fecha) {
        return ( !fecha.isBefore(fechaInicio) && !fecha.isAfter(fechaFin) );
    }

    /** Indica si esta promo aplica a un producto concreto. */
    public boolean aplicaA(Producto producto) {
        return idsProductosAplicados.contains(producto.id());
    }

    /**
     * Aplica el descuento al precio base y devuelve el precio final.
     * Si el descuento es 10%, precioFinal = precioBase * (1 - 0.10).
     */
    public BigDecimal aplicarADescuento(BigDecimal precioBase) {
        Objects.requireNonNull(precioBase, "precioBase");
        BigDecimal factor = BigDecimal.ONE.subtract(porcentajeDescuento);
        return precioBase.multiply(factor);
    }
}
