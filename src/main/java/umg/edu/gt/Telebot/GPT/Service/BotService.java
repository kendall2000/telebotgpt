package umg.edu.gt.Telebot.GPT.Service;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import umg.edu.gt.Telebot.GPT.Model.Client;
import umg.edu.gt.Telebot.GPT.Repository.ClientRepository;
import umg.edu.gt.Telebot.GPT.Repository.RequestRepository;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BotService {
    @Value("${openai.api.key}")
    private String openAiApiKey;
    @Value("${telegram.bot.token}")
    private String BOT_TOKEN;
    private final String TELEGRAM_API_URL = "https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage";
    private final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
    private final String OPENAI_API_KEY = openAiApiKey; // Reemplaza con tu clave API de OpenAI

    // Mapa para almacenar el estado de si se está preguntando el nombre al usuario
    private Map<Long, Boolean> askingName = new HashMap<>();
    // Mapa para almacenar el nombre del usuario por chatId
    private Map<Long, String> userNames = new HashMap<>();

    // Metodo para enviar un mensaje a Telegram
    public void sendTelegramMessage(Long chatId, String message) {
        RestTemplate restTemplate = new RestTemplate();
        String url = TELEGRAM_API_URL + "?chat_id=" + chatId + "&text=" + message;
        restTemplate.getForObject(url, String.class);
    }

    // Metodo para establecer el nombre del usuario
    public void setUserName(Long chatId, String name) {
        userNames.put(chatId, name);
    }

    // Metodo para obtener el nombre del usuario
    public String getUserName(Long chatId) {
        return userNames.getOrDefault(chatId, "Aún no me has dicho tu nombre.");
    }

    // Metodo para gestionar si el bot está preguntando el nombre del usuario
    public void setAskingName(Long chatId, boolean asking) {
        askingName.put(chatId, asking);
    }

    public boolean isAskingName(Long chatId) {
        return askingName.getOrDefault(chatId, false);
    }

    // Metodo para interactuar con OpenAI y obtener una respuesta usando el modelo gpt-3.5-turbo
    public String getOpenAIResponse(String question) {
        RestTemplate restTemplate = new RestTemplate();
        // Crear el cuerpo de la solicitud
        String requestBody = "{\n" +
                "  \"model\": \"gpt-3.5-turbo\",\n" +
                "  \"messages\": [\n" +
                "    {\"role\": \"system\", \"content\": \"You are a helpful assistant.\"},\n" +
                "    {\"role\": \"user\", \"content\": \"" + question + "\"}\n" +
                "  ],\n" +
                "  \"max_tokens\": 150\n" +
                "}";

        // Crear el objeto HttpHeaders correcto
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + OPENAI_API_KEY);
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Crear el cuerpo de la solicitud y la entidad HTTP
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        // Realizar la solicitud POST a la API de OpenAI
        ResponseEntity<Map> response = restTemplate.exchange(OPENAI_API_URL, HttpMethod.POST, entity, Map.class);

        // Procesar la respuesta de la API de OpenAI
        Map<String, Object> responseBody = response.getBody();
        if (responseBody != null && responseBody.containsKey("choices")) {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
            if (!choices.isEmpty()) {
                // Devolver la respuesta generada por OpenAI
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                return (String) message.get("content");
            }
        }

        return "Lo siento, no pude obtener una respuesta.";
    }

    // metodo para eliminar caracteres especiales
    private String removeSpecialCharacters(String input) {
        return Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", ""); // Remueve caracteres especiales
    }

    public void handleUpdate(Map<String, Object> update) throws SQLException {
        if (update.containsKey("message")) {
            Map<String, Object> message = (Map<String, Object>) update.get("message");
            Map<String, Object> chat = (Map<String, Object>) message.get("chat");
            long chatId = ((Number) chat.get("id")).longValue();  // Asegúrate de usar long para chatId
            String text = (String) message.get("text");

            // Intentamos buscar si el chatId ya existe en la base de datos
            Client client = ClientRepository.getById(chatId);

            if (client != null) {
                // Si el cliente ya está registrado, solo enviamos el saludo si es un mensaje de inicio
                if (text.equalsIgnoreCase("/start") || text.equalsIgnoreCase("hola")) {
                    sendTelegramMessage(chatId, "¡Hola " + client.getName() + ", en qué te puedo ayudar hoy?");
                } else {
                    // Si no es un mensaje de inicio, respondemos directamente con OpenAI
                    String openAIResponse = getOpenAIResponse(text);
                    openAIResponse = removeSpecialCharacters(openAIResponse);

                    // Insertamos la pregunta y respuesta en la base de datos
                    byte[] responseBytes = openAIResponse.getBytes(StandardCharsets.UTF_8);
                    RequestRepository.add(text, responseBytes, client.getClientId());

                    // Enviamos solo la respuesta de OpenAI a Telegram
                    sendTelegramMessage(chatId, openAIResponse);
                }
            } else {
                // Si no se encuentra el chatId, seguimos el proceso para preguntar y guardar el nombre
                if (text.equalsIgnoreCase("/start") || text.equalsIgnoreCase("hola")) {
                    sendTelegramMessage(chatId, "¡Bienvenido! ¿Cómo te llamas?");
                    setAskingName(chatId, true);  // Indicamos que estamos preguntando por el nombre
                } else if (isAskingName(chatId)) {
                    // Guardamos el nombre proporcionado y lo insertamos en la base de datos
                    setUserName(chatId, text);
                    ClientRepository.add(text, chatId);

                    // Luego de insertar, buscamos nuevamente para confirmar que fue guardado
                    Client newClient = ClientRepository.getById(chatId);

                    // Solo enviamos el saludo una vez cuando el nombre ha sido registrado por primera vez
                    if (newClient != null) {
                        sendTelegramMessage(chatId, "¡Hola " + newClient.getName() + ", en qué te puedo ayudar hoy?");
                    } else {
                        sendTelegramMessage(chatId, "¡Gracias! Tu nombre ha sido guardado.");
                    }

                    setAskingName(chatId, false);  // Terminamos de preguntar el nombre
                }
            }
        } else {
            System.out.println("La actualización no contiene un mensaje válido.");
        }
    }
}