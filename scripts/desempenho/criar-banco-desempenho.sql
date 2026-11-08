-- Banco separado para a medição do RNF02 (T-33), para a carga de 800 alunos não misturar
-- com os dados do protótipo. Dá ao usuário furin (scripts/criar-usuario-banco.sql) as
-- mesmas permissões que ele tem nos outros dois bancos.
--
-- Rodar uma vez, na raiz do repositório, com um usuário administrador:
--   mysql -u root -p -e "source scripts/desempenho/criar-banco-desempenho.sql"

CREATE DATABASE IF NOT EXISTS furin_desempenho CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES
  ON furin_desempenho.* TO 'furin'@'localhost';
