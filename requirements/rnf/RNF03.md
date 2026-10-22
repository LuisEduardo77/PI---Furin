# RNF03 — Senhas armazenadas com hash, nunca em texto plano

| Campo | Valor |
|---|---|
| **Categoria** | Segurança |
| **Documentação** | Revisada em 21/10/2026 (DEC-37 a DEC-39) |
| **Implementação** | Planejada |
| **Atualizado em** | 21/10/2026 |

## Descrição

Todas as senhas são guardadas só como **hash com salt**, gerado por uma função própria
para senhas, pelos recursos do Spring Security. Isso vale para todas as contas, que são
todas do perfil `FUNCIONARIO` (DEC-37), e também para as senhas temporárias. Nenhuma senha aparece em
texto puro no banco, em logs, em mensagens, em respostas HTTP ou no repositório.

## Justificativa

Se o banco vazar, as senhas não podem ser recuperadas, o que protege as contas dos
funcionários. A A09 trata disso como requisito legal (LGPD art. 46)
e a A10 como controle de segurança (ISO/IEC 27002 §5.17).

## Fonte documental

| Origem | Conteúdo |
|---|---|
| A08 p.4 (RNF03) | "Senhas armazenadas com hash, nunca em texto plano" — origem: Atividades 9 e 10 |
| A09 p.2 (LGPD art. 46) | "Não deve manter as senhas dos usuários (funcionários) gravadas explicitamente em texto plano no banco de dados (deve utilizar criptografia/hash)" |
| A10 p.2 §5.17 | Função de hash própria para senhas, com salt, pelo Spring Security. Segredos fora do GitHub. Fora de logs e mensagens de diagnóstico |
| A14 p.4 (`usuario.senha_hash`) | Hash com parâmetros e salt. "Nunca guardar senha em texto puro" |
| **ATU-04** | Incluía as contas de responsáveis (substituída pela DEC-37: só há contas de funcionários) |
| **DEC-21** | Senha temporária gravada só como hash |

**Alteração em relação à A08:** a A09 falava só de "funcionários". O requisito passa a
valer também para as senhas temporárias (DEC-21). As contas de responsáveis, previstas
pela ATU-04, saíram do escopo (DEC-37).

## Partes do sistema afetadas

| Parte | Como |
|---|---|
| `usuario.senha_hash` (VARCHAR(255)) | Guarda o hash com o identificador do algoritmo |
| Configuração do Spring Security | `PasswordEncoder` (DEC-28, G5): `PasswordEncoderFactories.createDelegatingPasswordEncoder()`, que usa bcrypt por padrão e permite trocar de algoritmo no futuro |
| Login (RF01), troca de senha, senha temporária (RF26) | Gravação e comparação sempre pelo `PasswordEncoder` |
| Carga inicial (V2) | As senhas dos usuários de demonstração vêm de **variáveis de ambiente** e são transformadas em hash na inicialização. Nunca ficam no repositório (A10 §5.17, §8.33) |
| Logs e mensagens | Mascaramento. Senhas e senhas temporárias nunca são registradas |

## Critérios de aceitação

| ID | Critério |
|---|---|
| CA-RNF03-01 | Depois de criar contas pelos dois caminhos (carga inicial e RF26) e de uma troca de senha (RF01), nenhuma linha de `usuario.senha_hash` contém a senha usada no teste. Todas começam com o prefixo do algoritmo (por exemplo, `{bcrypt}$2a$`) |
| CA-RNF03-02 | Duas contas com a mesma senha têm hashes **diferentes** (salt) |
| CA-RNF03-03 | O login confere a senha só pelo `PasswordEncoder.matches`, sem comparar texto |
| CA-RNF03-04 | Depois da suíte de testes, uma busca pela senha de teste nos arquivos de log não encontra nenhuma ocorrência |
| CA-RNF03-05 | Uma busca no repositório não encontra senhas, nem em `application*.yml`, nem em scripts SQL, nem em testes. Credenciais de teste vêm do ambiente ou são descartáveis da CI |
| CA-RNF03-06 | Nenhuma resposta HTTP, mensagem de erro ou página devolve a senha. A única exceção é a exibição única da senha temporária ao funcionário que criou a conta ou gerou a nova senha (RF26) |

## Verificação e evidência esperada

| Forma | Como | Evidência |
|---|---|---|
| Teste de integração (INT) | CA-RNF03-01 a 03 e 06, consultando `usuario` depois das operações | Relatório da CI |
| Busca automatizada (INT ou passo da CI) | CA-RNF03-04 e 05: procurar a senha de teste nos logs gerados e no repositório | Saída do passo na CI |
| Inspeção | `SELECT login, LEFT(senha_hash, 12) FROM usuario` no banco de demonstração | Captura da consulta (sem dados reais) |
| SonarCloud | Regras de segredos expostos e de criptografia fraca sem ocorrências | Quality gate no PR (T-44) |

## Dependências

| Tipo | Itens |
|---|---|
| RF | RF01, RF02, RF26 |
| RNF | RNF04 (proteção em trânsito, complementar) |
| Tarefas | T-09, T-11, T-15, T-43, T-44 |

## Protótipo e implementação

O requisito é **o mesmo** nos dois casos e precisa estar **implementado e verificado no
protótipo**. Não é simulado. A única diferença está na origem das senhas iniciais:

| Aspecto | Protótipo | Implementação |
|---|---|---|
| Senhas iniciais | Contas fictícias, com senhas vindas de variáveis de ambiente locais | Contas reais criadas pelos fluxos do sistema, com troca obrigatória |

## Pendências

| Pendência | Situação |
|---|---|
| Algoritmo e parâmetros de custo | **Resolvida** (DEC-28, G5): encoder delegante do Spring Security, com bcrypt e o custo padrão da biblioteca |
| Regras de tamanho das senhas | **Resolvida** (PEND-11, DEC-28, E4): temporária com 12 caracteres, nova com pelo menos 8 |
