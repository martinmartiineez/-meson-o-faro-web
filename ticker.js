(function(){
  const fallback = [
    {id:'A001',texto:'MENÚ DEL DÍA · Consulta lo que tenemos hoy',url:'menu-dia.html',activo:true,orden:1},
    {id:'A002',texto:'RESERVAS · Solicita tu mesa desde la web',url:'#reservas',activo:true,orden:2},
    {id:'A003',texto:'REDES · Síguenos para ver nuestras novedades',url:'#redes',activo:true,orden:3}
  ];

  function ensureShell(){
    let root = document.getElementById('infoTicker');
    if(root) return root;
    const hero = document.querySelector('.hero');
    if(!hero) return null;

    root = document.createElement('section');
    root.id = 'infoTicker';
    root.className = 'info-ticker';
    root.setAttribute('aria-label','Avisos y novedades');

    const viewport = document.createElement('div');
    viewport.className = 'ticker-viewport';
    const track = document.createElement('div');
    track.id = 'tickerTrack';
    track.className = 'ticker-track';
    viewport.appendChild(track);
    root.appendChild(viewport);
    hero.insertAdjacentElement('afterend',root);
    return root;
  }

  function safeHref(value){
    const raw = String(value || '').trim();
    if(!raw) return '';
    if(/^https?:\/\//i.test(raw)) return raw;
    if(/^#[-_a-z0-9]+$/i.test(raw)) return raw;
    if(/^(\.\/|\.\.\/|\/)?[-_a-z0-9./]+(?:\.html)?(?:[?#].*)?$/i.test(raw)) return raw;
    return '';
  }

  function normalize(items){
    return (Array.isArray(items) ? items : [])
      .filter(item => item && item.texto && item.activo !== false)
      .sort((a,b)=>(Number(a.orden)||0)-(Number(b.orden)||0));
  }

  function buildGroup(items, duplicate){
    const group = document.createElement('div');
    group.className = 'ticker-group';
    if(duplicate) group.setAttribute('aria-hidden','true');

    items.forEach(item=>{
      const href = safeHref(item.url);
      const node = href ? document.createElement('a') : document.createElement('span');
      node.className = 'ticker-item';
      if(href){
        node.href = href;
        const external = /^https?:\/\//i.test(href) && !href.startsWith(location.origin);
        if(external){ node.target = '_blank'; node.rel = 'noopener noreferrer'; }
        if(duplicate) node.tabIndex = -1;
      }

      const text = document.createElement('span');
      text.className = 'ticker-led-text';
      text.textContent = item.texto;
      node.appendChild(text);

      if(href){
        const hint = document.createElement('span');
        hint.className = 'ticker-hint';
        hint.textContent = '↗';
        hint.setAttribute('aria-hidden','true');
        node.appendChild(hint);
      }

      group.appendChild(node);
      const sep = document.createElement('span');
      sep.className = 'ticker-separator';
      sep.setAttribute('aria-hidden','true');
      group.appendChild(sep);
    });

    return group;
  }

  function render(items){
    const root = ensureShell();
    const track = document.getElementById('tickerTrack');
    if(!root || !track) return;

    const clean = normalize(items);
    if(!clean.length){ root.hidden = true; return; }

    root.hidden = false;
    track.replaceChildren(buildGroup(clean,false),buildGroup(clean,true));

    requestAnimationFrame(()=>{
      const first = track.firstElementChild;
      const width = first ? first.scrollWidth : 0;
      const seconds = Math.max(18,Math.min(55,width/42));
      track.style.setProperty('--ticker-duration',seconds.toFixed(1)+'s');
    });
  }

  async function init(){
    ensureShell();
    render(fallback);
    try{
      if(window.OfaroData && typeof window.OfaroData.loadPublic === 'function'){
        const data = await window.OfaroData.loadPublic();
        if(data && Array.isArray(data.avisos)) render(data.avisos);
      }
    }catch(err){
      console.warn('O Faro: no se pudieron cargar los avisos dinámicos.',err);
    }

    const root = document.getElementById('infoTicker');
    if(root){
      root.addEventListener('pointerdown',()=>root.classList.add('is-paused'));
      const resume = ()=>setTimeout(()=>root.classList.remove('is-paused'),350);
      root.addEventListener('pointerup',resume);
      root.addEventListener('pointercancel',resume);
    }
  }

  if(document.readyState === 'loading') document.addEventListener('DOMContentLoaded',init);
  else init();
})();
