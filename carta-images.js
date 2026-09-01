(function(){
  const cfg = window.OFARO_CONFIG || {};
  const spreadsheetId = String(cfg.spreadsheetId || '').trim();

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
        const images = rows.map(r=>({
          id:r['id'] || '',
          seccion:r['seccion'] || '',
          nombre:r['nombre'] || '',
          url:r['url publica'] || '',
          alt:r['texto alternativo'] || '',
          activa:r['activa'],
          orden:Number(r['orden']) || 0
        })).filter(x=>norm(x.seccion)==='carta' && x.url && active(x.activa));
        done(resolve,images);
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

  function forProduct(item,images){
    if(!item || !Array.isArray(images)) return null;
    const id = norm(item.id);
    const name = norm(item.producto);
    return images.find(img=>{
      const ref = norm(img.nombre);
      return ref === id || ref === name || ref.includes(id) || ref.includes(name);
    }) || null;
  }

  window.OfaroCartaImages = {load,forProduct};
})();
