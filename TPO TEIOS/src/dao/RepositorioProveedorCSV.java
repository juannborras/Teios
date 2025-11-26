// java
package dao;

import modelo.Proveedor;
import util.CsvUtils;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Repositorio de `Proveedor` persistido en un único fichero CSV.
 * <p>Formato del CSV (cabecera):
 * {@code id;nombre;contacto}</p>

 * Contrato y comportamiento importante:
  - La persistencia es basada en fichero plano;
    las operaciones leen/escriben todo el fichero (no hay base de datos ni índices).
  - El parser/escapado de campos se delega a {@code util.CsvUtils}:
    - {@code CsvUtils.split(line)} debe devolver los campos ya separados.
    - {@code CsvUtils.esc(value)} y {@code CsvUtils.unesc(field)} deben encargarse de escapar/desescapar separadores y comillas.
  - Las IOExceptions se envuelven en {@link UncheckedIOException} para simplificar el manejo en capas superiores.
   */
public class RepositorioProveedorCSV implements IRepositorioProveedor {
    // Ruta al fichero CSV que actúa como almacenamiento persistente.
    private final Path ruta;

    /**
     * Crea el repositorio asociado a la ruta indicada.
     * @param ruta ruta al fichero CSV (puede no existir, se creará)
     */
    public RepositorioProveedorCSV(Path ruta) {
        this.ruta = ruta;
        init(); // asegurar existencia del fichero y cabecera
    }

    /**
     * Inicializa el fichero:
       - crea directorio padre si hace falta y escribe la cabecera si el fichero no existía.
     * <p>Convierte cualquier {@code IOException} en {@code UncheckedIOException}.</p>
     */
    private void init() {
        try {
            // Asegurar que el directorio padre existe (createDirectories ignora si ya existe)
            Files.createDirectories(ruta.getParent());
            // Si el fichero no existe, crearlo y escribir la cabecera estándar
            if (!Files.exists(ruta)) try (var bw = Files.newBufferedWriter(ruta)) {
                bw.write("id;nombre;contacto\n");
            }
        } catch (IOException e) {
            // Convertir a unchecked para no forzar manejo en llamadores
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Lee todos los proveedores del CSV y los devuelve como lista.
     * Comportamiento:
      - Se descarta la primera línea (cabecera).
      - Se usa {@code CsvUtils.split} para obtener campos; se llama a
        {@code CsvUtils.unesc} para deshacer escapes antes de construir
        la instancia de {@code Proveedor}.
      - Si hay problemas de E/S se lanza {@code UncheckedIOException}.
     * @return lista de proveedores (vacía si no hay registros)
     */
    @Override
    public List<Proveedor> listar() {
        List<Proveedor> out = new ArrayList<>();
        try (var br = Files.newBufferedReader(ruta)) {
            // Descarta la cabecera
            br.readLine();
            for (String line; (line = br.readLine()) != null; ) {
                if (line.isBlank()) continue; // ignorar líneas vacías
                var c = CsvUtils.split(line);
                // Suponer al menos 3 columnas; si el CSV está corrupto puede lanzar
                // IndexOutOfBoundsException — aquí no se captura para que el fallo
                // sea visible durante desarrollo.
                out.add(new Proveedor(
                        CsvUtils.unesc(c.get(0)), // id
                        CsvUtils.unesc(c.get(1)), // nombre
                        CsvUtils.unesc(c.get(2))  // contacto
                ));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    /**
     * Busca un proveedor por su id.
     * Implementación sencilla: delega en {@link #listar()} y filtra por id.
     * Esto recorre todo el fichero cada vez.
     * @param id identificador del proveedor
     * @return Optional con el proveedor encontrado o vacío si no existe
     */
    @Override
    public Optional<Proveedor> buscar(String id) {
        return listar().stream().filter(p -> p.id().equals(id)).findFirst();
    }

    /**
     * Guarda (inserta o actualiza) un proveedor.
     * Algoritmo:
      - Lee todos los proveedores.
      - Elimina cualquier registro con el mismo id que la entidad recibida.
      - Añade la entidad actualizada.
      - Sobrescribe el fichero CSV completo con la nueva lista.

     * Notas:
      - Operación no atómica: si falla la escritura puede dejar el fichero
        en estado inconsistente (mejor envolver con un mecanismo de reemplazo
        atómico si es crítico).
      - Se asume que {@code entidad.id()}, {@code entidad.getNombre()} y
        {@code entidad.getContacto()} no son null; en caso contrario el CSV
        contendrá valores vacíos o "null".

     * @param entidad proveedor a guardar
     */
    @Override
    public void guardar(Proveedor entidad) {
        List<Proveedor> todos = listar();
        // Reemplazar cualquier proveedor con el mismo id
        todos.removeIf(p -> p.id().equals(entidad.id()));
        todos.add(entidad);
        try (var bw = Files.newBufferedWriter(ruta)) {
            // Escribir cabecera fija
            bw.write("id;nombre;contacto\n");
            // Escribir cada proveedor escapando campos problemáticos
            for (var p : todos) {
                bw.write(String.join(";", CsvUtils.esc(p.id()), CsvUtils.esc(p.getNombre()), CsvUtils.esc(p.getContacto())));
                bw.newLine();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Elimina un proveedor por id.
      - Si se elimina al menos un elemento, delega en {@link #guardarTodos(List)}
        para reescribir el fichero sin el elemento eliminado.
     * @param id identificador del proveedor a eliminar
     */
    @Override
    public void eliminar(String id) {
        List<Proveedor> todos = listar();
        if (todos.removeIf(p -> p.id().equals(id))) guardarTodos(todos);
    }

    /**
     * Reescribe el fichero CSV con la lista completa recibida.
     * metodo auxiliar para centralizar la lógica de escritura utilizada por
       {@link #eliminar(String)} y potencialmente por otros puntos.
     * @param todos lista completa de proveedores a persistir
     */
    private void guardarTodos(List<Proveedor> todos) {
        try (var bw = Files.newBufferedWriter(ruta)) {
            bw.write("id;nombre;contacto\n");
            for (var p : todos) {
                // Usar ';' explícito para mantener consistencia en el fichero
                bw.write(String.join(";", CsvUtils.esc(p.id()), CsvUtils.esc(p.getNombre()), CsvUtils.esc(p.getContacto())));
                bw.newLine();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
