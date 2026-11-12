# Roteiro da demonstração final (T-46)

| Campo | Valor |
|---|---|
| **Tarefa** | T-46 — roteiro e evidências da demonstração |
| **Criado em** | 12/11/2026 |
| **Situação** | **Escrito, ainda não ensaiado.** O ensaio é feito pelo grupo e registrado na seção 8 |
| **Duração prevista** | 25 a 30 minutos (a versão de 5 minutos está no [README](../../README.md), seção 5) |

Este roteiro mostra, na ordem, todas as funções do protótipo. Cada passo traz o que fazer,
o resultado esperado e os critérios de aceitação que ele demonstra. A coluna "Demonstra"
cita a linha **DEM** do RF (a demonstração prevista no próprio requisito) e os `CA-` que
ficam visíveis na tela.

**Só dados fictícios** (A10 §8.33): as contas `ate.demo` e `gestao.demo`, os alunos da
carga (Ana Teste, Davi Teste e Bia Exemplo), os responsáveis Carla Teste e Bruno Exemplo
e os dados inventados abaixo (Caio Teste, Rita Teste, Paula Exemplo). Nenhuma foto de
pessoa real: use uma imagem qualquer (um desenho, um quadrado colorido) em JPEG ou PNG.

**Simulações visíveis** (DEC-18, DEC-25, DEC-32): leitura pelo painel "Simular leitura
(protótipo)", passagem pelo botão "Simular passagem (giro)" e e-mail com "Simulação —
nenhum e-mail foi enviado". Fale isso em voz alta no começo: o grupo não tem leitor,
catraca nem servidor de e-mail.

## 1. Preparação (antes da banca)

| # | O que fazer | Resultado esperado |
|---|---|---|
| 0.1 | Faça um backup (`scripts/backup.ps1` ou `backup.sh`, DEC-30) e recomece o banco do protótipo (README §3.6) | Banco `furin` vazio, sem movimentações. A carga fictícia roda na próxima partida |
| 0.2 | Confira no `application-local.yml` as senhas de `ate.demo` e `gestao.demo` (pelo menos 8 caracteres). **Não mostre o arquivo na tela** | — |
| 0.3 | Suba o sistema no perfil `prototipo` (README §3.4) | Aplicação em <http://localhost:8080> |
| 0.4 | Abra duas janelas do navegador: uma normal e uma anônima (para a conta nova da Parte 5) | — |
| 0.5 | Deixe pronta uma imagem fictícia (JPEG ou PNG, até 2 MB) para a foto do Caio Teste | — |
| 0.6 | Se possível, grave a tela durante a demonstração | A gravação é a evidência pedida no RF09 e no RF14 |

Nos passos, **ontem** e **hoje** são as datas do dia da demonstração.

## 2. Parte 1 — Acesso (3 min)

| # | O que fazer | Resultado esperado | Demonstra |
|---|---|---|---|
| 1.1 | Sem entrar, digite <http://localhost:8080/portaria> na barra de endereço | O sistema abre a tela "Login \| Furin Access" | RF03 DEM; CA-RF03-03 (sem login vai para o login) |
| 1.2 | Entre com `ate.demo` e uma senha errada | "Usuário ou senha inválidos. Confira os dados e tente novamente." O usuário continua preenchido e a senha fica vazia | RF02 DEM; CA-RF02-01, CA-RF02-07 (parte visual) |
| 1.3 | Repita **5 vezes** com um login que não existe, `visitante.teste`, e qualquer senha. Na sexta tentativa, use a mesma conta | Da 1ª à 5ª: a mesma mensagem do passo 1.2. Na 6ª: "Muitas tentativas sem sucesso. Aguarde alguns minutos e tente novamente." **Use um login inexistente**, para não bloquear `ate.demo` por 15 minutos | RF02 DEM (bloqueio); CA-RF02-03, CA-RF02-08 |
| 1.4 | Entre com ` GESTAO.Demo ` (maiúsculas e espaços) e a senha certa | Abre a Portaria. Menu: Portaria · Alunos · Presentes · Histórico · Contas da equipe · Alterar senha · Sair | RF03 DEM; CA-RF01-01, CA-RF01-02, CA-RF03-01, CA-RF03-02 |
| 1.5 | Clique em **Sair** e entre com `ate.demo` | "você saiu do sistema." Depois, a mesma Portaria e **o mesmo menu**: os dois cargos usam tudo (DEC-37) | RF03 DEM; CA-RF03-01, CA-RF03-08 (não há tela para alunos nem pais) |

## 3. Parte 2 — Portaria (10 min)

Todos os passos com `ate.demo`.

### 3.1 Saída pendente de ontem e registro manual

| # | O que fazer | Resultado esperado | Demonstra |
|---|---|---|---|
| 2.1 | Na Portaria, clique em **Registro manual**. Busque `000124` e depois `ana` | `000124` encontra Davi Teste; `ana` encontra Ana Teste. Só alunos ativos, sem foto nem responsável, com a quantidade de resultados anunciada | RF11 DEM; CA-RF11-01, CA-RF11-02, CA-RF11-05 |
| 2.2 | Clique em **Registrar manualmente para Ana Teste**. Confira o tipo (**Entrada**, calculado pelo sistema). Em **Data e hora reais da movimentação**, ponha **ontem às 07:00**. Deixe **Motivo** vazio e clique em **Revisar** | Erro no campo Motivo, nada gravado | CA-RF12-03 |
| 2.3 | Escreva o motivo "Preparação da demonstração: entrada de ontem sem saída", clique em **Revisar** e em **Confirmar** | Resumo com aluno, tipo, horário e motivo antes de gravar. Depois, "Entrada manual registrada para Ana às …" e o aviso simulado para Carla | CA-RF12-07, CA-RF13-01 |
| 2.4 | Volte à **Portaria** e clique em **Ler carteirinha de Ana Teste** (painel "Simular leitura (protótipo)") | Painel do funcionário: "Leitura recusada: Saída pendente de dd/mm: registre a saída manual antes da nova entrada." Monitor do aluno: "Não foi possível ler sua carteirinha. Tente de novo ou procure o funcionário da portaria." | CA-RF13-04, CA-RF23-01, CA-RF23-02 |
| 2.5 | Abra **Presentes** | "Presentes agora" = 0. Ana aparece em **Saídas pendentes**, com o botão **Registrar saída de Ana Teste**. A legenda e o aviso "Este número não comprova presença em sala de aula." | RF16 DEM (pendência); CA-RF16-03, CA-RF16-08 |
| 2.6 | Clique em **Registrar saída de Ana Teste**. O tipo vem como **Saída**. Ponha **ontem às 12:30**, motivo "Saída não registrada ontem", **Revisar** e **Confirmar** | Saída manual registrada com o horário de ontem. De volta a **Presentes**, Ana saiu da lista de pendências | RF12 DEM (saída pendente); CA-RF12-02, CA-RF16-04 |

### 3.2 Leitura simulada pelo painel, repetição e giro

| # | O que fazer | Resultado esperado | Demonstra |
|---|---|---|---|
| 2.7 | Na **Portaria**, clique em **Ler carteirinha de Ana Teste** | Painel do funcionário: foto, "Ana Teste", "6A — Manhã", tipo **Entrada** e "Entrada liberada — aguardando passagem", em texto. Monitor do aluno, ao lado: só "Ana, entrada liberada. Pode passar.", sem foto, sobrenome nem turma. Não há seletor de entrada/saída na tela | RF10 DEM e RF22 DEM; CA-RF10-01 a 04, CA-RF22-01, CA-RF22-04, CA-RF13-08, CA-RNF07-05 |
| 2.8 | Logo em seguida (menos de 2 s), clique de novo em **Ler carteirinha de Ana Teste** | "Leitura já recebida". Nenhuma liberação nova | RF15 DEM; CA-RF15-01 |
| 2.9 | Espere uns 3 segundos e clique de novo | "Aguardando passagem" | CA-RF15-03 |
| 2.10 | Clique em **Ler carteirinha de Bia Exemplo** | "Aguarde a passagem do aluno anterior". Bia não é liberada | CA-RF15-04 |
| 2.11 | Em **Catraca (protótipo)**, clique em **Simular passagem (giro)** | "Entrada registrada às …". Monitor: "Ana, entrada registrada.", e volta a "Aproxime sua carteirinha do leitor." depois de 4 s. O contador **Presentes agora** sobe para 1 sem recarregar. Aparece **Aviso ao responsável (protótipo)**: "Simulação — nenhum e-mail foi enviado", destinatário mascarado e o texto do e-mail, só com o primeiro nome, o tipo, a data, a hora e a forma | RF09 DEM; CA-RF09-03, CA-RF09-11, CA-RF22-02, CA-RF16-06, CA-RF21-01, CA-RF21-04; RNF07 DEM |

### 3.3 Aviso desligado a pedido (Bia)

| # | O que fazer | Resultado esperado | Demonstra |
|---|---|---|---|
| 2.12 | Clique em **Ler carteirinha de Bia Exemplo** e em **Simular passagem (giro)** | Entrada de Bia registrada e contador em 2, **sem** a seção "Aviso ao responsável": o responsável dela, Bruno Exemplo, pediu para não receber e-mail | CA-RF21-02, CA-RF08-06 (parte da notificação) |
| 2.13 | (Opcional) Em **Alunos**, busque `Bia` e clique em **Editar aluno Bia Exemplo** | A caixa **Enviar avisos de entrada e saída por e-mail ao contato principal** está desmarcada. Só um funcionário a muda, a pedido do responsável (DEC-38) | CA-RF08-06 |

### 3.4 Leitura pelo campo, expiração e cancelamento

| # | O que fazer | Resultado esperado | Demonstra |
|---|---|---|---|
| 2.14 | Pegue o código de Davi: tecla **F12**, inspecione o botão **Ler carteirinha de Davi Teste** e copie o atributo `data-codigo` (32 caracteres). Feche o F12, cole o código no campo **Código da carteirinha** e tecle **Enter** | Davi: "Entrada liberada — aguardando passagem". O campo fica vazio e com o foco. É o que o leitor USB faria | RNF07 DEM (leitura pelo campo); CA-RNF07-01, CA-RNF07-03, CA-RF09-09 (parte visual) |
| 2.15 | Não clique no giro. Espere **15 segundos** | "Liberação expirada — Davi não passou". O monitor volta na hora para a espera. O contador continua em 2 | RF09 DEM (expiração); CA-RF09-04, CA-RF22-03, CA-RF16-02 |
| 2.16 | Leia Davi de novo pelo painel e clique em **Cancelar liberação de Davi Teste** | "Liberação de Davi cancelada". Nada vai para o histórico nem para o e-mail | RF09 fluxo A2; CA-RF21-03 |

### 3.5 Leitura inválida

| # | O que fazer | Resultado esperado | Demonstra |
|---|---|---|---|
| 2.17 | Digite `XYZ` no campo **Código da carteirinha** e tecle **Enter** | Painel do funcionário: "Leitura recusada: Leitura não reconhecida. Tente novamente." Monitor: a mesma frase genérica do passo 2.4, sem o motivo. O foco continua no campo | RF23 DEM (código digitado); CA-RF23-01, CA-RF23-02, CA-RF23-04, CA-RF23-06, CA-RNF07-06 |
| 2.18 | (Opcional) Clique fora do campo, numa área vazia da página | "Leitor não está pronto. Clique no campo de leitura." | CA-RNF07-04 |

### 3.6 Registro manual de entrada e saída por leitura

| # | O que fazer | Resultado esperado | Demonstra |
|---|---|---|---|
| 2.19 | **Registro manual** → busque `Davi` → **Registrar manualmente para Davi Teste**. Mantenha a hora atual, motivo "Aluno esqueceu a carteirinha", **Revisar** e **Confirmar** | "Entrada manual registrada para Davi às …" e o aviso simulado para Carla | RF12 DEM; CA-RF12-01, CA-RF12-07 |
| 2.20 | Na **Portaria**, clique em **Ler carteirinha de Ana Teste** e depois em **Simular passagem (giro)** | Agora o tipo é **Saída**, calculado pelo sistema, sem trocar de modo. Depois do giro, a saída fica registrada, o contador **diminui** e aparece o aviso simulado de saída | RF13 DEM e RF14 DEM; CA-RF13-03, CA-RF14-02, CA-RF14-07, CA-RF15-02 |

## 4. Parte 3 — Consultas (4 min)

| # | O que fazer | Resultado esperado | Demonstra |
|---|---|---|---|
| 3.1 | Abra **Presentes** | Total 2: 6A = 1 (Bia) e 3B = 1 (Davi). Todas as turmas ativas aparecem, inclusive com zero. Nenhuma saída pendente | CA-RF16-01, CA-RF16-02 |
| 3.2 | Abra **Histórico** | Só as movimentações confirmadas de hoje, da mais recente para a mais antiga. As liberações expiradas e canceladas de Davi não aparecem. A tela não tem nenhuma ação de alteração | CA-RF18-01, CA-RF18-07, CA-RF19-08, CA-RF09-02 |
| 3.3 | Filtre **Data inicial** = ontem, **Data final** = hoje, **Turma** = 6A e clique em **Filtrar**. Depois ponha `000123` em **Aluno** e filtre de novo | As movimentações de Ana: a entrada e a saída **manuais** de ontem (Hora = horário informado; Observação com o motivo e "Lançado às … por ATE Demo") e a entrada e a saída **por leitura** de hoje (Observação vazia). "Aviso ao responsável": "Simulada" nas de Ana e "Não enviado" na de Bia | RF18 DEM e RF19 DEM; CA-RF18-02, CA-RF18-03, CA-RF19-01 a 03, CA-RF19-10, CA-RF12-08, CA-RF21-12 |
| 3.4 | Ponha a **Data final** antes da **Data inicial** e filtre | Mensagem de erro no campo da data final | CA-RF18-05 |
| 3.5 | Volte ao filtro do passo 3.3 (sem o aluno), clique em **Versão para impressão** e em **Imprimir** | Cabeçalho "Furin Access — Relatório de entradas e saídas", nome da escola, filtros, "Emitido em … por …" e total. Sem a coluna "Aviso ao responsável". Na visualização de impressão do navegador, sem menu, botões nem filtros. Não há exportação para planilha | RF20 DEM; CA-RF20-01, CA-RF20-02, CA-RF20-03, CA-RF20-05, CA-RF20-07, CA-RF19-10 (parte impressa) |

## 5. Parte 4 — Cadastro, carteirinha e edição (7 min)

Ana Teste (000123) já existe na carga. Por isso o cadastro usa um aluno novo, **Caio
Teste**, e a matrícula 000123 entra só para mostrar a duplicidade.

| # | O que fazer | Resultado esperado | Demonstra |
|---|---|---|---|
| 4.1 | **Alunos** → **Novo aluno**. Aluno: "Caio Teste", matrícula `000300`, turma 6A, a foto fictícia. Contato: "Rita Teste", `(11) 90000-0003`, `rita.teste@example.com`. Termo: signatário "Rita Teste", versão já preenchida, **sem data**, protocolo `TERMO-0001` e a caixa **Confirmo que conferi…** marcada. Clique em **Salvar** | Nada é salvo. Resumo de erros no topo com link para o campo, a data marcada como inválida e os outros campos preenchidos (a foto precisa ser escolhida de novo). O formulário não tem CPF, endereço, nascimento nem biometria | RF05 DEM (recusa sem a data); CA-RF05-02, CA-RF05-10 (versão `v1-2026` preenchida), CA-RF04-07, CA-RF04-09 (parte visual) |
| 4.2 | Ponha a **Data da assinatura** de ontem, escolha a foto de novo e clique em **Salvar** | Abre "Carteirinha de Caio Teste" com "Aluno Caio Teste cadastrado. Imprima a carteirinha.": nome, turma e QR Code em 85,6 × 54 mm, sem foto e sem o código em texto. Com **Ctrl+P**, só o cartão vai para o papel | RF04 DEM e RF07 DEM (impressão); CA-RF04-01, CA-RF04-05, CA-RF07-03, CA-RF07-07 |
| 4.3 | **Alunos** → **Novo aluno** de novo, com a matrícula `000123` e os demais dados de outro aluno fictício. Clique em **Salvar** | "Esta matrícula já está cadastrada. Confira a matrícula ou localize o cadastro existente para editá-lo." e o link **Abrir cadastro existente** | RF06 DEM e RF04 DEM (duplicidade); CA-RF06-01 |
| 4.4 | Na **Portaria**, antes de ler, copie pelo F12 o `data-codigo` do botão **Ler carteirinha de Caio Teste** e guarde. Depois clique no botão e em **Simular passagem (giro)** | Caio entra; o aviso simulado vai para Rita Teste | RF07 DEM (ler na portaria) |
| 4.5 | **Alunos** → **Editar aluno Caio Teste** → **Emitir segunda via** | "A carteirinha atual deixará de funcionar. Confirmar?" → **Confirmar segunda via** → a nova carteirinha abre para impressão | RF07 DEM (segunda via); CA-RF07-05 |
| 4.6 | Na **Portaria**, cole no campo o código antigo do Caio (passo 4.4) e tecle **Enter** | "Leitura recusada: Carteirinha revogada. Emitir segunda via." O monitor mostra só a frase genérica | RF07 DEM (antiga recusada) e RF23 DEM (carteirinha revogada); CA-RF07-04, CA-RF23-01, CA-RF23-02 |
| 4.7 | **Alunos** → busque `ana` → **Editar aluno Ana Teste**. Troque a turma de 6A para **6B** e clique em **Revisar alterações** | Tabela "Antes" e "Depois" com **Corrigir** e **Confirmar**. Clique em **Confirmar**: "Cadastro de Ana Teste atualizado." | RF08 DEM; CA-RF08-01 |
| 4.8 | **Histórico** com hoje e a turma 6A | As movimentações de Ana de hoje **continuam na 6A** | RF08 DEM; CA-RF08-02, CA-RF19-04, CA-RF18-02 |
| 4.9 | (Opcional) **Editar aluno Davi Teste**, desmarque **Enviar avisos de entrada e saída por e-mail ao contato principal**, **Revisar alterações** e **Confirmar**. Na Portaria, leia Davi e simule o giro | A revisão pede confirmação ("Os avisos de … deixarão de ser enviados a …"). A saída de Davi fica registrada **sem** o aviso ao responsável | RF21 DEM (desligar o aviso de Davi); CA-RF21-08, CA-RF08-06 |

## 6. Parte 5 — Contas da equipe (4 min)

| # | O que fazer | Resultado esperado | Demonstra |
|---|---|---|---|
| 5.1 | **Sair** e entre com `gestao.demo`. Abra **Presentes** | Os mesmos números e as mesmas ações que `ate.demo` vê | RF16 DEM (gestao.demo); CA-RF16-07 |
| 5.2 | **Contas da equipe** → **Nova conta**: nome "Paula Exemplo", login ` Paula.Exemplo ` → **Criar conta** | A senha temporária aparece **uma vez** (12 caracteres), com **Mostrar a senha** e **Copiar a senha**. Copie a senha | RF26 DEM; CA-RF26-01, CA-RF26-02 |
| 5.3 | Recarregue a página (F5) | A senha **não** aparece de novo | CA-RF26-09 |
| 5.4 | Na **janela anônima**, entre com `paula.exemplo` e a senha temporária | Abre **Definir nova senha**. Qualquer outro endereço leva de volta a ela. Defina uma senha nova (pelo menos 8 caracteres): abre a Portaria com o mesmo menu | RF26 DEM (entrar e trocar a senha); CA-RF26-01, CA-RF01-04 |
| 5.5 | Na janela normal, **Sair** e entre com `ate.demo`. Abra **Contas da equipe** | Na linha da própria conta: "Você não pode desativar a sua própria conta." | CA-RF26-05 |
| 5.6 | Clique em **Desativar Paula Exemplo** e confirme em **Desativar a conta** | Página de confirmação antes de agir; depois, Paula aparece como "Inativa" | RF26 DEM (desativar com `ate.demo`); CA-RF26-04, CA-RF26-08 (parte visual) |
| 5.7 | Na janela anônima, onde Paula ainda estava, clique em qualquer item do menu | Login com "Sua conta foi desativada. Procure a equipe da escola." | CA-RF03-03 (conta desativada) |
| 5.8 | Tente entrar de novo como `paula.exemplo` | A mesma mensagem genérica de usuário ou senha inválidos | CA-RF26-06, CA-RF01-03, CA-RF02-02 |

## 7. Parte 6 — Encerramento e acesso negado sem login (1 min)

| # | O que fazer | Resultado esperado | Demonstra |
|---|---|---|---|
| 6.1 | Clique em **Sair** e depois no botão **Voltar** do navegador. Tente repetir uma ação | "você saiu do sistema." Voltar não reabre a área restrita: o sistema pede login | CA-RF01-08 |
| 6.2 | Sem entrar, digite <http://localhost:8080/historico> e <http://localhost:8080/contas/equipe> | As duas levam à tela de login | RF03 DEM; CA-RF18-08, CA-RF26-07 |

Fim da demonstração. Se quiser, mostre o log de auditoria (`logs/auditoria.log`): os
eventos `LOGIN_FALHA`, `LEITURA_RECUSADA`, `REGISTRO_MANUAL`, `NOTIFICACAO_SIMULADA`,
`ALUNO_CADASTRADO` e `PREFERENCIA_AVISO_ALTERADA` aparecem com o e-mail mascarado, sem
senha e com só os 4 primeiros caracteres do código da carteirinha (RNF05, CA-RNF05-05).

## 8. Registro dos ensaios

O cartão T-46 fica concluído quando o roteiro for ensaiado do começo ao fim. Anote cada
ensaio aqui, com o que falhou ou mudou.

| Data | Quem conduziu | Navegador | Passos com problema | Observações |
|---|---|---|---|---|
| | | | | |

## 9. O que este roteiro não mostra

| Item | Por quê | Onde está a evidência |
|---|---|---|
| Expiração da sessão (30 min sem uso, aviso 60 s antes) | Demora demais para a banca | `SessaoTests`, `AvisoDeSessaoTests` (CA-RF01-10) |
| Fim do bloqueio do login (15 min) | Demora demais | `BloqueioDeLoginTests` (CA-RF02-04) |
| Revogação do termo | Inativa o aluno; mostre só se sobrar tempo, num aluno criado na hora | `RevogacaoDoTermoTests` (CA-RF05-09) |
| Falha no envio do e-mail | Só existe com o envio trocado no teste | `EnvioDeNotificacoesTests` (CA-RF21-06) |
| Tempo da leitura na tela (2 s) | Medido só no servidor; a medição na tela ainda falta | [`rnf02-medicao.md`](rnf02-medicao.md) (RNF02) |
| Teclado, leitor de tela e Lighthouse | Conferência manual da T-45, ainda não feita | [`acessibilidade.md`](acessibilidade.md) (RNF06) |
| Leitor USB, catraca e e-mail real | Fora do MVP (DEC-25, DEC-32) | — |
