-- T-03, parte 2: testa CHECKs, UNIQUEs e FKs do DDL de docs/arquitetura-e-dados.md §5.5
-- com INSERTs válidos (esperado 0) e inválidos (esperado = código de erro do MySQL).
-- Códigos: 3819 = CHECK violado, 1062 = UNIQUE duplicado, 1452 = FK sem pai,
--          1048 = NOT NULL, 1406 = valor longo demais.
-- Só dados fictícios. Recria o banco furin_ddl_teste do zero.

-- Caminho relativo à raiz do repositório (de onde o mysql é chamado)
source docs/evidencias/t-03/validar-ddl.sql

-- Tabela de resultados e procedimento de teste (só existem neste banco descartável)
CREATE TABLE t03_resultado (
  n         INT AUTO_INCREMENT PRIMARY KEY,
  teste     VARCHAR(90) NOT NULL,
  esperado  INT NOT NULL,
  obtido    INT NOT NULL,
  situacao  VARCHAR(6) NOT NULL,
  mensagem  TEXT
);

DELIMITER //
CREATE PROCEDURE t(IN p_teste VARCHAR(90), IN p_esperado INT, IN p_sql TEXT)
BEGIN
  DECLARE v_codigo INT DEFAULT 0;
  DECLARE v_msg TEXT DEFAULT '';
  -- O handler cobre só o comando testado. Um erro ao gravar o resultado aparece na tela.
  BEGIN
    DECLARE CONTINUE HANDLER FOR SQLEXCEPTION
    BEGIN
      IF v_codigo = 0 THEN
        GET STACKED DIAGNOSTICS CONDITION 1 v_codigo = MYSQL_ERRNO, v_msg = MESSAGE_TEXT;
      END IF;
    END;
    SET @t03_sql = p_sql;
    PREPARE st FROM @t03_sql;
    IF v_codigo = 0 THEN EXECUTE st; END IF;
    DEALLOCATE PREPARE st;
  END;
  SET v_codigo = COALESCE(v_codigo, -1);
  INSERT INTO t03_resultado (teste, esperado, obtido, situacao, mensagem)
  VALUES (p_teste, p_esperado, v_codigo, IF(v_codigo = p_esperado, 'PASSOU', 'FALHOU'), v_msg);
END //
DELIMITER ;

-- ===== usuario =====
CALL t("usuario: ate.demo valido", 0, "INSERT INTO usuario (nome, login, senha_hash, perfil, criado_em) VALUES ('ATE Demo', 'ate.demo', 'hash-ficticio', 'FUNCIONARIO', '2026-10-27 12:00:00')");
CALL t("usuario: gestao.demo com senha temporaria valida", 0, "INSERT INTO usuario (nome, login, senha_hash, perfil, troca_senha_obrigatoria, senha_temporaria_expira_em, criado_em) VALUES ('Gestao Demo', 'gestao.demo', 'hash-ficticio', 'FUNCIONARIO', 1, '2026-10-30 12:00:00', '2026-10-27 12:00:00')");
CALL t("usuario: login duplicado", 1062, "INSERT INTO usuario (nome, login, senha_hash, perfil, criado_em) VALUES ('Outro Teste', 'ate.demo', 'hash-ficticio', 'FUNCIONARIO', '2026-10-27 12:00:00')");
CALL t("usuario: login duplicado em maiusculas", 1062, "INSERT INTO usuario (nome, login, senha_hash, perfil, criado_em) VALUES ('Outro Teste', 'ATE.DEMO', 'hash-ficticio', 'FUNCIONARIO', '2026-10-27 12:00:00')");
CALL t("usuario: nome em branco", 3819, "INSERT INTO usuario (nome, login, senha_hash, perfil, criado_em) VALUES ('   ', 'teste.u1', 'hash-ficticio', 'FUNCIONARIO', '2026-10-27 12:00:00')");
CALL t("usuario: login em branco", 3819, "INSERT INTO usuario (nome, login, senha_hash, perfil, criado_em) VALUES ('Outro Teste', '  ', 'hash-ficticio', 'FUNCIONARIO', '2026-10-27 12:00:00')");
CALL t("usuario: perfil ATE (DEC-37)", 3819, "INSERT INTO usuario (nome, login, senha_hash, perfil, criado_em) VALUES ('Outro Teste', 'teste.u2', 'hash-ficticio', 'ATE', '2026-10-27 12:00:00')");
CALL t("usuario: perfil GESTAO (DEC-37)", 3819, "INSERT INTO usuario (nome, login, senha_hash, perfil, criado_em) VALUES ('Outro Teste', 'teste.u3', 'hash-ficticio', 'GESTAO', '2026-10-27 12:00:00')");
CALL t("usuario: ativo = 2", 3819, "INSERT INTO usuario (nome, login, senha_hash, perfil, ativo, criado_em) VALUES ('Outro Teste', 'teste.u4', 'hash-ficticio', 'FUNCIONARIO', 2, '2026-10-27 12:00:00')");
CALL t("usuario: senha temporaria sem troca obrigatoria", 3819, "INSERT INTO usuario (nome, login, senha_hash, perfil, senha_temporaria_expira_em, criado_em) VALUES ('Outro Teste', 'teste.u5', 'hash-ficticio', 'FUNCIONARIO', '2026-10-30 12:00:00', '2026-10-27 12:00:00')");
SET @ate = (SELECT id_usuario FROM usuario WHERE login = 'ate.demo');

-- ===== turma =====
CALL t("turma: 6A valida", 0, "INSERT INTO turma (nome, ano_letivo, turno) VALUES ('6A', 2026, 'MANHA')");
CALL t("turma: 3B valida", 0, "INSERT INTO turma (nome, ano_letivo, turno) VALUES ('3B', 2026, 'MANHA')");
CALL t("turma: duplicada", 1062, "INSERT INTO turma (nome, ano_letivo, turno) VALUES ('6A', 2026, 'MANHA')");
CALL t("turma: turno invalido", 3819, "INSERT INTO turma (nome, ano_letivo, turno) VALUES ('7A', 2026, 'MADRUGADA')");
CALL t("turma: ano 1999", 3819, "INSERT INTO turma (nome, ano_letivo, turno) VALUES ('7A', 1999, 'MANHA')");
CALL t("turma: nome em branco", 3819, "INSERT INTO turma (nome, ano_letivo, turno) VALUES (' ', 2026, 'MANHA')");
SET @t6a = (SELECT id_turma FROM turma WHERE nome = '6A');
SET @t3b = (SELECT id_turma FROM turma WHERE nome = '3B');

-- ===== aluno =====
CALL t("aluno: Ana Teste valida", 0, "INSERT INTO aluno (matricula, nome, foto_chave, criado_em) VALUES ('000123', 'Ana Teste', 'foto-ficticia-1', '2026-10-27 12:00:00')");
CALL t("aluno: Davi Teste valido", 0, "INSERT INTO aluno (matricula, nome, foto_chave, criado_em) VALUES ('000124', 'Davi Teste', 'foto-ficticia-2', '2026-10-27 12:00:00')");
CALL t("aluno: matricula duplicada", 1062, "INSERT INTO aluno (matricula, nome, foto_chave, criado_em) VALUES ('000123', 'Outro Teste', 'foto-ficticia-3', '2026-10-27 12:00:00')");
CALL t("aluno: matricula em branco", 3819, "INSERT INTO aluno (matricula, nome, foto_chave, criado_em) VALUES ('  ', 'Outro Teste', 'foto-ficticia-3', '2026-10-27 12:00:00')");
CALL t("aluno: foto vazia", 3819, "INSERT INTO aluno (matricula, nome, foto_chave, criado_em) VALUES ('000900', 'Outro Teste', '', '2026-10-27 12:00:00')");
CALL t("aluno: foto NULL (DEC-05)", 1048, "INSERT INTO aluno (matricula, nome, foto_chave, criado_em) VALUES ('000901', 'Outro Teste', NULL, '2026-10-27 12:00:00')");
SET @ana  = (SELECT id_aluno FROM aluno WHERE matricula = '000123');
SET @davi = (SELECT id_aluno FROM aluno WHERE matricula = '000124');

-- ===== vinculo_turma =====
CALL t("vinculo: Ana no 6A valido", 0, "INSERT INTO vinculo_turma (id_aluno, id_turma, inicio_em) VALUES (@ana, @t6a, '2026-02-01 03:00:00')");
CALL t("vinculo: Davi no 3B valido", 0, "INSERT INTO vinculo_turma (id_aluno, id_turma, inicio_em) VALUES (@davi, @t3b, '2026-02-01 03:00:00')");
CALL t("vinculo: mesmo aluno e mesmo inicio", 1062, "INSERT INTO vinculo_turma (id_aluno, id_turma, inicio_em) VALUES (@ana, @t3b, '2026-02-01 03:00:00')");
CALL t("vinculo: fim igual ao inicio", 3819, "INSERT INTO vinculo_turma (id_aluno, id_turma, inicio_em, fim_em) VALUES (@ana, @t3b, '2026-03-01 03:00:00', '2026-03-01 03:00:00')");
CALL t("vinculo: aluno inexistente", 1452, "INSERT INTO vinculo_turma (id_aluno, id_turma, inicio_em) VALUES (999999, @t6a, '2026-02-01 03:00:00')");
CALL t("vinculo: turma inexistente", 1452, "INSERT INTO vinculo_turma (id_aluno, id_turma, inicio_em) VALUES (@ana, 999999, '2026-04-01 03:00:00')");
SET @v_ana  = (SELECT id_vinculo FROM vinculo_turma WHERE id_aluno = @ana);
SET @v_davi = (SELECT id_vinculo FROM vinculo_turma WHERE id_aluno = @davi);

-- ===== carteirinha =====
CALL t("carteirinha: Ana valida", 0, "INSERT INTO carteirinha (id_aluno, codigo, emitida_em) VALUES (@ana, 'a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6', '2026-02-01 03:00:00')");
CALL t("carteirinha: Davi valida", 0, "INSERT INTO carteirinha (id_aluno, codigo, emitida_em) VALUES (@davi, 'b1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6', '2026-02-01 03:00:00')");
CALL t("carteirinha: codigo duplicado", 1062, "INSERT INTO carteirinha (id_aluno, codigo, emitida_em) VALUES (@davi, 'a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6', '2026-02-01 03:00:00')");
CALL t("carteirinha: codigo com 33 caracteres", 1406, "INSERT INTO carteirinha (id_aluno, codigo, emitida_em) VALUES (@ana, 'c1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6X', '2026-02-01 03:00:00')");
CALL t("carteirinha: expira igual a emissao", 3819, "INSERT INTO carteirinha (id_aluno, codigo, emitida_em, expira_em) VALUES (@ana, 'd1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6', '2026-02-01 03:00:00', '2026-02-01 03:00:00')");
CALL t("carteirinha: revogada antes da emissao", 3819, "INSERT INTO carteirinha (id_aluno, codigo, emitida_em, revogada_em) VALUES (@ana, 'e1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6', '2026-02-01 03:00:00', '2026-01-31 03:00:00')");
SET @c_ana  = (SELECT id_carteirinha FROM carteirinha WHERE id_aluno = @ana);

-- ===== contato_responsavel =====
CALL t("contato: Carla principal da Ana valida", 0, "INSERT INTO contato_responsavel (id_aluno, nome, telefone, email, principal) VALUES (@ana, 'Carla Teste', '(11) 90000-0000', 'carla.teste@example.com', 1)");
CALL t("contato: Carla principal do Davi (irmao) valida", 0, "INSERT INTO contato_responsavel (id_aluno, nome, telefone, email, principal) VALUES (@davi, 'Carla Teste', '(11) 90000-0000', 'carla.teste@example.com', 1)");
CALL t("contato: segundo principal ativo do mesmo aluno", 1062, "INSERT INTO contato_responsavel (id_aluno, nome, telefone, email, principal) VALUES (@ana, 'Outro Teste', '(11) 90000-0001', 'outro.teste@example.com', 1)");
CALL t("contato: principal inativo convive com o ativo", 0, "INSERT INTO contato_responsavel (id_aluno, nome, telefone, email, principal, ativo) VALUES (@ana, 'Contato Antigo Teste', '(11) 90000-0002', 'antigo.teste@example.com', 1, 0)");
CALL t("contato: reativar o principal antigo", 1062, "UPDATE contato_responsavel SET ativo = 1 WHERE nome = 'Contato Antigo Teste'");
CALL t("contato: nao principal so com telefone", 0, "INSERT INTO contato_responsavel (id_aluno, nome, telefone) VALUES (@ana, 'Avo Teste', '(11) 90000-0003')");
CALL t("contato: sem telefone e sem e-mail", 3819, "INSERT INTO contato_responsavel (id_aluno, nome) VALUES (@ana, 'Outro Teste')");
CALL t("contato: telefone so com espacos", 3819, "INSERT INTO contato_responsavel (id_aluno, nome, telefone) VALUES (@ana, 'Outro Teste', '   ')");
CALL t("contato: principal sem e-mail", 3819, "INSERT INTO contato_responsavel (id_aluno, nome, telefone, principal) VALUES (@davi, 'Outro Teste', '(11) 90000-0004', 1)");
CALL t("contato: principal sem telefone", 3819, "INSERT INTO contato_responsavel (id_aluno, nome, email, principal) VALUES (@davi, 'Outro Teste', 'outro.teste@example.com', 1)");
CALL t("contato: nome em branco", 3819, "INSERT INTO contato_responsavel (id_aluno, nome, telefone) VALUES (@ana, ' ', '(11) 90000-0005')");
CALL t("contato: recebe_notificacao = 2", 3819, "INSERT INTO contato_responsavel (id_aluno, nome, telefone, recebe_notificacao) VALUES (@ana, 'Outro Teste', '(11) 90000-0006', 2)");
CALL t("contato: aluno inexistente", 1452, "INSERT INTO contato_responsavel (id_aluno, nome, telefone) VALUES (999999, 'Outro Teste', '(11) 90000-0007')");
SET @carla_ana = (SELECT id_contato FROM contato_responsavel WHERE id_aluno = @ana AND nome = 'Carla Teste');

-- ===== consentimento =====
CALL t("consentimento: valido", 0, "INSERT INTO consentimento (id_contato, versao_termo, assinado_em, referencia_termo, id_usuario_conferente, conferido_em) VALUES (@carla_ana, 'v1-2026', '2026-02-01', 'Protocolo ficticio 0001', @ate, '2026-02-01 13:00:00')");
CALL t("consentimento: versao em branco", 3819, "INSERT INTO consentimento (id_contato, versao_termo, assinado_em, referencia_termo, id_usuario_conferente, conferido_em) VALUES (@carla_ana, ' ', '2026-02-01', 'Protocolo ficticio 0002', @ate, '2026-02-01 13:00:00')");
CALL t("consentimento: referencia em branco", 3819, "INSERT INTO consentimento (id_contato, versao_termo, assinado_em, referencia_termo, id_usuario_conferente, conferido_em) VALUES (@carla_ana, 'v1-2026', '2026-02-01', '', @ate, '2026-02-01 13:00:00')");
CALL t("consentimento: revogado antes da conferencia", 3819, "INSERT INTO consentimento (id_contato, versao_termo, assinado_em, referencia_termo, id_usuario_conferente, conferido_em, revogado_em) VALUES (@carla_ana, 'v1-2026', '2026-02-01', 'Protocolo ficticio 0003', @ate, '2026-02-01 13:00:00', '2026-02-01 12:00:00')");
CALL t("consentimento: conferente inexistente", 1452, "INSERT INTO consentimento (id_contato, versao_termo, assinado_em, referencia_termo, id_usuario_conferente, conferido_em) VALUES (@carla_ana, 'v1-2026', '2026-02-01', 'Protocolo ficticio 0004', 999999, '2026-02-01 13:00:00')");

-- ===== registro_acesso =====
-- Colunas: id_vinculo, id_usuario, id_carteirinha, tipo, forma, situacao, lancado_em,
--          ocorrido_em, cancelado_em, motivo_cancelamento, chave_requisicao, motivo_manual
SET @cols = 'INSERT INTO registro_acesso (id_vinculo, id_usuario, id_carteirinha, tipo, forma, situacao, lancado_em, ocorrido_em, cancelado_em, motivo_cancelamento, chave_requisicao, motivo_manual) VALUES ';
CALL t("registro: leitura confirmada valida", 0, CONCAT(@cols, "(@v_ana, @ate, @c_ana, 'ENTRADA', 'LEITURA', 'CONFIRMADA', '2026-10-27 10:00:00', '2026-10-27 10:00:05', NULL, NULL, '00000000-0000-0000-0000-000000000001', NULL)"));
CALL t("registro: manual confirmada valida (DEC-03)", 0, CONCAT(@cols, "(@v_ana, @ate, NULL, 'SAIDA', 'MANUAL', 'CONFIRMADA', '2026-10-27 15:10:00', '2026-10-27 15:00:00', NULL, NULL, '00000000-0000-0000-0000-000000000002', 'Carteirinha esquecida')"));
-- Inválidos (antes de existir movimentação pendente, para o UNIQUE não interferir)
CALL t("registro: tipo invalido", 3819, CONCAT(@cols, "(@v_ana, @ate, @c_ana, 'MANUAL', 'LEITURA', 'CONFIRMADA', '2026-10-27 10:00:00', '2026-10-27 10:00:05', NULL, NULL, '00000000-0000-0000-0000-000000000101', NULL)"));
CALL t("registro: forma invalida", 3819, CONCAT(@cols, "(@v_ana, @ate, @c_ana, 'ENTRADA', 'QRCODE', 'CONFIRMADA', '2026-10-27 10:00:00', '2026-10-27 10:00:05', NULL, NULL, '00000000-0000-0000-0000-000000000102', NULL)"));
CALL t("registro: situacao invalida", 3819, CONCAT(@cols, "(@v_ana, @ate, @c_ana, 'ENTRADA', 'LEITURA', 'PENDENTE', '2026-10-27 10:00:00', NULL, NULL, NULL, '00000000-0000-0000-0000-000000000103', NULL)"));
CALL t("registro: leitura sem carteirinha", 3819, CONCAT(@cols, "(@v_ana, @ate, NULL, 'ENTRADA', 'LEITURA', 'CONFIRMADA', '2026-10-27 10:00:00', '2026-10-27 10:00:05', NULL, NULL, '00000000-0000-0000-0000-000000000104', NULL)"));
CALL t("registro: leitura com motivo manual", 3819, CONCAT(@cols, "(@v_ana, @ate, @c_ana, 'ENTRADA', 'LEITURA', 'CONFIRMADA', '2026-10-27 10:00:00', '2026-10-27 10:00:05', NULL, NULL, '00000000-0000-0000-0000-000000000105', 'Motivo')"));
CALL t("registro: manual com carteirinha", 3819, CONCAT(@cols, "(@v_ana, @ate, @c_ana, 'SAIDA', 'MANUAL', 'CONFIRMADA', '2026-10-27 15:10:00', '2026-10-27 15:00:00', NULL, NULL, '00000000-0000-0000-0000-000000000106', 'Motivo')"));
CALL t("registro: manual sem motivo", 3819, CONCAT(@cols, "(@v_ana, @ate, NULL, 'SAIDA', 'MANUAL', 'CONFIRMADA', '2026-10-27 15:10:00', '2026-10-27 15:00:00', NULL, NULL, '00000000-0000-0000-0000-000000000107', NULL)"));
CALL t("registro: manual com motivo so de espacos", 3819, CONCAT(@cols, "(@v_ana, @ate, NULL, 'SAIDA', 'MANUAL', 'CONFIRMADA', '2026-10-27 15:10:00', '2026-10-27 15:00:00', NULL, NULL, '00000000-0000-0000-0000-000000000108', '   ')"));
CALL t("registro: manual aguardando giro", 3819, CONCAT(@cols, "(@v_ana, @ate, NULL, 'SAIDA', 'MANUAL', 'AGUARDANDO_GIRO', '2026-10-27 15:10:00', NULL, NULL, NULL, '00000000-0000-0000-0000-000000000109', 'Motivo')"));
CALL t("registro: confirmada sem ocorrido_em", 3819, CONCAT(@cols, "(@v_ana, @ate, @c_ana, 'ENTRADA', 'LEITURA', 'CONFIRMADA', '2026-10-27 10:00:00', NULL, NULL, NULL, '00000000-0000-0000-0000-000000000110', NULL)"));
CALL t("registro: aguardando com ocorrido_em", 3819, CONCAT(@cols, "(@v_ana, @ate, @c_ana, 'ENTRADA', 'LEITURA', 'AGUARDANDO_GIRO', '2026-10-27 10:00:00', '2026-10-27 10:00:05', NULL, NULL, '00000000-0000-0000-0000-000000000111', NULL)"));
CALL t("registro: confirmada com cancelado_em", 3819, CONCAT(@cols, "(@v_ana, @ate, @c_ana, 'ENTRADA', 'LEITURA', 'CONFIRMADA', '2026-10-27 10:00:00', '2026-10-27 10:00:05', '2026-10-27 10:00:15', NULL, '00000000-0000-0000-0000-000000000112', NULL)"));
CALL t("registro: cancelada sem motivo", 3819, CONCAT(@cols, "(@v_ana, @ate, @c_ana, 'ENTRADA', 'LEITURA', 'CANCELADA', '2026-10-27 10:00:00', NULL, '2026-10-27 10:00:15', NULL, '00000000-0000-0000-0000-000000000113', NULL)"));
CALL t("registro: cancelada sem cancelado_em", 3819, CONCAT(@cols, "(@v_ana, @ate, @c_ana, 'ENTRADA', 'LEITURA', 'CANCELADA', '2026-10-27 10:00:00', NULL, NULL, 'SEM_GIRO', '00000000-0000-0000-0000-000000000114', NULL)"));
CALL t("registro: motivo de cancelamento invalido", 3819, CONCAT(@cols, "(@v_ana, @ate, @c_ana, 'ENTRADA', 'LEITURA', 'CANCELADA', '2026-10-27 10:00:00', NULL, '2026-10-27 10:00:15', 'OUTRO', '00000000-0000-0000-0000-000000000115', NULL)"));
CALL t("registro: leitura com giro antes do lancamento", 3819, CONCAT(@cols, "(@v_ana, @ate, @c_ana, 'ENTRADA', 'LEITURA', 'CONFIRMADA', '2026-10-27 10:00:00', '2026-10-27 09:59:59', NULL, NULL, '00000000-0000-0000-0000-000000000116', NULL)"));
CALL t("registro: manual com horario depois do lancamento", 3819, CONCAT(@cols, "(@v_ana, @ate, NULL, 'SAIDA', 'MANUAL', 'CONFIRMADA', '2026-10-27 15:10:00', '2026-10-27 15:10:01', NULL, NULL, '00000000-0000-0000-0000-000000000117', 'Motivo')"));
CALL t("registro: vinculo inexistente", 1452, CONCAT(@cols, "(999999, @ate, @c_ana, 'ENTRADA', 'LEITURA', 'CONFIRMADA', '2026-10-27 10:00:00', '2026-10-27 10:00:05', NULL, NULL, '00000000-0000-0000-0000-000000000118', NULL)"));
CALL t("registro: usuario inexistente", 1452, CONCAT(@cols, "(@v_ana, 999999, @c_ana, 'ENTRADA', 'LEITURA', 'CONFIRMADA', '2026-10-27 10:00:00', '2026-10-27 10:00:05', NULL, NULL, '00000000-0000-0000-0000-000000000119', NULL)"));
CALL t("registro: chave_requisicao duplicada (RN08)", 1062, CONCAT(@cols, "(@v_ana, @ate, @c_ana, 'ENTRADA', 'LEITURA', 'CONFIRMADA', '2026-10-27 11:00:00', '2026-10-27 11:00:05', NULL, NULL, '00000000-0000-0000-0000-000000000001', NULL)"));
-- Uma movimentação pendente por vínculo (RN02, coluna gerada pendente_vinculo)
CALL t("registro: Ana aguardando giro valida", 0, CONCAT(@cols, "(@v_ana, @ate, @c_ana, 'ENTRADA', 'LEITURA', 'AGUARDANDO_GIRO', '2026-10-27 16:00:00', NULL, NULL, NULL, '00000000-0000-0000-0000-000000000003', NULL)"));
CALL t("registro: segunda pendente da Ana", 1062, CONCAT(@cols, "(@v_ana, @ate, @c_ana, 'ENTRADA', 'LEITURA', 'AGUARDANDO_GIRO', '2026-10-27 16:00:01', NULL, NULL, NULL, '00000000-0000-0000-0000-000000000004', NULL)"));
CALL t("registro: pendente da Ana cancelada SEM_GIRO", 0, "UPDATE registro_acesso SET situacao = 'CANCELADA', cancelado_em = '2026-10-27 16:00:15', motivo_cancelamento = 'SEM_GIRO' WHERE chave_requisicao = '00000000-0000-0000-0000-000000000003'");
CALL t("registro: nova pendente da Ana apos cancelar", 0, CONCAT(@cols, "(@v_ana, @ate, @c_ana, 'ENTRADA', 'LEITURA', 'AGUARDANDO_GIRO', '2026-10-27 16:01:00', NULL, NULL, NULL, '00000000-0000-0000-0000-000000000005', NULL)"));
CALL t("registro: pendente confirmada pelo giro", 0, "UPDATE registro_acesso SET situacao = 'CONFIRMADA', ocorrido_em = '2026-10-27 16:01:04' WHERE chave_requisicao = '00000000-0000-0000-0000-000000000005'");
CALL t("registro: cancelada pelo FUNCIONARIO valida", 0, CONCAT(@cols, "(@v_davi, @ate, (SELECT id_carteirinha FROM carteirinha WHERE id_aluno = @davi), 'ENTRADA', 'LEITURA', 'CANCELADA', '2026-10-27 16:02:00', NULL, '2026-10-27 16:02:05', 'FUNCIONARIO', '00000000-0000-0000-0000-000000000006', NULL)"));
SET @r1 = (SELECT id_registro FROM registro_acesso WHERE chave_requisicao = '00000000-0000-0000-0000-000000000001');
SET @r2 = (SELECT id_registro FROM registro_acesso WHERE chave_requisicao = '00000000-0000-0000-0000-000000000002');

-- ===== notificacao =====
CALL t("notificacao: SIMULADA valida", 0, "INSERT INTO notificacao (id_registro, id_contato, status, criado_em) VALUES (@r1, @carla_ana, 'SIMULADA', '2026-10-27 10:00:06')");
CALL t("notificacao: valores padrao (EMAIL, PENDENTE)", 0, "INSERT INTO notificacao (id_registro, id_contato, criado_em) VALUES (@r2, @carla_ana, '2026-10-27 15:10:01')");
CALL t("notificacao: mesmo registro e contato", 1062, "INSERT INTO notificacao (id_registro, id_contato, status, criado_em) VALUES (@r1, @carla_ana, 'SIMULADA', '2026-10-27 10:00:07')");
CALL t("notificacao: canal WHATSAPP (DEC-37)", 3819, "INSERT INTO notificacao (id_registro, id_contato, canal, criado_em) VALUES (@r1, (SELECT id_contato FROM contato_responsavel WHERE nome = 'Avo Teste'), 'WHATSAPP', '2026-10-27 10:00:07')");
CALL t("notificacao: status invalido", 3819, "INSERT INTO notificacao (id_registro, id_contato, status, criado_em) VALUES (@r1, (SELECT id_contato FROM contato_responsavel WHERE nome = 'Avo Teste'), 'LIDA', '2026-10-27 10:00:07')");
CALL t("notificacao: registro inexistente", 1452, "INSERT INTO notificacao (id_registro, id_contato, criado_em) VALUES (999999, @carla_ana, '2026-10-27 10:00:07')");

-- ===== Resultado =====
SELECT n, teste, esperado, obtido, situacao FROM t03_resultado ORDER BY n;
SELECT n, teste, esperado, obtido, LEFT(mensagem, 120) AS mensagem FROM t03_resultado WHERE situacao = 'FALHOU' ORDER BY n;
SELECT situacao, COUNT(*) AS qtd FROM t03_resultado GROUP BY situacao;
SELECT COUNT(*) AS casos_registrados, 86 AS casos_no_script,
       IF(COUNT(*) = 86, 'OK', 'FALTAM CASOS') AS conferencia FROM t03_resultado;
