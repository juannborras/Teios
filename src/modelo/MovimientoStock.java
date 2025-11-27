package modelo;

import java.time.LocalDateTime; // Representa fecha y hora sin zona horaria
import java.util.Objects;       // Utilidad para validaciones de null

/**
 * Representa un movimiento de stock de un Producto en el sistema.
 * Un movimiento puede ser de ENTRADA (aumenta stock) o de SALIDA (disminuye stock),
 * siempre asociado a un producto, una cantidad, una fecha y un motivo.
 *
 * La clase es inmutable: todos sus campos son final y se asignan sólo en el constructor.
 */
public final class MovimientoStock {

    /**
     * Tipo de movimiento de stock:
     *  - ENTRADA: el stock del producto aumenta (por ejemplo, por una reposición).
     *  - SALIDA: el stock del producto disminuye (por ejemplo, por una venta).
     */
    public enum Tipo {
        ENTRADA, SALIDA
    }

    // Identificador único (o al menos distintivo) del movimiento.
    private final String id;
    // Producto al que afecta este movimiento de stock.
    private final Producto producto;
    // Cantidad involucrada en el movimiento. Siempre positiva; el signo se deduce del tipo.
    private final int cantidad;      // siempre positiva
    // Tipo de movimiento: ENTRADA o SALIDA.
    private final Tipo tipo;
    // Fecha y hora exacta en la que se registra el movimiento.
    private final LocalDateTime fecha;
    // Motivo textual del movimiento, por ejemplo "VENTA" o "PEDIDO_RECIBIDO".
    private final String motivo;     // ej. "VENTA", "PEDIDO_RECIBIDO"

    /**
     * Constructor principal de la clase.
     * Recibe todos los datos necesarios para crear un movimiento de stock válido y
     * valida que no haya valores nulos y que la cantidad sea mayor que cero.
     */
    public MovimientoStock(String id,
                           Producto producto,
                           int cantidad,
                           Tipo tipo,
                           LocalDateTime fecha,
                           String motivo) {

        // Valida que el id no sea null y lo asigna; si es null lanza NullPointerException.
        this.id = Objects.requireNonNull(id, "id es obligatorio");
        // Valida que el producto no sea null y lo asigna.
        this.producto = Objects.requireNonNull(producto, "producto es obligatorio");
        // Verifica que la cantidad sea estrictamente mayor que 0.
        if (cantidad <= 0) throw new IllegalArgumentException("cantidad debe ser > 0");
        // Asigna la cantidad ya validada.
        this.cantidad = cantidad;
        // Valida que el tipo (ENTRADA o SALIDA) no sea null y lo asigna.
        this.tipo = Objects.requireNonNull(tipo, "tipo es obligatorio");
        // Valida que la fecha no sea null y la asigna.
        this.fecha = Objects.requireNonNull(fecha, "fecha es obligatoria");
        // Valida que el motivo no sea null y lo asigna.
        this.motivo = Objects.requireNonNull(motivo, "motivo es obligatorio");
    }

    // ==== Getters / consultas (métodos públicos para leer el estado del objeto) ====

    /**
     * Devuelve el identificador del movimiento.
     * Se usa nombre corto "id()" en vez del clásico "getId()" por estilo.
     */
    public String id() { return id; }

    /** Devuelve el producto asociado a este movimiento de stock. */
    public Producto getProducto() { return producto; }

    /** Devuelve la cantidad positiva involucrada en el movimiento. */
    public int getCantidad() { return cantidad; }

    /** Devuelve el tipo de movimiento: ENTRADA o SALIDA. */
    public Tipo getTipo() { return tipo; }

    /** Devuelve la fecha y hora en la que se registró el movimiento. */
    public LocalDateTime getFecha() { return fecha; }

    /** Devuelve el motivo textual del movimiento (por ejemplo "VENTA"). */
    public String getMotivo() { return motivo; }

    /**
     * Devuelve la cantidad con signo:
     *  - positiva si el movimiento es de ENTRADA (aumenta el stock),
     *  - negativa si el movimiento es de SALIDA (disminuye el stock).
     *
     * Es útil para calcular el stock final sumando todos los movimientos
     * de un producto.
     */
    public int cantidadFirmada() {
        // Si el tipo es ENTRADA, retornar la cantidad tal cual; si es SALIDA, retornarla negativa.
        return (tipo == Tipo.ENTRADA) ? cantidad : -cantidad;
    }

    /** Indica si el movimiento es una ENTRADA de stock. */
    public boolean esEntrada() {
        return tipo == Tipo.ENTRADA;
    }

    /** Indica si el movimiento es una SALIDA de stock. */
    public boolean esSalida() {
        return tipo == Tipo.SALIDA;
    }

    // ==== Métodos de fábrica estáticos (formas cómodas de crear movimientos comunes) ====

    /**
     * Crea un MovimientoStock de tipo SALIDA asociado a una venta.
     *
     * - Genera un id automáticamente con prefijo "MS-V-" y el tiempo actual en milisegundos.
     * - Usa el producto y la cantidad recibidos por parámetro.
     * - Fija el tipo en SALIDA (porque una venta saca stock del inventario).
     * - Usa la fecha y hora actual como fecha del movimiento.
     * - Fija el motivo en el texto literal "VENTA".
     */
    public static MovimientoStock porVenta(Producto p, int cantidad) {
        return new MovimientoStock(
                // id generado automáticamente para identificar este movimiento de venta
                "MS-V-" + System.currentTimeMillis(),
                // producto afectado por la venta
                p,
                // cantidad de unidades vendidas (debe ser > 0, se valida en el constructor)
                cantidad,
                // tipo SALIDA: disminuye el stock del producto
                Tipo.SALIDA,
                // fecha y hora actual en el momento de crear el movimiento
                LocalDateTime.now(),
                // motivo textual que identifica que este movimiento se debe a una venta
                "VENTA"
        );
    }

    /**
     * Crea un MovimientoStock de tipo ENTRADA asociado a una reposición de stock
     * (por ejemplo, recepción de un pedido a un proveedor).
     *
     * - Genera un id automáticamente con prefijo "MS-R-" y el tiempo actual en milisegundos.
     * - Usa el producto y la cantidad recibidos por parámetro.
     * - Fija el tipo en ENTRADA (porque entra stock al inventario).
     * - Usa la fecha y hora actual como fecha del movimiento.
     * - Fija el motivo en el texto literal "PEDIDO_RECIBIDO".
     */
    public static MovimientoStock porReposicion(Producto p, int cantidad) {
        return new MovimientoStock(
                // id generado automáticamente para identificar este movimiento de reposición
                "MS-R-" + System.currentTimeMillis(),
                // producto al que se le repone stock
                p,
                // cantidad de unidades que ingresan al inventario
                cantidad,
                // tipo ENTRADA: aumenta el stock del producto
                Tipo.ENTRADA,
                // fecha y hora actual en el momento de registrar la reposición
                LocalDateTime.now(),
                // motivo textual indicando que se recibió un pedido
                "PEDIDO_RECIBIDO"
        );
    }
}
