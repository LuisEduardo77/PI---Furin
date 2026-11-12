# Evidência: rodada de verificação de acessibilidade (T-45)

**Tarefa:** T-45 · **Requisitos:** [RNF06](../../requirements/rnf/RNF06.md) e
[RNF01](../../requirements/rnf/RNF01.md) · **Checklist:**
[plano §3](../plano-de-implementacao.md#3-checklist-de-acessibilidade-por-tela) · **Fonte:** A11 p.9

Este arquivo tem três partes:

1. a revisão do código feita na T-45 e as correções que ela trouxe;
2. a checklist por tela, com as colunas **Resultado** e **Evidência** para a equipe
   preencher durante o teste manual;
3. o roteiro do teste com teclado, NVDA e Lighthouse.

**Como preencher.** Em "Resultado", use `OK`, `Falha` ou `Não se aplica`. Em
"Evidência", cite a captura (por exemplo, `capturas/t45-login-320px.png`), a nota da
sessão do NVDA ou o relatório do Lighthouse. Uma falha vai para o PR ou para o cartão da
T-45 com a tela, o passo e o que aconteceu. As capturas ficam no cartão do Trello, **sem**
dados reais (só os da carga fictícia).

O campo **Implementação** do RNF06 continua `Planejada` até a checklist estar preenchida
(CA-RNF06-01) e o relatório do Lighthouse ou do axe DevTools não ter violação "crítica" ou
"séria" nas telas do CA-RNF06-06.

## 1. Revisão do código (12/11/2026)

Revisados todos os templates de `src/main/resources/templates`, o
`static/css/base.css` e os scripts de `static/js` contra os 10 itens da checklist.

### 1.1 O que já estava atendido

| Item | Como está no código |
|---|---|
| C1 | `layout/base.html` tem `lang="pt-BR"` e monta o título "Tela \| Furin Access" para todas as telas. Cada template passa o próprio título |
| C2 | O link "Ir para o conteúdo principal" é o primeiro elemento focável (`.pular-para-conteudo`) e leva a `main#conteudo-principal`. O menu é um fragmento único, sempre na mesma ordem, com `aria-current="page"` e sublinhado grosso na tela atual |
| C3 | Um h1 por tela e h2 por seção. Na portaria, o "Último registro" é h3 dentro de "Painel do funcionário" (ver §1.3) |
| C4 | Todos os campos têm `label for/id`, e os obrigatórios têm "(obrigatório)" no rótulo e `required`. Dicas ligadas por `aria-describedby` |
| C5 | Os erros vêm do servidor (Bean Validation), com "Erro:" escrito, `aria-invalid="true"`, `aria-describedby` apontando para a mensagem e os valores preservados (as senhas e os arquivos não voltam, de propósito). Os formulários longos têm um resumo no topo com links para os campos, que recebe o foco |
| C6 | Só controles nativos (`a`, `button`, `input`, `select`, `textarea`, `dialog`), sem `tabindex` positivo. Ordem do código igual à da A11 §5.1.4 (cadastro e histórico). Foco de 3 px com afastamento de 2 px (`:focus-visible`). Nenhum cabeçalho fixo cobre o foco. Na portaria, o foco volta ao campo de leitura depois de cada ação, sem prender o Tab |
| C7 | Estados sempre em texto ("Sucesso:", "Erro:", "Leitura recusada:", "Aguardando passagem", "Ativa"/"Inativa", "Manual"). Paleta conferida pelo `EstiloBaseTests` (texto 4,5:1, componentes 3:1) |
| C8 | `.botao`, `button`, links do menu, campos e `select` com no mínimo 2,75 rem (44 px) |
| C9 | Unidades relativas, sem largura fixa (menos a carteirinha, ver §1.2), zoom liberado no `viewport`. As tabelas rolam dentro do próprio contêiner (exceção de tabela de dados da WCAG 1.4.10) |
| C10 | Sucesso em `role="status"` ou `<output>`, falha que pede ação em `role="alert"`. Na portaria, as regiões do funcionário e do monitor são separadas, e o contador de presentes e o aviso ao responsável não são regiões vivas. Nos Presentes, só o "Atualizar" anuncia |

### 1.2 Correções feitas na T-45

Nenhuma muda o comportamento do sistema: são ajustes de CSS, de classe e de texto para
leitor de tela.

| # | Tela | Item | Problema encontrado | Correção |
|---|---|---|---|---|
| 1 | Carteirinha | C9 | O cartão tem 85,6 mm (cerca de 324 px), mais largo que a área útil com 320 px: a página rolava na horizontal | `.carteirinha` ganhou `max-width: 100%`. Na tela estreita o cartão encolhe; na impressão continua com 85,6 × 54 mm |
| 2 | Registro manual (formulário e revisão) e painel do funcionário da portaria | C9 | A lista de dados (`.portaria__dados`) usava `max-content` para o termo: "Tipo (calculado pelo sistema)" mais o valor passavam de 320 px | Coluna do termo com `fit-content(50%)`, a do valor com `minmax(0, 1fr)` e `overflow-wrap: anywhere` no valor |
| 3 | Alunos, Edição | C8 | Caixas de seleção ("Incluir inativos", "Substituir o contato principal", "Enviar avisos… por e-mail", "Confirmo que conferi o termo") com área de 24 px; a A11 §5.8.6 cita a opção de aviso por e-mail | `.campo--caixa` em `flex`: o `label`, que marca a caixa ao ser clicado, tem no mínimo 44 px de altura. Erro e dica ocupam a linha inteira |
| 4 | Alunos | C8 | "Editar aluno Ana Teste" era um link de texto dentro da tabela, menor que 44 px | Classe `botao botao--secundario`, como o "Registrar manualmente para…" do registro manual (CA-RF12-10). O texto não mudou |
| 5 | Portaria, Registro manual, Contas da equipe, 403 e 404 | C8 | Links soltos num parágrafo ("Ver o painel de presentes", "Voltar à portaria", "Voltar às contas da equipe", "Voltar ao início") com a altura da linha de texto | Classe `botao botao--secundario`. Os links dentro de frases (por exemplo, "Voltar ao cadastro" na inativação) ficaram como estão: a WCAG 2.5.8 os exclui |
| 6 | Portaria | C7 | "Simular passagem (giro)" desativado tinha a mesma aparência do ativo | `button:disabled` com borda tracejada, fundo suave e cursor `not-allowed`. A dica em texto ("Fica disponível depois de uma leitura liberada") continua |
| 7 | Cadastro, Edição, Registro manual, Histórico, Contas, Revogação, Segunda via | C5, C6 | O resumo de erros recebe o foco pelo script, mas o `:focus-visible` nem sempre aparece em foco dado por script depois de um clique | `.aviso[tabindex="-1"]:focus` com o mesmo contorno de 3 px |
| 8 | Senha temporária | C10 | A máscara "••••••••••••" era lida pelo NVDA como "marcador" doze vezes | Os pontos ficaram com `aria-hidden="true"` e o leitor lê "oculta" (texto `.visualmente-oculto`) |

### 1.3 Pontos que dependem da equipe (não alterados)

| Ponto | Por que não foi mudado | Sugestão |
|---|---|---|
| Opção "Leitura ampliada" (RNF06, A11 §5.12.1 a §5.12.4) | Não existe no sistema. Seria um controle novo em todas as telas (comportamento novo), e a WCAG 1.4.12 só exige que nada quebre quando o próprio usuário aumenta os espaçamentos | Decidir se entra antes da entrega ou se o RNF06 registra que a verificação foi feita com um *bookmarklet* de espaçamento de texto. O CSS não tem altura fixa em texto, então o teste deve passar |
| Atualização automática dos Presentes (WCAG 2.2.2, Pausar, parar, ocultar) | O RF16 e o DEC-28 (C2) pedem a atualização a cada 30 s; ela não é anunciada e não move o foco | Registrar no teste se a equipe considera a tela inteira como o conteúdo que se atualiza (sem conteúdo paralelo) ou se quer um botão "Pausar atualização" (mudança de RF) |
| Portaria: "Último registro" é h3 | O RNF06 cita "Último registro" entre os h2. A tela tem o h2 "Painel do funcionário" com o h3 "Último registro" dentro, o que é uma hierarquia válida | Aceitar como está ou trocar os nomes; anotar no resultado do C3 da portaria |
| Teste automático com axe-core | Os testes de tela usam HtmlUnit, que não calcula o layout: as regras de contraste e de tamanho de alvo do axe não funcionam nele. Rodar o axe de verdade exigiria Selenium ou Playwright com um Chrome na CI | Fica o teste manual com Lighthouse ou axe DevTools (CA-RNF06-06), que já é a ferramenta prevista no DEC-28, G6 |

## 2. Checklist por tela

Itens (texto completo no [plano §3](../plano-de-implementacao.md#3-checklist-de-acessibilidade-por-tela)):
C1 idioma e título · C2 pular blocos e menu · C3 cabeçalhos · C4 rótulos · C5 erros ·
C6 teclado e foco · C7 cor e contraste · C8 alvos · C9 320 px e zoom · C10 mensagens.

Entre como `ate.demo` ou `gestao.demo` (perfil `prototipo`). Para as telas com erro, envie
o formulário vazio ou com um dado inválido.

### 1. Login

Rotas: `/login` (com e sem erro; avisos `?logout`, `?expirada`, `?desativada`).

| Item da checklist | Resultado | Evidência |
|---|---|---|
| C1 · `lang="pt-BR"` e título "Página \| Furin Access" | | |
| C2 · Link "Ir para o conteúdo principal"; menu na mesma ordem | | |
| C3 · h1 único e h2 por seção | | |
| C4 · Todos os campos com `label`; "(obrigatório)" escrito | | |
| C5 · Erro descritivo com `aria-invalid` e `aria-describedby`; dados válidos preservados | | |
| C6 · Só teclado; ordem de foco lógica; foco de 3 px visível e nunca coberto | | |
| C7 · Estado em texto, não só em cor; contraste (texto 4,5:1, componentes 3:1) | | |
| C8 · Alvos de 44 × 44 px | | |
| C9 · 320 px de largura e zoom de 200%, sem perda de conteúdo | | |
| C10 · Mensagens em `role="status"`/`role="alert"` (ou `<output>`), sem mover o foco | | |

### 2. Troca de senha

Rotas: `/senha/definir` (troca obrigatória, T-15) e `/senha/alterar`.

| Item da checklist | Resultado | Evidência |
|---|---|---|
| C1 · `lang="pt-BR"` e título "Página \| Furin Access" | | |
| C2 · Link "Ir para o conteúdo principal"; menu na mesma ordem | | |
| C3 · h1 único e h2 por seção | | |
| C4 · Todos os campos com `label`; "(obrigatório)" escrito | | |
| C5 · Erro descritivo com `aria-invalid` e `aria-describedby`; dados válidos preservados | | |
| C6 · Só teclado; ordem de foco lógica; foco de 3 px visível e nunca coberto | | |
| C7 · Estado em texto, não só em cor; contraste (texto 4,5:1, componentes 3:1) | | |
| C8 · Alvos de 44 × 44 px | | |
| C9 · 320 px de largura e zoom de 200%, sem perda de conteúdo | | |
| C10 · Mensagens em `role="status"`/`role="alert"` (ou `<output>`), sem mover o foco | | |

### 3. Portaria

Rotas: `/portaria` (campo de leitura, painel do funcionário, monitor do aluno, "Simular leitura", "Simular passagem", "Cancelar liberação").

| Item da checklist | Resultado | Evidência |
|---|---|---|
| C1 · `lang="pt-BR"` e título "Página \| Furin Access" | | |
| C2 · Link "Ir para o conteúdo principal"; menu na mesma ordem | | |
| C3 · h1 único e h2 por seção | | |
| C4 · Todos os campos com `label`; "(obrigatório)" escrito | | |
| C5 · Erro descritivo com `aria-invalid` e `aria-describedby`; dados válidos preservados | | |
| C6 · Só teclado; ordem de foco lógica; foco de 3 px visível e nunca coberto | | |
| C7 · Estado em texto, não só em cor; contraste (texto 4,5:1, componentes 3:1) | | |
| C8 · Alvos de 44 × 44 px | | |
| C9 · 320 px de largura e zoom de 200%, sem perda de conteúdo | | |
| C10 · Mensagens em `role="status"`/`role="alert"` (ou `<output>`), sem mover o foco | | |

### 4. Registro manual

Rotas: `/portaria/manual` (busca), `/portaria/manual/{id}` (formulário) e a revisão.

| Item da checklist | Resultado | Evidência |
|---|---|---|
| C1 · `lang="pt-BR"` e título "Página \| Furin Access" | | |
| C2 · Link "Ir para o conteúdo principal"; menu na mesma ordem | | |
| C3 · h1 único e h2 por seção | | |
| C4 · Todos os campos com `label`; "(obrigatório)" escrito | | |
| C5 · Erro descritivo com `aria-invalid` e `aria-describedby`; dados válidos preservados | | |
| C6 · Só teclado; ordem de foco lógica; foco de 3 px visível e nunca coberto | | |
| C7 · Estado em texto, não só em cor; contraste (texto 4,5:1, componentes 3:1) | | |
| C8 · Alvos de 44 × 44 px | | |
| C9 · 320 px de largura e zoom de 200%, sem perda de conteúdo | | |
| C10 · Mensagens em `role="status"`/`role="alert"` (ou `<output>`), sem mover o foco | | |

### 5. Alunos

Rotas: `/alunos` (busca, com e sem "Incluir inativos").

| Item da checklist | Resultado | Evidência |
|---|---|---|
| C1 · `lang="pt-BR"` e título "Página \| Furin Access" | | |
| C2 · Link "Ir para o conteúdo principal"; menu na mesma ordem | | |
| C3 · h1 único e h2 por seção | | |
| C4 · Todos os campos com `label`; "(obrigatório)" escrito | | |
| C5 · Erro descritivo com `aria-invalid` e `aria-describedby`; dados válidos preservados | | |
| C6 · Só teclado; ordem de foco lógica; foco de 3 px visível e nunca coberto | | |
| C7 · Estado em texto, não só em cor; contraste (texto 4,5:1, componentes 3:1) | | |
| C8 · Alvos de 44 × 44 px | | |
| C9 · 320 px de largura e zoom de 200%, sem perda de conteúdo | | |
| C10 · Mensagens em `role="status"`/`role="alert"` (ou `<output>`), sem mover o foco | | |

### 6. Cadastro

Rotas: `/alunos/novo` (vazio, com erros e com matrícula repetida).

| Item da checklist | Resultado | Evidência |
|---|---|---|
| C1 · `lang="pt-BR"` e título "Página \| Furin Access" | | |
| C2 · Link "Ir para o conteúdo principal"; menu na mesma ordem | | |
| C3 · h1 único e h2 por seção | | |
| C4 · Todos os campos com `label`; "(obrigatório)" escrito | | |
| C5 · Erro descritivo com `aria-invalid` e `aria-describedby`; dados válidos preservados | | |
| C6 · Só teclado; ordem de foco lógica; foco de 3 px visível e nunca coberto | | |
| C7 · Estado em texto, não só em cor; contraste (texto 4,5:1, componentes 3:1) | | |
| C8 · Alvos de 44 × 44 px | | |
| C9 · 320 px de largura e zoom de 200%, sem perda de conteúdo | | |
| C10 · Mensagens em `role="status"`/`role="alert"` (ou `<output>`), sem mover o foco | | |

### 7. Edição

Rotas: `/alunos/{id}/editar`, revisão das alterações, inativação e revogação do termo.

| Item da checklist | Resultado | Evidência |
|---|---|---|
| C1 · `lang="pt-BR"` e título "Página \| Furin Access" | | |
| C2 · Link "Ir para o conteúdo principal"; menu na mesma ordem | | |
| C3 · h1 único e h2 por seção | | |
| C4 · Todos os campos com `label`; "(obrigatório)" escrito | | |
| C5 · Erro descritivo com `aria-invalid` e `aria-describedby`; dados válidos preservados | | |
| C6 · Só teclado; ordem de foco lógica; foco de 3 px visível e nunca coberto | | |
| C7 · Estado em texto, não só em cor; contraste (texto 4,5:1, componentes 3:1) | | |
| C8 · Alvos de 44 × 44 px | | |
| C9 · 320 px de largura e zoom de 200%, sem perda de conteúdo | | |
| C10 · Mensagens em `role="status"`/`role="alert"` (ou `<output>`), sem mover o foco | | |

### 8. Carteirinha

Rotas: `/carteirinhas/{id}/imprimir` e `/alunos/{id}/carteirinhas/segunda-via`.

| Item da checklist | Resultado | Evidência |
|---|---|---|
| C1 · `lang="pt-BR"` e título "Página \| Furin Access" | | |
| C2 · Link "Ir para o conteúdo principal"; menu na mesma ordem | | |
| C3 · h1 único e h2 por seção | | |
| C4 · Todos os campos com `label`; "(obrigatório)" escrito | | |
| C5 · Erro descritivo com `aria-invalid` e `aria-describedby`; dados válidos preservados | | |
| C6 · Só teclado; ordem de foco lógica; foco de 3 px visível e nunca coberto | | |
| C7 · Estado em texto, não só em cor; contraste (texto 4,5:1, componentes 3:1) | | |
| C8 · Alvos de 44 × 44 px | | |
| C9 · 320 px de largura e zoom de 200%, sem perda de conteúdo | | |
| C10 · Mensagens em `role="status"`/`role="alert"` (ou `<output>`), sem mover o foco | | |

### 9. Presentes

Rotas: `/presentes` (com "Atualizar" e com a atualização automática).

| Item da checklist | Resultado | Evidência |
|---|---|---|
| C1 · `lang="pt-BR"` e título "Página \| Furin Access" | | |
| C2 · Link "Ir para o conteúdo principal"; menu na mesma ordem | | |
| C3 · h1 único e h2 por seção | | |
| C4 · Todos os campos com `label`; "(obrigatório)" escrito | | |
| C5 · Erro descritivo com `aria-invalid` e `aria-describedby`; dados válidos preservados | | |
| C6 · Só teclado; ordem de foco lógica; foco de 3 px visível e nunca coberto | | |
| C7 · Estado em texto, não só em cor; contraste (texto 4,5:1, componentes 3:1) | | |
| C8 · Alvos de 44 × 44 px | | |
| C9 · 320 px de largura e zoom de 200%, sem perda de conteúdo | | |
| C10 · Mensagens em `role="status"`/`role="alert"` (ou `<output>`), sem mover o foco | | |

### 10. Histórico

Rotas: `/historico` (filtros, erro de data, tabela e paginação).

| Item da checklist | Resultado | Evidência |
|---|---|---|
| C1 · `lang="pt-BR"` e título "Página \| Furin Access" | | |
| C2 · Link "Ir para o conteúdo principal"; menu na mesma ordem | | |
| C3 · h1 único e h2 por seção | | |
| C4 · Todos os campos com `label`; "(obrigatório)" escrito | | |
| C5 · Erro descritivo com `aria-invalid` e `aria-describedby`; dados válidos preservados | | |
| C6 · Só teclado; ordem de foco lógica; foco de 3 px visível e nunca coberto | | |
| C7 · Estado em texto, não só em cor; contraste (texto 4,5:1, componentes 3:1) | | |
| C8 · Alvos de 44 × 44 px | | |
| C9 · 320 px de largura e zoom de 200%, sem perda de conteúdo | | |
| C10 · Mensagens em `role="status"`/`role="alert"` (ou `<output>`), sem mover o foco | | |

### 11. Impressão

Rotas: `/historico/impressao` (tela e visualização de impressão do navegador).

| Item da checklist | Resultado | Evidência |
|---|---|---|
| C1 · `lang="pt-BR"` e título "Página \| Furin Access" | | |
| C2 · Link "Ir para o conteúdo principal"; menu na mesma ordem | | |
| C3 · h1 único e h2 por seção | | |
| C4 · Todos os campos com `label`; "(obrigatório)" escrito | | |
| C5 · Erro descritivo com `aria-invalid` e `aria-describedby`; dados válidos preservados | | |
| C6 · Só teclado; ordem de foco lógica; foco de 3 px visível e nunca coberto | | |
| C7 · Estado em texto, não só em cor; contraste (texto 4,5:1, componentes 3:1) | | |
| C8 · Alvos de 44 × 44 px | | |
| C9 · 320 px de largura e zoom de 200%, sem perda de conteúdo | | |
| C10 · Mensagens em `role="status"`/`role="alert"` (ou `<output>`), sem mover o foco | | |

### 12. Contas da equipe

Rotas: `/contas/equipe`, `/contas/equipe/nova`, confirmação de cada ação e senha temporária.

| Item da checklist | Resultado | Evidência |
|---|---|---|
| C1 · `lang="pt-BR"` e título "Página \| Furin Access" | | |
| C2 · Link "Ir para o conteúdo principal"; menu na mesma ordem | | |
| C3 · h1 único e h2 por seção | | |
| C4 · Todos os campos com `label`; "(obrigatório)" escrito | | |
| C5 · Erro descritivo com `aria-invalid` e `aria-describedby`; dados válidos preservados | | |
| C6 · Só teclado; ordem de foco lógica; foco de 3 px visível e nunca coberto | | |
| C7 · Estado em texto, não só em cor; contraste (texto 4,5:1, componentes 3:1) | | |
| C8 · Alvos de 44 × 44 px | | |
| C9 · 320 px de largura e zoom de 200%, sem perda de conteúdo | | |
| C10 · Mensagens em `role="status"`/`role="alert"` (ou `<output>`), sem mover o foco | | |

### 13. Página 403

Rotas: Qualquer resposta 403 (por exemplo, um formulário com o token CSRF vencido).

| Item da checklist | Resultado | Evidência |
|---|---|---|
| C1 · `lang="pt-BR"` e título "Página \| Furin Access" | | |
| C2 · Link "Ir para o conteúdo principal"; menu na mesma ordem | | |
| C3 · h1 único e h2 por seção | | |
| C4 · Todos os campos com `label`; "(obrigatório)" escrito | | |
| C5 · Erro descritivo com `aria-invalid` e `aria-describedby`; dados válidos preservados | | |
| C6 · Só teclado; ordem de foco lógica; foco de 3 px visível e nunca coberto | | |
| C7 · Estado em texto, não só em cor; contraste (texto 4,5:1, componentes 3:1) | | |
| C8 · Alvos de 44 × 44 px | | |
| C9 · 320 px de largura e zoom de 200%, sem perda de conteúdo | | |
| C10 · Mensagens em `role="status"`/`role="alert"` (ou `<output>`), sem mover o foco | | |


## 3. Roteiro do teste manual

Rode o protótipo (`SPRING_PROFILES_ACTIVE=prototipo`), abra `http://localhost:8080` no
Chrome e entre com `ate.demo`. Use só os alunos da carga fictícia.

### 3.1 Teclado (C2, C6, CA-RNF06-02)

Em cada tela, sem tocar no mouse:

1. Recarregue a página e tecle **Tab** uma vez: deve aparecer "Ir para o conteúdo
   principal". **Enter** leva ao h1 da tela.
2. Siga com **Tab** até o fim e volte com **Shift+Tab**: o contorno de foco aparece em
   todo elemento, nada fica escondido atrás de outro, e a ordem segue a leitura (no
   cadastro: Nome → Matrícula → Turma → Foto → contato → termo → Salvar → Cancelar; no
   histórico: data inicial → data final → turma → aluno → Filtrar).
3. Acione botões com **Enter** e **Espaço**, links com **Enter** e caixas de seleção com
   **Espaço**.
4. Formulários: envie vazio. O foco vai para o resumo de erros (ou para o campo, no login
   e na senha), e cada link do resumo leva ao campo.
5. Portaria: digite um código e tecle Enter; use "Ler carteirinha de …", "Simular
   passagem (giro)" e "Cancelar liberação de …". Depois de cada ação o foco volta ao campo
   de leitura, e o Tab sai dele normalmente.
6. Sessão: deixe a tela parada até o aviso de expiração. O foco vai para "Continuar
   conectado"; **Esc** também continua.

### 3.2 NVDA (C1, C3, C4, C10, CA-RNF06-05)

Com o NVDA ligado e o Chrome em primeiro plano:

1. Ao abrir cada tela, o NVDA lê o título "Tela | Furin Access".
2. **H** (próximo cabeçalho) e **NVDA+F7** (lista de elementos, aba Cabeçalhos): um h1 e
   os h2 das seções.
3. **Tab** pelos campos: cada um é lido com o rótulo, "obrigatório" quando for o caso e a
   dica. Com erro, lê "inválido" e a mensagem.
4. Tabelas (Alunos, Presentes, Histórico, Contas): **Ctrl+Alt+setas** dentro da tabela;
   cada célula é lida com o cabeçalho da coluna e o nome do aluno da linha.
5. Mensagens, com o foco parado no lugar:
   - portaria: liberação, registro, "Leitura recusada: …" (leia um código inválido, como
     `XYZ`), "Aguarde a passagem do aluno anterior" e "Liberação expirada";
   - busca de alunos e registro manual: "N alunos encontrados";
   - presentes: só o "Atualizar" é anunciado; a atualização automática fica em silêncio;
   - contas: "Senha copiada."; a senha mascarada é lida como "oculta".
6. Anote o que foi lido de diferente na coluna "Evidência" da tela.

### 3.3 Lighthouse (C7, CA-RNF06-04, CA-RNF06-06)

1. Em cada tela, **F12** → aba **Lighthouse** → **Mode: Navigation** (ou **Snapshot** para
   uma tela com erro ou com resultado de leitura) → **Device: Desktop** → marque só
   **Accessibility** → **Analyze page load**.
2. Salve o relatório (menu ⋮ → **Save as HTML**) com o nome da tela e anexe ao cartão.
3. Repita com **Device: Mobile** nas telas Login, Portaria, Alunos, Presentes, Histórico e
   Contas da equipe.
4. Cole aqui, ou no PR, cada item apontado, com a tela e o impacto. O CA-RNF06-06 pede zero
   itens "críticos" ou "sérios" (no axe DevTools, *critical* e *serious*).

### 3.4 Largura de 320 px e zoom (C9)

1. **F12** → **Ctrl+Shift+M** (modo dispositivo) → largura **320** → percorra cada tela:
   nada some e a página não rola na horizontal (as tabelas podem rolar dentro do próprio
   quadro).
2. Sem o modo dispositivo, numa janela de 1280 px, **Ctrl +** até **200%** e depois
   **400%** (A11 p.9): o conteúdo se reorganiza sem cortar texto nem botões.
3. Escala de cinza (CA-RNF06-03): **F12** → ⋮ → **More tools** → **Rendering** →
   **Emulate vision deficiencies: Achromatopsia**. Os estados continuam claros pelo texto.

## 4. Histórico deste arquivo

| Data | Mudança |
|---|---|
| 12/11/2026 | Criado na T-45: revisão do código, 8 correções, checklist em branco e roteiro |
