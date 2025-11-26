package dao;
import modelo.*;


/**
 * Repositorio tipado para la entidad Producto.
 *
 * <p>Actúa como un alias de {@code IRepositorio<Producto, String>}:
   hereda las operaciones CRUD definidas en la interfaz genérica y
   aporta intención semántica al resto del código (es más claro
   declarar dependencias sobre {@code IRepositorioProducto} que sobre
   una instancia genérica parametrizada).</p>

 * <p>Motivos para usar esta subinterfaz:
  - Documentación y lectura: deja explícito que el repositorio gestiona Productos.
  - Extensibilidad: permite añadir métodos específicos de Producto sin romper
    el contrato genérico.
  - Integración: facilita la inyección de dependencias y la búsqueda de
    implementaciones concretas por tipo.</p>

 * <p>Por ahora no declara métodos adicionales; las implementaciones deben
   cumplir el contrato de {@code IRepositorio} para {@code Producto}.</p>
 */

public interface IRepositorioProducto extends IRepositorio<Producto,String> {}
