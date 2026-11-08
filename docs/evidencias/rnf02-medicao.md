# RNF02 — Medição do tempo de resposta da portaria (T-33)

Evidência do [RNF02](../../requirements/rnf/RNF02.md): a resposta à leitura e ao giro
simulado aparece em **até 2 s** (CA-RNF02-01 a 04), com a carga de referência de **800
alunos fictícios e 30 dias de movimentações** e uma rodada de **30 leituras seguidas**
(DEC-28, G4).

Há duas medições, que se completam:

| Medição | O que mede | Quem roda | Situação |
|---|---|---|---|
| 1. No servidor (`DesempenhoDaPortariaTests`, INT) | O pedido dentro da aplicação, sem rede e sem navegador | Toda execução de `./mvnw -B verify`, local e na CI | **Feita** (08/11/2026, abaixo) |
| 2. Na tela (`scripts/desempenho/medir-portaria.js`, MAN) | Do clique no painel de simulação até a mensagem nos dois painéis, como pede o CA-RNF02-01 | O grupo, no ambiente de demonstração | **A fazer** |

A medição 1 **não substitui** a 2: a CI não mede a tela (RNF02, "Verificação"). O
CA-RNF02-01 e o CA-RNF02-02 só ficam atendidos com a medição 2.

## Carga de referência

Arquivos em [`scripts/desempenho/`](../../scripts/desempenho/). Nenhum deles faz parte da
carga padrão (não são migrações do Flyway):

| Arquivo | Para quê |
|---|---|
| `criar-banco-desempenho.sql` | Cria o banco `furin_desempenho` e dá acesso ao usuário `furin` |
| `carga-800-alunos.sql` | 800 alunos fictícios ("Aluno Desempenho 001" a "800", matrículas `DES0001` a `DES0800`), 20 turmas `DES-01` a `DES-20`, uma carteirinha válida por aluno e 30 dias de movimentações confirmadas (ENTRADA e SAIDA por dia: 48 000 registros) |
| `apagar-carga-800-alunos.sql` | Tira a carga, para repetir a medição do zero |
| `medir-portaria.js` | A medição na tela, colada no Console do navegador |

## 1. Medição no servidor (feita)

`DesempenhoDaPortariaTests` grava a carga no banco de testes, faz 30 leituras (cada uma de
um aluno diferente, de 26 em 26 matrículas) e o giro de cada liberação, mede o tempo de
cada pedido e falha se algum passar de `furin.portaria.tempo-resposta-max` (2 s). No fim,
apaga a carga e escreve o resultado em `target/rnf02-medicao-servidor.md`.

**Ambiente:** notebook de desenvolvimento do integrante, Windows 11, Java 21.0.12.1, MySQL
8.4 local, aplicação e banco na mesma máquina, perfil `test` + `prototipo`. Executado em
08/11/2026 às 12:28 (horário de Brasília).

| # | Leitura (ms) | Giro (ms) |
|---|---|---|
| 1 | 446,0 | 24,9 |
| 2 | 15,7 | 11,6 |
| 3 | 13,4 | 10,6 |
| 4 | 24,8 | 12,5 |
| 5 | 11,0 | 8,7 |
| 6 | 11,9 | 8,3 |
| 7 | 11,3 | 8,6 |
| 8 | 10,9 | 8,5 |
| 9 | 10,8 | 7,8 |
| 10 | 9,9 | 8,4 |
| 11 | 10,8 | 7,9 |
| 12 | 10,8 | 8,7 |
| 13 | 10,8 | 8,3 |
| 14 | 10,6 | 8,5 |
| 15 | 10,3 | 7,9 |
| 16 | 9,4 | 8,4 |
| 17 | 9,2 | 7,7 |
| 18 | 8,8 | 7,3 |
| 19 | 10,8 | 7,3 |
| 20 | 9,5 | 6,6 |
| 21 | 9,1 | 6,8 |
| 22 | 9,4 | 6,8 |
| 23 | 8,3 | 6,6 |
| 24 | 8,1 | 7,7 |
| 25 | 9,7 | 8,8 |
| 26 | 9,8 | 7,4 |
| 27 | 8,1 | 6,9 |
| 28 | 7,9 | 6,0 |
| 29 | 8,6 | 6,7 |
| 30 | 8,2 | 6,0 |
| **Máximo** | **446,0** | **24,9** |
| **Média** | **25,1** | **8,6** |

A primeira leitura inclui o aquecimento da aplicação (primeira chamada do caminho). Mesmo
ela fica abaixo de 2 s. **Resultado: todas as 30 leituras e os 30 giros dentro de 2 s no
servidor.**

## 2. Medição na tela (a fazer pelo grupo)

Precisa de uma conta de funcionário. A IA não entrou no sistema com as senhas de
demonstração, por isso esta parte fica para o grupo.

1. Uma vez por computador, crie o banco (com um usuário administrador do MySQL):

   ```bash
   mysql -u root -p -e "source scripts/desempenho/criar-banco-desempenho.sql"
   ```

2. Suba o protótipo apontando para esse banco, para o Flyway criar as tabelas e a carga
   fictícia V2 criar as contas `ate.demo` e `gestao.demo` (com as senhas de
   `FURIN_SENHA_ATE_DEMO` e `FURIN_SENHA_GESTAO_DEMO`). No PowerShell:

   ```bash
   $env:SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/furin_desempenho"; $env:SPRING_PROFILES_ACTIVE="prototipo"; ./mvnw spring-boot:run
   ```

   No Linux ou macOS: `SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/furin_desempenho SPRING_PROFILES_ACTIVE=prototipo ./mvnw spring-boot:run`.

3. Em outro terminal, grave a carga de referência:

   ```bash
   mysql -u furin -p furin_desempenho < scripts/desempenho/carga-800-alunos.sql
   ```

4. No navegador da demonstração (G3: Chrome, Edge ou Firefox atual), entre em
   `http://localhost:8080/login` com `ate.demo` e abra `/portaria`.
5. Abra as ferramentas do navegador (F12), aba **Console**. Cole o conteúdo de
   `scripts/desempenho/medir-portaria.js` e tecle Enter. Não mexa na tela até aparecer
   "Medição concluída" (cerca de 1 minuto).
6. O script mostra a tabela, o máximo e a média, e copia a tabela em Markdown para a área
   de transferência (no Chrome e no Edge). Cole-a na seção abaixo, com o ambiente.
7. Para repetir do zero: `mysql -u furin -p furin_desempenho < scripts/desempenho/apagar-carga-800-alunos.sql`
   e depois o passo 3 de novo.

O script clica em "Ler carteirinha de Aluno Desempenho …" e espera, quadro a quadro, até o
painel do funcionário mostrar "aguardando passagem" e o monitor do aluno mostrar "Pode
passar"; depois clica em "Simular passagem (giro)" e espera "registrada" nos dois. Assim, o
tempo vai do clique até a mensagem nos dois painéis (CA-RNF02-01 e 04).

### Resultado na tela

> **Pendente.** Preencher com a tabela do script e o ambiente: máquina, sistema,
> navegador e versão, data e hora.

## Situação dos critérios

| Critério | Situação |
|---|---|
| CA-RNF02-01 | Pendente da medição na tela |
| CA-RNF02-02 | Carga e rodada prontas; no servidor, 30 de 30 dentro de 2 s. Pendente da medição na tela |
| CA-RNF02-03 | Atendido no código: a resposta da leitura não espera notificação (ainda não existe; será depois do commit, RF21) e o texto aparece antes da foto, que carrega à parte (DEC-28, A9; `PortariaTelaTests`) |
| CA-RNF02-04 | No servidor, 30 de 30 giros dentro de 2 s. Pendente da medição na tela |
