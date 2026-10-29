# Resumo das ferramentas

**Documentação:** Rascunho (resumo feito a partir dos documentos revisados) ·
**Atualizado em:** 21/10/2026

Este resumo lista as ferramentas do Furin Access: o que cada uma é, para que serve no
projeto e em que estado está. Os detalhes técnicos estão em
[`arquitetura-e-dados.md`](arquitetura-e-dados.md) §1 e no [RNF08](../requirements/rnf/RNF08.md).
Se este resumo e esses arquivos discordarem, valem os arquivos detalhados.

**Hoje nenhuma ferramenta de desenvolvimento foi configurada.** O repositório existe,
mas o código ainda não começou.

## 1. Construção do sistema

| Ferramenta | O que é | Para que usamos | Decisão | Estado |
|---|---|---|---|---|
| **Java 21** | Linguagem de programação (versão LTS) | Escrever o backend | DEC-17 | Planejada |
| **Spring Boot 4.1.x** | Framework Java para aplicações web | Estrutura da aplicação, configuração e servidor web embutido | DEC-17 | Planejada |
| **Spring Security** | Módulo de segurança do Spring | Login, sessão, acesso só para funcionários (perfil único `FUNCIONARIO`, DEC-37), CSRF e hash de senhas | ATU-06 | Planejada |
| **Spring Data JPA** | Acesso a banco de dados pelo Spring | Ler e gravar no MySQL com consultas parametrizadas | DEC-28 (G1) | Planejada |
| **Bean Validation** | Validação de dados | Conferir os campos dos formulários no servidor | DEC-28 (G1) | Planejada |
| **Thymeleaf** | Motor de páginas HTML do lado do servidor | Montar as telas | ATU-06 | Planejada |
| **HTML, CSS e JavaScript** | Linguagens das páginas web | Telas, estilo acessível e a interação da portaria | ATU-06 | Planejada |
| **MySQL 8.4** | Banco de dados relacional (versão LTS) | Guardar alunos, contatos, carteirinhas, movimentações e contas | DEC-17 | Planejada |
| **Flyway** | Controle de versões do banco | Criar e atualizar as tabelas a partir de scripts numerados | DEC-28 (G1) | Planejada |
| **ZXing** | Biblioteca de códigos de barras | Gerar o QR Code da carteirinha e conferi-lo nos testes | DEC-28 (G1) | Planejada |
| **Maven** | Gerenciador de build do Java | Baixar dependências, compilar e rodar os testes (`mvn -B verify`) | ATU-07 | Planejada |
| **Spring Initializr** | Gerador de projetos Spring | Criar o projeto inicial com as dependências certas (T-04) | — | Planejada |
| **Spring Mail** | Módulo de e-mail do Spring | **Previsto, mas não usado:** o e-mail é sempre simulado (DEC-32) | ATU-06, DEC-32 | Fora do MVP |

## 2. Testes e qualidade

| Ferramenta | Para que usamos | Decisão | Estado |
|---|---|---|---|
| **JUnit e Spring Boot Test** | Testes de unidade e de integração com o MySQL | ATU-07 | Planejada |
| **Spring Security Test (MockMvc)** | Testar login, bloqueio e o acesso de cada rota (sem login, funcionário ativo, conta desativada) | — | Planejada |
| **GitHub Actions** | Integração contínua: compila e testa a cada pull request, com um MySQL 8.4 de teste | ATU-07 | **Configurada** (T-05), com execução verde no PR #4 |
| **SonarCloud** | Análise estática do código (falhas e trechos inseguros) no pull request | ATU-07 | **Não configurada** (T-44) |
| **NVDA** | Leitor de tela gratuito, para testar a acessibilidade | DEC-28 (G6) | Planejada |
| **axe DevTools ou Lighthouse** | Verificador automático de acessibilidade no navegador | DEC-28 (G6) | Planejada |
| **Ferramentas do navegador** | Medir o tempo de resposta da leitura (meta de 2 s, RNF02) | — | Planejada |

## 3. Organização e versionamento

| Ferramenta | Para que usamos | Decisão | Estado |
|---|---|---|---|
| **GitHub** | Guardar o código e a documentação. Repositório: `https://github.com/LuisEduardo77/PI---Furin.git` | DEC-24 | **Criado** |
| **GitHub Desktop** | Fazer commit, push e branches sem linha de comando | ATU-07 | Em uso pelo grupo |
| **Trello** | Quadro de tarefas, com um cartão por tarefa do [plano](plano-de-implementacao.md) (T-00 a T-47) | A13 p.6 | A criar (T-02) |
| **GitHub Pages** | **Opcional:** publicar a documentação como site. **Não** hospeda a aplicação, porque não roda Java nem MySQL | DEC-31 | Opcional |

## 4. Execução e operação do protótipo

| Ferramenta | Para que usamos | Decisão | Estado |
|---|---|---|---|
| **Computador da equipe** | Rodar a aplicação, o MySQL e a pasta de arquivos, tudo local | DEC-31; A13 p.3 | — |
| **Navegadores** | Chrome, Edge e Firefox (duas últimas versões). O Safari no iOS saiu junto com a consulta do responsável | DEC-28 (G3), DEC-37 | — |
| **mysqldump** | Copiar o banco no script de backup (`scripts/backup.ps1` e `backup.sh`) | DEC-30 | Planejada (T-47) |

## 5. Apoio de inteligência artificial

| Ferramenta | Para que | Registro |
|---|---|---|
| **Claude Code** | Gerar o código a partir destes Markdown, seguindo o [`CLAUDE.md`](../CLAUDE.md). Todo código passa por revisão no pull request | [`registro-de-ia.md`](registro-de-ia.md) §3 |
| **Claude (claude.ai)** | Analisar as atividades e produzir esta documentação | [`registro-de-ia.md`](registro-de-ia.md) §2 |
| **Gemini e ChatGPT** | Usados pelo grupo em atividades anteriores (A02, A05 e A07 a A14; a A12 não foi analisada) | [`registro-de-ia.md`](registro-de-ia.md) §1 |

## 6. O que **não** usamos

| Item | Por quê | Decisão |
|---|---|---|
| Leitor USB de QR Code | O grupo não tem o equipamento. A leitura é simulada pelas telas | DEC-25 |
| Catraca e sensor de giro | Integração física fora do MVP. O giro é simulado por um botão | DEC-18 |
| Provedor de e-mail (SMTP) | O envio de e-mail é sempre simulado | DEC-32 |
| WhatsApp | Não é usado, nem simulado: os pais não têm conta e não recebem senha | DEC-37 |
| Hospedagem externa e domínio | O projeto é acadêmico e roda local | DEC-31 |
| Framework de SPA ou outro banco | Fora da stack decidida | RNF08 |
