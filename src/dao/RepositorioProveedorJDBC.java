package dao;

import modelo.Proveedor;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementación JDBC del repositorio de Proveedor.
 * Tabla: proveedor (id, nombre, contacto)
 */
public class RepositorioProveedorJDBC implements IRepositorioProveedor {

    @Override
    public List<Proveedor> listar() {
        String sql = "SELECT id, nombre, contacto FROM proveedor";
        List<Proveedor> proveedores = new ArrayList<>();

        try (Connection conn = ConexionBD.obtenerConexion();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                proveedores.add(mapearProveedor(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al listar proveedores", e);
        }

        return proveedores;
    }

    @Override
    public Optional<Proveedor> buscar(String id) {
        String sql = "SELECT id, nombre, contacto FROM proveedor WHERE id = ?";

        try (Connection conn = ConexionBD.obtenerConexion();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapearProveedor(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al buscar proveedor " + id, e);
        }

        return Optional.empty();
    }

    @Override
    public void guardar(Proveedor entidad) {
        String sql = """
                INSERT INTO proveedor (id, nombre, contacto)
                VALUES (?,?,?)
                ON DUPLICATE KEY UPDATE
                    nombre = VALUES(nombre),
                    contacto = VALUES(contacto)
                """;

        try (Connection conn = ConexionBD.obtenerConexion();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, entidad.id());
            ps.setString(2, entidad.getNombre());
            ps.setString(3, entidad.getContacto());
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Error al guardar proveedor " + entidad.id(), e);
        }
    }

    @Override
    public void eliminar(String id) {
        String sql = "DELETE FROM proveedor WHERE id = ?";

        try (Connection conn = ConexionBD.obtenerConexion();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, id);
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Error al eliminar proveedor " + id, e);
        }
    }

    // ========== helper ==========

    private Proveedor mapearProveedor(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        String nombre = rs.getString("nombre");
        String contacto = rs.getString("contacto");
        return new Proveedor(id, nombre, contacto);
    }
}
