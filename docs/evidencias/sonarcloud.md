# SonarCloud — análise na CI e problemas não corrigidos (T-44)

Evidência do [RNF08](../../requirements/rnf/RNF08.md) (CA-RNF08-07: a análise do
SonarCloud roda na CI e mostra o quality gate no pull request) e da parte da T-44 que
toca o [RNF04](../../requirements/rnf/RNF04.md) (busca de segredos no repositório).

## Como a análise roda

| Item | Como ficou |
|---|---|
| Onde | Passo "Análise SonarCloud" de [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml), depois do `./mvnw -B verify`, a cada pull request e a cada push na `main` |
| Cobertura | O JaCoCo (`jacoco-maven-plugin` no [`pom.xml`](../../pom.xml)) liga o agente nos testes e gera `target/site/jacoco/jacoco.xml` no `verify`. O SonarCloud lê esse arquivo (`sonar.coverage.jacoco.xmlReportPaths`). O relatório também vai para o artefato `relatorios-de-teste` da execução (7 dias) |
| Configuração | Chave do projeto e organização pelas variáveis `SONAR_PROJECT_KEY` e `SONAR_ORG` do GitHub; token pelo *secret* `SONAR_TOKEN`. Nenhum valor no repositório |
| Sem token | O passo é pulado (por exemplo, em um PR vindo de fork, que não recebe os *secrets*). O build e os testes rodam normalmente |
| O que é enviado | Só o código-fonte, as classes compiladas e os relatórios de teste e cobertura. Nunca o banco nem dados de alunos |
| Onde ver o resultado | No PR: o *check* "SonarCloud Code Analysis" (aba Checks e lista de verificações no fim da conversa) e o comentário do SonarCloud com o quality gate |

### Diferenças em relação ao ponto de partida da arquitetura §7

- o token não fica no `env` do job inteiro: o job só sabe **se** o token existe
  (`TEM_SONAR_TOKEN`), e o valor vai apenas para o passo da análise, para que o passo dos
  testes não o receba;
- usa `./mvnw` (como na T-05) e a versão do `sonar-maven-plugin` fica fixada no
  `pom.xml`; `sonar.host.url` também fica no `pom.xml`;
- a análise reaproveita as classes e a cobertura do passo de testes, sem rodá-los de novo;
- cache de `~/.sonar/cache` para acelerar as execuções.

### Execução local (12/11/2026)

`./mvnw -B verify` na branch `t-44-sonarcloud`: 771 testes, sem falhas, `BUILD SUCCESS`,
com o `jacoco.xml` gerado. Cobertura medida pelo JaCoCo: linhas 97,0%, desvios
(*branches*) 85,7%, métodos 98,4%. O número que vale para o quality gate é o do
SonarCloud no PR.

## Busca de segredos no repositório (RNF04, T-44)

Feita em 12/11/2026 na branch `t-44-sonarcloud`, fora de `target/`:

| Busca | Resultado |
|---|---|
| Arquivos `*.pem`, `*.p12`, `*.pfx`, `*.jks`, `*.key`, `*.keystore`, `*.crt`, `*.cer` | Nenhum |
| Chaves privadas (`BEGIN … PRIVATE KEY`), tokens do SonarCloud (`sqp_`, `squ_`) e do GitHub (`ghp_`), `SONAR_TOKEN` com valor | Nenhum |

O SonarCloud também aponta segredos no código (regras de *security hotspot*), a cada PR.

## Problemas apontados e não corrigidos

Regra da T-44: são corrigidos os de severidade **Blocker** e **Critical** e todos os de
**segurança** (vulnerabilidades e *security hotspots*). Os demais ficam listados aqui,
com o motivo.

### Levantamento de 12/11/2026

Lista lida pela API pública do SonarCloud (`api/issues/search`, problemas não resolvidos
da `main`) e `api/hotspots/search`: **58 problemas**, **nenhum de segurança** e **nenhum
*security hotspot***. Quality gate do PR #45 (T-44): **OK**.

| Qualidade | Total | Corrigidos na T-44 | Ficaram (tabela abaixo) |
|---|---|---|---|
| Security | 0 | — | — |
| Reliability | 8 | 7 (S8786: regex dos testes) | 1 (S9379, `autofocus`) |
| Maintainability, High/Critical | 18 | 14 (S1192 em `RegistrosDaPortaria`, S6809, S7761 ×10) | 4 (plsql:S1192 na V1) |
| Maintainability, Medium | 19 | 9 (S5778 ×4, S3358, S6819 ×4) | 10 (S2925 ×3, S6819 ×7) |
| Maintainability, Low/Info | 13 | 10 (S1128, S5838 ×2, S6582 ×3, S7765 ×2, S1135 ×2) | 3 (S101, S7718 ×2) |

As correções não mudam o comportamento do sistema. Os S1135 eram falso positivo: o Sonar
leu o "todo" de "todo pedido" como TODO; o comentário passou a dizer "cada pedido". Nos
S6819 corrigidos, a região de status virou `<output>` (`#aviso-leitor` e `#monitor-aluno`
na portaria, `#anuncio-presentes` e `#aviso-expirada` no login), que já tem o papel
status e anuncia sem mover o foco.

### Não corrigidos

| Regra | Severidade (qualidade) | Arquivo | Motivo de não corrigir |
|---|---|---|---|
| Web:S9379 (`autofocus`) | Medium (Reliability) | `templates/portaria/inicio.html:46` | O [RNF07](../../requirements/rnf/RNF07.md) pede o campo "Leitura da carteirinha" com foco automático: o leitor USB digita no campo que tem o foco, e a tela só serve para isso. Decisão do grupo de manter (12/11/2026); marcar como "Accepted" no SonarCloud. Trocar o atributo pelo `campo.focus()` do `portaria.js` só esconderia o apontamento |
| plsql:S1192 ×4 (literal repetido) | Critical/High (Maintainability) | `db/migration/V1__esquema_inicial.sql:145-146` | A V1 já foi aplicada: mudar o arquivo muda o checksum do Flyway e quebra todo banco já migrado. Além disso, o arquivo é DDL do MySQL, sem constantes; o Sonar o leu como PL/SQL da Oracle. Ignorar a regra nas migrações pelo `pom.xml` fica para decisão do grupo |
| java:S2925 ×3 (`Thread.sleep`) | Medium (Maintainability) | `EnvioDeNotificacoesTests.java:275` e `:433`, `LeituraConcorrenteTests.java:211` | São esperas de propósito nos testes: provar que nada é enviado depois de um *rollback*, a espera com prazo do `esperarAte` e o intervalo entre as leituras concorrentes. Tirar exigiria uma biblioteca nova (Awaitility), fora do escopo da T-44 |
| Web:S6819 ×7 (`role="status"`) | Medium (Maintainability) | `alunos/editar.html:13`, `alunos/inicio.html:39`, `alunos/revogacao.html:13`, `carteirinhas/imprimir.html:17`, `portaria/inicio.html:60` (`#painel-funcionario`), `portaria/manual-busca.html:14` e `:59` | Essas regiões têm `<div>` e `<p>` dentro (e o `#painel-funcionario` recebe blocos montados pelo `portaria.js`). O `<output>` só aceita conteúdo de frase, então a troca exigiria refazer a marcação das regiões e os testes de acessibilidade (RNF06, CA-RF22-06). Fica para quando cada tela for mexida |
| java:S101 (nome de classe) | Low (Maintainability) | `carga/V2__CargaFicticia.java:53` | O Flyway exige o nome `V<versão>__<descrição>` na migração Java |
| javascript:S7718 ×2 (`catch (erro)` → `error_`) | Low (Maintainability) | `static/js/portaria.js:127` e `:156` | O projeto escreve os nomes em português (CLAUDE.md §6) |
