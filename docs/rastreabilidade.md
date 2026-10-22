# Rastreabilidade

**Documentação:** Revisada em 21/10/2026 (DEC-37 a DEC-39) · **Atualizado em:** 21/10/2026

Este arquivo reúne:

- de onde vem cada requisito (fontes);
- o que foi decidido depois das entregas (ATU, DEC);
- o que foi resolvido na revisão em grupo (T-00) e o que ficou para a implantação;
- como cada RF e RNF será verificado.

## 1. Fontes analisadas

Paginação: a capa conta como p.1.

| Cód. | Arquivo | Conteúdo | Págs | Observações de leitura |
|---|---|---|---|---|
| A01 | `atividade01.pdf` | Contexto e comunidade externa | 2 | Legível. Contém dados reais da escola e da direção, que **não** vão para o repositório |
| A02 | `atividade02.pdf` | Demandas e grupos | 3 | Legível |
| A03 | `atividade03.pdf` | Planilha de hipóteses | 1 | Legível. Sem capa nem número de atividade |
| A04 | `atividade04.pdf` | Comparativo de soluções | 1 | Legível. Sem capa |
| A05 | `Atividade05.pdf` | MVP | 4 | Legível |
| A07 | `atividade07.pdf` | User stories US1–US11 | 4 | Legível |
| A08 | `atividade08.pdf` | RF01–RF23, RNF01–RNF08 | 4 | Legível |
| A09 | `atividade09.pdf` | LGPD e ECA | 3 | Legível |
| A10 | `atividade10.pdf` | ISO/IEC 27002 | 5 | Legível |
| A11 | `atividade11.pdf` | ABNT NBR 17225, WCAG 2.2 | 9 | Três trechos com problema: p.3 §5.6.3 (frase truncada), p.4 §5.9.9 ("ariainvalid … mensagemp", corrompido) e p.8 §5.13.8 (frase incompleta) |
| A13 | `atividade13.pdf` | Arquitetura | 7 | Legível. Erro de digitação na p.4 ("c conferência") |
| A14 | `atividade14.pdf` | Modelo relacional e dicionário | 9 | Legível |

**Citados, mas não recebidos:** Atividades 6 e 12; ADO 2, 3 e 4; enunciados; guia do
PI; devolutivas da professora (A14 p.9; A13 p.7).

## 2. Decisões aprovadas

### 2.1 Atualizações de escopo (ATU), 18/10/2026

| ID | Decisão | Divergência registrada |
|---|---|---|
| ATU-01 | A leitura normal identifica sozinha se é entrada ou saída. O ATE não escolhe | A07 US5, A08 RF13, A11 §5.1.13/§5.11.1/§5.11.4/§5.13.10/§5.8.6 e A14 (`tipo`) previam escolha manual. A10 §8.26 e A13 p.4 já estavam alinhados |
| ATU-02 | Notificações aos responsáveis fazem parte do MVP | A05 p.3 as deixava fora |
| ATU-03 | Notificações por e-mail, com envio que pode ser simulado no protótipo, identificado de forma clara | Alinhado com A10 p.4 e A13 p.5 |
| ATU-04 | A consulta dos responsáveis é breve, resumida e somente de leitura, só dos alunos vinculados. **Substituída pela DEC-37** (os pais não têm conta) | A05 p.3 e A09 art. 46 restringiam o acesso a funcionários. Com a DEC-37, voltam a valer |
| ATU-05 | Modelo relacional. Java, Spring Boot e MySQL | Alinhado com A08 RNF08, A13 e A14 |
| ATU-06 | Thymeleaf, HTML, CSS, JavaScript, Spring Security, Spring Mail | Alinhado com A13 p.2–3 |
| ATU-07 | GitHub e GitHub Desktop. GitHub Actions, Maven e SonarCloud, em escala proporcional ao PI | Alinhado com A13 p.5–6 |

### 2.2 Decisões do grupo (DEC), 19/10/2026

| ID | Decisão | Afeta |
|---|---|---|
| DEC-01 | O RF13 **mantém o ID** e o título é reescrito: "Determinar automaticamente o tipo de movimentação na leitura" | RF13 |
| DEC-02 | Janela de **2 s** para o mesmo código. O giro da catraca **encerra a janela na hora** | RF15, RN03 |
| DEC-03 | **O RF12 é ampliado** para entrada e saída manual | RF12 |
| DEC-04 | **Toda entrada precisa de uma saída**, inclusive manual. A saída manual exige motivo | RF12, RF15, RF16, RN06 |
| DEC-05 | **Foto obrigatória** | RF04 |
| DEC-06 | Monitor para o aluno, mostrando só a confirmação e que ele pode passar. No protótipo, **uma tela com dois painéis** | RF22, RF23 |
| DEC-07 | **QR Code** | RF07, RNF07 |
| DEC-08 | Fotos e termos na **área privada do sistema** | RF04, RF05 |
| DEC-09 | Um **contato específico** (o principal) recebe as notificações. O cadastro dele é **obrigatório**. O envio pode ser recusado | RF04, RF21 |
| DEC-10 | O **ATE cria e vincula** a conta do responsável. A senha inicial é aleatória e vai por WhatsApp. **Troca obrigatória** no primeiro acesso. Contas fictícias bastam no protótipo. **Substituída pela DEC-37** | RF01, RF24, RF25 |
| DEC-11 | A Gestão **só consulta** (vale o RF17). Só o ATE cadastra alunos. **Substituída pela DEC-37**: qualquer funcionário cadastra, e o RF17 saiu | RF04, RF17 |
| DEC-12 | A Gestão vê o painel. O ATE imprime relatórios. Continua valendo, agora para todo funcionário (DEC-37) | RF16, RF20 |
| DEC-13 | Carga inicial com as primeiras contas ATE e GESTAO. **O ATE não cria contas de Gestão.** A Gestão cria contas de Gestão. O ATE cria só contas de responsáveis. A carga inicial continua valendo. A divisão de quem cria cada conta foi **substituída pela DEC-39** | RF25, RF26 |
| DEC-14 | **Ajuste mínimo** do modelo de dados | Modelo |
| DEC-15 | Criados o **RF24** e o **RF25** a partir do RF21. **Substituída pela DEC-37**: os dois saíram do escopo, com os IDs preservados | RF21, RF24, RF25 |
| DEC-16 | Os controles de A10 e A11 mantêm os **IDs originais** e não viram novos RNF | RNF |
| DEC-17 | **Java 21, Spring Boot 4.1.x, MySQL 8.4** | RNF08 |
| DEC-18 | O giro é **simulado** no protótipo. A integração física fica para depois | RF09, RF14, RF22, RNF07 |
| DEC-19 | A movimentação **só vale depois do giro**. Sem giro no prazo, a liberação é cancelada | RF09, RF14, RN02 |
| DEC-20 | Na saída manual, gravar o **horário informado** e o **horário do lançamento** | RF12, RF19 |
| DEC-21 | Sem integração com WhatsApp. A senha é **exibida uma vez** ao ATE, que a envia manualmente. Ela **expira em 72 h** e exige troca. No banco fica só o hash. A parte do WhatsApp e do responsável foi **Substituída pela DEC-37**. A senha temporária continua para as contas da equipe (RF26) | RF25, RN14 |
| DEC-22 | A preferência de notificação começa em **"receber"**. **O próprio responsável** a altera. O padrão continua. Quem altera passou a ser o funcionário, a pedido (**DEC-38**) | RF21, RF24 |
| DEC-23 | Consulta dos **últimos 7 dias corridos**. **Substituída pela DEC-37** | RF24 |

### 2.3 Revisão em grupo (T-00), 19/10/2026

| ID | Decisão | Afeta |
|---|---|---|
| DEC-24 | Repositório do projeto: `https://github.com/LuisEduardo77/PI---Furin.git` | README; T-01 |
| DEC-25 | **O grupo não tem leitor USB de QR Code.** No protótipo, a leitura também é **simulada** pelas telas: o campo de leitura aceita digitar ou colar o código e apertar Enter, e o painel "Simular leitura (protótipo)" lista as carteirinhas fictícias com um botão por aluno. Os dois caminhos usam `POST /portaria/leituras` | RNF07; RF07, RF09, RF14; T-30, T-33 |
| DEC-26 | Versão vigente do termo de consentimento: **`v1-2026`** | RF05; `furin.termo.versao-atual` |
| DEC-27 | Texto completo da US10 (ver §7.3). A parte "verificar no celular" ficou fora do escopo (DEC-37) | US10; RF21, RF24, RF25 |
| DEC-28 | **Aprovação de todas as propostas da pauta T-00** (itens A1 a G7, listados na §3.2). As medidas que estavam "a definir" passam a ter os valores aprovados | Todos os RF e RNF; regras; arquitetura |
| DEC-29 | **Substituída pela DEC-34.** Revogação do consentimento (pauta D4, opção a): **fora do MVP**. O ATE registra só a data da revogação. O aluno continua ativo e a carteirinha continua válida. Os efeitos ficam para a implantação, com a escola | RF05, RF08; RN16; RNF05 |

### 2.4 Decisões complementares, 19/10/2026

| ID | Decisão | Afeta |
|---|---|---|
| DEC-30 | **Backup simples:** script com `mysqldump` do banco e `.zip` da pasta de fotos e termos. No protótipo, feito antes de cada demonstração e no fim de cada dia de testes; na implantação, diário. Guarda as 7 últimas cópias fora do projeto e do Git. Restauração testada uma vez. Base: LGPD art. 46 (proteção contra perda e destruição acidental) e A10 §8.13 | Arquitetura §6.1; T-47 |
| DEC-31 | **Hospedagem:** a aplicação roda local (A13 p.3). O GitHub Pages não executa Java nem MySQL; pode publicar só a documentação, de forma opcional. Não há domínio próprio | RNF04, RNF08; arquitetura §2 |
| DEC-32 | **E-mail e WhatsApp sempre simulados.** Não há envio real nem provedor SMTP. O perfil `smtp` sai do MVP. Com a DEC-37, não há mais WhatsApp, nem simulado | RF21, RF25; RNF04 |
| DEC-33 | **Guarda e descarte pelo padrão da LGPD** (art. 15 e 16): manter só enquanto necessário à finalidade e eliminar ou anonimizar depois, salvo obrigação legal da escola. A lei não fixa um prazo em anos; os prazos exatos ficam na tabela de temporalidade da escola. No protótipo, nada é descartado automaticamente | RNF05 |
| DEC-34 | **Recusa ou revogação do termo:** sem termo, o aluno não é cadastrado. A revogação inativa o aluno, revoga a carteirinha e encerra o acesso do responsável (hoje, encerra os avisos por e-mail, DEC-37). O aluno **continua estudando**, com entrada e saída controladas pela escola fora do sistema (LGPD art. 14, §4º). **Substitui a DEC-29.** Confirmada pelo grupo em 19/10/2026: "se for negado faremos do jeito sem o sistema, como está hoje" | RF05, RF08; RN12, RN16; RNF05 |

### 2.5 Fechamento, 19/10/2026

| ID | Decisão | Afeta |
|---|---|---|
| DEC-35 | **As atividades já entregues não serão reenviadas.** O prazo de entrega passou, e o material foi sendo ajustado ao longo das atividades. Onde houver divergência, **valem estes Markdown** e as decisões registradas aqui. As seções 7.3 e 7.4 ficam só como registro das diferenças | §7.3, §7.4; RF13, RF14; RNF06 |
| DEC-36 | **Prazo de guarda dos dados: 5 anos** após o fim do tratamento (saída do aluno ou revogação do termo). Logs: 5 anos a partir do evento. Depois, eliminar ou anonimizar. A tabela de temporalidade da escola prevalece se for diferente | RNF05; regras §1.2; `furin.retencao.anos` |

**Confirmação do grupo:** as decisões DEC-24 a DEC-36 foram confirmadas pelos
integrantes Adriane Helfstein de Jesus, Gabriel Eiji Shiquedoni e Hugo Rocha da Silva,
conforme informado por Luis Eduardo Belo Silva em 19/10/2026.

### 2.6 Perfil único, 21/10/2026

Pedido de Luis Eduardo Belo Silva em 21/10/2026: "o ATE e a gestão escolar têm um mesmo
perfil, são cargos diferentes, porém no sistema é a mesma coisa / ATE e gestão têm o
sistema completo / alunos — só passam a carteira / pais — só recebem o e-mail de
confirmação". As opções de detalhe foram escolhidas por ele na mesma data.

| ID | Decisão | Afeta |
|---|---|---|
| DEC-37 | **Perfil único `FUNCIONARIO`** para o ATE e a Gestão Escolar, com o **sistema completo**: portaria, cadastro, carteirinhas, registros manuais, presentes, histórico, impressão e contas da equipe. **Alunos** não têm conta: só apresentam a carteirinha. **Pais/responsáveis** não têm conta: só recebem o e-mail de entrada e saída, sem link para o sistema. Substitui ATU-04, DEC-10, DEC-11, DEC-13 (divisão de contas), DEC-15, DEC-23, a parte de WhatsApp e de responsável da DEC-21 e da DEC-32, e os itens E1, E3, E5, E8, parte do E7 e o Safari do G3 da DEC-28. **RF17, RF24 e RF25 saem do escopo**, com os IDs preservados. RN13 fica obsoleta | RF01–RF05, RF07–RF26; RNF01, RNF03–RNF07; RN11, RN13, RN14; arquitetura; escopo |
| DEC-38 | **Aviso por e-mail ligado ou desligado pelo funcionário, a pedido do responsável**, na edição do aluno (RF08, fluxo A5). Padrão "receber" (DEC-22). A mudança vai para o log como `PREFERENCIA_AVISO_ALTERADA`, com o autor | RF08, RF21; RN11; RNF05 |
| DEC-39 | **Qualquer funcionário gerencia as contas da equipe** (criar, gerar nova senha temporária, desativar e reativar), pelo RF26 "Gerenciar contas da equipe". Ninguém desativa a própria conta. As primeiras contas vêm da carga inicial | RF26; RN14; PEND-02 |

**Consequências registradas:**

- A separação de funções da A10 §5.15 diminui: todo funcionário vê fotos e termos e
  pode gerenciar contas. O log registra o autor de cada ação. Vale esta documentação
  (DEC-35).
- O `motivo_cancelamento` do cancelamento manual passa de `ATE` para `FUNCIONARIO`.
- O DDL perde os campos de conta e vínculo em `contato_responsavel`, e o `login` volta a
  VARCHAR(80).
- **Confirmação do grupo:** pendente. As decisões foram tomadas por Luis Eduardo Belo
  Silva e precisam ser repassadas aos demais integrantes.

## 3. Pendências e decisões da T-00

### 3.1 Situação das pendências

| ID | Pendência | Situação | Decisão |
|---|---|---|---|
| PEND-01 | Prazo para o giro depois da liberação | **Resolvida:** 15 s | DEC-28 (A1) |
| PEND-02 | Quem cria contas ATE depois da carga inicial | **Resolvida:** qualquer funcionário, pela tela de contas da equipe (RF26). Antes: só por carga ou script (E1) | DEC-39 (substitui DEC-28, E1) |
| PEND-03 | O ATE pode cancelar uma liberação pendente? | **Resolvida:** sim, com motivo `FUNCIONARIO` (antes `ATE`). Movimentações confirmadas não são editadas | DEC-28 (A2), DEC-37 |
| PEND-04 | Tentativas de login, bloqueio, inatividade da sessão | **Resolvida:** 5 tentativas; bloqueio de 15 min; sessão de 30 min com aviso 60 s antes | DEC-28 (E2) |
| PEND-05 | Formatos e tamanho da foto e do termo digitalizado | **Resolvida:** foto JPEG ou PNG até 2 MB; termo PDF, JPEG ou PNG até 5 MB | DEC-28 (D1, D2) |
| PEND-06 | Login do responsável | **Obsoleta:** os pais não têm conta. O `login` volta a 80 caracteres | DEC-37 (substitui DEC-28, E3) |
| PEND-07 | Periodicidade e retenção do backup | **Resolvida:** script simples, 7 últimas cópias, restauração testada | DEC-30 |
| PEND-08 | Hospedagem e provedor SMTP | **Resolvida:** aplicação local; e-mail simulado (sem WhatsApp, DEC-37) | DEC-31, DEC-32 |
| PEND-09 | Uma liberação por vez no ponto de leitura | **Resolvida:** sim | DEC-28 (A3) |
| PEND-10 | Notificar movimentações manuais | **Resolvida:** sim, indicando "registro manual" | DEC-28 (B3) |
| PEND-11 | Tamanho mínimo das senhas | **Resolvida:** temporária com 12 caracteres; nova com pelo menos 8, diferente da temporária | DEC-28 (E4) |
| PEND-12 | Tempo de exibição no monitor do aluno | **Resolvida:** 4 s | DEC-28 (A6) |
| PEND-13 | Tentativas de envio de e-mail | **Resolvida:** 3 | DEC-28 (F1) |
| PEND-14 | Cadastro de turmas | **Resolvida:** fora do MVP. As turmas vêm da carga inicial | DEC-28 (D3) |
| PEND-15 | Revogação do consentimento | **Resolvida:** o aluno sai do sistema e continua estudando | DEC-34 (substitui a DEC-29) |
| PEND-16 | Prazo de retenção e descarte dos dados | **Resolvida:** regra da LGPD (art. 15 e 16) com prazo de 5 anos | DEC-33, DEC-36 |

**Todas as pendências estão resolvidas.** O prazo de guarda é de 5 anos (DEC-36); se a
escola tiver uma tabela de temporalidade diferente, vale a dela.

### 3.2 Itens da pauta aprovados (DEC-28)

| Item | Decisão aprovada | Onde |
|---|---|---|
| A1 | Prazo do giro: 15 s | RN02; RF09, RF14 |
| A2 | O ATE cancela uma liberação pendente, com motivo `ATE` (hoje, qualquer funcionário, com motivo `FUNCIONARIO`, DEC-37) | RN02; RF09, RF10, RF13 |
| A3 | Uma liberação por vez no ponto de leitura | RN05; RF15 |
| A4 | Leitura repetida com liberação pendente não cria outra nem renova o prazo | RN04; RF15 |
| A5 | Leitura de aluno com saída pendente de dia anterior é recusada | RN01, RN06; RF13, RF23 |
| A6 | Mensagem do monitor do aluno: 4 s | RN17; RF22, RF23 |
| A7 | Painel do ATE limpa na próxima leitura ou após 60 s sem leitura | RN17; RF10 |
| A8 | Aviso "Leitor não está pronto" quando o foco sai do campo | RNF07 |
| A9 | Foto que não carrega não bloqueia a liberação | RF10; RNF02 |
| A10 | Limite de 2 s também para a confirmação do giro | RNF02 |
| A11 | Prazo do giro tratado como limite essencial (A11 §5.16.2) | RNF06 |
| A12 | Só os 4 primeiros caracteres do código no log | RF23 |
| B1 | Tipo do registro manual calculado pelo servidor | RN07; RF12 |
| B2 | Registro manual nasce `CONFIRMADA`, sem giro | RN07; RF12 |
| B3 | Movimentação manual também gera notificação | RN11; RF12, RF21 |
| C1 | "Presentes agora" conta só entradas de hoje. Pendências de dias anteriores ficam separadas | RN12; RF16 |
| C2 | Tela Presentes atualiza a cada 30 s, mais o botão "Atualizar" | RF16 |
| C3 | Turmas com zero aparecem; sem anúncio automático no leitor de tela | RF16 |
| C4 | Busca: 20 resultados, mínimo de 2 caracteres, matrícula por prefixo, portaria só com ativos | RF11 |
| C5 | Histórico: 50 por página, até 31 dias, padrão "hoje", ordem decrescente, HH:MM, autor só nos manuais | RF18, RF19 |
| C6 | Relatório: até 1.000 registros, rodapé "uso restrito", impressão registrada no log | RF20 |
| C7 | Nome da escola configurável (`furin.escola.nome`), com valor fictício no repositório | RF20 |
| D1 | Foto: JPEG ou PNG, até 2 MB | RF04, RF08 |
| D2 | Termo: PDF, JPEG ou PNG, até 5 MB | RF05 |
| D3 | Turmas pela carga inicial | RF04; RN16 |
| D4 | Revogação: ver DEC-34 (substituiu a DEC-29) | RF05 |
| D5 | Signatário do termo pode ser diferente do contato principal | RF05 |
| D6 | Reaproveitar o contato do irmão no cadastro | RF04 |
| D7 | Colisão do código: 3 novas tentativas | RF06; RN15 |
| D8 | Carteirinha 85,6 × 54 mm, com nome, turma e QR Code, sem foto e sem o código em texto | RF07 |
| D9 | QR Code nível M, lado de pelo menos 2,5 cm, sem validade no MVP | RF07 |
| D10 | Matrícula editável com unicidade; inativação só sem saída pendente; remoção da foto antiga; vale a última gravação | RF08 |
| D11 | Troca do contato principal mantém o consentimento e tira o acesso da conta antiga (hoje, só encerra os avisos ao contato antigo, DEC-37) | RF08 |
| E1 | Contas ATE novas só por carga ou script. **Substituído pela DEC-39** | RF25, RF26 |
| E2 | 5 tentativas, bloqueio de 15 min, sessão de 30 min com aviso 60 s antes | RF01, RF02 |
| E3 | Login do responsável = e-mail do contato principal. **Substituído pela DEC-37** | RF25 |
| E4 | Senha temporária com 12 caracteres; nova com pelo menos 8 | RN14; RF01, RF26 |
| E5 | Regra da senha temporária vale também para as contas GESTAO. Hoje vale para todas as contas, que são da equipe (DEC-37) | RN14; RF26 |
| E6 | Login da Gestão em texto livre único (`gestao.nome`). Hoje vale para todo funcionário | RF26 |
| E7 | Conta só para o contato principal; vínculo removível; página da senha sem cache. A parte da conta do responsável foi **substituída pela DEC-37**. A página sem cache continua | RF25, RF26 |
| E8 | Consulta do responsável com nome completo, sem motivo, com "Saída não registrada desde DD/MM". **Substituído pela DEC-37** | RF24 |
| F1 | 3 tentativas de envio | RN11; RF21 |
| F2 | ATE e Gestão veem o status das notificações (hoje, todo funcionário) | RF21 |
| F3 | Aviso de notificação simulada na portaria | RF21 |
| G1 | Flyway, Spring Data JPA, Bean Validation e ZXing | Arquitetura §1; RNF08 |
| G2 | DDL, coluna gerada, carga fictícia, rotas e pipeline como estão na arquitetura | Arquitetura §3 a §7 |
| G3 | Duas últimas versões estáveis de Chrome, Edge e Firefox; Safari no iOS para o RF24 (**obsoleto**, DEC-37); só a portaria exige JavaScript | RNF01 |
| G4 | Medição dos 2 s com 800 alunos fictícios, 30 dias de dados e 30 leituras | RNF02 |
| G5 | bcrypt com custo padrão; TLS 1.2 ou superior; HTTPS autoassinado opcional na demonstração | RNF03, RNF04 |
| G6 | NVDA e axe DevTools ou Lighthouse | RNF06 |
| G7 | Backup, hospedagem/SMTP e retenção ficariam para a implantação. **Substituído** pelas DEC-30 a DEC-33 | PEND-07, 08, 16 |

### 3.3 O que continua marcado como [PROP]

Depois da T-00, a marca **[PROP]** só aparece em **sugestões de detalhe** que não
precisaram de decisão do grupo: textos de mensagens e menus, nomes de propriedades e
detalhes de implementação (por exemplo, onde guardar o contador de tentativas de
login). Eles podem ser ajustados na implementação, registrando a mudança no pull
request, desde que os critérios de aceitação e de acessibilidade continuem atendidos.

## 4. Matriz de rastreabilidade: requisitos funcionais

Siglas de **verificação**:

- **UNI:** teste de unidade.
- **INT:** teste de integração com MySQL.
- **MAN:** verificação manual registrada.
- **DEM:** demonstração no protótipo.

| RF | Título (igual ao do arquivo) | Fonte original | US (A07) | Decisões | Arquivo | Depende de | RN | Verificação |
|---|---|---|---|---|---|---|---|---|
| RF01 | Autenticar o usuário com usuário e senha individuais | A08 p.2 | US1, US8 | DEC-21, DEC-28, DEC-37 | [RF01](../requirements/rf/RF01.md) | RNF03, RNF04, RNF06 | RN14 | INT, MAN (A11), DEM |
| RF02 | Bloquear o acesso com credenciais inválidas e exibir mensagem de erro | A08 p.2 | US1, US8 | — | [RF02](../requirements/rf/RF02.md) | RF01 | — | INT |
| RF03 | Direcionar cada usuário às telas do seu perfil | A08 p.2 | US1, US8 | DEC-37 | [RF03](../requirements/rf/RF03.md) | RF01 | — | INT, DEM |
| RF04 | Cadastrar aluno com nome, matrícula, turma, foto e contato principal | A08 p.2 | US2 | DEC-05, DEC-08, DEC-09, DEC-37 | [RF04](../requirements/rf/RF04.md) | RF01, RF03, RF05, RF07; RNF05, RNF06 | RN16 | INT, MAN (A11), DEM |
| RF05 | Exigir no cadastro a conferência do termo de consentimento assinado | A08 p.2; A09 p.2 | US2 | DEC-08, DEC-26, DEC-28, DEC-34 | [RF05](../requirements/rf/RF05.md) | RF04 | RN16 | INT |
| RF06 | Impedir cadastro com matrícula ou código de carteirinha duplicados | A08 p.2 | US2 | — | [RF06](../requirements/rf/RF06.md) | RF04, RF07 | RN15 | INT |
| RF07 | Gerar o QR Code do aluno para impressão da carteirinha | A08 p.2 | US2 | DEC-07, DEC-25, DEC-28 | [RF07](../requirements/rf/RF07.md) | RF04; RNF07 | RN15 | UNI, MAN, DEM |
| RF08 | Editar os dados cadastrais do aluno | A08 p.2 | US2 | DEC-28, DEC-34, DEC-38 | [RF08](../requirements/rf/RF08.md) | RF04 | — | INT, MAN (A11) |
| RF09 | Registrar a entrada automaticamente após leitura e giro | A08 p.2 | US3, **US11** | ATU-01, DEC-18, DEC-19, DEC-25, DEC-28 | [RF09](../requirements/rf/RF09.md) | RF07, RF10, RF13, RF15, RF22; RNF02, RNF07 | RN01–RN03, RN08, RN09 | INT, MAN (tempo), DEM |
| RF10 | Exibir ao ATE a foto, o nome e a turma do aluno após a leitura | A08 p.2 | US3 | — | [RF10](../requirements/rf/RF10.md) | RF09 | RN17 | INT, MAN (A11) |
| RF11 | Buscar aluno por nome ou matrícula | A08 p.2 | US4 | — | [RF11](../requirements/rf/RF11.md) | RF04 | — | INT |
| RF12 | Registrar entrada ou saída manual excepcional, com motivo | A08 p.3 | US4 | DEC-03, DEC-04, DEC-20, DEC-28 | [RF12](../requirements/rf/RF12.md) | RF11, RF13 | RN01, RN06, RN07 | UNI, INT, DEM |
| RF13 | Determinar automaticamente o tipo de movimentação na leitura | A08 p.3 | US5, US11 | ATU-01, DEC-01 | [RF13](../requirements/rf/RF13.md) | RF09, RF14 | RN01, RN06 | UNI, INT |
| RF14 | Registrar a saída automaticamente após leitura e giro | A08 p.3 | US5, **US11** | ATU-01, DEC-18, DEC-19, DEC-25, DEC-28 | [RF14](../requirements/rf/RF14.md) | Iguais às do RF09 | RN01–RN03, RN08, RN09 | INT, DEM |
| RF15 | Impedir movimentações repetidas ou fora de sequência | A08 p.3 | US5 | DEC-02, DEC-04 | [RF15](../requirements/rf/RF15.md) | RF09, RF14 | RN03–RN05, RN08 | UNI, INT, DEM |
| RF16 | Exibir o total de presentes, o total por turma e as saídas pendentes | A08 p.3 | US6 | DEC-04, DEC-12, DEC-28 | [RF16](../requirements/rf/RF16.md) | RF09, RF12, RF14 | RN06, RN12 | INT, DEM |
| RF17 | Restringir o perfil de gestão a consultas, sem registro de entrada ou saída *(fora do escopo)* | A08 p.3 | US8 | DEC-11, **DEC-37** | [RF17](../requirements/rf/RF17.md) | — | — | Nenhuma rota implementa a restrição |
| RF18 | Consultar o histórico de entradas e saídas com filtro por período, turma ou aluno | A08 p.3 | US7, US9 | — | [RF18](../requirements/rf/RF18.md) | RF09, RF12, RF14 | RN10 | INT, DEM |
| RF19 | Exibir no histórico: aluno, turma, tipo, data, hora e forma de registro | A08 p.3 | US7, US9 | DEC-20, DEC-28 | [RF19](../requirements/rf/RF19.md) | RF18 | RN07 | INT, MAN (A11) |
| RF20 | Permitir a impressão do relatório pelo navegador | A08 p.3 | US7, US9 | DEC-12 | [RF20](../requirements/rf/RF20.md) | RF18 | — | MAN, DEM |
| RF21 | Notificar o contato principal sobre a entrada e a saída do aluno | A08 p.4 | US10 | ATU-02, ATU-03, DEC-09, DEC-22, DEC-28, DEC-32, DEC-37, DEC-38 | [RF21](../requirements/rf/RF21.md) | RF04, RF08, RF09, RF12, RF14 | RN11 | INT, DEM (simulada) |
| RF22 | Exibir no monitor do aluno o primeiro nome e a liberação ou confirmação | A08 p.4 | US11 | DEC-06, DEC-19 | [RF22](../requirements/rf/RF22.md) | RF09, RF14 | RN17 | MAN (A11), DEM |
| RF23 | Exibir aviso quando a leitura falhar, orientando a tentar de novo ou procurar o ATE | A08 p.4 | US11 | — | [RF23](../requirements/rf/RF23.md) | RF09 | RN09, RN17 | INT, MAN, DEM |
| RF24 | Consulta resumida do responsável *(novo, fora do escopo)* | **Novo** (de A08 RF21) | US10 | DEC-15, **DEC-37** | [RF24](../requirements/rf/RF24.md) | — | — | Nenhuma rota implementa a consulta |
| RF25 | Criar e vincular a conta do responsável *(novo, fora do escopo)* | **Novo** (de A08 RF21) | US10 | DEC-15, **DEC-37** | [RF25](../requirements/rf/RF25.md) | — | — | Nenhuma rota implementa a conta |
| RF26 | Gerenciar contas da equipe | **Novo** | US8 | DEC-28, DEC-37, **DEC-39** | [RF26](../requirements/rf/RF26.md) | RF01, RF03, RF19 | RN14 | INT, MAN, DEM |

## 5. Matriz de rastreabilidade: requisitos não funcionais

| RNF | Título (igual ao do arquivo) | Categoria | Fonte original | Decisões | Arquivo | RF afetados | Verificação | Meta no protótipo (hoje todos estão `Planejada`) |
|---|---|---|---|---|---|---|---|---|
| RNF01 | Acesso por navegador, com layout responsivo | Plataforma | A08 p.4 (origem: A05) | — | [RNF01](../requirements/rnf/RNF01.md) | Todos | MAN (navegadores, 320 px) | Implementada |
| RNF02 | Confirmação da leitura na tela em até 2 segundos | Desempenho | A08 p.4 (origem: US3) | DEC-19 | [RNF02](../requirements/rnf/RNF02.md) | RF09, RF14 | MAN (medição registrada) | Implementada |
| RNF03 | Senhas armazenadas com hash, nunca em texto plano | Segurança | A08 p.4 (origem: A09, A10) | DEC-21, DEC-37 | [RNF03](../requirements/rnf/RNF03.md) | RF01, RF26 | INT, inspeção do banco | Implementada |
| RNF04 | Comunicação entre navegador e servidor via HTTPS | Segurança | A08 p.4 (origem: A10) | DEC-28, DEC-31, DEC-32 | [RNF04](../requirements/rnf/RNF04.md) | Todos | Não verificado no protótipo (aplicação local, DEC-31). Cookies e cabeçalhos: INT | **Planejada**, não verificada |
| RNF05 | Coleta e exposição mínima de dados pessoais | Privacidade | A08 p.4 (origem: A09) | DEC-09, DEC-33, DEC-34, DEC-37, DEC-38 | [RNF05](../requirements/rnf/RNF05.md) | RF04, RF05, RF08, RF21 | Inspeção do modelo e das telas | Implementada |
| RNF06 | Acessibilidade digital | Acessibilidade | A08 p.4 (origem: A11) | DEC-16 | [RNF06](../requirements/rnf/RNF06.md) | Todas as telas | MAN (A11 p.9) | Implementada |
| RNF07 | Compatibilidade com leitor USB de QR Code que funciona como teclado | Hardware | A08 p.4 (origem: A03) | DEC-07, DEC-18, DEC-25, DEC-28 | [RNF07](../requirements/rnf/RNF07.md) | RF07, RF09, RF14 | UNI, INT, MAN, DEM | **Simulada**: leitura e giro pelas telas (DEC-25) |
| RNF08 | Backend em Java (Spring Boot) e banco de dados MySQL | Implementação | A08 p.4 (origem: A03) | ATU-05, DEC-17, DEC-28, DEC-31 | [RNF08](../requirements/rnf/RNF08.md) | Todos | Build na CI | Implementada |

## 6. Requisitos legais e normativos vinculados

| Item | Fonte | RF/RNF |
|---|---|---|
| LGPD art. 14: consentimento | A09 p.2 | RF05 |
| LGPD art. 14, §4º: não condicionar a participação da criança | DEC-34 | RF05 |
| LGPD art. 15 e 16: término do tratamento e eliminação | DEC-33 | RNF05 |
| LGPD art. 46: segurança | A09 p.2 | RF01–RF03, RNF03; backup (DEC-30) |
| LGPD art. 6º, III: minimização | A09 p.2 | RF04, RF21, RNF05 |
| ECA art. 53, parágrafo único: informação aos pais | A09 p.3 | RF18–RF21 |
| ECA art. 17: imagem e identidade | A09 p.3 | RF10, RF22, RF23 |
| ISO/IEC 27002 §5.15 Controle de acesso | A10 p.2 | RF03, RF26 (perfil único, DEC-37) |
| §5.16 Gestão de identidade | A10 p.2 | RF26 |
| §5.17 Informações de autenticação | A10 p.2 | RF01, RF26, RNF03 |
| §5.34 Privacidade | A10 p.2 | RF04, RF05, RF21, RNF05 |
| §8.5 Autenticação segura | A10 p.3 | RF01, RF02 |
| §8.13 Backup | A10 p.3 | Script simples (DEC-30) |
| §8.15 Log | A10 p.3 | Todos |
| §8.24 Criptografia | A10 p.3 | RNF04, RF21 |
| §8.26 Segurança da aplicação | A10 p.3 | RF13, RF15, RF21 |
| §8.28 Codificação segura | A10 p.3 | Todos |
| §8.29 Testes de segurança | A10 p.4 | RF02, RF03, RF15 |
| §8.33 Informações de teste | A10 p.4 | Carga fictícia; RF21 |
| ABNT NBR 17225 §5.1.1, §5.1.4, §5.1.13 | A11 p.2 | RNF06; RF01, RF04, RF08, RF11, RF12, RF18, RF20 |
| §5.3.1, §5.6.3, §5.7.12, §5.7.15, §5.13.1, §5.13.2 | A11 p.3 | RNF06; RF03, RF18, RF19 |
| §5.9.3, §5.9.7, §5.9.9, §5.9.10 | A11 p.4 | RNF06; RF02, RF04–RF06, RF08 |
| §5.9.12, §5.9.15, §5.9.18, §5.16.2 | A11 p.5 | RNF06; RF01, RF08, RF12 |
| §5.1.3, §5.11.1, §5.11.3, §5.11.4 | A11 p.6 | RNF06; RF13, RF19, RF22, RF23 |
| §5.8.6, §5.10.3, §5.10.4, §5.12.1–§5.12.4, §5.12.7, §5.13.5 | A11 p.7 | RNF01, RNF06 |
| §5.2.1, §5.12.12, §5.13.7, §5.13.8, §5.13.10 | A11 p.8 | RNF06; RF10, RF16, RF22, RF23 |

## 7. Alterações aprovadas em relação às entregas anteriores

**Nenhum ID foi removido nem reaproveitado.** RF17, RF24 e RF25 continuam com arquivo
próprio, marcados como **fora do escopo** (DEC-37). RN13 fica obsoleta.

### 7.1 Requisitos funcionais (A08)

| ID | Tipo | Antes | Depois | Decisão |
|---|---|---|---|---|
| RF01 | Ampliado | Usuário e senha individuais | Perfil único `FUNCIONARIO`, troca obrigatória da senha temporária e alteração da própria senha | DEC-21, DEC-28, DEC-37 |
| RF03 | Simplificado | Perfis ATE ou Gestão | Perfil único: mesma tela inicial e mesmo menu para todos | DEC-37 |
| RF04 | Ampliado | Nome, matrícula, turma e foto | Foto obrigatória e contato principal obrigatório. Qualquer funcionário cadastra | DEC-05, DEC-09, DEC-37 |
| RF05 | Detalhado | Confirmação do termo | Evidência com versão (`v1-2026`), data, referência e conferente (A14 p.7). Revogação tira o aluno do sistema | DEC-26, DEC-34 |
| RF07 | Detalhado | QR Code/código de barras | QR Code. Leitura simulada no protótipo | DEC-07, DEC-25 |
| RF08 | Detalhado | Edição do aluno | Matrícula editável, inativação, troca do contato principal, registro da revogação e aviso por e-mail ligado ou desligado a pedido | DEC-28, DEC-34, DEC-38 |
| RF09 | Ampliado; US corrigida | Registro na leitura. US3, US10 | Liberação na leitura e confirmação após o giro. US3, **US11** | ATU-01, DEC-18, DEC-19 |
| RF12 | **Ampliado** | Entrada manual | Entrada ou saída manual, com motivo, horário informado e horário do lançamento | DEC-03, DEC-04, DEC-20 |
| RF13 | **Título reescrito, mesmo ID** | "Alternar o modo de leitura entre Entrada e Saída" | "Determinar automaticamente o tipo de movimentação na leitura" | ATU-01, DEC-01 |
| RF14 | Ampliado; US corrigida | US5, US10 | Liberação e giro. US5, **US11** | ATU-01, DEC-19 |
| RF15 | **Generalizado** | "Impedir duas saídas seguidas do mesmo aluno" | "Impedir movimentações repetidas ou fora de sequência" | DEC-02, DEC-04 |
| RF16 | Ampliado | Presentes, total e por turma | Inclui as saídas pendentes. Toda a equipe vê e registra a saída manual | DEC-04, DEC-12, DEC-37 |
| RF19 | Detalhado | — | Motivo e horário do lançamento no registro manual. Coluna "Aviso ao responsável" | DEC-20, DEC-28 |
| RF20 | Ampliado | Grupo Gestão | Todo funcionário | DEC-12, DEC-37 |
| RF21 | Reescrito | Notificação e "local de verificação" | Só a notificação ao contato principal, sem link e com opção de não receber (alterada pelo funcionário). A verificação no sistema saiu do escopo | ATU-02, ATU-03, DEC-09, DEC-22, DEC-37, DEC-38 |
| RF22 | Ampliado | "Entrada registrada"/"Saída registrada" | Liberação ("pode passar") e confirmação após o giro, só com o primeiro nome | DEC-06, DEC-19 |
| RF17 | **Fora do escopo** | Restrição da Gestão a consultas | Sem efeito: perfil único | DEC-37 |
| RF24 | **Novo, depois fora do escopo** | — | Consulta resumida do responsável (não implementar) | DEC-15, DEC-37 |
| RF25 | **Novo, depois fora do escopo** | — | Conta e vínculo do responsável (não implementar) | DEC-15, DEC-37 |
| RF26 | **Novo**, título reescrito | — | "Gerenciar contas da equipe" (antes: contas do perfil Gestão) | DEC-13, DEC-39 |

Sem mudança de escopo (só valores definidos na T-00 e o perfil único): RF02, RF06, RF10, RF11, RF18 e RF23.

### 7.2 Requisitos não funcionais (A08)

| ID | Alteração | Decisão |
|---|---|---|
| RNF02 | Os 2 s valem até a resposta de liberação | DEC-19 |
| RNF04 | Marcado como **planejado** no protótipo local | A13 p.3 |
| RNF05 | O contato inclui telefone e e-mail do contato principal. Não há conta de responsável (DEC-37) | DEC-09, DEC-37 |
| RNF07 | QR Code. Catraca e sensor de giro simulados. Sem leitor físico no protótipo: leitura simulada pelo campo ou pelo painel | DEC-07, DEC-18, DEC-25 |
| RNF08 | Versões Java 21, Spring Boot 4.1.x, MySQL 8.4 | DEC-17 |

### 7.3 User stories (A07): diferenças registradas

As atividades não serão reenviadas (DEC-35). Esta tabela só registra onde estes
Markdown diferem da A07. **Para o desenvolvimento, valem os Markdown.**

| US | Diferença em relação à A07 |
|---|---|
| US2 | Incluir foto e contato principal entre os campos obrigatórios |
| US4 | Padronizar o nome "US04" para "US4" e incluir a saída manual |
| US5 | Retirar "alternar o sistema para o modo Saída" |
| US10 | Usar o texto completo (DEC-27): "Como representante legal, quero receber notificação por e-mail e verificar no celular quando meu filho chegar e sair da escola, para que eu tenha a confirmação de que ele chegou e saiu com segurança, sem precisar entrar em contato com a secretaria." Relacionar a RF21. A parte "verificar no celular" ficou fora do escopo (RF24 e RF25, DEC-37) |
| US11 | Passa a ser citada por RF09, RF14, RF22 e RF23 |

### 7.4 Diferenças registradas nos documentos entregues

As atividades não serão reenviadas (DEC-35). Esta tabela só registra as diferenças.
**Para o desenvolvimento, valem os Markdown.**

| Documento | Diferença em relação a estes Markdown | Origem |
|---|---|---|
| A03 p.1 | A solução escolhida é a H2 com leitura e giro simulados no protótipo. Hoje "ESCOLHIDO" está na H3 | DEC-18, DEC-25 |
| A04 p.1 | Atualizar a linha "Furin LTDA" | DEC-18 |
| A05 p.2–3 | Incluir notificações (simuladas) e giro simulado. A consulta dos responsáveis continua fora, e o cadastro por "Secretaria e ATEs" volta a valer | ATU-02, DEC-18, DEC-37 |
| A07 | Ver §7.3 | — |
| A08 | Ver §7.1 e §7.2 | — |
| A09 p.2 | Art. 14: evidência conforme A14. (A inclusão do perfil RESPONSAVEL no art. 46 deixou de ser necessária, DEC-37) | A14 |
| A10 p.2 §5.34 | Trocar "não necessariamente no nosso sistema" por "na área privada do sistema" | DEC-08 |
| A11 | Retirar "alternar Entrada/Saída" e "Modo: Entrada/Saída". Em §5.11.1 e §5.12.12, trocar RF21 por RF22/RF23. Corrigir os trechos corrompidos | ATU-01, DEC-01 |
| A13 | Fixar QR Code, versões e giro simulado. Corrigir "c conferência" | DEC-07, DEC-17, DEC-18 |
| A14 | Aplicar o ajuste mínimo de [`arquitetura-e-dados.md`](arquitetura-e-dados.md) §5.2, com o perfil único `FUNCIONARIO` | DEC-14, DEC-37 |
| A07 (US8, US10), A10 §5.15 | Previam perfis separados para ATE e Gestão e a consulta dos pais | DEC-37 |

## 8. Histórico deste conjunto de documentos

| Data | Versão | Alteração |
|---|---|---|
| 18/10/2026 | 0.1 | Análise das atividades, divergências e questões para o grupo |
| 19/10/2026 | 0.2 | Decisões DEC-01 a DEC-23 incorporadas. Primeiro pacote em `docs/00` a `docs/12` (**substituído**) |
| 19/10/2026 | 0.3 | Reorganização na estrutura atual (`docs/` + `requirements/`), produzida em lotes. Medidas ausentes marcadas como "a definir" |
| 19/10/2026 | 0.4 | Os 26 RF e os 8 RNF foram escritos. Entraram PEND-14 a PEND-16 e a PEND-05 ampliada. Foram listadas as medidas a definir e as propostas surgidas nos RF e RNF |
| 19/10/2026 | 0.5 | Revisão em grupo (T-00): DEC-24 a DEC-29. PEND-01 a 06 e 09 a 15 resolvidas. PEND-07, 08 e 16 ficam para a implantação. Todos os arquivos passam a `Revisada` |
| 19/10/2026 | 0.6 | Decisões complementares DEC-30 a DEC-34. PEND-07, 08, 15 e 16 resolvidas. A DEC-29 foi substituída pela DEC-34 |
| 19/10/2026 | 0.7 | Revisão de consistência em 10 itens (P10). Títulos da matriz iguais aos dos arquivos, decisões atualizadas na matriz, RF08 incluído nas alterações e verificação do RNF07 corrigida |
| 19/10/2026 | 0.8 | DEC-35 (atividades não serão reenviadas; valem os Markdown) e DEC-36 (guarda por 5 anos). Decisões confirmadas pelo grupo |
| 21/10/2026 | 0.9 | DEC-37 a DEC-39: perfil único `FUNCIONARIO`, aviso alterado pelo funcionário a pedido, contas da equipe por qualquer funcionário. RF17, RF24 e RF25 fora do escopo (IDs preservados). RF26 com título novo. RN13 obsoleta. PEND-02 e PEND-06 atualizadas. Confirmação do grupo pendente |
