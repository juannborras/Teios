package dao;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Mapea productoId -> proveedorId desde un CSV "productoId;proveedorId". */
public final class ProveedorProductoConfig {
    private final Map<String, String> productoAProveedor = new HashMap<>();
    private final Path path;

    public ProveedorProductoConfig(Path csvPath) {
        Objects.requireNonNull(csvPath, "csvPath");
        this.path = csvPath;
        cargar(csvPath);
    }

    private void cargar(Path path) {
        productoAProveedor.clear();
        try (BufferedReader br = Files.newBufferedReader(path)) {
            br.readLine(); // salteo encabezado
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] parts = line.split(";", -1);
                if (parts.length >= 2) {
                    String prodId = parts[0].trim();
                    String provId = parts[1].trim();
                    if (!prodId.isEmpty() && !provId.isEmpty()) {
                        productoAProveedor.put(prodId, provId);
                    }
                }
            }
        } catch (IOException e) {
            // si no existe o hay error, dejamos el mapa vacío y seguimos
        }
    }

    /** Devuelve el proveedor configurado para un producto, si existe. */
    public Optional<String> proveedorDe(String productoId) {
        return Optional.ofNullable(productoAProveedor.get(productoId));
    }

    /** Asocia un producto con un proveedor y persiste el cambio. */
    public void asociar(String productoId, String proveedorId) {
        if (productoId == null || proveedorId == null) throw new IllegalArgumentException("Null id");
        productoAProveedor.put(productoId, proveedorId);
        guardarTodos();
    }

    /** Elimina cualquier asociación para el producto y persiste. */
    public void desasociar(String productoId) {
        if (productoId == null) return;
        if (productoAProveedor.remove(productoId) != null) guardarTodos();
    }

    private void guardarTodos() {
        try {
            Files.createDirectories(path.getParent());
            try (var bw = Files.newBufferedWriter(path)) {
                bw.write("productoId;proveedorId\n");
                for (var e : productoAProveedor.entrySet()) {
                    bw.write(String.join(";", e.getKey(), e.getValue()));
                    bw.newLine();
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
