package br.edu.ifba.adrmcptools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpSchema;

import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class McpClientApp {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_ITERATIONS = 8;

    private static final String SYSTEM_PROMPT =
            "You are an assistant specialized in the architecture of the Sistema Maria Brasileira. "
            + "You have access to MCP tools that analyze and query the system's architectural decisions (ADRs). "
            + "Use the available tools whenever they are useful to answer the user and, at the end, present a "
            + "clear and well-organized answer in Portuguese.";

    private final AIService aiService = new AIService();

    public void run() {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));

        ServerParameters params = buildServerParameters();

        StdioClientTransport transport = new StdioClientTransport(params, McpJsonDefaults.getMapper());
        transport.setStdErrorHandler(line -> System.err.println("[servidor] " + line));

        McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(180))
                .initializationTimeout(Duration.ofSeconds(60))
                .clientInfo(new McpSchema.Implementation("adr-mcp-tools-client", "1.0.0"))
                .build();

        try {
            System.out.println("==============================================================");
            System.out.println(" Cliente MCP - Sistema Maria Brasileira");
            System.out.println("==============================================================");
            System.out.println("Inicializando conexão com o servidor MCP...");
            client.initialize();

            McpSchema.ListToolsResult toolsResult = client.listTools();
            List<McpSchema.Tool> tools = toolsResult.tools();
            System.out.println("Ferramentas disponíveis no servidor MCP:");
            for (McpSchema.Tool t : tools) {
                System.out.println("  - " + t.name());
            }

            ArrayNode toolsOpenAI = convertToolsToOpenAI(tools);

            String prompt1 = "Analise os riscos arquiteturais de todas as ADRs do Sistema Maria Brasileira "
                    + "e me apresente um relatório priorizado";
            String prompt2 = "Busque nas ADRs do Sistema Maria Brasileira quais decisões arquiteturais "
                    + "tratam do tema segurança";

            runPrompt(client, toolsOpenAI, 1, prompt1);
            runPrompt(client, toolsOpenAI, 2, prompt2);

        } finally {
            client.closeGracefully();
        }
    }

    private void runPrompt(McpSyncClient client, ArrayNode tools, int number, String prompt) {
        System.out.println();
        System.out.println("==============================================================");
        System.out.println(" PROMPT " + number + ": " + prompt);
        System.out.println("==============================================================");

        ArrayNode messages = MAPPER.createArrayNode();
        messages.add(message("system", SYSTEM_PROMPT));
        messages.add(message("user", prompt));

        String finalAnswer = null;

        for (int iteration = 1; iteration <= MAX_ITERATIONS; iteration++) {
            JsonNode aiMessage = aiService.completeChat(messages, tools);

            messages.add(aiMessage.deepCopy());

            JsonNode toolCalls = aiMessage.path("tool_calls");
            if (toolCalls.isArray() && toolCalls.size() > 0) {
                for (JsonNode toolCall : toolCalls) {
                    String toolCallId = toolCall.path("id").asText();
                    String toolName = toolCall.path("function").path("name").asText();
                    String argumentsJson = toolCall.path("function").path("arguments").asText("{}");

                    System.out.println("  > A IA decidiu chamar a ferramenta: " + toolName
                            + (argumentsJson.isBlank() || "{}".equals(argumentsJson)
                                    ? "" : " com argumentos " + argumentsJson));

                    String result = callTool(client, toolName, argumentsJson);

                    ObjectNode toolMsg = MAPPER.createObjectNode();
                    toolMsg.put("role", "tool");
                    toolMsg.put("tool_call_id", toolCallId);
                    toolMsg.put("name", toolName);
                    toolMsg.put("content", result);
                    messages.add(toolMsg);
                }
                continue;
            }

            finalAnswer = aiMessage.path("content").asText("");
            break;
        }

        System.out.println();
        System.out.println("----------------- RESPOSTA FINAL (PROMPT " + number + ") -----------------");
        if (finalAnswer == null || finalAnswer.isBlank()) {
            System.out.println("(A IA não retornou uma resposta final dentro do limite de iterações.)");
        } else {
            System.out.println(finalAnswer.trim());
        }
        System.out.println("--------------------------------------------------------------");
    }

    private String callTool(McpSyncClient client, String name, String argumentsJson) {
        try {
            Map<String, Object> arguments = new HashMap<>();
            if (argumentsJson != null && !argumentsJson.isBlank()) {
                JsonNode argsNode = MAPPER.readTree(argumentsJson);
                if (argsNode.isObject()) {
                    argsNode.fields().forEachRemaining(e -> arguments.put(e.getKey(), simpleValue(e.getValue())));
                }
            }

            McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(name, arguments);
            McpSchema.CallToolResult result = client.callTool(request);

            StringBuilder sb = new StringBuilder();
            if (result.content() != null) {
                for (McpSchema.Content content : result.content()) {
                    if (content instanceof McpSchema.TextContent text) {
                        sb.append(text.text());
                    }
                }
            }
            String text = sb.toString();
            return text.isBlank() ? "(no content returned by the tool)" : text;
        } catch (Exception e) {
            return "Error while executing tool '" + name + "': " + e.getMessage();
        }
    }

    private static Object simpleValue(JsonNode node) {
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isInt() || node.isLong()) {
            return node.asLong();
        }
        if (node.isNumber()) {
            return node.asDouble();
        }
        return node.asText();
    }

    private ArrayNode convertToolsToOpenAI(List<McpSchema.Tool> mcpTools) {
        ArrayNode tools = MAPPER.createArrayNode();
        for (McpSchema.Tool t : mcpTools) {
            ObjectNode tool = MAPPER.createObjectNode();
            tool.put("type", "function");

            ObjectNode function = MAPPER.createObjectNode();
            function.put("name", t.name());
            if (t.description() != null) {
                function.put("description", t.description());
            }

            JsonNode parameters;
            if (t.inputSchema() != null) {
                parameters = MAPPER.valueToTree(t.inputSchema());
            } else {
                ObjectNode empty = MAPPER.createObjectNode();
                empty.put("type", "object");
                empty.set("properties", MAPPER.createObjectNode());
                parameters = empty;
            }
            function.set("parameters", parameters);

            tool.set("function", function);
            tools.add(tool);
        }
        return tools;
    }

    private static ObjectNode message(String role, String content) {
        ObjectNode m = MAPPER.createObjectNode();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    private ServerParameters buildServerParameters() {
        String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        String jarPath = locateJar();

        ServerParameters.Builder builder = ServerParameters.builder(javaBin)
                .args("-jar", jarPath, "servidor");

        Map<String, String> env = new HashMap<>(System.getenv());
        builder.env(env);

        return builder.build();
    }

    private String locateJar() {
        try {
            File file = new File(
                    McpClientApp.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return file.getAbsolutePath();
        } catch (Exception e) {
            return "target" + File.separator + "adr-mcp-tools.jar";
        }
    }
}
