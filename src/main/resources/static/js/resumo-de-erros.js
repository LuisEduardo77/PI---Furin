/*
 * Formulários com resumo de erros (T-19; RF04, exceções; A11 §5.9.9): quando a página volta
 * com erros, o foco vai para o resumo. Fica em arquivo próprio por causa da CSP.
 */
(function () {
  'use strict';

  var resumo = document.getElementById('resumo-erros');
  if (resumo) {
    resumo.focus();
  }
})();
