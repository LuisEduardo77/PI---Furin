# RNF04 — Comunicação entre navegador e servidor via HTTPS

| Campo | Valor |
|---|---|
| **Categoria** | Segurança |
| **Documentação** | Revisada em 21/10/2026 (DEC-37 a DEC-39) |
| **Implementação** | **Planejada. Não será verificada no protótipo local** |
| **Atualizado em** | 21/10/2026 |

## Descrição

Toda comunicação entre o navegador e o servidor, quando passar por uma rede, usa
**HTTPS**. Quando o envio real de e-mail for implementado, a comunicação com o serviço
SMTP usa **TLS**. Certificados, chaves e credenciais ficam protegidos e fora do
repositório.

## Justificativa

Credenciais, nomes de alunos, fotos e horários de entrada e saída trafegam entre o
navegador e o servidor. Sem criptografia em trânsito, esses dados podem ser lidos ou
alterados por quem estiver na mesma rede. Isso contraria a LGPD (art. 46) e o controle
§8.24 da ISO/IEC 27002 (A10).

## Fonte documental

| Origem | Conteúdo |
|---|---|
| A08 p.4 (RNF04) | "Comunicação entre navegador e servidor via HTTPS" — origem: Atividade 10 |
| A10 p.3 §8.24 | "O acesso ao sistema pela rede deverá utilizar HTTPS. Quando o envio real de e-mails for implementado, a comunicação com o serviço de envio deverá utilizar TLS. Certificados, chaves e credenciais deverão permanecer protegidos. **No protótipo, essas medidas deverão ser documentadas como requisitos técnicos previstos**" |
| A10 p.4 | HTTPS deve ser verificado quando houver implementação. A representação na tela não comprova o controle |
| A13 p.3 | "Para a demonstração inicial, aplicação, banco e arquivos poderão ficar no mesmo computador. […] O uso externo real exigirá ambiente acessível e HTTPS, conforme RNF04" |
| A13 p.4 | Hospedagem externa e provedor SMTP serão definidos na implantação |
| A09 p.2 (LGPD art. 46) | Medidas técnicas contra acessos não autorizados |

**Alteração em relação à A08:** nenhuma no texto. O requisito foi **classificado como
planejado** para o protótipo, conforme A10 p.3 e A13 p.3.

## Partes do sistema afetadas

| Parte | Aplicação |
|---|---|
| Servidor web (Spring Boot ou proxy reverso) | Terminação TLS, redirecionamento de HTTP para HTTPS |
| Cookies de sessão | Atributo `Secure` quando houver HTTPS |
| Cabeçalhos | `Strict-Transport-Security` (HSTS) quando houver HTTPS |
| Envio de e-mail (perfil `smtp`, **fora do MVP**, DEC-32) | STARTTLS ou SMTPS obrigatório, se um dia houver envio real |
| Segredos (certificado, chave privada, senha do SMTP) | Fora do Git: variáveis de ambiente ou armazenamento do provedor |

## Critérios de aceitação

Estes critérios valem **no ambiente de implantação**:

| ID | Critério |
|---|---|
| CA-RNF04-01 | Todo acesso por `http://` é redirecionado para `https://` |
| CA-RNF04-02 | O servidor aceita só TLS 1.2 ou superior (DEC-28, G5) e usa um certificado válido para o domínio |
| CA-RNF04-03 | O cookie de sessão tem os atributos `Secure`, `HttpOnly` e `SameSite` |
| CA-RNF04-04 | A resposta tem o cabeçalho HSTS |
| CA-RNF04-05 | As páginas não carregam nenhum recurso por HTTP (sem conteúdo misto) |
| CA-RNF04-06 | O envio real de e-mail usa TLS. Sem TLS, o envio é recusado |
| CA-RNF04-07 | Nenhum certificado, chave ou senha de SMTP está no repositório |

## Verificação e evidência esperada

| Forma | Como | Evidência |
|---|---|---|
| Manual (MAN), na implantação | Acessar por `http://` e conferir o redirecionamento. Inspecionar o certificado, os cookies e os cabeçalhos nas ferramentas do navegador | Capturas e registro da data da verificação |
| Ferramenta externa [PROP: sugestão] | Teste de configuração TLS do domínio (por exemplo, SSL Labs) | Relatório do teste |
| Revisão do repositório | Busca por chaves e certificados (`*.pem`, `*.p12`, `keystore`) | Saída da busca e SonarCloud |

## Dependências

| Tipo | Itens |
|---|---|
| RNF | RNF03 (complementar: protege as senhas em repouso) |
| RF | RF01 (login), RF21 (envio real de e-mail) |
| Decisões | DEC-31 (aplicação local), DEC-32 (e-mail simulado) |
| Tarefas | T-09 (cookies e cabeçalhos de segurança, já no protótipo), T-44 (busca de segredos no repositório) |

## Protótipo e implementação

| Aspecto | Protótipo | Implementação |
|---|---|---|
| Situação | **Planejada.** Não verificada | Obrigatória |
| Acesso | `http://localhost`, no mesmo computador (A13 p.3) | `https://` pelo domínio da escola, se um dia houver implantação real (DEC-31) |
| Celular na demonstração | (DEC-28, G5, opcional) Se for usado um celular na rede local, a demonstração **pode** ativar HTTPS com certificado autoassinado (`server.ssl.*` do Spring Boot). Mesmo assim, conta só como *demonstrado*, não como *verificado* | Certificado válido |
| E-mail | Envio **simulado**. Não há conexão SMTP | SMTP com TLS |
| Como aparece na entrega | O README e este RNF dizem com clareza que o HTTPS **não** está implementado no protótipo | — |

## Pendências

| ID | Pendência | Situação |
|---|---|---|
| PEND-08 | Hospedagem, domínio e provedor SMTP | **Resolvida** (DEC-31, DEC-32): a aplicação roda local. Não há domínio nem SMTP. O GitHub Pages pode publicar só a documentação, com HTTPS do próprio GitHub |
| — | Versão mínima de TLS | **Resolvida** (DEC-28, G5): TLS 1.2 ou superior |
| — | HTTPS com certificado autoassinado na demonstração com celular | **Aprovado como opcional** (DEC-28, G5) |
