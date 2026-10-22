# RNF05 — Coleta e exposição mínima de dados pessoais

| Campo | Valor |
|---|---|
| **Categoria** | Privacidade |
| **Documentação** | Revisada em 21/10/2026 (DEC-37 a DEC-39) |
| **Implementação** | Planejada |
| **Atualizado em** | 21/10/2026 |

## Descrição

O sistema **coleta** apenas os dados necessários às funções do MVP. Ele também
**mostra** em cada canal só o necessário:

- monitor do aluno;
- e-mail;
- logs.

A lista de dados permitidos está abaixo. Qualquer dado novo precisa de decisão
registrada do grupo.

## Justificativa

O sistema trata dados de crianças e adolescentes. A LGPD exige limitar o tratamento ao
mínimo necessário (art. 6º, III) e dar atenção especial aos dados de menores (art. 14).
O ECA protege a imagem e a identidade (art. 17). Quanto menos dados, menor o impacto de
um incidente.

## Fonte documental

| Origem | Conteúdo |
|---|---|
| A08 p.4 (RNF05) | "Coleta apenas de nome, matrícula, turma, foto e contato do responsável" — origem: Atividade 9 (LGPD art. 6º) |
| A09 p.2 (LGPD art. 6º, III) | Exigir só os dados estritamente necessários (nome, matrícula, turma e contato do responsável), sem dados sensíveis desnecessários (religião, histórico médico) |
| A09 p.3 (ECA art. 17) | Sem exposição pública dos dados na portaria |
| A10 p.2 §5.34 | Só os dados necessários. Fotos, contatos e documentos com acesso restrito. Notificações com o mínimo necessário |
| A10 p.3 §8.15 | Logs sem senhas nem documentos completos |
| A10 p.4 §8.33 | Dados fictícios em testes, no GitHub e nas ferramentas de IA |
| A14 p.5 | "Não coletar CPF, endereço, nascimento ou biometria para este escopo" |
| **DEC-05** | Foto obrigatória |
| **DEC-08** | Fotos e termos na área privada |
| **DEC-09** | Contato principal com telefone e e-mail |
| **DEC-10, DEC-21** | Conta do responsável (login e hash). **Substituídas pela DEC-37**: os pais/responsáveis não têm conta |
| **DEC-37** | Pais/responsáveis só recebem o e-mail. Nenhum login, senha ou tela para eles |

**Alteração em relação à A08:** a lista de dados ficou detalhada e ampliada, por causa
das decisões do grupo:

- o contato do responsável tem **nome, telefone e e-mail** (DEC-09);
- **não** há conta de responsável: nenhum login, senha ou hash dos pais (DEC-37);
- o termo tem **evidência de conferência** (A14 p.7).

O requisito também passa a cobrir a **exposição mínima**, além da coleta.

## Dados permitidos

| Titular | Dado | Finalidade | Onde | Fonte |
|---|---|---|---|---|
| Aluno | Nome | Identificação | `aluno.nome` | A08 |
| Aluno | Matrícula | Identificação única | `aluno.matricula` | A08 |
| Aluno | Turma (histórico de vínculos) | Contagem e relatórios | `vinculo_turma` | A08, A14 |
| Aluno | Foto | Conferência visual na portaria | Área privada + `aluno.foto_chave` | A08, DEC-05, DEC-08 |
| Aluno | Código da carteirinha | Leitura (opaco, sem dados pessoais) | `carteirinha.codigo` | A14 |
| Aluno | Entradas e saídas | Finalidade do sistema | `registro_acesso` | A05 |
| Responsável | Nome, telefone, e-mail do contato | Notificação por e-mail (o telefone serve para a escola entrar em contato) | `contato_responsavel` | A08, DEC-09, DEC-37 |
| Responsável | Evidência do termo (versão, data, referência) | LGPD art. 14 | `consentimento` + área privada | A09, A14 |
| Responsável | Preferência de notificação | Ligar ou desligar o aviso a pedido do responsável | `contato_responsavel.recebe_notificacao` | DEC-22, DEC-38 |
| Funcionário (FUNCIONARIO) | Nome, login, hash de senha | Acesso e autoria | `usuario` | A14 |
| Todos | Eventos de auditoria, com dados mascarados | Rastreabilidade | Log da aplicação | A10 §8.15 |

**Não são coletados:** CPF, RG, endereço, data de nascimento, biometria, reconhecimento
facial, religião, dados de saúde ou qualquer outro dado não listado (A09 p.2; A14 p.5).

## Exposição mínima por canal

| Canal | O que aparece | O que **não** aparece | RF |
|---|---|---|---|
| Painel do funcionário | Foto, nome completo, turma, tipo, situação | Contato, termo | RF10 |
| Monitor do aluno | Primeiro nome e estado | Foto, nome completo, turma, matrícula, motivo | RF22, RF23 |
| E-mail ao responsável | Primeiro nome, tipo, data, hora, forma | Link para o sistema, turma, matrícula, foto, nome completo | RF21 |
| Histórico e relatório | Nome, matrícula, turma, tipo, data, hora, forma, motivo e autor (manuais) | Foto, contato, código | RF18–RF20 |
| Logs | Evento, data e hora, usuário mascarado, IDs | Senhas, conteúdo de foto ou termo, e-mail completo, código completo | A10 §8.15 |
| Tabela `notificacao` | Status e tentativas | Corpo da mensagem | RF21 |

## Partes do sistema afetadas

Modelo de dados ([arquitetura-e-dados §5](../../docs/arquitetura-e-dados.md)),
formulários do RF04 e do RF08, respostas JSON da portaria, templates de e-mail, logger
de auditoria e carga fictícia.

## Critérios de aceitação

| ID | Critério |
|---|---|
| CA-RNF05-01 | O esquema do banco (V1) tem só as colunas da tabela "Dados permitidos". Uma coluna nova exige atualizar este RNF e registrar a decisão |
| CA-RNF05-02 | Os formulários de cadastro e edição não têm campos de CPF, endereço, nascimento, biometria, religião ou saúde |
| CA-RNF05-03 | A resposta JSON de `POST /portaria/leituras` tem só tipo, nomes, turma, referência da foto e ID da liberação. Não tem contato, matrícula nem código |
| CA-RNF05-04 | Os canais da tabela "Exposição mínima" seguem as colunas "aparece" e "não aparece" (verificado nos critérios dos RF citados) |
| CA-RNF05-05 | Os logs gerados na suíte de testes não têm e-mail completo, senha nem código de carteirinha completo |
| CA-RNF05-06 | O repositório, os testes e a carga inicial usam só dados fictícios (`@example.com`, nomes como "Ana Teste") (A10 §8.33) |
| CA-RNF05-07 | Fotos e termos só são entregues pela rota autorizada do funcionário (DEC-08) |

## Verificação e evidência esperada

| Forma | Como | Evidência |
|---|---|---|
| Inspeção do modelo | Comparar a migração V1 com a tabela "Dados permitidos" | Checklist assinada na revisão do PR do esquema (T-06) |
| INT | CA-RNF05-03, 05 e 07 | Relatório da CI |
| MAN | CA-RNF05-02 e 04 nas telas | Capturas |
| Revisão do repositório | CA-RNF05-06: busca por padrões de dados reais (CPF, e-mails fora de `example.com`) | Saída da busca |

## Dependências

| Tipo | Itens |
|---|---|
| RF | RF04, RF05, RF08, RF10, RF18–RF23 |
| RNF | RNF03, RNF04 |
| Tarefas | T-06, T-10, T-11, T-19 |

## Protótipo e implementação

| Aspecto | Protótipo | Implementação |
|---|---|---|
| Dados | **Só fictícios** (A10 §8.33) | Dados reais, com termo de consentimento conferido (RF05) |
| Coleta e exposição mínima | **Implementadas e verificadas** | Iguais |
| Retenção e descarte | Não se aplica: dados fictícios, nada é descartado automaticamente | **5 anos** após o fim do tratamento (DEC-36), depois eliminar ou anonimizar (LGPD art. 16, DEC-33) |

## Guarda e descarte (DEC-33, DEC-36)

A LGPD **não fixa um prazo em anos**. A regra dela é esta:

- o tratamento termina quando a finalidade é alcançada, quando os dados deixam de ser
  necessários ou quando o titular revoga o consentimento (art. 15);
- depois disso, os dados são **eliminados**, mas podem ser **conservados** para cumprir
  obrigação legal ou regulatória da escola, ou **anonimizados** para uso exclusivo dela
  (art. 16).

O grupo definiu o prazo de guarda de **5 anos** (DEC-36), contados do **fim do
tratamento**: a saída do aluno da escola (inativação) ou a revogação do termo. Se a
escola tiver uma tabela de temporalidade com prazo diferente, vale a tabela da escola.

| Dado | Enquanto é necessário | Depois do fim do tratamento |
|---|---|---|
| Cadastro, foto e contatos | Enquanto o aluno estiver na escola e com o termo válido | Guardar por **5 anos**, depois eliminar ou anonimizar |
| Termo de consentimento (evidência) | Enquanto houver tratamento | Guardar por **5 anos**, para comprovar o consentimento, depois eliminar |
| Entradas e saídas | Enquanto servirem à finalidade (portaria, merenda, informação aos pais) | Guardar por **5 anos**, depois eliminar ou anonimizar |
| Notificações (status) | Enquanto o registro existir | Seguem o registro de entrada e saída |
| Logs de auditoria | Enquanto servirem à segurança e à rastreabilidade | Guardar por **5 anos** a partir da data do evento, depois eliminar |
| Backups | 7 cópias em rodízio (DEC-30) | A cópia mais antiga é apagada quando entra uma nova |

O Furin Access **não guarda os documentos escolares oficiais** (matrícula, diário de
classe, histórico), que seguem as regras próprias da rede de ensino.

**No protótipo**, nada é descartado automaticamente: os dados são fictícios e a
inativação preserva o histórico. O prazo fica configurado (`furin.retencao.anos = 5`),
e o descarte automático é uma evolução fora do MVP.

## Pendências

| ID | Pendência | Situação |
|---|---|---|
| PEND-16 | **Prazo de retenção e descarte** dos dados | **Resolvida** (DEC-33, DEC-36): regra da LGPD (art. 15 e 16) com prazo de **5 anos** definido pelo grupo. Ver "Guarda e descarte" acima |
| PEND-15 | Processo de revogação do consentimento | **Resolvida** (DEC-34): o aluno é inativado e a carteirinha revogada |
| — | Mostrar o nome completo do próprio aluno na consulta do responsável | **Obsoleta** (DEC-37): a consulta (RF24) saiu do escopo. A DEC-28 (E8) não se aplica mais |
