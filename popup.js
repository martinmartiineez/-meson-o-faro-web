(function(){
  const CACHE_KEY = 'ofaro-popup-fast-cache-v1';
  const CACHE_MAX_AGE = 30000;
  let closedThisLoad = false;

  function text(value){ return String(value == null ? '' : value).trim(); }
  function active(value){
    if(typeof value === 'boolean') return value;
    return !/^(no|false|0)$/i.test(text(value));
  }
  function safeHref(value){
    const raw = text(value);
    if(!raw) return '';
    if(/^https?:\/\//i.test(raw)) return raw;
    if(/^#[-_a-z0-9]+$/i.test(raw)) return raw;
    if(/^(\.\/|\.\.\/|\/)?[-_a-z0-9./]+(?:\.html)?(?:[?#].*)?$/i.test(raw)) return raw;
    return '';
  }
  function safeImage(value){
    const raw = text(value);
    return /^https?:\/\//i.test(raw) ? raw : '';
  }
  function normalize(items){
    return (Array.isArray(items) ? items : [])
      .map(item=>({
        id:text(item && item.id),
        activa:active(item && item.activa),
        etiqueta:text(item && item.etiqueta),
        titulo:text(item && item.titulo),
        texto:text(item && item.texto),
        imagen:safeImage(item && (item.imagen || item.imagenUrl || item['imagen url'])),
        boton:text(item && (item.boton || item.textoBoton || item['texto boton'])),
        url:safeHref(item && (item.url || item.urlBoton || item['url boton'])),
        orden:Number(item && item.orden) || 0
      }))
      .filter(item=>item.activa && (item.titulo || item.texto || item.imagen))
      .sort((a,b)=>a.orden-b.orden);
  }

  function readFastCache(){
    try{
      const raw = sessionStorage.getItem(CACHE_KEY);
      if(!raw) return [];
      const parsed = JSON.parse(raw);
      if(!parsed || !Array.isArray(parsed.items) || Date.now() - Number(parsed.time || 0) > CACHE_MAX_AGE){
        sessionStorage.removeItem(CACHE_KEY);
        return [];
      }
      return normalize(parsed.items);
    }catch(_){ return []; }
  }

  function writeFastCache(items){
    try{
      sessionStorage.setItem(CACHE_KEY,JSON.stringify({time:Date.now(),items:items || []}));
    }catch(_){}
  }

  function clearFastCache(){
    try{ sessionStorage.removeItem(CACHE_KEY); }catch(_){}
  }

  function createShell(){
    let root = document.getElementById('ofaroPopup');
    if(root) return root;

    root = document.createElement('div');
    root.id = 'ofaroPopup';
    root.className = 'ofaro-popup';
    root.hidden = true;
    root.setAttribute('role','dialog');
    root.setAttribute('aria-modal','true');
    root.setAttribute('aria-labelledby','ofaroPopupTitle');

    root.innerHTML = '<article class="ofaro-popup-card"><button class="ofaro-popup-close" type="button" aria-label="Cerrar aviso">×</button><div class="ofaro-popup-media" hidden><img alt=""></div><div class="ofaro-popup-content"><div class="ofaro-popup-label" hidden></div><h2 class="ofaro-popup-title" id="ofaroPopupTitle"></h2><p class="ofaro-popup-text"></p><div class="ofaro-popup-actions"><a class="ofaro-popup-action" hidden></a></div></div></article>';
    document.body.appendChild(root);
    return root;
  }

  function hide(){
    const root = document.getElementById('ofaroPopup');
    if(root) root.hidden = true;
    document.body.classList.remove('ofaro-popup-open');
  }

  function show(item){
    if(closedThisLoad || !item) return;

    const root = createShell();
    const card = root.querySelector('.ofaro-popup-card');
    const media = root.querySelector('.ofaro-popup-media');
    const img = media.querySelector('img');
    const label = root.querySelector('.ofaro-popup-label');
    const title = root.querySelector('.ofaro-popup-title');
    const body = root.querySelector('.ofaro-popup-text');
    const action = root.querySelector('.ofaro-popup-action');
    const close = root.querySelector('.ofaro-popup-close');

    if(item.imagen){
      img.src = item.imagen;
      img.alt = item.titulo || item.etiqueta || 'Aviso de Mesón O Faro';
      media.hidden = false;
      card.classList.remove('no-image');
    }else{
      img.removeAttribute('src');
      media.hidden = true;
      card.classList.add('no-image');
    }

    label.textContent = item.etiqueta;
    label.hidden = !item.etiqueta;
    title.textContent = item.titulo;
    title.hidden = !item.titulo;
    body.textContent = item.texto;
    body.hidden = !item.texto;

    if(item.url && item.boton){
      action.textContent = item.boton;
      action.href = item.url;
      const external = /^https?:\/\//i.test(item.url) && !item.url.startsWith(location.origin);
      if(external){ action.target = '_blank'; action.rel = 'noopener noreferrer'; }
      else{ action.removeAttribute('target'); action.removeAttribute('rel'); }
      action.hidden = false;
    }else{
      action.hidden = true;
      action.removeAttribute('href');
    }

    function dismiss(){
      closedThisLoad = true;
      hide();
      document.removeEventListener('keydown',onKey);
    }
    function onKey(e){ if(e.key === 'Escape') dismiss(); }

    close.onclick = dismiss;
    root.onclick = e=>{ if(e.target === root) dismiss(); };
    action.onclick = ()=>{};
    document.addEventListener('keydown',onKey);

    root.hidden = false;
    document.body.classList.add('ofaro-popup-open');
    setTimeout(()=>close.focus({preventScroll:true}),20);
  }

  async function init(){
    const cached = readFastCache();
    if(cached.length) show(cached[0]);

    if(!window.OfaroData || typeof window.OfaroData.loadPublic !== 'function') return;

    try{
      const data = await window.OfaroData.loadPublic();
      const items = normalize(data && (data.popups || data.popup));
      if(items.length){
        writeFastCache(items);
        if(!closedThisLoad) show(items[0]);
      }else{
        clearFastCache();
        if(!closedThisLoad) hide();
      }
    }catch(err){
      console.warn('O Faro: no se pudo cargar la ventana emergente.',err);
    }
  }

  if(document.readyState === 'loading') document.addEventListener('DOMContentLoaded',init,{once:true});
  else init();
})();
