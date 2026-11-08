-- Carga de referência da medição do RNF02 (T-33; DEC-28, G4; CA-RNF02-02):
-- 800 alunos fictícios, cada um com turma, carteirinha válida e 30 dias de movimentações
-- confirmadas (uma ENTRADA e uma SAIDA por dia), num total de 48 000 registros.
--
-- NÃO faz parte da carga padrão: não é migração do Flyway e não roda sozinho. Use só num
-- banco de teste ou de demonstração, nunca num banco com dados reais:
--
--   mysql -u furin -p furin_desempenho < scripts/desempenho/carga-800-alunos.sql
--
-- O banco já precisa ter as migrações (suba a aplicação uma vez apontando para ele). Para
-- tirar a carga depois, use apagar-carga-800-alunos.sql.
--
-- Só dados fictícios (A10 §8.33): "Aluno Desempenho 001", matrículas DES0001 a DES0800,
-- turmas DES-01 a DES-20. O código da carteirinha é o MD5 de "furin-rnf02-" + matrícula
-- (32 caracteres hexadecimais, A14 p.6), para a medição saber quais códigos ler. As fotos
-- não existem: a tela mostra "Foto indisponível" (DEC-28, A9), o que não muda a medição,
-- porque o texto aparece antes da foto (CA-RNF02-03).
--
-- Os horários são gravados em UTC (RN10): entrada entre 07:00 e 07:49 e saída às 12:30 no
-- horário de Brasília (UTC-3), nos 30 dias anteriores ao de hoje. Nenhum registro é de
-- hoje, então a próxima leitura de cada aluno é uma ENTRADA (RN01).

SET @agora := UTC_TIMESTAMP(6);
SET @antes := @agora - INTERVAL 60 DAY;

-- Quem lançou as movimentações da carga. A conta fica inativa (não entra no sistema), e a
-- senha é um valor aleatório gerado na hora, que ninguém conhece (CLAUDE.md §4)
INSERT INTO usuario (nome, login, senha_hash, perfil, ativo, troca_senha_obrigatoria, senha_temporaria_expira_em,
                     criado_em)
VALUES ('Carga de Desempenho', 'desempenho.carga', CONCAT('{noop}', UUID(), UUID()), 'FUNCIONARIO', 0, 0, NULL,
        @antes);
SET @usuario := LAST_INSERT_ID();

INSERT INTO turma (nome, ano_letivo, turno, ativa)
WITH RECURSIVE n (i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < 20)
SELECT CONCAT('DES-', LPAD(i, 2, '0')), YEAR(@agora), 'MANHA', 1 FROM n;

INSERT INTO aluno (matricula, nome, foto_chave, ativo, criado_em)
WITH RECURSIVE n (i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < 800)
SELECT CONCAT('DES', LPAD(i, 4, '0')), CONCAT('Aluno Desempenho ', LPAD(i, 3, '0')), CONCAT(UUID(), '.png'), 1, @antes
FROM n;

-- 40 alunos por turma
INSERT INTO vinculo_turma (id_aluno, id_turma, inicio_em, fim_em)
SELECT a.id_aluno, t.id_turma, @antes, NULL
FROM aluno a
JOIN turma t ON t.ano_letivo = YEAR(@agora) AND t.turno = 'MANHA'
 AND t.nome = CONCAT('DES-', LPAD(MOD(CAST(SUBSTRING(a.matricula, 4) AS UNSIGNED) - 1, 20) + 1, 2, '0'))
WHERE a.matricula LIKE 'DES____';

INSERT INTO carteirinha (id_aluno, codigo, emitida_em, expira_em, revogada_em)
SELECT a.id_aluno, MD5(CONCAT('furin-rnf02-', a.matricula)), @antes, NULL, NULL
FROM aluno a
WHERE a.matricula LIKE 'DES____';

-- 30 dias × 800 alunos × (ENTRADA + SAIDA), como se cada uma tivesse passado pelo giro
INSERT INTO registro_acesso (id_vinculo, id_usuario, id_carteirinha, tipo, forma, situacao, lancado_em, ocorrido_em,
                             chave_requisicao)
WITH RECURSIVE dia (d) AS (SELECT 1 UNION ALL SELECT d + 1 FROM dia WHERE d < 30),
movimento (tipo, hora) AS (SELECT 'ENTRADA', TIME '10:00:00' UNION ALL SELECT 'SAIDA', TIME '15:30:00'),
horario AS (
  SELECT v.id_vinculo, c.id_carteirinha, m.tipo,
         TIMESTAMP(DATE(@agora - INTERVAL dia.d DAY), m.hora)
           + INTERVAL IF(m.tipo = 'ENTRADA', MOD(a.id_aluno, 50), 0) MINUTE AS quando
  FROM aluno a
  JOIN vinculo_turma v ON v.id_aluno = a.id_aluno
  JOIN carteirinha c ON c.id_aluno = a.id_aluno
  CROSS JOIN dia
  CROSS JOIN movimento m
  WHERE a.matricula LIKE 'DES____'
)
SELECT id_vinculo, @usuario, id_carteirinha, tipo, 'LEITURA', 'CONFIRMADA', quando, quando, UUID()
FROM horario;
