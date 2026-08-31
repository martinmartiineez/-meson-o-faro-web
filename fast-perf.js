(function(){
  const fast = window.OfaroFastData;
  if(!fast) return;

  const FRESH_MS = 30000;
  const MAX_STALE_MS = 7 * 24 * 60 * 60 * 1000;
  const CARTA_CACHE_KEY = 'ofaro-fast-carta-v2';

  const allergensById = {
    C001:'Huevo, Pescado', C002:'Huevo, Pescado', C003:'',
    C004:'Gluten, Leche, Huevo', C005:'Gluten, Leche, Huevo', C006:'',
    C007:'Huevo', C008:'', C009:'Huevo', C010:'Crustáceos',
    C011:'Leche', C012:'Leche', C013:'', C014:'Leche', C015:'Leche',
    C016:'Gluten, Huevo', C017:'Pescado, Gluten',
    C018:'Moluscos (cefalópodos)', C019:'Crustáceos', C020:'Crustáceos',
    C021:'Moluscos', C022:'Pescado',
    C023:'Moluscos (cefalópodos), Gluten', C024:'Moluscos (cefalópodos)',
    C025:'Moluscos (cefalópodos), Gluten', C026:'Moluscos (cefalópodos), Gluten',
    C027:'Moluscos'
  };

  function decorate(data){
    if(!data || !Array.isArray(data.carta)) return data;
    return Object.assign({},data,{
      carta:data.carta.map(item=>Object.assign({},item,{
        alergenos:String(item && item.alergenos || allergensById[item && item.id] || '').trim()
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
      if('requestIdleCallback' in window){
        requestIdleCallback(()=>task(),{timeout:2500});
      }else{
        task();
      }
    };
    setTimeout(run,900);
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
        if(cached.age > FRESH_MS) scheduleRefresh();
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
