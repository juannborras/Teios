package servicio;

import excepciones.EntidadNoEncontradaException;
import excepciones.StockInsuficienteException;
import modelo.*;

import java.util.List;

public interface IServicioInventario {

    //metodos comunes para la gestion de productos y ventas
    void altaProducto(Producto p);
    void bajaProducto(String id);
    void modificarProducto(Producto p);

    List<Producto> listarProductos();

    void registrarVenta(List<ItemVenta> items)
            throws EntidadNoEncontradaException, StockInsuficienteException;

    void reponerSiCorresponde(); // placeholder para lógica de reposición automática
    void reponerSiCorresponde(Producto p); // reposición automática para un producto específico

}
