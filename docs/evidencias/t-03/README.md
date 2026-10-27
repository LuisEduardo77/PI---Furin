# T-03: validação do DDL

**Executado em:** 27/10/2026 · **Servidor:** MySQL 8.4.13 local · **Resultado:** DDL
executado sem erro e 86 de 86 casos de teste aprovados

Evidência da tarefa T-03 do [plano de implementação](../../plano-de-implementacao.md):
o DDL de referência de [`arquitetura-e-dados.md` §5.5](../../arquitetura-e-dados.md)
foi executado em um banco descartável (`furin_ddl_teste`), e as restrições foram
testadas com INSERTs válidos e inválidos. Só dados fictícios.

| Arquivo | Conteúdo |
|---|---|
| [`validar-ddl.sql`](validar-ddl.sql) | Recria `furin_ddl_teste`, executa o DDL da §5.5 (linhas 378–547, copiadas sem alteração em 27/10/2026) e lista as tabelas e restrições |
| [`testar-restricoes.sql`](testar-restricoes.sql) | Roda o `validar-ddl.sql` e depois 86 casos: cada um informa o código de erro esperado (0 = deve passar) e o obtido |
| [`resultado-2026-10-27.txt`](resultado-2026-10-27.txt) | Saída do cliente MySQL na execução de 27/10/2026 |

## Como repetir

Na raiz do repositório, com um usuário que possa criar e apagar o banco
`furin_ddl_teste` (a senha é digitada no prompt, nunca no comando):

```bash
mysql -u root -p --table -e "source docs/evidencias/t-03/testar-restricoes.sql"
```

O resultado esperado é 86 `PASSOU` e `OK` na última conferência.

Códigos de erro usados: 3819 (CHECK violado), 1062 (UNIQUE duplicado), 1452 (FK sem
registro pai), 1048 (NOT NULL) e 1406 (valor longo demais).

## O que foi coberto

- **Casos válidos (24):** o DDL aceita o que as regras permitem, como o registro manual de
  entrada e saída (DEC-03), o cancelamento `SEM_GIRO` e pelo `FUNCIONARIO`, uma nova
  pendente depois do cancelamento, irmãos com o mesmo responsável e um contato principal
  inativo ao lado do ativo.
- **CHECKs (42):** perfil só `FUNCIONARIO` (DEC-37), canal só `EMAIL`, textos em branco,
  booleanos fora de 0 e 1, combinações de tipo, forma e situação, horários de leitura e
  de lançamento manual, períodos de vínculo, carteirinha e consentimento.
- **UNIQUEs (11):** login (inclusive em maiúsculas), turma, matrícula, vínculo, código da
  carteirinha, um contato principal ativo por aluno, uma movimentação pendente por
  vínculo (RN02), `chave_requisicao` (RN08) e notificação por registro e contato.
- **FKs, NOT NULL e tamanho (9).**

## Limites

- As regras que envolvem várias tabelas ficam nos serviços (§5.5, "Observações") e não
  são testadas aqui.
- Os scripts usam uma cópia do DDL. Se a §5.5 mudar, copie de novo e rode outra vez. A
  migração Flyway da T-06 parte deste DDL validado.
- O usuário do banco exclusivo do projeto (sem `root`), previsto na T-03, ainda não foi
  criado.
