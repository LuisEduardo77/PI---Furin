/*
 * Medição do RNF02 na tela (T-33; CA-RNF02-01, 02 e 04; DEC-28, G4).
 *
 * Como usar (o passo a passo completo está em docs/evidencias/rnf02-medicao.md):
 *  1. Rode o protótipo (perfil prototipo) num banco com a carga
 *     scripts/desempenho/carga-800-alunos.sql e entre com uma conta de funcionário.
 *  2. Abra /portaria, abra as ferramentas do navegador (F12), aba Console.
 *  3. Cole este arquivo inteiro e tecle Enter. Não mexa na tela até aparecer "Medição
 *     concluída" (cerca de 1 minuto).
 *
 * O que mede: 30 vezes seguidas, o clique em "Ler carteirinha de Aluno Desempenho …" no
 * painel "Simular leitura (protótipo)" até a liberação aparecer NOS DOIS PAINÉIS
 * (funcionário: "aguardando passagem"; aluno: "Pode passar"), e o clique em "Simular passagem
 * (giro)" até a confirmação aparecer nos dois ("registrada"). O tempo é o de
 * performance.now(), conferido a cada quadro da tela (requestAnimationFrame).
 *
 * Só lê a tela e clica nos botões da própria tela: não chama nenhuma rota por fora. Cada
 * leitura é de um aluno diferente, e cada uma é confirmada pelo giro antes da próxima (RN05).
 */
(async function medirRnf02() {
  'use strict';

  var RODADA = 30;
  var LIMITE_MS = 2000;
  var DESISTIR_MS = 10000;

  var painel = document.getElementById('painel-funcionario');
  var monitor = document.getElementById('monitor-aluno');
  var giro = document.getElementById('botao-giro');
  var botoes = Array.prototype.filter.call(document.querySelectorAll('[data-simular-leitura]'), function (botao) {
    return botao.textContent.indexOf('Aluno Desempenho') >= 0;
  });

  if (!painel || !monitor || !giro) {
    console.error('Abra a tela /portaria no perfil prototipo antes de rodar a medição.');
    return;
  }
  if (botoes.length < RODADA) {
    console.error('Faltam carteirinhas da carga de referência (encontradas: ' + botoes.length
      + '). Rode scripts/desempenho/carga-800-alunos.sql.');
    return;
  }

  function texto(elemento) {
    return elemento.textContent.replace(/\s+/g, ' ');
  }

  /** Espera, quadro a quadro, até a condição valer; devolve o instante em que valeu. */
  function esperar(condicao) {
    var inicio = performance.now();
    return new Promise(function (pronto, falhou) {
      (function conferir() {
        if (condicao()) {
          pronto(performance.now());
        } else if (performance.now() - inicio > DESISTIR_MS) {
          falhou(new Error('A tela não respondeu em ' + DESISTIR_MS / 1000 + ' s: ' + texto(painel)));
        } else {
          requestAnimationFrame(conferir);
        }
      })();
    });
  }

  function pausa(ms) {
    return new Promise(function (pronto) { setTimeout(pronto, ms); });
  }

  var medidas = [];
  // De 26 em 26, como o teste automatizado: alunos espalhados pela carga
  for (var i = 0; i < RODADA; i++) {
    var botao = botoes[(i * 26) % botoes.length];
    var nome = botao.textContent.replace('Ler carteirinha de ', '').trim();

    var inicio = performance.now();
    botao.click();
    var liberada = await esperar(function () {
      return texto(painel).indexOf(nome) >= 0 && texto(painel).indexOf('aguardando passagem') >= 0
        && texto(monitor).indexOf('Pode passar') >= 0;
    });

    var inicioDoGiro = performance.now();
    giro.click();
    var registrada = await esperar(function () {
      return texto(painel).indexOf('registrada às') >= 0 && texto(monitor).indexOf('registrada') >= 0;
    });

    medidas.push({ aluno: nome, leituraMs: Math.round(liberada - inicio), giroMs: Math.round(registrada - inicioDoGiro) });
    await pausa(300);
  }

  function resumo(campo) {
    var valores = medidas.map(function (m) { return m[campo]; });
    var soma = valores.reduce(function (a, b) { return a + b; }, 0);
    return { maximo: Math.max.apply(null, valores), media: Math.round(soma / valores.length) };
  }

  var leitura = resumo('leituraMs');
  var giroResumo = resumo('giroMs');
  console.table(medidas);

  var linhas = ['| # | Aluno | Leitura (ms) | Giro (ms) |', '|---|---|---|---|'];
  medidas.forEach(function (m, indice) {
    linhas.push('| ' + (indice + 1) + ' | ' + m.aluno + ' | ' + m.leituraMs + ' | ' + m.giroMs + ' |');
  });
  linhas.push('| **Máximo** | | ' + leitura.maximo + ' | ' + giroResumo.maximo + ' |');
  linhas.push('| **Média** | | ' + leitura.media + ' | ' + giroResumo.media + ' |');
  var tabela = linhas.join('\n');
  console.log(tabela);
  if (typeof copy === 'function') {
    copy(tabela);
    console.log('A tabela foi copiada para a área de transferência.');
  }
  var dentro = leitura.maximo <= LIMITE_MS && giroResumo.maximo <= LIMITE_MS;
  console.log('Medição concluída. Leitura: máximo ' + leitura.maximo + ' ms, média ' + leitura.media
    + ' ms. Giro: máximo ' + giroResumo.maximo + ' ms, média ' + giroResumo.media + ' ms. '
    + (dentro ? 'Todas dentro de 2 s.' : 'ATENÇÃO: alguma passou de 2 s.'));
})();
