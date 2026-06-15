package br.edu.ifba.adrmcptools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class McpServerApp {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public void start() throws InterruptedException {
        AIService aiService = new AIService();

        StdioServerTransportProvider transportProvider =
                new StdioServerTransportProvider(McpJsonDefaults.getMapper());

        McpSyncServer server = McpServer.sync(transportProvider)
                .serverInfo("adr-mcp-tools", "1.0.0")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .build())
                .tools(
                        createAnalyzeRisksTool(aiService),
                        createSearchByKeywordTool()
                )
                .build();

        System.err.println("[McpServerApp] Servidor MCP 'adr-mcp-tools' iniciado (stdio). "
                + "Ferramentas: analyze_adr_risks, search_adrs_by_keyword.");

        Runtime.getRuntime().addShutdownHook(new Thread(server::closeGracefully));

        new CountDownLatch(1).await();
    }

    private McpServerFeatures.SyncToolSpecification createAnalyzeRisksTool(AIService aiService) {
        String schema = "{\"type\":\"object\",\"properties\":{}}";

        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("analyze_adr_risks")
                .description("Analyzes all ADRs of the Sistema Maria Brasileira using AI and returns a report "
                        + "of architectural risks prioritized by impact level, with mitigation suggestions "
                        + "for each decision")
                .inputSchema(McpJsonDefaults.getMapper(), schema)
                .build();

        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, request) -> {
            try {
                Map<String, Utils.ADR> adrs = Utils.loadADRs();
                String report = aiService.analyzeRisks(adrs);
                return textResult(report, false);
            } catch (Exception e) {
                return textResult("Error while analyzing ADR risks: " + e.getMessage(), true);
            }
        });
    }

    private McpServerFeatures.SyncToolSpecification createSearchByKeywordTool() {
        String schema = "{"
                + "\"type\":\"object\","
                + "\"properties\":{"
                + "\"keyword\":{\"type\":\"string\",\"description\":\"Keyword to search for in the ADRs\"}"
                + "},"
                + "\"required\":[\"keyword\"]"
                + "}";

        McpSchema.Tool tool = McpSchema.Tool.builder()
                .name("search_adrs_by_keyword")
                .description("Receives a keyword and returns all ADRs of the Sistema Maria Brasileira that "
                        + "contain that word in the context, decision or consequences, indicating in which "
                        + "section it appears")
                .inputSchema(McpJsonDefaults.getMapper(), schema)
                .build();

        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, request) -> {
            try {
                Object keywordObj = request.arguments() != null ? request.arguments().get("keyword") : null;
                String keyword = keywordObj != null ? keywordObj.toString().trim() : "";
                String result = searchByKeyword(keyword);
                return textResult(result, false);
            } catch (Exception e) {
                return textResult("Error while searching ADRs by keyword: " + e.getMessage(), true);
            }
        });
    }

    private String searchByKeyword(String keyword) throws Exception {
        if (keyword == null || keyword.isBlank()) {
            ObjectNode error = MAPPER.createObjectNode();
            error.put("message", "Provide a keyword to search the ADRs.");
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(error);
        }

        Map<String, Utils.ADR> adrs = Utils.loadADRs();
        String target = keyword.toLowerCase(Locale.ROOT);

        ArrayNode results = MAPPER.createArrayNode();
        for (Utils.ADR adr : adrs.values()) {
            ArrayNode sections = MAPPER.createArrayNode();

            if (contains(adr.getContext(), target)) {
                sections.add("context");
            }
            if (contains(adr.getDecision(), target)) {
                sections.add("decision");
            }
            if (contains(String.join(" ", adr.getConsequences()), target)) {
                sections.add("consequences");
            }

            if (sections.size() > 0) {
                ObjectNode item = MAPPER.createObjectNode();
                item.put("adr_id", adr.getId());
                item.put("title", adr.getTitle());
                item.set("sections", sections);
                results.add(item);
            }
        }

        if (results.size() == 0) {
            ObjectNode empty = MAPPER.createObjectNode();
            empty.put("message", "No ADR of the Sistema Maria Brasileira contains the word \"" + keyword + "\".");
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(empty);
        }

        ObjectNode root = MAPPER.createObjectNode();
        root.put("keyword", keyword);
        root.set("results", results);
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
    }

    private static boolean contains(String text, String lowerTarget) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(lowerTarget);
    }

    private static McpSchema.CallToolResult textResult(String text, boolean isError) {
        List<McpSchema.Content> content = List.of(new McpSchema.TextContent(text));
        return new McpSchema.CallToolResult(content, isError, null, null);
    }
}
