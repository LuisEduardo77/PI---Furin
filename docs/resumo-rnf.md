# Resumo dos requisitos não funcionais

**Documentação:** Rascunho (resumo feito a partir dos documentos revisados) ·
**Atualizado em:** 21/10/2026

O Furin Access tem **8 requisitos não funcionais** (RNF01 a RNF08), todos vindos da
Atividade 8. Eles dizem **como** o sistema deve funcionar: em que plataforma, com que
velocidade, com que segurança e com que acessibilidade. Cada RNF tem um arquivo próprio
em [`requirements/rnf/`](../requirements/rnf/), com critérios de aceitação e forma de
verificação. **Se este resumo e um arquivo de RNF discordarem, vale o arquivo do RNF.**

Hoje todos estão com `Documentação: Revisada` e `Implementação: Planejada`. A coluna
"No protótipo" mostra a **meta** de cada um.

## 1. Visão geral

| RNF | Título | Categoria | Em uma frase | No protótipo |
|---|---|---|---|---|
| [RNF01](../requirements/rnf/RNF01.md) | Acesso por navegador, com layout responsivo | Plataforma | Funciona no navegador, sem instalar nada, do computador ao celular | Implementada |
| [RNF02](../requirements/rnf/RNF02.md) | Confirmação da leitura na tela em até 2 segundos | Desempenho | A resposta da leitura e do giro aparece em até 2 s | Implementada |
| [RNF03](../requirements/rnf/RNF03.md) | Senhas armazenadas com hash, nunca em texto plano | Segurança | Senhas guardadas só como hash bcrypt | Implementada |
| [RNF04](../requirements/rnf/RNF04.md) | Comunicação entre navegador e servidor via HTTPS | Segurança | Tráfego protegido por HTTPS quando houver rede | **Planejada, não verificada** |
| [RNF05](../requirements/rnf/RNF05.md) | Coleta e exposição mínima de dados pessoais | Privacidade | Só os dados necessários, mostrados só a quem precisa e guardados por 5 anos | Implementada |
| [RNF06](../requirements/rnf/RNF06.md) | Acessibilidade digital | Acessibilidade | Telas conforme a ABNT NBR 17225 e a WCAG 2.2, nível AA | Implementada |
| [RNF07](../requirements/rnf/RNF07.md) | Compatibilidade com leitor USB de QR Code que funciona como teclado | Hardware | A leitura entra pelo campo de texto, como um teclado | **Simulada** (sem leitor) |
| [RNF08](../requirements/rnf/RNF08.md) | Backend em Java (Spring Boot) e banco de dados MySQL | Implementação | Java 21, Spring Boot 4.1.x e MySQL 8.4 | Implementada |

## 2. O que cada um exige

### RNF01 — Plataforma

- Chrome, Edge e Firefox, nas duas últimas versões estáveis (DEC-28, G3). O Safari no
  iOS saiu junto com a consulta do responsável (DEC-37).
- Funciona com **320 px** de largura e com **zoom de 200%**, sem perder conteúdo.
- Retrato e paisagem. Alvos de toque de pelo menos 44 × 44 px.
- Só a tela da portaria exige JavaScript.

### RNF02 — Desempenho

- Até **2 s** entre o Enter (ou o clique no painel de simulação) e a mensagem nos dois
  painéis. O mesmo limite vale para a confirmação do giro (DEC-28, A10).
- A resposta não espera o envio da notificação nem o carregamento completo da foto.
- Medição: **30 leituras seguidas**, com uma base de **800 alunos fictícios** e 30 dias
  de movimentações (DEC-28, G4). A evidência vai para o cartão T-33.

### RNF03 — Senhas

- Hash pelo encoder delegante do Spring Security, com bcrypt e o custo padrão
  (DEC-28, G5).
- Nenhuma senha em log, mensagem, resposta, repositório ou script SQL.
- As senhas das contas de demonstração vêm de variáveis de ambiente.

### RNF04 — HTTPS

- Na implantação: HTTPS obrigatório, TLS 1.2 ou superior, cookies `Secure`, `HttpOnly`
  e `SameSite`, e HSTS.
- **No protótipo**, a aplicação roda local, com HTTP (DEC-31). Por isso o RNF04 fica
  **planejado e não verificado**. Os cookies e os cabeçalhos de segurança serão
  configurados já no protótipo (T-09).
- Nenhum certificado, chave ou senha fica no repositório.

### RNF05 — Dados pessoais

- **Coleta:** nome, matrícula, turma, foto, contato principal (nome, telefone e e-mail),
  evidência do termo, movimentações e contas dos funcionários. Os pais não têm conta
  (DEC-37). **Não** coleta CPF, endereço, data de
  nascimento, biometria, religião nem dados de saúde.
- **Exposição mínima:** o monitor do aluno mostra só o primeiro nome; o e-mail leva só o
  primeiro nome, o tipo, a data e a hora, sem link para o sistema; fotos e termos ficam
  na área privada, que só funcionários autenticados acessam.
- **Guarda:** 5 anos depois que o aluno sai da escola ou que o termo é revogado. Depois,
  os dados são apagados ou anonimizados (DEC-33, DEC-36). No protótipo, nada é apagado
  automaticamente.
- **Recusa ou revogação do termo:** o aluno não fica no sistema e continua estudando,
  com o controle feito pela escola (DEC-34).
- Testes, repositório e carga inicial usam só **dados fictícios**.

### RNF06 — Acessibilidade

- Baseado nos itens da Atividade 11, com os **IDs originais** (DEC-16).
- Pontos principais:
  - todas as funções pelo teclado, com foco visível de 3 px;
  - título por página, `lang="pt-BR"` e link "Ir para o conteúdo principal";
  - `label` em todos os campos, "(obrigatório)" escrito e erros que dizem o que
    corrigir;
  - estado sempre em texto, e não só em cor, com contraste de pelo menos 4,5:1;
  - mensagens anunciadas por leitor de tela sem mover o foco;
  - aviso de sessão 60 s antes de expirar.
- Verificação com o **NVDA** e com o **axe DevTools ou o Lighthouse** (DEC-28, G6).
- O prazo de 15 s do giro é tratado como limite essencial da operação (DEC-28, A11).

### RNF07 — Leitor de QR Code

- **No protótipo não há leitor** (DEC-25). A leitura é simulada de duas formas, pela
  mesma rota que o leitor real usaria:
  - digitar ou colar o código no campo de leitura e apertar Enter;
  - clicar no aluno no painel **"Simular leitura (protótipo)"**.
- O giro da catraca é simulado pelo botão "Simular passagem (giro)" (DEC-18).
- As simulações só existem no modo protótipo e aparecem identificadas na tela.
- Na implantação: leitor **2D** (os leitores a laser 1D não leem QR Code), USB, em modo
  teclado, com Enter no final.

### RNF08 — Tecnologia

- Java 21, Spring Boot 4.1.x e MySQL 8.4 (DEC-17). Telas em Thymeleaf. Segurança com
  Spring Security.
- `mvn -B verify` passa localmente e no GitHub Actions. O SonarCloud mostra o resultado
  da análise no pull request.
- O envio de e-mail fica atrás de uma interface, e no MVP só existe a versão simulada
  (DEC-32).
- A aplicação roda local. O GitHub Pages pode publicar só a documentação (DEC-31).

## 3. Como os RNF se ligam aos RF

| RNF | RF mais afetados |
|---|---|
| RNF01 | Todos, em especial RF16 e RF18 (consultas em telas pequenas) |
| RNF02 | RF09, RF14 (leitura e giro) |
| RNF03 | RF01, RF26 (senhas) |
| RNF04 | Todos, quando houver rede. RF21 só numa evolução com envio real |
| RNF05 | RF04, RF05, RF08, RF10, RF18 a RF23 |
| RNF06 | Todas as telas |
| RNF07 | RF07, RF09, RF14 |
| RNF08 | Todos |

## 4. Segurança, privacidade e acessibilidade também estão nos RF

Os controles das Atividades 9, 10 e 11 **não** viraram RNF novos (DEC-16). Eles estão
dentro dos RF e dos RNF a que se aplicam. A tabela completa está em
[`rastreabilidade.md`](rastreabilidade.md) §6. Os principais:

| Controle | Fonte | Onde está |
|---|---|---|
| Consentimento dos pais (LGPD art. 14) | A09 | RF05 |
| Minimização de dados (LGPD art. 6º, III) | A09 | RF04, RF21, RNF05 |
| Controle de acesso: só funcionários autenticados (perfil único, DEC-37) | A10 §5.15 | RF03, RF26 |
| Senhas e autenticação | A10 §5.17, §8.5 | RF01, RF02, RNF03 |
| Log de eventos sem senhas | A10 §8.15 | Todos |
| Backup | A10 §8.13 | Script simples, 7 cópias (DEC-30) |
| Codificação segura (validação, consultas parametrizadas, CSRF) | A10 §8.28 | Todos |
| Acessibilidade (ABNT NBR 17225, WCAG 2.2) | A11 | RNF06 e os RF de tela |
