/*
 * Senha temporária exibida uma vez (T-43; RF26, "Criar", passo 3). Sem este script, a senha
 * aparece em texto. Com ele, começa mascarada, e os botões "Mostrar a senha" e "Copiar a
 * senha" aparecem. O resultado da cópia vai para um role="status", sem mover o foco
 * (A11 §5.13.8). Fica em arquivo próprio por causa da CSP.
 */
(function () {
  'use strict';

  var area = document.querySelector('[data-senha-temporaria]');
  if (!area) {
    return;
  }
  var senha = document.getElementById('senha-temporaria');
  var mascara = document.getElementById('senha-mascarada');
  var botoes = area.querySelector('[data-botoes-senha]');
  var mostrar = document.getElementById('mostrar-senha');
  var copiar = document.getElementById('copiar-senha');
  var resultado = document.getElementById('resultado-copia');

  function exibir(visivel) {
    senha.hidden = !visivel;
    mascara.hidden = visivel;
    mostrar.textContent = visivel ? 'Ocultar a senha' : 'Mostrar a senha';
  }

  exibir(false);
  botoes.hidden = false;

  mostrar.addEventListener('click', function () {
    exibir(senha.hidden);
  });

  copiar.addEventListener('click', function () {
    if (!navigator.clipboard) {
      resultado.textContent = 'Não foi possível copiar. Use "Mostrar a senha" e anote-a.';
      return;
    }
    navigator.clipboard.writeText(senha.textContent).then(function () {
      resultado.textContent = 'Senha copiada.';
    }, function () {
      resultado.textContent = 'Não foi possível copiar. Use "Mostrar a senha" e anote-a.';
    });
  });
})();
