-- Furin Access: usuário do banco SÓ DE LEITURA, usado pelos scripts de backup (T-47, DEC-30).
-- Ele lê o banco furin e não consegue alterar nada.
--
-- A senha é GERADA pelo MySQL e aparece UMA ÚNICA VEZ na tela, na coluna
-- "generated password". Ela não fica neste arquivo nem no histórico de comandos.
-- Guarde-a fora do repositório. Os scripts a leem da variável FURIN_BACKUP_DB_PASSWORD.
--
-- Rodar uma vez por computador, na raiz do repositório, com um usuário administrador:
--   mysql -u root -p --table -e "source scripts/criar-usuario-backup.sql"
--
-- Se o usuário já existir, o CREATE USER falha (erro 1396) e nada é sobrescrito.
-- Para gerar uma senha nova:  ALTER USER 'furin_backup'@'localhost' IDENTIFIED BY RANDOM PASSWORD;

CREATE USER 'furin_backup'@'localhost' IDENTIFIED BY RANDOM PASSWORD;

-- O mínimo para o mysqldump com --single-transaction e --no-tablespaces:
-- SELECT (dados), SHOW VIEW (visões) e TRIGGER (gatilhos). Sem INSERT, UPDATE, DELETE,
-- sem LOCK TABLES, sem privilégios globais e sem acesso a outros bancos.
GRANT SELECT, SHOW VIEW, TRIGGER ON furin.* TO 'furin_backup'@'localhost';

SHOW GRANTS FOR 'furin_backup'@'localhost';
