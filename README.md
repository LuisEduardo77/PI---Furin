# Furin Access

> **Identificação simples, acesso seguro!**
> Projeto Integrador da equipe FURIN — TADS, Centro Universitário Senac Santo Amaro, 2026.

## Sobre o projeto

O Furin Access é uma aplicação web que registra a entrada e a saída dos alunos da
EMEF Eng. José Amadei, que tem mais de 800 alunos no Ensino Fundamental I e II.
O aluno apresenta uma carteirinha com QR Code a um leitor USB na portaria. Cada
leitura válida libera a passagem, e a movimentação é registrada depois da passagem
pela catraca. No protótipo, a leitura e o giro são **simulados** pelas telas, porque
o grupo não tem leitor nem catraca (DEC-18, DEC-25). Com esses registros, o sistema:

- mostra quantos alunos estão presentes, no total e por turma, para apoiar a merenda;
- mantém um histórico consultável e imprimível para a Gestão Escolar;
- avisa por e-mail o contato principal de cada aluno.

Só os **funcionários** da escola têm conta, todos com o mesmo perfil `FUNCIONARIO`: o
ATE e a Gestão Escolar são cargos diferentes, mas no sistema usam tudo. Os **alunos** só
apresentam a carteirinha, e os **pais/responsáveis** só recebem o e-mail (DEC-37).

Tecnologias: Java 21, Spring Boot 4.1.x, MySQL 8.4, Thymeleaf, HTML, CSS, JavaScript e
Spring Security. O Spring Mail fica previsto, mas o envio de e-mail é sempre simulado
(DEC-32). Versionamento no GitHub, com GitHub Actions, Maven e
SonarCloud como apoio à qualidade.

## Estado atual do trabalho

Atualizado em 21/10/2026.

| Item | Estado | Observação |
|---|---|---|
| Atividades 1 a 14 (fontes) | Entregues | Analisadas: A01–A05, A07–A11, A13 e A14. As Atividades 6 e 12 não foram recebidas para análise |
| Decisões do grupo | Registradas | ATU-01 a ATU-07 (18/10/2026), DEC-01 a DEC-23 e, na revisão T-00, DEC-24 a DEC-29, mais as complementares DEC-30 a DEC-36 (19/10/2026), **confirmadas pelo grupo**. Em 21/10/2026, DEC-37 a DEC-39 (perfil único `FUNCIONARIO`), pedidas por Luis Eduardo Belo Silva e **ainda sem a confirmação dos demais integrantes**. Tudo em [`docs/rastreabilidade.md`](docs/rastreabilidade.md) |
| Pendências | **Todas resolvidas** | PEND-01 a PEND-16 resolvidas ou obsoletas (DEC-28 a DEC-39). Guarda de dados por 5 anos (DEC-36). As atividades entregues não serão reenviadas; onde divergirem, valem estes Markdown (DEC-35) ([`rastreabilidade.md`](docs/rastreabilidade.md) §3) |
| Documentação Markdown | **Revisada em 19/10/2026** (T-00) e **atualizada em 21/10/2026** (DEC-37 a DEC-39) | Os 46 arquivos foram escritos com apoio de IA (ver [`docs/registro-de-ia.md`](docs/registro-de-ia.md)). Os 41 de requisitos e documentação e o `CLAUDE.md` foram revisados pelo grupo. Os 4 resumos (20/10/2026) estão em `Rascunho`, aguardando a leitura do grupo. **Conferência de consistência** em 10 itens feita em 19/10/2026, com as correções aplicadas. Em 21/10/2026, o perfil único foi aplicado a todos os arquivos. RF17, RF24 e RF25 ficaram **fora do escopo**, com os arquivos mantidos para não perder os IDs |
| Repositório no GitHub | **Criado** (DEC-24) | `https://github.com/LuisEduardo77/PI---Furin.git` |
| Código-fonte | **Projeto gerado** (T-04, 29/10/2026) | Spring Boot 4.1.2 e Java 21, com os perfis `prototipo` e `test`. Nenhuma funcionalidade implementada ainda. Para rodar localmente, copie `application-local.yml.example` para `application-local.yml` e coloque a senha do usuário `furin` |
| Pipeline de CI (GitHub Actions + SonarCloud) | GitHub Actions **configurado e verde** (T-05, PR #4). SonarCloud **planejado** (T-44) | A13 p.6 |
| Notificação por e-mail | **Sempre simulada** (DEC-32) | Nenhuma mensagem real é enviada. Não há WhatsApp (DEC-37) |
| Leitura do QR Code | **Planejada como função simulada** no protótipo | Sem leitor físico (DEC-25): campo de leitura (digitar ou colar) e painel "Simular leitura" |
| Giro da catraca | **Planejado como função simulada** no protótipo | A integração física fica fora do MVP |
| Hospedagem | **Local** (DEC-31) | A aplicação roda no computador da equipe. O GitHub Pages pode publicar só a documentação |
| HTTPS | **Planejado** | Não verificado, porque a aplicação roda local com HTTP (RNF04) |
| Backup | **Planejado** (T-47, DEC-30) | Script simples com o banco e os arquivos, 7 cópias e restauração testada |
| Proteção de logs | **Planejada** | Só será "verificada" depois de configurada e testada |

### Como ler o estado de cada requisito

Cada RF e RNF tem dois campos de estado: o da documentação e o da implementação.

| Campo | Valores | Significado |
|---|---|---|
| **Documentação** | `Rascunho` | Texto preparado, ainda não aprovado pelo grupo |
| | `Revisada` | Conferida e aprovada pelo grupo, com data |
| **Implementação** | `Planejada` | Descrita para ser construída. Ainda não há código |
| | `Simulada` | O protótipo mostra o comportamento, mas sem o efeito real (por exemplo, nenhum e-mail sai), **com indicação visível de simulação** |
| | `Implementada` | O código executa a função de verdade |
| | `Verificada` | Um teste ou inspeção registrada comprovou o funcionamento |

Uma tela que mostra um comportamento não prova o controle técnico por trás dela
(A10 p.4). **Hoje todos os requisitos estão em `Documentação: Revisada` e
`Implementação: Planejada`**, menos RF17, RF24 e RF25, que estão **fora do escopo**
(não implementar).

## Organização dos documentos

Os arquivos de requisitos e de documentação estão em `Documentação: Revisada`, com as
decisões da T-00 aplicadas. Em 21/10/2026 entrou o perfil único (DEC-37 a DEC-39), que
ainda precisa ser confirmado pelos demais integrantes. "Atualizada" na tabela abaixo
indica os arquivos alterados nessa data. Os quatro **resumos** são um atalho para quem quer uma visão
geral: eles não trazem decisões novas e, se discordarem dos arquivos detalhados, valem
os arquivos detalhados.

| Caminho | Conteúdo | Situação |
|---|---|---|
| `README.md` | Este arquivo | Atualizada (21/10/2026) |
| [`CLAUDE.md`](CLAUDE.md) | Instruções para o Claude Code: fontes da verdade, como trabalhar, regras invioláveis e o que fica fora do MVP | Atualizada (21/10/2026) |
| [`docs/escopo-e-perfis.md`](docs/escopo-e-perfis.md) | Contexto, solução escolhida, escopo do MVP, perfil único, permissões e glossário | Atualizada (21/10/2026) |
| [`docs/regras-de-negocio.md`](docs/regras-de-negocio.md) | Regras RN01–RN17 (RN13 obsoleta), medidas definidas e o que fica para a implantação | Atualizada (21/10/2026) |
| [`docs/arquitetura-e-dados.md`](docs/arquitetura-e-dados.md) | Componentes, versões, fluxos, parâmetros, modelo relacional, dicionário, DDL de referência (validado na T-03) e CI | Atualizada (27/10/2026) |
| [`docs/rastreabilidade.md`](docs/rastreabilidade.md) | Fontes, decisões (ATU, DEC), pendências (PEND), propostas, matriz requisito → fonte → arquivo → verificação, alterações aprovadas | Atualizada (21/10/2026) |
| [`docs/plano-de-implementacao.md`](docs/plano-de-implementacao.md) | Tarefas T-00 a T-47, ordenadas por dependência, prontas para cartões do Trello (T-37, T-40 a T-42 canceladas) | Atualizada (21/10/2026) |
| [`docs/registro-de-ia.md`](docs/registro-de-ia.md) | Ferramentas, modelos, datas, finalidades e prompts de IA usados | Atualizada (21/10/2026) |
| [`requirements/rf/`](requirements/rf/) `RF01.md` … `RF26.md` | Um arquivo por requisito funcional (26; RF17, RF24 e RF25 fora do escopo) | Atualizada (21/10/2026) |
| [`requirements/rnf/`](requirements/rnf/) `RNF01.md` … `RNF08.md` | Um arquivo por requisito não funcional (8) | Atualizada (21/10/2026) |
| [`docs/resumo-ferramentas.md`](docs/resumo-ferramentas.md) | Resumo das ferramentas: o que é cada uma, para que serve e em que estado está | Rascunho (resumo, atualizado em 21/10/2026) |
| [`docs/resumo-funcionamento.md`](docs/resumo-funcionamento.md) | Como o sistema vai funcionar no dia a dia, em linguagem simples | Rascunho (resumo, atualizado em 21/10/2026) |
| [`docs/resumo-rf.md`](docs/resumo-rf.md) | Os 26 RF em tabelas por área, com o que fazem e a meta no protótipo | Rascunho (resumo, atualizado em 21/10/2026) |
| [`docs/resumo-rnf.md`](docs/resumo-rnf.md) | Os 8 RNF em uma página, com o que cada um exige | Rascunho (resumo, atualizado em 21/10/2026) |

### Antes de começar a programar

1. ~~**Revisão em grupo (T-00)**~~ — **concluída em 19/10/2026** (DEC-24 a DEC-36, confirmadas pelo grupo).
   **Falta:** os demais integrantes confirmarem a DEC-37 a DEC-39 (perfil único).
   Uma decisão nova recebe o próximo ID DEC e vai para `docs/rastreabilidade.md`.
2. **Repositório:** já criado (DEC-24). Falta copiar esta documentação para a raiz e
   conferir a proteção da `main` e o `.gitignore` (T-01).
3. ~~**Validação do DDL (T-03)**~~ — **DDL validado em 27/10/2026** no MySQL 8.4.13,
   sem alteração (evidência em [`docs/evidencias/t-03/`](docs/evidencias/t-03/README.md)).
   Usuário do banco exclusivo do projeto (`furin`, sem `root`) criado por
   [`scripts/criar-usuario-banco.sql`](scripts/criar-usuario-banco.sql). Cada integrante
   roda esse script uma vez no próprio computador.
4. **Desenvolvimento com o Claude Code:** ele começa pelo [`CLAUDE.md`](CLAUDE.md) e
   segue o plano a partir da T-01.
5. **Atualizar o estado:** quando uma função for simulada, implementada ou verificada,
   mudar o campo **Implementação** do RF ou RNF e registrar a evidência no cartão do
   Trello.

## Ordem recomendada de leitura

**Para uma visão rápida:** [`resumo-funcionamento.md`](docs/resumo-funcionamento.md),
depois [`resumo-rf.md`](docs/resumo-rf.md), [`resumo-rnf.md`](docs/resumo-rnf.md) e
[`resumo-ferramentas.md`](docs/resumo-ferramentas.md).

**Para trabalhar no projeto:**

1. **Este README**: projeto, estado e convenções.
2. **`docs/escopo-e-perfis.md`**: o que entra no MVP e quem faz o quê.
3. **`docs/regras-de-negocio.md`**: como a leitura, o giro, o registro manual e as
   notificações funcionam.
4. **`requirements/rf/`**, na ordem do fluxo:
   - acesso: RF01–RF03;
   - cadastro: RF04–RF08;
   - portaria: RF09–RF15, RF22, RF23;
   - consultas: RF16–RF20;
   - notificação: RF21 (RF24 e RF25 fora do escopo);
   - contas da equipe: RF26 (RF17 fora do escopo).
5. **`requirements/rnf/`**: RNF01–RNF08.
6. **`docs/arquitetura-e-dados.md`**: como o sistema será construído.
7. **`docs/rastreabilidade.md`**: de onde vem cada requisito e o que mudou.
8. **`docs/plano-de-implementacao.md`**: por onde começar o código.
9. **`docs/registro-de-ia.md`**: uso de IA na documentação.

Quem vai programar pode ir direto do item 3 para os itens 6 e 8, consultando os RF
conforme as tarefas.

## Convenções

### Marcas de origem

| Marca | Significado |
|---|---|
| **[DOC]** | Está escrito em um documento entregue. A fonte aparece como `A08 p.3` (tabela abaixo) |
| **[ATU-nn]** | Atualização de escopo informada pelo grupo em 18/10/2026 |
| **[DEC-nn]** | Decisão do grupo, de 19/10/2026 (DEC-37 a DEC-39: 21/10/2026). DEC-28 aprova os itens da pauta T-00, citados como "DEC-28, A1", "DEC-28, E4" e assim por diante |
| **[PROP]** | Sugestão de detalhe (texto de tela, nome de propriedade, detalhe de implementação) que não precisou de decisão do grupo. Pode ser ajustada na implementação, registrando no PR. As propostas de comportamento foram aprovadas na T-00 (DEC-28) |
| **[PEND-nn]** | Ponto que estava indefinido. Os que ficam para a implantação aparecem como **"a definir"** |

### Códigos das fontes

A paginação conta a capa como p.1.

| Código | Arquivo | Conteúdo |
|---|---|---|
| A01 | `atividade01.pdf` | Contexto e comunidade externa |
| A02 | `atividade02.pdf` | Demandas da comunidade |
| A03 | `atividade03.pdf` | Planilha de hipóteses |
| A04 | `atividade04.pdf` | Comparativo de soluções |
| A05 | `Atividade05.pdf` | MVP do sistema |
| A07 | `atividade07.pdf` | User stories |
| A08 | `atividade08.pdf` | Requisitos funcionais e não funcionais |
| A09 | `atividade09.pdf` | Requisitos legais (LGPD, ECA) |
| A10 | `atividade10.pdf` | Segurança da informação (ISO/IEC 27002) |
| A11 | `atividade11.pdf` | Acessibilidade (ABNT NBR 17225, WCAG 2.2) |
| A13 | `atividade13.pdf` | Arquitetura da solução |
| A14 | `atividade14.pdf` | Modelo relacional e dicionário de dados |

### Dados fictícios

Exemplos, testes e cargas iniciais usam **somente dados fictícios**, com e-mails no
domínio `example.com` (A10 §8.33). Nunca coloque dados reais de alunos, responsáveis
ou funcionários no repositório nem em ferramentas de IA.

## Equipe

Adriane Helfstein de Jesus · Gabriel Eiji Shiquedoni · Hugo Rocha da Silva ·
Luis Eduardo Belo Silva
