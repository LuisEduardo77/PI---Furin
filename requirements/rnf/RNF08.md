# RNF08 — Backend em Java (Spring Boot) e banco de dados MySQL

| Campo | Valor |
|---|---|
| **Categoria** | Implementação |
| **Documentação** | Revisada em 19/10/2026 (T-00: DEC-24 a DEC-36) |
| **Implementação** | Planejada, em andamento. Projeto criado na T-04 (Boot 4.1.2) e CI verde na T-05 ([PR #4](https://github.com/LuisEduardo77/PI---Furin/pull/4)): CA-RNF08-03 e 06 com evidência na CI. Faltam CA-RNF08-04 (migrações, T-06), 05 (telas, segurança e e-mail simulado) e 07 (SonarCloud, T-44) |
| **Atualizado em** | 29/10/2026 |

## Descrição

A aplicação é feita em:

- **Java 21**, com **Spring Boot 4.1.x**;
- banco relacional **MySQL 8.4**;
- interface em Thymeleaf, HTML, CSS e JavaScript;
- Spring Security; Spring Mail fica previsto (ATU-06), mas o envio é sempre simulado
  (DEC-32);
- build com Maven.

O código fica versionado no GitHub. A qualidade é apoiada por GitHub Actions e
SonarCloud, em escala proporcional ao PI.

## Justificativa

- A equipe conhece essa stack, e ela já foi definida desde a planilha de hipóteses.
- O modelo relacional da A14 se encaixa nas regras de integridade do projeto:
  restrições UNIQUE, CHECK e bloqueio transacional por aluno.
- Fixar versões estáveis com suporte evita incompatibilidades durante o semestre.

## Fonte documental

| Origem | Conteúdo |
|---|---|
| A08 p.4 (RNF08) | "Backend em Java (Spring Boot) e banco de dados MySQL" — origem: planilha de hipóteses |
| A03 p.1 (H2) | "Backend: Java, Spring Boot. Banco de Dados: MySQL" |
| A13 p.2–3 | Java com Spring Boot e MySQL. Telas com Thymeleaf, HTML, CSS e JavaScript. Spring Security. Spring Mail |
| A13 p.5–6 | GitHub e GitHub Desktop, GitHub Actions com Maven, SonarCloud. A pipeline ainda não foi configurada |
| A14 p.9 | Referências do MySQL 8.4 Reference Manual |
| **ATU-05, ATU-06, ATU-07** | Stack e ferramentas confirmadas |
| **DEC-14** | Modelo relacional com ajuste mínimo |
| **DEC-17** | **Java 21, Spring Boot 4.1.x, MySQL 8.4** |

**Alteração em relação à A08:** as versões foram fixadas (DEC-17). O texto original não
indicava versão.

## Partes do sistema afetadas

Todo o projeto:

- `pom.xml`;
- configuração (`application*.yml`);
- migrações (`db/migration`);
- pipeline (`.github/workflows/ci.yml`);
- ambiente local de cada integrante.

A organização está em [arquitetura-e-dados §4 e §7](../../docs/arquitetura-e-dados.md).

## Critérios de aceitação

| ID | Critério |
|---|---|
| CA-RNF08-01 | O `pom.xml` declara Java **21** (`<java.version>21</java.version>`) e o `spring-boot-starter-parent` na linha **4.1.x**, com o patch fixado |
| CA-RNF08-02 | As versões das bibliotecas gerenciadas pelo Spring Boot não são sobrescritas manualmente sem justificativa registrada no PR |
| CA-RNF08-03 | O ambiente local e o serviço de banco da CI usam **MySQL 8.4** |
| CA-RNF08-04 | As migrações criam o esquema completo de [arquitetura-e-dados §5.5](../../docs/arquitetura-e-dados.md) em um banco vazio, sem erro |
| CA-RNF08-05 | As telas usam Thymeleaf. A autenticação e a autorização usam Spring Security. O envio de e-mail fica atrás de uma interface, e no MVP só existe a implementação simulada (DEC-32). Uma implementação com Spring Mail pode ser acrescentada depois sem mudar o restante |
| CA-RNF08-06 | `mvn -B verify` passa localmente e no GitHub Actions a cada pull request |
| CA-RNF08-07 | A análise do SonarCloud roda na CI e mostra o quality gate no pull request (T-44) |
| CA-RNF08-08 | Nenhum framework de outra plataforma (por exemplo, um framework de SPA ou outro banco) entra sem decisão registrada do grupo |

## Verificação e evidência esperada

| Forma | Como | Evidência |
|---|---|---|
| Revisão do `pom.xml` | CA-RNF08-01, 02 e 08 no PR da tarefa T-04 | Aprovação do PR |
| CI | CA-RNF08-03, 04 e 06: job com o serviço `mysql:8.4` rodando as migrações e os testes | Execução verde no GitHub Actions |
| SonarCloud | CA-RNF08-07 | Quality gate no PR |
| Validação do DDL | T-03: script executado no MySQL 8.4 local, com testes das restrições | Saída do cliente MySQL anexada ao cartão |

## Dependências

| Tipo | Itens |
|---|---|
| RNF | Base de todos os demais |
| Tarefas | T-01, T-03, T-04, T-05, T-06, T-44 |

## Protótipo e implementação

| Aspecto | Protótipo | Implementação |
|---|---|---|
| Execução | Computador da equipe: aplicação e MySQL locais (A13 p.3) | **Local também** (DEC-31). O GitHub Pages só serve páginas estáticas e não executa Java nem MySQL; pode publicar a documentação |
| CI | GitHub Actions compila, testa e analisa. **Não publica** a aplicação (A13 p.6) | Não há publicação da aplicação (DEC-31). O GitHub Pages pode publicar só a documentação |

## Pendências

| Pendência | Situação |
|---|---|
| Patch exato do Spring Boot 4.1.x e do MySQL 8.4 | **a definir** na criação do projeto (T-04), usando a versão estável mais recente de cada linha |
| Uso de Flyway, Spring Data JPA, Bean Validation e ZXing | **Aprovado** (DEC-28, G1; [arquitetura-e-dados §1](../../docs/arquitetura-e-dados.md)) |
| Configuração do GitHub Actions e do SonarCloud | GitHub Actions **configurado** na T-05 (`.github/workflows/ci.yml`), com execução verde no PR #4 e no push da `main` (29/10/2026). SonarCloud planejado na T-44 |
