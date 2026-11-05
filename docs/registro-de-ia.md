# Registro de uso de inteligência artificial

**Documentação:** Revisada em 19/10/2026 (T-00) · **Atualizado em:** 21/10/2026

Este registro reúne o uso de IA declarado pelo grupo em cada atividade e o uso de IA
na produção desta documentação Markdown.

**Critério do registro**

- O que está escrito nas atividades foi **transcrito como está**, com a fonte.
- Os campos que ninguém informou ficam marcados **_a preencher_**.
- O nome do modelo só aparece quando foi declarado ou quando pode ser conferido.

> **Revisão humana.** Todo conteúdo gerado com apoio de IA foi ou será revisado pelo
> grupo antes da entrega. Para o código, a revisão é obrigatória antes do merge
> (A10 §8.28; A13 p.6). A IA apoiou a organização e a redação. **As decisões de
> escopo são do grupo** (ATU e DEC em [`rastreabilidade.md`](rastreabilidade.md)).

## 1. Uso declarado nas atividades entregues

| Atividade | Ferramenta | Modelo/versão declarado | Data | Finalidade declarada | Prompts declarados | Fonte |
|---|---|---|---|---|---|---|
| A01: Contexto | **Não houve uso de IA** (informado pelo grupo em 19/10/2026) | — | — | — | — | A01 p.1–2; confirmação do grupo |
| A02: Demandas | Gemini | "versão lançada no dia 19/08/2026" | 19/10/2026 ¹ | "Enviamos o enunciado da atividade 02, enviamos os dados do projeto, atividade 01, e pedimos direcionamento para realizar a atividade." | Não transcritos | A02 p.3 |
| A03: Planilha de hipóteses | **Não houve uso de IA** (informado pelo grupo em 19/10/2026) | — | — | — | — | A03 p.1; confirmação do grupo |
| A04: Comparativo | **Não houve uso de IA** (informado pelo grupo em 19/10/2026) | — | — | — | — | A04 p.1; confirmação do grupo |
| A05: MVP | Gemini (Google) | "Modelo de atualização contínua" | 19/10/2026 ¹ | Correção gramatical e estruturação lógica do documento, separando os processos gerais do escopo do MVP | Não transcritos | A05 p.4 |
| A07: User stories | ChatGPT (OpenAI), ambiente Codex | "GPT-6 Astra" | 19/10/2026 ¹ | Criar as user stories passo a passo | "Com base no nosso MVP, nos ajude a criar user stories passo a passo enquanto vou revisando" | A07 p.4 |
| A08: RF e RNF | ChatGPT (OpenAI), ambiente Codex | "GPT-6 Astra" | 19/10/2026 ¹ | Criar os RF e RNF | "Com base no nosso MVP e nos user stories, nos ajude a criar os requisitos funcionais e os requisitos não funcionais para que possamos verificar o que será utilizado no nosso projeto." | A08 p.4 |
| A09: Requisitos legais | Gemini (Google) | "Modelo de atualização contínua" | 19/10/2026 ¹ | Identificar a legislação (LGPD, ECA) e mapear os artigos para requisitos técnicos | Não transcritos | A09 p.3 |
| A10: Segurança | ChatGPT (OpenAI) | **Não identificado.** O próprio documento pede que o grupo confira "a identificação do modelo de IA antes da entrega" | 14/10/2026 | Organizar os requisitos de segurança, redigir as explicações e montar a tabela de controles | Dois prompts transcritos em A10 p.5 | A10 p.5 |
| A11: Acessibilidade | ChatGPT (OpenAI) | "Versão web" | 19/10/2026 ¹ | Identificar os requisitos da ABNT NBR 17225:2025 e da WCAG 2.2, propor a implementação, revisar a gramática e formatar | Não transcritos | A11 p.9 |
| A13: Arquitetura | ChatGPT (OpenAI), ambiente Codex | "sessão web de 14/10/2026" | 14/10/2026 | Escolher as tecnologias, organizar a arquitetura, fazer os diagramas, revisar redação e formatação. O documento registra que "Claude é ferramenta prevista para a futura geração do código e não foi usado para produzir este documento" | Trechos transcritos em A13 p.7 | A13 p.7 |
| A14: Modelo relacional | ChatGPT (OpenAI), ambiente Codex | "GPT-6 Astra" | 19/10/2026 ¹ | Analisar os documentos, propor entidades e relacionamentos, fazer diagramas, dicionário e regras de integridade | Dois prompts transcritos em A14 p.9 | A14 p.9 |
| A06, A12 | _a preencher_ (não recebidas para análise) | — | — | — | — | — |

¹ **Data de registro informada pelo grupo (19/10/2026).** A data original de uso nessas
atividades não foi informada. Há sinais de que o uso foi anterior: a A02 cita uma
versão do Gemini de 19/08/2026, e a A14 cita referências técnicas acessadas em
28/09/2026.

## 2. Uso de IA nesta documentação Markdown

| Campo | Registro |
|---|---|
| Ferramenta | Claude (Anthropic), usado em uma conversa de um Projeto no claude.ai ("Furin Access — Documentação do PI"), com ferramentas de leitura de arquivos e de criação de arquivos |
| Modelo | **Claude Opus 5.5, nível "Alto"**, conforme a interface informada pelo integrante. Identificador configurado na sessão: `claude-opus-5-5` |
| Datas | 18/10/2026 (análise), 19/10/2026 (decisões, produção dos lotes, revisão T-00, decisões complementares e revisão de consistência) 20/10/2026 (resumos) e 21/10/2026 (perfil único, DEC-37 a DEC-39) |
| Integrante que conduziu a conversa | Luis Eduardo Belo Silva |
| Material fornecido | PDFs das Atividades 1, 2, 3, 4, 5, 7, 8, 9, 10, 11, 13 e 14. A A01 contém dados reais de identificação da escola e da direção, que não foram copiados para os Markdown. Nenhum dado de aluno real foi enviado |
| Finalidade | Analisar os documentos, apontar divergências, organizar as questões para o grupo decidir e redigir os arquivos Markdown de requisitos, regras, arquitetura, rastreabilidade e plano, além do `CLAUDE.md` com as instruções para o Claude Code e dos quatro resumos (20/10/2026) |
| Consulta externa feita pela IA | Buscas na web em 19/10/2026: (1) versões estáveis de Java, Spring Boot e MySQL, com a escolha final do grupo (DEC-17); (2) textos da LGPD (art. 14 §4º, 15, 16 e 46) e do Marco Civil da Internet (art. 15), para as DEC-30 a DEC-34 |
| O que a IA **não** decidiu | Escopo, perfis, regras de negócio e versões. Tudo isso foi decidido pelo grupo e registrado como ATU/DEC. As propostas da IA ficaram marcadas [PROP] até a revisão T-00, quando o grupo as aprovou (DEC-28). Depois disso, [PROP] marca só sugestões de detalhe |
| Limitações registradas | O DDL de referência não foi executado em um MySQL (T-03 do plano). Os diagramas Mermaid não foram renderizados durante a produção. As citações foram conferidas por script contra o texto extraído dos PDFs, sem leitura visual de cada página. As Atividades 6 e 12 não foram analisadas |
| Revisão humana | **Feita na T-00, em 19/10/2026**, conduzida por Luis Eduardo Belo Silva: decisões DEC-24 a DEC-36. Revisão de consistência em 10 itens pedida pelo grupo e feita pela IA em 19/10/2026 (P10), com as correções aplicadas. Decisões **confirmadas pelos demais integrantes** (Adriane, Gabriel e Hugo), conforme informado por Luis em 19/10/2026. As DEC-37 a DEC-39 (21/10/2026) foram decididas por Luis; a confirmação dos demais integrantes ainda não foi informada |

### 2.1 Prompts usados

Os prompts estão transcritos, com pequenos ajustes de formatação.

<details>
<summary><strong>P1 — 18/10/2026 — Análise inicial</strong></summary>

> "Atue como analista de requisitos de software. Estamos desenvolvendo o Projeto
> Integrador Furin Access, da equipe FURIN, para apoiar o controle de acesso dos alunos
> de uma escola. Sua tarefa inicial é analisar todos os documentos anexados e organizar
> uma base consistente para a criação dos arquivos Markdown de requisitos. Nesta
> primeira resposta, apresente a análise dos documentos e as questões que precisam ser
> resolvidas. A geração dos Markdown completos e a programação ocorrerão depois."
>
> Seções do pedido:
> - **Fontes e rastreabilidade:** inventário, arquivo/página/item, separação entre
>   documentado, atualizado, proposto e indefinido, preservação dos IDs, diferença
>   entre hipóteses, sistemas comparados e MVP.
> - **Decisões atualizadas do grupo:** leitura automática de entrada e saída;
>   notificações no MVP; e-mail que pode ser simulado; consulta breve dos responsáveis;
>   Java, Spring Boot e MySQL; Thymeleaf, HTML, CSS, JS, Spring Security e Spring Mail;
>   GitHub, GitHub Desktop, GitHub Actions, Maven e SonarCloud.
> - **Pontos que exigem atenção:** perfis, acessos, leitura automatizada (repetição,
>   simultaneidade, mudança de dia, falta de registro, fora de sequência), exceções
>   manuais, modelo de dados para responsáveis e notificações, e segurança,
>   acessibilidade e privacidade.
> - **Resultado esperado:** A a F (inventário, resumo, catálogo de RF/RNF,
>   divergências, questões, estrutura dos Markdown).
>
> "Use português claro. Preserve o escopo do PI e utilize dados fictícios nos exemplos.
> Evite preencher lacunas como se fossem decisões já aprovadas."
</details>

<details>
<summary><strong>P2 — 19/10/2026 — Respostas às questões 1 a 17</strong></summary>

> "1 - manter o ID. 2 - 2 segundos para bipadas de mesmo ID. Se o usuário bipar e a
> catraca registrar o giro físico dos braços (via sensor de giro/giroscópio da catraca),
> a janela anti-repetição deve ser derrubada imediatamente para aquele ID. 3 - sim,
> ampliar a RF12. 4 - toda entrada de aluno deverá ter uma saída, mesmo que de forma
> manual. se for manual, colocar motivo de ter sido manual. 5 - foto obrigatória. 6 -
> sim, terá monitor para aluno verificar somente quando a entrada/saída for registrada e
> ele está apto para passar a catraca. para o protótipo resolve uma tela com dois
> painéis. 7 - QR Code. 8 - ficam na área privada do sistema. 9 - será definido um
> contato especifico para receber a notificação. sim existe opção de não receber a
> notificação, mas o cadastro do contato é obrigatório e depois optam por receber ou
> não. 10 - o ATE cria e vincula. senha inicial gerada de forma aleatória e enviada por
> whatsapp, sendo obrigatória a alteração após o primeiro acesso, para o protótipo
> bastam. podemos seguir com o que a A13 indica, mostrar por alguns dias. 11 - seguir a
> RF17, apenas consulta. 12 - sim, a gestão vê. o ATE pode imprimir relatórios. 13 - ATE
> cria contas de usuários. seguir sua recomendação. 14 - ajuste mínimo. 15 - aprovado.
> 16 - IDs originais. 17 - as versões mais recentes."
</details>

<details>
<summary><strong>P3 — 19/10/2026 — Respostas R1 a R7 e versões</strong></summary>

> "R1 - A. R2 - B. R3 - seguir o PROP. R4 - não, ATE não cria contas de gestão, somente
> gestão-gestão. seguir da forma que que recomenda. R5 - aprovado. R6 - o padrão será
> 'receber'. o próprio responsável altera. R7 - aprovado 7 dias corridos. Sobre as
> versões das tecnologias, usar as seguintes Java 21, o Spring Boot 4.1.x e o MySQL 8.4"
</details>

<details>
<summary><strong>P4 — 19/10/2026 — Produção dos Markdown em lotes</strong></summary>

> "Com base nos documentos analisados e nas decisões confirmadas pelo grupo nesta
> conversa, produza os arquivos Markdown que orientarão o desenvolvimento do Furin
> Access. Crie os arquivos em lotes de até três por resposta, para facilitar a revisão.
> Informe o caminho e entregue o conteúdo completo de cada arquivo. Quando houver
> recurso de criação de arquivos, disponibilize também os arquivos .md."
>
> O pedido também define:
> - a estrutura sugerida: README, `docs/` e `requirements/rf|rnf/`;
> - os campos obrigatórios de cada RF e RNF;
> - a regra de registrar "a definir" com a proposta em separado;
> - as orientações de rastreabilidade, do plano para o Trello e deste registro de IA;
> - a diferença entre documentação revisada, implementação planejada, função simulada e
>   função implementada.
</details>

<details>
<summary><strong>P5 e seguintes — 19/10/2026 — Continuação dos lotes</strong></summary>

> "Gere o próximo lote, seguindo as decisões confirmadas e mantendo a lista de arquivos
> concluídos e pendentes."
</details>

<details>
<summary><strong>P6 — 19/10/2026 — Dados para este registro</strong></summary>

> "1 - Luis Eduardo Belo 2 - Opus 5.5 - Alto 3 - pode colocar a data de hoje 4 - não
> houve"
</details>

<details>
<summary><strong>P7 — 19/10/2026 — Pauta da revisão T-00</strong></summary>

> "sim, faça uma pauta só com as decisões pendentes, logo após de te mandar as decisões
> que faltam vou enviar um arquivo para conferencia de 10 passos e quando estiver tudo
> concluído quero mandar para o claude code"
</details>

<details>
<summary><strong>P8 — 19/10/2026 — Respostas da pauta</strong></summary>

> "H1 - https://github.com/LuisEduardo77/PI---Furin.git H2 - não temos leitor USB de
> QR Code, vai ser tudo feito simulado pelas telas. H3 - seguir com sua recomendação.
> H4 - seguir com sua recomendação."
>
> Em seguida, nas perguntas de múltipla escolha: "Aprovar todas" (blocos A a G),
> "Só registrar a data" (D4) e "Sim, campo + painel" (leitura simulada).
</details>

<details>
<summary><strong>P9 — 19/10/2026 — Backup, hospedagem, e-mail, guarda de dados e termo</strong></summary>

> "referente ao backup, se for obrigatorio (por causa dos requisitos legais ou outro tipo
> de obrigatoriedade) devemos fazer, você deve procurar a forma fácil e simples
> aceitável, referente a hospedagem, podemos usar o github pages, o q acha? como é um
> projeto acadêmico n tem necessidade de pegar um endereço de verdade, envio real de
> email não precisa, pode ser simulado também, como a mensagem do whatsapp, sobre o
> prezo de guarda de dados, mantenha o padrão determinado por lei, sobre a negação do
> termo, sobre a recusa, não temos o que fazer, o aluno não será cadastrado, ai ou o
> aluno não poderia estudar na escola"
>
> Para responder, a IA consultou na web, em 19/10/2026, os textos da LGPD (art. 14 §4º,
> art. 15, art. 16 e art. 46) e do Marco Civil da Internet (art. 15). Resultado:
> DEC-30 a DEC-34.
</details>

<details>
<summary><strong>P10 — 19/10/2026 — Revisão de consistência em 10 itens</strong></summary>

> "Faça uma revisão de consistência de todos os arquivos Markdown produzidos nesta
> conversa."
>
> O pedido lista dez verificações: RF e RNF contemplados; identificadores e perfis
> padronizados; tipo automático na leitura; notificações e consulta dos responsáveis
> no MVP; compatibilidade entre arquitetura, modelo, permissões e requisitos; ligação
> de segurança, acessibilidade e proteção de dados aos requisitos; critérios
> verificáveis; propostas e simulações identificadas; referências documentais; e
> registro de IA completo. Pede também as inconsistências, as correções, as questões
> que dependem do grupo e o conteúdo completo dos arquivos corrigidos.
</details>

<details>
<summary><strong>P11 — 19/10/2026 — Confirmação da DEC-34</strong></summary>

> "sobre DEC 34 tudo certo, se for negado faremos do jeito sem o sistema, como está
> hoje"
</details>

<details>
<summary><strong>P12 — 19/10/2026 — Fechamento</strong></summary>

> "os meus colegas já confirmaram, os pdf não precisam mais ser ajustados (só precisa
> de ajuste se tem alguma informação divergente, o projeto sofreu alterações desde
> essas atividades, se nos fomos ajustando o material das atividades até agora está
> ok, n precisa mudar novamente só para entregar), o envio ja venceu, não dá mais para
> reenviar, os prazos de documento pode ser 5 anos."
>
> Resultado: DEC-35 e DEC-36.
</details>

<details>
<summary><strong>P13 — 20/10/2026 — Resumos</strong></summary>

> "Me faça mais alguns MDs com os seguintes resumos: das ferramentas, como o sistema
> vai funcionar, os RF e os RNF"
>
> Resultado: `docs/resumo-ferramentas.md`, `docs/resumo-funcionamento.md`,
> `docs/resumo-rf.md` e `docs/resumo-rnf.md`, feitos só a partir dos documentos já
> revisados, sem decisões novas. Entregues em dois lotes (P14: "pode mandar o próximo
> lote").
</details>

<details>
<summary><strong>P15 — 21/10/2026 — Perfil único</strong></summary>

> "quero fazer uma mudança, o ate e a gestão escolar tem um mesmo perfil, são cargos
> diferentes, porém no sistema é a mesma coisa / ate e gestão tem o sistema completo /
> alunos - só passam a carteira / pais - só recebem o email de confirmação"
>
> A IA perguntou três detalhes, e Luis escolheu as opções recomendadas: nome do perfil
> `FUNCIONARIO`; o funcionário liga ou desliga o aviso por e-mail a pedido do
> responsável; qualquer funcionário cria, desativa e reativa contas da equipe.
>
> Resultado: DEC-37 a DEC-39. RF17, RF24 e RF25 marcados como fora do escopo (IDs
> preservados), RF26 reescrito como "Gerenciar contas da equipe", RN13 obsoleta, DDL
> sem conta de responsável e todos os arquivos atualizados. As linhas antigas de
> decisões foram mantidas, com a nota "substituída pela DEC-37".
</details>

## 3. Uso previsto de IA no desenvolvimento

| Campo | Previsão |
|---|---|
| Ferramenta | Claude Code, para gerar código a partir destes Markdown (A13 p.6 prevê o Claude). O grupo pretende entregar este pacote ao Claude Code depois da revisão |
| Modelo | _a preencher a cada uso_ |
| Regras | Usar só dados fictícios (A10 §8.33). Nunca enviar segredos. O código gerado passa por revisão em PR, testes e SonarCloud antes do merge (A10 §8.28) |
| Registro | Para cada tarefa do plano feita com IA, anotar no cartão do Trello ou no PR: data, ferramenta, modelo, finalidade e prompt principal. Adicionar uma linha na tabela abaixo |

| Data | Tarefa (T-nn) | Ferramenta e modelo | Finalidade | Prompt principal | Revisado por |
|---|---|---|---|---|---|
| 05/11/2026 | T-27 (parte: RN04) | Claude Code, Claude Opus 5.5 (`claude-opus-5-5`) | Corrigir um erro da própria IA, achado pelo integrante nos testes: depois de uma leitura repetida, a tela perdia a liberação e o aluno ficava preso em `LIBERACAO_PENDENTE`. Criar a branch `t-27-liberacao-pendente`; a leitura repetida passa a devolver a liberação pendente (`repetida: true`), sem gravar nem renovar o prazo; a tela mostra "Aguardando passagem" e "Ana, pode passar."; uma recusa não encerra mais a liberação em curso; testes em `LeituraTests`, `LeituraConcorrenteTests` e `PortariaTelaTests`, inclusive com a página recarregada. A janela de 2 s e a RN05 ficaram para a T-27 | "pode implementar esse ajusta da liberação no código para eu subir para o github" | _pendente (revisão no PR)_ |
| 02/11/2026 | T-30 | Claude Code, Claude Opus 5.5 (`claude-opus-5-5`) | Criar a branch `t-30-tela-portaria` e a tela `/portaria` (antecipada; Fase 3 ainda não feita): campo de leitura com foco, painel do funcionário com "Foto indisponível" (DEC-28, A9), monitor do aluno só com o primeiro nome, painel "Simular leitura (protótipo)" com as carteirinhas válidas (`CarteirinhasFicticias`, só no `prototipo`) e o botão "Simular passagem (giro)"; o script `static/js/portaria.js`; o horário do giro formatado no servidor (`horarioNaEscola`); o evento `furin:atividade` no `sessao.js`; os testes `PortariaTelaTests` (HtmlUnit com JavaScript, sem a transação do teste) e `PortariaSemSimulacaoTests`; e a documentação. A IA subiu o protótipo na porta 8081 (a 8080 estava ocupada pela aplicação do integrante) e conferiu sem login, sem fazer leituras no banco da demonstração | "Vamos fazer a T-30 do docs/plano-de-implementacao.md, na branch t-30-tela-portaria a partir da main atualizada. É uma antecipação para uma demonstração [...] No lugar da foto, mostre "Foto indisponível", sem bloquear a liberação (DEC-28, A9). [...] Rode ./mvnw -B verify, suba a aplicação no perfil prototipo e me diga o passo a passo para testar no navegador." | _pendente (revisão no PR)_ |
| 02/11/2026 | T-28 | Claude Code, Claude Opus 5.5 (`claude-opus-5-5`) | Criar `POST /portaria/giros` (`GiroSimuladoController` com `@Profile("prototipo")` e `Giros`: aluno bloqueado, só `AGUARDANDO_GIRO` vira `CONFIRMADA`, `ocorrido_em` do servidor), os testes `GiroTests` (perfis `test` e `prototipo`, com a carga V2 desligada por `spring.flyway.target=1` e um teste que confere isso) e o caso da rota fora do `prototipo` (404) em `LeituraTests`; o teste de autorização da T-17 passou a rodar também com o `prototipo`, para incluir o giro. Atualizar RF09, RF14, plano, arquitetura, README e CLAUDE.md sem marcar nenhum RF como `Implementada`, como pedido. O botão, o prazo de 15 s e as notificações ficaram para depois | "T-28: POST /portaria/giros (só no perfil prototipo): confirma a liberação e grava ocorrido_em. [...] Inclua as rotas novas no teste de autorização da T-17. Não marque nenhum RF como Implementada" | _pendente (revisão no PR)_ |
| 02/11/2026 | T-26 | Claude Code, Claude Opus 5.5 (`claude-opus-5-5`) | Criar `POST /portaria/leituras` (`LeituraController`, `Leituras`, `RegistrosDaPortaria` com SQL explícito e `SELECT … FOR UPDATE`, `MotivoDaRecusa`, `Liberacao`), com transação `READ_COMMITTED`, reenvio pela `chave_requisicao`, recusas da RN09/RN06 com `LEITURA_RECUSADA` mascarado e uma proteção mínima contra duas liberações pendentes até a T-27 [PROP]; os testes `LeituraTests` e `LeituraConcorrenteTests` (dois pedidos ao mesmo tempo, sem a transação do teste); o relógio ajustável dos testes virou classe compartilhada. A foto ficou fora da resposta, como pedido | "T-26: endpoint POST /portaria/leituras: recebe o código e a chave_requisicao, valida, bloqueia o aluno (SELECT ... FOR UPDATE), grava AGUARDANDO_GIRO e devolve tipo, nome e turma" | _pendente (revisão no PR)_ |
| 02/11/2026 | T-25 | Claude Code, Claude Opus 5.5 (`claude-opus-5-5`) | Criar a branch `t-25-26-28-portaria` (pedido do integrante: antecipar parte da Fase 4 para a demonstração, sem a Fase 3), `portaria/TipoAutomatico` (tabela da RN01, dia no fuso da escola), `TipoMovimentacao`, a constante `RelogioConfig.FUSO_DA_ESCOLA` [PROP] e o teste de unidade `TipoAutomaticoTests` com relógio fixo, inclusive a virada do dia (CA-RF13-05) | "Preciso demonstrar a portaria amanhã, então vamos ANTECIPAR uma parte da Fase 4, pulando a Fase 3 de propósito. [...] T-25: serviço de tipo automático (RN01), com teste de unidade da tabela de decisão" | _pendente (revisão no PR)_ |
| 02/11/2026 | T-17 | Claude Code, Claude Opus 5.5 (`claude-opus-5-5`) | Criar a branch `t-17-autorizacao`, o filtro `ContaDesativada` (conta desativada durante a sessão encerra a sessão, `ACESSO_NEGADO motivo=CONTA_DESATIVADA` e aviso no login), a página `error/403.html`, o teste `AutorizacaoDeTodasAsRotasTests` (descobre as rotas pelos controllers registrados no Spring e cruza com as três situações do CA-RF03-03, mais a rota com controller e sem regra e o log), `PaginaDeAcessoNegadoTests` (Tomcat real), o ajuste de `LayoutBaseTests` e a documentação. O teste da página 403 revelou que o Tomcat punha `;jsessionid=` nos links da primeira página; a IA configurou a sessão só por cookie e acrescentou um teste. Texto da página 403 [PROP] | "Vamos fazer só a T-17 do docs/plano-de-implementacao.md. Crie a branch t-17-autorizacao a partir da main atualizada. Leia o RF03. Escreva um teste parametrizado que passe por TODAS as rotas que existem hoje no projeto, em três situações [...] O teste precisa descobrir as rotas sozinho ou ficar fácil de atualizar, porque ele será rodado de novo no fim de cada fase." | _pendente (revisão no PR)_ |
| 02/11/2026 | T-16 | Claude Code, Claude Opus 5.5 (`claude-opus-5-5`) | Apontar a contradição entre o pedido ("foco no botão") e o CA-RF01-10 ("sem mover o foco") e perguntar antes de implementar; o integrante escolheu mover o foco, registrado como DEC-40. Depois, criar a branch `t-16-sessao`, `TemposDaSessao` (inatividade por sessão em segundos, de `furin.sessao.*`), `TemposDaSessaoNaTela`, o filtro `SessaoExpirada`, `SessaoController` (`POST /sessao/renovar`), o diálogo do aviso no layout-base, o primeiro script do projeto (`static/js/sessao.js`), a mensagem no login, o "Sair" apagando o cookie, os testes `SessaoTests`, `AvisoDeSessaoTests` (HtmlUnit com JavaScript e tempos de poucos segundos só no teste) e `SessaoExpiradaNoServidorTests` (Tomcat real) e a documentação. Os testes antigos mostraram que o `invalidSessionUrl` do Spring Security mudava o 403 de um POST sem sessão; a IA trocou por um filtro próprio. A IA conferiu no navegador do app a mensagem do login e o script, sem entrar com as senhas de demonstração. No PR #17, corrigiu o apontamento de segurança do SonarCloud (`getRequestedSessionId()`, valor vindo do navegador: o filtro passou a conferir só a presença do cookie de sessão) e o nome do método igual ao da constante; o aviso de `role="status"` ficou para ser aceito | "Vamos fazer só a T-16 do docs/plano-de-implementacao.md. Crie a branch t-16-sessao a partir da main atualizada. Leia o RF01. A sessão expira depois de 30 minutos sem uso (furin.sessao.inatividade). 60 segundos antes (furin.sessao.aviso-antes), aparece um aviso acessível com o botão "Continuar conectado" [...] Escreva os testes possíveis e me diga como testar no navegador sem esperar 30 minutos." | _pendente (revisão no PR)_ |
| 02/11/2026 | T-15 (SonarCloud) | Claude Code, Claude Opus 5.5 (`claude-opus-5-5`) | Criar a branch `t-15-sonar` e corrigir 6 dos 8 apontamentos do SonarCloud no PR #15: a chamada a um método `@Transactional` pelo próprio objeto em `SenhasDosFuncionarios`, o literal `"redirect:"` repetido em `SenhaController` e 4 asserções com `containsEntry` em `SenhaTests`. Os 2 avisos de trocar `role="status"` por `<output>` ficaram, porque a A11 e o CLAUDE.md pedem `role="status"`; a sugestão é marcá-los como aceitos no SonarCloud. Sem mudança de comportamento (`./mvnw -B verify`: 201 testes) | "Sim, corrija" (depois do resumo dos apontamentos do SonarCloud no PR #15) | _pendente (revisão no PR)_ |
| 02/11/2026 | T-15 | Claude Code, Claude Opus 5.5 (`claude-opus-5-5`) | Criar a branch `t-15-senha`, a troca obrigatória da senha temporária (`ContaAutenticada`, desvio do login para `/senha/definir` e o filtro `TrocaDeSenhaObrigatoria` antes da autorização), a senha temporária vencida como credencial expirada (mensagem só para quem acertou a senha, `LOGIN_FALHA motivo=SENHA_TEMPORARIA_VENCIDA`), `SenhaController` e `SenhasDosFuncionarios` (regras com `furin.senha.tamanho-minimo`, só hash, nova sessão, `SENHA_ALTERADA` sem a senha), as telas `senha/definir` e `senha/alterar`, os testes `SenhaTests` (MockMvc, HtmlUnit e relógio ajustável) e a documentação. Os testes mostraram que, com `hibernate.jdbc.time_zone: UTC`, um `LocalDateTime` lido pelo JPA vinha 3 h deslocado; a IA mapeou o prazo da temporária como `Instant` e registrou o cuidado na arquitetura. Decisões [PROP] da IA: senha vencida não conta para o bloqueio, login bloqueado impede "Alterar senha", textos de campo vazio e de sucesso | "Vamos fazer só a T-15 do docs/plano-de-implementacao.md. Crie a branch t-15-senha a partir da main atualizada. Leia o RF01 (fluxo principal e fluxo A3) e a RN14 [...] troca obrigatória [...] senha temporária vencida (mais de 72 h) é recusada; nova senha com pelo menos 8 caracteres e diferente da temporária; tela "Alterar senha" (/senha/alterar), que exige a senha atual. Os valores vêm de furin.senha.* da T-07. Nenhuma senha em log." | _pendente (revisão no PR)_ |
| 02/11/2026 | T-14 | Claude Code, Claude Opus 5.5 (`claude-opus-5-5`) | Criar a branch `t-14-bloqueio`, `TentativasDeLogin` (contador em memória por login normalizado, com `furin.login.max-tentativas` e `furin.login.bloqueio` e o `Clock` da aplicação), o filtro `LoginBloqueado` (recusa durante o bloqueio sem conferir a senha, mensagem E2 igual para logins existentes e inexistentes), `LOGIN_BLOQUEADO` e `LOGIN_FALHA motivo=BLOQUEADO` no log, o contador zerado no login com sucesso, os testes `BloqueioDeLoginTests` (relógio ajustável no lugar do bean `Clock`), o ajuste de `LoginTests`, `ErroDeLoginTests` e `LayoutBaseTests` e a documentação. Decisões [PROP] da IA: a tentativa durante o bloqueio não o prolonga, o contador recomeça quando o bloqueio vence e a falha que bloqueia ainda mostra a mensagem genérica. RF02 passou a `Implementada` | "Vamos fazer só a T-14 do docs/plano-de-implementacao.md. Crie a branch t-14-bloqueio a partir da main atualizada. Leia o RF02. Depois de 5 tentativas seguidas erradas para o mesmo login, bloqueie o login por 15 minutos, usando furin.login.max-tentativas e furin.login.bloqueio da T-07, sem números escritos no código. [...] Escreva testes do bloqueio e do desbloqueio, controlando o relógio no teste em vez de esperar 15 minutos de verdade." | _pendente (revisão no PR)_ |
| 02/11/2026 | T-13 | Claude Code, Claude Opus 5.5 (`claude-opus-5-5`) | Criar a branch `t-13-erro-login`, `LoginComFalhaAuditado` (mensagem única para usuário inexistente, senha errada e conta inativa; `LOGIN_FALHA` com login mascarado, motivo e origem, sem a senha), `CamposDoLoginObrigatorios` ("Informe o usuário." / "Informe a senha." sem consultar a conta), `RetornoAoLogin` (usuário preservado por atributo *flash*), as mensagens na tela com `role="alert"`, `aria-invalid`, `aria-describedby` e foco no primeiro campo com erro, os testes `ErroDeLoginTests` (MockMvc, HtmlUnit e um *spy* do repositório), o ajuste do `LoginTests`, `.claude/` no `.gitignore` (pedido do integrante) e a documentação. A IA decidiu que o campo vazio não gera `LOGIN_FALHA` (não é tentativa de autenticação) e registrou como [PROP]. O bloqueio ficou para a T-14 | "Vamos fazer só a T-13 do docs/plano-de-implementacao.md. Crie a branch t-13-erro-login a partir da main atualizada. Leia o RF02. Para usuário inexistente, senha errada ou conta desativada, a mensagem deve ser sempre a mesma [...] Cada tentativa que falhar vai para o log de auditoria da T-10, sem a senha. Implemente e teste os critérios de aceitação do RF02 que não dependem do bloqueio (o bloqueio é a T-14)." | _pendente (revisão no PR)_ |
| 01/11/2026 | T-12 | Claude Code, Claude Opus 5.5 (`claude-opus-5-5`) | Criar a branch `t-12-login`, o pacote `autenticacao` (entidade `Usuario`, `UsuarioRepository`, `ContasDosFuncionarios` com o login normalizado e o perfil vindo do banco, `LoginController`, `LoginComSucessoAuditado` com `LOGIN_SUCESSO`), a tela `autenticacao/login.html` (labels, `autocomplete`, colar permitido), a portaria provisória com o menu único, o menu só para quem entrou, a retirada do usuário temporário `user`, os testes `LoginTests` (MockMvc e HtmlUnit, com senhas descartáveis) e a documentação. A IA subiu o protótipo e conferiu a tela de login e o redirecionamento de `/portaria` no navegador do app, mas não digitou as senhas das contas de demonstração. A mensagem de erro do login ficou para a T-13. Textos da tela [PROP] | "Vamos fazer só a T-12 do docs/plano-de-implementacao.md. Crie a branch t-12-login a partir da main atualizada. Leia o RF01 e o RF03. Faça a tela de login e a autenticação do perfil FUNCIONARIO [...] Escreva testes: ate.demo e gestao.demo entram, caem em /portaria e veem o mesmo menu (CA-RF03-01). Rode ./mvnw -B verify." | _pendente (revisão no PR)_ |
| 22/10/2026 | T-01 | Claude Code, Claude Opus 5.5 (`claude-opus-5-5`) | Criar a branch `t-01-repositorio` e o `.gitignore` (Java 21, Maven, Spring Boot, IDE, `application-local.yml`, logs, pastas de arquivos privados e de backup) | "Vamos fazer só a T-01. Crie a branch t-01-repositorio e um .gitignore para um projeto Java 21 + Maven + Spring Boot, incluindo application-local.yml, target/, pastas de IDE, *.log e a pasta de backup (FURIN_BACKUP_DIR)." | _pendente (revisão no PR)_ |
| 27/10/2026 | T-03 | Claude Code, Claude Opus 5.5 (`claude-opus-5-5`) | Montar os scripts de validação do DDL da §5.5 e dos 86 casos de teste das restrições (`docs/evidencias/t-03/`) e o script do usuário do banco `furin` (`scripts/criar-usuario-banco.sql`, senha gerada pelo MySQL e não vista pela IA). Os scripts foram executados pelo integrante no MySQL 8.4.13 local, que digitou a própria senha; a IA leu a saída no terminal e registrou a evidência | "Agora a T-03. Quero validar o DDL da seção 5.5 de docs/arquitetura-e-dados.md num banco de teste furin_ddl_teste no meu MySQL 8.4. Me passe o comando para eu rodar e digitar a senha eu mesmo." | _pendente (revisão no PR)_ |
| 29/10/2026 | T-04 | Claude Code, Claude Opus 5.5 (`claude-opus-5-5`) | Gerar o projeto pelo Spring Initializr (Boot 4.1.2, Java 21, sem Spring Mail), configurar os perfis `prototipo` e `test` e o modelo `application-local.yml.example`, rodar `./mvnw -B verify` e subir a aplicação. A senha do banco foi colocada pelo integrante no `application-local.yml`, sem passar pela IA | "Agora a T-04: gere o projeto Spring Boot conforme a tabela da seção 1 da arquitetura (Java 21, Boot 4.1.x, pacote br.com.furin.access), na branch t-04-projeto. Não adicione o Spring Mail. Rode ./mvnw -B verify e me mostre o resultado." | _pendente (revisão no PR)_ |
| 01/11/2026 | T-11 | Claude Code, Claude Opus 5.5 (`claude-opus-5-5`) | Mostrar que uma migração SQL não gera o hash bcrypt nem os arquivos fictícios e propor uma migração Java V2 só no perfil `prototipo`, que o integrante aprovou antes da implementação. Depois, criar a branch `t-11-carga`, `carga/V2__CargaFicticia` (contas, turmas, alunos, vínculos, contatos, consentimentos e carteirinhas da §5.6), `carga/ArquivosFicticios` (silhueta PNG e termo PDF gerados no código), os testes `CargaFicticiaTests` e `CargaFicticiaSenhaTests`, o modelo `application-local.yml.example` (chaves sem valor) e a documentação. A IA não escolheu nem viu as senhas reais das contas; os testes usam senhas descartáveis | "Vamos fazer só a T-11 do docs/plano-de-implementacao.md. [...] As senhas das duas contas vêm de variáveis de ambiente e nunca ficam no repositório. Se uma migração SQL não conseguir gerar o hash bcrypt a partir da variável, proponha outra forma [...] e espere a minha aprovação antes de implementar." | _pendente (revisão no PR)_ |
| 01/11/2026 | T-10 | Claude Code, Claude Opus 5.5 (`claude-opus-5-5`) | Criar a branch `t-10-auditoria`, o pacote `auditoria` (`Auditoria`, catálogo `EventoAuditoria` com os nomes dos RF, `DetalhesAuditoria` só com tipos seguros, `Mascaramento` de e-mail, login e código), o bean `Clock` em UTC, o arquivo próprio do logger `AUDITORIA` (`logback-spring.xml`, sem descarte, DEC-36), o registro de `ACESSO_NEGADO` em todo 403 (`AcessoNegadoAuditado`), os testes `MascaramentoTests`, `AuditoriaTests` e `AuditoriaIntegracaoTests` e a documentação. No mesmo PR, corrigiu os 3 apontamentos do SonarCloud da T-09 (`throws Exception` e asserções do cookie). Máscara de login e `LOGIN_SUCESSO` [PROP] | "Vamos fazer só a T-10 do docs/plano-de-implementacao.md. Crie a branch t-10-auditoria a partir da main atualizada. Crie o logger de auditoria separado (A10 §8.15, RNF05) [...] Escreva testes confirmando que nenhuma senha aparece no log e que o e-mail e o código saem mascarados." | _pendente (revisão no PR)_ |
| 01/11/2026 | T-09 | Claude Code, Claude Opus 5.5 (`claude-opus-5-5`) | Criar a branch `t-09-seguranca` e `config/SegurancaConfig` (rotas da arquitetura §4 só para `FUNCIONARIO` e `denyAll` no resto, conforme RF03 e escopo §6; CSRF; `PasswordEncoder` delegante; CSP e `Referrer-Policy` [PROP]), o cookie de sessão `HttpOnly`/`SameSite=Lax`, o perfil `FUNCIONARIO` temporário para o usuário gerado no perfil `prototipo` (até a T-12), os testes `SegurancaTests` e `CookieDeSessaoTests`, o ajuste do `LayoutBaseTests` e a documentação. O pedido dizia "toda rota exige FUNCIONARIO"; a IA seguiu a regra mais restrita dos documentos (rota não listada = 403) e registrou isso | "Vamos fazer só a T-09 do docs/plano-de-implementacao.md. Crie a branch t-09-seguranca a partir da main atualizada. Configure o Spring Security base conforme o RF03, o RNF03, o RNF04 e a seção 6 do docs/arquitetura-e-dados.md [...] Escreva testes: uma rota não mapeada sem login leva ao login, e o encoder gera um hash começando com {bcrypt} que não contém a senha." | _pendente (revisão no PR)_ |
| 01/11/2026 | T-08 | Claude Code, Claude Opus 5.5 (`claude-opus-5-5`) | Criar a branch `t-08-layout`, o layout-base (`templates/layout/base.html`, com título "Página \| Furin Access", `lang="pt-BR"`, link "Ir para o conteúdo principal" e o menu do RF03), o CSS base (`static/css/base.css`, paleta de A11, foco de 3 px, alvos de 2,75rem, unidades relativas), a página temporária `/exemplo/layout`, os testes `LayoutBaseTests` (HtmlUnit, dependência de teste gerenciada pelo Boot) e `EstiloBaseTests` (contraste pela fórmula da WCAG), e atualizar a documentação. A IA subiu a aplicação local e conferiu 320 px, fonte em 200% e o foco pelo teclado no navegador do app, com o usuário temporário gerado pelo Spring Security (sem registrar a senha). Cores de erro e sucesso [PROP]. Depois, corrigiu os apontamentos do SonarCloud no PR #8 (título no modelo da página, regex com backtracking no teste, comentário e `aria-label`), mantendo `role="status"` como pede a A11 | "Vamos fazer só a T-08 do docs/plano-de-implementacao.md. Crie a branch t-08-layout a partir da main atualizada. Crie o layout-base em Thymeleaf: lang="pt-BR", título por página no formato "Página \| Furin Access", link "Ir para o conteúdo principal" como primeiro elemento, cabeçalho e o menu único dos funcionários na ordem do RF03 [...] Crie uma página de exemplo usando o layout e me mostre como conferir cada item da checklist da seção 3 do plano." | _pendente (revisão no PR)_ |
| 29/10/2026 | T-07 | Claude Code, Claude Opus 5.5 (`claude-opus-5-5`) | Criar a branch `t-07-propriedades`, a classe `config/FurinProperties` (record com `@ConfigurationProperties(prefix = "furin")`, `@Validated` e um record por grupo) com os 28 parâmetros da tabela da arquitetura §4, os valores aprovados no `application.yml` (`furin.escola.nome` = "Escola Exemplo", fictício), `@ConfigurationPropertiesScan` na aplicação, os testes `FurinPropertiesTests` (valores carregados) e `FurinPropertiesValidacaoTests` (16 valores inválidos recusados), e atualizar a documentação. Detalhes [PROP] registrados na arquitetura §4: tipos MIME e diretório padrão `${user.home}/furin-access/arquivos` | "Vamos fazer só a T-07 do docs/plano-de-implementacao.md. Crie a branch t-07-propriedades a partir da main atualizada. Crie as classes de configuração (@ConfigurationProperties com prefixo furin) com todas as propriedades da tabela "Parâmetros" da seção 4 do docs/arquitetura-e-dados.md, com os valores aprovados no application.yml e validação (Bean Validation) para valores inválidos. [...]" | _pendente (revisão no PR)_ |
| 29/10/2026 | T-06 | Claude Code, Claude Opus 5.5 (`claude-opus-5-5`) | Criar a branch `t-06-flyway` e a migração `V1__esquema_inicial.sql`, copiada por script das linhas do DDL da arquitetura §5.5 (comparada linha a linha com a cópia validada na T-03: idêntica, sem correções). O `flyway-mysql` já estava no `pom.xml`. Escrever `EsquemaInicialTests` (esvazia o `furin_test` com o `clean` do Flyway só nesse teste, confere as 9 tabelas, InnoDB/utf8mb4, ausência de colunas proibidas pelo RNF05 e o histórico da V1) e atualizar a documentação. O pedido falava em 8 tabelas; o DDL e a T-03 têm 9, e o teste segue o DDL | "Vamos fazer só a T-06 do docs/plano-de-implementacao.md. Crie a branch t-06-flyway a partir da main atualizada. Crie src/main/resources/db/migration/V1__esquema_inicial.sql a partir do DDL da seção 5.5 do docs/arquitetura-e-dados.md, já validado na T-03. [...] Escreva um teste de integração que suba a aplicação no perfil test com um banco vazio e confira que as 8 tabelas foram criadas." | _pendente (revisão no PR)_ |
| 29/10/2026 | T-05 (fechamento) | Claude Code, Claude Opus 5.5 (`claude-opus-5-5`) | Conferir pela API pública do GitHub o resultado da CI do PR #4 (verde no PR e no push da `main`) e marcar a T-05 como concluída no plano, no README, na arquitetura, no resumo de ferramentas e no RNF08 (campo Implementação, que ainda dizia "Projeto ainda não criado") | "Sim, preparar" (o commit que marca a T-05 como concluída, numa branch nova a partir da `main`) | _pendente (revisão no PR)_ |
| 29/10/2026 | T-05 | Claude Code, Claude Opus 5.5 (`claude-opus-5-5`) | Criar a branch `t-05-ci`, o workflow do GitHub Actions (`.github/workflows/ci.yml`, a partir do ponto de partida da arquitetura §7, sem o SonarCloud, que é da T-44) com o serviço `mysql:8.4` e credencial descartável da CI, o teste `BancoDeDadosTests` (MySQL 8.4 e banco `furin_test`) e atualizar a documentação. `./mvnw -B verify` rodado localmente. Nenhum segredo real passou pela IA | "Vamos fazer só a T-05 do docs/plano-de-implementacao.md. Leia os RF e RNF citados nela, crie a branch t-05-ci a partir da main atualizada, implemente o que o "Pronto quando" pede e escreva os testes necessários. Rode ./mvnw -B verify e me mostre o resultado." | _pendente (revisão no PR)_ |
