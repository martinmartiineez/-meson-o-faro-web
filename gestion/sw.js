const CACHE = 'ofaro-gestion-v6-20260902';
const SHELL = ['./','index.html','styles.css?v=20260901-2','app.js?v=20260901-2','remote-print.js?v=20260902-1','promotions-compat.js?v=20260902-3','promotions.js?v=20260902-2','manifest.webmanifest','icon.svg'];
self.addEventListener('install', event => {
  event.waitUntil(caches.open(CACHE).then(cache => cache.addAll(SHELL)).then(() => self.skipWaiting()));
});
self.addEventListener('activate', event => {
  event.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k)))).then(() => self.clients.claim()));
});
self.addEventListener('fetch', event => {
  if(event.request.method !== 'GET') return;
  const url = new URL(event.request.url);
  if(url.origin !== location.origin) return;
  event.respondWith(caches.match(event.request).then(cached => {
    const fresh = fetch(event.request).then(response => {
      if(response && response.ok){
        const copy = response.clone();
        caches.open(CACHE).then(cache => cache.put(event.request, copy));
      }
      return response;
    }).catch(() => cached);
    return cached || fresh;
  }));
});