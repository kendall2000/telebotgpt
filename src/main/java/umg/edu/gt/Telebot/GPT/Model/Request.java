package umg.edu.gt.Telebot.GPT.Model;

import lombok.Data;

@Data
public class Request {
    private int request;          // ID del request (clave primaria)
    private String question;      // Pregunta (máximo 2000 caracteres)
    private byte[] response;      // Respuesta (almacenada como BLOB)
    private int cliente;          // ID del cliente (clave foránea)

    // Constructor con todos los campos
    public Request(int request, String question, byte[] response, int cliente) {
        this.request = request;
        this.question = question;
        this.response = response;
        this.cliente = cliente;
    }
}
