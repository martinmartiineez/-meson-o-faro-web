const CACHE='ofaro-gestion-v8-20260902';
const SHELL=['./','index.html','styles.css?v=20260901-2','api-v2.js?v=20260902-1','app.js?v=20260901-2','remote-print-v2.js?v=20260902-1','promotions.js?v=20260902-2','promotions-qr.js?v=20260902-1','ticket-studio.js?v=20260902-1','manifest.webmanifest','icon.svg'];
self.addEventListener('install',event=>{event.waitUntil(caches.open(CACHE).then(cache=>cache.addAll(SHELL)).then(()=>self.skipWaiting()));});
self.addEventListener('activate',event=>{event.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(k=>k!==CACHE).map(k=>caches.delete(k)))).then(()=>self.clients.claim()));});
self.addEventListener('fetch',event=>{
  if(event.request.method!=='GET')return;
  const url=new URL(event.request.url);
  if(url.origin!==location.origin)return;
  const navigation=event.request.mode==='navigate'||url.pathname==='/gestion/'||url.pathname.endsWith('/gestion/index.html');
  if(navigation){
    event.respondWith(fetch(event.request,{cache:'no-store'}).then(r=>{if(r&&r.ok){const copy=r.clone();caches.open(CACHE).then(c=>c.put(event.request,copy));}return r;}).catch(()=>caches.match(event.request).then(x=>x||caches.match('./'))));
    return;
  }
  event.respondWith(caches.match(event.request).then(cached=>{
    const fresh=fetch(event.request).then(response=>{if(response&&response.ok){const copy=response.clone();caches.open(CACHE).then(cache=>cache.put(event.request,copy));}return response;}).catch(()=>cached);
    return cached||fresh;
  }));
});