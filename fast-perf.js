(function(){
  const fast = window.OfaroFastData;
  if(!fast) return;

  const MAX_STALE_MS = 7 * 24 * 60 * 60 * 1000;
  const CARTA_CACHE_KEY = 'ofaro-fast-carta-v5';
  const CACHE_SCHEMA_KEY = 'ofaro-fast-carta-schema';
  const CACHE_SCHEMA = 'api-allergens-v1';

  function decorate(data){
    if(!data || !Array.isArray(data.carta)) return data;
    return Object.assign({},data,{
      carta:data.carta.map(item=>Object.assign({},item,{
        alergenos:String(item && item.alergenos == null ? '' : item.alergenos).trim()
      }))
    });
  }

  function signature(data){
    try{
      return JSON.stringify({
        carta:(data && data.carta || []).map(x=>[
          x.id,x.categoria,x.producto,x.descripcion,x.precioMedia,x.precioRacion,x.disponible,x.orden,x.alergenos
        ]),
        terraza:data && data.config && data.config.incrementoTerraza
      });
    }catch(_){ return ''; }
  }

  try{
    if(localStorage.getItem(CACHE_SCHEMA_KEY) !== CACHE_SCHEMA){
      ['ofaro-fast-carta-v1','ofaro-fast-carta-v2','ofaro-fast-carta-v3','ofaro-fast-carta-v4',CARTA_CACHE_KEY]
        .forEach(k=>localStorage.removeItem(k));
      localStorage.setItem(CACHE_SCHEMA_KEY,CACHE_SCHEMA);
    }
  }catch(_){}

  function readCartaCache(){
    try{
      const raw = localStorage.getItem(CARTA_CACHE_KEY);
      if(!raw) return null;
      const parsed = JSON.parse(raw);
      if(!parsed || !parsed.time || !parsed.data) return null;
      const age = Date.now() - Number(parsed.time || 0);
      if(age > MAX_STALE_MS) return null;
      return {time:Number(parsed.time),age,data:decorate(parsed.data)};
    }catch(_){ return null; }
  }

  function bundledCarta(){
    const fallback = window.OfaroData && window.OfaroData.fallback;
    if(!fallback || !Array.isArray(fallback.carta) || !fallback.carta.length) return null;
    return decorate({
      carta:fallback.carta,
      config:Object.assign({incrementoTerraza:0.20},fallback.config || {}),
      source:'bundled-instant'
    });
  }

  function schedule(task){
    const run = ()=>{
      if('requestIdleCallback' in window) requestIdleCallback(()=>task(),{timeout:900});
      else task();
    };
    setTimeout(run,120);
  }

  if(typeof fast.loadCarta === 'function'){
    const originalCarta = fast.loadCarta.bind(fast);
    let cartaInFlight = null;
    let shownSignature = '';
    let refreshScheduled = false;

    function refreshCarta(notify){
      if(cartaInFlight) return cartaInFlight;
      cartaInFlight = Promise.resolve(originalCarta(true))
        .then(decorate)
        .then(data=>{
          const nextSignature = signature(data);
          const changed = nextSignature && nextSignature !== shownSignature;
          if(changed) shownSignature = nextSignature;
          if(notify && changed){
            try{ window.dispatchEvent(new CustomEvent('ofaro:carta-updated',{detail:data})); }catch(_){}
          }
          return data;
        })
        .finally(()=>{ cartaInFlight = null; });
      return cartaInFlight;
    }

    function scheduleRefresh(){
      if(refreshScheduled) return;
      refreshScheduled = true;
      schedule(()=>{
        refreshScheduled = false;
        refreshCarta(true).catch(()=>{});
      });
    }

    fast.loadCarta = function(force){
      if(force === true) return refreshCarta(true);

      const cached = readCartaCache();
      if(cached){
        shownSignature = signature(cached.data);
        scheduleRefresh();
        return Promise.resolve(cached.data);
      }

      const bundled = bundledCarta();
      if(bundled){
        shownSignature = signature(bundled);
        scheduleRefresh();
        return Promise.resolve(bundled);
      }

      return refreshCarta(false);
    };
  }

  if(typeof fast.loadMenu === 'function'){
    const originalMenu = fast.loadMenu.bind(fast);
    let menuInFlight = null;
    fast.loadMenu = function(force){
      if(force === true) return originalMenu(true);
      if(menuInFlight) return menuInFlight;
      menuInFlight = Promise.resolve(originalMenu(false)).finally(()=>{ menuInFlight = null; });
      return menuInFlight;
    };
  }
})();
