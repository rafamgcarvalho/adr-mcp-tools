package br.edu.ifba.adrmcptools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Utils {

    public static final String ARCHITECTURE_PATH = "arquitetura/maria_brasileira_arquitetura.md";

    private Utils() {
    }

    public static final class ADR {
        private final String id;
        private final String title;
        private final String context;
        private final String decision;
        private final String status;
        private final List<String> consequences;
        private final List<String> relatedRequirements;

        public ADR(String id, String title, String context, String decision, String status,
                   List<String> consequences, List<String> relatedRequirements) {
            this.id = id;
            this.title = title;
            this.context = context;
            this.decision = decision;
            this.status = status;
            this.consequences = consequences;
            this.relatedRequirements = relatedRequirements;
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public String getContext() {
            return context;
        }

        public String getDecision() {
            return decision;
        }

        public String getStatus() {
            return status;
        }

        public List<String> getConsequences() {
            return consequences;
        }

        public List<String> getRelatedRequirements() {
            return relatedRequirements;
        }

        @Override
        public String toString() {
            return id + " - " + title;
        }
    }

    public static Map<String, ADR> loadADRs() {
        Path path = locateDocument();
        String content;
        try {
            content = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[Utils] Erro ao carregar ADRs: " + e.getMessage());
            return new LinkedHashMap<>();
        }
        Map<String, ADR> adrs = parseADRs(content);
        if (adrs.isEmpty()) {
            System.err.println("[Utils] Nenhuma ADR encontrada no arquivo. Verifique o formato do documento.");
        }
        return adrs;
    }

    private static Path locateDocument() {
        Path base = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 4 && base != null; i++) {
            Path candidate = base.resolve(ARCHITECTURE_PATH);
            if (Files.exists(candidate)) {
                return candidate;
            }
            base = base.getParent();
        }
        return Paths.get(ARCHITECTURE_PATH).toAbsolutePath();
    }

    static Map<String, ADR> parseADRs(String content) {
        Map<String, ADR> adrs = new LinkedHashMap<>();

        Pattern header = Pattern.compile("(?m)^##\\s+(ADR-\\d+)\\s*:\\s*(.+?)\\s*$");
        Matcher m = header.matcher(content);

        List<int[]> positions = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        while (m.find()) {
            positions.add(new int[]{m.start(), m.end()});
            ids.add(m.group(1).trim());
            titles.add(m.group(2).trim());
        }

        for (int i = 0; i < positions.size(); i++) {
            int bodyStart = positions.get(i)[1];
            int bodyEnd = (i + 1 < positions.size()) ? positions.get(i + 1)[0] : content.length();
            String body = content.substring(bodyStart, bodyEnd);

            String context = extractTextSection(body, "Contexto");
            String decision = extractTextSection(body, "Decisão");
            String status = extractStatus(body);
            List<String> consequences = extractList(body, "Consequências");
            List<String> requirements = extractRequirements(body);

            ADR adr = new ADR(ids.get(i), titles.get(i), context, decision, status, consequences, requirements);
            adrs.put(adr.getId(), adr);
        }

        return adrs;
    }

    private static String extractTextSection(String body, String sectionName) {
        Pattern p = Pattern.compile(
                "\\*\\*" + Pattern.quote(sectionName) + ":\\*\\*\\s*(.*?)(?=\\n\\s*\\*\\*[^*]+:\\*\\*|\\z)",
                Pattern.DOTALL);
        Matcher m = p.matcher(body);
        if (m.find()) {
            return normalize(m.group(1));
        }
        return "";
    }

    private static String extractStatus(String body) {
        Pattern p = Pattern.compile("\\*\\*Status:\\*\\*\\s*(.+)");
        Matcher m = p.matcher(body);
        if (m.find()) {
            return m.group(1).trim();
        }
        return "";
    }

    private static List<String> extractList(String body, String sectionName) {
        List<String> items = new ArrayList<>();
        String section = extractSectionBlock(body, sectionName);
        for (String line : section.split("\\R")) {
            String t = line.trim();
            if (t.startsWith("- ")) {
                items.add(t.substring(2).trim());
            } else if (t.startsWith("-")) {
                items.add(t.substring(1).trim());
            }
        }
        return items;
    }

    private static String extractSectionBlock(String body, String sectionName) {
        Pattern p = Pattern.compile(
                "\\*\\*" + Pattern.quote(sectionName) + ":\\*\\*\\s*(.*?)(?=\\n\\s*\\*\\*[^*]+:\\*\\*|\\z)",
                Pattern.DOTALL);
        Matcher m = p.matcher(body);
        return m.find() ? m.group(1) : "";
    }

    private static List<String> extractRequirements(String body) {
        Pattern p = Pattern.compile("\\*\\*Requisitos relacionados:\\*\\*\\s*(.+)");
        Matcher m = p.matcher(body);
        if (!m.find()) {
            return new ArrayList<>();
        }
        String line = m.group(1).trim();
        if (line.endsWith(".")) {
            line = line.substring(0, line.length() - 1);
        }
        List<String> requirements = new ArrayList<>();
        for (String part : line.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                requirements.add(t);
            }
        }
        return requirements;
    }

    private static String normalize(String text) {
        String t = text.trim();
        t = t.replaceAll("\\s*\\n\\s*", " ").trim();
        return t;
    }

    public static String join(List<String> items) {
        return String.join(", ", items == null ? Arrays.asList() : items);
    }
}
