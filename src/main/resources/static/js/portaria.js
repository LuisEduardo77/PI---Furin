/*
 * Tela da portaria (T-30; RF09, RF10, RF14, RF22; RNF06; DEC-06, DEC-25).
 *
 * - O campo de leitura recebe o código com Enter (RNF07) ou um botão do painel "Simular
 *   leitura (protótipo)" envia o código de uma carteirinha fictícia. Os dois fazem
 *   POST /portaria/leituras com uma chaveRequisicao nova (RN08). O tipo nunca é enviado:
 *   quem decide é o servidor (RN01).
 * - O painel do funcionário mostra a foto (GET /arquivos/{chave}, T-18) e, sempre em texto
 *   (A11 §5.11.1), nome, turma, tipo e situação. Se a foto não carregar, aparece "Foto
 *   indisponível", sem bloquear a liberação (DEC-28, A9).
 * - O monitor do aluno mostra só o primeiro nome e o estado (RF22; RN17).
 * - "Simular passagem (giro)" faz POST /portaria/giros com o ID da liberação e uma
 *   chaveRequisicao. Depois de uma falha, a tentativa repete a mesma chave (RN08; DEC-41).
 * - A resposta da leitura tem só o que o CA-RNF05-03 lista (DEC-43). O status diz se a
 *   liberação é nova (201), se já estava pendente (200; RN04) ou se a leitura foi ignorada
 *   na janela de 2 s (202; RN03). Com outro aluno aguardando, a recusa mostra "Aguarde a
 *   passagem do aluno anterior" (RN05).
 * - Expiração e cancelamento (T-29): passado o prazo do giro, a tela consulta
 *   GET /portaria/liberacoes/{id} e, se o servidor cancelou a liberação, mostra "Liberação
 *   expirada — Ana não passou". "Cancelar liberação de Ana Teste" faz
 *   POST /portaria/liberacoes/{id}/cancelar (RN02, item 5).
 * - Leitura inválida (T-31; RF23): o motivo vai para a região role="alert" do funcionário,
 *   com o rótulo "Leitura recusada:", e o monitor do aluno mostra sempre a mesma mensagem.
 * - Contador de presentes (T-34; RF16, passo 4): a resposta do giro traz o total e o da
 *   turma, e o contador muda sem recarregar, sem anúncio próprio.
 * - Aviso ao responsável (T-39; DEC-28, F3): a resposta do giro traz o e-mail que teria
 *   saído, e a seção "Aviso ao responsável (protótipo)" mostra "Simulação — nenhum e-mail
 *   foi enviado", o destinatário mascarado, o assunto e o texto. Também sem anúncio próprio.
 * - Os dois painéis são regiões role="status" separadas: anunciam sem mover o foco, e o
 *   foco volta sempre ao campo de leitura (A11 §5.1.13, §5.13.8).
 */
(function () {
  'use strict';

  var raiz = document.getElementById('portaria');
  if (!raiz) {
    return;
  }

  var MONITOR_EM_ESPERA = 'Aproxime sua carteirinha do leitor.';
  // RF23, passo 2: a mesma mensagem em toda recusa
  var MONITOR_RECUSA = 'Não foi possível ler sua carteirinha. Tente de novo ou procure o funcionário da portaria.';
  var LEITOR_NAO_PRONTO = 'Leitor não está pronto. Clique no campo de leitura.';
  var ESPERA_DO_AVISO_DO_LEITOR_MS = 200;
  var ERRO_INESPERADO = 'Não foi possível registrar. Tente novamente ou use o registro manual.';
  // RF15, exceções (T-27)
  var LEITURA_JA_RECEBIDA = 'Leitura já recebida';
  var AGUARDE_O_ANTERIOR = 'Aguarde a passagem do aluno anterior';
  var MONITOR_AGUARDE = 'Aguarde.';
  // 201: liberação nova; 200: já pendente (RN04); 202: ignorada na janela de 2 s (RN03)
  var DESFECHO_POR_STATUS = { 201: 'nova', 200: 'aguardando', 202: 'ja-recebida' };
  // RF09, fluxos A1 e A2 e exceções (T-29)
  var GIRO_FORA_DO_PRAZO = 'Liberação expirada. Leia a carteirinha novamente.';
  var JA_CONFIRMADA = 'A passagem já foi registrada e não pode ser cancelada.';
  // RF23, passo 1 (T-31): o motivo para o funcionário. O aluno vê sempre MONITOR_RECUSA
  var MOTIVOS_DA_RECUSA = {
    LEITURA_NAO_RECONHECIDA: 'Leitura não reconhecida. Tente novamente.',
    CODIGO_NAO_RECONHECIDO: 'Código não reconhecido.',
    CARTEIRINHA_REVOGADA: 'Carteirinha revogada. Emitir segunda via.',
    CARTEIRINHA_EXPIRADA: 'Carteirinha expirada. Emitir segunda via.',
    ALUNO_INATIVO: 'Aluno inativo.',
    SEM_TURMA_VIGENTE: 'Aluno sem turma vigente.'
  };

  var formulario = document.getElementById('form-leitura');
  var campo = document.getElementById('campo-leitura');
  var painel = document.getElementById('painel-funcionario');
  var alerta = document.getElementById('alerta-funcionario');
  var monitor = document.getElementById('monitor-aluno');
  var avisoDoLeitor = document.getElementById('aviso-leitor');
  var botaoGiro = document.getElementById('botao-giro');
  var botaoCancelar = document.getElementById('botao-cancelar');
  var contadorDePresentes = document.getElementById('contador-presentes');
  var contadorDaTurma = document.getElementById('contador-turma');
  var avisoAoResponsavel = document.getElementById('aviso-responsavel');
  var token = document.querySelector('meta[name="_csrf"]');
  var cabecalhoDoToken = document.querySelector('meta[name="_csrf_header"]');

  var tempoDoMonitor = Number(raiz.getAttribute('data-monitor-ms'));
  var tempoDoPainel = Number(raiz.getAttribute('data-painel-ms'));
  var tempoDoPrazo = Number(raiz.getAttribute('data-prazo-giro-ms'));
  var tempoDaVerificacao = Number(raiz.getAttribute('data-verificacao-ms'));

  var liberacaoEmCurso = null;
  var giroEmCurso = null;
  var esperaDoMonitor = null;
  var esperaDoPainel = null;
  // T-29: consulta da situação quando passa o prazo do giro, e o ID que ela acompanha
  var esperaDaExpiracao = null;
  var liberacaoAcompanhada = null;

  // --- Pedidos ao servidor ---

  /** UUID v4 para a chaveRequisicao (RN08). */
  function novaChave() {
    if (window.crypto && typeof window.crypto.randomUUID === 'function') {
      return window.crypto.randomUUID();
    }
    var bytes = new Uint8Array(16);
    window.crypto.getRandomValues(bytes);
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    var hex = Array.prototype.map.call(bytes, function (b) {
      return (b + 0x100).toString(16).substring(1);
    }).join('');
    return hex.substring(0, 8) + '-' + hex.substring(8, 12) + '-' + hex.substring(12, 16) + '-'
      + hex.substring(16, 20) + '-' + hex.substring(20);
  }

  function enviar(endereco, corpo, aoResponder) {
    var pedido = new XMLHttpRequest();
    pedido.open('POST', endereco);
    pedido.setRequestHeader('Content-Type', 'application/json');
    pedido.setRequestHeader('Accept', 'application/json');
    if (token && cabecalhoDoToken) {
      pedido.setRequestHeader(cabecalhoDoToken.getAttribute('content'), token.getAttribute('content'));
    }
    pedido.onload = function () {
      // A sessão acabou: o servidor mandou para o login (T-16, T-17)
      if (pedido.responseURL && pedido.responseURL.indexOf('/login') >= 0) {
        window.location.assign(pedido.responseURL);
        return;
      }
      var dados = null;
      try {
        dados = JSON.parse(pedido.responseText);
      } catch (erro) {
        dados = null;
      }
      // Cada pedido renova a sessão no servidor: o aviso de expiração recomeça (T-16)
      document.dispatchEvent(new CustomEvent('furin:atividade'));
      aoResponder(pedido.status, dados);
    };
    pedido.onerror = function () {
      aoResponder(0, null);
    };
    pedido.send(JSON.stringify(corpo));
  }

  /**
   * GET da situação de uma liberação (T-29). Não conta como atividade do funcionário: é a
   * tela que pergunta sozinha, e o aviso de expiração da sessão não deve recomeçar.
   */
  function consultar(endereco, aoResponder) {
    var pedido = new XMLHttpRequest();
    pedido.open('GET', endereco);
    pedido.setRequestHeader('Accept', 'application/json');
    pedido.onload = function () {
      if (pedido.responseURL && pedido.responseURL.indexOf('/login') >= 0) {
        window.location.assign(pedido.responseURL);
        return;
      }
      var dados = null;
      try {
        dados = JSON.parse(pedido.responseText);
      } catch (erro) {
        dados = null;
      }
      aoResponder(pedido.status, dados);
    };
    pedido.onerror = function () {
      aoResponder(0, null);
    };
    pedido.send();
  }

  // --- Leitura (RF09, RF14) ---

  function ler(codigo) {
    var limpo = (codigo || '').trim();
    campo.value = '';
    campo.focus();
    if (limpo === '') {
      return;
    }
    // Esvaziar antes faz o leitor de tela anunciar de novo uma recusa igual à anterior
    limparAlerta();
    enviar(raiz.getAttribute('data-leituras'), { codigo: limpo, chaveRequisicao: novaChave() },
      function (status, dados) {
        if ((status === 201 || status === 200 || status === 202) && dados) {
          mostrarLiberacao(dados, DESFECHO_POR_STATUS[status]);
        } else if (status === 202) {
          // RN03: a leitura da janela já não está pendente; nada muda na tela além do aviso
          paragrafo(painel, LEITURA_JA_RECEBIDA);
        } else if (status === 422 && dados && dados.motivo === 'OUTRA_LIBERACAO_PENDENTE') {
          // RN05; RN09: o aluno vê só "Aguarde"
          mostrarRecusa(AGUARDE_O_ANTERIOR, MONITOR_AGUARDE);
        } else if (status === 422 && dados) {
          mostrarLeituraInvalida(textoDaRecusa(dados));
        } else {
          mostrarLeituraInvalida(ERRO_INESPERADO);
        }
        campo.focus();
      });
  }

  function mostrarLiberacao(liberacao, desfecho) {
    var registrada = liberacao.situacao === 'CONFIRMADA';
    liberacaoEmCurso = registrada ? null : liberacao;
    limparAlerta();
    preencherPainel(liberacao, situacaoNoPainel(liberacao, desfecho));
    mostrarNoMonitor(mensagemDoMonitor(liberacao, desfecho !== 'nova'), registrada);
    if (botaoGiro) {
      botaoGiro.disabled = registrada;
    }
    atualizarCancelamento();
    if (registrada) {
      pararDeAcompanhar();
    } else if (liberacaoAcompanhada !== liberacao.idLiberacao) {
      // A repetida (RN03, RN04) mantém a contagem que já corria para a mesma liberação
      acompanharExpiracao(liberacao.idLiberacao, tempoDoPrazo + tempoDaVerificacao);
    }
    reiniciarLimpezaDoPainel();
  }

  // --- Expiração e cancelamento (T-29; RF09, fluxos A1 e A2) ---

  function atualizarCancelamento() {
    if (!botaoCancelar) {
      return;
    }
    botaoCancelar.hidden = !liberacaoEmCurso;
    if (liberacaoEmCurso) {
      botaoCancelar.textContent = 'Cancelar liberação de ' + liberacaoEmCurso.nome;
    }
  }

  function acompanharExpiracao(idLiberacao, espera) {
    clearTimeout(esperaDaExpiracao);
    liberacaoAcompanhada = idLiberacao;
    esperaDaExpiracao = setTimeout(function () {
      consultar(raiz.getAttribute('data-liberacoes') + idLiberacao, function (status, dados) {
        if (liberacaoAcompanhada !== idLiberacao) {
          return;
        }
        if (status === 200 && dados) {
          mostrarSituacao(dados);
        } else {
          // Sem resposta: tenta de novo, sem encerrar a liberação na tela
          acompanharExpiracao(idLiberacao, tempoDaVerificacao);
        }
      });
    }, espera);
  }

  function pararDeAcompanhar() {
    clearTimeout(esperaDaExpiracao);
    liberacaoAcompanhada = null;
  }

  /** A resposta da consulta ou do cancelamento: só a cancelada muda a tela. */
  function mostrarSituacao(situacao) {
    if (situacao.situacao === 'AGUARDANDO_GIRO') {
      // O servidor ainda não cancelou (relógios diferentes): pergunta de novo em seguida
      acompanharExpiracao(situacao.idLiberacao, tempoDaVerificacao);
    } else if (situacao.situacao === 'CANCELADA') {
      encerrarLiberacao(situacao.motivoCancelamento === 'SEM_GIRO'
        ? 'Liberação expirada — ' + situacao.primeiroNome + ' não passou'
        : 'Liberação de ' + situacao.primeiroNome + ' cancelada');
    } else {
      // Confirmada por outra tela: nada a encerrar aqui
      liberacaoEmCurso = null;
      pararDeAcompanhar();
      atualizarCancelamento();
    }
  }

  /** A liberação acabou sem passagem: o painel diz por quê, e o monitor volta à espera. */
  function encerrarLiberacao(texto) {
    liberacaoEmCurso = null;
    pararDeAcompanhar();
    if (botaoGiro) {
      botaoGiro.disabled = true;
    }
    atualizarCancelamento();
    limparAlerta();
    painel.textContent = '';
    paragrafo(painel, texto);
    mostrarNoMonitor(MONITOR_EM_ESPERA, false);
    reiniciarLimpezaDoPainel();
  }

  function cancelar() {
    if (!liberacaoEmCurso) {
      return;
    }
    var idLiberacao = liberacaoEmCurso.idLiberacao;
    botaoCancelar.disabled = true;
    // O botão some com o cancelamento: o foco volta ao campo de leitura (DEC-28, A8)
    campo.focus();
    enviar(raiz.getAttribute('data-liberacoes') + idLiberacao + '/cancelar', {},
      function (status, dados) {
        botaoCancelar.disabled = false;
        if (status === 200 && dados) {
          mostrarSituacao(dados);
        } else if (status === 422 && dados && dados.motivo === 'LIBERACAO_CONFIRMADA') {
          liberacaoEmCurso = null;
          pararDeAcompanhar();
          atualizarCancelamento();
          paragrafo(painel, JA_CONFIRMADA);
        } else {
          paragrafo(painel, ERRO_INESPERADO);
        }
        campo.focus();
      });
  }

  function mensagemDoMonitor(liberacao, repetida) {
    if (liberacao.situacao === 'CONFIRMADA') {
      return liberacao.primeiroNome + ', ' + nomeDoTipo(liberacao.tipo) + ' registrada.';
    }
    // RN03, RN04: leitura repetida com a liberação pendente (RF09, exceções)
    if (repetida) {
      return liberacao.primeiroNome + ', pode passar.';
    }
    return liberacao.primeiroNome + ', ' + nomeDoTipo(liberacao.tipo) + ' liberada. Pode passar.';
  }

  // --- Leitura inválida (T-31; RF23, RN09, RN17) ---

  /** O motivo em texto. A saída pendente leva o dia da entrada (RN06). */
  function textoDaRecusa(recusa) {
    if (recusa.motivo === 'SAIDA_PENDENTE' && recusa.saidaPendenteDe) {
      var dia = recusa.saidaPendenteDe.split('-');
      return 'Saída pendente de ' + dia[2] + '/' + dia[1]
        + ': registre a saída manual antes da nova entrada.';
    }
    return MOTIVOS_DA_RECUSA[recusa.motivo] || ERRO_INESPERADO;
  }

  /**
   * RF23, passos 1 a 3: o motivo vai para a região role="alert" do funcionário, e o aluno vê
   * sempre a mesma mensagem, sem saber se o código existe (CA-RF23-01, 06). O foco fica no
   * campo de leitura. Uma liberação em curso continua no painel, com o giro disponível.
   */
  function mostrarLeituraInvalida(texto) {
    if (botaoGiro) {
      botaoGiro.disabled = !liberacaoEmCurso;
    }
    if (!liberacaoEmCurso) {
      painel.textContent = '';
      paragrafo(painel, 'Nenhuma leitura em andamento.');
    }
    alerta.textContent = '';
    alerta.className = 'aviso aviso--erro';
    var p = document.createElement('p');
    var rotulo = document.createElement('strong');
    // CA-RF23-08: o estado está no texto, não só na cor
    rotulo.textContent = 'Leitura recusada:';
    p.appendChild(rotulo);
    p.appendChild(document.createTextNode(' ' + texto));
    alerta.appendChild(p);
    mostrarNoMonitor(MONITOR_RECUSA, true);
    reiniciarLimpezaDoPainel();
  }

  function limparAlerta() {
    alerta.textContent = '';
    alerta.className = '';
  }

  /** RN05: não é falha (RF23, exceções); o aviso fica no painel, sem alerta. */
  function mostrarRecusa(texto, textoDoMonitor) {
    // A recusa de uma leitura não encerra a liberação em curso: o giro continua disponível
    if (botaoGiro) {
      botaoGiro.disabled = !liberacaoEmCurso;
    }
    painel.textContent = '';
    paragrafo(painel, texto);
    // O motivo nunca vai para o monitor do aluno (RF22; RN17)
    mostrarNoMonitor(textoDoMonitor, true);
    reiniciarLimpezaDoPainel();
  }

  // --- Giro simulado (T-28) ---

  function girar() {
    if (!liberacaoEmCurso) {
      return;
    }
    botaoGiro.disabled = true;
    campo.focus();
    // RN08: repetir o giro depois de uma falha usa a mesma chave, sem confirmar duas vezes
    if (!giroEmCurso || giroEmCurso.idLiberacao !== liberacaoEmCurso.idLiberacao) {
      giroEmCurso = { idLiberacao: liberacaoEmCurso.idLiberacao, chaveRequisicao: novaChave() };
    }
    enviar(raiz.getAttribute('data-giros'), giroEmCurso,
      function (status, dados) {
        if (status === 200 && dados) {
          mostrarLiberacao(dados, 'nova');
          mostrarPresentes(dados.presentes);
          mostrarAvisoAoResponsavel(dados.avisoAoResponsavel);
        } else if (status === 422 && dados && dados.motivo === 'LIBERACAO_ENCERRADA') {
          // RF09, exceções: giro depois do prazo ou de uma liberação cancelada
          encerrarLiberacao(GIRO_FORA_DO_PRAZO);
        } else if (status === 422 && dados) {
          encerrarLiberacao('Giro recusado: ' + dados.motivo + '.');
        } else {
          botaoGiro.disabled = false;
          paragrafo(painel, ERRO_INESPERADO);
        }
        campo.focus();
      });
  }

  // --- Contador de presentes (T-34; RF16, passo 4) ---

  /** Fora de região viva: o painel do funcionário já anunciou o registro (CA-RF16-09). */
  function mostrarPresentes(presentes) {
    if (!presentes || !contadorDePresentes) {
      return;
    }
    contadorDePresentes.textContent = String(presentes.total);
    if (contadorDaTurma && presentes.turma) {
      contadorDaTurma.textContent = presentes.turma + ': ' + presentes.presentesNaTurma;
      contadorDaTurma.hidden = false;
    }
  }

  // --- Aviso ao responsável (T-39; RF21, passo 6; DEC-28, F3) ---

  /**
   * O e-mail que teria saído, com o destinatário mascarado. Sem notificação (aviso desligado),
   * a seção some. Só texto (textContent), e o e-mail nunca tem link (DEC-37).
   */
  function mostrarAvisoAoResponsavel(aviso) {
    if (!avisoAoResponsavel) {
      return;
    }
    avisoAoResponsavel.hidden = !aviso;
    if (!aviso) {
      return;
    }
    document.getElementById('aviso-responsavel-titulo').textContent = aviso.titulo;
    document.getElementById('aviso-responsavel-destinatario').textContent = aviso.destinatario;
    document.getElementById('aviso-responsavel-assunto').textContent = aviso.assunto;
    document.getElementById('aviso-responsavel-corpo').textContent = aviso.corpo;
  }

  // --- Painel do funcionário (RF10) ---

  function preencherPainel(liberacao, situacao) {
    painel.textContent = '';
    colocarFoto(liberacao);
    var lista = document.createElement('dl');
    lista.className = 'portaria__dados';
    item(lista, 'Nome', liberacao.nome);
    item(lista, 'Turma', liberacao.turma);
    item(lista, 'Tipo', nomeDoTipoComMaiuscula(liberacao.tipo));
    item(lista, 'Situação', situacao);
    painel.appendChild(lista);
  }

  function colocarFoto(liberacao) {
    var imagem = document.createElement('img');
    imagem.className = 'portaria__foto-imagem';
    imagem.alt = 'Foto de identificação de ' + liberacao.nome;
    // DEC-28, A9: a foto que não carrega nunca bloqueia a liberação
    imagem.addEventListener('error', function () {
      if (imagem.parentNode) {
        imagem.parentNode.replaceChild(textoDeFotoIndisponivel(), imagem);
      }
    });
    painel.appendChild(imagem);
    imagem.src = raiz.getAttribute('data-arquivos') + encodeURIComponent(liberacao.fotoChave);
  }

  function textoDeFotoIndisponivel() {
    var aviso = document.createElement('p');
    aviso.className = 'portaria__foto';
    aviso.textContent = 'Foto indisponível';
    return aviso;
  }

  function situacaoNoPainel(liberacao, desfecho) {
    var tipo = nomeDoTipoComMaiuscula(liberacao.tipo);
    if (liberacao.situacao === 'CONFIRMADA') {
      // O horário já vem no fuso da escola (RN10), formatado pelo servidor
      return tipo + ' registrada às ' + liberacao.horarioNaEscola;
    }
    if (desfecho === 'ja-recebida') {
      return LEITURA_JA_RECEBIDA;
    }
    return desfecho === 'aguardando' ? 'Aguardando passagem' : tipo + ' liberada — aguardando passagem';
  }

  function reiniciarLimpezaDoPainel() {
    clearTimeout(esperaDoPainel);
    // DEC-28, A7: sem leitura por um tempo, o painel volta ao vazio para não expor dados
    if (tempoDoPainel > 0) {
      esperaDoPainel = setTimeout(function () {
        if (!liberacaoEmCurso) {
          painel.textContent = '';
          paragrafo(painel, 'Nenhuma leitura em andamento.');
          limparAlerta();
        }
      }, tempoDoPainel);
    }
  }

  // --- Monitor do aluno (RF22) ---

  function mostrarNoMonitor(texto, voltaParaEspera) {
    clearTimeout(esperaDoMonitor);
    monitor.textContent = texto;
    // DEC-28, A6: depois do registro (ou de uma recusa), volta à espera
    if (voltaParaEspera && tempoDoMonitor > 0) {
      esperaDoMonitor = setTimeout(function () {
        monitor.textContent = MONITOR_EM_ESPERA;
      }, tempoDoMonitor);
    }
  }

  // --- Textos ---

  function nomeDoTipo(tipo) {
    return tipo === 'SAIDA' ? 'saída' : 'entrada';
  }

  function nomeDoTipoComMaiuscula(tipo) {
    return tipo === 'SAIDA' ? 'Saída' : 'Entrada';
  }

  function paragrafo(pai, texto) {
    var p = document.createElement('p');
    p.textContent = texto;
    pai.appendChild(p);
    return p;
  }

  function item(lista, termo, valor) {
    var dt = document.createElement('dt');
    dt.textContent = termo;
    var dd = document.createElement('dd');
    dd.textContent = valor;
    lista.appendChild(dt);
    lista.appendChild(dd);
  }

  // --- Aviso do leitor (DEC-28, A8; CA-RNF07-04) ---

  function atualizarAvisoDoLeitor() {
    var pronto = document.activeElement === campo;
    avisoDoLeitor.textContent = pronto ? '' : LEITOR_NAO_PRONTO;
    avisoDoLeitor.className = pronto ? '' : 'aviso aviso--erro';
  }

  // --- Ligações ---

  campo.addEventListener('focus', atualizarAvisoDoLeitor);
  campo.addEventListener('blur', function () {
    // Espera um instante: as ações da tela devolvem o foco ao campo logo em seguida
    setTimeout(atualizarAvisoDoLeitor, ESPERA_DO_AVISO_DO_LEITOR_MS);
  });
  // Acionar a portaria fora de um controle devolve o foco ao campo (DEC-28, A8)
  raiz.addEventListener('click', function (evento) {
    if (!evento.target.closest('a, button, input, label, select, textarea')) {
      campo.focus();
    }
  });

  formulario.addEventListener('submit', function (evento) {
    evento.preventDefault();
    ler(campo.value);
  });
  Array.prototype.forEach.call(document.querySelectorAll('[data-simular-leitura]'), function (botao) {
    botao.addEventListener('click', function () {
      ler(botao.getAttribute('data-codigo'));
    });
  });
  if (botaoGiro) {
    botaoGiro.addEventListener('click', girar);
  }
  if (botaoCancelar) {
    botaoCancelar.addEventListener('click', cancelar);
  }
  // O campo já tem autofocus; isto cobre navegadores que não o aplicam
  campo.focus();
})();
