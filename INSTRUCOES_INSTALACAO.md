# Instruções de Instalação do Java 17 e do Maven (Windows)

> **Situação neste computador:** o **JDK 17** foi instalado automaticamente com sucesso via
> `winget`. O **Maven**, porém, **não está disponível no winget** (não existe o pacote
> `Apache.Maven`), então ele foi instalado manualmente (download do .zip oficial + configuração de
> variáveis de ambiente). **Ambos já estão instalados e funcionando** — este guia documenta o que
> foi feito e serve de referência caso você precise reinstalar ou configurar em outra máquina.

Sistema operacional detectado: **Windows 11**.

---

## O que já foi instalado neste computador

| Ferramenta | Versão           | Como foi instalada                                              | Local                                                            |
|------------|------------------|-----------------------------------------------------------------|-----------------------------------------------------------------|
| JDK 17     | Microsoft OpenJDK 17.0.19 | `winget install Microsoft.OpenJDK.17`                  | `C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot`             |
| Maven      | Apache Maven 3.9.9 | Download manual do .zip oficial + extração                    | `C:\Users\rafac\tools\apache-maven-3.9.9`                       |

E as variáveis de ambiente de **usuário** foram configuradas:

- `JAVA_HOME = C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot`
- `MAVEN_HOME = C:\Users\rafac\tools\apache-maven-3.9.9`
- `Path` recebeu `%JAVA_HOME%\bin` e `%MAVEN_HOME%\bin`

> ⚠️ Variáveis de ambiente só passam a valer em **novos** terminais. Se você abriu o terminal antes
> da instalação, **feche e abra um novo** PowerShell para que `java` e `mvn` sejam reconhecidos.

---

## Como verificar se está tudo certo

Abra um **novo** PowerShell e rode:

```powershell
java -version
mvn -version
```

Saída esperada (aproximada):

```
openjdk version "17.0.19" ...
Apache Maven 3.9.9 ...
```

Se os dois comandos responderem, **pule direto para a seção "Voltar e rodar o projeto"** no final
deste documento.

---

## Instalação do zero (caso precise refazer)

### 1) JDK 17

**Opção A — via winget (recomendada, foi a usada aqui):**

```powershell
winget install --id Microsoft.OpenJDK.17 -e
```

**Opção B — download manual (Eclipse Temurin / Adoptium):**

1. Acesse o site oficial: <https://adoptium.net>
2. Baixe o **Temurin 17 (LTS)** para **Windows x64** (instalador `.msi`).
3. Execute o instalador e, na tela de opções, marque **"Set JAVA_HOME variable"** e
   **"Add to PATH"** (se disponíveis).

### 2) Maven

O Maven **não está no winget**, então a instalação é manual:

1. Acesse o site oficial: <https://maven.apache.org/download.cgi>
2. Baixe o arquivo **"Binary zip archive"** (ex.: `apache-maven-3.9.9-bin.zip`).
3. Extraia o conteúdo para uma pasta de sua preferência, por exemplo `C:\Users\seu-usuario\tools\`.
   Você ficará com algo como `C:\Users\seu-usuario\tools\apache-maven-3.9.9`.

   Em PowerShell, é possível baixar e extrair automaticamente:

   ```powershell
   $tools = "$env:USERPROFILE\tools"
   New-Item -ItemType Directory -Force $tools | Out-Null
   $url = "https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip"
   Invoke-WebRequest -Uri $url -OutFile "$tools\maven.zip"
   Expand-Archive -Path "$tools\maven.zip" -DestinationPath $tools -Force
   Remove-Item "$tools\maven.zip"
   ```

### 3) Configurar as variáveis de ambiente JAVA_HOME e MAVEN_HOME

Em PowerShell (ajuste os caminhos se necessário):

```powershell
[Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot", "User")
[Environment]::SetEnvironmentVariable("MAVEN_HOME", "$env:USERPROFILE\tools\apache-maven-3.9.9", "User")

$userPath = [Environment]::GetEnvironmentVariable("Path", "User")
$userPath = "$userPath;$env:JAVA_HOME\bin;$env:MAVEN_HOME\bin"
[Environment]::SetEnvironmentVariable("Path", $userPath, "User")
```

> Alternativa pela interface gráfica: menu Iniciar → "Editar as variáveis de ambiente do sistema" →
> botão **Variáveis de Ambiente** → em **Variáveis de usuário**, crie `JAVA_HOME` e `MAVEN_HOME` e
> edite o `Path` adicionando `%JAVA_HOME%\bin` e `%MAVEN_HOME%\bin`.

### 4) Conferir

**Feche e abra um novo PowerShell** e rode novamente:

```powershell
java -version
mvn -version
```

---

## Voltar e rodar o projeto

Depois que `java -version` e `mvn -version` funcionarem, abra um **novo** terminal e execute:

```powershell
cd "c:\Users\rafac\Downloads\Arquitetura de Software\arch.IA\adr-mcp-tools"

# 1) Configure sua chave da OpenAI (apenas uma vez)
Copy-Item .env.example .env
notepad .env        # substitua "sua_chave_aqui" pela sua chave real e salve

# 2) Compile
mvn package

# 3) Rode o cliente (ele sobe o servidor automaticamente e executa os 2 prompts)
java -jar target/adr-mcp-tools.jar cliente
```

Para rodar apenas o servidor MCP isoladamente:

```powershell
java -jar target/adr-mcp-tools.jar servidor
```

> Dica: se os acentos aparecerem errados no terminal, rode `chcp 65001` antes (ativa UTF-8) ou use
> o **Windows Terminal**.
