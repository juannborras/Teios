package servicio;

import modelo.PedidoReposicion;

import java.time.LocalDate;
import java.util.List;

public interface IServicioPedidos {

    PedidoReposicion crearPedido(String proveedorId);

    void agregarItemPedido(String pedidoId, String productoId, int cantidad);
    void marcarPedidoEnviado(String pedidoId);
    void marcarPedidoRecibido(String pedidoId);
    void enviarPedido(String pedidoId);
    void recibirPedido(String pedidoId, LocalDate fechaR);


    List<PedidoReposicion> listarPedidos();
}
