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
| _a preencher_ | | | | | |
