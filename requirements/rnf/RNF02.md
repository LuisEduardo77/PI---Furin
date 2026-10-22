# RNF02 — Confirmação da leitura na tela em até 2 segundos

| Campo | Valor |
|---|---|
| **Categoria** | Desempenho |
| **Documentação** | Revisada em 19/10/2026 (T-00: DEC-24 a DEC-36) |
| **Implementação** | Planejada |
| **Atualizado em** | 19/10/2026 |

## Descrição

Depois que o leitor USB envia o código, a tela da portaria mostra o resultado da
leitura em **até 2 segundos**. O resultado pode ser liberação, leitura ignorada ou
recusa.

## Justificativa

A portaria recebe muitos alunos ao mesmo tempo (A05 p.2). Uma resposta lenta forma fila
no portão, que é justamente o problema que o sistema quer resolver (US11: "sem fila no
portão").

## Fonte documental

| Origem | Conteúdo |
|---|---|
| A08 p.4 (RNF02) | "Confirmação da leitura na tela em até 2 segundos" — origem: US3 |
| A07 p.2 (US3) | "A tela confirma a leitura válida com destaque visual e texto" |
| A07 p.4 (US11) | "Para que minha presença seja registrada rápido, sem fila no portão" |
| A02 p.3 | Demanda por identificação rápida "evitando gargalos" |
| A13 p.5 (item 5) | O envio do e-mail fica fora do caminho crítico da leitura, "para não atrasar a confirmação prevista no RNF02" |
| **DEC-19** | A movimentação só vale depois do giro. A resposta à leitura é a **liberação** |

**Alteração em relação à A08:** com a DEC-19, a "confirmação da leitura" passa a ser a
**resposta de liberação**, que aparece antes do giro. O valor de **2 segundos** foi
mantido.

## Partes do sistema afetadas

| Parte | Por quê |
|---|---|
| `POST /portaria/leituras` | É o caminho medido |
| Serviço da portaria (RN01, RN03–RN09) | Validações e bloqueio por aluno |
| Banco: índices de `carteirinha.codigo` e `registro_acesso (id_vinculo, situacao, ocorrido_em)` | Consultas da leitura |
| Tela da portaria (JavaScript) | Exibição nos dois painéis |
| Notificação (RF21) | **Fica fora** do caminho medido: é processada depois do commit |
| Foto (RF10) | O carregamento da imagem pode terminar depois. O nome, a turma e o tipo aparecem primeiro (DEC-28, A9) |

RF afetados: RF09, RF10, RF13, RF14, RF15, RF22, RF23.

## Critérios de aceitação

| ID | Critério |
|---|---|
| CA-RNF02-01 | Entre o Enter no campo de leitura (ou o clique no painel de simulação, DEC-25) e a mensagem de liberação, ignorada ou recusada aparecer nos dois painéis, passam **no máximo 2 segundos** |
| CA-RNF02-02 | O critério vale em **todas** as leituras de uma rodada de medição, no ambiente de demonstração e com a carga de dados de referência. A rodada tem **30 leituras seguidas** e a carga de referência tem **800 alunos fictícios e 30 dias de movimentações** (DEC-28, G4) |
| CA-RNF02-03 | A resposta à leitura não espera o envio da notificação (RF21) nem o carregamento completo da foto |
| CA-RNF02-04 | (DEC-28, A10) A resposta ao giro simulado (confirmação) também aparece em até 2 s |

## Verificação e evidência esperada

| Forma | Como | Evidência |
|---|---|---|
| Manual (MAN), com medição | No ambiente de demonstração, fazer a rodada de leituras simuladas (campo de leitura ou painel, DEC-25) com carteirinhas fictícias. Medir pela aba Rede das ferramentas do navegador, ou com `performance.now()` registrado no console | Planilha com cada medição (ms), o maior valor e o ambiente usado (máquina, navegador, quantidade de dados), anexada ao cartão T-33 |
| Teste automatizado (INT) [PROP: detalhe de implementação] | Teste que faz 30 leituras na base de teste e falha se alguma resposta do servidor passar de um limite de referência | Relatório da CI. **Não substitui** a medição manual, porque a CI não mede a tela |

## Dependências

| Tipo | Itens |
|---|---|
| RF | RF09, RF14 (fluxo da leitura), RF21 (envio assíncrono) |
| RNF | RNF07 (o leitor USB entrega o código como teclado), RNF08 (stack) |
| Tarefas | T-26, T-30, T-33 |

## Protótipo e implementação

| Aspecto | Protótipo | Implementação (fora do PI) |
|---|---|---|
| Ambiente | Aplicação, banco e navegador no mesmo computador (A13 p.3) | Servidor remoto, com a latência da rede somada |
| O que a medição prova | Que a lógica cabe em 2 s localmente | Precisa ser medido de novo no ambiente real. **A evidência do protótipo não vale para a implantação** |

## Pendências

| Pendência | Situação |
|---|---|
| Carga de dados de referência para a medição | **Resolvida** (DEC-28, G4): base fictícia com 800 alunos e 30 dias de movimentações, gerada por script separado |
| Tamanho da rodada de medição | **Resolvida** (DEC-28, G4): 30 leituras seguidas |
| Estender o limite de 2 s à confirmação do giro | **Aprovado** (DEC-28, A10) |
