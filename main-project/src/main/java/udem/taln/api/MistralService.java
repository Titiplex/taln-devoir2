package udem.taln.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import udem.taln.api.utils.*;
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
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

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
    private static final TokenBucket BUCKET =
            new TokenBucket(MistralUtilities.getPermitsPerSecond(), 1);
    private static final Cooldown COOLDOWN =
            new Cooldown(MistralUtilities.getMax429Strikes(), MistralUtilities.getCooldownMs());

    private static final Map<String, NER.TYPE> MEMO = new java.util.concurrent.ConcurrentHashMap<>();


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

    private static final RateLimiter RL = new RateLimiter(
            MistralUtilities.getQps());

    @Override
    public List<NER.TYPE> process(Analyser.Pair sentence) {
        RL.acquire(); // throttle
        try {
            String fullPrompt = getFullPrompt(sentence);

            // Request
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", "You are a concise assistant."),
                    Map.of("role", "user", "content", fullPrompt)
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

            // throttle
            BUCKET.acquire();

            String cacheKey = sentence.sentence; // cache just in case
            NER.TYPE cached = MEMO.get(cacheKey);
            if (cached != null) return List.of(cached);

            HttpResponse<String> resp = sendWithRetries429(req, MistralUtilities.getMaxRetries(), MistralUtilities.getMaxTotalWaitMs());
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

            return ResponseHelper.getTypes(response);

        } catch (Exception e) {
            System.err.println("Mistral API call failed: " + e.getMessage());
            return List.of(NER.TYPE.NONE);
        }
    }

    public List<NER.PSentence> executeBatch(Map<Integer, Analyser.Pair> text) {
        List<NER.PSentence> result = new ArrayList<>();

        var analysed = this.processBatch(new ArrayList<>(text.values()));
        int i = 0;
        for (var sentence : text.entrySet()) {
            result.add(new NER.PSentence(sentence.getKey(), List.of(analysed.get(i++))));
        }

        return result;
    }

    private List<NER.TYPE> processBatch(List<Analyser.Pair> sentences) {
        if (sentences == null || sentences.isEmpty()) return List.of();

        StringBuilder sb = new StringBuilder();
        sb.append(getFullPrompt(new Analyser.Pair("", "")));
        sb.append("""
                Output format (STRICT):
                        ANSWER [[PERSON]]   or   ANSWER [[ORGANIZATION]]   or   ANSWER [[LOCATION]]   or   ANSWER [[NONE]]
                        No explanation. No extra words. Exactly that format.
                """);
        for (int i = 0; i < sentences.size(); i++) {
            sb.append(i).append(") ").append(sentences.get(i).sentence).append('\n');
        }
        String fullPrompt = sb.toString();

        // messages
        List<Map<String, Object>> msgs = new ArrayList<>();
        msgs.add(Map.of("role", "system", "content", "You are a concise assistant."));
        msgs.add(Map.of("role", "user", "content", fullPrompt));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", msgs);
        body.put("temperature", 0.0);
        body.put("top_p", 1.0);

        try {
            BUCKET.acquire();
            String json = om.writeValueAsString(body);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + CHAT_COMPLETIONS))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(MistralUtilities.getRequestTimeoutSeconds()))
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resp = sendWithRetries429(req,
                    MistralUtilities.getMaxRetries(),
                    MistralUtilities.getMaxTotalWaitMs());

            if (resp.statusCode() / 100 != 2) {
                System.err.println("Mistral API error (batch): " + resp.statusCode() + " -> " + resp.body());
                return Collections.nCopies(sentences.size(), NER.TYPE.NONE);
            }

            Map<?, ?> root = om.readValue(resp.body(), Map.class);
            List<?> choices = (List<?>) root.get("choices");
            if (choices == null || choices.isEmpty())
                return Collections.nCopies(sentences.size(), NER.TYPE.NONE);
            Map<?, ?> msg = (Map<?, ?>) ((Map<?, ?>) choices.getFirst()).get("message");
            String content = String.valueOf(msg.get("content"));

            String[] lines = content.split("\\r?\\n");
            List<NER.TYPE> out = new ArrayList<>(sentences.size());
            int i = 0;
            for (String line : lines) {
                System.out.println("RAW: " + line);
                if (i >= sentences.size()) break;
                String token = ResponseHelper.extractBetweenDoubleBrackets(line);
                if (token == null || token.isBlank()) {
                    token = greedyLabelRegex(line);
                }
                System.out.println("TOKEN: " + token);
                String norm = normalizeToEnumLabel(token);
                System.out.println("NORM: " + norm);
                NER.TYPE t = NER.mapType(norm);
                out.add(t);
                MEMO.putIfAbsent(sentences.get(i).sentence, t);
                i++;
            }
            while (out.size() < sentences.size()) out.add(NER.TYPE.NONE);
            return out;

        } catch (Exception e) {
            System.err.println("Batch call failed: " + e.getMessage());
            return java.util.Collections.nCopies(sentences.size(), NER.TYPE.NONE);
        }
    }

    private HttpResponse<String> sendWithRetries429(HttpRequest req,
                                                    int maxRetries,
                                                    long maxTotalWaitMs) throws IOException, InterruptedException {
        int attempt = 0;
        long waited = 0L;
        long baseBackoff = 800L;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        while (true) {
            attempt++;
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int code = resp.statusCode();

            if (code / 100 == 2) {
                COOLDOWN.reset();
                return resp;
            }

            // 429 -> Retry-After if present
            if (code == 429 && attempt <= maxRetries) {
                long sleepMs = -1L;
                Optional<String> retryAfter = resp.headers().firstValue("Retry-After");
                if (retryAfter.isPresent()) {
                    try {
                        long secs = Long.parseLong(retryAfter.get().trim());
                        sleepMs = Math.max(0, secs * 1000L);
                    } catch (NumberFormatException ignore) {
                    }
                }
                if (sleepMs < 0) {
                    long pow = Math.min(13, attempt);
                    long backoff = (long) (baseBackoff * Math.pow(2, pow - 1));
                    sleepMs = Math.min(8000L, backoff); // cap 8s
                    sleepMs = rnd.nextLong(sleepMs + 1);
                }
                if (waited + sleepMs > maxTotalWaitMs) return resp;
                Thread.sleep(sleepMs);
                waited += sleepMs;
                continue;
            }

            if (code == 429) COOLDOWN.on429();

            if (code / 100 == 5 && attempt <= maxRetries) {
                long sleepMs = rnd.nextLong(1 + Math.min(8000L, (long) (baseBackoff * Math.pow(2, attempt - 1))));
                if (waited + sleepMs > maxTotalWaitMs) return resp;
                Thread.sleep(sleepMs);
                waited += sleepMs;
                continue;
            }

            return resp;
        }
    }

    private static String greedyLabelRegex(String s) {
        if (s == null) return null;
        Pattern P = Pattern.compile(
                "\\b(PERSON|ORGANIZATION|ORGANISATION|LOCATION|LOC|ORG|GPE|PER|NONE)\\b",
                Pattern.CASE_INSENSITIVE);
        var m = P.matcher(s);
        return m.find() ? m.group(1) : null;
    }

    private static String normalizeToEnumLabel(String raw) {
        if (raw == null) return "NONE";
        String t = raw.replaceAll("[^A-Za-z]", "").toUpperCase();
        // alias courants
        if (t.equals("PER")) t = "PERSON";
        if (t.equals("ORG") || t.equals("ORGANISATION")) t = "ORGANIZATION";
        if (t.equals("LOC") || t.equals("GPE")) t = "LOCATION";
        if (!(t.equals("PERSON") || t.equals("ORGANIZATION") || t.equals("LOCATION") || t.equals("NONE"))) {
            t = "NONE";
        }
        return t;
    }

    public String getModel() {
        return model;
    }
}