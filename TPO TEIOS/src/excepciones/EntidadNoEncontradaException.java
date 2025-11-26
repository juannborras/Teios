package excepciones;


/**
 * Excepción verificada que representa el caso en que no se encuentra una entidad.
 * Al extender `Exception` es una checked exception: quien la lance debe declararla
 * con `throws` o capturarla.
 */
public class EntidadNoEncontradaException extends Exception {

    /**
     * Constructor principal.
     * @param msg mensaje descriptivo sobre qué entidad no se encontró
     */
    public EntidadNoEncontradaException(String msg) { super(msg); }
}
