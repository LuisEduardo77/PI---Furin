# RNF01 — Acesso por navegador, com layout responsivo

| Campo | Valor |
|---|---|
| **Categoria** | Plataforma |
| **Documentação** | Revisada em 21/10/2026 (DEC-37 a DEC-39) |
| **Implementação** | Planejada |
| **Atualizado em** | 21/10/2026 |

## Descrição

O sistema é usado **só pelo navegador** (Chrome, Edge e Firefox), sem aplicativo para
instalar. O layout se adapta a computadores, tablets e celulares.

## Justificativa

- A escola já tem computadores com navegador na portaria e na secretaria.
- A A07 (US10) previa a consulta dos responsáveis pelo celular. Essa consulta saiu do
  escopo (DEC-37), mas a equipe pode usar o sistema em telas pequenas.
- A A05 deixa o aplicativo para celular fora do MVP.

Uma única aplicação web, com layout responsivo, atende a todos os perfis sem custo de
instalação.

## Fonte documental

| Origem | Conteúdo |
|---|---|
| A08 p.4 (RNF01) | "Acesso por navegador (Chrome, Edge, Firefox), com layout responsivo" — origem: Atividade 5 |
| A05 p.2 | "Acessado exclusivamente por meio de navegador web (acessível via computadores, tablets ou celulares de forma responsiva)" |
| A05 p.3 | Aplicativo para celular fora do MVP |
| A07 p.3 (US10) | O responsável quer verificar "no meu celular" |
| A11 p.7 §5.10.3, §5.10.4 | Orientação retrato e paisagem. Refluxo em 320 px CSS sem rolagem nas duas direções |
| A11 p.7 §5.12.7, §5.13.5 | Texto ampliável até 200% e zoom do navegador liberado |
| A13 p.3 | O acesso por celular no protótipo depende da conectividade do ambiente local |

**Alteração em relação à A08:** nenhuma no texto. Os critérios de responsividade de A11
foram incorporados.

## Partes do sistema afetadas

Todas as telas:

- login e troca de senha;
- portaria (os dois painéis);
- cadastro e edição;
- busca;
- presentes;
- histórico e impressão;
- contas da equipe.

O layout-base fica em [plano T-08](../../docs/plano-de-implementacao.md).

## Critérios de aceitação

| ID | Critério |
|---|---|
| CA-RNF01-01 | Todas as telas funcionam nas **duas últimas versões estáveis** de Chrome, Edge e Firefox para desktop (DEC-28, G3) |
| CA-RNF01-02 | Com 320 px CSS de largura, as telas mostram todo o conteúdo e todas as funções sem rolagem horizontal da página. Tabelas largas podem rolar dentro do próprio contêiner (A11 §5.10.4) |
| CA-RNF01-03 | As telas funcionam em retrato e em paisagem, sem bloquear a orientação (A11 §5.10.3) |
| CA-RNF01-04 | O zoom do navegador não está bloqueado (sem `user-scalable=no` ou `maximum-scale`). Com 200%, não há perda de conteúdo (A11 §5.12.7, §5.13.5) |
| CA-RNF01-05 | As telas de consulta (Presentes e Histórico) funcionam no navegador do celular, com alvos de toque de pelo menos 44 × 44 px (A11 §5.8.6) |
| CA-RNF01-06 | Nenhuma função exige instalar aplicativo, extensão ou plugin |
| CA-RNF01-07 | Em telas estreitas, os painéis da portaria (funcionário e monitor do aluno) ficam um abaixo do outro |

## Verificação e evidência esperada

| Forma | Como | Evidência |
|---|---|---|
| Manual (MAN) | Percorrer as telas principais em Chrome, Edge e Firefox para desktop, e em um celular Android com Chrome | Matriz navegador × tela preenchida (OK / falha), anexada ao cartão T-45 |
| Manual (MAN) | Modo responsivo das ferramentas do navegador com 320 px, 768 px e 1280 px. Zoom de 200% e 400% em janela de 1280 px (A11 p.9) | Capturas de tela por largura |
| Revisão de código | Conferir a meta tag viewport sem bloqueio de zoom e as unidades relativas (`rem`, `%`) | Comentário no PR |

## Dependências

| Tipo | Itens |
|---|---|
| RNF | RNF06 (acessibilidade) |
| RF | Todos. Em especial RF09/RF14 (portaria), RF16 (presentes) e RF18 (histórico) |
| Tarefas | T-08, T-45 |

## Protótipo e implementação

| Aspecto | Protótipo | Implementação (fora do PI) |
|---|---|---|
| Acesso | Navegador no mesmo computador da aplicação, ou na rede local (A13 p.3) | Ambiente acessível pela internet, com HTTPS (RNF04) |
| Celular | Demonstrado no modo responsivo do navegador ou em um celular na mesma rede | Acesso pelo celular ou tablet de um funcionário |

## Pendências

| Pendência | Situação |
|---|---|
| Versões mínimas suportadas de cada navegador | **Resolvida** (DEC-28, G3): as duas últimas versões estáveis de cada navegador |
| **Safari (iPhone e Mac) não está na lista da A08**, mas muitos responsáveis usam iPhone | **Obsoleta** (DEC-37): a DEC-28 (G3) incluía o Safari no iOS por causa da consulta do responsável (RF24), que saiu do escopo. O Safari não entra na matriz obrigatória |
| Telas sem JavaScript | **Resolvida** (DEC-28, G3): só a portaria exige JavaScript. As demais telas funcionam com envio de formulário comum |
