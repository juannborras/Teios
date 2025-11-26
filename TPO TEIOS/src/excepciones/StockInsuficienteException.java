package excepciones;

// Excepción propia lanzada cuando no hay suficiente stock para completar una operación
public class StockInsuficienteException extends Exception {

    public StockInsuficienteException(String productoId, int requerido, int disponible) {
        super("Stock insuficiente (" + productoId + ") requerido: " + requerido + " disponible: " + disponible);
    }
}
