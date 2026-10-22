import test from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM, CookieJar } from 'jsdom';
import { CONSENT_JS } from '../src/consent.js';

function visit(url, jar) {
  const dom = new JSDOM('<!doctype html><html><head></head><body><main>O Faro</main></body></html>', {
    url, cookieJar: jar, runScripts: 'outside-only',
  });
  dom.window.eval(CONSENT_JS);
  dom.window.document.dispatchEvent(new dom.window.Event('DOMContentLoaded'));
  return dom;
}

test('la decisión de rechazar se conserva al cambiar de Inicio a Carta y Menú', () => {
  const cookies = new CookieJar();
  const home = visit('https://ofaro-pruebas.example/', cookies);
  const reject = [...home.window.document.querySelectorAll('button')].find(b => b.textContent === 'Rechazar');
  assert.ok(reject, 'Debe mostrar aviso solo cuando falta una decisión');
  reject.click();
  assert.equal(home.window.document.getElementById('ofPrivacyPanel'), null);
  assert.match(home.window.document.cookie, /ofaro_privacy_choice=/);
  home.window.close();

  for (const page of ['/carta.html', '/menu-dia.html', '/index.html']) {
    const dom = visit('https://ofaro-pruebas.example' + page, cookies);
    dom.window.localStorage.clear();
    assert.equal(dom.window.document.getElementById('ofPrivacyPanel'), null, 'No repetir aviso en ' + page);
    dom.window.close();
  }
});

test('reconoce una preferencia caducada y vuelve a solicitarla', () => {
  const cookies = new CookieJar();
  const dom = visit('https://ofaro-pruebas.example/', cookies);
  assert.ok(dom.window.document.getElementById('ofPrivacyPanel'));
  dom.window.close();
});
