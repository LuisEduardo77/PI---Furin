-- Tira a carga de referência do RNF02 (T-33), gravada por carga-800-alunos.sql.
--
-- Use só no mesmo banco de teste ou de demonstração. Os dados da carga são fictícios e
-- existem só para a medição, por isso aqui eles são apagados de verdade; a regra de nunca
-- apagar (inativação preserva o histórico) vale para os dados do sistema, não para esta
-- carga.
--
--   mysql -u furin -p furin_desempenho < scripts/desempenho/apagar-carga-800-alunos.sql

DELETE r FROM registro_acesso r
JOIN vinculo_turma v ON v.id_vinculo = r.id_vinculo
JOIN aluno a ON a.id_aluno = v.id_aluno
WHERE a.matricula LIKE 'DES____';

DELETE c FROM carteirinha c JOIN aluno a ON a.id_aluno = c.id_aluno WHERE a.matricula LIKE 'DES____';

DELETE v FROM vinculo_turma v JOIN aluno a ON a.id_aluno = v.id_aluno WHERE a.matricula LIKE 'DES____';

DELETE FROM aluno WHERE matricula LIKE 'DES____';

DELETE FROM turma WHERE nome LIKE 'DES-__' AND turno = 'MANHA';

DELETE FROM usuario WHERE login = 'desempenho.carga';
