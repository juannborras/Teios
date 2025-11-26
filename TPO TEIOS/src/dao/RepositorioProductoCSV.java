// java
package dao;

import modelo.*;
import util.CsvUtils;

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;

/**
 * Repositorio de productos persistido en un fichero CSV.
 * <p> Implementa {@code IRepositorioProducto} usando un fichero en disco como
 * backend. El CSV usado tiene la cabecera:
 * {@code tipo;id;nombre;stockActual;stockMinimo;unidad;costoPorUnidad} para Ingrediente
 * y para Combo: {@code Combo;id;nombre;stockActual;stockMinimo;componentes} donde
 * componentes tiene la forma id1:qty|id2:qty ...</p>
 */
public class RepositorioProductoCSV implements IRepositorioProducto {

    // Ruta al fichero CSV que actúa como almacenamiento persistente.
    private final Path ruta;

    public RepositorioProductoCSV(Path ruta) {
        this.ruta = ruta;
        inicializar(); // asegurar existencia del fichero y cabecera
    }

    /**
     * Asegura que el directorio existe y que el fichero tiene la cabecera.

     * <p>Si el fichero no existía se crea y se escribe la cabecera por defecto.
     * Cualquier error de E/S se convierte en {@code UncheckedIOException}.</p>
     */
    private void inicializar() {
        try {
            Files.createDirectories(ruta.getParent());
            if (!Files.exists(ruta)) try (var bw = Files.newBufferedWriter(ruta)) {
                // Cabecera fija usada por listar/escribirTodos
                bw.write("tipo;id;nombre;stockActual;stockMinimo;unidad;costoPorUnidad;componentes\n");
            }
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    /**
     * Lee todas las filas del CSV y las convierte a objetos {@code Producto}.

     * <p>Por ahora solo crea instancias de {@code Ingrediente} cuando el campo
       tipo es "Ingrediente". Otras filas se ignoran (marcadas para futuro).
     * Se espera que {@code CsvUtils.split} devuelva una lista de campos
       ya separados por el separador {@code ;} y sin comillas.</p>

     * @return lista de productos (vacía si no hay líneas)
     */
    @Override public List<Producto> listar() {
        List<Producto> out = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();
        try (var br = Files.newBufferedReader(ruta)) {
            br.readLine(); // descartar la cabecera
            for (String line; (line = br.readLine()) != null; ) {
                if (line.isBlank()) continue; // ignorar líneas vacías
                var c = CsvUtils.split(line);
                rows.add(c);
            }
        } catch (IOException e) { throw new UncheckedIOException(e); }

        // Primera pasada: crear Ingredientes (productos simples) y mapear por id
        Map<String, Producto> map = new LinkedHashMap<>();
        for (var c : rows) {
            if (c.isEmpty()) continue;
            String tipo = c.get(0);
            if ("Ingrediente".equals(tipo)) {
                if (c.size() < 7) continue;
                try {
                    String id = CsvUtils.unesc(c.get(1));
                    String nombre = CsvUtils.unesc(c.get(2));
                    int stockA = Integer.parseInt(c.get(3));
                    int stockM = Integer.parseInt(c.get(4));
                    UnidadMedida um = UnidadMedida.valueOf(c.get(5));
                    BigDecimal costo = new BigDecimal(c.get(6));
                    Producto ing = new Ingrediente(id, nombre, stockA, stockM, um, costo);
                    map.put(id, ing);
                } catch (RuntimeException ex) {
                    // omitir linea corrupta
                }
            }
        }

        // Segunda pasada: crear Combos usando referencias a productos ya cargados
        for (var c : rows) {
            if (c.isEmpty()) continue;
            String tipo = c.get(0);
            if ("Combo".equals(tipo)) {
                // esperamos que el campo 'componentes' esté en el índice 7 (último)
                if (c.size() < 8) continue;
                try {
                    String id = CsvUtils.unesc(c.get(1));
                    String nombre = CsvUtils.unesc(c.get(2));
                    String comps = CsvUtils.unesc(c.get(7));
                    Combo combo = new Combo(id, nombre);
                    if (!comps.isBlank()) {
                        String[] parts = comps.split("\\|");
                        for (String p : parts) {
                            String[] kv = p.split(":");
                            if (kv.length != 2) continue;
                            String pid = kv[0];
                            int qty = Integer.parseInt(kv[1]);
                            Producto ref = map.get(pid);
                            if (ref != null) combo.agregarComponente(ref, qty);
                        }
                    }
                    map.put(id, combo);
                } catch (RuntimeException ex) {
                    // omitir combo corrupto
                }
            }
        }

        out.addAll(map.values());
        return out;
    }

    /**
     * Busca un producto por id.
     * <p>Se apoya en {@code listar()} y filtra por la identidad. Atención a la
       firma del getter de id en modelo.Producto: aquí se asume {@code id()}.
     * Si en el modelo el metodo es {@code getId()} adaptar esta llamada.</p>
     */

    @Override public Optional<Producto> buscar(String id) {
        return listar().stream().filter(p -> p.id().equals(id)).findFirst();
    }

    /**
     * Guarda o actualiza una entidad.
     * <p>Se lee la lista completa, se elimina cualquier producto con el mismo id
       y se añade la entidad nueva/actualizada. Finalmente se persiste la lista
       completa con {@link #escribirTodos(List)}.</p>
     */
    @Override public void guardar(Producto entidad) {
        List<Producto> todos = listar();
        // Reemplazar cualquier producto con el mismo id
        todos.removeIf(p -> p.id().equals(entidad.id()));
        todos.add(entidad);
        escribirTodos(todos);
    }

    /**
     * Elimina un producto por id.
     * <p>Si hubo un cambio en la lista (se eliminó al menos uno), se reescribe
       el fichero para persistir el cambio.</p>
     */
    @Override public void eliminar(String id) {
        List<Producto> todos = listar();
        if (todos.removeIf(p -> p.id().equals(id))) escribirTodos(todos);
    }

    /**
     * Escribe todas las entidades en el CSV, sobrescribiendo el fichero.
     * <p>Escribe la cabecera y luego una línea por producto. Para los
     * {@code Ingrediente} se usan los campos en el orden esperado.
       Se emplea {@code CsvUtils.esc} para escapar valores que puedan contener separadores o comillas.</p>
     */
    private void escribirTodos(List<Producto> productos) {
        try (var bw = Files.newBufferedWriter(ruta)) {
            // Escribir cabecera fija
            bw.write("tipo;id;nombre;stockActual;stockMinimo;unidad;costoPorUnidad;componentes\n");

            for (Producto p : productos) {
                if (p instanceof Ingrediente ing) {
                    // Construir la línea CSV con separador ';' y campos escapados
                    bw.write(String.join(";",
                            "Ingrediente",
                            CsvUtils.esc(ing.id()),              // id escapado
                            CsvUtils.esc(ing.getNombre()),      // nombre (verificar firma: getNombre()/nombre())
                            String.valueOf(ing.getStockActual()),
                            String.valueOf(ing.getStockMinimo()),
                            ing.getUnidad().name(),
                            ing.getCostoPorUnidad().toPlainString(),
                            "" // componentes vacío para ingredientes
                    ));
                    bw.newLine();
                } else if (p instanceof Combo cb) {
                    // Serializar componentes como id:qty|id2:qty2
                    var comps = cb.componentes();
                    StringBuilder sb = new StringBuilder();
                    boolean first = true;
                    for (var comp : comps) {
                        if (!first) sb.append('|');
                        sb.append(comp.producto().id()).append(':').append(comp.cantidad());
                        first = false;
                    }
                    // Para Combo dejamos vacíos los campos de stock/unidad/costo y ponemos componentes en la última columna
                    bw.write(String.join(";",
                            "Combo",
                            CsvUtils.esc(cb.id()),
                            CsvUtils.esc(cb.getNombre()),
                            "", // stockActual vacío
                            "", // stockMinimo vacío
                            "", // unidad vacío
                            "", // costoPorUnidad vacío
                            CsvUtils.esc(sb.toString())
                    ));
                    bw.newLine();
                }
                // otros tipos no soportados -> omitir
            }
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
}
