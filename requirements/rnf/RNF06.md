# RNF06 — Acessibilidade digital

| Campo | Valor |
|---|---|
| **Categoria** | Acessibilidade |
| **Documentação** | Revisada em 21/10/2026 (DEC-37 a DEC-39) |
| **Implementação** | Planejada |
| **Atualizado em** | 21/10/2026 |

## Descrição

Todas as telas seguem os requisitos da **ABNT NBR 17225:2025** com os critérios
correspondentes da **WCAG 2.2**, conforme a Atividade 11. O nível alvo é o **AA**, com
as recomendações AAA que a A11 escolheu. O RNF original da A08 resume isso em três
pontos:

- status sempre com texto além da cor;
- contraste adequado;
- navegação por teclado nos formulários.

Os itens da A11 mantêm os **identificadores originais** (DEC-16).

## Justificativa

O sistema é usado por pessoas diferentes, em situações diferentes:

- ATEs na portaria, com pressa;
- Secretaria e Direção;
- funcionários em telas pequenas, às vezes;
- alunos do Ensino Fundamental, que leem o monitor.

Acessibilidade amplia o uso por todos, inclusive por pessoas com deficiência, e é
requisito do PI (A11).

## Fonte documental

| Origem | Conteúdo |
|---|---|
| A08 p.4 (RNF06) | "Status sempre com texto além da cor, contraste adequado e navegação por teclado nos formulários" — origem: Atividade 11 |
| A11 p.2–8 | Requisitos por tema, com o critério WCAG e a forma de implementar |
| A11 p.9 | Verificações previstas |
| **ATU-01, DEC-01** | Sem seletor de modo Entrada/Saída. Os itens da A11 que citam esse seletor foram ajustados |
| **DEC-16** | Os itens da A11 ficam com os IDs originais, sem virar novos RNF |

**Alterações em relação à A11, por causa da ATU-01:**

| Item da A11 | Antes | Agora |
|---|---|---|
| §5.1.13 | "alternar Entrada/Saída" | Removido. Incluídos os botões do painel "Simular leitura (protótipo)" (DEC-25), "Simular passagem (giro)" e "Cancelar liberação" |
| §5.11.1 | "Modo: Entrada"/"Modo: Saída" | Substituído pelos estados "Entrada liberada", "Saída registrada" etc. |
| §5.11.4 | Botões Entrada/Saída | Removidos |
| §5.13.10 | Controles Entrada/Saída | Removidos |
| §5.8.6 | Lista de botões com Entrada/Saída | Ajustada |
| §5.11.1 e §5.12.12 | Citavam o RF21 | Corrigido para RF22/RF23 |

**Trechos corrompidos da A11 que foram reconstruídos:**

- p.3 §5.6.3: frase truncada;
- p.4 §5.9.9: provavelmente `aria-invalid` e `aria-describedby`;
- p.8 §5.13.8: frase incompleta.

## Requisitos

### Operação por teclado (A11 p.2)

| ABNT | WCAG | Aplicação | RF |
|---|---|---|---|
| 5.1.13 Teclado | 2.1.1 (A) | Todas as funções funcionam pelo teclado: login, cadastro e edição, busca, registro manual, botões do painel "Simular leitura", "Simular passagem", "Cancelar liberação", filtros, impressão e contas. Depois de cada leitura, o foco volta ao campo de leitura sem prender o Tab | RF01, RF04, RF08, RF09, RF11, RF12, RF14, RF18, RF20, RF26 |
| 5.1.1 Foco visível | 2.4.7 (AA) | Contorno de foco de 3 px CSS, com afastamento de 2 px, em cor forte sobre branco. O erro não substitui o foco | Todas |
| 5.1.4 Ordem de foco | 2.4.3 (A) | Cadastro: Nome → Matrícula → Turma → Foto → Contato principal → Termo → Salvar → Cancelar. Histórico: data inicial → data final → turma → aluno → Filtrar | RF04, RF05, RF18 |

### Estrutura e navegação (A11 p.3)

| ABNT | WCAG | Aplicação | RF |
|---|---|---|---|
| 5.13.1 Título; 5.13.2 Idioma | 2.4.2, 3.1.1 (A) | Títulos "Tela \| Furin Access" e `lang="pt-BR"` | Todas |
| 5.3.1 Cabeçalhos | 1.3.1 (A) | Um h1 por tela e h2 por seção (portaria: "Leitura da carteirinha", "Último registro", "Monitor do aluno", "Alunos presentes") | Todas |
| 5.6.3 Cabeçalhos de tabela | 1.3.1 (A) | `<caption>`, `<th scope="col">` e o aluno como `<th scope="row">` | RF16, RF18, RF19 |
| 5.7.12 Pular blocos; 5.7.15 Navegação consistente | 2.4.1 (A), 3.2.3 (AA) | "Ir para o conteúdo principal" como primeiro link. Menu com a mesma ordem em todas as telas do perfil | RF03 |

### Cadastro e erros (A11 p.4)

| ABNT | WCAG | Aplicação | RF |
|---|---|---|---|
| 5.9.3 Rótulo associado | 1.3.1 (A) | `label for/id` em todos os campos. Instruções com `aria-describedby` | RF04, RF05, RF08, RF12, RF18, RF26 |
| 5.9.7 Obrigatórios | 3.3.2 (A) | "(obrigatório)" escrito e `required`. Asterisco ou cor nunca sozinhos | RF04, RF05, RF12 |
| 5.9.9 Erro descritivo | 3.3.1 (A) | Mensagem que identifica o campo, `aria-invalid="true"`, `aria-describedby` e dados preservados | Formulários |
| 5.9.10 Sugestão de correção | 3.3.3 (AA) | Por exemplo: "Confira a matrícula ou localize o cadastro existente…". Login com mensagem genérica | RF02, RF06 |

### Autenticação e prevenção de erros (A11 p.5)

| ABNT | WCAG | Aplicação | RF |
|---|---|---|---|
| 5.9.18 Autenticação acessível | 3.3.8 (AA) | Colar senha, gerenciador de senhas e `autocomplete` adequado | RF01 |
| 5.9.15 Reentrada de dados | 3.3.7 (A) | Os dados do aluno escolhido são reaproveitados no registro manual | RF12 |
| 5.9.12 Prevenção de erros | 3.3.4 (AA) | Resumo antes de salvar a edição. Confirmação no registro manual, na segunda via e nas ações de conta | RF07, RF08, RF12, RF26 |
| 5.16.2 Tempo ajustável | 2.2.1 (A) | Aviso **60 s** antes de a sessão expirar (DEC-28, E2; o mínimo de A11 é 20 s), com "Continuar conectado". O prazo do giro é um limite essencial da operação e aparece em texto (DEC-28, A11) | RF01, RF09 |

### Contraste e cor (A11 p.6)

| ABNT | WCAG | Aplicação | RF |
|---|---|---|---|
| 5.11.1 Uso de cores | 1.4.1 (A) | Estados sempre em texto: "Entrada liberada", "Saída registrada", "Aguardando passagem", "Leitura não reconhecida", "Manual" | RF10, RF13, RF19, RF22, RF23 |
| 5.11.3 Contraste de texto | 1.4.3 (AA) | Texto `#172B3A` sobre `#FFFFFF`. Botões `#FFFFFF` sobre `#173F5F` | Todas |
| 5.11.4 Contraste de componentes | 1.4.11 (AA) | Bordas `#596B7A` sobre branco (5,51:1). Foco e erro avaliados em cada estado | Todas |
| 5.1.3 Foco não obscurecido | 2.4.11 (AA) | Barras e mensagens não cobrem o elemento com foco | RF09–RF12 |

### Ampliação, responsividade e toque (A11 p.7)

| ABNT | WCAG | Aplicação | RF |
|---|---|---|---|
| 5.10.3, 5.10.4 | 1.3.4, 1.4.10 (AA) | Retrato e paisagem. 320 px sem rolagem nas duas direções (ver RNF01) | Todas |
| 5.12.7, 5.13.5 | 1.4.4, 1.4.10 (AA) | Unidades relativas. Zoom não bloqueado. 200% sem perda | Todas |
| 5.12.1–5.12.4 | 1.4.12 (AA) | Opção "Leitura ampliada": entrelinha 1,5; parágrafo 2; letras 0,12; palavras 0,16 | Todas |
| 5.8.6 Área de acionamento | 2.5.8 (AA), 2.5.5 (AAA) | Alvos de pelo menos 44 × 44 px CSS: botões do painel "Simular leitura", Simular passagem, Cancelar liberação, Registrar manualmente, Salvar, Filtrar, Imprimir, opção de aviso por e-mail na edição do aluno | RF08, RF09, RF12, RF18, RF20 |

### Retorno e compreensão (A11 p.8)

| ABNT | WCAG | Aplicação | RF |
|---|---|---|---|
| 5.13.8 Mensagens de status | 4.1.3 (AA) | `role="status"` para sucesso e `role="alert"` para falhas que pedem ação. As regiões do painel do funcionário e do monitor do aluno são separadas, sem anúncio duplicado. O painel de presentes não faz anúncios repetidos | RF09, RF14, RF16, RF22, RF23 |
| 5.2.1 Texto alternativo | 1.1.1 (A) | `alt="Foto de identificação de Ana Teste"`. QR Code com alt descritivo, sem o código | RF07, RF10 |
| 5.13.7, 5.13.10 Nome acessível | 2.5.3, 4.1.2 (A) | Nomes iguais ao texto visível ("Editar aluno Ana Teste"). Interruptor como checkbox nativo | RF08, RF11, RF12, RF26 |
| 5.12.12 Nível de linguagem | 3.1.5 (AAA, recomendação) | Linguagem simples no monitor do aluno e legenda explicativa no painel de presentes | RF16, RF22, RF23 |

## Partes do sistema afetadas

- Todos os templates Thymeleaf.
- O CSS base (paleta, foco, alvos, unidades).
- O JavaScript da portaria (foco, regiões `status`/`alert`).
- Os formulários.
- As tabelas.

O layout-base fica em T-08 do [plano](../../docs/plano-de-implementacao.md).

## Critérios de aceitação

| ID | Critério |
|---|---|
| CA-RNF06-01 | Cada tela passa na checklist de acessibilidade do [plano §3](../../docs/plano-de-implementacao.md) |
| CA-RNF06-02 | Todas as funções podem ser concluídas só com teclado, com foco sempre visível e sem armadilha de foco |
| CA-RNF06-03 | Com as cores desativadas (escala de cinza), todos os estados continuam identificáveis pelo texto |
| CA-RNF06-04 | As combinações de cor medidas atingem 4,5:1 para texto comum e 3:1 para componentes e texto grande |
| CA-RNF06-05 | Com o NVDA, em cada tela principal: o título da página é lido ao abrir; cada campo é lido com o seu rótulo e com "obrigatório" quando for o caso; cada célula de tabela é lida com o cabeçalho da coluna; e as mensagens de liberação, registro e erro são lidas sem que o foco saia do lugar |
| CA-RNF06-06 | Uma ferramenta automática de verificação (axe DevTools ou Lighthouse, DEC-28, G6) não aponta nenhuma violação de impacto "crítico" ou "sério" nas telas Login, Portaria, Alunos, Presentes, Histórico e Contas da equipe |

## Verificação e evidência esperada

Verificações previstas na A11 p.9:

| Verificação | Resultado esperado | Evidência |
|---|---|---|
| Teclado e leitor USB | Percorrer login, cadastro, portaria e histórico sem mouse. Depois da leitura e do Tab, o foco não fica preso | Checklist por tela |
| Leitor de tela (NVDA, DEC-28, G6) | Rótulos, títulos, cabeçalhos, anúncios de liberação, registro e erro. Contagem de presentes sem repetição excessiva | Notas da sessão de teste |
| Ampliação e toque | Zoom de 400% em janela de 1280 px. Retrato e paisagem. Espaçamento de leitura ampliada | Capturas |
| Cor, contraste e erros | Contraste medido em todos os estados. Uso sem diferenciação de cores. Cadastro incompleto e duplicado | Tabela de contrastes e capturas |
| Login e dados críticos | Colar senha e usar gerenciador. Confirmação na edição e no registro manual. Aviso de expiração de sessão | Checklist |

As evidências ficam anexadas ao cartão T-45, e o relatório da ferramenta automática vai
junto, se for usada.

## Dependências

| Tipo | Itens |
|---|---|
| RNF | RNF01 (responsividade) |
| RF | Todos os RF com tela |
| Tarefas | T-08 (layout-base) e cada tarefa de tela, além de T-45 (rodada final) |

## Protótipo e implementação

O requisito é **o mesmo** nos dois casos e deve estar **implementado e verificado no
protótipo**. Não há simulação de acessibilidade.

## Pendências

| Pendência | Situação |
|---|---|
| Ferramentas de verificação (leitor de tela e verificador automático) | **Resolvida** (DEC-28, G6): NVDA e axe DevTools ou Lighthouse, todos gratuitos |
| Tratar o prazo do giro como limite essencial (exceção de A11 §5.16.2) | **Aprovado** (DEC-28, A11) |
| Itens da A11 alterados pela ATU-01 e trechos corrompidos | Divergência só registrada. A atividade não será reenviada e vale este RNF (DEC-35; [rastreabilidade §7.4](../../docs/rastreabilidade.md)) |
