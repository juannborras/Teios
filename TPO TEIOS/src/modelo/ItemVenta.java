package modelo;

/**
 * DTO inmutable que representa una solicitud de venta de un producto.
 * Componentes:
 * - productoId: identificador del producto (no nulo, no vacío).
 * - cantidad: unidades solicitadas (debe ser > 0).

 * La estructura `record` genera automáticamente:
 * - un constructor canonical,
 * - métodos de acceso `productoId()` y `cantidad()`,
 * - `equals`, `hashCode` y `toString`.

 * Aquí se utiliza un constructor compacto para validar los argumentos.
 */
public record ItemVenta(String productoId, int cantidad) {

    // Constructor compacto: se ejecuta después de la asignación de componentes
    // y permite validar los valores recibidos.
    public ItemVenta {
        // Validación: productoId no puede ser null ni vacío/whitespace
        if (productoId == null || productoId.isBlank()) throw new IllegalArgumentException("productoId");
        // Validación: cantidad debe ser positiva
        if (cantidad <= 0) throw new IllegalArgumentException("cantidad");
    }

    // Observación: no hace falta declarar getters; puedes llamar a
    // item.productoId() e item.cantidad() para obtener los valores.
}
