<#
.SYNOPSIS
  Backup do protótipo Furin Access (T-47, DEC-30; arquitetura §6.1).

.DESCRIPTION
  Gera, dentro de FURIN_BACKUP_DIR, uma pasta furin-backup-AAAAMMDD-HHMMSS com:
    - furin.sql     : mysqldump do banco do projeto;
    - arquivos.zip  : a pasta privada de fotos e termos (furin.arquivos.diretorio).
  Depois mantém só as cópias mais recentes (7, por padrão) e apaga as mais antigas.

  Variáveis de ambiente:
    FURIN_BACKUP_DIR          (obrigatória) pasta que já existe, FORA do repositório
    FURIN_BACKUP_DB_PASSWORD  senha do usuário só de leitura; sem ela, o script pergunta a
                              senha sem mostrá-la na tela
    FURIN_BACKUP_DB_USER      usuário do banco (padrão: furin_backup)
    FURIN_BACKUP_DB_HOST      servidor (padrão: localhost)
    FURIN_BACKUP_DB_PORT      porta (padrão: 3306)
    FURIN_BACKUP_DB_NAME      banco (padrão: furin)
    FURIN_ARQUIVOS_DIRETORIO  pasta de fotos e termos (padrão: %USERPROFILE%\furin-access\arquivos,
                              o mesmo da aplicação)
    FURIN_BACKUP_COPIAS       quantas cópias manter (padrão: 7)
    FURIN_MYSQLDUMP           caminho do mysqldump.exe, se ele não estiver no PATH

  A senha nunca aparece no script nem na linha de comando do mysqldump: ela vai para um
  arquivo de opções temporário, só do usuário, apagado no fim.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\backup.ps1
#>

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Sair-ComErro([string] $mensagem) {
    Write-Host "ERRO: $mensagem" -ForegroundColor Red
    exit 1
}

function Valor-OuPadrao([string] $nome, [string] $padrao) {
    $valor = [Environment]::GetEnvironmentVariable($nome)
    if ([string]::IsNullOrWhiteSpace($valor)) { return $padrao }
    return $valor
}

function Caminho-Completo([string] $caminho) {
    return [IO.Path]::GetFullPath($caminho).TrimEnd('\', '/')
}

# Verdadeiro se $filho é $pai ou está dentro dele (sem diferenciar maiúsculas, como o Windows)
function Esta-Dentro([string] $filho, [string] $pai) {
    $cmp = [StringComparison]::OrdinalIgnoreCase
    return $filho.Equals($pai, $cmp) -or $filho.StartsWith($pai + '\', $cmp)
}

# --- Configuração ------------------------------------------------------------------
$repositorio = Caminho-Completo (Join-Path $PSScriptRoot '..')

$pastaBackup = [Environment]::GetEnvironmentVariable('FURIN_BACKUP_DIR')
if ([string]::IsNullOrWhiteSpace($pastaBackup)) {
    Sair-ComErro 'defina FURIN_BACKUP_DIR com uma pasta fora do projeto.'
}
if (-not (Test-Path -LiteralPath $pastaBackup -PathType Container)) {
    Sair-ComErro "a pasta de FURIN_BACKUP_DIR não existe: $pastaBackup. Crie-a antes."
}
$pastaBackup = Caminho-Completo (Resolve-Path -LiteralPath $pastaBackup).ProviderPath

if (Esta-Dentro $pastaBackup $repositorio) {
    Sair-ComErro "FURIN_BACKUP_DIR ($pastaBackup) está dentro do repositório ($repositorio). O backup tem dados pessoais e nunca pode ficar no projeto nem no Git. Use uma pasta fora dele."
}

$senha = [Environment]::GetEnvironmentVariable('FURIN_BACKUP_DB_PASSWORD')
if ([string]::IsNullOrEmpty($senha) -and [Environment]::UserInteractive) {
    # Sem a variável, pergunta sem mostrar o que é digitado (nada fica no histórico)
    try {
        $seguro = Read-Host -AsSecureString 'Senha do furin_backup (a generated password, não a do root)'
        $senha = (New-Object Management.Automation.PSCredential 'x', $seguro).GetNetworkCredential().Password
    } catch {
        $senha = ''   # sem terminal (por exemplo, no Agendador de Tarefas): cai no erro abaixo
    }
}
if ([string]::IsNullOrEmpty($senha)) {
    Sair-ComErro 'defina FURIN_BACKUP_DB_PASSWORD com a senha do usuário só de leitura (scripts/criar-usuario-backup.sql).'
}

$usuario = Valor-OuPadrao 'FURIN_BACKUP_DB_USER' 'furin_backup'
$servidor = Valor-OuPadrao 'FURIN_BACKUP_DB_HOST' 'localhost'
$porta = Valor-OuPadrao 'FURIN_BACKUP_DB_PORT' '3306'
$banco = Valor-OuPadrao 'FURIN_BACKUP_DB_NAME' 'furin'
$pastaArquivos = Caminho-Completo (Valor-OuPadrao 'FURIN_ARQUIVOS_DIRETORIO' (Join-Path $env:USERPROFILE 'furin-access\arquivos'))

$copias = 0
if (-not [int]::TryParse((Valor-OuPadrao 'FURIN_BACKUP_COPIAS' '7'), [ref] $copias) -or $copias -lt 1) {
    Sair-ComErro 'FURIN_BACKUP_COPIAS precisa ser um número inteiro maior que zero.'
}

if (Esta-Dentro $pastaBackup $pastaArquivos) {
    Sair-ComErro "FURIN_BACKUP_DIR ($pastaBackup) está dentro da pasta de fotos e termos ($pastaArquivos)."
}

$mysqldump = Valor-OuPadrao 'FURIN_MYSQLDUMP' ''
if ($mysqldump -eq '') {
    $noPath = Get-Command 'mysqldump' -ErrorAction SilentlyContinue
    $instalado = Join-Path $env:ProgramFiles 'MySQL\MySQL Server 8.4\bin\mysqldump.exe'
    if ($noPath) { $mysqldump = $noPath.Source }
    elseif (Test-Path -LiteralPath $instalado) { $mysqldump = $instalado }
    else { Sair-ComErro 'mysqldump não encontrado. Ponha a pasta bin do MySQL no PATH ou defina FURIN_MYSQLDUMP.' }
}

# --- Pasta desta cópia -------------------------------------------------------------
$nome = 'furin-backup-' + (Get-Date -Format 'yyyyMMdd-HHmmss')
$destino = Join-Path $pastaBackup $nome
if (Test-Path -LiteralPath $destino) { Sair-ComErro "a pasta $destino já existe. Espere um segundo e rode de novo." }
New-Item -ItemType Directory -Path $destino | Out-Null

$opcoes = Join-Path ([IO.Path]::GetTempPath()) ('furin-backup-' + [Guid]::NewGuid().ToString('N') + '.cnf')
$sucesso = $false
try {
    # Arquivo de opções do cliente MySQL, na pasta temporária do usuário (privada no Windows)
    $senhaEscapada = $senha.Replace('\', '\\').Replace('"', '\"')
    $conteudo = "[client]`nuser=`"$usuario`"`npassword=`"$senhaEscapada`"`nhost=`"$servidor`"`nport=$porta`n"
    [IO.File]::WriteAllText($opcoes, $conteudo, (New-Object Text.UTF8Encoding $false))

    # --- 1. Banco ----------------------------------------------------------------------
    # Sem --databases: o .sql pode ser carregado em qualquer banco vazio (restauração).
    $sql = Join-Path $destino 'furin.sql'
    Write-Host "Copiando o banco '$banco' com o usuário '$usuario'..."
    & $mysqldump "--defaults-extra-file=$opcoes" `
        '--single-transaction' '--no-tablespaces' '--set-gtid-purged=OFF' `
        '--triggers' '--hex-blob' '--default-character-set=utf8mb4' `
        "--result-file=$sql" $banco
    if ($LASTEXITCODE -ne 0) {
        Sair-ComErro "o mysqldump terminou com o código $LASTEXITCODE. Se apareceu 'Access denied', confira FURIN_BACKUP_DB_PASSWORD: é a 'generated password' do usuário '$usuario' (scripts/criar-usuario-backup.sql), não a senha do root."
    }

    # --- 2. Fotos e termos -------------------------------------------------------------
    # Entrada por entrada, com "/" nos nomes: o ZipFile do Windows PowerShell 5.1 grava "\",
    # e o .zip não abriria direito no Linux/macOS
    Add-Type -AssemblyName System.IO.Compression, System.IO.Compression.FileSystem
    $zip = Join-Path $destino 'arquivos.zip'
    $existe = Test-Path -LiteralPath $pastaArquivos -PathType Container
    if ($existe) {
        Write-Host "Compactando $pastaArquivos..."
    } else {
        Write-Host "AVISO: a pasta de fotos e termos não existe ($pastaArquivos). O arquivos.zip fica vazio." -ForegroundColor Yellow
    }
    $arquivoZip = [IO.Compression.ZipFile]::Open($zip, [IO.Compression.ZipArchiveMode]::Create)
    try {
        if ($existe) {
            foreach ($arquivo in Get-ChildItem -LiteralPath $pastaArquivos -Recurse -File -Force) {
                $relativo = $arquivo.FullName.Substring($pastaArquivos.Length + 1).Replace('\', '/')
                [void] [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($arquivoZip, $arquivo.FullName, $relativo)
            }
        }
    }
    finally { $arquivoZip.Dispose() }
    $sucesso = $true
}
finally {
    if (Test-Path -LiteralPath $opcoes) { Remove-Item -LiteralPath $opcoes -Force }
    if (-not $sucesso -and (Test-Path -LiteralPath $destino)) {
        # Cópia incompleta não conta como cópia: apaga para não entrar no rodízio
        Remove-Item -LiteralPath $destino -Recurse -Force
    }
}

# --- 3. Rodízio: só as N mais recentes ---------------------------------------------
# Só pastas com o nome exato que este script cria; nada mais em FURIN_BACKUP_DIR é tocado.
$antigas = @(Get-ChildItem -LiteralPath $pastaBackup -Directory |
    Where-Object { $_.Name -match '^furin-backup-\d{8}-\d{6}$' } |
    Sort-Object Name -Descending |
    Select-Object -Skip $copias)
foreach ($pasta in $antigas) {
    Write-Host "Apagando a cópia antiga $($pasta.Name)"
    Remove-Item -LiteralPath $pasta.FullName -Recurse -Force
}

Write-Host "Backup concluído: $destino"
