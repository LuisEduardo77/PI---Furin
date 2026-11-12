# Backup e restauração do protótipo

**Tarefa:** T-47 · **Decisão:** DEC-30 · **Arquitetura:** [§6.1](arquitetura-e-dados.md#61-backup-do-protótipo-dec-30)
· **Evidência do teste:** [`evidencias/backup-restauracao.md`](evidencias/backup-restauracao.md)

O backup tem duas partes, numa pasta `furin-backup-AAAAMMDD-HHMMSS`:

| Arquivo | O que é |
|---|---|
| `furin.sql` | `mysqldump` do banco `furin` (estrutura, dados e o histórico do Flyway) |
| `arquivos.zip` | A pasta privada de fotos e termos (`furin.arquivos.diretorio`) |

**O backup contém dados pessoais.** Ele fica só na pasta de `FURIN_BACKUP_DIR`, fora do
projeto, e nunca vai para o Git, para o Trello nem para o chat. Os scripts se recusam a
rodar se essa pasta estiver dentro do repositório.

Quando fazer (DEC-30): antes de cada demonstração e no fim de cada dia de testes.

## 1. Preparar (uma vez por computador)

1. **Usuário só de leitura.** Com o MySQL ligado, na pasta do projeto, com o `root`:

   ```bat
   mysql -u root -p --table -e "source scripts/criar-usuario-backup.sql"
   ```

   O script cria o usuário `furin_backup`, que só consegue ler o banco `furin` (`SELECT`,
   `SHOW VIEW` e `TRIGGER`). A coluna **`generated password`** mostra a senha dele uma
   única vez: guarde-a num lugar seguro, fora do projeto.

2. **Pasta dos backups**, fora do projeto. Por exemplo, `%USERPROFILE%\furin-backups` no
   Windows ou `~/furin-backups` no Linux/macOS. Crie a pasta antes: o script não a cria.

3. No Linux, o programa `zip` precisa estar instalado (no Ubuntu: `sudo apt install zip`).

## 2. Fazer um backup

**Windows (PowerShell),** na pasta do projeto:

```powershell
$env:FURIN_BACKUP_DIR = "$env:USERPROFILE\furin-backups"
powershell -ExecutionPolicy Bypass -File scripts\backup.ps1
```

**Linux/macOS (Terminal),** na pasta do projeto:

```bash
export FURIN_BACKUP_DIR="$HOME/furin-backups"
bash scripts/backup.sh
```

Rode **um comando por vez**. O script pergunta `Senha do furin_backup …`: só então cole
a **`generated password` do `furin_backup`** (passo 1 da seção 1, não a do `root`) com
Ctrl+V ou com o botão direito do mouse e tecle Enter. A senha não entra em nenhum
comando, não aparece na tela e não fica no histórico. Se der `Access denied`, a senha
está errada; confira-a com `mysql -u furin_backup -p -e "SELECT 'senha ok'"`. Sem
terminal (por exemplo, no Agendador de Tarefas, na implantação), defina antes a variável
`FURIN_BACKUP_DB_PASSWORD`. O script a
passa ao `mysqldump` por um arquivo de opções temporário, só do usuário, que é apagado
no fim; ela não aparece na linha de comando.

No fim aparece `Backup concluído: …` com a pasta criada. O script mantém as **7 cópias
mais recentes** e apaga as mais antigas. Ele só mexe em pastas com o nome
`furin-backup-AAAAMMDD-HHMMSS`; o resto da pasta não é tocado. Se algo falhar, a cópia
incompleta é apagada e o script termina com erro.

Variáveis opcionais (com o padrão entre parênteses): `FURIN_BACKUP_DB_USER`
(`furin_backup`), `FURIN_BACKUP_DB_HOST` (`localhost`), `FURIN_BACKUP_DB_PORT` (`3306`),
`FURIN_BACKUP_DB_NAME` (`furin`), `FURIN_ARQUIVOS_DIRETORIO` (a mesma pasta da aplicação,
`furin-access/arquivos` na pasta do usuário), `FURIN_BACKUP_COPIAS` (`7`) e
`FURIN_MYSQLDUMP` (caminho do `mysqldump`; no Windows, o script também procura em
`C:\Program Files\MySQL\MySQL Server 8.4\bin`).

## 3. Teste de restauração num banco vazio

O teste carrega o backup num banco **novo**, `furin_restaurado`, e compara com o `furin`.
O banco do protótipo não é alterado. Faça logo depois de um backup e **sem usar o
sistema no meio**, para os números baterem.

**Windows (PowerShell),** na pasta do projeto, com o MySQL no `PATH`:

Só siga se o backup terminou com `Backup concluído: …`. Se o passo 1 mostrar "Nenhum
backup encontrado", pare e refaça a seção 2.

```powershell
# 1. A cópia mais recente
$copia = Get-ChildItem $env:FURIN_BACKUP_DIR -Directory -Filter 'furin-backup-*' | Sort-Object Name | Select-Object -Last 1
if ($copia) { $copia.FullName } else { 'Nenhum backup encontrado: pare aqui' }

# 2. Banco vazio e carga do furin.sql (com o root)
mysql -u root -p -e "CREATE DATABASE furin_restaurado CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"
mysql -u root -p furin_restaurado -e ("source " + "$($copia.FullName)\furin.sql".Replace('\', '/'))

# 3. Conferir o banco: "igual" em todas as linhas e o mesmo checksum em cada par
mysql -u root -p --table -e "source scripts/conferir-restauracao.sql"

# 4. Conferir as fotos e os termos: nenhuma linha no Compare-Object = tudo igual
$orig = "$env:USERPROFILE\furin-access\arquivos"
$rest = "$env:TEMP\furin-arquivos-restaurados"
Expand-Archive -LiteralPath "$($copia.FullName)\arquivos.zip" -DestinationPath $rest
function Lista($p) { Get-ChildItem $p -Recurse -File | ForEach-Object { $_.FullName.Substring($p.Length) + ' ' + (Get-FileHash $_.FullName).Hash } }
"Arquivos: original $(@(Lista $orig).Count), restaurado $(@(Lista $rest).Count)"
Compare-Object @(Lista $orig) @(Lista $rest)

# 5. Limpar o teste
mysql -u root -p -e "DROP DATABASE furin_restaurado"
Remove-Item -Recurse -Force $rest
```

**Linux/macOS (Terminal),** na pasta do projeto:

```bash
# 1. A cópia mais recente
copia="$(ls -d "$FURIN_BACKUP_DIR"/furin-backup-* | sort | tail -n 1)"; echo "$copia"

# 2. Banco vazio e carga do furin.sql (com o root)
mysql -u root -p -e "CREATE DATABASE furin_restaurado CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"
mysql -u root -p furin_restaurado < "$copia/furin.sql"

# 3. Conferir o banco
mysql -u root -p --table -e "source scripts/conferir-restauracao.sql"

# 4. Conferir as fotos e os termos: "iguais" no fim = tudo igual
rest="$(mktemp -d)"; unzip -q "$copia/arquivos.zip" -d "$rest"
diff -r "$HOME/furin-access/arquivos" "$rest" && echo "iguais"

# 5. Limpar o teste
mysql -u root -p -e "DROP DATABASE furin_restaurado"
rm -rf "$rest"
```

O primeiro teste, de 12/11/2026, está em
[`evidencias/backup-restauracao.md`](evidencias/backup-restauracao.md). Para um teste novo,
registre o resultado (data, quantidade de linhas por tabela, se os checksums bateram e
quantos arquivos) em [`evidencias/backup-restauracao.md`](evidencias/backup-restauracao.md)
e no cartão da T-47 no Trello, **sem** colar dados dos alunos.

## 4. Restaurar de verdade (perda do banco ou dos arquivos)

Use só quando o banco ou a pasta de arquivos se perderam. **Isso apaga o que está no
banco `furin` e põe no lugar o conteúdo do backup.**

1. Desligue o sistema (Ctrl+C na janela dele).
2. Recrie o banco vazio e carregue o backup (com o `root`):

   ```bat
   mysql -u root -p -e "DROP DATABASE IF EXISTS furin; CREATE DATABASE furin CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"
   ```

   Depois, carregue o `furin.sql` como no passo 2 da seção 3, trocando `furin_restaurado`
   por `furin`. O usuário `furin` continua com acesso: as permissões são por nome de banco.
3. Extraia o `arquivos.zip` para a pasta de fotos e termos (`%USERPROFILE%\furin-access\arquivos`
   ou `~/furin-access/arquivos`):

   ```powershell
   Expand-Archive -LiteralPath "$($copia.FullName)\arquivos.zip" -DestinationPath "$env:USERPROFILE\furin-access\arquivos" -Force
   ```

   No Linux/macOS: `unzip -o "$copia/arquivos.zip" -d ~/furin-access/arquivos`.
4. Suba o sistema (README §3.4). O Flyway encontra o histórico das migrações no banco
   restaurado e não roda a carga fictícia de novo; as contas e as senhas voltam a ser as
   do momento do backup.

## 5. Na implantação (fora do protótipo)

A DEC-30 prevê, numa implantação real: backup **diário** pelo Agendador de Tarefas ou
pelo `cron`, uma cópia também fora do computador do servidor e o `.zip` protegido por
senha. Nada disso faz parte do protótipo (plano, "Depois do MVP").
