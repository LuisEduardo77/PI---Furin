-- Furin Access: confere o teste de restauração do backup (T-47, DEC-30).
-- Compara o banco do protótipo (furin) com a cópia restaurada (furin_restaurado),
-- tabela por tabela. Passo a passo em docs/backup-e-restauracao.md.
--
-- Rodar na raiz do repositório, com um usuário administrador:
--   mysql -u root -p --table -e "source scripts/conferir-restauracao.sql"
--
-- Resultado esperado: "original" igual a "restaurado" em todas as linhas e, no
-- CHECKSUM TABLE, o mesmo número para cada par de tabelas. Se o sistema foi usado
-- depois do backup, as diferenças são os registros novos.

SELECT t.tabela, t.original, t.restaurado,
       IF(t.original = t.restaurado, 'igual', 'DIFERENTE') AS resultado
FROM (
  SELECT 'usuario' AS tabela,
         (SELECT COUNT(*) FROM furin.usuario) AS original,
         (SELECT COUNT(*) FROM furin_restaurado.usuario) AS restaurado
  UNION ALL SELECT 'turma',
         (SELECT COUNT(*) FROM furin.turma), (SELECT COUNT(*) FROM furin_restaurado.turma)
  UNION ALL SELECT 'aluno',
         (SELECT COUNT(*) FROM furin.aluno), (SELECT COUNT(*) FROM furin_restaurado.aluno)
  UNION ALL SELECT 'vinculo_turma',
         (SELECT COUNT(*) FROM furin.vinculo_turma), (SELECT COUNT(*) FROM furin_restaurado.vinculo_turma)
  UNION ALL SELECT 'carteirinha',
         (SELECT COUNT(*) FROM furin.carteirinha), (SELECT COUNT(*) FROM furin_restaurado.carteirinha)
  UNION ALL SELECT 'contato_responsavel',
         (SELECT COUNT(*) FROM furin.contato_responsavel), (SELECT COUNT(*) FROM furin_restaurado.contato_responsavel)
  UNION ALL SELECT 'consentimento',
         (SELECT COUNT(*) FROM furin.consentimento), (SELECT COUNT(*) FROM furin_restaurado.consentimento)
  UNION ALL SELECT 'registro_acesso',
         (SELECT COUNT(*) FROM furin.registro_acesso), (SELECT COUNT(*) FROM furin_restaurado.registro_acesso)
  UNION ALL SELECT 'notificacao',
         (SELECT COUNT(*) FROM furin.notificacao), (SELECT COUNT(*) FROM furin_restaurado.notificacao)
  UNION ALL SELECT 'flyway_schema_history',
         (SELECT COUNT(*) FROM furin.flyway_schema_history), (SELECT COUNT(*) FROM furin_restaurado.flyway_schema_history)
) AS t;

CHECKSUM TABLE
  furin.usuario, furin_restaurado.usuario,
  furin.aluno, furin_restaurado.aluno,
  furin.contato_responsavel, furin_restaurado.contato_responsavel,
  furin.registro_acesso, furin_restaurado.registro_acesso,
  furin.notificacao, furin_restaurado.notificacao;
