/*
 * Máscara do telefone do contato (DEC-47; RF04, RF08): enquanto se digita, o campo aceita só
 * números, até 11 dígitos (DDD + 9), e mostra (12) 91234-5678 para celular ou
 * (12) 1234-5678 para fixo. O servidor confere o telefone de novo (ConferenciasDoAluno),
 * e o maxlength do campo limita o tamanho mesmo sem este script.
 */
(function () {
  'use strict';

  var MAX_DIGITOS = 11;

  function formatar(digitos) {
    if (digitos.length === 0) {
      return '';
    }
    if (digitos.length <= 2) {
      return '(' + digitos;
    }
    var ddd = digitos.substring(0, 2);
    var numero = digitos.substring(2);
    if (numero.length <= 4) {
      return '(' + ddd + ') ' + numero;
    }
    // Com 9 dígitos (celular), o hífen fica depois do quinto; com até 8 (fixo), depois do quarto
    var antesDoHifen = numero.length === 9 ? 5 : 4;
    return '(' + ddd + ') ' + numero.substring(0, antesDoHifen) + '-' + numero.substring(antesDoHifen);
  }

  function aplicar(campo) {
    var digitos = campo.value.replace(/\D/g, '').substring(0, MAX_DIGITOS);
    var formatado = formatar(digitos);
    if (campo.value !== formatado) {
      campo.value = formatado;
    }
  }

  Array.prototype.forEach.call(document.querySelectorAll('[data-mascara-telefone]'), function (campo) {
    // Um valor antigo, como "+55 11 900000001", fica como está até a pessoa mexer no campo
    campo.addEventListener('input', function () {
      aplicar(campo);
    });
  });
})();
