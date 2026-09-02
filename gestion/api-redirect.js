(() => {
'use strict';
const NEW_API = 'https://script.google.com/macros/s/AKfycbxPSc2Ep3IkZ7lDV1tkm3dnBWgMV0QVrRfx50WJJJXN-q1xELZSWrfdJt3lTxaAXG2miA/exec';
const originalFetch = window.fetch.bind(window);
window.fetch = function(input, options={}) {
  const method = String((options && options.method) || 'GET').toUpperCase();
  const url = typeof input === 'string' ? input : (input && input.url) || '';
  if (method === 'POST' && url.includes('script.google.com/macros/s/')) {
    return originalFetch(NEW_API, options);
  }
  return originalFetch(input, options);
};
window.OFARO_API_URL = NEW_API;
})();
