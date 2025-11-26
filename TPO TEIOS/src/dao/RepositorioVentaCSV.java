// java
package dao;

import modelo.*;
import util.CsvUtils;

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Repositorio de ventas persistido en dos ficheros CSV:
  - `ventas.csv`: almacena cabeceras de venta (id;fecha)
  - `lineas_venta.csv`: almacena las líneas (ventaId;productoId;cantidad;precioUnitario)

 * Contrato y comportamiento importante:
  - Las operaciones de lectura reconstrullen objetos {@code Venta} y {@code LineaVenta}.
  - Las líneas contienen una referencia mínima al producto: aquí se crea un
    {@code Ingrediente} placeholder con nombre "N/D" y unidad {@code UNIDAD}.
    La reconstrucción completa del producto debe hacerse en capas superiores si hace falta.
  - Escritura con {@code guardar} abre ambos ficheros en modo APPEND: no evita duplicados
    ni realiza deduplicación/actualización, simplemente añade registros nuevos.
  - {@code eliminar} no está soportado.
  - Las IOExceptions se envuelven en {@code UncheckedIOException} para simplificar callers.
 */
public class RepositorioVentaCSV implements IRepositorioVenta {
    // Rutas a los ficheros usados como backend
    private final Path ventas;
    private final Path lineas;
    private final IRepositorioProducto repoProductos; // nuevo

    /**
     * Constructor que recibe el repositorio de productos y el directorio base.
     */
    public RepositorioVentaCSV(IRepositorioProducto repoProductos, Path baseDir) {
        this.repoProductos = Objects.requireNonNull(repoProductos);
        this.ventas = baseDir.resolve("ventas.csv");
        this.lineas = baseDir.resolve("lineas_venta.csv");
        init(ventas, "id;fecha\n");
        init(lineas, "ventaId;productoId;cantidad;precioUnitario\n");
    }

    /**
     * Inicializador auxiliar que asegura el directorio padre y crea el fichero
     * con la cabecera si no existe.
     */
    private void init(Path p, String header){
        try {
            // ✅ asegurar carpeta asociada (archivo → carpeta padre ; carpeta → ella misma)
            Path folder = Files.isDirectory(p) ? p : p.getParent();
            Files.createDirectories(folder);

            // ✅ si p es archivo y no existe → crear con cabecera
            if (!Files.exists(p) && !Files.isDirectory(p)) {
                try (var bw = Files.newBufferedWriter(p)) {
                    bw.write(header);
                }
            }

        } catch (IOException e){
            throw new UncheckedIOException(e);
        }
    }


    /**
     * Lee ambos CSV y reconstruye la lista de {@code Venta}.

     * Flujo:
      - Lee `lineas_venta.csv` y agrupa las LineaVenta por `ventaId` en el mapa `porVenta`.
        Cada línea produce una instancia de {@code LineaVenta} cuyo producto es un
        {@code Ingrediente} placeholder (nombre "N/D", stock 0) y el precioUnitario
        se toma del campo correspondiente.
      - Lee `ventas.csv`, por cada fila crea una {@code Venta} usando el builder local,
        asignándole las líneas agrupadas por su id (o lista vacía si no tiene líneas).

     * Puntos a vigilar:
      - `CsvUtils.split` y `CsvUtils.unesc` deben coincidir con el formato real.
      - Se asume que las columnas existen y están en el orden correcto (IndexOutOfBounds o
        NumberFormatException si el CSV está corrupto).
      - La fecha se parsea con {@code LocalDateTime.parse(c.get(1))}: el formato debe
        ser compatible con {@code LocalDateTime.toString()} usado en escritura.
     */
    @Override
    public List<Venta> listar() {
        // 1) Agrupar líneas por ventaId desde lineas_venta.csv
        Map<String, List<LineaVenta>> porVenta = new LinkedHashMap<>();
        try (var br = Files.newBufferedReader(lineas)) {
            String header = br.readLine(); // cabecera
            String line;
            int n = 1;
            while ((line = br.readLine()) != null) {
                n++;
                if (line.isBlank()) continue;
                var c = CsvUtils.split(line);
                // ventaId;productoId;cantidad;precioUnitario
                if (c.size() < 4) {
                    System.err.printf("[WARN] lineas_venta.csv línea %d inválida: '%s'%n", n, line);
                    continue;
                }
                String ventaId = CsvUtils.unesc(c.get(0)).trim();
                String prodId  = CsvUtils.unesc(c.get(1)).trim();
                String cantStr = c.get(2).trim();
                String precioStr = c.get(3).trim();

                int cant;
                try { cant = Integer.parseInt(cantStr); }
                catch (NumberFormatException ex) {
                    System.err.printf("[WARN] cantidad inválida en línea %d: '%s'%n", n, cantStr);
                    continue;
                }

                BigDecimal precio;
                try { precio = new BigDecimal(precioStr); }
                catch (NumberFormatException ex) {
                    System.err.printf("[WARN] precio inválido en línea %d: '%s'%n", n, precioStr);
                    continue;
                }

                // Intentar reconstruir el producto real desde repoProductos
                Producto prod = repoProductos.buscar(prodId).orElse(null);
                if (prod == null) {
                    // placeholder como antes
                    prod = new Ingrediente(prodId, "N/D", 0, 0, UnidadMedida.UNIDAD, precio);
                }

                porVenta.computeIfAbsent(ventaId, k -> new ArrayList<>())
                        .add(new LineaVenta(prod, cant, precio));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        // 2) Leer cabeceras de ventas con tolerancia: id;fecha
        List<Venta> ventasLeidas = new ArrayList<>();
        try (var br = Files.newBufferedReader(ventas)) {
            String header = br.readLine(); // cabecera
            String line;
            int n = 1;
            while ((line = br.readLine()) != null) {
                n++;
                if (line.isBlank()) continue;
                var c = CsvUtils.split(line);
                if (c.size() < 2) {
                    System.err.printf("[WARN] ventas.csv línea %d inválida (esperado id;fecha): '%s'%n", n, line);
                    continue;
                }
                String id = CsvUtils.unesc(c.get(0)).trim();
                String fechaStr = c.get(1).trim();

                LocalDateTime fecha;
                try { fecha = LocalDateTime.parse(fechaStr); }
                catch (Exception ex) {
                    // fallback por si alguna línea vieja tiene solo yyyy-MM-dd
                    try { fecha = java.time.LocalDate.parse(fechaStr).atStartOfDay(); }
                    catch (Exception ex2) {
                        System.err.printf("[WARN] fecha inválida en ventas.csv línea %d: '%s'%n", n, fechaStr);
                        continue;
                    }
                }

                ventasLeidas.add(new VentaBuilder()
                        .id(id)
                        .fecha(fecha)
                        .lineas(porVenta.getOrDefault(id, List.of()))
                        .build());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return ventasLeidas;
    }

    /**
     * Busca una venta por id delegando en listar() y filtrando por identidad.
     */
    @Override public Optional<Venta> buscar(String id) {
        return listar().stream().filter(v -> v.id().equals(id)).findFirst();
    }

    /**
     * Guarda una venta añadiendo registros al final de los CSV.
     * Características:
      - Abre ambos ficheros en modo APPEND; no sobrescribe ni elimina entradas previas.
      - Escribe una fila en `ventas.csv` y tantas filas en `lineas_venta.csv` como líneas tenga la venta.
      - Usa {@code CsvUtils.esc} para escapar campos susceptibles de contener separador o comillas.

     * Riesgos:
      - Llamadas repetidas a guardar la misma venta crearán duplicados.
      - No hay transacción entre ambos ficheros: si falla la escritura en uno, el otro puede quedar escrito.
     */
    @Override public void guardar(Venta venta) {
        try (var bwV = Files.newBufferedWriter(ventas, StandardOpenOption.APPEND);
             var bwL = Files.newBufferedWriter(lineas, StandardOpenOption.APPEND)) {
            bwV.write(String.join(";", CsvUtils.esc(venta.id()), venta.getFecha().toString()));
            bwV.newLine();
            for (var l : venta.getLineas()) {
                bwL.write(String.join(";",
                        CsvUtils.esc(venta.id()),
                        CsvUtils.esc(l.getProducto().id()),
                        String.valueOf(l.getCantidad()),
                        l.getPrecioUnitario().toPlainString()));
                bwL.newLine();
            }
        } catch (IOException e){ throw new UncheckedIOException(e); }
    }

    /**
     * Eliminación no soportada por esta implementación.
     */
    @Override public void eliminar(String id) { throw new UnsupportedOperationException("No soportado"); }

    /**
     * Builder local usado sólo para reconstruir objetos {@code Venta} sin exponer
      directamente el constructor/reconstrucción en otras capas.

     * Nota: {@code Venta.reconstruir} debe ser público y validar/defender copias de listas si se requiere inmutabilidad.
     */
    private static class VentaBuilder {
        private String id; private LocalDateTime fecha; private List<LineaVenta> lineas;
        VentaBuilder id(String s){ this.id=s; return this; }
        VentaBuilder fecha(LocalDateTime t){ this.fecha=t; return this; }
        VentaBuilder lineas(List<LineaVenta> l){ this.lineas=l; return this; }
        Venta build(){ return Venta.reconstruir(id, fecha, lineas); }
    }

}
