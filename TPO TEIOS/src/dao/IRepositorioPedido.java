package dao;
import modelo.*;

import java.util.Map;

// Interfaz que define el repositorio de pedidos de reposición
// mismo funcionamiento que IRepositorioProducto pero para pedidos de reposición

public interface IRepositorioPedido extends IRepositorio<PedidoReposicion,String> {

    // devuelve los items crudos (productoId -> cantidad) para un pedido
    Map<String, Integer> obtenerItems(String pedidoId);

    void guardarCabecera(PedidoReposicion pedido);

    void agregarItem(String pedidoId, String productoId, int cantidad);

}
