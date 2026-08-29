(function(){
  const fallback = [
    {id:'S001',red:'Instagram',url:'',activa:true,orden:1},
    {id:'S002',red:'Facebook',url:'',activa:true,orden:2},
    {id:'S003',red:'TikTok',url:'',activa:true,orden:3},
    {id:'S004',red:'WhatsApp',url:'',activa:true,orden:4}
  ];

  function norm(value){
    return String(value == null ? '' : value).normalize('NFD').replace(/[\u0300-\u036f]/g,'').trim().toLowerCase();
  }

  function active(value){
    return !['no','false','0'].includes(norm(value));
  }

  function safeHref(value){
    const raw = String(value || '').trim();
    if(!raw) return '';
    if(/^(https?:|mailto:|tel:)/i.test(raw)) return raw;
    if(/^www\./i.test(raw)) return 'https://' + raw;
    if(/^[a-z0-9.-]+\.[a-z]{2,}(\/.*)?$/i.test(raw)) return 'https://' + raw;
    return '';
  }

  function initials(name){
    const n = norm(name);
    const known = {
      instagram:'IG',facebook:'FB',tiktok:'TT',whatsapp:'WA',youtube:'YT',tripadvisor:'TA',x:'X',twitter:'X',threads:'TH'
    };
    if(known[n]) return known[n];
    return String(name || 'RS').split(/\s+/).filter(Boolean).slice(0,2).map(x=>x[0]).join('').toUpperCase() || 'RS';
  }

  function render(items){
    const grid = document.getElementById('socialLinks');
    const section = document.getElementById('redes');
    if(!grid || !section) return;

    const clean = (Array.isArray(items) ? items : [])
      .filter(item => item && item.red && item.activa !== false)
      .sort((a,b)=>(Number(a.orden)||0)-(Number(b.orden)||0));

    if(!clean.length){
      section.hidden = true;
      return;
    }

    section.hidden = false;
    grid.replaceChildren();

    clean.forEach(item => {
      const href = safeHref(item.url);
      const card = href ? document.createElement('a') : document.createElement('div');
      card.className = 'social-card' + (href ? '' : ' disabled');
      if(href){
        card.href = href;
        card.target = '_blank';
        card.rel = 'noopener noreferrer';
        card.setAttribute('aria-label','Abrir ' + item.red);
      }else{
        card.setAttribute('aria-label',item.red + ', enlace pendiente');
      }

      const top = document.createElement('div');
      top.className = 'social-card-top';
      const mark = document.createElement('span');
      mark.className = 'social-mark';
      mark.textContent = initials(item.red);
      const arrow = document.createElement('span');
      arrow.className = 'social-arrow';
      arrow.setAttribute('aria-hidden','true');
      arrow.textContent = '↗';
      top.append(mark,arrow);

      const bottom = document.createElement('div');
      const name = document.createElement('div');
      name.className = 'social-name';
      name.textContent = item.red;
      const meta = document.createElement('span');
      meta.className = 'social-meta';
      meta.textContent = href ? 'Abrir perfil' : 'Próximamente';
      bottom.append(name,meta);

      card.append(top,bottom);
      grid.appendChild(card);
    });
  }

  function loadFromSheet(){
    return new Promise((resolve,reject)=>{
      const cfg = window.OFARO_CONFIG || {};
      const spreadsheetId = String(cfg.spreadsheetId || '').trim();
      if(!spreadsheetId) return reject(new Error('Falta spreadsheetId'));

      const callback = '__ofaro_social_' + Math.random().toString(36).slice(2);
      const script = document.createElement('script');
      let done = false;
      let timer;

      function finish(fn,value){
        if(done) return;
        done = true;
        clearTimeout(timer);
        try{ delete window[callback]; }catch(_){ window[callback] = undefined; }
        script.remove();
        fn(value);
      }

      window[callback] = response => {
        if(!response || response.status === 'error' || !response.table){
          return finish(reject,new Error('Google Sheets no devolvió redes sociales'));
        }
        const labels = (response.table.cols || []).map((col,i)=>norm(col.label || col.id || ('col'+i)));
        const rows = (response.table.rows || []).map(row=>{
          const out = {};
          labels.forEach((label,i)=>{
            const cell = row.c && row.c[i];
            out[label] = cell && cell.v !== null && typeof cell.v !== 'undefined' ? cell.v : '';
          });
          return out;
        });
        const items = rows.map(r=>({
          id:r['id'],
          red:r['red'],
          url:r['url'],
          activa:active(r['activa']),
          orden:Number(r['orden']) || 0
        })).filter(x=>x.red);
        finish(resolve,items);
      };

      script.onerror = () => finish(reject,new Error('No se pudo acceder a la pestaña Redes sociales'));
      const params = new URLSearchParams({gid:'136543210',headers:'1',tq:'select *',tqx:'out:json;responseHandler:' + callback,_:String(Date.now())});
      script.src = 'https://docs.google.com/spreadsheets/d/' + encodeURIComponent(spreadsheetId) + '/gviz/tq?' + params.toString();
      document.head.appendChild(script);
      timer = setTimeout(()=>finish(reject,new Error('Tiempo de espera agotado')),8000);
    });
  }

  async function init(){
    render(fallback);

    try{
      if(window.OfaroData && typeof window.OfaroData.loadPublic === 'function'){
        const data = await window.OfaroData.loadPublic();
        if(data && Array.isArray(data.redes)){
          render(data.redes);
          return;
        }
      }
    }catch(err){
      console.warn('O Faro: la API no devolvió las redes sociales.',err);
    }

    try{
      const items = await loadFromSheet();
      render(items.length ? items : fallback);
    }catch(err){
      console.warn('O Faro: no se pudieron cargar las redes sociales.',err);
    }
  }

  if(document.readyState === 'loading') document.addEventListener('DOMContentLoaded',init);
  else init();
})();

(function(){
  if(!document.querySelector('link[data-ofaro-ticker]')){
    const link = document.createElement('link');
    link.rel = 'stylesheet';
    link.href = 'ticker.css?v=20260830-0006';
    link.dataset.ofaroTicker = '1';
    document.head.appendChild(link);
  }
  if(!document.querySelector('script[data-ofaro-ticker]')){
    const script = document.createElement('script');
    script.src = 'ticker.js?v=20260830-0006';
    script.defer = true;
    script.dataset.ofaroTicker = '1';
    document.head.appendChild(script);
  }
})();
