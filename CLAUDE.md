# CLAUDE.md — Furin Access

Instruções para o Claude Code trabalhar neste repositório. Leia este arquivo inteiro
antes da primeira tarefa.

## 1. O projeto em poucas linhas

Aplicação web que registra a entrada e a saída dos alunos de uma escola pública
(Projeto Integrador da equipe FURIN, TADS Senac). **Só os funcionários têm conta, todos
com o perfil único `FUNCIONARIO`**: o ATE e a Gestão Escolar são cargos diferentes, mas
no sistema usam tudo (DEC-37). O aluno só apresenta a carteirinha. Os pais/responsáveis
só recebem o e-mail de entrada e saída: não têm conta, login nem tela.

- **Stack:** Java 21, Spring Boot 4.1.x, MySQL 8.4, Maven, Thymeleaf, HTML, CSS,
  JavaScript, Spring Security, Spring Data JPA, Bean Validation, Flyway, ZXing.
- **Pacote base:** `br.com.furin.access` (organização em
  [`docs/arquitetura-e-dados.md`](docs/arquitetura-e-dados.md) §4).
- **Repositório:** `https://github.com/LuisEduardo77/PI---Furin.git`.
- **Estado:** a documentação está revisada. A mudança para o perfil único (DEC-37 a
  DEC-39, 21/10/2026) já está aplicada em todos os arquivos. T-03 (DDL validado), T-04
  (projeto gerado, Boot 4.1.2) e T-05 (CI no GitHub Actions com MySQL 8.4) estão
  concluídas; **nenhuma funcionalidade foi escrita
  ainda.** Todos os RF e RNF estão com `Implementação: Planejada`, menos **RF17, RF24 e
  RF25**, que estão **fora do escopo** e não devem ser implementados (DEC-37).

## 2. Onde está a verdade

Em caso de conflito, vale a primeira fonte da lista:

1. Decisões registradas em [`docs/rastreabilidade.md`](docs/rastreabilidade.md) §2
   (ATU-01 a ATU-07, DEC-01 a DEC-39). Uma decisão marcada como "substituída" não vale
   mais; vale a que a substituiu.
2. Os requisitos: [`requirements/rf/`](requirements/rf/) (RF01 a RF26) e
   [`requirements/rnf/`](requirements/rnf/) (RNF01 a RNF08).
3. [`docs/regras-de-negocio.md`](docs/regras-de-negocio.md) (RN01 a RN17 e todas as
   medidas).
4. [`docs/arquitetura-e-dados.md`](docs/arquitetura-e-dados.md) (rotas, parâmetros,
   modelo, DDL de referência, CI).
5. [`docs/escopo-e-perfis.md`](docs/escopo-e-perfis.md) (MVP, perfis, matriz de
   permissões).

Os PDFs das atividades (A01 a A14) **não** são fonte para o código. Eles têm
diferenças conhecidas, que não serão corrigidas (DEC-35).

Se um requisito parecer ambíguo ou contraditório, **pare e pergunte**. Não invente
regra nova. Uma decisão nova recebe o próximo ID DEC e vai para a rastreabilidade.

## 3. Como trabalhar

1. Siga o [plano de implementação](docs/plano-de-implementacao.md), na ordem das
   dependências. Cada tarefa (T-nn) é um cartão do Trello, uma branch e um pull request.
2. Antes de cada tarefa, leia os RF e RNF citados nela. Os **critérios de aceitação**
   (`CA-RFnn-nn`, `CA-RNFnn-nn`) definem quando a tarefa está pronta.
3. Escreva os testes dos critérios marcados como UNI ou INT. Os marcados como MAN ou
   DEM viram itens de verificação no PR.
4. `mvn -B verify` precisa passar localmente e na CI.
5. Ao terminar:
   - mude o campo **Implementação** do RF ou RNF só com evidência (`Simulada`,
     `Implementada` ou `Verificada`; ver [README](README.md));
   - atualize a tabela de estado do README quando mudar algo dela;
   - registre o uso de IA na tabela da seção 3 de
     [`docs/registro-de-ia.md`](docs/registro-de-ia.md).

**Primeiras tarefas:** T-01 (completar o repositório: `.gitignore`, proteção da `main`),
T-03 (validar o DDL no MySQL 8.4 **antes** de escrever as migrações; o DDL nunca foi
executado) e T-04 (gerar o projeto pelo Spring Initializr).

## 4. Regras que não podem ser quebradas

**Dados e segredos**

- Use **só dados fictícios** (`@example.com`, "Ana Teste", "Carla Teste", contas
  `ate.demo` e `gestao.demo`). Nunca use dados reais de alunos, responsáveis,
  funcionários ou da escola, nem os da A01.
- Nenhuma senha, token ou credencial no repositório. Tudo vem de variável de ambiente
  ou de `application-local.yml` no `.gitignore`. Na CI, *Secrets*.
- Senhas só como hash, pelo `PasswordEncoder` delegante (RNF03). Nunca em log.
- Logs sem senha, sem documento e com e-mail mascarado (A10 §8.15). Do código da
  carteirinha, só os 4 primeiros caracteres.

**Regras de negócio essenciais**

- O tipo (entrada ou saída) **sempre** é calculado no servidor (RN01). Nunca aceite o
  tipo vindo da interface, e não crie seletor de modo.
- A movimentação só vale depois do giro (RN02): `AGUARDANDO_GIRO` → `CONFIRMADA`, ou
  `CANCELADA` em 15 s.
- Toda operação sobre um aluno usa `SELECT … FOR UPDATE` e `chave_requisicao` (RN08).
- Todo valor de regra fica em propriedade `furin.*`, nunca escrito no código
  (tabela em [arquitetura §4](docs/arquitetura-e-dados.md)).

**Segurança e acesso**

- Negar por padrão. Toda rota (menos `/login`) exige um funcionário autenticado e com
  conta ativa, conferido no servidor. Esconder um botão não é controle de acesso.
- O perfil é sempre `FUNCIONARIO`, gravado pelo servidor e nunca aceito da interface.
  Ninguém desativa a própria conta (DEC-39).
- CSRF ligado. Bean Validation no servidor. Consultas parametrizadas. `th:text`,
  nunca `th:utext` com dado do usuário.
- Fotos e termos ficam fora de `static/`, com nome UUID, entregues só por controller
  autorizado a funcionários (DEC-08).

**Simulações (o grupo não tem leitor, catraca nem SMTP)**

- Leitura (campo + painel "Simular leitura"), giro ("Simular passagem") e e-mail
  (`SIMULADA`) são **simulados** e só existem no perfil `prototipo`, sempre com
  indicação visível de simulação (DEC-18, DEC-25, DEC-32). Não há WhatsApp, nem
  simulado (DEC-37).
- O serviço de envio de e-mail fica atrás de uma interface. No MVP só existe a
  implementação simulada.

**Acessibilidade (desde a primeira tela)**

- Siga o RNF06 e a checklist do [plano §3](docs/plano-de-implementacao.md):
  `lang="pt-BR"`, título por página, link "Ir para o conteúdo principal", `label` em
  todo campo, erros com `aria-invalid` e `aria-describedby`, foco visível de 3 px, alvos
  de 44 px, estado em texto e não só em cor, `role="status"`/`role="alert"` sem mover o
  foco, funcionamento com 320 px e zoom de 200%.

## 5. O que **não** fazer (fora do MVP)

- Integração com leitor USB físico, catraca ou sensor de giro.
- Envio real de e-mail, WhatsApp de qualquer tipo, e o perfil `smtp`.
- Contas, login ou telas para alunos e pais/responsáveis; a consulta do responsável
  (RF24), a conta do responsável (RF25) e a restrição da Gestão (RF17) (DEC-37).
- Perfis separados para ATE e Gestão (DEC-37).
- Cadastro de turmas pela tela (vêm da carga inicial).
- Descarte automático de dados (o prazo de 5 anos fica só configurado, DEC-36).
- Hospedagem externa ou publicação da aplicação. Ela roda local; o GitHub Pages pode
  publicar só a documentação (DEC-31).
- Aplicativo móvel, biometria, reconhecimento facial, RFID, relatórios avançados,
  exportação para a DRE.

## 6. Convenções

- Textos de tela, mensagens e documentação em **português do Brasil**.
- Horários gravados em UTC (`DATETIME(6)`) e exibidos em America/Sao_Paulo (RN10).
- Nada é apagado fisicamente. Inativação preserva o histórico.
- Marcas nos documentos: `[DEC-nn]` decisão do grupo; `[PROP]` sugestão de detalhe
  (texto de tela, nome de propriedade) que pode ser ajustada, **registrando no PR**.
- Perfis de execução: `prototipo` (padrão, com simulações e carga fictícia) e `test`.
- Mudou um requisito ao implementar? Atualize o arquivo do RF/RNF e a rastreabilidade
  no mesmo PR, e diga isso na descrição.

## 7. Comandos

```bash
# build e testes (precisa de um MySQL 8.4 local ou das variáveis da CI)
mvn -B verify

# rodar o protótipo
SPRING_PROFILES_ACTIVE=prototipo mvn spring-boot:run

# backup do protótipo (T-47, DEC-30)
scripts/backup.sh      # Linux/macOS
scripts/backup.ps1     # Windows
```

Variáveis esperadas (nomes sugeridos, sem valores no repositório):
`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`,
as senhas iniciais da carga fictícia e `FURIN_BACKUP_DIR`.
