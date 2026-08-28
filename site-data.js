(function(){
  const cfg = window.OFARO_CONFIG || {};
  const apiUrl = (cfg.apiUrl || '').trim();
  const spreadsheetId = (cfg.spreadsheetId || '').trim();
  const sheets = Object.assign({carta:'Carta',menu:'Menu del dia',config:'Configuracion'}, cfg.sheets || {});

  if(!document.querySelector('link[data-ofaro-dynamic]')){
    const link = document.createElement('link');
    link.rel = 'stylesheet';
    link.href = 'dynamic.css';
    link.dataset.ofaroDynamic = '1';
    document.head.appendChild(link);
  }

  const fallback = {
    carta: [
      {id:'C001',categoria:'Ensaladas',producto:'Ensalada Mixta',descripcion:'Lechugas frescas, tomate, cebolla y acompañamiento clásico.',precioMedia:null,precioRacion:12,disponible:true,orden:1},
      {id:'C002',categoria:'Ensaladas',producto:'Ensalada de la Casa',descripcion:'Lechuga, tomate, zanahoria, maíz, jamón, huevo, bonito y aceitunas.',precioMedia:null,precioRacion:14,disponible:true,orden:2},
      {id:'C003',categoria:'Ensaladas',producto:'Ensalada Tropical',descripcion:'Aguacate, mango, aceitunas y tomate.',precioMedia:null,precioRacion:15,disponible:true,orden:3},
      {id:'C004',categoria:'Raciones',producto:'Croquetas de Jamón Serrano',descripcion:'',precioMedia:8,precioRacion:14,disponible:true,orden:1},
      {id:'C005',categoria:'Raciones',producto:'Croquetas de Cecina',descripcion:'',precioMedia:8,precioRacion:14,disponible:true,orden:2},
      {id:'C006',categoria:'Raciones',producto:'Pimientos de Padrón',descripcion:'',precioMedia:null,precioRacion:10,disponible:true,orden:3},
      {id:'C007',categoria:'Raciones',producto:'Tortilla de Patatas',descripcion:'',precioMedia:null,precioRacion:18,disponible:true,orden:4},
      {id:'C008',categoria:'Raciones',producto:'Patatas Bravas',descripcion:'',precioMedia:null,precioRacion:8,disponible:true,orden:5},
      {id:'C009',categoria:'Raciones',producto:'Patatas Alioli',descripcion:'',precioMedia:null,precioRacion:8,disponible:true,orden:6},
      {id:'C010',categoria:'Carnes',producto:'Raxo de la Casa',descripcion:'Con pimientos del piquillo y gambas.',precioMedia:13,precioRacion:16,disponible:true,orden:1},
      {id:'C011',categoria:'Carnes',producto:'Raxo al Cabrales',descripcion:'',precioMedia:12,precioRacion:15.5,disponible:true,orden:2},
      {id:'C012',categoria:'Carnes',producto:'Raxo al Queso',descripcion:'',precioMedia:10.5,precioRacion:14,disponible:true,orden:3},
      {id:'C013',categoria:'Carnes',producto:'Raxo con Pimiento',descripcion:'',precioMedia:10.5,precioRacion:14,disponible:true,orden:4},
      {id:'C014',categoria:'Carnes',producto:'Pollo a la Pimienta',descripcion:'',precioMedia:10.5,precioRacion:14,disponible:true,orden:5},
      {id:'C015',categoria:'Carnes',producto:'Pollo al Cabrales',descripcion:'',precioMedia:10.5,precioRacion:15.5,disponible:true,orden:6},
      {id:'C016',categoria:'Carnes',producto:'Fingers de Pollo',descripcion:'',precioMedia:9,precioRacion:12,disponible:true,orden:7},
      {id:'C017',categoria:'Pescados y mariscos',producto:'Pescaditos Fritos',descripcion:'',precioMedia:9.5,precioRacion:14,disponible:true,orden:1},
      {id:'C018',categoria:'Pescados y mariscos',producto:'Pulpo “A Feira”',descripcion:'',precioMedia:null,precioRacion:23,disponible:true,orden:2},
      {id:'C019',categoria:'Pescados y mariscos',producto:'Langostinos al Ajillo',descripcion:'',precioMedia:null,precioRacion:22,disponible:true,orden:3},
      {id:'C020',categoria:'Pescados y mariscos',producto:'Gambas al Ajillo',descripcion:'',precioMedia:null,precioRacion:16,disponible:true,orden:4},
      {id:'C021',categoria:'Pescados y mariscos',producto:'Volandeiras',descripcion:'',precioMedia:null,precioRacion:18,disponible:true,orden:5},
      {id:'C022',categoria:'Pescados y mariscos',producto:'Marraxo a la Plancha',descripcion:'',precioMedia:null,precioRacion:16,disponible:true,orden:6},
      {id:'C023',categoria:'Pescados y mariscos',producto:'Chipirones Fritos',descripcion:'',precioMedia:12,precioRacion:16,disponible:true,orden:7},
      {id:'C024',categoria:'Pescados y mariscos',producto:'Chipirones a la Plancha',descripcion:'',precioMedia:12,precioRacion:16,disponible:true,orden:8},
      {id:'C025',categoria:'Pescados y mariscos',producto:'Puntillas de Calamar',descripcion:'',precioMedia:10,precioRacion:14,disponible:true,orden:9},
      {id:'C026',categoria:'Pescados y mariscos',producto:'Calamares',descripcion:'',precioMedia:12,precioRacion:16,disponible:true,orden:10},
      {id:'C027',categoria:'Pescados y mariscos',producto:'Navajas a la Plancha',descripcion:'',precioMedia:null,precioRacion:16,disponible:true,orden:11}
    ],
    menu: [
      {id:'M001',fecha:'',tipo:'Primero',plato:'Caldo gallego',descripcion:'Ejemplo editable',disponible:true,orden:1},
      {id:'M002',fecha:'',tipo:'Primero',plato:'Ensaladilla de la casa',descripcion:'Ejemplo editable',disponible:true,orden:2},
      {id:'M003',fecha:'',tipo:'Segundo',plato:'Merluza a la gallega',descripcion:'Ejemplo editable',disponible:true,orden:1},
      {id:'M004',fecha:'',tipo:'Segundo',plato:'Pollo asado con patatas',descripcion:'Ejemplo editable',disponible:true,orden:2}
    ],
    config: {precioMenu:12,incrementoTerraza:0.20,direccion:'Calle María, 53 · Ferrol'}
  };

  const clone = value => JSON.parse(JSON.stringify(value));
  const norm = value => String(value || '').normalize('NFD').replace(/[\u0300-\u036f]/g,'').trim().toLowerCase();
  const numberOrNull = value => value === '' || value === null || typeof value === 'undefined' ? null : (Number.isFinite(Number(value)) ? Number(value) : null);
  const isAvailable = value => !['no','false','0'].includes(norm(value));

  function gviz(sheetName){
    return new Promise((resolve,reject)=>{
      if(!spreadsheetId) return reject(new Error('Falta spreadsheetId'));
      const callback = '__ofaro_' + Math.random().toString(36).slice(2);
      const script = document.createElement('script');
      let done = false;
      let timer;
      const finish = (fn,value) => {
        if(done) return;
        done = true;
        clearTimeout(timer);
        try{ delete window[callback]; }catch(_){ window[callback] = undefined; }
        script.remove();
        fn(value);
      };
      window[callback] = response => {
        if(!response || response.status === 'error' || !response.table) return finish(reject,new Error('Google Sheets no devolvió datos'));
        finish(resolve,response.table);
      };
      script.onerror = () => finish(reject,new Error('No se pudo acceder a Google Sheets'));
      const params = new URLSearchParams({sheet:sheetName,headers:'1',tq:'select *',tqx:'out:json;responseHandler:' + callback,_:String(Date.now())});
      script.src = 'https://docs.google.com/spreadsheets/d/' + encodeURIComponent(spreadsheetId) + '/gviz/tq?' + params.toString();
      document.head.appendChild(script);
      timer = setTimeout(()=>finish(reject,new Error('Tiempo de espera agotado')),8000);
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
      id:pick(r,'ID'),categoria:pick(r,'Categoría'),producto:pick(r,'Producto'),descripcion:pick(r,'Descripción'),
      precioMedia:numberOrNull(pick(r,'Precio media')),precioRacion:numberOrNull(pick(r,'Precio ración')),
      disponible:isAvailable(pick(r,'Disponible')),orden:Number(pick(r,'Orden'))||0
    })).filter(x=>x.producto && x.categoria);
  }

  function parseMenu(table){
    return rows(table).map(r=>({
      id:pick(r,'ID'),fecha:pick(r,'Fecha'),tipo:pick(r,'Tipo'),plato:pick(r,'Plato'),descripcion:pick(r,'Descripción'),
      disponible:isAvailable(pick(r,'Disponible')),orden:Number(pick(r,'Orden'))||0
    })).filter(x=>x.plato && x.tipo);
  }

  function parseConfig(table){
    const values = {};
    rows(table).forEach(r=>{ const key = norm(pick(r,'Campo')); if(key) values[key] = pick(r,'Valor'); });
    return {
      precioMenu:numberOrNull(values['precio menu']) ?? 12,
      incrementoTerraza:numberOrNull(values['incremento terraza']) ?? 0.20,
      direccion:values['direccion'] || 'Calle María, 53 · Ferrol'
    };
  }

  async function loadFromSheets(){
    const [cartaTable,menuTable,configTable] = await Promise.all([gviz(sheets.carta),gviz(sheets.menu),gviz(sheets.config)]);
    return {carta:parseCarta(cartaTable),menu:parseMenu(menuTable),config:parseConfig(configTable),source:'sheets'};
  }

  async function loadPublic(){
    if(apiUrl){
      try{
        const res = await fetch(apiUrl + '?action=public&_=' + Date.now(), {cache:'no-store'});
        if(!res.ok) throw new Error('HTTP ' + res.status);
        const data = await res.json();
        if(!data || !Array.isArray(data.carta) || !Array.isArray(data.menu)) throw new Error('Formato inválido');
        data.source = 'api';
        return data;
      }catch(err){ console.warn('O Faro: la API no respondió.',err); }
    }
    if(spreadsheetId){
      try{ return await loadFromSheets(); }
      catch(err){ console.warn('O Faro: Google Sheets no está accesible; se usa la copia local.',err); }
    }
    const data = clone(fallback);
    data.source = 'fallback';
    return data;
  }

  async function apiPost(action,payload,token){
    if(!apiUrl) throw new Error('La API de escritura todavía no está conectada.');
    const res = await fetch(apiUrl,{method:'POST',body:JSON.stringify(Object.assign({action,token:token||''},payload||{})),cache:'no-store'});
    const data = await res.json();
    if(!res.ok || !data || data.ok === false) throw new Error((data && data.error) || 'No se pudo completar la operación.');
    return data;
  }

  window.OfaroData = {apiUrl,spreadsheetId,sheetUrl:cfg.sheetUrl||'',fallback,loadPublic,apiPost};
})();
