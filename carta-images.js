(function(){
  const cfg = window.OFARO_CONFIG || {};
  const spreadsheetId = String(cfg.spreadsheetId || '').trim();
  let images = [];

  function norm(value){
    return String(value == null ? '' : value)
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g,'')
      .trim()
      .toLowerCase();
  }

  function active(value){
    return !['no','false','0',''].includes(norm(value));
  }

  function ensureStyles(){
    if(document.getElementById('ofaro-carta-images-style')) return;
    const style = document.createElement('style');
    style.id = 'ofaro-carta-images-style';
    style.textContent = `
      .menu-item-content{display:flex;align-items:flex-start;gap:13px;min-width:0}
      .menu-item-copy{min-width:0;flex:1}
      .menu-item-photo{width:92px;height:92px;flex:0 0 92px;object-fit:cover;border-radius:16px;background:#f3f1ed;border:1px solid #e7e4df}
      @media(max-width:430px){.menu-item-photo{width:78px;height:78px;flex-basis:78px;border-radius:14px}.menu-item-content{gap:11px}}
      @media(max-width:360px){.menu-item-photo{width:88px;height:88px;flex-basis:88px}}
    `;
    document.head.appendChild(style);
  }

  function load(){
    return new Promise((resolve,reject)=>{
      if(!spreadsheetId) return resolve([]);
      const callback = '__ofaro_carta_img_' + Math.random().toString(36).slice(2);
      const script = document.createElement('script');
      let finished = false;
      let timer;

      function done(fn,value){
        if(finished) return;
        finished = true;
        clearTimeout(timer);
        try{ delete window[callback]; }catch(_){ window[callback] = undefined; }
        script.remove();
        fn(value);
      }

      window[callback] = response => {
        if(!response || response.status === 'error' || !response.table){
          return done(reject,new Error('Google Sheets no devolvió las imágenes'));
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
        const list = rows.map(r=>({
          id:r['id'] || '',
          seccion:r['seccion'] || '',
          nombre:r['nombre'] || '',
          url:r['url publica'] || '',
          alt:r['texto alternativo'] || '',
          activa:r['activa'],
          orden:Number(r['orden']) || 0
        })).filter(x=>norm(x.seccion)==='carta' && x.url && active(x.activa));
        done(resolve,list);
      };

      script.onerror = ()=>done(reject,new Error('No se pudo leer la pestaña Imagenes'));
      const params = new URLSearchParams({
        sheet:'Imagenes',
        headers:'1',
        tq:'select *',
        tqx:'out:json;responseHandler:' + callback,
        _:String(Date.now())
      });
      script.src = 'https://docs.google.com/spreadsheets/d/' + encodeURIComponent(spreadsheetId) + '/gviz/tq?' + params.toString();
      document.head.appendChild(script);
      timer = setTimeout(()=>done(resolve,[]),4500);
    });
  }

  function findForProduct(productName){
    const name = norm(productName);
    if(!name) return null;
    return images.find(item=>norm(item.nombre)===name || norm(item.alt)===name) || null;
  }

  function decorate(){
    if(!images.length) return;
    document.querySelectorAll('.menu-item').forEach(row=>{
      if(row.querySelector('.menu-item-photo')) return;
      const title = row.querySelector('h4');
      if(!title) return;
      const item = findForProduct(title.textContent);
      if(!item) return;

      const copy = row.firstElementChild;
      if(!copy || copy.classList.contains('menu-prices')) return;
      copy.classList.add('menu-item-copy');

      const wrapper = document.createElement('div');
      wrapper.className = 'menu-item-content';
      const img = document.createElement('img');
      img.className = 'menu-item-photo';
      img.src = item.url;
      img.alt = item.alt || title.textContent || 'Plato de Mesón O Faro';
      img.loading = 'lazy';
      img.decoding = 'async';

      row.insertBefore(wrapper,copy);
      wrapper.appendChild(img);
      wrapper.appendChild(copy);
    });
  }

  async function init(){
    ensureStyles();
    try{ images = await load(); }
    catch(err){ console.warn('O Faro: no se pudieron cargar las fotos de la carta.',err); images = []; }
    decorate();
    const container = document.getElementById('menuSections');
    if(container){
      new MutationObserver(()=>decorate()).observe(container,{childList:true,subtree:true});
    }
  }

  window.OfaroCartaImages = {load:()=>Promise.resolve(images.length ? images : load()),refresh:async()=>{images=await load();decorate();return images;}};

  if(document.readyState === 'loading') document.addEventListener('DOMContentLoaded',init,{once:true});
  else init();
})();
