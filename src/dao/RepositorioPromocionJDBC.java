package dao;

import modelo.Promocion;
import modelo.Producto;

import java.sql.*;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

public class RepositorioPromocionJDBC implements IRepositorioPromocion {

    private final Connection connection;

    public RepositorioPromocionJDBC(Connection connection) {
        this.connection = Objects.requireNonNull(connection);
    }

    @Override
    public void guardar(Promocion promo) {
        Objects.requireNonNull(promo, "promocion");

        // 1) Guardar la promoción principal
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO promocion (id, nombre, porcentaje, fecha_inicio, fecha_fin) " +
                        "VALUES (?, ?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE nombre=?, porcentaje=?, fecha_inicio=?, fecha_fin=?"
        )) {
            ps.setString(1, promo.id());
            ps.setString(2, promo.nombre());
            ps.setBigDecimal(3, promo.porcentajeDescuento().multiply(new java.math.BigDecimal("100")));
            // opcional: guardar como 10 para 10%
            ps.setDate(4, Date.valueOf(promo.fechaInicio()));
            ps.setDate(5, Date.valueOf(promo.fechaFin()));

            // UPDATE part
            ps.setString(6, promo.nombre());
            ps.setBigDecimal(7, promo.porcentajeDescuento().multiply(new java.math.BigDecimal("100")));
            ps.setDate(8, Date.valueOf(promo.fechaInicio()));
            ps.setDate(9, Date.valueOf(promo.fechaFin()));

            ps.executeUpdate();
        }
        catch (SQLException e) {
            throw new RuntimeException("Error al guardar promocion: " + promo.id(), e);
        }

        // 2) Limpiar y volver a insertar los productos asociados
        try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM promocion_producto WHERE promocion_id = ?"
        )) {
            delete.setString(1, promo.id());
            delete.executeUpdate();
        }
        catch (SQLException e) {
            throw new RuntimeException("Error al limpiar productos de promo: " + promo.id(), e);
        }

        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO promocion_producto (promocion_id, producto_id) VALUES (?, ?)"
        )) {
            for (String prodId : promo.idsProductosAplicados()) {
                insert.setString(1, promo.id());
                insert.setString(2, prodId);
                insert.addBatch();
            }
            insert.executeBatch();
        }
        catch (SQLException e) {
            throw new RuntimeException("Error al insertar productos de promo " + promo.id(), e);
        }
    }

    @Override
    public Promocion buscarPorId(String id) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM promocion WHERE id = ?"
        )) {
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();

            if (!rs.next()) return null;

            return reconstruirPromocion(rs);
        }
        catch (SQLException e) {
            throw new RuntimeException("Error al buscar promocion por id: " + id, e);
        }
    }

    @Override
    public List<Promocion> listarTodas() {
        List<Promocion> result = new ArrayList<>();

        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM promocion"
        )) {
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                result.add(reconstruirPromocion(rs));
            }

            return result;
        }
        catch (SQLException e) {
            throw new RuntimeException("Error al listar promociones", e);
        }
    }

    @Override
    public List<Promocion> buscarVigentesParaProducto(String productoId, LocalDate fecha) {
        List<Promocion> result = new ArrayList<>();

        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT p.* FROM promocion p " +
                        "JOIN promocion_producto pp ON p.id = pp.promocion_id " +
                        "WHERE pp.producto_id = ? " +
                        "AND ? BETWEEN p.fecha_inicio AND p.fecha_fin"
        )) {
            ps.setString(1, productoId);
            ps.setDate(2, Date.valueOf(fecha));

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                result.add(reconstruirPromocion(rs));
            }

            return result;
        }
        catch (SQLException e) {
            throw new RuntimeException("Error buscando promociones vigentes para producto " + productoId, e);
        }
    }

    // =====================
    //   Métodos privados
    // =====================

    private Promocion reconstruirPromocion(ResultSet rs) throws SQLException {

        String id = rs.getString("id");
        String nombre = rs.getString("nombre");
        // guardaste porcentaje como 10 = 10%
        java.math.BigDecimal porcentajeBD = rs.getBigDecimal("porcentaje")
                .divide(new java.math.BigDecimal("100")); // volvemos a 0.10
        LocalDate inicio = rs.getDate("fecha_inicio").toLocalDate();
        LocalDate fin = rs.getDate("fecha_fin").toLocalDate();

        // reconstruir lista de productos asociados
        Set<String> productos = obtenerProductosDePromo(id);

        return new Promocion(
                id,
                nombre,
                porcentajeBD,
                inicio,
                fin,
                productos
        );
    }

    private Set<String> obtenerProductosDePromo(String promoId) {
        Set<String> productos = new HashSet<>();

        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT producto_id FROM promocion_producto WHERE promocion_id = ?"
        )) {
            ps.setString(1, promoId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                productos.add(rs.getString("producto_id"));
            }
        }
        catch (SQLException e) {
            throw new RuntimeException("Error obteniendo productos de promo " + promoId, e);
        }

        return productos;
    }
}
