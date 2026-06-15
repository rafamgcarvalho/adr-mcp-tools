package br.edu.ifba.adrmcptools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.cdimascio.dotenv.Dotenv;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

public class AIService {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static final String OPENAI_BASE_URL = "https://api.openai.com/v1";
    private static final String OPENAI_MODEL = "gpt-4o";

    private static final String GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/openai";
    private static final String GEMINI_MODEL = "gemini-2.5-flash";

    private final String apiKey;
    private final String endpointUrl;
    private final String model;
    private final OkHttpClient http;
    private final ObjectMapper mapper;

    public AIService() {
        this.apiKey = readApiKey();

        boolean isGemini = apiKey != null && apiKey.startsWith("AIza");
        String baseUrl = isGemini ? GEMINI_BASE_URL : OPENAI_BASE_URL;
        this.endpointUrl = baseUrl + "/chat/completions";
        this.model = isGemini ? GEMINI_MODEL : OPENAI_MODEL;

        this.mapper = new ObjectMapper();
        this.http = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(120))
                .writeTimeout(Duration.ofSeconds(30))
                .build();
    }

    private static String readApiKey() {
        String key = null;
        try {
            Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
            key = dotenv.get("OPENAI_API_KEY");
        } catch (Exception ignored) {
        }
        if (key == null || key.isBlank()) {
            key = System.getenv("OPENAI_API_KEY");
        }
        return (key == null || key.isBlank()) ? null : key.trim();
    }

    public String analyzeRisks(Map<String, Utils.ADR> adrs) {
        String prompt = buildRisksPrompt(adrs);

        ArrayNode messages = mapper.createArrayNode();
        ObjectNode userMsg = mapper.createObjectNode();
        userMsg.put("role", "user");
        userMsg.put("content", prompt);
        messages.add(userMsg);

        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.set("messages", messages);
        body.put("temperature", 0.2);
        ObjectNode responseFormat = mapper.createObjectNode();
        responseFormat.put("type", "json_object");
        body.set("response_format", responseFormat);

        JsonNode response = send(body);
        JsonNode message = response.path("choices").path(0).path("message");
        String content = message.path("content").asText("");
        return content == null ? "" : content.trim();
    }

    public JsonNode completeChat(ArrayNode messages, ArrayNode tools) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.set("messages", messages);
        body.put("temperature", 0.2);
        if (tools != null && tools.size() > 0) {
            body.set("tools", tools);
            body.put("tool_choice", "auto");
        }

        JsonNode response = send(body);
        return response.path("choices").path(0).path("message");
    }

    private JsonNode send(ObjectNode body) {
        if (apiKey == null) {
            throw new IllegalStateException(
                    "The OPENAI_API_KEY variable was not found. "
                    + "Create a .env file (based on .env.example) with your key "
                    + "or set the OPENAI_API_KEY environment variable.");
        }
        String json;
        try {
            json = mapper.writeValueAsString(body);
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize the request to the AI API.", e);
        }

        Request request = new Request.Builder()
                .url(endpointUrl)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(json, JSON))
                .build();

        try (Response response = http.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new RuntimeException("AI API error (HTTP " + response.code() + "): " + responseBody);
            }
            return mapper.readTree(responseBody);
        } catch (IOException e) {
            throw new RuntimeException("Communication failure with the AI API.", e);
        }
    }

    private String buildRisksPrompt(Map<String, Utils.ADR> adrs) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a senior software architect specialized in architectural risk analysis. ")
          .append("Analyze the following architectural decision records (ADRs) of the Sistema Maria Brasileira, ")
          .append("a web system for managing cleaning franchises, and generate an architectural risk report based ")
          .append("on the negative consequences recorded in each ADR. For each ADR, identify the main ")
          .append("architectural risk, classify it with an impact level of \"alto\", \"médio\" or \"baixo\", and ")
          .append("suggest a concise mitigation strategy. Order the risks from highest to lowest impact. Return ")
          .append("only a JSON with the field \"risks\", where each item contains: \"adr_id\", \"title\", ")
          .append("\"risk\", \"impact\" and \"mitigation\". Write the textual values in Portuguese. Do not include ")
          .append("explanations, comments or markdown code blocks.\n\n");

        sb.append("ADRs of the Sistema Maria Brasileira:\n\n");
        for (Utils.ADR adr : adrs.values()) {
            sb.append("### ").append(adr.getId()).append(": ").append(adr.getTitle()).append("\n");
            sb.append("Context: ").append(adr.getContext()).append("\n");
            sb.append("Decision: ").append(adr.getDecision()).append("\n");
            sb.append("Status: ").append(adr.getStatus()).append("\n");
            sb.append("Consequences:\n");
            for (String c : adr.getConsequences()) {
                sb.append("  - ").append(c).append("\n");
            }
            sb.append("Related requirements: ").append(Utils.join(adr.getRelatedRequirements())).append("\n\n");
        }

        return sb.toString();
    }
}
