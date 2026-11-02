/*
 * Aviso de expiração da sessão (T-16; RF01, fluxo A2; A11 §5.16.2; DEC-40).
 *
 * A contagem começa quando a página carrega, porque é aí que o servidor viu o último
 * pedido. Faltando data-aviso segundos para o fim de data-inatividade, abre o diálogo
 * #aviso-sessao (templates/layout/base.html), leva o foco para "Continuar conectado" e
 * mostra o tempo restante. "Continuar conectado" (ou Esc) faz POST em data-renovar, que
 * renova a sessão no servidor, e recomeça a contagem. Se o tempo acabar, a página é
 * pedida de novo: o servidor, com a sessão expirada, leva ao login com o aviso.
 */
(function () {
  'use strict';

  var aviso = document.getElementById('aviso-sessao');
  if (!aviso || typeof aviso.showModal !== 'function') {
    return;
  }
  var inatividade = Number(aviso.getAttribute('data-inatividade'));
  var antes = Number(aviso.getAttribute('data-aviso'));
  if (!(inatividade > 0) || !(antes > 0) || antes >= inatividade) {
    return;
  }

  var tempo = aviso.querySelector('[data-tempo-restante]');
  var botao = aviso.querySelector('[data-continuar]');
  var token = document.querySelector('meta[name="_csrf"]');
  var cabecalhoDoToken = document.querySelector('meta[name="_csrf_header"]');
  var fim = 0;
  var esperaDoAviso = null;
  var contagem = null;
  var focoAnterior = null;

  function emTexto(segundos) {
    if (segundos >= 60 && segundos % 60 === 0) {
      var minutos = segundos / 60;
      return minutos === 1 ? '1 minuto' : minutos + ' minutos';
    }
    return segundos === 1 ? '1 segundo' : segundos + ' segundos';
  }

  function restante() {
    return Math.max(0, Math.ceil((fim - Date.now()) / 1000));
  }

  function iniciar() {
    fim = Date.now() + inatividade * 1000;
    clearTimeout(esperaDoAviso);
    clearInterval(contagem);
    esperaDoAviso = setTimeout(mostrar, (inatividade - antes) * 1000);
  }

  function atualizar() {
    var segundos = restante();
    tempo.textContent = emTexto(segundos);
    if (segundos === 0) {
      clearInterval(contagem);
      // GET da mesma página, sem reenviar formulário: o servidor decide se ainda vale
      window.location.assign(window.location.pathname + window.location.search);
    }
  }

  function mostrar() {
    focoAnterior = document.activeElement;
    atualizar();
    aviso.showModal();
    botao.focus();
    contagem = setInterval(atualizar, 1000);
  }

  function fechar() {
    clearInterval(contagem);
    if (aviso.open) {
      aviso.close();
    }
    if (focoAnterior && typeof focoAnterior.focus === 'function') {
      focoAnterior.focus();
    }
  }

  function irParaOLogin() {
    window.location.assign(aviso.getAttribute('data-login'));
  }

  function continuar() {
    var pedido = new XMLHttpRequest();
    pedido.open('POST', aviso.getAttribute('data-renovar'));
    if (token && cabecalhoDoToken) {
      pedido.setRequestHeader(cabecalhoDoToken.getAttribute('content'), token.getAttribute('content'));
    }
    pedido.onload = function () {
      // 204: renovada. Outra resposta (por exemplo, o login): a sessão já tinha acabado
      if (pedido.status === 204) {
        fechar();
        iniciar();
      } else {
        irParaOLogin();
      }
    };
    pedido.onerror = irParaOLogin;
    pedido.send();
  }

  botao.addEventListener('click', continuar);
  aviso.addEventListener('cancel', function (evento) {
    // Esc: quem apertou está presente, então continua conectado
    evento.preventDefault();
    continuar();
  });
  iniciar();
})();
