-- DEC-46 (RN05): uma liberação AGUARDANDO_GIRO por vez no ponto de leitura. O MVP tem
-- um ponto só, então a coluna vale 1 em toda liberação pendente e NULL no resto, e a
-- UNIQUE impede a segunda, como a uk_registro_pendente faz por vínculo. O serviço
-- (Leituras) confere antes; a restrição cobre duas leituras de alunos diferentes ao
-- mesmo tempo, que o bloqueio de cada aluno (RN08) não serializa.
--
-- Uma liberação pendente dura no máximo o prazo do giro (15 s). As que sobraram de
-- antes desta migração já passaram dele, então são canceladas como SEM_GIRO: sem isso,
-- duas pendentes antigas impediriam a criação do índice.
UPDATE registro_acesso
   SET situacao = 'CANCELADA', cancelado_em = UTC_TIMESTAMP(6), motivo_cancelamento = 'SEM_GIRO'
 WHERE situacao = 'AGUARDANDO_GIRO';

ALTER TABLE registro_acesso
  ADD COLUMN pendente_ponto TINYINT UNSIGNED
      GENERATED ALWAYS AS (CASE WHEN situacao = 'AGUARDANDO_GIRO' THEN 1 END) STORED AFTER pendente_vinculo,
  ADD UNIQUE KEY uk_registro_pendente_ponto (pendente_ponto);
