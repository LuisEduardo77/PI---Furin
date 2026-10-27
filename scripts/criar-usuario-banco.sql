-- Furin Access: usuário do banco exclusivo do projeto, sem root (T-03).
-- Cria os bancos do protótipo (furin) e dos testes (furin_test, o mesmo nome da CI)
-- e o usuário furin, com permissão só nesses dois bancos.
--
-- A senha é GERADA pelo MySQL e aparece UMA ÚNICA VEZ na tela, na coluna
-- "generated password". Ela não fica neste arquivo nem no histórico de comandos.
-- Guarde-a fora do repositório (gerenciador de senhas; depois, application-local.yml
-- ou SPRING_DATASOURCE_PASSWORD).
--
-- Rodar uma vez por computador, na raiz do repositório, com um usuário administrador:
--   mysql -u root -p --table -e "source scripts/criar-usuario-banco.sql"
--
-- Se o usuário já existir, o CREATE USER falha (erro 1396) e nada é sobrescrito.
-- Para gerar uma senha nova:  ALTER USER 'furin'@'localhost' IDENTIFIED BY RANDOM PASSWORD;

CREATE DATABASE IF NOT EXISTS furin      CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS furin_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER 'furin'@'localhost' IDENTIFIED BY RANDOM PASSWORD;

-- Dados (SELECT a DELETE) e estrutura para as migrações Flyway (CREATE a REFERENCES).
-- Sem privilégios globais, sem GRANT OPTION e sem acesso a outros bancos.
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES
  ON furin.* TO 'furin'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES
  ON furin_test.* TO 'furin'@'localhost';

SHOW GRANTS FOR 'furin'@'localhost';
