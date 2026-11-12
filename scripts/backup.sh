#!/usr/bin/env bash
# Backup do protótipo Furin Access (T-47, DEC-30; arquitetura §6.1). Linux e macOS.
#
# Gera, dentro de FURIN_BACKUP_DIR, uma pasta furin-backup-AAAAMMDD-HHMMSS com:
#   - furin.sql     : mysqldump do banco do projeto;
#   - arquivos.zip  : a pasta privada de fotos e termos (furin.arquivos.diretorio).
# Depois mantém só as cópias mais recentes (7, por padrão) e apaga as mais antigas.
#
# Variáveis de ambiente:
#   FURIN_BACKUP_DIR          (obrigatória) pasta que já existe, FORA do repositório
#   FURIN_BACKUP_DB_PASSWORD  senha do usuário só de leitura; sem ela, num terminal, o
#                             script pergunta a senha sem mostrá-la na tela
#   FURIN_BACKUP_DB_USER      usuário do banco (padrão: furin_backup)
#   FURIN_BACKUP_DB_HOST      servidor (padrão: localhost)
#   FURIN_BACKUP_DB_PORT      porta (padrão: 3306)
#   FURIN_BACKUP_DB_NAME      banco (padrão: furin)
#   FURIN_ARQUIVOS_DIRETORIO  pasta de fotos e termos (padrão: ~/furin-access/arquivos,
#                             o mesmo da aplicação)
#   FURIN_BACKUP_COPIAS       quantas cópias manter (padrão: 7)
#   FURIN_MYSQLDUMP           caminho do mysqldump, se ele não estiver no PATH
#
# A senha nunca aparece no script nem na linha de comando do mysqldump: ela vai para um
# arquivo de opções temporário, com permissão só do usuário (600), apagado no fim.
#
# Uso: bash scripts/backup.sh

set -euo pipefail
umask 077

erro() {
  echo "ERRO: $1" >&2
  exit 1
}

# Caminho absoluto e sem links simbólicos de uma pasta que existe
pasta_real() {
  (cd "$1" && pwd -P)
}

# Verdadeiro se $1 é $2 ou está dentro dele
esta_dentro() {
  [ "$1" = "$2" ] || case "$1" in "$2"/*) true ;; *) false ;; esac
}

# --- Configuração ------------------------------------------------------------------
repositorio="$(pasta_real "$(dirname "${BASH_SOURCE[0]}")/..")"

[ -n "${FURIN_BACKUP_DIR:-}" ] || erro "defina FURIN_BACKUP_DIR com uma pasta fora do projeto."
[ -d "$FURIN_BACKUP_DIR" ] || erro "a pasta de FURIN_BACKUP_DIR não existe: $FURIN_BACKUP_DIR. Crie-a antes."
pasta_backup="$(pasta_real "$FURIN_BACKUP_DIR")"

if esta_dentro "$pasta_backup" "$repositorio"; then
  erro "FURIN_BACKUP_DIR ($pasta_backup) está dentro do repositório ($repositorio). O backup tem dados pessoais e nunca pode ficar no projeto nem no Git. Use uma pasta fora dele."
fi

if [ -z "${FURIN_BACKUP_DB_PASSWORD:-}" ] && [ -t 0 ]; then
  # Sem a variável, pergunta sem mostrar o que é digitado (nada fica no histórico)
  read -rsp 'Senha do furin_backup (a generated password, não a do root): ' FURIN_BACKUP_DB_PASSWORD
  echo
fi
[ -n "${FURIN_BACKUP_DB_PASSWORD:-}" ] \
  || erro "defina FURIN_BACKUP_DB_PASSWORD com a senha do usuário só de leitura (scripts/criar-usuario-backup.sql)."

usuario="${FURIN_BACKUP_DB_USER:-furin_backup}"
servidor="${FURIN_BACKUP_DB_HOST:-localhost}"
porta="${FURIN_BACKUP_DB_PORT:-3306}"
banco="${FURIN_BACKUP_DB_NAME:-furin}"
pasta_arquivos="${FURIN_ARQUIVOS_DIRETORIO:-$HOME/furin-access/arquivos}"
copias="${FURIN_BACKUP_COPIAS:-7}"

case "$copias" in
  '' | *[!0-9]*) erro "FURIN_BACKUP_COPIAS precisa ser um número inteiro maior que zero." ;;
esac
[ "$copias" -ge 1 ] || erro "FURIN_BACKUP_COPIAS precisa ser um número inteiro maior que zero."

if [ -d "$pasta_arquivos" ]; then
  pasta_arquivos="$(pasta_real "$pasta_arquivos")"
  if esta_dentro "$pasta_backup" "$pasta_arquivos"; then
    erro "FURIN_BACKUP_DIR ($pasta_backup) está dentro da pasta de fotos e termos ($pasta_arquivos)."
  fi
fi

mysqldump="${FURIN_MYSQLDUMP:-$(command -v mysqldump || true)}"
[ -n "$mysqldump" ] || erro "mysqldump não encontrado. Ponha a pasta bin do MySQL no PATH ou defina FURIN_MYSQLDUMP."
command -v zip > /dev/null || erro "o programa zip não está instalado (no Ubuntu: sudo apt install zip)."

# --- Pasta desta cópia -------------------------------------------------------------
nome="furin-backup-$(date +%Y%m%d-%H%M%S)"
destino="$pasta_backup/$nome"
[ ! -e "$destino" ] || erro "a pasta $destino já existe. Espere um segundo e rode de novo."

opcoes=""
sucesso=0
limpar() {
  if [ -n "$opcoes" ]; then rm -f "$opcoes"; fi
  # Cópia incompleta não conta como cópia: apaga para não entrar no rodízio
  if [ "$sucesso" -ne 1 ]; then rm -rf "$destino"; fi
}
trap limpar EXIT
mkdir "$destino"
opcoes="$(mktemp "${TMPDIR:-/tmp}/furin-backup-XXXXXX")"

# Arquivo de opções do cliente MySQL (permissão 600 por causa do umask 077)
senha_escapada="$(printf '%s' "$FURIN_BACKUP_DB_PASSWORD" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g')"
{
  echo "[client]"
  echo "user=\"$usuario\""
  echo "password=\"$senha_escapada\""
  echo "host=\"$servidor\""
  echo "port=$porta"
} > "$opcoes"

# --- 1. Banco ----------------------------------------------------------------------
# Sem --databases: o .sql pode ser carregado em qualquer banco vazio (restauração).
echo "Copiando o banco '$banco' com o usuário '$usuario'..."
"$mysqldump" --defaults-extra-file="$opcoes" \
  --single-transaction --no-tablespaces --set-gtid-purged=OFF \
  --triggers --hex-blob --default-character-set=utf8mb4 \
  --result-file="$destino/furin.sql" "$banco" \
  || erro "o mysqldump falhou. Se apareceu 'Access denied', confira FURIN_BACKUP_DB_PASSWORD: é a 'generated password' do usuário '$usuario' (scripts/criar-usuario-backup.sql), não a senha do root."

# --- 2. Fotos e termos -------------------------------------------------------------
zip_destino="$destino/arquivos.zip"
if [ -d "$pasta_arquivos" ] && [ -n "$(ls -A "$pasta_arquivos")" ]; then
  echo "Compactando $pasta_arquivos..."
  (cd "$pasta_arquivos" && zip -qr "$zip_destino" .) || erro "não foi possível criar o arquivos.zip."
else
  echo "AVISO: a pasta de fotos e termos não existe ou está vazia ($pasta_arquivos). O arquivos.zip fica vazio." >&2
  # Um .zip vazio é só o registro de fim do diretório central (22 bytes)
  printf 'PK\005\006\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0' > "$zip_destino"
fi
sucesso=1

# --- 3. Rodízio: só as N mais recentes ---------------------------------------------
# Só pastas com o nome exato que este script cria; nada mais em FURIN_BACKUP_DIR é tocado.
# O nome tem data e hora fixas, então a ordem alfabética é a ordem do tempo.
find "$pasta_backup" -mindepth 1 -maxdepth 1 -type d -name 'furin-backup-*' -print \
  | sed 's|.*/||' \
  | { grep -E '^furin-backup-[0-9]{8}-[0-9]{6}$' || true; } \
  | sort -r \
  | tail -n +"$((copias + 1))" \
  | while IFS= read -r antiga; do
      echo "Apagando a cópia antiga $antiga"
      rm -rf "${pasta_backup:?}/$antiga"
    done

echo "Backup concluído: $destino"
