
package dao;

import modelo.*;
import util.CsvUtils;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

/**
 * Repositorio de persistencia para `PedidoReposicion` usando dos ficheros CSV:
  - `pedidos.csv`: cabeceras de pedido (id;proveedorId;fechaCreacion;estado)
  - `items_pedido.csv`: líneas de pedido (pedidoId;productoId;cantidad)

 * Comportamiento y contratos importantes:
  - Esta implementación almacena solo datos básicos en los CSV. La resolución
    completa de relaciones (por ejemplo, obtener el `Proveedor` real o los
    `Producto`) se delega a capas superiores (p. ej. ServicioPedidosCSV).
  - Lectura (`listar`) devuelve "encabezados" de pedidos con un `Proveedor`
    placeholder cuya información debe ser reemplazada por el servicio.
  - Escritura (`guardar`) abre ambos ficheros en modo APPEND: no sobrescribe
    ni deduplica entradas. Guardar varias veces la misma entidad produce duplicados.
  - No hay eliminación física de items ni de pedidos (el metodo `eliminar`
    lanza `UnsupportedOperationException`).
  - Las IOExceptions se envuelven en `UncheckedIOException` para simplificar
    el manejo en capas superiores.

 * Supuestos y riesgos:
  - Se asume que `CsvUtils.split` / `CsvUtils.unesc` coinciden con el formato real.
  - Si los CSV están corruptos (columnas faltantes, formatos incorrectos) se
    lanzarán excepciones como `IndexOutOfBoundsException` o `DateTimeParseException`.
 */
public class RepositorioPedidoCSV implements IRepositorioPedido {

    // Rutas a ficheros usados como backend
    private final Path pedidos;
    private final Path items; // formato: pedidoId;productoId;cantidad



    /**
     * Constructor que recibe el directorio base donde se crean/usan los CSV.
     * Crea los ficheros si no existen y escribe la cabecera correspondiente.
     * @param baseDir directorio donde se crearán `pedidos.csv` y `items_pedido.csv`
     */
    public RepositorioPedidoCSV(Path baseDir) {
        this.pedidos = baseDir.resolve("pedidos.csv");
        this.items = baseDir.resolve("items_pedido.csv");
        init(pedidos, "id;proveedorId;fechaCreacion;estado\n");
        init(items, "pedidoId;productoId;cantidad\n");
    }

    /**
     * Inicializador auxiliar que asegura el directorio padre y crea el fichero
       con la cabecera si no existe.
     * Convierte {@code IOException} a {@code UncheckedIOException} para no forzar
       manejo de checked exceptions en capas superiores.
     */
    private void init(Path p, String header){
        try {
            Files.createDirectories(p.getParent());
            if (!Files.exists(p)) try (var bw = Files.newBufferedWriter(p)) { bw.write(header); }
        } catch (IOException e){ throw new UncheckedIOException(e); }
    }

    /**
     * Lee los encabezados de pedidos desde `pedidos.csv` y los devuelve como
     * lista de {@code PedidoReposicion}.
     * Detalles:
      - Se descarta la primera línea (cabecera).
      - Cada fila debe tener al menos 4 columnas: id;proveedorId;fechaCreacion;estado.
      - Se crea un {@code Proveedor} placeholder con id = provId y resto "N/D".
        La reconstrucción completa (datos reales del proveedor y los items)
        corresponde a la capa de servicio que invoque este repositorio.
      - La fecha se parsea con {@code LocalDate.parse}: el formato debe coincidir.

     * Efectos secundarios y errores:
     * - Si el CSV está corrupto se lanzarán excepciones no capturadas intencionadamente
        para hacer el fallo visible.
     * @return lista de pedidos (solo encabezados), vacía si no hay registros
     */

    @Override public List<PedidoReposicion> listar() {
        // Para reconstruir items necesitamos un resolver de Producto/Proveedor en ServicioPedidosCSV.
        // Aquí devolvemos solo encabezados; Servicio completará items y relaciones según corresponda.
        List<PedidoReposicion> encabezados = new ArrayList<>();
        try (var br = Files.newBufferedReader(pedidos)) {
            br.readLine(); // descartar cabecera
            for (String line; (line = br.readLine()) != null; ) {
                if (line.isBlank()) continue; // ignorar líneas vacías
                var c = CsvUtils.split(line);
                // c: id;proveedorId;fechaCreacion;estado
                var id = CsvUtils.unesc(c.get(0));
                var provId = CsvUtils.unesc(c.get(1));
                var fecha = LocalDate.parse(c.get(2));
                var estado = EstadoPedido.valueOf(c.get(3));
                // proveedor y items se re-hidratan en el servicio; aquí usamos placeholder.
                encabezados.add(
                        PedidoReposicion.reconstruir(id, new Proveedor(provId, "N/D", "N/D"), fecha, estado)
                );
            }
        } catch (IOException e){ throw new UncheckedIOException(e); }
        return encabezados;
    }

    /**
     * Busca un pedido por id.
     * Implementación simple: delega en {@link #listar()} y filtra por id.
     * Atención: esto recorre todo el fichero cada vez, puede ser costoso para muchos registros.
     * @param id identificador del pedido
     * @return Optional con el pedido encontrado o vacío si no existe
     */
    @Override public Optional<PedidoReposicion> buscar(String id) {
        return listar().stream().filter(p -> p.id().equals(id)).findFirst();
    }

    @Override
    public void guardarCabecera(PedidoReposicion entidad) {
        boolean existe = false;

        try (var br = Files.newBufferedReader(pedidos)) {
            br.readLine(); // cabecera
            for (String line; (line = br.readLine()) != null; ) {
                if (line.isBlank()) continue;
                var c = CsvUtils.split(line);
                if (CsvUtils.unesc(c.get(0)).equals(entidad.id())) { existe = true; break; }
            }
        } catch (IOException e){ throw new UncheckedIOException(e); }

        if (!existe) {
            try (var bw = Files.newBufferedWriter(pedidos, StandardOpenOption.APPEND)) {
                bw.write(String.join(";",
                        CsvUtils.esc(entidad.id()),
                        CsvUtils.esc(entidad.getProveedor().id()),
                        entidad.getFechaCreacion().toString(),
                        entidad.getEstado().name()));
                bw.newLine();
            } catch (IOException e){ throw new UncheckedIOException(e); }
        }
    }
    @Override
    public void agregarItem(String pedidoId, String productoId, int cantidad) {

        // evitar duplicar ítems iguales
        try (var br = Files.newBufferedReader(items)) {
            br.readLine(); // cabecera
            for (String line; (line = br.readLine()) != null; ) {
                if (line.isBlank()) continue;
                var c = CsvUtils.split(line);
                if (pedidoId.equals(CsvUtils.unesc(c.get(0))) &&
                        productoId.equals(CsvUtils.unesc(c.get(1)))) {

                    // si ya está el producto, solo sumamos la cantidad en memoria (PedidoReposicion ya lo hace)
                    return;
                }
            }
        } catch (IOException e){ throw new UncheckedIOException(e); }

        // escribir el item nuevo
        try (var bw = Files.newBufferedWriter(items, StandardOpenOption.APPEND)) {
            bw.write(String.join(";", CsvUtils.esc(pedidoId), CsvUtils.esc(productoId), String.valueOf(cantidad)));
            bw.newLine();
        } catch (IOException e){ throw new UncheckedIOException(e); }
    }

    @Override
    public void guardar(PedidoReposicion entidad) {
        try {
            List<String> in = Files.exists(pedidos) ? Files.readAllLines(pedidos) : List.of();
            List<String> out = new ArrayList<>();

            if (in.isEmpty()) {
                out.add("id;proveedorId;fechaCreacion;estado");
            } else {
                out.add(in.get(0)); // cabecera
            }

            boolean updated = false;
            for (int i = 1; i < in.size(); i++) {
                String line = in.get(i);
                if (line.isBlank()) continue;
                var c = CsvUtils.split(line);
                String id = CsvUtils.unesc(c.get(0));
                if (id.equals(entidad.id())) {
                    // Reescribo la fila con el NUEVO estado (y mismos id/proveedor/fecha)
                    out.add(String.join(";",
                            CsvUtils.esc(entidad.id()),
                            CsvUtils.esc(entidad.getProveedor().id()),
                            entidad.getFechaCreacion().toString(),
                            entidad.getEstado().name()));
                    updated = true;
                } else {
                    out.add(line);
                }
            }

            if (!updated) {
                // No estaba: agrego la cabecera del pedido
                out.add(String.join(";",
                        CsvUtils.esc(entidad.id()),
                        CsvUtils.esc(entidad.getProveedor().id()),
                        entidad.getFechaCreacion().toString(),
                        entidad.getEstado().name()));
            }

            Files.write(pedidos, out,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        // IMPORTANTÍSIMO: acá NO escribir ítems. Los ítems se persisten SOLO con agregarItem(...).
    }

    @Override
    public Map<String, Integer> obtenerItems(String pedidoId) {
        Map<String, Integer> r = new LinkedHashMap<>();
        try (var br = Files.newBufferedReader(items)) {
            br.readLine(); // cabecera
            for (String line; (line = br.readLine()) != null; ) {
                if (line.isBlank()) continue;
                var c = CsvUtils.split(line);
                String pid = CsvUtils.unesc(c.get(0));
                String prod = CsvUtils.unesc(c.get(1));
                int cant = Integer.parseInt(c.get(2));
                if (pid.equals(pedidoId)) {
                    r.merge(prod, cant, Integer::sum);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return r;
    }

    /**
     * Eliminación no soportada por esta implementación.
     * no se implementa la eliminacion para simular entorno de solo escritura.
     * Nota: si se necesitara eliminar, habría que:
      - Reescribir `pedidos.csv` sin la entrada.
      - Reescribir `items_pedido.csv` excluyendo las líneas del pedido.
      - Considerar escriturar a fichero temporal y mover atómicamente.
     * @param id identificador del pedido a eliminar
     */
    @Override public void eliminar(String id) { throw new UnsupportedOperationException("No soportado"); }
}
