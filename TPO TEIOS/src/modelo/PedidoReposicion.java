package modelo;

import java.time.LocalDate;
import java.util.*;

/**
 * Representa un pedido de reposición de stock asociado a un `Proveedor`.
 * Diseño y comportamiento clave:
  - `id`: identificador inmutable del pedido.
  - `proveedor`: referencia al proveedor responsable.
  - `items`: mapa ordenado (inserción) de productos a cantidades; la estructura interna es mutable,
     pero se expone de forma inmutable usando `Map.copyOf`.
  - `fechaCreacion`: fecha en que se creó el pedido.
  - `estado`: estado actual del pedido (PENDIENTE, ENVIADO, RECIBIDO); es mutable y se modifica mediante métodos.
 */

public final class PedidoReposicion implements IIdentificable<String> {

    // Identificador único del pedido. Se asigna en el constructor y no cambia.
    private final String id;

    // Proveedor asociado al pedido. Inmutable desde la referencia (el objeto proveedor puede ser inmutable).
    private final Proveedor proveedor;

    /**
     * Mapa de productos a cantidades solicitadas.
     * Usamos LinkedHashMap para preservar el orden de inserción (útil para presentación).
     * Es mutable internamente para permitir agregar/quitar productos antes de enviar/recibir.
     */
     private final Map<Producto, Integer> items = new LinkedHashMap<>();

    // Fecha en que se creó el pedido. Se establece una vez en la creación.
    private final LocalDate fechaCreacion;

    //fecha en la que se envio el pedido
    private LocalDate fechaEnvio;

    //fecha en la que se recibio el pedido
    private LocalDate fechaRecepcion;

    // Estado actual del pedido. Cambia mediante los métodos de la clase.
    private EstadoPedido estado;

    /**
     * Constructor principal.
      - Valida que `id` y `proveedor` no sean nulos.
      - Inicializa la fecha de creación al día actual.
      - Establece el estado inicial en PENDIENTE.
     */
    public PedidoReposicion(String id, Proveedor proveedor) {
        this.id = Objects.requireNonNull(id);
        this.proveedor = Objects.requireNonNull(proveedor);
        this.fechaCreacion = LocalDate.now();
        this.estado = EstadoPedido.PENDIENTE;
    }

    /**
     * Implementación de IIdentificable\<String\>.
     * Devuelve el identificador del pedido.
     */
    @Override public String id() { return id; }

    //devuelve el proveedor asociado al pedido
    public Proveedor getProveedor() { return proveedor; }

    //devuelve la fecha de creación del pedido
    public LocalDate getFechaCreacion() { return fechaCreacion; }

    //develve la fecha de envio del pedido
    public LocalDate getFechaEnvio() { return fechaEnvio; }

    //devuelve la fecha de recepcion del pedido
    public  LocalDate getFechaRecepcion() { return fechaRecepcion; }

    //devuelve el estado actual del pedido
    public EstadoPedido getEstado() { return estado; }

    /**
     * Expone una vista inmutable de los items.
      - Internamente `items` es mutable, pero aquí devolvemos `Map.copyOf(items)` para proteger el estado.
      - Esto evita que callers modifiquen la colección interna directamente.
     */

    public Map<Producto,Integer> getItems() { return Map.copyOf(items); }

    /**
     * Agrega una cantidad del producto al pedido.
      - Valida cantidad > 0.
      - Usa `merge` para sumar cantidades si el producto ya existía.
      - Lanza IllegalArgumentException en caso de cantidad no válida.
     */
    public void agregarProducto(Producto producto, int cantidad) {
        if (cantidad <= 0) throw new IllegalArgumentException("cantidad");
        items.merge(Objects.requireNonNull(producto), cantidad, Integer::sum);
    }

    /**
     * Quita completamente el producto del pedido.
      - Si el producto no existe, `remove` no hace nada.
     */
    public void quitarProducto(Producto producto) { items.remove(producto); }

    /**
     * Marca el pedido como enviado.
      - Cambia el estado a ENVIADO.
     */
    public void marcarEnviado() {
        if (this.estado != EstadoPedido.PENDIENTE)
            throw new IllegalStateException("Solo se puede marcar como enviado desde estado: PENDIENTE.");
        if (this.items.isEmpty())
            throw new IllegalStateException("No se puede enviar un pedido sin productos.");
        this.estado = EstadoPedido.ENVIADO;
        this.fechaEnvio = LocalDate.now();
    }

    /**
     * Marca el pedido como recibido.
      - Cambia el estado a RECIBIDO..
     */
    public void marcarRecibido() {
        if (this.estado != EstadoPedido.ENVIADO)
            throw new IllegalStateException("Solo se puede marcar como RECIBIDO desde el estado ENVIADO");
        this.estado = EstadoPedido.RECIBIDO;
        this.fechaRecepcion = LocalDate.now();
    }

    // metodo para reconstruir un pedido de reposicion desde datos externos (ej: CSV)
    public static PedidoReposicion reconstruir(String id, Proveedor proveedor, LocalDate fecha, EstadoPedido estado) {
        PedidoReposicion pedido = new PedidoReposicion(id, proveedor);
        pedido.estado = estado;
        // no cargamos items acá; el ServicioPedidosCSV lo hará después
        return pedido;
    }


}
