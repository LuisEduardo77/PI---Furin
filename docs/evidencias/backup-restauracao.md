# Evidência: teste de restauração do backup (T-47)

**Data:** 12/11/2026 · **Decisão:** DEC-30 · **Arquitetura:** §6.1 · **Passo a passo:**
[`docs/backup-e-restauracao.md`](../backup-e-restauracao.md) §3

## Ambiente

- Windows 11, Windows PowerShell 5.1, MySQL 8.4 (serviço `MySQL84`), banco do protótipo
  `furin` com a carga fictícia (só dados fictícios).
- Backup feito com `scripts/backup.ps1`, pelo usuário só de leitura `furin_backup`
  (`SELECT`, `SHOW VIEW` e `TRIGGER` em `furin.*`), com `FURIN_BACKUP_DIR` fora do
  repositório (`%USERPROFILE%\furin-backups`). A senha foi pedida pelo script, sem
  aparecer na tela nem em comando.
- Restauração num banco **vazio** criado para o teste, `furin_restaurado`, com o `root`,
  carregando o `furin.sql` da cópia. Conferência com `scripts/conferir-restauracao.sql`.
  Feita por Luis Eduardo Belo Silva; resultado lido nas telas do terminal.

## Resultado

**Banco: todas as tabelas iguais.**

| Tabela | Original (`furin`) | Restaurado (`furin_restaurado`) | Resultado |
|---|---:|---:|---|
| usuario | 2 | 2 | igual |
| turma | 2 | 2 | igual |
| aluno | 3 | 3 | igual |
| vinculo_turma | 3 | 3 | igual |
| carteirinha | 3 | 3 | igual |
| contato_responsavel | 3 | 3 | igual |
| consentimento | 3 | 3 | igual |
| registro_acesso | 5 | 5 | igual |
| notificacao | 0 | 0 | igual |
| flyway_schema_history | 2 | 2 | igual |

`CHECKSUM TABLE`: o mesmo valor nos dois bancos para `usuario`, `aluno`,
`contato_responsavel`, `registro_acesso` e `notificacao` (esta vazia, checksum 0).

**Fotos e termos: iguais.** O `arquivos.zip` extraído tinha 4 arquivos, o mesmo número
da pasta original (`%USERPROFILE%\furin-access\arquivos`). O `Compare-Object` do caminho
relativo e do hash SHA-256 de cada arquivo não mostrou diferença.

Depois da conferência, o banco `furin_restaurado` foi apagado.

## Limitações

- A tabela `notificacao` estava vazia, então ela foi conferida só na estrutura.
- O teste foi feito só no Windows. O `scripts/backup.sh` (Linux/macOS) passou pela
  verificação de sintaxe (`bash -n`), mas ainda não rodou num Linux ou macOS.
- Não foi feita a restauração "de verdade" (seção 4 do passo a passo), que substitui o
  banco `furin`; ela usa os mesmos comandos, com outro nome de banco.

## Ocorrências durante o teste

As três primeiras tentativas de backup falharam com `Access denied` ou com a senha vazia.
A causa foi a senha do `furin_backup` digitada no lugar errado (a senha do `root`, a senha
dentro do comando, ou várias linhas coladas de uma vez, que o `Read-Host` lê como
resposta). Por isso, os scripts passaram a pedir a senha eles mesmos, e a mensagem de erro
ganhou a dica. Como a senha apareceu em prints, ela foi trocada com
`ALTER USER … IDENTIFIED BY RANDOM PASSWORD` antes da tentativa que deu certo.
