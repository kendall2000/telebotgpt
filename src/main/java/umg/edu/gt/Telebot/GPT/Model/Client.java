package umg.edu.gt.Telebot.GPT.Model;

import jakarta.persistence.*;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

// Clase que representa la entidad Client
@Data
@Entity
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int clientId;

    private String name;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserSession> sessions = new ArrayList<>();

    public Client(int clientId, String name) {
        this.clientId = clientId;
        this.name = name;
    }

    public Client() {}

    public void addSession(UserSession session) {
        sessions.add(session);
        session.setUser(this);
    }

    public void removeSession(UserSession session) {
        sessions.remove(session);
        session.setUser(null);
    }
}
