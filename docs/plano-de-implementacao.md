# Plano de implementação

**Documentação:** Revisada em 21/10/2026 (DEC-37 a DEC-39) · **Implementação:**
T-00 concluída; T-01 iniciada (repositório criado); T-03 concluída (27/10/2026); T-04 concluída (29/10/2026); T-05 concluída (29/10/2026); T-06 concluída (29/10/2026); T-07 concluída (29/10/2026); demais não iniciadas · **Atualizado em:** 29/10/2026

As tarefas são pequenas, cabem em um cartão do Trello e estão em ordem de
dependência. Segurança e acessibilidade fazem parte de cada tarefa desde o começo,
em vez de ficarem para uma etapa final.

## 1. Como usar no Trello

### Listas sugeridas

| Lista | Significado |
|---|---|
| Backlog | Tarefas ainda não iniciadas |
| Pronto para começar | Todas as dependências estão concluídas |
| Em andamento | Alguém já está trabalhando na tarefa |
| Em revisão (PR aberto) | Aguardando revisão de outro integrante |
| Concluído | Tarefa terminada e com o pull request aceito |

### Modelo de cartão

- **Título:** `T-nn — <tarefa>`
- **Etiquetas:** RF/RNF, mais `segurança` ou `acessibilidade` quando se aplicar
- **Responsável:** *a preencher*
- **Prazo:** *a preencher*
- **Depende de:** IDs das tarefas
- **Checklist:** os itens de "Pronto quando" da tarefa

### Critério geral de "concluído"

Vale para todo cartão:

1. O código foi revisado em pull request por **outro integrante**. Código gerado com o
   Claude não é considerado validado sem essa revisão (A10 §8.28; A13 p.6).
2. Os testes da tarefa passam localmente e na CI, a partir de T-05.
3. Nenhum segredo e nenhum dado real foram versionados (A10 §5.17, §8.33).
4. Telas novas passaram pela checklist de acessibilidade (seção 3).
5. O campo **Implementação** do RF/RNF foi atualizado:
   - `Implementada` ou `Simulada` quando o código existir;
   - `Verificada` só quando houver evidência registrada.

## 2. Tarefas

### Fase 0: preparação

| ID | Tarefa | RF/RNF | Depende de | Pronto quando |
|---|---|---|---|---|
| T-00 | Revisar estes documentos em grupo, decidir PEND-01 a PEND-16 e as propostas [PROP], e marcar os arquivos como `Revisada` | Todos | — | **Concluída em 19/10/2026.** Decisões DEC-24 a DEC-29 em `rastreabilidade.md` §2.3 , DEC-30 a DEC-34 em §2.4 e DEC-35 e DEC-36 em §2.5, confirmadas pelo grupo. Todas as pendências foram resolvidas |
| T-01 | Criar o repositório no GitHub com proteção da `main`, `.gitignore` (Java, Maven, IDE, `application-local.yml`, pasta de arquivos privados) e esta documentação | RNF08 | — | **Repositório criado** (DEC-24): `https://github.com/LuisEduardo77/PI---Furin.git`. Falta conferir a proteção da `main`, o `.gitignore`, o envio desta documentação e o push pelo GitHub Desktop de todos |
| T-02 | Criar o quadro no Trello com as listas e os cartões deste plano | — | — | Cartões T-00 a T-47 criados |

### Fase 1: base técnica

| ID | Tarefa | RF/RNF | Depende de | Pronto quando | Segurança e acessibilidade |
|---|---|---|---|---|---|
| T-03 | Instalar o MySQL 8.4 local e **validar o DDL** de `arquitetura-e-dados.md` §5.5 | RNF08 | T-01 | Todas as tabelas criadas sem erro. CHECKs e UNIQUEs testados com INSERTs inválidos. **Concluída em 27/10/2026** no MySQL 8.4.13: 9 tabelas sem erro e 86 de 86 casos aprovados ([evidência](evidencias/t-03/README.md)). Usuário `furin` criado por [`scripts/criar-usuario-banco.sql`](../scripts/criar-usuario-banco.sql), com acesso só a `furin` e `furin_test` | Usuário do banco exclusivo do projeto, sem `root` |
| T-04 | Gerar o projeto no Spring Initializr (Java 21, Boot 4.1.x, Web, Thymeleaf, Security, Data JPA, Validation, Flyway, driver MySQL; o Mail só entra se houver envio real, DEC-32) e definir os perfis `prototipo` e `test` (o `smtp` saiu do MVP, DEC-32) | RNF08 | T-01 | `mvn verify` passa e a aplicação sobe no perfil `prototipo`. **Concluída em 29/10/2026:** Boot 4.1.2, `./mvnw -B verify` com BUILD SUCCESS (1 teste, perfil `test`, banco `furin_test`) e aplicação iniciada no perfil `prototipo` (banco `furin`) | Credenciais só em variável de ambiente ou em `application-local.yml` |
| T-05 | Configurar o GitHub Actions: build e testes com o serviço MySQL 8.4 | RNF08 | T-04 | Pipeline verde em um PR de teste. **Concluída em 29/10/2026:** workflow `.github/workflows/ci.yml` e teste `BancoDeDadosTests` (MySQL 8.4 e `furin_test`), com execução verde no [PR #4](https://github.com/LuisEduardo77/PI---Furin/pull/4) e no push da `main` | Credenciais da CI descartáveis. Nenhum dado real |
| T-06 | Criar a migração Flyway `V1__esquema_inicial.sql` a partir do DDL validado | RNF08, RNF05 | T-03, T-04 | A aplicação cria o esquema em um banco vazio. **Concluída em 29/10/2026:** `V1__esquema_inicial.sql` copiada sem alteração do DDL validado na T-03 (9 tabelas). `EsquemaInicialTests` esvazia o `furin_test`, sobe a aplicação no perfil `test` e confere as 9 tabelas, InnoDB/utf8mb4 e o histórico do Flyway (`./mvnw -B verify`: 7 testes). A CI do PR confirma no MySQL 8.4 vazio | — |
| T-07 | Criar a classe de propriedades `furin.*` com os valores aprovados ([arquitetura-e-dados §4](arquitetura-e-dados.md)) | — | T-04, T-00 | Nenhum valor de regra escrito direto no código. **Concluída em 29/10/2026:** `config/FurinProperties` com os 28 parâmetros da tabela, valores só no `application.yml` e Bean Validation (a aplicação não sobe com valor ausente ou inválido). `FurinPropertiesTests` confere cada valor carregado e `FurinPropertiesValidacaoTests` confere 16 valores inválidos (`./mvnw -B verify`: 31 testes) | — |
| T-08 | Fazer o layout-base Thymeleaf: cabeçalho, menu único dos funcionários (DEC-37), link "Ir para o conteúdo principal", `lang="pt-BR"`, títulos de página e CSS com a paleta de A11, foco de 3 px, alvos de 44 px e unidades relativas | RNF01, RNF06 | T-04 | Página de exemplo aprovada na checklist da seção 3 | A11 §5.1.1, §5.7.12, §5.11.3, §5.13.1, §5.13.2, §5.8.6 |
| T-09 | Configurar o Spring Security base: negar por padrão, CSRF ligado, `PasswordEncoder` delegante, cookies seguros e cabeçalhos de segurança | RNF03, RNF04 | T-04 | Uma rota não mapeada retorna 403/login. Teste do encoder | A10 §5.15, §5.17, §8.28 |
| T-10 | Criar o logger de auditoria com mascaramento de e-mail e sem senhas | RNF05 | T-04 | Teste confirma que nenhuma senha aparece no log | A10 §8.15 |
| T-11 | Criar a carga fictícia `V2__carga_ficticia.sql` (turmas, alunos, contatos de responsáveis sem conta e as contas `ate.demo` e `gestao.demo`, DEC-37), com as senhas vindas do ambiente | RNF05, RNF03 | T-06 | Login dos usuários de demonstração possível | A10 §8.33 |

### Fase 2: acesso

| ID | Tarefa | RF/RNF | Depende de | Pronto quando | Segurança e acessibilidade |
|---|---|---|---|---|---|
| T-12 | Fazer a tela de login e a autenticação do perfil `FUNCIONARIO`, com redirecionamento para `/portaria` | RF01, RF03 | T-08, T-09, T-11 | `ate.demo` e `gestao.demo` entram e caem na portaria, com o mesmo menu (DEC-37) | `autocomplete`, colar senha, título "Login \| Furin Access" (A11 §5.9.18) |
| T-13 | Mostrar a mensagem genérica para credencial inválida e conta inativa, com log da tentativa | RF02 | T-12, T-10 | Critérios de aceitação do RF02 atendidos | Mensagem igual nos dois casos (A10 §8.5) |
| T-14 | Limitar tentativas seguidas de login (5 tentativas, bloqueio de 15 min; DEC-28) | RF02 | T-13, T-07 | Teste do bloqueio e do desbloqueio | A10 §8.5 |
| T-15 | Fazer a troca obrigatória de senha, a validade da senha temporária (72 h) e a tela "Alterar senha" (RF01, fluxo A3) | RF01, RNF03 | T-12 | O filtro redireciona para `/senha/definir`. Senha vencida é recusada. `/senha/alterar` exige a senha atual | RN14. Senha temporária com 12 caracteres, nova com pelo menos 8 (DEC-28) |
| T-16 | Configurar a expiração da sessão com aviso acessível 60 s antes dos 30 min de inatividade (DEC-28) e "Continuar conectado" | RF01 | T-12 | Aviso anunciado e operável por teclado | A11 §5.16.2 |
| T-17 | Fazer o teste de autorização em todas as rotas existentes (sem login, funcionário ativo, conta desativada), revisado a cada fase | RF03 | T-12 | Teste parametrizado de rotas × situações (CA-RF03-03) | A10 §8.29 |

### Fase 3: cadastro

| ID | Tarefa | RF/RNF | Depende de | Pronto quando | Segurança e acessibilidade |
|---|---|---|---|---|---|
| T-18 | Criar a área privada de arquivos: upload e entrega autorizada | RF04, RF05 | T-09 | Arquivo salvo fora de `static/`, com nome UUID. Sem login, o pedido leva à tela de login | Tipo validado pelo conteúdo. Foto: JPEG/PNG até 2 MB. Termo: PDF/JPEG/PNG até 5 MB (DEC-28; A10 §5.34) |
| T-19 | Fazer o formulário de cadastro de aluno: dados, turma, foto e contato principal | RF04, RNF05 | T-08, T-18 | Salva com vínculo de turma vigente e contato principal | Labels, "(obrigatório)", ordem de foco, erros descritivos (A11 §5.9.3, §5.9.7, §5.9.9, §5.1.4) |
| T-20 | Registrar a conferência do termo (versão `v1-2026`, data, referência, conferente), o cadastro completo em uma transação e o registro da revogação (DEC-34) | RF05 | T-19 | Sem termo, nada é salvo. Data futura é recusada. A revogação inativa o aluno, revoga a carteirinha e encerra o acesso do responsável | RN16. Checkbox com texto associado |
| T-21 | Validar duplicidade de matrícula e de código, com sugestão de correção | RF06 | T-19 | Mensagem de A11 §5.9.10 | — |
| T-22 | Emitir a carteirinha (código de 32 caracteres hexadecimais com `SecureRandom`), gerar o QR Code (ZXing), fazer a página de impressão e a segunda via com revogação | RF07, RNF07 | T-19 | O QR Code decodificado em teste (ZXing) devolve o código. A carteirinha antiga é revogada | RN15. Código sem dados pessoais |
| T-23 | Fazer a edição do aluno com resumo de confirmação, troca de turma por novo vínculo e a opção "Aviso por e-mail" ligada ou desligada a pedido do responsável (RF08 A5, DEC-38) | RF08 | T-19 | Histórico antigo preservado. Log da alteração e de `PREFERENCIA_AVISO_ALTERADA` | A11 §5.9.12. Checkbox nativo com estado anunciado |
| T-24 | Fazer a busca por nome ou matrícula | RF11 | T-19 | Resultado acessível e navegável por teclado | Nome acessível nas ações ("Editar aluno Ana Teste") |

### Fase 4: portaria

| ID | Tarefa | RF/RNF | Depende de | Pronto quando | Segurança e acessibilidade |
|---|---|---|---|---|---|
| T-25 | Criar o serviço de tipo automático (RN01), com teste de unidade da tabela de decisão | RF13 | T-06 | Os três casos da RN01 cobertos | O tipo nunca vem da interface |
| T-26 | Criar o endpoint de leitura: validações (RN09), bloqueio por aluno, `chave_requisicao` e registro `AGUARDANDO_GIRO` | RF09, RF14, RNF02 | T-22, T-25 | Liberação devolvida com tipo, nome, turma e foto | A10 §8.26. RN08 |
| T-27 | Implementar a janela de 2 s, a leitura repetida com pendência (RN04) e uma liberação por vez (RN05) | RF15 | T-26 | Critérios de aceitação do RF15 atendidos | RN03–RN05 |
| T-28 | Fazer o giro simulado: o botão confirma e grava `ocorrido_em` | RF09, RF14, RNF07 | T-26 | Situação `CONFIRMADA`. Botão disponível só no perfil `prototipo` | Botão de 44 px, operável por teclado |
| T-29 | Expirar as liberações sem giro (15 s) e permitir que o funcionário cancele, com motivo `FUNCIONARIO` (DEC-28, A1 e A2; DEC-37) | RF09, RF15 | T-28, T-07 | Teste de expiração. Cancelamento registrado no log | — |
| T-30 | Montar a tela da portaria com dois painéis (funcionário e monitor do aluno), campo de leitura com foco e o painel "Simular leitura (protótipo)" com as carteirinhas fictícias (DEC-25) | RF10, RF22, RF23, RNF07, RNF02 | T-26, T-08 | Foto, nome, turma e tipo só no painel do funcionário. Monitor com primeiro nome. Volta à espera em 4 s. O painel de simulação só aparece no perfil `prototipo` | `role="status"`/`alert` separados, texto além da cor, alt da foto, foco que volta ao campo (A11 §5.13.8, §5.11.1, §5.2.1; A09 art. 17) |
| T-31 | Mostrar as mensagens de leitura inválida: motivo ao funcionário e mensagem genérica ao aluno | RF23 | T-30 | Critérios de aceitação do RF23 atendidos | RN09, RN17 |
| T-32 | Fazer o registro manual de entrada e saída: busca, tipo calculado, horário informado, motivo e confirmação | RF12 | T-24, T-25 | Critérios de aceitação do RF12 atendidos | Reaproveitamento dos dados e confirmação (A11 §5.9.12, §5.9.15) |
| T-33 | Medir o tempo de resposta da leitura simulada e do giro (meta de 2 s) com 30 leituras e a base de 800 alunos fictícios | RNF02, RNF07 | T-30 | Planilha ou print com as medições anexado ao cartão | — |

### Fase 5: consultas

| ID | Tarefa | RF/RNF | Depende de | Pronto quando | Segurança e acessibilidade |
|---|---|---|---|---|---|
| T-34 | Fazer o painel de presentes (total e por turma) e as saídas pendentes, com registro de saída manual a partir das pendências | RF16 | T-28, T-32 | Só confirmadas contam. Pendências de dias anteriores listadas | Legenda "Presentes agora…" (A11 §5.12.12). Contagem sem anúncio repetido |
| T-35 | Fazer o histórico com filtros (período, turma, aluno) | RF18, RF19 | T-28, T-32 | Mostra tipo, forma, motivo e os dois horários do registro manual | Tabela com `th scope` (A11 §5.6.3). Filtros com label |
| T-36 | Fazer a impressão pelo navegador (`@media print`) | RF20 | T-35 | Impressão mostra os filtros aplicados | — |
| T-37 | ~~Testar as restrições da Gestão~~ **Cancelada** (RF17 fora do escopo, DEC-37). O ID não é reaproveitado | — | — | — | — |

### Fase 6: notificações (simuladas)

| ID | Tarefa | RF/RNF | Depende de | Pronto quando | Segurança e acessibilidade |
|---|---|---|---|---|---|
| T-38 | Criar a notificação `PENDENTE` na transação da confirmação, seguindo as regras de destinatário e preferência | RF21 | T-28, T-32 | Critérios de aceitação do RF21 sobre destinatário e preferência atendidos | RN11. Conteúdo mínimo (A10 §5.34) |
| T-39 | Fazer o envio assíncrono depois do commit: sempre `SIMULADA` (DEC-32), com o serviço de envio atrás de uma interface | RF21 | T-38 | Uma falha não altera a movimentação (critérios do RF21). A tela indica "Simulação — nenhum e-mail foi enviado" | TLS no perfil `smtp` (A10 §8.24) |

### Fase 7: contas da equipe

| ID | Tarefa | RF/RNF | Depende de | Pronto quando | Segurança e acessibilidade |
|---|---|---|---|---|---|
| T-40 | ~~Conta do responsável~~ **Cancelada** (RF25 fora do escopo, DEC-37) | — | — | — | — |
| T-41 | ~~Consulta resumida do responsável~~ **Cancelada** (RF24 fora do escopo, DEC-37) | — | — | — | — |
| T-42 | ~~Interruptor do responsável~~ **Cancelada**. A opção de aviso foi para a edição do aluno, na T-23 (DEC-38) | — | — | — | — |
| T-43 | Fazer a tela "Contas da equipe": criar, gerar nova senha temporária, desativar e reativar contas `FUNCIONARIO` (DEC-39) | RF26, RNF03 | T-15 | Critérios de aceitação do RF26 atendidos | Ninguém desativa a própria conta. Senha exibida uma vez, `no-store`. Confirmação nas ações (A11 §5.9.12) |

### Fase 8: qualidade e entrega

| ID | Tarefa | RF/RNF | Depende de | Pronto quando | Segurança e acessibilidade |
|---|---|---|---|---|---|
| T-44 | Integrar o SonarCloud à CI e tratar os problemas críticos | RNF08, RNF04 | T-05 | Quality gate visível no PR | Token em *Secrets* |
| T-45 | Fazer a rodada de verificação de acessibilidade (A11 p.9) em todas as telas e registrar as evidências | RNF06, RNF01 | T-30, T-35, T-43 | Checklist da seção 3 preenchida por tela | A11 p.9 |
| T-47 | Criar os scripts de backup (`scripts/backup.ps1` e `backup.sh`): dump do banco e `.zip` da pasta de arquivos, 7 últimas cópias fora do projeto, e testar uma restauração | — (A10 §8.13; DEC-30) | T-06, T-18 | Restauração feita em um banco vazio e registrada no cartão | Usuário do banco só de leitura. Senha por variável de ambiente. Pasta de backup no `.gitignore` |
| T-46 | Preparar o roteiro e as evidências da demonstração: as contas `ate.demo` e `gestao.demo` com o mesmo acesso, leitura simulada (campo e painel), giro simulado, repetição, inválida, notificação simulada, aviso desligado a pedido e contas da equipe | Todos | T-33 a T-45, T-47 | Roteiro ensaiado. Status dos RF/RNF atualizado | A10 p.4 |

Itens **fora** deste plano, porque estão planejados para depois do protótipo:

- HTTPS em ambiente externo (RNF04). A aplicação roda local (DEC-31);
- backup **automático** diário, só se houver implantação real (DEC-30);
- envio real de e-mail (DEC-32). Não há WhatsApp, nem simulado (DEC-37);
- descarte automático de dados (DEC-33);
- publicação da documentação no GitHub Pages (opcional, DEC-31);
- integração física com a catraca;
- contas e telas para pais/responsáveis e para alunos (DEC-37);
- leitor USB físico e integração com a catraca (DEC-18, DEC-25).

## 3. Checklist de acessibilidade por tela

Usada em T-08, nas tarefas de tela e em T-45. A fonte é A11.

- [ ] `lang="pt-BR"` e título "Página \| Furin Access"
- [ ] Link "Ir para o conteúdo principal". Menu sempre na mesma ordem
- [ ] h1 único e h2 por seção
- [ ] Todos os campos com `label`. "(obrigatório)" escrito, não só asterisco
- [ ] Erro descritivo com `aria-invalid` e `aria-describedby`. Dados válidos preservados
- [ ] Operável só pelo teclado. Ordem de foco lógica. Foco de 3 px visível e nunca coberto
- [ ] Estado sempre em texto, não só em cor. Contraste conferido (texto 4,5:1, componentes 3:1)
- [ ] Alvos de 44 × 44 px
- [ ] Funciona com 320 px de largura e com zoom de 200%, sem perda de conteúdo
- [ ] Mensagens de status em `role="status"`/`role="alert"`, sem mover o foco

## 4. Ordem resumida

```
T-00 → T-01 → T-03/T-04 → T-05, T-06 → T-07..T-11
     → T-12..T-17 (acesso)
     → T-18..T-24 (cadastro)
     → T-25..T-33 (portaria)
     → T-34..T-37 (consultas)
     → T-38..T-39 (notificações)
     → T-43 (contas da equipe; T-37, T-40 a T-42 canceladas)
     → T-44, T-45, T-47, T-46 (qualidade, backup e entrega)
```

Fases 6 e 7 podem andar em paralelo depois da fase 4. T-44 pode começar logo depois
de T-05.
