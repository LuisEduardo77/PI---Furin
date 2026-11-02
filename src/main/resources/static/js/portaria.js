/*
 * Tela da portaria (T-30; RF09, RF10, RF14, RF22; RNF06; DEC-06, DEC-25).
 *
 * - O campo de leitura recebe o código com Enter (RNF07) ou um botão do painel "Simular
 *   leitura (protótipo)" envia o código de uma carteirinha fictícia. Os dois fazem
 *   POST /portaria/leituras com uma chaveRequisicao nova (RN08). O tipo nunca é enviado:
 *   quem decide é o servidor (RN01).
 * - O painel do funcionário mostra nome, turma, tipo e situação, sempre em texto
 *   (A11 §5.11.1). A foto ainda não é entregue (T-18): aparece "Foto indisponível", sem
 *   bloquear a liberação (DEC-28, A9).
 * - O monitor do aluno mostra só o primeiro nome e o estado (RF22; RN17).
 * - "Simular passagem (giro)" faz POST /portaria/giros com o ID da liberação.
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
  var MONITOR_RECUSA = 'Leitura não reconhecida. Tente novamente.';
  var ERRO_INESPERADO = 'Não foi possível registrar. Tente novamente ou use o registro manual.';

  var formulario = document.getElementById('form-leitura');
  var campo = document.getElementById('campo-leitura');
  var painel = document.getElementById('painel-funcionario');
  var monitor = document.getElementById('monitor-aluno');
  var botaoGiro = document.getElementById('botao-giro');
  var token = document.querySelector('meta[name="_csrf"]');
  var cabecalhoDoToken = document.querySelector('meta[name="_csrf_header"]');

  var tempoDoMonitor = Number(raiz.getAttribute('data-monitor-ms'));
  var tempoDoPainel = Number(raiz.getAttribute('data-painel-ms'));

  var liberacaoEmCurso = null;
  var esperaDoMonitor = null;
  var esperaDoPainel = null;

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

  // --- Leitura (RF09, RF14) ---

  function ler(codigo) {
    var limpo = (codigo || '').trim();
    campo.value = '';
    campo.focus();
    if (limpo === '') {
      return;
    }
    enviar(raiz.getAttribute('data-leituras'), { codigo: limpo, chaveRequisicao: novaChave() },
      function (status, dados) {
        if (status === 200 && dados) {
          mostrarLiberacao(dados);
        } else if (status === 422 && dados) {
          mostrarRecusa('Leitura recusada: ' + dados.motivo + '.');
        } else {
          mostrarRecusa(ERRO_INESPERADO);
        }
        campo.focus();
      });
  }

  function mostrarLiberacao(liberacao) {
    var registrada = liberacao.situacao === 'CONFIRMADA';
    liberacaoEmCurso = registrada ? null : liberacao;
    preencherPainel(liberacao, situacaoNoPainel(liberacao));
    mostrarNoMonitor(liberacao.primeiroNome + ', ' + nomeDoTipo(liberacao.tipo) + (registrada
      ? ' registrada.' : ' liberada. Pode passar.'), registrada);
    if (botaoGiro) {
      botaoGiro.disabled = registrada;
    }
    reiniciarLimpezaDoPainel();
  }

  function mostrarRecusa(texto) {
    liberacaoEmCurso = null;
    if (botaoGiro) {
      botaoGiro.disabled = true;
    }
    painel.textContent = '';
    paragrafo(painel, texto);
    // O motivo nunca vai para o monitor do aluno (RF22; RN17)
    mostrarNoMonitor(MONITOR_RECUSA, true);
    reiniciarLimpezaDoPainel();
  }

  // --- Giro simulado (T-28) ---

  function girar() {
    if (!liberacaoEmCurso) {
      return;
    }
    botaoGiro.disabled = true;
    enviar(raiz.getAttribute('data-giros'), { idLiberacao: liberacaoEmCurso.idLiberacao },
      function (status, dados) {
        if (status === 200 && dados) {
          mostrarLiberacao(dados);
        } else if (status === 422 && dados) {
          liberacaoEmCurso = null;
          paragrafo(painel, 'Giro recusado: ' + dados.motivo + '.');
          mostrarNoMonitor(MONITOR_EM_ESPERA, false);
        } else {
          botaoGiro.disabled = false;
          paragrafo(painel, ERRO_INESPERADO);
        }
        campo.focus();
      });
  }

  // --- Painel do funcionário (RF10) ---

  function preencherPainel(liberacao, situacao) {
    painel.textContent = '';
    // DEC-28, A9: sem a área privada (T-18), a foto não carrega e a liberação segue
    var foto = paragrafo(painel, 'Foto indisponível');
    foto.className = 'portaria__foto';
    var lista = document.createElement('dl');
    lista.className = 'portaria__dados';
    item(lista, 'Nome', liberacao.nome);
    item(lista, 'Turma', liberacao.turma);
    item(lista, 'Tipo', nomeDoTipoComMaiuscula(liberacao.tipo));
    item(lista, 'Situação', situacao);
    painel.appendChild(lista);
  }

  function situacaoNoPainel(liberacao) {
    var tipo = nomeDoTipoComMaiuscula(liberacao.tipo);
    if (liberacao.situacao === 'CONFIRMADA') {
      // O horário já vem no fuso da escola (RN10), formatado pelo servidor
      return tipo + ' registrada às ' + liberacao.horarioNaEscola;
    }
    return tipo + ' liberada — aguardando passagem';
  }

  function reiniciarLimpezaDoPainel() {
    clearTimeout(esperaDoPainel);
    // DEC-28, A7: sem leitura por um tempo, o painel volta ao vazio para não expor dados
    if (tempoDoPainel > 0) {
      esperaDoPainel = setTimeout(function () {
        if (!liberacaoEmCurso) {
          painel.textContent = '';
          paragrafo(painel, 'Nenhuma leitura em andamento.');
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

  // --- Ligações ---

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
  // O campo já tem autofocus; isto cobre navegadores que não o aplicam
  campo.focus();
})();
