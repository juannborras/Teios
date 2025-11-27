package dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Punto único para obtener conexiones JDBC hacia la base de datos.
 * Ajustá URL, USER y PASS según tu entorno (XAMPP, etc.).
 */
public final class ConexionBD {

    private static final String URL  = "jdbc:mysql://localhost:3306/Teio's";
    private static final String USER = "root";
    private static final String PASS = "";

    private ConexionBD() { }

    public static Connection obtenerConexion() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }
}
