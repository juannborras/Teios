package modelo;

import java.util.Objects;

/**
 * Representa un proveedor inmutable identificado por un id.
 * Características principales:
  - Inmutable: campos `final` y sin setters.
  - Valida que los parámetros no sean nulos en el constructor.
  - Implementa IIdentificable\<String\> para exponer el identificador.
 */

public final class Proveedor implements IIdentificable<String> {

    //identificador unico del proveedor, no puede ser null
    private final String id;

    //Nombre del proveedor, no puede ser null
    private final String nombre;

    //Información de contacto del proveedor, no puede ser null
    private final String contacto;

    /**
     * Constructor público.
      - Valida que `id`, `nombre` y `contacto` no sean null usando Objects.requireNonNull.
      - Asigna los valores a campos finales para garantizar inmutabilidad.

     * se valida en el constructor que los Strings no esten vacios.
     */
    public Proveedor(String id, String nombre, String contacto) {
        this.id = Objects.requireNonNull(id, "id");
        if (this.id.isBlank()) throw new IllegalArgumentException("id no puede estar vacío");

        this.nombre = Objects.requireNonNull(nombre, "nombre");
        if (this.nombre.isBlank()) throw new IllegalArgumentException("nombre no puede estar vacío");

        this.contacto = Objects.requireNonNull(contacto, "contacto");
        if (this.contacto.isBlank()) throw new IllegalArgumentException("contacto no puede estar vacío");
    }

    /**
     * Accesor requerido por la interfaz IIdentificable.
     * Devuelve el identificador del proveedor.
     */
    @Override
    public String id() {
        return id;
    }

   //devuelve el nombre del proveedor
    public String getNombre() {
        return nombre;
    }

    //devuelve la info de contacto del proveedor
    public String getContacto() {
        return contacto;
    }
}
