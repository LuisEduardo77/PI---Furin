/*
 * Versão para impressão do histórico (T-36; RF20, passo 4): "Imprimir" abre a impressão do
 * navegador. Arquivo próprio por causa da CSP (sem script embutido). O botão é um <button>,
 * e por isso já funciona com Enter e espaço (A11 §5.8.6).
 */
(function () {
  'use strict';

  var botao = document.getElementById('botao-imprimir');
  if (!botao) {
    return;
  }
  botao.addEventListener('click', function () {
    window.print();
  });
})();
