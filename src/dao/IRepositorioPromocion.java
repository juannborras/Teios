package dao;

import modelo.Promocion;

import java.util.List;

public interface IRepositorioPromocion {

    void guardar(Promocion promocion);
    List<Promocion> listarTodas();
    List<Promocion> buscarVigentesParaProducto(String productoId, java.time.LocalDate fecha);
    Promocion buscarPorId(String Id);

}
