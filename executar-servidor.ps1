chcp 65001 > $null
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

$jar = Join-Path $PSScriptRoot "target\adr-mcp-tools.jar"

if (-not (Test-Path $jar)) {
    Write-Host "JAR nao encontrado. Compilando o projeto..." -ForegroundColor Yellow
    Push-Location $PSScriptRoot
    mvn -q package
    $buildOk = $?
    Pop-Location
    if (-not $buildOk -or -not (Test-Path $jar)) {
        Write-Host "Falha ao compilar o projeto." -ForegroundColor Red
        exit 1
    }
}

java -jar $jar servidor
