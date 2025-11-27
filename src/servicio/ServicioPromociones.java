package servicio;

import dao.IRepositorioPromocion;        // Interfaz del repositorio encargado de persistir/buscar promociones
import modelo.Producto;                  // Modelo de producto para el que se aplican promociones
import modelo.Promocion;                // Modelo de promoción (descuentos, fechas de vigencia, etc.)

import java.math.BigDecimal;            // Tipo numérico para valores monetarios (precio, descuentos), más seguro que double
import java.time.LocalDate;             // Representa una fecha (sin hora) para evaluar vigencias
import java.util.List;                  // Lista de promociones que se devuelven en consultas
import java.util.Objects;               // Utilidad para validaciones de null

/**
 * Servicio de dominio encargado de la lógica relacionada con las promociones.
 *
 * No conoce detalles de persistencia: delega en un IRepositorioPromocion para guardar y consultar.
 * Se ocupa de:
 *  - Agregar nuevas promociones.
 *  - Listar todas las promociones.
 *  - Consultar qué promociones están vigentes para un producto en una fecha.
 *  - Calcular el mejor precio aplicando las promociones vigentes a un precio base.
 */
public class ServicioPromociones implements IServicioPromociones {

    // Dependencia al repositorio de promociones. Se inyecta por constructor (inyección de dependencias).
    private final IRepositorioPromocion repoPromocion;

    /**
     * Constructor que recibe la implementación concreta del repositorio de promociones.
     *
     * Se guarda en un campo final para no poder cambiarlo luego y se valida que no sea null.
     */
    public ServicioPromociones(IRepositorioPromocion repoPromocion) {
        // requireNonNull lanza NullPointerException con mensaje si se pasa null.
        this.repoPromocion = Objects.requireNonNull(repoPromocion);
    }

    /**
     * Agrega una nueva promoción al sistema.
     * Este metodo forma parte del contrato del servicio. La lógica de cómo se guarda
     * (base de datos, archivo, memoria, etc.) queda a cargo del repositorio inyectado.
     */
    @Override
    public void agregarPromocion(Promocion promocion) {
        // Delegamos la persistencia de la promoción al repositorio.
        repoPromocion.guardar(promocion);
    }

    /**
     * Devuelve la lista completa de promociones registradas.
     * No aplica filtros de vigencia ni de producto; sólo reenvía la llamada al repositorio.
     */
    @Override
    public List<Promocion> listarPromociones() {
        // Pide al repositorio todas las promociones almacenadas.
        return repoPromocion.listarTodas();
    }

    /**
     * Devuelve las promociones que están vigentes para un producto en una fecha dada.
     *
     * - Usa el id del producto para buscar en el repositorio (evita acoplarse a más detalles de Producto).
     * - La lógica concreta de filtrado por fechas/producto está en el repositorio.
     */
    @Override
    public List<Promocion> promocionesVigentesPara(Producto producto, LocalDate fecha) {
        // Se delega en el repositorio, pasando el id del producto y la fecha de consulta.
        return repoPromocion.buscarVigentesParaProducto(producto.id(), fecha);
    }

    /**
     * Calcula el mejor precio posible para un producto en una fecha, aplicando sus promociones vigentes.
     * - Recibe el producto, el precio base (antes de descuentos) y la fecha de compra.
     * - Consulta qué promociones están vigentes ese día para ese producto.
     * - Aplica cada promoción al precio base y va quedándose con el menor precio (mejor descuento).
     * - Si no hay promociones o ninguna mejora el precio, devuelve el precio base original.
     */
    @Override
    public BigDecimal calcularPrecioConPromocion(Producto producto,
                                                 BigDecimal precioBase,
                                                 LocalDate fecha) {
        // Obtiene del repositorio todas las promociones vigentes para ese producto y fecha.
        var promos = promocionesVigentesPara(producto, fecha);
        // Variable que mantiene el "mejor" precio encontrado hasta el momento.
        // Se inicializa con el precio base (caso en el que no haya promociones o no mejoren el precio).
        BigDecimal mejor = precioBase;

        // Recorre una por una las promociones vigentes.
        for (Promocion promo : promos) {
            // Le pide a la promoción que calcule el precio con su descuento aplicado al precio base.
            BigDecimal conDesc = promo.aplicarADescuento(precioBase);
            // compareTo devuelve < 0 si conDesc es menor que mejor.
            if (conDesc.compareTo(mejor) < 0) {
                // Si esta promoción deja un precio más bajo, se actualiza "mejor".
                mejor = conDesc;
            }
        }
        // Al final del recorrido, "mejor" tiene el precio más bajo conseguido con las promociones vigentes.
        return mejor;
    }
}
