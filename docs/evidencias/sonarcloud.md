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

_Aguardando a primeira análise no PR da T-44._

| Regra | Severidade | Tipo | Arquivo | Motivo de não corrigir |
|---|---|---|---|---|
