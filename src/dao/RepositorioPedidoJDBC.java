package dao;

import modelo.EstadoPedido;
import modelo.PedidoReposicion;
import modelo.Proveedor;

import java.sql.*;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

/**
 * Implementación JDBC del repositorio de pedidos de reposición.
 * Tablas:
 *  - pedido_reposicion (id, proveedor_id, fecha_creacion, fecha_envio, fecha_recepcion, estado)
 *  - item_pedido (pedido_id, producto_id, cantidad)
 */
public class RepositorioPedidoJDBC implements IRepositorioPedido {

    @Override
    public List<PedidoReposicion> listar() {
        String sql = """
            SELECT id, proveedor_id, fecha_creacion, fecha_envio, fecha_recepcion, estado
            FROM pedido_reposicion
            """;

        List<PedidoReposicion> pedidos = new ArrayList<>();

        try (Connection conn = ConexionBD.obtenerConexion();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String id = rs.getString("id");
                String provId = rs.getString("proveedor_id");
                LocalDate fechaCreacion = rs.getDate("fecha_creacion").toLocalDate();
                LocalDate fechaEnvio = rs.getDate("fecha_envio") != null
                        ? rs.getDate("fecha_envio").toLocalDate()
                        : null;
                LocalDate fechaRecepcion = rs.getDate("fecha_recepcion") != null
                        ? rs.getDate("fecha_recepcion").toLocalDate()
                        : null;
                EstadoPedido estado = EstadoPedido.valueOf(rs.getString("estado"));

                Proveedor proveedor = new Proveedor(provId, "N/D", "N/D");
                pedidos.add(PedidoReposicion.reconstruir(id, proveedor, fechaCreacion,
                        fechaEnvio, fechaRecepcion, estado));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al listar pedidos", e);
        }

        return pedidos;
    }

    @Override
    public Optional<PedidoReposicion> buscar(String id) {
        // Implementación simple, como en CSV: apoyarse en listar()
        return listar().stream().filter(p -> p.id().equals(id)).findFirst();
    }

    @Override
    public void guardar(PedidoReposicion entidad) {
        try (Connection conn = ConexionBD.obtenerConexion()) {
            conn.setAutoCommit(false);

            // 1) upsert de cabecera
            guardarCabeceraInterno(entidad, conn);

            // 2) borrar ítems existentes
            try (PreparedStatement psDel = conn.prepareStatement(
                    "DELETE FROM item_pedido WHERE pedido_id = ?")) {
                psDel.setString(1, entidad.id());
                psDel.executeUpdate();
            }

            // 3) insertar ítems actuales del pedido
            String sqlItem = "INSERT INTO item_pedido (pedido_id, producto_id, cantidad) VALUES (?,?,?)";
            try (PreparedStatement psIns = conn.prepareStatement(sqlItem)) {
                for (var e : entidad.getItems().entrySet()) {
                    String prodId = e.getKey().id();
                    int cant = e.getValue();
                    psIns.setString(1, entidad.id());
                    psIns.setString(2, prodId);
                    psIns.setInt(3, cant);
                    psIns.addBatch();
                }
                psIns.executeBatch();
            }

            conn.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Error al guardar pedido " + entidad.id(), e);
        }
    }

    @Override
    public void eliminar(String id) {
        try (Connection conn = ConexionBD.obtenerConexion()) {
            conn.setAutoCommit(false);

            try (PreparedStatement psDelItems = conn.prepareStatement(
                    "DELETE FROM item_pedido WHERE pedido_id = ?")) {
                psDelItems.setString(1, id);
                psDelItems.executeUpdate();
            }

            try (PreparedStatement psDelPed = conn.prepareStatement(
                    "DELETE FROM pedido_reposicion WHERE id = ?")) {
                psDelPed.setString(1, id);
                psDelPed.executeUpdate();
            }

            conn.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Error al eliminar pedido " + id, e);
        }
    }

    // ============ métodos específicos de IRepositorioPedido ============

    @Override
    public Map<String, Integer> obtenerItems(String pedidoId) {
        String sql = "SELECT producto_id, cantidad FROM item_pedido WHERE pedido_id = ?";
        Map<String, Integer> items = new LinkedHashMap<>();

        try (Connection conn = ConexionBD.obtenerConexion();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, pedidoId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String prodId = rs.getString("producto_id");
                    int cant = rs.getInt("cantidad");
                    items.merge(prodId, cant, Integer::sum);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error al obtener ítems del pedido " + pedidoId, e);
        }

        return items;
    }

    @Override
    public void guardarCabecera(PedidoReposicion pedido) {
        try (Connection conn = ConexionBD.obtenerConexion()) {
            guardarCabeceraInterno(pedido, conn);
        } catch (SQLException e) {
            throw new RuntimeException("Error al guardar cabecera del pedido " + pedido.id(), e);
        }
    }

    @Override
    public void agregarItem(String pedidoId, String productoId, int cantidad) {
        String sql = """
                INSERT INTO item_pedido (pedido_id, producto_id, cantidad)
                VALUES (?,?,?)
                ON DUPLICATE KEY UPDATE
                    cantidad = cantidad + VALUES(cantidad)
                """;

        try (Connection conn = ConexionBD.obtenerConexion();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, pedidoId);
            ps.setString(2, productoId);
            ps.setInt(3, cantidad);
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Error al agregar item al pedido " + pedidoId, e);
        }
    }

    // ============ helpers internos ============

    private void guardarCabeceraInterno(PedidoReposicion pedido, Connection conn) throws SQLException {
        String sqlCab = """
            INSERT INTO pedido_reposicion
                (id, proveedor_id, fecha_creacion, fecha_envio, fecha_recepcion, estado)
            VALUES (?,?,?,?,?,?)
            ON DUPLICATE KEY UPDATE
                proveedor_id    = VALUES(proveedor_id),
                fecha_creacion  = VALUES(fecha_creacion),
                fecha_envio     = VALUES(fecha_envio),
                fecha_recepcion = VALUES(fecha_recepcion),
                estado          = VALUES(estado)
            """;

        try (PreparedStatement ps = conn.prepareStatement(sqlCab)) {
            ps.setString(1, pedido.id());
            ps.setString(2, pedido.getProveedor().id());
            ps.setDate(3, Date.valueOf(pedido.getFechaCreacion()));

            if (pedido.getFechaEnvio() != null) {
                ps.setDate(4, Date.valueOf(pedido.getFechaEnvio()));
            } else {
                ps.setNull(4, Types.DATE);
            }

            if (pedido.getFechaRecepcion() != null) {
                ps.setDate(5, Date.valueOf(pedido.getFechaRecepcion()));
            } else {
                ps.setNull(5, Types.DATE);
            }

            ps.setString(6, pedido.getEstado().name());

            ps.executeUpdate();
        }
    }

}
