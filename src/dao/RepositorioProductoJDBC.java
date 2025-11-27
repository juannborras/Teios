package dao;

import modelo.Combo;
import modelo.ComponenteCombo;
import modelo.Ingrediente;
import modelo.Producto;
import modelo.UnidadMedida;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementación de IRepositorioProducto usando JDBC contra MySQL.
 *
 * Tablas involucradas:
 *  - producto (id, nombre, stock_actual, stock_minimo, tipo)
 *  - ingrediente (id_producto, unidad, costo_por_unidad)
 *  - combo (id_producto)
 *  - componente_combo (id_combo, id_componente, cantidad)
 */
public class RepositorioProductoJDBC implements IRepositorioProducto {

    // =====================================================
    // CRUD genérico
    // =====================================================

    @Override
    public List<Producto> listar() {
        String sql = "SELECT id, nombre, stock_actual, stock_minimo, tipo FROM producto";
        List<Producto> resultado = new ArrayList<>();

        try (Connection conn = ConexionBD.obtenerConexion();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String id = rs.getString("id");
                String nombre = rs.getString("nombre");
                int stockActual = rs.getInt("stock_actual");
                int stockMinimo = rs.getInt("stock_minimo");
                String tipo = rs.getString("tipo");

                if ("INGREDIENTE".equalsIgnoreCase(tipo)) {
                    resultado.add(cargarIngrediente(conn, id, nombre, stockActual, stockMinimo));
                } else if ("COMBO".equalsIgnoreCase(tipo)) {
                    resultado.add(cargarCombo(conn, id, nombre, stockActual, stockMinimo));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al listar productos", e);
        }

        return resultado;
    }

    @Override
    public Optional<Producto> buscar(String id) {
        String sql = "SELECT id, nombre, stock_actual, stock_minimo, tipo FROM producto WHERE id = ?";

        try (Connection conn = ConexionBD.obtenerConexion();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();

                String nombre = rs.getString("nombre");
                int stockActual = rs.getInt("stock_actual");
                int stockMinimo = rs.getInt("stock_minimo");
                String tipo = rs.getString("tipo");

                if ("INGREDIENTE".equalsIgnoreCase(tipo)) {
                    return Optional.of(cargarIngrediente(conn, id, nombre, stockActual, stockMinimo));
                } else if ("COMBO".equalsIgnoreCase(tipo)) {
                    return Optional.of(cargarCombo(conn, id, nombre, stockActual, stockMinimo));
                } else {
                    return Optional.empty();
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Error al buscar producto " + id, e);
        }
    }

    @Override
    public void guardar(Producto entidad) {
        try (Connection conn = ConexionBD.obtenerConexion()) {
            conn.setAutoCommit(false);

            String tipo;
            if (entidad instanceof Ingrediente) tipo = "INGREDIENTE";
            else if (entidad instanceof Combo) tipo = "COMBO";
            else throw new IllegalArgumentException("Tipo de producto no soportado: " + entidad.getClass());

            // 1) UPSERT en tabla producto
            String upsertProducto = """
                    INSERT INTO producto (id, nombre, stock_actual, stock_minimo, tipo)
                    VALUES (?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE
                        nombre       = VALUES(nombre),
                        stock_actual = VALUES(stock_actual),
                        stock_minimo = VALUES(stock_minimo),
                        tipo         = VALUES(tipo)
                    """;

            try (PreparedStatement ps = conn.prepareStatement(upsertProducto)) {
                ps.setString(1, entidad.id());
                ps.setString(2, entidad.getNombre());
                ps.setInt(3, entidad.getStockActual());
                ps.setInt(4, entidad.getStockMinimo());
                ps.setString(5, tipo);
                ps.executeUpdate();
            }

            // 2) Detalle según subtipo
            if (entidad instanceof Ingrediente ing) {
                // Si antes era combo, limpiamos su rastro
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM combo WHERE id_producto = ?")) {
                    ps.setString(1, ing.id());
                    ps.executeUpdate();
                }
                guardarIngrediente(ing, conn);

            } else if (entidad instanceof Combo combo) {
                // Si antes era ingrediente, limpiamos su detalle
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM ingrediente WHERE id_producto = ?")) {
                    ps.setString(1, combo.id());
                    ps.executeUpdate();
                }
                guardarCombo(combo, conn);
            }

            conn.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Error al guardar producto " + entidad.id(), e);
        }
    }

    @Override
    public void eliminar(String id) {
        try (Connection conn = ConexionBD.obtenerConexion()) {
            conn.setAutoCommit(false);

            // detalle ingrediente (si lo hubiera)
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM ingrediente WHERE id_producto = ?")) {
                ps.setString(1, id);
                ps.executeUpdate();
            }

            // registro combo (los componentes se borran por ON DELETE CASCADE de fk_cc_combo)
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM combo WHERE id_producto = ?")) {
                ps.setString(1, id);
                ps.executeUpdate();
            }

            // por último, el producto base
            try (PreparedStatement ps = conn.prepareStatement(
                    "DELETE FROM producto WHERE id = ?")) {
                ps.setString(1, id);
                ps.executeUpdate();
            }

            conn.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Error al eliminar producto " + id, e);
        }
    }

    // =====================================================
    // Helpers de reconstrucción
    // =====================================================

    private Ingrediente cargarIngrediente(Connection conn,
                                          String id,
                                          String nombre,
                                          int stockActual,
                                          int stockMinimo) throws SQLException {
        String sql = "SELECT unidad, costo_por_unidad FROM ingrediente WHERE id_producto = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next())
                    throw new IllegalStateException("Ingrediente sin detalle en tabla ingrediente para id " + id);

                UnidadMedida unidad = UnidadMedida.valueOf(rs.getString("unidad"));
                BigDecimal costo = rs.getBigDecimal("costo_por_unidad");

                return new Ingrediente(id, nombre, stockActual, stockMinimo, unidad, costo);
            }
        }
    }

    private Combo cargarCombo(Connection conn,
                              String id,
                              String nombre,
                              int stockActual,
                              int stockMinimo) throws SQLException {

        // Los combos no tienen stock propio: se inicializan siempre con stock 0.
        // Ignoramos los valores de stockActual y stockMinimo provenientes de la BD.
        Combo combo = new Combo(id, nombre);

        String sql = """
                SELECT c.id_componente, c.cantidad,
                       p.nombre, p.stock_actual, p.stock_minimo, p.tipo,
                       i.unidad, i.costo_por_unidad
                FROM componente_combo c
                JOIN producto p ON p.id = c.id_componente
                LEFT JOIN ingrediente i ON i.id_producto = p.id
                WHERE c.id_combo = ?
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String idComp       = rs.getString("id_componente");
                    String nombreComp   = rs.getString("nombre");
                    int stockActComp    = rs.getInt("stock_actual");
                    int stockMinComp    = rs.getInt("stock_minimo");
                    String tipoComp     = rs.getString("tipo");
                    int cantidad        = rs.getInt("cantidad");

                    Producto componente;

                    if ("INGREDIENTE".equalsIgnoreCase(tipoComp)) {
                        UnidadMedida unidad = UnidadMedida.valueOf(rs.getString("unidad"));
                        BigDecimal costo    = rs.getBigDecimal("costo_por_unidad");
                        componente = new Ingrediente(idComp, nombreComp, stockActComp, stockMinComp, unidad, costo);

                    } else if ("COMBO".equalsIgnoreCase(tipoComp)) {
                        // Por simplicidad seguimos asumiendo que los combos no contienen combos.
                        // Si llegara a haber datos as in BD, fallamos de forma controlada.
                        throw new IllegalStateException("Los combos no deben contener otros combos como componentes (id combo=" + id + ", componente=" + idComp + ")");
                    } else {
                        throw new IllegalStateException("Tipo de componente desconocido: " + tipoComp);
                    }

                    combo.agregarComponente(componente, cantidad);
                }
            }
        }

        return combo;
    }

    // =====================================================
    // Helpers de persistencia de detalle
    // =====================================================

    private void guardarIngrediente(Ingrediente ing, Connection conn) throws SQLException {
        String sql = """
                INSERT INTO ingrediente (id_producto, unidad, costo_por_unidad)
                VALUES (?,?,?)
                ON DUPLICATE KEY UPDATE
                    unidad           = VALUES(unidad),
                    costo_por_unidad = VALUES(costo_por_unidad)
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ing.id());
            ps.setString(2, ing.getUnidad().name());
            ps.setBigDecimal(3, ing.getCostoPorUnidad());
            ps.executeUpdate();
        }
    }

    private void guardarCombo(Combo combo, Connection conn) throws SQLException {
        // upsert en tabla combo
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO combo (id_producto) VALUES (?) " +
                        "ON DUPLICATE KEY UPDATE id_producto = VALUES(id_producto)")) {
            ps.setString(1, combo.id());
            ps.executeUpdate();
        }

        // borrar componentes viejos
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM componente_combo WHERE id_combo = ?")) {
            ps.setString(1, combo.id());
            ps.executeUpdate();
        }

        // insertar componentes actuales
        String sqlComp = "INSERT INTO componente_combo (id_combo, id_componente, cantidad) VALUES (?,?,?)";

        try (PreparedStatement ps = conn.prepareStatement(sqlComp)) {
            for (ComponenteCombo c : combo.componentes()) {
                ps.setString(1, combo.id());
                ps.setString(2, c.producto().id());
                ps.setInt(3, c.cantidad());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}
