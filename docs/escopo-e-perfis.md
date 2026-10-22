# Escopo e perfis

**Documentação:** Revisada em 21/10/2026 (DEC-37 a DEC-39) · **Atualizado em:** 21/10/2026

## 1. Contexto e problema [DOC]

A EMEF Eng. José Amadei atende mais de 800 alunos do Ensino Fundamental I e II e se
relaciona com a comunidade local, empresas terceirizadas e a Diretoria Regional de
Ensino (DRE) (A01 p.2; A05 p.2).

Hoje a entrada é manual. Um ATE abre o portão e muitos alunos entram ao mesmo tempo,
sem identificação individual. Por isso, a escola não tem como identificar, contar e
registrar com segurança quem entrou (A02 p.2; A05 p.2).

| Categoria (A02 p.3) | Demanda | Como o Furin Access responde |
|---|---|---|
| Controle de acesso e segurança | Identificar cada aluno rapidamente, sem gargalo | Leitura do QR Code com resposta em até 2 s (RF09, RNF02) |
| Controle de acesso e segurança | Risco de entrada de pessoas não autorizadas | Liberação individual confirmada pela passagem na catraca (RF09, RF14). A integração física fica fora do MVP |
| Gestão administrativa | Contar na hora os alunos presentes | Painel de presentes (RF16) |
| Gestão administrativa | Relatórios de frequência lentos | Histórico com filtros e impressão (RF18–RF20) |
| Comunicação e transparência | Responsáveis sem aviso de chegada | Notificação por e-mail (RF21). Os pais não têm conta (DEC-37) |

## 2. Objetivo [DOC A05 p.2]

Registrar a entrada e a saída dos alunos, apoiar a contagem para a merenda escolar e
avisar os responsáveis, por e-mail, de cada entrada e saída dos filhos.

## 3. Hipóteses, sistemas comparados e solução escolhida

| Item | Fonte | Papel no projeto |
|---|---|---|
| H1: planilha Excel/Sheets | A03 p.1 | Hipótese descartada |
| **H2: sistema web com leitura óptica** | A03 p.1 | **Base da solução escolhida** (A05 p.3; A08 RNF07; A13 p.2–3) |
| H3: catraca eletrônica com RFID | A03 p.1, marcada "ESCOLHIDO" | RFID e integração física com catraca **fora do MVP** (A05 p.3). O giro da catraca entra **simulado** no protótipo [DEC-18]. A marcação da A03 diverge; vale esta documentação (DEC-35) |
| Furin LTDA (nossa proposta no comparativo) | A04 p.1 | Descrita com bloqueio físico, como a H3. Diverge da solução atual (H2 com leitura e giro simulados); vale esta documentação (DEC-35) |
| Escola Ágil | A04 p.1 | **Sistema comparado.** Reconhecimento facial e aplicativo fora do escopo |
| EasyAccess/EasySchool | A04 p.1 | **Sistema comparado.** Reconhecimento facial e RFID fora do escopo |

**Solução do MVP:**

- Aplicação web acessada pelo navegador.
- Leitor USB de **QR Code** que funciona como teclado [DEC-07]. **No protótipo não há
  leitor:** a leitura é simulada pelo campo de leitura (digitar ou colar o código) ou
  pelo painel "Simular leitura (protótipo)" [DEC-25].
- Uma leitura válida **libera** a passagem, e a movimentação **só é registrada depois
  do giro da catraca** [DEC-19].
- No protótipo, o giro é acionado por um botão "Simular passagem (giro)" [DEC-18].

## 4. Escopo do MVP

A coluna **Forma no protótipo** diz como cada item será entregue: *Implementada*
(função real) ou *Simulada* (comportamento mostrado sem o efeito real, com indicação
visível).

| # | Funcionalidade | Origem | RF/RNF | Forma no protótipo |
|---|---|---|---|---|
| 1 | Acesso pelo navegador, com login individual dos funcionários (perfil único `FUNCIONARIO`) | [DOC] A05 p.3; [DEC-37] | RF01–RF03, RNF01 | Implementada |
| 2 | Cadastro do aluno com foto, contato principal e termo conferido | [DOC] A05 p.3; [DEC-05], [DEC-09] | RF04–RF06, RF08 | Implementada |
| 3 | Geração do QR Code da carteirinha | [DOC] A05 p.3; [DEC-07] | RF07 | Implementada |
| 4 | Leitura com tipo (entrada/saída) definido automaticamente | [ATU-01]; [DEC-25] | RF09, RF13, RF14, RNF07 | Tipo automático: Implementada. Leitura do QR Code: **Simulada** (campo ou painel, sem leitor físico) |
| 5 | Confirmação da passagem pelo giro da catraca | [DEC-18], [DEC-19] | RF09, RF14, RNF07 | **Simulada** (botão) |
| 6 | Bloqueio de leitura repetida e de movimentação fora de sequência | [DOC] A10 §8.26; [DEC-02] | RF15 | Implementada |
| 7 | Registro manual excepcional de entrada e saída, com motivo | [DOC] A08 RF12; [DEC-03], [DEC-04] | RF11, RF12 | Implementada |
| 8 | Painel de presentes (total, por turma) e saídas pendentes | [DOC] A05 p.3; [DEC-04], [DEC-12] | RF16 | Implementada |
| 9 | Histórico com filtros e impressão | [DOC] A05 p.3; [DEC-12] | RF18–RF20 | Implementada |
| 10 | Notificação por e-mail ao contato principal | [ATU-02], [ATU-03] | RF21 | **Simulada** (nenhum e-mail sai) |
| 11 | ~~Consulta resumida do responsável~~ | **Fora do escopo** [DEC-37] | RF24 | Não implementar |
| 12 | ~~Conta do responsável~~ | **Fora do escopo** [DEC-37] | RF25 | Não implementar |
| 13 | Contas da equipe criadas, desativadas e reativadas por qualquer funcionário | [DEC-39] | RF26 | Implementada |
| 14 | Monitor do aluno com primeiro nome e confirmação | [DOC] A08 RF22; [DEC-06] | RF22, RF23 | Implementada, como painel na mesma tela do funcionário |
| 15 | HTTPS, backup | [DOC] A08 RNF04; A10 §8.13; [DEC-30], [DEC-31] | RNF04; T-47 | HTTPS: **Planejada**, não verificada (aplicação local). Backup: Implementada como script simples, com restauração testada |

### Fora do MVP

Lista de A05 p.3–4, com as atualizações aprovadas:

- Aplicativo para celular. O acesso é sempre pelo navegador, com layout responsivo.
- Acesso para professores.
- Notas, atividades escolares, controle financeiro e controle de estoque.
- Gestão de professores e funcionários. **Exceção:** contas de usuário do próprio
  sistema, só da equipe (RF26).
- Integração automática com sistemas externos (DRE).
- Biometria, reconhecimento facial e RFID.
- **Integração física com a catraca e o sensor de giro** [DEC-18] e **leitor USB físico** [DEC-25].
- **Envio real de e-mail** [DEC-32]. Não há mais mensagem de WhatsApp, nem simulada [DEC-37].
- **Contas e telas para pais/responsáveis** e para alunos [DEC-37].
- Relatórios e estatísticas avançados.

### Mudanças em relação à Atividade 5

| Item | Como estava em A05 p.3 | Como fica | Decisão |
|---|---|---|---|
| Notificações aos responsáveis | Fora do MVP | Dentro, **simuladas** no protótipo | ATU-02, ATU-03 |
| Acesso dos responsáveis | Fora do MVP | Continua fora: os pais só recebem o e-mail | ATU-04 → DEC-37 |
| Catracas eletrônicas | Fora do MVP | Giro **simulado**. A integração física continua fora | DEC-18 |
| Quem cadastra alunos | "Secretaria e ATEs" (A05 p.2) | Qualquer funcionário (ATE ou Gestão), como na A05 | DEC-11 → DEC-37 |

## 5. Participantes e perfis do sistema

Nem todo participante do contexto escolar tem conta no sistema. **Só os funcionários
têm conta, e todos têm o mesmo perfil** [DEC-37].

| Participante | Nomes encontrados nos documentos | Tem conta? | Perfil | Fonte |
|---|---|---|---|---|
| Auxiliar Técnico de Educação | ATE, ATEs, funcionário da portaria | **Sim** | `FUNCIONARIO` | A05 p.2; A07 p.2; A14 p.4; [DEC-37] |
| Gestão Escolar (Secretaria e Direção) | Gestão, Diretoria, Secretaria, Direção | **Sim** | `FUNCIONARIO` | A07 p.3; A08 p.3; A14 p.4; [DEC-37] |
| Pais/responsáveis | Representantes legais, Responsáveis Legais, pais e responsáveis, contato do responsável | **Não**. Só recebem o e-mail de entrada e saída | — | [DEC-37]; A10 p.2; A13 p.4 |
| Aluno | — | **Não**, apenas apresenta a carteirinha | — | A13 p.3 |
| Professores | — | **Não**, fora do MVP | — | A05 p.3 |
| DRE | Diretoria Regional de Ensino | **Não**, recebe relatórios pela Gestão | — | A02 p.2 |
| Empresas terceirizadas, comunidade | — | **Não** | — | A01 p.2 |

**Nomes padronizados:**

- No texto, usamos "**pais/responsáveis**" e "**funcionário**" (ATE ou Gestão Escolar).
- No contexto jurídico (LGPD, ECA), "**responsável legal**".
- No código e no banco, o único perfil é `FUNCIONARIO`.

### `FUNCIONARIO`: o sistema completo

ATE e Gestão Escolar são **cargos diferentes**, mas no sistema fazem as mesmas coisas
[DEC-37]. Qualquer funcionário pode:

- operar a portaria: acompanhar as leituras, conferir o aluno, cancelar uma liberação e
  fazer os registros manuais excepcionais;
- cadastrar e editar alunos, contatos e termos, e emitir carteirinhas;
- ligar ou desligar o aviso por e-mail **a pedido do responsável** (RF08, [DEC-38]);
- consultar o painel de presentes e o histórico e imprimir relatórios;
- criar, desativar e reativar contas da equipe (RF26, [DEC-39]), menos desativar a
  própria conta.

### Alunos e pais/responsáveis

- **Alunos:** só apresentam a carteirinha. Veem o monitor da portaria, sem login.
- **Pais/responsáveis:** só recebem o e-mail de entrada e saída (RF21), que não tem link
  para o sistema. Para deixar de receber, pedem à escola [DEC-38].

**Divergência registrada:** a A07 (US8, US10) e a A10 §5.15 previam perfis separados e
uma consulta dos pais. Vale esta documentação (DEC-35, DEC-37). Com perfil único, a
separação de funções fica menor: todo funcionário vê fotos e termos. O log registra quem
fez cada ação.

### Criação de contas

| Conta | Quem cria | Fonte |
|---|---|---|
| Primeiras contas (`ate.demo`, `gestao.demo`) | Carga inicial com dados fictícios no protótipo | DEC-13 |
| Novas contas da equipe | Qualquer funcionário (RF26) | DEC-39 |
| Contas de alunos e de pais/responsáveis | **Não existem** | DEC-37 |

### Alunos sem termo de consentimento [DEC-34]

Sem o termo conferido, o aluno **não é cadastrado** no Furin Access. Se o termo for
revogado depois, o aluno é inativado e a carteirinha é revogada. Em nenhum dos casos ele
deixa de estudar: o direito à educação não depende deste sistema (LGPD art. 14, §4º). A
escola controla a entrada e a saída dele pelo procedimento que já usa, e a contagem de
presentes do sistema não o inclui.

## 6. Matriz de permissões

O servidor confere todas as permissões em cada requisição (A10 §5.15; A13 p.3).
Esconder um botão na tela não é controle de acesso. Com um único perfil, a regra é:
**toda rota exige um funcionário autenticado e com conta ativa**. Sem login, o pedido
leva à tela de login. Rotas não liberadas continuam negadas por padrão (403).

**Legenda:**
- ✅ permitido.
- 🔸 permitido com restrição.
- — negado.

| Função | RF | FUNCIONARIO | Sem login (aluno, pais, visitante) |
|---|---|---|---|
| Login, logout e alteração da própria senha (fluxo A3 do RF01) | RF01–RF03 | ✅ | Só a tela de login |
| Operar a portaria (leitura, ver liberação, simular leitura e giro no protótipo) | RF09, RF10, RF13–RF15; RNF07 | ✅ | — |
| Cancelar uma liberação pendente | RN02 [DEC-28, A2] | ✅ | — |
| Registro manual de entrada ou saída | RF11, RF12 | ✅ | — |
| Cadastrar e editar aluno, contato e termo, inclusive registrar a revogação do termo [DEC-34] | RF04, RF05, RF08 | ✅ | — |
| Ligar ou desligar o aviso por e-mail, a pedido do responsável | RF08, RF21 [DEC-38] | ✅ | — |
| Emitir carteirinha ou segunda via | RF06, RF07 | ✅ | — |
| Ver foto e termo digitalizado (área privada) | RF04, RF05, RF10 | ✅ | — |
| Painel de presentes e saídas pendentes | RF16 | ✅ | — |
| Histórico com filtros | RF18, RF19 | ✅ | — |
| Imprimir relatório | RF20 | ✅ | — |
| Ver o status das notificações [DEC-28, F2] | RF19, RF21 | ✅ | — |
| Criar, desativar e reativar contas da equipe; gerar nova senha temporária | RF26 | 🔸 ¹ | — |
| Ver o monitor do aluno | RF22 | ✅ (na tela da portaria) | Só olhando o monitor, sem acesso ao sistema |

**Notas**

1. Ninguém desativa a própria conta [DEC-39].

## 7. Simulações e limites do protótipo

| Comportamento | No protótipo | Como fica indicado |
|---|---|---|
| Leitura da carteirinha | **Simulada** [DEC-25]: o funcionário digita ou cola o código no campo de leitura, ou usa o painel com as carteirinhas fictícias | Título do painel: "Simular leitura (protótipo)". Botões "Ler carteirinha de Ana Teste" |
| Envio do e-mail de notificação | **Simulado.** A notificação é gravada com status `SIMULADA` | A tela mostra "Simulação — nenhum e-mail foi enviado" |
| Giro da catraca | **Simulado** pelo botão no painel do funcionário | O rótulo do botão é "Simular passagem (giro)" |
| Monitor do aluno | Painel na mesma tela do funcionário [DEC-06] | Títulos "Painel do funcionário" e "Monitor do aluno" |
| HTTPS | **Planejado.** A aplicação roda local, com HTTP [DEC-31] | Consta como "Planejada" no RNF04 |
| Hospedagem | Local, no computador da equipe [DEC-31]. O GitHub Pages pode publicar só a documentação | — |

## 8. Glossário

| Termo | Definição |
|---|---|
| **Carteirinha** | Credencial física com QR Code. O código tem 32 caracteres hexadecimais aleatórios, sem dados pessoais (A14 p.6) |
| **Leitura** | Envio do código da carteirinha. Na implantação, vem do leitor USB, que funciona como teclado (RNF07). No protótipo, é simulada pelo campo ou pelo painel [DEC-25] |
| **Liberação** | Resultado de uma leitura válida: o aluno pode passar e o sistema aguarda o giro. Ainda não é movimentação [DEC-19] |
| **Giro** | Passagem física pela catraca, percebida pelo sensor de giro. Simulado no protótipo [DEC-18] |
| **Movimentação** | Entrada ou saída **confirmada**, que vai para o histórico |
| **Tipo** | `ENTRADA` ou `SAIDA`, sempre definido pelo servidor [ATU-01] |
| **Forma** | `LEITURA` (carteirinha) ou `MANUAL` (exceção lançada por um funcionário, com motivo) |
| **Janela anti-repetição** | Intervalo de 2 s em que uma nova leitura do mesmo código é ignorada. Termina na hora se houver giro [DEC-02] |
| **Saída pendente** | Entrada confirmada que ainda não tem saída. Só é encerrada por uma saída, inclusive manual [DEC-04] |
| **Presente** | Aluno cuja última movimentação confirmada é uma `ENTRADA` (A11 §5.12.12) |
| **Contato principal** | Contato do responsável que recebe as notificações por e-mail. Obrigatório e único por aluno [DEC-09]. Não tem conta [DEC-37] |
| **Termo de consentimento** | Documento assinado pelo responsável e conferido por um funcionário (LGPD art. 14; A14 p.7) |
| **Funcionário** | Pessoa da equipe da escola (ATE ou Gestão Escolar) com conta no sistema. Todos têm o perfil `FUNCIONARIO` [DEC-37] |
| **Monitor do aluno** | Painel voltado ao aluno. Mostra apenas o primeiro nome e a liberação ou confirmação [DEC-06] |
| **Área privada de arquivos** | Local das fotos e dos termos digitalizados. Só é acessível pelo backend, com autorização [DEC-08] |
| **Senha temporária** | Senha aleatória do primeiro acesso: exibida uma vez ao funcionário que criou a conta, válida por 72 h e com troca obrigatória [DEC-21] |
