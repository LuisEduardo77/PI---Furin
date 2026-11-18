-- DEC-47: aceite do Termo de uso e da Política de privacidade pelo funcionário, no primeiro
-- acesso. Fica a última versão aceita e quando; cada aceite também vai para o log de
-- auditoria (TERMOS_ACEITOS), com a versão. Conta sem aceite (NULL) ou com uma versão
-- diferente de furin.termos.versao-atual precisa aceitar antes de usar o sistema.
ALTER TABLE usuario
  ADD COLUMN termos_versao_aceita VARCHAR(30) NULL AFTER senha_temporaria_expira_em,
  ADD COLUMN termos_aceitos_em    DATETIME(6) NULL AFTER termos_versao_aceita,
  ADD CONSTRAINT ck_usuario_termos CHECK (
    (termos_versao_aceita IS NULL AND termos_aceitos_em IS NULL)
    OR (TRIM(termos_versao_aceita) <> '' AND termos_aceitos_em IS NOT NULL));
