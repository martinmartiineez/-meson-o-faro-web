(function(){
  const fullFallback = 13;
  const halfFallback = 10;

  function norm(value){
    return String(value == null ? '' : value).normalize('NFD').replace(/[\u0300-\u036f]/g,'').trim().toLowerCase();
  }

  function toNumber(value){
    if(typeof value === 'number' && Number.isFinite(value)) return value;
    const cleaned = String(value == null ? '' : value)
      .replace(/\s/g,'')
      .replace(/€/g,'')
      .replace(/\./g,'')
      .replace(',','.');
    const n = Number(cleaned);
    return Number.isFinite(n) ? n : null;
  }

  function formatPrice(value){
    return value.toLocaleString('es-ES',{minimumFractionDigits:0,maximumFractionDigits:2}) + ' €';
  }

  function apply(full,half){
    const fullValue = Number.isFinite(full) ? full : fullFallback;
    const halfValue = Number.isFinite(half) ? half : halfFallback;
    document.querySelectorAll('[data-menu-price-full]').forEach(el => { el.textContent = formatPrice(fullValue); });
    document.querySelectorAll('[data-menu-price-half]').forEach(el => { el.textContent = formatPrice(halfValue); });
  }

  function loadConfig(){
    return new Promise((resolve,reject)=>{
      const cfg = window.OFARO_CONFIG || {};
      const spreadsheetId = String(cfg.spreadsheetId || '').trim();
      if(!spreadsheetId) return reject(new Error('Falta spreadsheetId'));

      const sheetName = (cfg.sheets && cfg.sheets.config) || 'Configuracion';
      const callback = '__ofaro_prices_' + Math.random().toString(36).slice(2);
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
          return finish(reject,new Error('No se pudo leer Configuracion'));
        }

        const labels = (response.table.cols || []).map((col,i)=>norm(col.label || col.id || ('col'+i)));
        const values = {};
        (response.table.rows || []).forEach(row => {
          const obj = {};
          labels.forEach((label,i)=>{
            const cell = row.c && row.c[i];
            obj[label] = cell && cell.v !== null && typeof cell.v !== 'undefined' ? cell.v : '';
          });
          const key = norm(obj.campo);
          if(key) values[key] = obj.valor;
        });

        finish(resolve,{
          full:toNumber(values['precio menu']),
          half:toNumber(values['precio medio menu'])
        });
      };

      script.onerror = () => finish(reject,new Error('No se pudo acceder a Google Sheets'));
      const params = new URLSearchParams({sheet:sheetName,headers:'1',tq:'select *',tqx:'out:json;responseHandler:' + callback,_:String(Date.now())});
      script.src = 'https://docs.google.com/spreadsheets/d/' + encodeURIComponent(spreadsheetId) + '/gviz/tq?' + params.toString();
      document.head.appendChild(script);
      timer = setTimeout(()=>finish(reject,new Error('Tiempo de espera agotado')),6000);
    });
  }

  async function init(){
    apply(fullFallback,halfFallback);
    try{
      const prices = await loadConfig();
      apply(prices.full,prices.half);
    }catch(err){
      console.warn('O Faro: no se pudieron actualizar los precios del menú.',err);
    }
  }

  if(document.readyState === 'loading') document.addEventListener('DOMContentLoaded',init);
  else init();
})();
