package br.edu.ifba.adrmcptools;

public class Main {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            printUsage();
            System.exit(1);
            return;
        }

        String mode = args[0].trim().toLowerCase();
        switch (mode) {
            case "servidor" -> new McpServerApp().start();
            case "cliente" -> new McpClientApp().run();
            default -> {
                System.err.println("Modo desconhecido: " + args[0]);
                printUsage();
                System.exit(1);
            }
        }
    }

    private static void printUsage() {
        System.err.println("Uso: java -jar adr-mcp-tools.jar <servidor|cliente>");
        System.err.println("  servidor  - inicia o servidor MCP via stdio");
        System.err.println("  cliente   - executa o cliente MCP (sobe o servidor e roda os prompts)");
    }
}
