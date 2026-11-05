-- DEC-41 (RN08): o giro também leva uma chave própria. A chave_requisicao da mesma linha
-- é a da leitura; esta é a do giro que confirmou a liberação. Um reenvio com a mesma
-- chave devolve o resultado original. Fica NULL até o giro e nos registros manuais.
ALTER TABLE registro_acesso
  ADD COLUMN chave_giro CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER chave_requisicao,
  ADD UNIQUE KEY uk_registro_chave_giro (chave_giro);
