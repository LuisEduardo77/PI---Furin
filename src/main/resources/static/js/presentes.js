/*
 * Tela Presentes (T-34; RF16, passos 4 e 5; DEC-28, C2 e C3; A11 p.9).
 *
 * - A cada data-intervalo-ms (furin.presentes.intervalo-atualizacao, 30 s) a tela pede
 *   GET /presentes de novo e troca o conteúdo de #dados-presentes pelo da resposta. O
 *   pedido vai com o cabeçalho X-Furin-Atualizacao-Automatica: não é atividade do
 *   funcionário, não renova a sessão (autenticacao/AtualizacaoAutomatica) e não recomeça o
 *   aviso de expiração (sessao.js).
 * - A atualização automática não é anunciada: #dados-presentes não é região viva, e só o
 *   "Atualizar" escreve em #anuncio-presentes (role="status"), sem mover o foco.
 * - O conteúdo só é trocado quando muda. Se o foco estava num "Registrar saída de …" que
 *   continua na lista, ele volta para o mesmo link.
 * - Se o pedido falhar, os últimos números ficam e aparece "Não foi possível atualizar.
 *   Última atualização às HH:MM." (RF16, exceções).
 */
(function () {
  'use strict';

  var raiz = document.getElementById('presentes');
  if (!raiz) {
    return;
  }

  var CABECALHO_AUTOMATICO = 'X-Furin-Atualizacao-Automatica';

  var endereco = raiz.getAttribute('data-endereco');
  var intervalo = Number(raiz.getAttribute('data-intervalo-ms'));
  var botao = document.getElementById('botao-atualizar');
  var dados = document.getElementById('dados-presentes');
  var hora = document.querySelector('#atualizado-em [data-hora]');
  var falha = document.getElementById('falha-atualizacao');
  var anuncio = document.getElementById('anuncio-presentes');
  var pedindo = false;

  function pedir(manual) {
    // Uma atualização automática não se sobrepõe a outra; o "Atualizar" sempre vale
    if (pedindo && !manual) {
      return;
    }
    pedindo = true;
    var pedido = new XMLHttpRequest();
    pedido.open('GET', endereco);
    pedido.setRequestHeader('Accept', 'text/html');
    if (!manual) {
      pedido.setRequestHeader(CABECALHO_AUTOMATICO, '1');
    }
    pedido.onload = function () {
      pedindo = false;
      // Sessão encerrada: o servidor mandou para o login (T-16, T-17)
      if (pedido.responseURL && pedido.responseURL.indexOf('/login') >= 0) {
        window.location.assign(pedido.responseURL);
        return;
      }
      var pagina = pedido.status === 200 ? lerPagina(pedido.responseText) : null;
      var novos = pagina && pagina.getElementById('dados-presentes');
      var novaHora = pagina && pagina.querySelector('#atualizado-em [data-hora]');
      if (!novos || !novaHora) {
        mostrarFalha(manual);
        return;
      }
      trocar(novos);
      hora.textContent = novaHora.textContent;
      falha.hidden = true;
      falha.textContent = '';
      if (manual) {
        // O pedido manual renovou a sessão no servidor: o aviso recomeça (T-16)
        document.dispatchEvent(new CustomEvent('furin:atividade'));
        anunciar('Atualizado às ' + hora.textContent.trim() + '. Presentes agora: '
          + texto(document.getElementById('total-presentes')) + '.');
      }
    };
    pedido.onerror = function () {
      pedindo = false;
      mostrarFalha(manual);
    };
    pedido.send();
  }

  function lerPagina(html) {
    try {
      return new DOMParser().parseFromString(html, 'text/html');
    } catch (erro) {
      return null;
    }
  }

  /** Troca só se mudou, e devolve o foco ao mesmo link, se ele continuar na lista. */
  function trocar(novos) {
    if (novos.innerHTML === dados.innerHTML) {
      return;
    }
    var focado = document.activeElement;
    var idFocado = focado && dados.contains(focado) ? focado.id : null;
    dados.innerHTML = novos.innerHTML;
    if (idFocado) {
      var mesmo = document.getElementById(idFocado);
      if (mesmo) {
        mesmo.focus();
      } else {
        // O aluno saiu da lista: o foco não se perde, vai para o "Atualizar"
        botao.focus();
      }
    }
  }

  function mostrarFalha(manual) {
    var mensagem = 'Não foi possível atualizar. Última atualização às ' + hora.textContent.trim() + '.';
    falha.textContent = mensagem;
    falha.hidden = false;
    if (manual) {
      anunciar(mensagem);
    }
  }

  function anunciar(mensagem) {
    // Esvaziar antes faz o leitor de tela anunciar de novo um texto igual ao anterior
    anuncio.textContent = '';
    setTimeout(function () {
      anuncio.textContent = mensagem;
    }, 50);
  }

  function texto(elemento) {
    return elemento ? elemento.textContent.trim() : '';
  }

  botao.addEventListener('click', function () {
    pedir(true);
  });
  if (intervalo > 0) {
    setInterval(function () {
      pedir(false);
    }, intervalo);
  }
})();
