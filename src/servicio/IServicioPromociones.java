package servicio;

import modelo.Producto;
import modelo.Promocion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface IServicioPromociones {

    void agregarPromocion(Promocion promocion);

    List<Promocion> listarPromociones();

    /**
     * Devuelve las promociones vigentes para un producto en una fecha dada.
     */
    List<Promocion> promocionesVigentesPara(Producto producto, LocalDate fecha);

    /**
     * Calcula el precio final aplicando la mejor promoción disponible
     * (o el mismo precio si no hay ninguna promo que aplique).
     */
    BigDecimal calcularPrecioConPromocion(Producto producto,
                                          BigDecimal precioBase,
                                          LocalDate fecha);
}
