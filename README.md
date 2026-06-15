# ADR MCP Tools

Servidor e cliente **MCP (Model Context Protocol)** em **Java 17+** para análise das decisões
arquiteturais (ADRs) do **Sistema Maria Brasileira** — um sistema web de gerenciamento de uma rede
de franquias de limpeza e conservação residencial e empresarial.

O projeto usa a **IA da OpenAI (GPT-4o)** como agente: o cliente envia prompts em linguagem natural,
a IA decide quais ferramentas MCP chamar, o cliente executa essas ferramentas no servidor MCP,
devolve os resultados para a IA e repete o laço até obter a resposta final.

---

## Arquitetura do projeto

```
adr-mcp-tools/
├── arquitetura/
│   └── maria_brasileira_arquitetura.md   # Documento com as 6 ADRs do sistema
├── src/main/java/br/edu/ifba/adrmcptools/
│   ├── Main.java            # Ponto de entrada (modo servidor ou cliente)
│   ├── Utils.java           # Parser do documento de arquitetura + classe ADR
│   ├── AIService.java       # Comunicação com a API da OpenAI (GPT-4o)
│   ├── McpServerApp.java    # Servidor MCP (stdio) com as 2 ferramentas
│   └── McpClientApp.java    # Cliente MCP agêntico (sobe o servidor e roda os prompts)
├── .env.example
├── pom.xml
└── README.md
```

### Tecnologias

| Função                       | Dependência                                   |
|------------------------------|-----------------------------------------------|
| Linguagem                    | Java 17+                                       |
| Build                        | Maven (`pom.xml`)                              |
| MCP SDK para Java            | `io.modelcontextprotocol.sdk:mcp`             |
| Cliente HTTP (API de IA)     | `com.squareup.okhttp3:okhttp`                 |
| JSON                         | `com.fasterxml.jackson.core:jackson-databind` |
| Variáveis de ambiente (.env) | `io.github.cdimascio:dotenv-java`             |

---

## Pré-requisitos

- **JDK 17 ou superior** instalado (`java -version`)
- **Maven** instalado (`mvn -version`)
- Uma **chave de API de IA**: da **OpenAI** OU do **Google Gemini** (Google AI Studio)

> Se o Java e/ou o Maven não estiverem instalados, consulte o arquivo
> [`INSTRUCOES_INSTALACAO.md`](INSTRUCOES_INSTALACAO.md) (gerado apenas quando a instalação
> automática não é possível).

---

## Configuração da chave de IA

Crie um arquivo `.env` na raiz do projeto (use o `.env.example` como base), com **uma única
variável**:

```
OPENAI_API_KEY=sua_chave_aqui
```

O projeto usa o formato **Chat Completions compatível com a OpenAI** e **detecta o provedor
automaticamente pelo formato da chave** — basta colar a sua chave, sem configurar mais nada:

| Tipo de chave        | Começa com | Provedor usado                    | Modelo            |
|----------------------|------------|-----------------------------------|-------------------|
| OpenAI               | `sk-`      | `api.openai.com`                  | `gpt-4o`          |
| Google Gemini (AI Studio) | `AIza`     | camada do Gemini compatível com OpenAI | `gemini-2.5-flash` |

Como alternativa, você pode definir a variável de ambiente `OPENAI_API_KEY` no sistema.

---

## Como compilar

Na raiz do projeto (`adr-mcp-tools/`):

```bash
mvn package
```

Isso gera o JAR executável (fat jar) em `target/adr-mcp-tools.jar`.

---

## Como executar

### Rodar o cliente (recomendado)

O cliente sobe o servidor automaticamente como subprocesso e executa os dois prompts.

**No Windows (PowerShell) — use o script de atalho** (já ativa o UTF-8 no terminal, então os
acentos aparecem corretos):

```powershell
.\executar-cliente.ps1
```

Ou diretamente (veja a nota sobre acentos abaixo):

```bash
java -jar target/adr-mcp-tools.jar cliente
```

### Rodar apenas o servidor

O servidor MCP fala via **stdio** (entrada/saída padrão). Normalmente ele é iniciado pelo cliente
ou por um host MCP, mas pode ser executado isoladamente:

```powershell
.\executar-servidor.ps1
```

```bash
java -jar target/adr-mcp-tools.jar servidor
```

> **Acentos no Windows:** o programa imprime em UTF-8. O console do PowerShell, por padrão, usa
> outra codificação (code page 850), o que faz os acentos aparecerem como símbolos estranhos
> (ex.: `relat├│rio`). Os scripts `executar-cliente.ps1` / `executar-servidor.ps1` resolvem isso
> automaticamente. Se preferir rodar o `java` direto, ative o UTF-8 antes com:
>
> ```powershell
> chcp 65001
> java -jar target/adr-mcp-tools.jar cliente
> ```

---

## As duas ferramentas MCP e sua relação com as ADRs

O servidor expõe duas ferramentas, ambas operando sobre as 6 ADRs documentadas em
`arquitetura/maria_brasileira_arquitetura.md`:

### 1. `analyze_adr_risks`

- **Parâmetros:** nenhum.
- **O que faz:** carrega todas as ADRs, envia-as para a IA (GPT-4o) e retorna um relatório de
  **riscos arquiteturais priorizados por nível de impacto** (alto, médio, baixo), com uma
  **estratégia de mitigação** para cada decisão.
- **Relação com as ADRs:** baseia-se nas **consequências negativas** registradas em cada ADR
  (ex.: aumento de acoplamento no módulo financeiro — ADR-04; complexidade da verificação de
  disponibilidade — ADR-02; duplicação de validação — ADR-05).
- **Saída:** JSON com o campo `risks`, em que cada item contém `adr_id`, `title`, `risk`,
  `impact` e `mitigation`.

### 2. `search_adrs_by_keyword`

- **Parâmetro:** `keyword` (String).
- **O que faz:** busca a palavra (case-insensitive) nos campos **contexto**, **decisão** e
  **consequências** de cada ADR e retorna quais ADRs contêm a palavra e em **qual seção** ela
  aparece. Se nada for encontrado, retorna uma mensagem informando.
- **Relação com as ADRs:** permite localizar rapidamente decisões por tema. Por exemplo, a busca por
  *"segurança"* tende a destacar a **ADR-03 (Controle de Acesso Baseado em Perfis / RBAC)**.
- **Saída:** JSON com a lista de resultados (`adr_id`, `title`, `sections`) ou uma mensagem de
  "nenhuma ADR encontrada".

---

## Os dois prompts executados pelo cliente

1. **Prompt 1:** *"Analise os riscos arquiteturais de todas as ADRs do Sistema Maria Brasileira e me
   apresente um relatório priorizado"* → leva a IA a chamar `analyze_adr_risks`.
2. **Prompt 2:** *"Busque nas ADRs do Sistema Maria Brasileira quais decisões arquiteturais tratam do
   tema segurança"* → leva a IA a chamar `search_adrs_by_keyword` (ex.: palavra `segurança`).

Para cada prompt, o cliente imprime claramente o prompt executado, as ferramentas acionadas e a
**resposta final** produzida pela IA.

---

## As 6 ADRs do Sistema Maria Brasileira

| ADR    | Decisão                                                        |
|--------|----------------------------------------------------------------|
| ADR-01 | Adoção do estilo arquitetural N-Camadas                        |
| ADR-02 | Verificação de disponibilidade de prestadores no agendamento   |
| ADR-03 | Controle de acesso baseado em perfis de usuário (RBAC)         |
| ADR-04 | Módulo financeiro integrado com agendamentos e vendas          |
| ADR-05 | Validação de dados em duas etapas                              |
| ADR-06 | Adoção de banco de dados relacional (MySQL)                    |
