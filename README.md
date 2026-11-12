# Furin Access

> **Identificação simples, acesso seguro!**
> Projeto Integrador da equipe FURIN — TADS, Centro Universitário Senac Santo Amaro, 2026.

Este guia ensina a colocar o sistema para rodar no seu computador e a usá-lo, tela por
tela. Ele foi escrito para quem nunca viu o projeto. O estado detalhado do trabalho, as
convenções e o índice de todos os documentos estão em
[`docs/README-documentacao.md`](docs/README-documentacao.md).

## Sumário

1. [O que é o Furin Access](#1-o-que-é-o-furin-access)
2. [O que precisa ter instalado](#2-o-que-precisa-ter-instalado)
3. [Como rodar](#3-como-rodar)
4. [Como usar, tela por tela](#4-como-usar-tela-por-tela)
5. [Roteiro de demonstração em 5 minutos](#5-roteiro-de-demonstração-em-5-minutos)
6. [Problemas comuns e soluções](#6-problemas-comuns-e-soluções)
7. [Documentação do projeto](#7-documentação-do-projeto)

## 1. O que é o Furin Access

O Furin Access é uma aplicação web que registra a entrada e a saída dos alunos de uma
escola pública. Só os **funcionários** da escola têm conta, todos com o mesmo perfil
`FUNCIONARIO`: o ATE e a Gestão Escolar são cargos diferentes, mas no sistema usam
todas as telas (DEC-37). O **aluno** só apresenta a carteirinha com QR Code na portaria.
Os **pais ou responsáveis** só recebem um e-mail a cada entrada e saída, sem conta nem
tela; no protótipo esse e-mail é **simulado** (nenhuma mensagem sai do computador,
DEC-32). O grupo não tem leitor de QR Code nem catraca, então, no protótipo, a
**leitura da carteirinha** (DEC-25) e a **passagem pela catraca** (DEC-18) também são
simuladas por botões na tela, sempre marcados com "(protótipo)".

## 2. O que precisa ter instalado

| Programa | Versão | Como conferir (no terminal) | O que deve aparecer |
|---|---|---|---|
| Java (JDK) | 21 | `java -version` | uma linha com `21` (por exemplo, `openjdk version "21.0.5"`) |
| MySQL Server | 8.4 | `mysql --version` | uma linha com `8.4` (por exemplo, `Ver 8.4.3`) |
| Git (opcional) | qualquer | `git --version` | só se for baixar com `git clone` |

O **Maven não precisa ser instalado**: o projeto traz o Maven Wrapper (`mvnw` e
`mvnw.cmd`), que baixa a versão certa do Maven na primeira execução. Para isso, a
primeira execução precisa de internet.

Precisa ser o **JDK** (o kit de desenvolvimento), não só o "Java" de navegador. Uma
opção gratuita é o Eclipse Temurin 21, em `https://adoptium.net`. O MySQL 8.4 LTS
(*MySQL Community Server*) está em `https://dev.mysql.com/downloads/mysql/`.

Não tem permissão de administrador no Windows? Veja
[3.8 Alternativa sem permissão de administrador](#38-alternativa-sem-permissão-de-administrador-windows).

## 3. Como rodar

Os comandos aparecem primeiro para o **Windows (Prompt de Comando, `cmd`)** e depois
para **Linux e macOS (Terminal)**. Rode todos eles **na pasta do projeto**, a que tem o
arquivo `pom.xml`.

### 3.1 Baixar o projeto

**Com o Git:**

```bat
git clone https://github.com/LuisEduardo77/PI---Furin.git
cd PI---Furin
```

**Sem o Git:** abra `https://github.com/LuisEduardo77/PI---Furin`, clique em **Code** →
**Download ZIP**, extraia o arquivo e entre na pasta extraída (ela se chama
`PI---Furin-main`):

```bat
cd %USERPROFILE%\Downloads\PI---Furin-main
```

No Linux ou macOS, o `git clone` é igual. Para o ZIP: `cd ~/Downloads/PI---Furin-main`.

### 3.2 Criar o banco e o usuário

O script [`scripts/criar-usuario-banco.sql`](scripts/criar-usuario-banco.sql) cria dois
bancos, `furin` (o do protótipo) e `furin_test` (o dos testes automatizados), e o
usuário `furin`, com acesso só a esses dois bancos. Rode **uma vez por computador**, com
o MySQL ligado e com o usuário administrador do MySQL (`root`):

**Windows e Linux/macOS (o comando é o mesmo):**

```bat
mysql -u root -p --table -e "source scripts/criar-usuario-banco.sql"
```

Digite a senha do `root` do MySQL quando ela for pedida. No fim, aparece uma tabela com a
coluna **`generated password`**: é a senha do usuário `furin`, **gerada pelo MySQL e
mostrada uma única vez**. Copie essa senha para um lugar seguro: ela vai para o arquivo
do próximo passo.

Perdeu a senha? Gere outra (com o `root`) e use a nova:

```bat
mysql -u root -p --table -e "ALTER USER 'furin'@'localhost' IDENTIFIED BY RANDOM PASSWORD"
```

[print: tabela com a coluna generated password]

### 3.3 Criar o `application-local.yml`

As senhas ficam num arquivo só seu, o `application-local.yml`, na pasta do projeto. Esse
arquivo está no `.gitignore` e **nunca vai para o Git**. Comece pelo modelo:

**Windows:**

```bat
copy application-local.yml.example application-local.yml
notepad application-local.yml
```

**Linux/macOS:**

```bash
cp application-local.yml.example application-local.yml
nano application-local.yml
```

Deixe o arquivo assim, trocando cada `TROQUE_AQUI` pelo valor real (sem aspas e sem o
`#` no começo da linha):

```yaml
spring:
  datasource:
    password: TROQUE_AQUI        # a "generated password" do passo 3.2

FURIN_SENHA_ATE_DEMO: TROQUE_AQUI     # senha que você escolhe para a conta ate.demo
FURIN_SENHA_GESTAO_DEMO: TROQUE_AQUI  # senha que você escolhe para a conta gestao.demo
```

- As duas senhas das contas de demonstração são **inventadas por você** e precisam ter
  **pelo menos 8 caracteres**. Sem elas, a aplicação não sobe.
- Elas só são usadas **na primeira vez** que o sistema sobe com o banco vazio: nesse
  momento, a carga fictícia cria as contas `ate.demo` e `gestao.demo` com essas senhas.
  Mudar o arquivo depois não muda as contas (para recomeçar, veja a seção 3.6).
- Em vez do arquivo, os mesmos valores podem vir das variáveis de ambiente
  `SPRING_DATASOURCE_PASSWORD`, `FURIN_SENHA_ATE_DEMO` e `FURIN_SENHA_GESTAO_DEMO`.

### 3.4 Subir o sistema (perfil `prototipo`)

Com o MySQL ligado, na pasta do projeto:

**Windows:**

```bat
mvnw spring-boot:run -Dspring-boot.run.profiles=prototipo
```

**Linux/macOS:**

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=prototipo
```

O `prototipo` já é o perfil padrão, então `mvnw spring-boot:run` sozinho também funciona.
É nesse perfil que existem as simulações (leitura, catraca e e-mail) e os dados
fictícios.

A primeira vez demora mais (o Maven baixa as dependências). O sistema está pronto quando
o terminal mostra uma linha parecida com `Started FurinAccessApplication in 8.5 seconds`.
Na primeira partida, o sistema também cria as tabelas do banco e grava os dados
fictícios (seção 4). Deixe essa janela aberta enquanto usar o sistema.

Abra no navegador: **<http://localhost:8080/login>**

[print: tela de login]

### 3.5 Desligar

Na janela onde o sistema está rodando, tecle **Ctrl+C**. No Windows, se aparecer
"Deseja finalizar o arquivo em lotes (S/N)?", digite `S` e Enter. Os dados continuam
gravados no banco para a próxima vez.

### 3.6 Recomeçar o banco do protótipo

Ainda **não há um script de reset**. Para voltar aos dados fictícios do início (por
exemplo, antes de uma apresentação), com o sistema **desligado** (se quiser guardar os
dados atuais, faça antes um backup, seção 3.10):

1. Apague e recrie o banco `furin` com o `root` (o comando é o mesmo no Windows e no
   Linux/macOS). **Isso apaga todos os registros do protótipo e não tem volta.** O banco
   dos testes (`furin_test`) e o usuário `furin` não são afetados.

   ```bat
   mysql -u root -p -e "DROP DATABASE furin; CREATE DATABASE furin CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
   ```

2. Confira as senhas das contas de demonstração no `application-local.yml` (elas valem de
   novo, porque a carga fictícia vai rodar outra vez).
3. Suba o sistema de novo (seção 3.4). As tabelas e os dados fictícios são recriados, e as
   carteirinhas fictícias recebem **códigos novos** (as impressas antes deixam de valer).

As fotos e os termos ficam fora do projeto, na pasta `furin-access/arquivos` dentro da sua
pasta de usuário (`%USERPROFILE%\furin-access\arquivos` no Windows,
`~/furin-access/arquivos` no Linux/macOS). Depois do reset, os arquivos antigos ficam
sobrando nessa pasta; se quiser, apague-os com o sistema desligado.

### 3.7 Rodar os testes automatizados

Os testes usam o banco `furin_test` e a mesma senha do `application-local.yml`. Com o
MySQL ligado:

**Windows:**

```bat
mvnw -B verify
```

**Linux/macOS:**

```bash
./mvnw -B verify
```

O resultado esperado, no fim, é `BUILD SUCCESS`. São centenas de testes, e a execução
leva alguns minutos. Os relatórios ficam em `target/surefire-reports/`.

### 3.8 Alternativa sem permissão de administrador (Windows)

Dá para usar o JDK e o MySQL em arquivo **ZIP**, sem instalar nada. O exemplo usa a pasta
`%USERPROFILE%\ferramentas`; ajuste os nomes das pastas para os que aparecerem depois de
extrair.

**JDK em ZIP**

1. Em `https://adoptium.net`, baixe o **Temurin 21 (JDK)** para Windows x64 no formato
   **.zip** e extraia em `%USERPROFILE%\ferramentas`. Renomeie a pasta extraída (algo
   como `jdk-21.0.5+11`) para `jdk-21`.
2. Em **cada** janela do Prompt de Comando que for usar o projeto, rode antes:

   ```bat
   set "JAVA_HOME=%USERPROFILE%\ferramentas\jdk-21"
   set "PATH=%JAVA_HOME%\bin;%PATH%"
   java -version
   ```

**MySQL em ZIP**

1. Em `https://dev.mysql.com/downloads/mysql/`, escolha **MySQL Community Server 8.4
   LTS**, Windows (x86, 64-bit), **ZIP Archive**, e extraia em
   `%USERPROFILE%\ferramentas`. Renomeie a pasta extraída para `mysql-8.4`.
2. Só na primeira vez, prepare a pasta de dados. O `root` fica sem senha por enquanto:

   ```bat
   cd /d %USERPROFILE%\ferramentas\mysql-8.4\bin
   mysqld --initialize-insecure --console
   ```

3. Ligue o servidor e **deixe essa janela aberta** (ela é o MySQL rodando):

   ```bat
   cd /d %USERPROFILE%\ferramentas\mysql-8.4\bin
   mysqld --console
   ```

4. Em **outra** janela, ponha o MySQL no caminho e defina uma senha para o `root`
   (escolha a sua no lugar de `TROQUE_AQUI`):

   ```bat
   set "PATH=%USERPROFILE%\ferramentas\mysql-8.4\bin;%PATH%"
   mysql -u root --skip-password -e "ALTER USER 'root'@'localhost' IDENTIFIED BY 'TROQUE_AQUI'"
   ```

5. Nessa mesma janela, siga a partir da seção 3.2.
6. Para desligar o MySQL: `mysqladmin -u root -p shutdown` (ou Ctrl+C na janela do
   `mysqld`).

O MySQL para Windows precisa do *Microsoft Visual C++ Redistributable*. Se o `mysqld`
reclamar de um arquivo como `VCRUNTIME140.dll` ou `MSVCP140.dll`, esse pacote falta no
computador, e a instalação dele precisa de alguém com permissão de administrador.

### 3.9 Linux e macOS: instalação dos programas

- **macOS (Homebrew):** `brew install openjdk@21 mysql@8.4` e, para ligar o MySQL,
  `brew services start mysql@8.4`. Siga as instruções que o Homebrew mostra para pôr o
  `java` e o `mysql` no caminho.
- **Linux:** instale o JDK 21 pelo gerenciador de pacotes da distribuição (por exemplo,
  o pacote `openjdk-21-jdk` no Ubuntu) e o MySQL 8.4 pelo repositório oficial do MySQL
  (*MySQL APT Repository* ou *MySQL Yum Repository*), porque muitas distribuições trazem
  outra versão.

### 3.10 Backup

Faça um backup **antes de cada demonstração e no fim de cada dia de testes** (DEC-30). O
backup copia o banco `furin` (`furin.sql`) e a pasta de fotos e termos (`arquivos.zip`)
para uma pasta com data e hora, guarda as **7 cópias mais recentes** e apaga as mais
antigas. Ele tem dados pessoais: fica **fora do projeto**, na pasta indicada pela variável
`FURIN_BACKUP_DIR`, e o script se recusa a rodar se ela estiver dentro do repositório.

**Uma vez por computador:** crie o usuário só de leitura `furin_backup` (a senha aparece
uma única vez, na coluna `generated password`) e a pasta dos backups, por exemplo
`%USERPROFILE%\furin-backups`:

```bat
mysql -u root -p --table -e "source scripts/criar-usuario-backup.sql"
```

**A cada backup, no Windows (PowerShell):**

```powershell
$env:FURIN_BACKUP_DIR = "$env:USERPROFILE\furin-backups"
powershell -ExecutionPolicy Bypass -File scripts\backup.ps1
```

**No Linux/macOS** (precisa do programa `zip`):

```bash
export FURIN_BACKUP_DIR="$HOME/furin-backups"
bash scripts/backup.sh
```

Rode um comando por vez. O script pergunta a senha do `furin_backup`: cole a `generated
password` só nesse momento. Ela não entra em nenhum comando e não aparece na tela (sem
terminal, ela vem da variável `FURIN_BACKUP_DB_PASSWORD`).

Como testar a restauração num banco vazio e como restaurar de verdade:
[`docs/backup-e-restauracao.md`](docs/backup-e-restauracao.md).

## 4. Como usar, tela por tela

### Dados fictícios do protótipo

Na primeira partida, o sistema grava só dados inventados:

| O quê | Dados |
|---|---|
| Contas | `ate.demo` (ATE Demo) e `gestao.demo` (Gestão Demo), com as senhas do seu `application-local.yml` |
| Turmas | 3B e 6A, turno da manhã |
| Alunos | **Ana Teste** (matrícula 000123, 6A) e **Davi Teste** (000124, 3B), irmãos, com a responsável Carla Teste (`carla.teste@example.com`); **Bia Exemplo** (000200, 6A), com o responsável Bruno Exemplo (`bruno.pai@example.com`), que **pediu para não receber** o e-mail |
| Carteirinhas | uma válida para cada aluno, com código gerado na hora |

Todas as telas, menos a de login, têm o mesmo menu no topo: **Portaria · Alunos ·
Presentes · Histórico · Contas da equipe · Alterar senha · Sair**. O primeiro item da
página, que aparece com a tecla Tab, é o link **"Ir para o conteúdo principal"**.

### 4.1 Login

1. Abra <http://localhost:8080/login>.
2. Em **Usuário (obrigatório)**, digite `ate.demo` ou `gestao.demo`. As duas contas podem
   usar todas as telas.
3. Em **Senha (obrigatório)**, digite a senha que **você** definiu no
   `application-local.yml` (seção 3.3). Este guia não traz senhas: elas vêm da
   configuração local de cada computador.
4. Clique em **Entrar**. O sistema abre a tela **Portaria**.

O que pode aparecer:

- "Usuário ou senha inválidos. Confira os dados e tente novamente." — a mesma mensagem
  para usuário que não existe, senha errada e conta inativa.
- "Muitas tentativas sem sucesso. Aguarde alguns minutos e tente novamente." — depois de
  5 erros seguidos, o login fica bloqueado por 15 minutos.
- Depois de **30 minutos sem uso**, a sessão termina. Um minuto antes, aparece o aviso
  "Sua sessão vai expirar" com o botão **Continuar conectado**.

[print: tela de login]

### 4.2 Portaria

É a tela de trabalho do funcionário na entrada da escola. No topo, um aviso lembra:
"Protótipo: não há leitor nem catraca."

**Simulado nesta tela:** a leitura da carteirinha (painel "Simular leitura (protótipo)"),
a passagem pela catraca (botão "Simular passagem (giro)") e o e-mail ao responsável
(seção "Aviso ao responsável (protótipo)").

**Registrar uma entrada ou saída (leitura + passagem):**

1. No painel **Simular leitura (protótipo)**, clique em **Ler carteirinha de Ana Teste**.
   (Também dá para colar o código de uma carteirinha no campo **Código da carteirinha** e
   teclar Enter ou clicar em **Registrar leitura**: é o que o leitor USB faria.)
2. O **Painel do funcionário** mostra a foto, o nome, a turma, o tipo e a situação
   "Entrada liberada — aguardando passagem". O **Monitor do aluno** mostra só o primeiro
   nome: "Ana, entrada liberada. Pode passar."
3. Em **Catraca (protótipo)**, clique em **Simular passagem (giro)** em até 15 segundos.
4. A situação muda para "Entrada registrada às …", o contador **Presentes agora** aumenta
   e aparece a seção **Aviso ao responsável (protótipo)**, com "Simulação — nenhum e-mail
   foi enviado", o destinatário mascarado, o assunto e o texto do e-mail que teria saído.

O **tipo é sempre calculado pelo sistema**: não há botão de "entrada" ou "saída". Se Ana
entrou, a próxima leitura dela é uma **saída**.

**Se a passagem não acontecer:**

- Sem o giro em 15 segundos, a liberação é cancelada sozinha: "Liberação expirada — Ana
  não passou".
- Para cancelar antes, clique em **Cancelar liberação de Ana Teste** (o botão só aparece
  enquanto há uma liberação pendente). O painel mostra "Liberação de Ana cancelada".
- Uma liberação cancelada não gera registro nem e-mail.

**Mensagens que podem aparecer:**

| Mensagem no Painel do funcionário | Quando |
|---|---|
| Leitura já recebida | a mesma carteirinha foi lida de novo em menos de 2 segundos |
| Aguardando passagem | a carteirinha foi lida de novo com a liberação ainda pendente |
| Aguarde a passagem do aluno anterior | outro aluno foi lido antes de o anterior passar |
| Leitura recusada: Leitura não reconhecida. Tente novamente. | o texto lido não tem o formato de um código de carteirinha |
| Leitura recusada: Código não reconhecido. | o formato está certo, mas a carteirinha não existe |
| Leitura recusada: Carteirinha revogada. Emitir segunda via. | a carteirinha foi substituída por uma segunda via |
| Leitura recusada: Aluno inativo. | o aluno foi inativado |
| Leitura recusada: Saída pendente de dd/mm: registre a saída manual antes da nova entrada. | o aluno entrou num dia anterior e ficou sem saída |
| Leitor não está pronto. Clique no campo de leitura. | o cursor saiu do campo de leitura |

Nas recusas, o Monitor do aluno mostra sempre a mesma frase, sem o motivo: "Não foi
possível ler sua carteirinha. Tente de novo ou procure o funcionário da portaria."

**Registro manual** (quando a leitura não é possível, ou para fechar uma saída pendente):

1. Clique em **Registro manual**.
2. Em **Buscar aluno por nome ou matrícula**, digite parte do nome (pelo menos 2 letras)
   ou o começo da matrícula e clique em **Buscar**. Só aparecem alunos ativos.
3. Na linha do aluno, clique em **Registrar manualmente para Ana Teste**.
4. Confira o tipo, que o sistema calculou. Em **Data e hora reais da movimentação**, o
   campo já vem com o momento atual (mude se foi em outro horário, nunca no futuro). Em
   **Motivo**, escreva por que o registro é manual (por exemplo, "Aluno esqueceu a
   carteirinha").
5. Clique em **Revisar**, confira o resumo e clique em **Confirmar** (ou **Corrigir** para
   voltar).
6. Aparece "Entrada manual registrada para Ana às …" e, se o responsável recebe avisos, a
   seção **Aviso ao responsável (protótipo)** com o e-mail simulado.

[print: tela da portaria com uma liberação]

### 4.3 Alunos

Na tela **Alunos** ficam o botão **Novo aluno** e a busca.

**Buscar:**

1. Em **Buscar aluno por nome ou matrícula**, digite parte do nome (pelo menos 2 letras)
   ou o começo da matrícula, com os zeros à esquerda (por exemplo, `0001`).
2. Marque **Incluir inativos** se quiser ver também os alunos inativados.
3. Clique em **Buscar**. Aparecem até 20 alunos, com nome, matrícula, turma e situação.

**Cadastrar um aluno (com o termo de consentimento):**

1. Clique em **Novo aluno**. Todos os blocos são obrigatórios.
2. Bloco **Aluno**: **Nome do aluno**, **Matrícula** (não pode repetir a de outro aluno,
   nem de um inativo), **Turma** e **Foto do aluno** (JPEG ou PNG, até 2 MB). Use só
   imagens fictícias, nunca a foto de uma pessoa real.
3. Bloco **Contato principal**: **Nome do responsável**, **Telefone com DDD** e
   **E-mail**. Use e-mails `@example.com`.
4. Bloco **Termo de consentimento**: **Signatário**, **Versão do termo** (já vem
   preenchida) e **Data da assinatura**. Depois, informe o **Protocolo ou localização do
   termo físico** *ou* envie a **Cópia digitalizada do termo** (PDF, JPEG ou PNG, até
   5 MB). Por fim, marque **Confirmo que conferi o termo de consentimento assinado pelo
   responsável**.
5. Clique em **Salvar**. Se algo estiver errado, um resumo no topo lista cada erro, com
   link para o campo, e nada é salvo (a foto e o termo precisam ser escolhidos de novo).

**Carteirinha e impressão:** ao salvar, a carteirinha é emitida na hora e o sistema abre a
página **Carteirinha de …**, com "Aluno … cadastrado. Imprima a carteirinha." O cartão tem
o nome, a turma e o QR Code, no tamanho 85,6 × 54 mm. Use a impressão do navegador
(**Ctrl+P**): só o cartão sai no papel. O novo aluno também passa a ter um botão no painel
"Simular leitura (protótipo)" da portaria.

**Segunda via:**

1. Na página da carteirinha (ou em **Editar aluno**), clique em **Emitir segunda via**.
2. A tela avisa: "A carteirinha atual deixará de funcionar. Confirmar?" Clique em
   **Confirmar segunda via**.
3. A nova carteirinha abre para impressão. A antiga passa a ser recusada na portaria com
   "Carteirinha revogada. Emitir segunda via."

**Editar:**

1. Busque o aluno e clique em **Editar aluno Ana Teste**.
2. Mude o que precisar: dados do aluno, turma (o histórico antigo continua na turma
   anterior), **Nova foto (opcional)** e os dados do contato. Para trocar o responsável,
   marque **Substituir o contato principal por outra pessoa**.
3. Clique em **Revisar alterações**. Uma tabela mostra cada campo "Antes" e "Depois".
4. Clique em **Confirmar** (ou **Corrigir**). Aparece "Cadastro de Ana Teste atualizado."

**Aviso por e-mail:** na edição, a caixa **Enviar avisos de entrada e saída por e-mail ao
contato principal** liga ou desliga o aviso, só a pedido do responsável. Ao revisar,
o sistema pede confirmação ("Os avisos de … deixarão de ser enviados a …"). O e-mail é
sempre **simulado** no protótipo.

**Outras ações** (no fim da tela de edição): **Imprimir carteirinha**, **Emitir segunda
via**, **Registrar revogação do termo** (informe a data e clique em **Registrar
revogação**: o aluno é inativado e a carteirinha deixa de valer) e **Inativar aluno**
(clique em **Confirmar inativação**). Nada é apagado: o histórico do aluno continua.

[print: formulário Novo aluno]

### 4.4 Presentes

Mostra quantos alunos estão na escola agora, para apoiar a merenda.

- **Presentes agora**: alunos com entrada registrada hoje e sem saída. A tela avisa que
  "Este número não comprova presença em sala de aula."
- **Por turma**: todas as turmas ativas, inclusive as que têm zero.
- **Saídas pendentes**: entradas de dias anteriores que ficaram sem saída. O botão
  **Registrar saída de …** abre o registro manual já com a saída.
- A tela se atualiza sozinha a cada 30 segundos. Para atualizar na hora, clique em
  **Atualizar**.

### 4.5 Histórico e impressão

1. Abra **Histórico**. Ele mostra as entradas e saídas confirmadas de hoje. A tela é só
   de consulta: nada pode ser editado nela.
2. Para filtrar, use **Data inicial**, **Data final** (período de até 31 dias),
   **Turma** e **Aluno** (nome ou matrícula) e clique em **Filtrar**.
3. A tabela mostra data, hora, aluno, turma, tipo, forma (leitura ou manual),
   observação (o motivo dos registros manuais) e **Aviso ao responsável** ("Simulada",
   "Pendente" ou "Não enviado"). São 50 registros por página, com **Próxima página** e
   **Página anterior**.
4. Para imprimir, clique em **Versão para impressão** e depois em **Imprimir** (abre a
   impressão do navegador, que também salva em PDF). O relatório usa os mesmos filtros,
   aceita até 1.000 registros e traz o nome da escola configurado (no protótipo,
   "Escola Exemplo"). **Voltar ao histórico** retorna à consulta.

### 4.6 Alterar senha

1. Clique em **Alterar senha** no menu.
2. Preencha **Senha atual**, **Nova senha** (pelo menos 8 caracteres, diferente da atual)
   e **Confirme a nova senha**.
3. Clique em **Alterar senha**. Aparece "sua senha foi alterada."

A senha nova fica só no banco. O `application-local.yml` não muda, e a senha antiga dele
deixa de funcionar para essa conta (até um reset, seção 3.6).

### 4.7 Contas da equipe

Qualquer funcionário cria e gerencia as contas de quem usa o sistema. Todas têm o mesmo
acesso.

1. Clique em **Contas da equipe** no menu. A lista mostra nome, login, situação (Ativa ou
   Inativa) e a data de criação.
2. Para criar, clique em **Nova conta**, preencha **Nome** e **Login** (texto livre, por
   exemplo `secretaria.paula`; fica em minúsculas e não pode repetir) e clique em **Criar
   conta**.
3. A próxima tela mostra a **senha temporária** uma única vez (use **Mostrar a senha** ou
   **Copiar a senha**). Entregue-a à pessoa de forma reservada. Ela vale por 72 horas, e a
   pessoa precisa trocá-la no primeiro acesso (tela **Definir nova senha**). Recarregar ou
   voltar para essa página **não** mostra a senha de novo.
4. Na lista, cada conta tem **Gerar nova senha temporária** (a anterior deixa de valer),
   **Desativar** e, nas inativas, **Reativar** (que já gera uma nova senha temporária).
   Cada ação pede confirmação. Ninguém desativa a própria conta.

Uma conta desativada não entra mais, mas os registros feitos por ela continuam mostrando
o nome de quem os fez.

### 4.8 Sair

Clique em **Sair**, no fim do menu. O sistema volta para o login com "você saiu do
sistema."

### Em desenvolvimento

Ainda não está pronto (não aparece no protótipo ou aparece incompleto):

- **Script de reset** do banco do protótipo: por enquanto, o reset é manual (seção 3.6).
- **HTTPS**: o protótipo roda só em `http://localhost` (RNF04).
- **Medição de desempenho na tela** da portaria (RNF02): só o servidor foi medido.

Fora do escopo, de propósito (não serão feitos no protótipo): leitor USB, catraca e
envio real de e-mail, WhatsApp, contas para alunos e pais e perfis separados para ATE e
Gestão.

## 5. Roteiro de demonstração em 5 minutos

Para a demonstração completa (25 a 30 minutos), com o resultado esperado e os critérios
de aceitação de cada passo, use o
[roteiro da demonstração final](docs/evidencias/roteiro-demonstracao.md).

Antes de começar: sistema rodando (seção 3.4), de preferência com o banco recomeçado
(seção 3.6), e o navegador em <http://localhost:8080/login>.

1. **Login (30 s).** Entre com `ate.demo` e a senha do seu `application-local.yml`.
   Mostre o menu único: todos os funcionários usam as mesmas telas.
2. **Entrada com aviso (1 min).** Na Portaria, clique em **Ler carteirinha de Ana Teste**.
   Mostre o Painel do funcionário (foto, turma, "Entrada liberada — aguardando
   passagem") e o Monitor do aluno (só "Ana"). Clique em **Simular passagem (giro)**.
   Mostre "Entrada registrada", o contador **Presentes agora** e o **Aviso ao responsável
   (protótipo)** para Carla Teste, com "Simulação — nenhum e-mail foi enviado".
3. **Entrada sem aviso (30 s).** Clique em **Ler carteirinha de Bia Exemplo** e em
   **Simular passagem (giro)**. Não aparece o aviso: o responsável pediu para não
   receber e-mail.
4. **Expiração e uma liberação por vez (1 min).** Clique em **Ler carteirinha de Davi
   Teste** e logo em **Ler carteirinha de Ana Teste**: aparece "Aguarde a passagem do
   aluno anterior". Não clique no giro e espere 15 segundos: "Liberação expirada — Davi
   não passou".
5. **Saída e recusa (40 s).** Clique em **Ler carteirinha de Ana Teste**: agora o tipo é
   **Saída**, calculado pelo sistema. Clique em **Simular passagem (giro)**. Depois,
   digite `abc` no campo **Código da carteirinha** e tecle Enter: "Leitura recusada:
   Leitura não reconhecida. Tente novamente."
6. **Registro manual (40 s).** Clique em **Registro manual**, busque `Davi`, clique em
   **Registrar manualmente para Davi Teste**, escreva o motivo "Aluno esqueceu a
   carteirinha", clique em **Revisar** e em **Confirmar**.
7. **Presentes e histórico (40 s).** Abra **Presentes**: Davi aparece na turma 3B e Bia
   na 6A. Abra **Histórico**: estão as leituras, o registro manual com o motivo e a
   coluna **Aviso ao responsável** ("Simulada" para Ana e Davi, "Não enviado" para Bia).
   Clique em **Versão para impressão**.
8. **Sair.** Clique em **Sair**.

Se der tempo, mostre o cadastro em **Alunos** → **Novo aluno**, com dados fictícios
(por exemplo, "Caio Teste", responsável "Rita Teste", `rita.teste@example.com`, uma
imagem qualquer como foto e um protocolo fictício para o termo), até a página da
carteirinha.

## 6. Problemas comuns e soluções

| Problema | O que aparece | Solução |
|---|---|---|
| **MySQL desligado** | Ao subir, erros como `Communications link failure` ou `Connection refused`, e a aplicação para | Ligue o MySQL. No Windows com instalador, abra **Serviços** e inicie o serviço **MySQL84** (ou o nome escolhido na instalação). Com o ZIP, rode `mysqld --console` (seção 3.8). No macOS, `brew services start mysql@8.4`. No Linux, `sudo systemctl start mysql` (ou `mysqld`, conforme a distribuição) |
| **`java` não é reconhecido** | "'java' não é reconhecido como um comando interno ou externo" ou `JAVA_HOME not found` ao rodar o `mvnw` | Instale o JDK 21 ou, com o ZIP, rode os dois comandos `set` da seção 3.8 na mesma janela. Confira com `java -version`. Se aparecer outra versão (por exemplo, 17), o `JAVA_HOME` e o `PATH` apontam para outro Java |
| **`mysql` não é reconhecido** | "'mysql' não é reconhecido…" ao rodar o script do banco | Ponha a pasta `bin` do MySQL no `PATH` (seção 3.8, passo 4) ou rode o comando de dentro dessa pasta, com o caminho completo do script |
| **Porta 8080 ocupada** | `Port 8080 was already in use` | Feche o outro programa (pode ser outra cópia do Furin Access aberta em outra janela) ou mude a porta: acrescente `server:` e, na linha de baixo, `  port: 8081` ao `application-local.yml` e abra `http://localhost:8081/login` |
| **`application-local.yml` ausente ou incompleto** | `Access denied for user 'furin'@'localhost'` ou "Carga fictícia: defina a variável de ambiente FURIN_SENHA_ATE_DEMO…" | Crie o arquivo na pasta do projeto, ao lado do `pom.xml` (seção 3.3), com a senha do usuário `furin` e as duas senhas de demonstração (pelo menos 8 caracteres). Confira se o nome não ficou `application-local.yml.txt` (no Windows, ative "Extensões de nomes de arquivos" no Explorador) |
| **Senha das contas de demonstração não funciona** | "Usuário ou senha inválidos…" | As senhas só valem na primeira partida com o banco vazio. Se você mudou o `application-local.yml` depois, ou alterou a senha pela tela, use a senha atual ou recomece o banco (seção 3.6). Depois de 5 erros, espere 15 minutos |
| **"Leitura não reconhecida"** | "Leitura recusada: Leitura não reconhecida. Tente novamente." | O campo recebeu algo que não é um código de carteirinha (o código tem 32 letras e números, de 0 a 9 e de a a f). Use os botões **Ler carteirinha de …** do painel "Simular leitura (protótipo)". Se a mensagem for "Código não reconhecido.", o código tem o formato certo mas não existe neste banco (por exemplo, de uma carteirinha impressa antes de um reset) |
| **Testes falham ao conectar** | Erros de conexão no `mvnw -B verify` | O MySQL precisa estar ligado e o banco `furin_test` criado pelo script da seção 3.2, com a senha do usuário `furin` no `application-local.yml` |

## 7. Documentação do projeto

- [`docs/README-documentacao.md`](docs/README-documentacao.md): índice de toda a
  documentação, estado do trabalho, significado dos estados de cada requisito
  (Planejada, Simulada, Implementada, Verificada) e convenções.
- [`requirements/rf/`](requirements/rf/): requisitos funcionais (RF01 a RF26).
- [`requirements/rnf/`](requirements/rnf/): requisitos não funcionais (RNF01 a RNF08).
- [`docs/regras-de-negocio.md`](docs/regras-de-negocio.md): regras de negócio (RN01 a
  RN17).
- [`docs/arquitetura-e-dados.md`](docs/arquitetura-e-dados.md): arquitetura, rotas,
  parâmetros e modelo de dados.
- [`docs/plano-de-implementacao.md`](docs/plano-de-implementacao.md): plano de
  implementação (tarefas T-00 a T-47).
- [`docs/rastreabilidade.md`](docs/rastreabilidade.md): decisões do grupo (DEC) e
  rastreabilidade.
- [`docs/registro-de-ia.md`](docs/registro-de-ia.md): registro do uso de IA.

## Equipe

Adriane Helfstein de Jesus · Gabriel Eiji Shiquedoni · Hugo Rocha da Silva ·
Luis Eduardo Belo Silva
