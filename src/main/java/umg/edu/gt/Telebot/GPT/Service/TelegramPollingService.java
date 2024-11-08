package umg.edu.gt.Telebot.GPT.Service;
import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class TelegramPollingService {

    @Autowired
    private BotService botService;
    @Value("${openai.api.key}")
    private String openAiApiKey;
    @Value("${telegram.bot.token}")
    private String BOT_TOKEN;
    private final String TELEGRAM_API_URL = "https://api.telegram.org/bot" + BOT_TOKEN;

    @PostConstruct
    public void startPolling() {
        // Verificar que el token no sea nulo
        if (BOT_TOKEN == null || BOT_TOKEN.isEmpty()) {
            System.err.println("El BOT_TOKEN no está configurado. Por favor, configúralo como una variable de entorno.");
            return;
        }

        // Eliminar el webhook antes de iniciar el polling
        try {
            String deleteWebhookUrl = TELEGRAM_API_URL + "/deleteWebhook";
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<Map> response = restTemplate.getForEntity(deleteWebhookUrl, Map.class);
            Map<String, Object> body = response.getBody();
            if (body != null && (Boolean) body.get("ok")) {
                System.out.println("Webhook eliminado correctamente.");
            } else {
                System.out.println("No se pudo eliminar el webhook: " + body);
            }
        } catch (Exception e) {
            e.printStackTrace();
            // Manejar errores si es necesario
        }

        new Thread(() -> {
            int offset = 0;
            while (true) {
                try {
                    String url = TELEGRAM_API_URL + "/getUpdates?timeout=60&offset=" + offset;
                    RestTemplate restTemplate = new RestTemplate();
                    ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
                    Map<String, Object> body = response.getBody();

                    if (body != null && (Boolean) body.get("ok")) {
                        List<Map<String, Object>> result = (List<Map<String, Object>>) body.get("result");
                        for (Map<String, Object> update : result) {
                            offset = ((Number) update.get("update_id")).intValue() + 1;
                            // Procesar la actualización
                            botService.handleUpdate(update);
                        }
                    }

                    // Dormir un momento antes de la siguiente solicitud
                    Thread.sleep(1000);

                } catch (org.springframework.web.client.HttpClientErrorException e) {
                    if (e.getStatusCode().value() == 409) {
                        System.err.println("Conflicto detectado: " + e.getResponseBodyAsString());
                        // Posible conflicto por otra instancia; esperar y reintentar
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException ex) {
                            throw new RuntimeException(ex);
                        }
                    } else {
                        e.printStackTrace();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    // En caso de error, esperar un poco antes de reintentar
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }).start();
    }
}
