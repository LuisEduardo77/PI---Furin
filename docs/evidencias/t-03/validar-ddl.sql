-- T-03: validação do DDL de docs/arquitetura-e-dados.md §5.5 (linhas 378-547, copiadas sem alteração em 27/10/2026)
-- Rodar a partir da raiz do repositório: ver docs/evidencias/t-03/README.md
-- Banco descartável: furin_ddl_teste. Só estrutura, nenhum dado.
DROP DATABASE IF EXISTS furin_ddl_teste;
CREATE DATABASE furin_ddl_teste CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE furin_ddl_teste;
SELECT VERSION() AS versao_mysql;

-- ===== DDL do documento =====
CREATE TABLE usuario (
  id_usuario                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  nome                        VARCHAR(150)    NOT NULL,
  login                       VARCHAR(80)     NOT NULL,
  senha_hash                  VARCHAR(255)    NOT NULL,
  perfil                      VARCHAR(15)     NOT NULL,
  ativo                       BOOLEAN         NOT NULL DEFAULT 1,
  troca_senha_obrigatoria     BOOLEAN         NOT NULL DEFAULT 0,
  senha_temporaria_expira_em  DATETIME(6)     NULL,
  criado_em                   DATETIME(6)     NOT NULL,
  PRIMARY KEY (id_usuario),
  UNIQUE KEY uk_usuario_login (login),
  CONSTRAINT ck_usuario_nome   CHECK (TRIM(nome) <> ''),
  CONSTRAINT ck_usuario_login  CHECK (TRIM(login) <> ''),
  CONSTRAINT ck_usuario_perfil CHECK (perfil IN ('FUNCIONARIO')),
  CONSTRAINT ck_usuario_bools  CHECK (ativo IN (0,1) AND troca_senha_obrigatoria IN (0,1)),
  CONSTRAINT ck_usuario_temp   CHECK (senha_temporaria_expira_em IS NULL OR troca_senha_obrigatoria = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE turma (
  id_turma    BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
  nome        VARCHAR(30)       NOT NULL,
  ano_letivo  SMALLINT UNSIGNED NOT NULL,
  turno       VARCHAR(10)       NOT NULL,
  ativa       BOOLEAN           NOT NULL DEFAULT 1,
  PRIMARY KEY (id_turma),
  UNIQUE KEY uk_turma (nome, ano_letivo, turno),
  CONSTRAINT ck_turma_nome  CHECK (TRIM(nome) <> ''),
  CONSTRAINT ck_turma_ano   CHECK (ano_letivo BETWEEN 2000 AND 2199),
  CONSTRAINT ck_turma_turno CHECK (turno IN ('MANHA','TARDE','INTEGRAL','NOITE')),
  CONSTRAINT ck_turma_ativa CHECK (ativa IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE aluno (
  id_aluno    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  matricula   VARCHAR(30)     NOT NULL,
  nome        VARCHAR(150)    NOT NULL,
  foto_chave  VARCHAR(255)    NOT NULL,
  ativo       BOOLEAN         NOT NULL DEFAULT 1,
  criado_em   DATETIME(6)     NOT NULL,
  PRIMARY KEY (id_aluno),
  UNIQUE KEY uk_aluno_matricula (matricula),
  CONSTRAINT ck_aluno_matricula CHECK (TRIM(matricula) <> ''),
  CONSTRAINT ck_aluno_nome      CHECK (TRIM(nome) <> ''),
  CONSTRAINT ck_aluno_foto      CHECK (TRIM(foto_chave) <> ''),
  CONSTRAINT ck_aluno_ativo     CHECK (ativo IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE vinculo_turma (
  id_vinculo  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_aluno    BIGINT UNSIGNED NOT NULL,
  id_turma    BIGINT UNSIGNED NOT NULL,
  inicio_em   DATETIME(6)     NOT NULL,
  fim_em      DATETIME(6)     NULL,
  PRIMARY KEY (id_vinculo),
  UNIQUE KEY uk_vinculo_inicio (id_aluno, inicio_em),
  CONSTRAINT fk_vinculo_aluno FOREIGN KEY (id_aluno) REFERENCES aluno (id_aluno),
  CONSTRAINT fk_vinculo_turma FOREIGN KEY (id_turma) REFERENCES turma (id_turma),
  CONSTRAINT ck_vinculo_periodo CHECK (fim_em IS NULL OR fim_em > inicio_em)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE carteirinha (
  id_carteirinha  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_aluno        BIGINT UNSIGNED NOT NULL,
  codigo          CHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  emitida_em      DATETIME(6)     NOT NULL,
  expira_em       DATETIME(6)     NULL,
  revogada_em     DATETIME(6)     NULL,
  PRIMARY KEY (id_carteirinha),
  UNIQUE KEY uk_carteirinha_codigo (codigo),
  CONSTRAINT fk_carteirinha_aluno FOREIGN KEY (id_aluno) REFERENCES aluno (id_aluno),
  CONSTRAINT ck_carteirinha_expira CHECK (expira_em IS NULL OR expira_em > emitida_em),
  CONSTRAINT ck_carteirinha_revoga CHECK (revogada_em IS NULL OR revogada_em >= emitida_em)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE contato_responsavel (
  id_contato              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_aluno                BIGINT UNSIGNED NOT NULL,
  nome                    VARCHAR(150)    NOT NULL,
  telefone                VARCHAR(25)     NULL,
  email                   VARCHAR(254)    NULL,
  ativo                   BOOLEAN         NOT NULL DEFAULT 1,
  principal               BOOLEAN         NOT NULL DEFAULT 0,
  recebe_notificacao      BOOLEAN         NOT NULL DEFAULT 1,
  principal_aluno         BIGINT UNSIGNED
      GENERATED ALWAYS AS (CASE WHEN principal = 1 AND ativo = 1 THEN id_aluno END) STORED,
  PRIMARY KEY (id_contato),
  UNIQUE KEY uk_contato_principal (principal_aluno),
  CONSTRAINT fk_contato_aluno     FOREIGN KEY (id_aluno) REFERENCES aluno (id_aluno),
  CONSTRAINT ck_contato_nome      CHECK (TRIM(nome) <> ''),
  CONSTRAINT ck_contato_algum     CHECK (COALESCE(TRIM(telefone),'') <> '' OR COALESCE(TRIM(email),'') <> ''),
  CONSTRAINT ck_contato_principal CHECK (principal = 0
      OR (COALESCE(TRIM(telefone),'') <> '' AND COALESCE(TRIM(email),'') <> '')),
  CONSTRAINT ck_contato_bools     CHECK (ativo IN (0,1) AND principal IN (0,1) AND recebe_notificacao IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE consentimento (
  id_consentimento       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_contato             BIGINT UNSIGNED NOT NULL,
  versao_termo           VARCHAR(30)     NOT NULL,
  assinado_em            DATE            NOT NULL,
  referencia_termo       VARCHAR(255)    NOT NULL,
  id_usuario_conferente  BIGINT UNSIGNED NOT NULL,
  conferido_em           DATETIME(6)     NOT NULL,
  revogado_em            DATETIME(6)     NULL,
  PRIMARY KEY (id_consentimento),
  CONSTRAINT fk_consent_contato    FOREIGN KEY (id_contato) REFERENCES contato_responsavel (id_contato),
  CONSTRAINT fk_consent_conferente FOREIGN KEY (id_usuario_conferente) REFERENCES usuario (id_usuario),
  CONSTRAINT ck_consent_versao CHECK (TRIM(versao_termo) <> ''),
  CONSTRAINT ck_consent_ref    CHECK (TRIM(referencia_termo) <> ''),
  CONSTRAINT ck_consent_revoga CHECK (revogado_em IS NULL OR revogado_em >= conferido_em)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE registro_acesso (
  id_registro          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_vinculo           BIGINT UNSIGNED NOT NULL,
  id_usuario           BIGINT UNSIGNED NOT NULL,
  id_carteirinha       BIGINT UNSIGNED NULL,
  tipo                 VARCHAR(7)      NOT NULL,
  forma                VARCHAR(7)      NOT NULL,
  situacao             VARCHAR(15)     NOT NULL,
  lancado_em           DATETIME(6)     NOT NULL,
  ocorrido_em          DATETIME(6)     NULL,
  cancelado_em         DATETIME(6)     NULL,
  motivo_cancelamento  VARCHAR(15)     NULL,
  chave_requisicao     CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  motivo_manual        VARCHAR(200)    NULL,
  pendente_vinculo     BIGINT UNSIGNED
      GENERATED ALWAYS AS (CASE WHEN situacao = 'AGUARDANDO_GIRO' THEN id_vinculo END) STORED,
  PRIMARY KEY (id_registro),
  UNIQUE KEY uk_registro_chave (chave_requisicao),
  UNIQUE KEY uk_registro_pendente (pendente_vinculo),
  KEY ix_registro_vinculo (id_vinculo, situacao, ocorrido_em),
  KEY ix_registro_ocorrido (situacao, ocorrido_em),
  CONSTRAINT fk_registro_vinculo     FOREIGN KEY (id_vinculo) REFERENCES vinculo_turma (id_vinculo),
  CONSTRAINT fk_registro_usuario     FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario),
  CONSTRAINT fk_registro_carteirinha FOREIGN KEY (id_carteirinha) REFERENCES carteirinha (id_carteirinha),
  CONSTRAINT ck_registro_tipo     CHECK (tipo IN ('ENTRADA','SAIDA')),
  CONSTRAINT ck_registro_forma    CHECK (forma IN ('LEITURA','MANUAL')),
  CONSTRAINT ck_registro_situacao CHECK (situacao IN ('AGUARDANDO_GIRO','CONFIRMADA','CANCELADA')),
  CONSTRAINT ck_registro_leitura  CHECK (forma <> 'LEITURA'
      OR (id_carteirinha IS NOT NULL AND motivo_manual IS NULL)),
  CONSTRAINT ck_registro_manual   CHECK (forma <> 'MANUAL'
      OR (id_carteirinha IS NULL AND COALESCE(TRIM(motivo_manual),'') <> '' AND situacao = 'CONFIRMADA')),
  CONSTRAINT ck_registro_ocorrido CHECK ((situacao = 'CONFIRMADA') = (ocorrido_em IS NOT NULL)),
  CONSTRAINT ck_registro_cancel   CHECK ((situacao = 'CANCELADA') = (cancelado_em IS NOT NULL)
      AND (situacao = 'CANCELADA') = (motivo_cancelamento IS NOT NULL)),
  CONSTRAINT ck_registro_motcanc  CHECK (motivo_cancelamento IS NULL OR motivo_cancelamento IN ('SEM_GIRO','FUNCIONARIO')),
  CONSTRAINT ck_registro_horarios CHECK (ocorrido_em IS NULL
      OR (forma = 'MANUAL'  AND ocorrido_em <= lancado_em)
      OR (forma = 'LEITURA' AND ocorrido_em >= lancado_em))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE notificacao (
  id_notificacao       BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
  id_registro          BIGINT UNSIGNED  NOT NULL,
  id_contato           BIGINT UNSIGNED  NOT NULL,
  canal                VARCHAR(10)      NOT NULL DEFAULT 'EMAIL',
  status               VARCHAR(10)      NOT NULL DEFAULT 'PENDENTE',
  tentativas           TINYINT UNSIGNED NOT NULL DEFAULT 0,
  criado_em            DATETIME(6)      NOT NULL,
  ultima_tentativa_em  DATETIME(6)      NULL,
  PRIMARY KEY (id_notificacao),
  UNIQUE KEY uk_notificacao (id_registro, id_contato),
  KEY ix_notificacao_status (status),
  CONSTRAINT fk_notif_registro FOREIGN KEY (id_registro) REFERENCES registro_acesso (id_registro),
  CONSTRAINT fk_notif_contato  FOREIGN KEY (id_contato) REFERENCES contato_responsavel (id_contato),
  CONSTRAINT ck_notif_canal  CHECK (canal IN ('EMAIL')),
  CONSTRAINT ck_notif_status CHECK (status IN ('PENDENTE','ENVIADA','FALHA','SIMULADA'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ===== Conferência =====
SELECT TABLE_NAME AS tabela, ENGINE AS motor, TABLE_COLLATION AS collation
  FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'furin_ddl_teste' ORDER BY TABLE_NAME;
SELECT TABLE_NAME AS tabela, CONSTRAINT_TYPE AS tipo, COUNT(*) AS qtd
  FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = 'furin_ddl_teste'
  GROUP BY TABLE_NAME, CONSTRAINT_TYPE ORDER BY TABLE_NAME, CONSTRAINT_TYPE;
SELECT 'OK: DDL executado sem erro' AS resultado;