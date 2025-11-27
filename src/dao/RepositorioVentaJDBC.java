package dao;

import modelo.Ingrediente;
import modelo.LineaVenta;
import modelo.UnidadMedida;
import modelo.Venta;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Implementación JDBC del repositorio de ventas.
 * Tablas:
 *  - venta (id, fecha)
 *  - linea_venta (id_venta, id_producto, cantidad, precio_unit)
 */
public class RepositorioVentaJDBC implements IRepositorioVenta {

    @Override
    public List<Venta> listar() {
        Map<String, List<LineaVenta>> lineasPorVenta = new HashMap<>();

        // 1) leer todas las líneas y agruparlas por id_venta
        String sqlLineas = """
                SELECT id_venta, id_producto, cantidad, precio_unit
                FROM linea_venta
                """;

        try (Connection conn = ConexionBD.obtenerConexion();
             PreparedStatement ps = conn.prepareStatement(sqlLineas);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String idVenta = rs.getString("id_venta");
                String idProd = rs.getString("id_producto");
                int cantidad = rs.getInt("cantidad");
                BigDecimal precioUnit = rs.getBigDecimal("precio_unit");

                // Producto placeholder, como en RepositorioVentaCSV
                Ingrediente prodPlaceholder = new Ingrediente(
                        idProd, "N/D", 0, 0, UnidadMedida.UNIDAD, precioUnit);

                LineaVenta lv = new LineaVenta(prodPlaceholder, cantidad, precioUnit);
                lineasPorVenta.computeIfAbsent(idVenta, k -> new ArrayList<>()).add(lv);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al listar líneas de venta", e);
        }

        // 2) leer cabeceras de ventas y reconstruir objetos Venta
        String sqlVentas = "SELECT id, fecha FROM venta";
        List<Venta> ventas = new ArrayList<>();

        try (Connection conn = ConexionBD.obtenerConexion();
             PreparedStatement ps = conn.prepareStatement(sqlVentas);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String id = rs.getString("id");
                Timestamp ts = rs.getTimestamp("fecha");
                LocalDateTime fecha = ts.toLocalDateTime();

                List<LineaVenta> lineas = lineasPorVenta.getOrDefault(id, List.of());
                if (lineas.isEmpty()) {
                    // por seguridad, pero en principio no debería ocurrir
                    continue;
                }

                ventas.add(Venta.reconstruir(id, fecha, lineas));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al listar ventas", e);
        }

        return ventas;
    }

    @Override
    public Optional<Venta> buscar(String id) {
        // Implementación simple basada en listar()
        return listar().stream().filter(v -> v.id().equals(id)).findFirst();
    }

    @Override
    public void guardar(Venta venta) {
        try (Connection conn = ConexionBD.obtenerConexion()) {
            conn.setAutoCommit(false);

            // 1) upsert de cabecera
            String sqlVenta = """
                    INSERT INTO venta (id, fecha)
                    VALUES (?,?)
                    ON DUPLICATE KEY UPDATE
                        fecha = VALUES(fecha)
                    """;

            try (PreparedStatement psV = conn.prepareStatement(sqlVenta)) {
                psV.setString(1, venta.id());
                psV.setTimestamp(2, Timestamp.valueOf(venta.getFecha()));
                psV.executeUpdate();
            }

            // 2) borrar líneas existentes
            try (PreparedStatement psDel = conn.prepareStatement(
                    "DELETE FROM linea_venta WHERE id_venta = ?")) {
                psDel.setString(1, venta.id());
                psDel.executeUpdate();
            }

            // 3) insertar líneas actuales
            String sqlLinea = """
                    INSERT INTO linea_venta (id_venta, id_producto, cantidad, precio_unit)
                    VALUES (?,?,?,?)
                    """;

            try (PreparedStatement psL = conn.prepareStatement(sqlLinea)) {
                for (LineaVenta l : venta.getLineas()) {
                    psL.setString(1, venta.id());
                    psL.setString(2, l.getProducto().id());
                    psL.setInt(3, l.getCantidad());
                    psL.setBigDecimal(4, l.getPrecioUnitario());
                    psL.addBatch();
                }
                psL.executeBatch();
            }

            conn.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Error al guardar venta " + venta.id(), e);
        }
    }

    @Override
    public void eliminar(String id) {
        try (Connection conn = ConexionBD.obtenerConexion()) {
            conn.setAutoCommit(false);

            try (PreparedStatement psDelL = conn.prepareStatement(
                    "DELETE FROM linea_venta WHERE id_venta = ?")) {
                psDelL.setString(1, id);
                psDelL.executeUpdate();
            }

            try (PreparedStatement psDelV = conn.prepareStatement(
                    "DELETE FROM venta WHERE id = ?")) {
                psDelV.setString(1, id);
                psDelV.executeUpdate();
            }

            conn.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Error al eliminar venta " + id, e);
        }
    }
}
