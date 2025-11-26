package dao;

import java.util.List;
import java.util.Optional;

/**
 * Interfaz genérica para repositorios que exponen operaciones CRUD básicas.
 * <p> Separa la abstracción del almacenamiento de la entidad del resto de la
   aplicación. Implementaciones concretas pueden usar memoria, base de datos,
   ficheros, etc.</p>

 * @param <T>  tipo de entidad gestionada
 * @param <ID> tipo del identificador de la entidad
 */
public interface IRepositorio<T, ID> {

    /**
     * Devuelve todas las entidades almacenadas.
     * <p> El contrato no especifica si la lista es mutable ni si es una vista en
       tiempo real del repositorio; la implementación debe documentar ese detalle.

     * @return lista con las entidades (posiblemente vacía)
     */
    List<T> listar();

    /**
     * Busca una entidad por su identificador.
     * @param id identificador de la entidad a buscar
     * @return {@link Optional} que contiene la entidad si existe, o vacío si no
     */
    Optional<T> buscar(ID id);

    /**
     * Inserta o actualiza una entidad en el repositorio (upsert).
     * <p> El comportamiento exacto para claves nulas o duplicadas depende de la
     * implementación.
     * @param entidad entidad a guardar o actualizar
     */
    void guardar(T entidad);   // upsert

    /**
     * Elimina la entidad asociada al identificador.
     * <p>Si la entidad no existe, el comportamiento (no-op, excepción, retorno
     * booleano) depende de la implementación;
     * @param id identificador de la entidad a eliminar
     */
    void eliminar(ID id);
}
