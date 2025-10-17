package udem.taln.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import udem.taln.api.utils.MistralUtilities;
import udem.taln.api.utils.ResponseHelper;
import udem.taln.ner.Analyser;
import udem.taln.ner.NER;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Service to interact with Mistral AI (api).
 * <p>
 * Requires :
 * - Env var mistral_key=sk-xxxx
 */
public class MistralService implements LLMService {

    private static final String DEFAULT_BASE_URL = "https://api.mistral.ai";
    private static final String CHAT_COMPLETIONS = "/v1/chat/completions";

    private final String model;
    private final String apiKey;
    private final String baseUrl;
    private final HttpClient http;
    private final ObjectMapper om;

    public MistralService(String model) {
        this(model, DEFAULT_BASE_URL);
    }

    public MistralService(String model, String baseUrl) {
        this.model = Objects.requireNonNull(model);
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = Optional.ofNullable(MistralUtilities.getApiKey())
                .filter(s -> !s.isBlank())
                .orElseThrow(() -> new RuntimeException("MISTRAL_API_KEY is missing (env or config.properties)"));

        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(MistralUtilities.getRequestTimeoutSeconds()))
                .build();

        this.om = new ObjectMapper(new JsonFactory().enable(JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS));
        this.om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.om.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        System.out.println("Mistral API initialized");
        System.out.println("Using model: " + model);
    }

    @Override
    public List<NER.TYPE> process(Analyser.Pair sentence) {
        try {
            String fullPrompt = getFullPrompt(sentence);

            // Request
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", "You are a concise assistant."),
                    Map.of("role", "user", fullPrompt, "")
            ));
            // values
            body.put("temperature", 0.0);
            body.put("top_p", 1.0);
            // body.put("stop", List.of("\n\n"));

            String json = om.writeValueAsString(body);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + CHAT_COMPLETIONS))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(MistralUtilities.getRequestTimeoutSeconds()))
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resp = sendWithBasicRetries(req, MistralUtilities.getMaxRetries());
            if (resp.statusCode() / 100 != 2) {
                System.err.println("Mistral API error: " + resp.statusCode() + " -> " + resp.body());
                return List.of(NER.TYPE.NONE);
            }

            // reponse : { choices: [ { message: { role, content } } ] }
            Map<?, ?> root = om.readValue(resp.body(), Map.class);
            Object choicesObj = root.get("choices");
            if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
                return List.of(NER.TYPE.NONE);
            }
            Object first = choices.getFirst();
            if (!(first instanceof Map<?, ?> f)) return List.of(NER.TYPE.NONE);
            Object message = f.get("message");
            if (!(message instanceof Map<?, ?> msg)) return List.of(NER.TYPE.NONE);
            Object content = msg.get("content");
            String response = content == null ? "" : content.toString();

            // extraction et mapping comme ton OllamaService
            return ResponseHelper.getTypes(response);

        } catch (Exception e) {
            System.err.println("Mistral API call failed: " + e.getMessage());
            return List.of(NER.TYPE.NONE);
        }
    }

    private HttpResponse<String> sendWithBasicRetries(HttpRequest req, int maxRetries) throws IOException, InterruptedException {
        int attempt = 0;
        long backoffMs = 800;
        while (true) {
            attempt++;
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int code = resp.statusCode();
            if (code / 100 == 2) return resp;
            // Retry 429/5xx with exponential backoff
            if (attempt <= maxRetries && (code == 429 || code / 100 == 5)) {
                Thread.sleep(backoffMs);
                backoffMs = Math.min(backoffMs * 2, 8000);
                continue;
            }
            return resp;
        }
    }

    public String getModel() {
        return model;
    }
}