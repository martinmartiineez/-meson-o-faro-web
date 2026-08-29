(function(){
  function norm(value){
    return String(value == null ? '' : value).normalize('NFD').replace(/[\u0300-\u036f]/g,'').trim().toLowerCase();
  }

  function active(item){
    return item && item.url && !['no','false','0',''].includes(norm(item.activa));
  }

  function applyCardBackground(element, item){
    if(!element || !item || !item.url) return;
    const safeUrl = String(item.url).replace(/"/g,'%22');
    element.style.backgroundImage = 'linear-gradient(180deg,rgba(0,0,0,.52),rgba(0,0,0,.74)),url("' + safeUrl + '")';
    element.style.backgroundSize = 'cover';
    element.style.backgroundPosition = 'center';
    element.style.backgroundRepeat = 'no-repeat';
  }

  function applyImages(images){
    const clean = Array.isArray(images) ? images.filter(active) : [];
    if(!clean.length) return false;

    const ordered = clean.slice().sort((a,b)=>(Number(a.orden)||0)-(Number(b.orden)||0));

    const heroImage = ordered.find(item => norm(item.nombre) === 'portada principal');
    const hero = document.querySelector('.hero');
    if(heroImage && hero){
      const safeUrl = String(heroImage.url).replace(/"/g,'%22');
      hero.style.backgroundImage = 'linear-gradient(180deg,rgba(0,0,0,.16),rgba(0,0,0,.25) 45%,rgba(0,0,0,.78)),url("' + safeUrl + '")';
      hero.style.backgroundPosition = 'center';
      hero.style.backgroundSize = 'cover';
    }

    const menuBackground = ordered.find(item => norm(item.nombre) === 'fondo menu del dia');
    applyCardBackground(document.getElementById('card-menu-dia'), menuBackground);

    const reserveBackground = ordered.find(item => norm(item.nombre) === 'fondo reserva');
    applyCardBackground(document.getElementById('card-reserva'), reserveBackground);

    const presentation = ordered.find(item => {
      const name = norm(item.nombre);
      return norm(item.seccion) === 'presentacion' && !['portada principal','fondo menu del dia','fondo reserva'].includes(name);
    });
    const aboutImage = document.getElementById('aboutImage');
    if(presentation && aboutImage){
      aboutImage.src = presentation.url;
      aboutImage.alt = presentation.alt || presentation.nombre || 'Mesón O Faro';
    }

    const galleryItems = ordered.filter(item => norm(item.seccion) === 'galeria');
    const gallery = document.getElementById('homeGallery');
    if(gallery && galleryItems.length){
      gallery.replaceChildren();
      galleryItems.forEach(item => {
        const img = document.createElement('img');
        img.src = item.url;
        img.alt = item.alt || item.nombre || 'Mesón O Faro';
        img.loading = 'lazy';
        gallery.appendChild(img);
      });
    }
    return true;
  }

  function loadImagesFromSheet(){
    return new Promise((resolve,reject)=>{
      const cfg = window.OFARO_CONFIG || {};
      const spreadsheetId = String(cfg.spreadsheetId || '').trim();
      if(!spreadsheetId) return reject(new Error('Falta spreadsheetId'));

      const callback = '__ofaro_img_' + Math.random().toString(36).slice(2);
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
          return finish(reject,new Error('Google Sheets no devolvió imágenes'));
        }

        const labels = (response.table.cols || []).map((col,i)=>norm(col.label || col.id || ('col'+i)));
        const rows = (response.table.rows || []).map(row=>{
          const obj = {};
          labels.forEach((label,i)=>{
            const cell = row.c && row.c[i];
            obj[label] = cell && cell.v !== null && typeof cell.v !== 'undefined' ? cell.v : '';
          });
          return obj;
        });

        const images = rows.map(r=>({
          seccion:r['seccion'],
          nombre:r['nombre'],
          url:r['url publica'],
          alt:r['texto alternativo'],
          activa:r['activa'],
          orden:r['orden']
        })).filter(x=>x.nombre || x.url);

        finish(resolve,images);
      };

      script.onerror = () => finish(reject,new Error('No se pudo acceder a la hoja Imagenes'));
      const params = new URLSearchParams({sheet:'Imagenes',headers:'1',tq:'select *',tqx:'out:json;responseHandler:' + callback,_:String(Date.now())});
      script.src = 'https://docs.google.com/spreadsheets/d/' + encodeURIComponent(spreadsheetId) + '/gviz/tq?' + params.toString();
      document.head.appendChild(script);
      timer = setTimeout(()=>finish(reject,new Error('Tiempo de espera agotado')),8000);
    });
  }

  async function init(){
    try{
      if(window.OfaroData && typeof window.OfaroData.loadPublic === 'function'){
        const data = await window.OfaroData.loadPublic();
        if(applyImages(data && data.imagenes)) return;
      }
    }catch(err){
      console.warn('O Faro: la API no devolvió imágenes dinámicas.', err);
    }

    try{
      const images = await loadImagesFromSheet();
      applyImages(images);
    }catch(err){
      console.warn('O Faro: no se pudieron cargar las imágenes dinámicas.', err);
    }
  }

  if(document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
})();
