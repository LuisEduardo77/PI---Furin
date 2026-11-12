# Furin Access — índice da documentação

> **Identificação simples, acesso seguro!**
> Projeto Integrador da equipe FURIN — TADS, Centro Universitário Senac Santo Amaro, 2026.

Este é o índice da documentação: estado do trabalho, como ler o estado de cada requisito,
organização dos documentos e convenções. Para **instalar, rodar e usar** o sistema, veja o
[README da raiz](../README.md).

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

Atualizado em 12/11/2026 (revisão final da T-46).

| Item | Estado | Observação |
|---|---|---|
| Atividades 1 a 14 (fontes) | Entregues | Analisadas: A01–A05, A07–A11, A13 e A14. As Atividades 6 e 12 não foram recebidas para análise |
| Decisões do grupo | Registradas | ATU-01 a ATU-07 (18/10/2026), DEC-01 a DEC-23 e, na revisão T-00, DEC-24 a DEC-29, mais as complementares DEC-30 a DEC-36 (19/10/2026), **confirmadas pelo grupo**. Em 21/10/2026, DEC-37 a DEC-39 (perfil único `FUNCIONARIO`), pedidas por Luis Eduardo Belo Silva e **ainda sem a confirmação dos demais integrantes**. Tudo em [`docs/rastreabilidade.md`](rastreabilidade.md) |
| Pendências | **Todas resolvidas** | PEND-01 a PEND-16 resolvidas ou obsoletas (DEC-28 a DEC-39). Guarda de dados por 5 anos (DEC-36). As atividades entregues não serão reenviadas; onde divergirem, valem estes Markdown (DEC-35) ([`rastreabilidade.md`](rastreabilidade.md) §3) |
| Documentação Markdown | **Revisada em 19/10/2026** (T-00) e **atualizada em 21/10/2026** (DEC-37 a DEC-39) | Os 46 arquivos foram escritos com apoio de IA (ver [`docs/registro-de-ia.md`](registro-de-ia.md)). Os 41 de requisitos e documentação e o `CLAUDE.md` foram revisados pelo grupo. Os 4 resumos (20/10/2026) estão em `Rascunho`, aguardando a leitura do grupo. **Conferência de consistência** em 10 itens feita em 19/10/2026, com as correções aplicadas. Em 21/10/2026, o perfil único foi aplicado a todos os arquivos. RF17, RF24 e RF25 ficaram **fora do escopo**, com os arquivos mantidos para não perder os IDs |
| Repositório no GitHub | **Criado** (DEC-24) | `https://github.com/LuisEduardo77/PI---Furin.git` |
| Código-fonte | **Projeto gerado** (T-04, 29/10/2026) | Spring Boot 4.1.2 e Java 21, com os perfis `prototipo` e `test`. Esquema do banco criado pelo Flyway na partida (`V1__esquema_inicial.sql`, T-06). Parâmetros `furin.*` no `application.yml`, validados na partida (T-07). Layout-base acessível e página temporária `/exemplo/layout` (T-08). Spring Security base: negar por padrão, CSRF, bcrypt e cabeçalhos (T-09). Log de auditoria mascarado em `logs/auditoria.log` (T-10). Carga fictícia no perfil `prototipo` (T-11): antes da primeira partida, defina `FURIN_SENHA_ATE_DEMO` e `FURIN_SENHA_GESTAO_DEMO` (pelo menos 8 caracteres), senão a aplicação não sobe. Tela "Login \| Furin Access" com as contas do banco (`ate.demo` e `gestao.demo`), portaria provisória com o menu único e "Sair" (T-12). Mensagem única para credencial inválida e conta inativa, campo vazio sem consultar a conta e `LOGIN_FALHA` no log (T-13). Bloqueio do login depois de 5 falhas seguidas, por 15 minutos (`furin.login.*`), igual para logins inexistentes (T-14). Troca obrigatória da senha temporária, senha temporária vencida recusada e "Alterar senha" (T-15). Sessão que expira depois de 30 minutos sem uso, com aviso acessível 60 s antes e "Continuar conectado" (T-16). Conta desativada durante a sessão encerra a sessão, página 403 acessível e teste de autorização de todas as rotas (T-17). Portaria antecipada: tipo automático (T-25), `POST /portaria/leituras` (T-26), giro simulado `POST /portaria/giros`, só no perfil `prototipo` (T-28), e a tela `/portaria` com o painel do funcionário, o monitor do aluno e as simulações identificadas (T-30). Área privada de fotos e termos fora do projeto, com tipo validado pelo conteúdo e entrega em `GET /arquivos/{chave}` só para funcionário autenticado; a portaria mostra a foto real (T-18). Formulário "Novo aluno" (T-19). Conferência do termo de consentimento no cadastro e registro da revogação, que inativa o aluno, revoga a carteirinha e inativa os contatos (T-20). Matrícula repetida recusada, inclusive de aluno inativo, e código de carteirinha sem repetição, com novas tentativas na colisão (T-21). O cadastro passou a ser salvo com a carteirinha emitida; QR Code, página de impressão de 85,6 × 54 mm e segunda via com confirmação (T-22). Edição do aluno com resumo "antes → depois", troca de turma sem mudar o histórico, substituição do contato principal, aviso por e-mail a pedido do responsável e inativação (T-23). Busca por nome ou matrícula na tela Alunos, com o filtro "Incluir inativos" (T-24). Painel Presentes com o total de hoje, a contagem por turma (todas as turmas ativas, inclusive com zero), as saídas pendentes de dias anteriores com "Registrar saída de …", atualização a cada 30 s e botão "Atualizar"; o contador da portaria muda a cada giro confirmado (T-34). Notificação `PENDENTE` ao contato principal criada na mesma transação do giro e do registro manual, respeitando a opção "receber" (T-38). Envio simulado depois do commit, em outra thread: a notificação fica `SIMULADA`, uma falha conta até 3 tentativas sem mudar a movimentação, e a portaria mostra "Simulação — nenhum e-mail foi enviado" com o texto do e-mail (T-39). Histórico `/historico`, só de consulta, com filtros de período (padrão hoje, até 31 dias), turma e aluno, 50 por página, a turma do dia do evento, os dois horários e o motivo dos registros manuais e o status do aviso ao responsável (T-35). Versão para impressão do histórico, com os mesmos filtros, até 1.000 registros, cabeçalho com a escola, a emissão e o total, rodapé de uso restrito, botão "Imprimir" e registro no log, sem exportação (T-36). Tela "Contas da equipe": criar conta com login único e perfil sempre `FUNCIONARIO`, senha temporária de 12 caracteres exibida uma vez (sem cache) e válida por 72 h, gerar nova senha, desativar e reativar, cada ação com confirmação; ninguém desativa a própria conta (T-43). Para rodar localmente, copie `application-local.yml.example` para `application-local.yml` e coloque a senha do usuário `furin` |
| Pipeline de CI (GitHub Actions + SonarCloud) | GitHub Actions **configurado e verde** (T-05, PR #4). SonarCloud com cobertura do JaCoCo **configurado e com quality gate OK** nos PRs #45 e #46 (T-44; [evidência](evidencias/sonarcloud.md)) | A13 p.6 |
| Notificação por e-mail | **Simulada** (DEC-32): criação na confirmação (T-38) e envio simulado depois do commit, com o aviso na tela (T-39) | Nenhuma mensagem real é enviada. Não há WhatsApp (DEC-37) |
| Leitura do QR Code | **Simulada** (T-26, T-30; RF09, RF14, RNF07) | Sem leitor físico (DEC-25): campo de leitura (digitar ou colar) e painel "Simular leitura (protótipo)", só no perfil `prototipo` |
| Giro da catraca | **Simulado** (T-28; RF09, RF14) | Botão "Simular passagem (giro)", só no perfil `prototipo`. A integração física fica fora do MVP |
| Acessibilidade | **Revisão do código feita** (T-45, 12/11/2026) | Falta a rodada manual (teclado, NVDA, Lighthouse), com a [checklist](evidencias/acessibilidade.md) ainda em branco; RNF06 e RNF01 em `Planejada` |
| Demonstração final | **Roteiro escrito** (T-46, 12/11/2026; [roteiro](evidencias/roteiro-demonstracao.md)) | Falta o ensaio do grupo |
| Hospedagem | **Local** (DEC-31) | A aplicação roda no computador da equipe. O GitHub Pages pode publicar só a documentação |
| HTTPS | **Planejado** | Não verificado, porque a aplicação roda local com HTTP (RNF04) |
| Backup | **Implementado e com restauração testada** em 12/11/2026 (T-47, DEC-30; [evidência](evidencias/backup-restauracao.md)) | `scripts/backup.ps1` e `scripts/backup.sh`: `mysqldump` com usuário só de leitura e `.zip` das fotos e termos, 7 cópias fora do projeto. Passo a passo em [`docs/backup-e-restauracao.md`](backup-e-restauracao.md) |
| Proteção de logs | **Planejada** | Só será "verificada" depois de configurada e testada. O conteúdo já é protegido: o log de auditoria tem e-mail, login e código mascarados e nenhuma senha, com teste (T-10, RNF05) |

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
(A10 p.4). Todos os requisitos estão em `Documentação: Revisada`. A **revisão final da
T-46 (12/11/2026)** conferiu os critérios de aceitação de cada RF e RNF contra os testes
e as evidências. Nenhum está `Verificada`, porque as verificações manuais (teclado,
leitor de tela, navegadores, medição na tela) ainda não foram registradas.

| Estado | Requisitos |
|---|---|
| `Implementada` | RF01 a RF08, RF10 a RF13, RF15, RF16, RF18 a RF20, RF22, RF23, RF26; RNF02, RNF03, RNF05, RNF08 |
| `Simulada` | RF09 e RF14 (leitura e giro), RF21 (e-mail), RNF07 (leitor e catraca) |
| `Planejada` | RNF01 e RNF06 (só critérios manuais, que dependem da rodada da T-45); RNF04 (HTTPS, não verificado no protótipo local, DEC-31) |
| Fora do escopo | RF17, RF24 e RF25 (DEC-37, não implementar) |

O que falta em cada um está escrito no campo Implementação do próprio arquivo. Em
resumo: a conferência manual com teclado e leitor de tela (quase todos os RF com tela), a
medição do tempo da leitura na tela (RNF02, RF09, RF14), a visualização de impressão nos
três navegadores (RF20), o fluxo alternativo A1 do cadastro, que reaproveita o contato de
um irmão (RF04), a sugestão de aluno enquanto digita no filtro do histórico (RF18), as
buscas de senha como passo automático da CI (RNF03) e o ensaio do
[roteiro da demonstração](evidencias/roteiro-demonstracao.md). O teste de autorização de
todas as rotas foi rodado de novo no fim de cada fase e na T-46.

## Organização dos documentos

Os arquivos de requisitos e de documentação estão em `Documentação: Revisada`, com as
decisões da T-00 aplicadas. Em 21/10/2026 entrou o perfil único (DEC-37 a DEC-39), que
ainda precisa ser confirmado pelos demais integrantes. "Atualizada" na tabela abaixo
indica os arquivos alterados nessa data. Os quatro **resumos** são um atalho para quem quer uma visão
geral: eles não trazem decisões novas e, se discordarem dos arquivos detalhados, valem
os arquivos detalhados.

| Caminho | Conteúdo | Situação |
|---|---|---|
| [`README.md`](../README.md) | Como instalar, rodar e usar o sistema, com o roteiro de demonstração | Atualizada (11/11/2026) |
| `docs/README-documentacao.md` | Este arquivo: índice da documentação, estado e convenções (antes era o `README.md` da raiz) | Atualizada (12/11/2026) |
| [`CLAUDE.md`](../CLAUDE.md) | Instruções para o Claude Code: fontes da verdade, como trabalhar, regras invioláveis e o que fica fora do MVP | Atualizada (21/10/2026) |
| [`docs/escopo-e-perfis.md`](escopo-e-perfis.md) | Contexto, solução escolhida, escopo do MVP, perfil único, permissões e glossário | Atualizada (21/10/2026) |
| [`docs/regras-de-negocio.md`](regras-de-negocio.md) | Regras RN01–RN17 (RN13 obsoleta), medidas definidas e o que fica para a implantação | Atualizada (21/10/2026) |
| [`docs/arquitetura-e-dados.md`](arquitetura-e-dados.md) | Componentes, versões, fluxos, parâmetros, modelo relacional, dicionário, DDL de referência (validado na T-03) e CI | Atualizada (27/10/2026) |
| [`docs/rastreabilidade.md`](rastreabilidade.md) | Fontes, decisões (ATU, DEC), pendências (PEND), propostas, matriz requisito → fonte → arquivo → verificação, alterações aprovadas | Atualizada (12/11/2026) |
| [`docs/plano-de-implementacao.md`](plano-de-implementacao.md) | Tarefas T-00 a T-47, ordenadas por dependência, prontas para cartões do Trello (T-37, T-40 a T-42 canceladas), com o que foi feito em cada uma | Atualizada (12/11/2026) |
| [`docs/evidencias/`](evidencias/) | Evidências das tarefas: DDL (T-03), desempenho (T-33), SonarCloud (T-44), acessibilidade (T-45, checklist em branco), backup (T-47) e o [roteiro da demonstração final](evidencias/roteiro-demonstracao.md) (T-46) | Atualizada (12/11/2026) |
| [`docs/registro-de-ia.md`](registro-de-ia.md) | Ferramentas, modelos, datas, finalidades e prompts de IA usados | Atualizada (12/11/2026) |
| [`docs/backup-e-restauracao.md`](backup-e-restauracao.md) | Como fazer o backup do protótipo, testar a restauração num banco vazio e restaurar de verdade (T-47, DEC-30) | Nova (12/11/2026) |
| [`requirements/rf/`](../requirements/rf/) `RF01.md` … `RF26.md` | Um arquivo por requisito funcional (26; RF17, RF24 e RF25 fora do escopo) | Atualizada (21/10/2026); campo Implementação revisado em 12/11/2026 (T-46) |
| [`requirements/rnf/`](../requirements/rnf/) `RNF01.md` … `RNF08.md` | Um arquivo por requisito não funcional (8) | Atualizada (21/10/2026); campo Implementação revisado em 12/11/2026 (T-46) |
| [`docs/resumo-ferramentas.md`](resumo-ferramentas.md) | Resumo das ferramentas: o que é cada uma, para que serve e em que estado está | Rascunho (resumo, atualizado em 21/10/2026) |
| [`docs/resumo-funcionamento.md`](resumo-funcionamento.md) | Como o sistema vai funcionar no dia a dia, em linguagem simples | Rascunho (resumo, atualizado em 21/10/2026) |
| [`docs/resumo-rf.md`](resumo-rf.md) | Os 26 RF em tabelas por área, com o que fazem e a meta no protótipo | Rascunho (resumo, atualizado em 21/10/2026) |
| [`docs/resumo-rnf.md`](resumo-rnf.md) | Os 8 RNF em uma página, com o que cada um exige | Rascunho (resumo, atualizado em 21/10/2026) |

### Antes de começar a programar

1. ~~**Revisão em grupo (T-00)**~~ — **concluída em 19/10/2026** (DEC-24 a DEC-36, confirmadas pelo grupo).
   **Falta:** os demais integrantes confirmarem a DEC-37 a DEC-39 (perfil único).
   Uma decisão nova recebe o próximo ID DEC e vai para `docs/rastreabilidade.md`.
2. **Repositório:** já criado (DEC-24). Falta copiar esta documentação para a raiz e
   conferir a proteção da `main` e o `.gitignore` (T-01).
3. ~~**Validação do DDL (T-03)**~~ — **DDL validado em 27/10/2026** no MySQL 8.4.13,
   sem alteração (evidência em [`docs/evidencias/t-03/`](evidencias/t-03/README.md)).
   Usuário do banco exclusivo do projeto (`furin`, sem `root`) criado por
   [`scripts/criar-usuario-banco.sql`](../scripts/criar-usuario-banco.sql). Cada integrante
   roda esse script uma vez no próprio computador.
4. **Desenvolvimento com o Claude Code:** ele começa pelo [`CLAUDE.md`](../CLAUDE.md) e
   segue o plano a partir da T-01.
5. **Atualizar o estado:** quando uma função for simulada, implementada ou verificada,
   mudar o campo **Implementação** do RF ou RNF e registrar a evidência no cartão do
   Trello.

## Ordem recomendada de leitura

**Para uma visão rápida:** [`resumo-funcionamento.md`](resumo-funcionamento.md),
depois [`resumo-rf.md`](resumo-rf.md), [`resumo-rnf.md`](resumo-rnf.md) e
[`resumo-ferramentas.md`](resumo-ferramentas.md).

**Para trabalhar no projeto:**

1. **Este índice**: projeto, estado e convenções. Para rodar o sistema, o
   [README da raiz](../README.md).
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
