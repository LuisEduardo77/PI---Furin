/*
 * Janelas do Termo de uso e da Política de privacidade na tela de login (DEC-47).
 *
 * Cada botão [data-abrir-janela] abre o <dialog> indicado como modal (showModal): o resto
 * da tela fica inerte, o foco vai para o título da janela (o leitor de tela lê o nome dela)
 * e a tecla Esc fecha, como faz o próprio navegador. "Fechar" [data-fechar-janela] fecha, e
 * o foco volta ao botão que abriu (A11 §5.1.13).
 */
(function () {
  'use strict';

  Array.prototype.forEach.call(document.querySelectorAll('[data-abrir-janela]'), function (botao) {
    var janela = document.getElementById(botao.getAttribute('data-abrir-janela'));
    if (!janela || typeof janela.showModal !== 'function') {
      return;
    }
    botao.addEventListener('click', function () {
      janela.showModal();
      var titulo = janela.querySelector('h2');
      if (titulo) {
        titulo.setAttribute('tabindex', '-1');
        titulo.focus();
      }
    });
    janela.addEventListener('close', function () {
      botao.focus();
    });
  });

  Array.prototype.forEach.call(document.querySelectorAll('[data-fechar-janela]'), function (botao) {
    botao.addEventListener('click', function () {
      botao.closest('dialog').close();
    });
  });
})();
