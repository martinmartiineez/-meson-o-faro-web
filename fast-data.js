(function(){
  const cfg = window.OFARO_CONFIG || {};
  const spreadsheetId = String(cfg.spreadsheetId || '').trim();
  const sheets = Object.assign({carta:'Carta',menu:'Menu del dia',config:'Configuracion'}, cfg.sheets || {});
  const CACHE_MS = 30000;

  const norm = value => String(value == null ? '' : value).normalize('NFD').replace(/[\u0300-\u036f]/g,'').trim().toLowerCase();
  const numberOrNull = value => {
    if(value === '' || value === null || typeof value === 'undefined') return null;
    const n = Number(String(value).replace(',','.').replace(/[^0-9.-]/g,''));
    return Number.isFinite(n) ? n : null;
  };
  const isAvailable = value => !['no','false','0'].includes(norm(value));

  function readCache(key){
    try{
      const raw = localStorage.getItem(key);
      if(!raw) return null;
      const parsed = JSON.parse(raw);
      if(!parsed || !parsed.time || Date.now() - parsed.time > CACHE_MS) return null;
      return parsed.data || null;
    }catch(_){ return null; }
  }

  function writeCache(key,data){
    try{ localStorage.setItem(key, JSON.stringify({time:Date.now(),data:data})); }catch(_){}
  }

  function gviz(sheetName,query){
    return new Promise((resolve,reject)=>{
      if(!spreadsheetId) return reject(new Error('Falta spreadsheetId'));
      const callback = '__ofaro_fast_' + Math.random().toString(36).slice(2);
      const script = document.createElement('script');
      let finished = false;
      let timer;
      const done = (fn,value) => {
        if(finished) return;
        finished = true;
        clearTimeout(timer);
        try{ delete window[callback]; }catch(_){ window[callback] = undefined; }
        script.remove();
        fn(value);
      };
      window[callback] = response => {
        if(!response || response.status === 'error' || !response.table) return done(reject,new Error('Google Sheets no devolvió datos'));
        done(resolve,response.table);
      };
      script.onerror = () => done(reject,new Error('No se pudo acceder a Google Sheets'));
      const params = new URLSearchParams({
        sheet:sheetName,
        headers:'1',
        tq:query || 'select *',
        tqx:'out:json;responseHandler:' + callback,
        _ : String(Math.floor(Date.now()/10000))
      });
      script.src = 'https://docs.google.com/spreadsheets/d/' + encodeURIComponent(spreadsheetId) + '/gviz/tq?' + params.toString();
      document.head.appendChild(script);
      timer = setTimeout(()=>done(reject,new Error('Tiempo de espera agotado')),3000);
    });
  }

  function rows(table){
    const labels = (table.cols || []).map((col,i)=>norm(col.label || col.id || ('col'+i)));
    return (table.rows || []).map(row=>{
      const out = {};
      labels.forEach((label,i)=>{
        const cell = row.c && row.c[i];
        out[label] = cell && cell.v !== null && typeof cell.v !== 'undefined' ? cell.v : '';
      });
      return out;
    });
  }

  const pick = (obj,key) => obj[norm(key)];

  function parseCarta(table){
    return rows(table).map(r=>({
      id:pick(r,'ID'),
      categoria:pick(r,'Categoría'),
      producto:pick(r,'Producto'),
      descripcion:pick(r,'Descripción'),
      precioMedia:numberOrNull(pick(r,'Precio media')),
      precioRacion:numberOrNull(pick(r,'Precio ración')),
      disponible:isAvailable(pick(r,'Disponible')),
      orden:Number(pick(r,'Orden'))||0,
      alergenos:String(pick(r,'Alérgenos') || '').trim()
    })).filter(x=>x.producto && x.categoria);
  }

  function parseMenu(table){
    return rows(table).map(r=>({
      id:pick(r,'ID'),
      fecha:String(pick(r,'Fecha') || '').trim(),
      tipo:pick(r,'Tipo'),
      plato:pick(r,'Plato'),
      descripcion:pick(r,'Descripción'),
      disponible:isAvailable(pick(r,'Disponible')),
      orden:Number(pick(r,'Orden'))||0
    })).filter(x=>x.plato && x.tipo);
  }

  function parseConfig(table){
    const values = {};
    rows(table).forEach(r=>{
      const key = norm(pick(r,'Campo'));
      if(key) values[key] = pick(r,'Valor');
    });
    return {
      precioMenu:numberOrNull(values['precio menu']) ?? 13,
      precioMedioMenu:numberOrNull(values['precio medio menu']) ?? 10,
      incrementoTerraza:numberOrNull(values['incremento terraza']) ?? 0.20,
      direccion:values['direccion'] || 'Calle María, 53 · Ferrol'
    };
  }

  function madridDates(){
    const parts = new Intl.DateTimeFormat('es-ES',{timeZone:'Europe/Madrid',day:'2-digit',month:'2-digit',year:'numeric'}).formatToParts(new Date());
    const map = {};
    parts.forEach(p=>{ if(p.type !== 'literal') map[p.type] = p.value; });
    return {iso:map.year+'-'+map.month+'-'+map.day, es:map.day+'/'+map.month+'/'+map.year};
  }

  function selectCurrentMenu(items){
    const d = madridDates();
    const available = items.filter(x=>x.disponible !== false);
    const dated = available.filter(x=>x.fecha === d.iso || x.fecha === d.es);
    const selected = dated.length ? dated : available.filter(x=>!x.fecha);
    return selected.sort((a,b)=>{
      const type = String(a.tipo||'').localeCompare(String(b.tipo||''),'es');
      return type || ((Number(a.orden)||0)-(Number(b.orden)||0));
    });
  }

  async function fallbackPublic(){
    if(window.OfaroData && typeof window.OfaroData.loadPublic === 'function') return window.OfaroData.loadPublic();
    throw new Error('No hay fuente alternativa');
  }

  async function loadCarta(force){
    const key = 'ofaro-fast-carta-v4';
    if(!force){ const cached = readCache(key); if(cached) return cached; }
    try{
      /* Carta no espera a Configuración: una segunda petición no debe poder
         bloquear ni hacer perder los alérgenos. */
      const cartaTable = await gviz(sheets.carta,'select A,B,C,D,E,F,G,H,I');
      const data = {
        carta:parseCarta(cartaTable),
        config:{incrementoTerraza:0.20},
        source:'sheets-fast'
      };
      writeCache(key,data);
      return data;
    }catch(err){
      const data = await fallbackPublic();
      const slim = {carta:data.carta || [],config:data.config || {incrementoTerraza:0.20},source:data.source || 'fallback'};
      writeCache(key,slim);
      return slim;
    }
  }

  async function loadMenu(force){
    const key = 'ofaro-fast-menu-v1';
    if(!force){ const cached = readCache(key); if(cached) return cached; }
    try{
      const [menuTable,configTable] = await Promise.all([gviz(sheets.menu),gviz(sheets.config)]);
      const config = parseConfig(configTable);
      const data = {menu:selectCurrentMenu(parseMenu(menuTable)),config:config,source:'sheets-fast'};
      writeCache(key,data);
      return data;
    }catch(err){
      const data = await fallbackPublic();
      const slim = {menu:data.menu || [],config:data.config || {},source:data.source || 'fallback'};
      writeCache(key,slim);
      return slim;
    }
  }

  function clearCache(){
    try{
      localStorage.removeItem('ofaro-fast-carta-v1');
      localStorage.removeItem('ofaro-fast-carta-v2');
      localStorage.removeItem('ofaro-fast-carta-v3');
      localStorage.removeItem('ofaro-fast-carta-v4');
      localStorage.removeItem('ofaro-fast-menu-v1');
    }catch(_){}
  }

  window.OfaroFastData = {loadCarta,loadMenu,clearCache};
})();
