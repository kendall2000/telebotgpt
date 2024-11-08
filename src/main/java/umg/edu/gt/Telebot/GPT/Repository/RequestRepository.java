package umg.edu.gt.Telebot.GPT.Repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import umg.edu.gt.Telebot.GPT.Model.Request;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Repository
public class RequestRepository {

    private static final String TABLENAME = "requests";
    private static DataSource dataSource;

    @Autowired  // Inyección automática del DataSource gestionado por Spring Boot
    public RequestRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // Metodo para agregar un nuevo request
    public static void add(String question, byte[] response, int client) throws SQLException {
        String sql = "INSERT INTO " + TABLENAME + " (question, response, cliente) VALUES (?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, question);         // Vincula la pregunta (question)
            stmt.setBytes(2, response);          // Vincula la respuesta (response) como blob
            stmt.setInt(3, client);              // Vincula el id del cliente
            stmt.executeUpdate();                // Ejecuta la consulta de inserción
        }
    }

    // Metodo para obtener un request por ID
    public static Request getById(int requestId) throws SQLException {
        String sql = "SELECT * FROM " + TABLENAME + " WHERE request = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, requestId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new Request(rs.getInt("request"),
                        rs.getString("question"),
                        rs.getBytes("response"),
                        rs.getInt("cliente"));
            }
        }
        return null;  // Si no se encuentra el request
    }
}
