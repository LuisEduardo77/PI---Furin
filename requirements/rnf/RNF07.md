# RNF07 — Compatibilidade com leitor USB de QR Code que funciona como teclado

| Campo | Valor |
|---|---|
| **Categoria** | Hardware |
| **Documentação** | Revisada em 21/10/2026 (DEC-37 a DEC-39) |
| **Implementação** | Planejada. Meta no protótipo: **Simulada**. O grupo **não tem leitor USB** (DEC-25), então a leitura, a catraca e o sensor de giro são simulados pelas telas |
| **Atualizado em** | 21/10/2026 |

## Descrição

Na implantação, a portaria funciona com um **leitor USB de QR Code** que se comporta
como teclado. O leitor "digita" o código lido no campo de leitura do navegador e envia
**Enter** no final. Não é preciso instalar driver, aplicativo nem extensão.

**No protótipo não há leitor físico** (DEC-25). A portaria oferece dois caminhos
simulados. Os dois enviam o código pela **mesma rota** que o leitor real usaria
(`POST /portaria/leituras`):

1. **Campo de leitura:** o funcionário digita ou cola o código de 32 caracteres e aperta Enter.
   É exatamente o que o leitor faria, então o sistema fica pronto para receber um
   leitor depois sem mudar o código.
2. **Painel "Simular leitura (protótipo)":** lista as carteirinhas fictícias ativas. Um
   botão por aluno ("Ler carteirinha de Ana Teste") envia o código daquela carteirinha.

A **catraca** e o **sensor de giro** também são simulados, pelo botão "Simular passagem
(giro)" (DEC-18). A integração física fica para depois do MVP.

## Justificativa

- Um leitor que funciona como teclado tem baixo custo e já é aceito por qualquer
  navegador (A03, H2).
- Esse tipo de leitor dispensa integração de hardware complexa, como a de catraca com
  RFID (H3), que é cara e de alta complexidade (A03).
- Sem equipamento disponível, simular a leitura **pelo mesmo caminho de entrada**
  (campo + Enter) mantém o protótipo fiel ao uso real e permite testar tudo sem
  hardware (DEC-25).
- A integração física com a catraca ficou fora do MVP (DEC-18). Mesmo assim, o fluxo
  foi desenhado para recebê-la depois: liberação primeiro, confirmação pelo giro
  depois (DEC-19).

## Fonte documental

| Origem | Conteúdo |
|---|---|
| A08 p.4 (RNF07) | "Compatibilidade com leitor USB de código de barras/QR Code que funciona como teclado" — origem: planilha de hipóteses |
| A03 p.1 (H2) | Leitor USB tipo pistola ligado ao computador. Baixo custo de hardware. Leitura rápida |
| A03 p.1 (H3) | Catraca com RFID: alto custo e alta complexidade. Integração serial/TCP |
| A05 p.3 | Catracas eletrônicas fora do MVP |
| A13 p.3 | "Ler o código da carteirinha como entrada de teclado no navegador da portaria, conforme RNF07" |
| A14 p.6 | O código tem 32 caracteres hexadecimais |
| **DEC-07** | **QR Code** |
| **DEC-02, DEC-18, DEC-19** | Sensor de giro da catraca, simulado no protótipo. A movimentação só vale depois do giro |
| **DEC-25** | **O grupo não tem leitor USB.** A leitura é simulada pelas telas: campo de digitação ou colagem mais o painel "Simular leitura" |

**Alterações em relação à A08:**

- o formato foi fixado em **QR Code** (o leitor precisa ler QR);
- entra o **giro simulado** como parte do fluxo de hardware;
- no protótipo, a **leitura também é simulada** (DEC-25). O requisito de compatibilidade
  com o leitor continua valendo para a implantação.

## Partes do sistema afetadas

| Parte | Aplicação |
|---|---|
| Tela da portaria | Campo "Leitura da carteirinha" com foco automático. O envio acontece com Enter |
| Painel "Simular leitura (protótipo)" | Lista das carteirinhas fictícias ativas, com um botão por aluno. **Só existe no perfil `prototipo`** e aparece identificado como simulação |
| JavaScript da portaria | Captura do Enter, envio, limpeza do campo e retorno do foco. Os botões do painel usam a mesma função de envio |
| Validação no servidor | Formato: 32 caracteres `[0-9a-f]` (RF23). O servidor trata igual o código vindo do campo e o do painel |
| Carteirinha impressa | QR Code gerado pelo RF07. No protótipo, a leitura do QR é verificada por um decodificador em teste automatizado |
| Giro | Botão "Simular passagem (giro)" (RF09, RF14), só no perfil `prototipo` |

## Requisitos do leitor para a implantação (DEC-28)

| Característica | Necessário | Motivo |
|---|---|---|
| Tipo de leitura | **Imagem 2D**, que lê QR Code | Leitores a laser 1D **não** leem QR Code |
| Interface | USB HID, emulando teclado | Funciona no navegador sem driver |
| Sufixo | Enter (CR) depois do código | Envia a leitura sem clique |
| Prefixo | Nenhum | O campo recebe só o código |
| Layout do teclado | Indiferente | O código usa só `0-9` e `a-f`, que ficam no mesmo lugar em ABNT2 e US |

## Critérios de aceitação

### No protótipo (verificáveis agora)

| ID | Critério |
|---|---|
| CA-RNF07-01 | Com o foco no campo de leitura, digitar ou colar os 32 caracteres de um código fictício e apertar Enter envia a leitura. O resultado é igual ao de um leitor real |
| CA-RNF07-02 | Funciona em Chrome, Edge e Firefox (RNF01), sem driver, extensão ou configuração no navegador |
| CA-RNF07-03 | Depois de cada leitura, o campo fica vazio e com o foco, pronto para a próxima, sem clique (A11 §5.1.13) |
| CA-RNF07-04 | Se o foco sair do campo, a tela mostra "Leitor não está pronto. Clique no campo de leitura." e volta o foco quando o usuário aciona a portaria (DEC-28, A8) |
| CA-RNF07-05 | O botão "Ler carteirinha de Ana Teste" do painel envia o código da carteirinha **ativa** de Ana pela mesma rota. A resposta é igual à do campo |
| CA-RNF07-06 | Uma leitura parcial ou com caracteres a mais é recusada como "Leitura não reconhecida" (RF23) |
| CA-RNF07-07 | O painel "Simular leitura" e o botão "Simular passagem (giro)" aparecem identificados como **simulação** e **não existem** fora do perfil `prototipo` (a rota e os elementos não são renderizados) |
| CA-RNF07-08 | O QR Code gerado pelo RF07 é decodificado em teste automatizado (ZXing) e devolve exatamente o código gravado |
| CA-RNF07-09 | Os botões do painel funcionam por teclado, têm alvo de pelo menos 44 px e nome acessível com o nome do aluno (RNF06) |

### Na implantação (fora do protótipo, não verificados agora)

| ID | Critério |
|---|---|
| CA-RNF07-10 | Ler o QR Code de uma carteirinha impressa com um leitor 2D em modo teclado coloca **exatamente** os 32 caracteres no campo e envia a leitura com o Enter |
| CA-RNF07-11 | O giro físico da catraca chega ao servidor como evento e confirma a liberação, como hoje faz o botão de simulação |

## Verificação e evidência esperada

| Forma | Como | Evidência |
|---|---|---|
| UNI | CA-RNF07-08: gerar o QR Code e decodificar a imagem | Relatório da CI |
| INT | CA-RNF07-01, 05, 06 e 07: envio do código pela rota, pelos dois caminhos, e ausência do painel fora do perfil `prototipo` | Relatório da CI |
| MAN | CA-RNF07-02, 03, 04 e 09: digitar e colar códigos fictícios e usar o painel, em cada navegador | Planilha (navegador × resultado) anexada ao cartão T-33 |
| DEM | Leitura pelo painel e pelo campo, seguida do giro simulado | Roteiro da demonstração (T-46) |
| Implantação | CA-RNF07-10 e 11 com o equipamento real | Fora do protótipo |

## Dependências

| Tipo | Itens |
|---|---|
| RF | RF07 (QR Code), RF09, RF14 (leitura e giro), RF23 (leitura inválida) |
| RNF | RNF01 (navegadores), RNF02 (tempo de resposta), RNF06 (acessibilidade) |
| Tarefas | T-22, T-28, T-30, T-33 |

## Protótipo e implementação

| Aspecto | Protótipo | Implantação (fora do MVP) |
|---|---|---|
| Leitor | **Simulado** (DEC-25): digitação ou colagem no campo, ou botão do painel "Simular leitura" | Leitor USB 2D na portaria, no mesmo campo |
| Catraca e sensor de giro | **Simulados** pelo botão (DEC-18) | Integração física. O controlador da catraca envia o evento de giro ao servidor, por protocolo **a definir** |
| Monitor do aluno | Painel na mesma tela (DEC-06) | Segundo monitor voltado ao aluno |

## Pendências

| Pendência | Situação |
|---|---|
| Modelo do leitor USB | **Não se aplica ao protótipo**: o grupo não tem leitor (DEC-25). Escolha na implantação, seguindo a tabela "Requisitos do leitor" |
| Integração física com a catraca e protocolo do sensor de giro | Fora do MVP (DEC-18). Protocolo **a definir** em evolução futura |
