(function(){
  if(!window.OfaroFastData || typeof window.OfaroFastData.loadCarta !== 'function') return;

  const cfg = window.OFARO_CONFIG || {};
  const apiUrl = String(cfg.apiUrl || '').trim();
  const originalLoadCarta = window.OfaroFastData.loadCarta.bind(window.OfaroFastData);

  function localDescriptions(){
    const map = {};
    try{
      const rows = window.OfaroData && window.OfaroData.fallback && Array.isArray(window.OfaroData.fallback.carta)
        ? window.OfaroData.fallback.carta : [];
      rows.forEach(item=>{
        if(item && item.id && item.descripcion) map[String(item.id)] = String(item.descripcion).trim();
      });
    }catch(_){}
    return map;
  }

  function normalizeCarta(data){
    if(!data || !Array.isArray(data.carta)) return data;
    const descFallback = localDescriptions();
    data.carta = data.carta.map(item=>{
      if(!item) return item;
      const id = String(item.id || '').trim();
      const descripcion = String(
        item.descripcion != null ? item.descripcion :
        (item.description != null ? item.description : '')
      ).trim();
      return Object.assign({},item,{
        descripcion: descripcion || descFallback[id] || '',
        alergenos:String(item.alergenos == null ? '' : item.alergenos).trim()
      });
    });
    return data;
  }

  async function loadLive(){
    if(!apiUrl) throw new Error('Falta apiUrl');
    const controller = typeof AbortController === 'function' ? new AbortController() : null;
    const timer = setTimeout(()=>{ try{ controller && controller.abort(); }catch(_){} }, 8000);
    try{
      const res = await fetch(apiUrl + '?action=public&_=' + Date.now(), {
        cache:'no-store',
        signal:controller ? controller.signal : undefined
      });
      if(!res.ok) throw new Error('HTTP ' + res.status);
      const data = await res.json();
      if(!data || !Array.isArray(data.carta)) throw new Error('Formato inválido');
      return normalizeCarta({
        carta:data.carta,
        config:data.config || {incrementoTerraza:0.20},
        source:'api-live'
      });
    } finally {
      clearTimeout(timer);
    }
  }

  window.OfaroFastData.loadCarta = async function(force){
    try{
      return await loadLive();
    }catch(_){
      return normalizeCarta(await originalLoadCarta(force));
    }
  };

  try{
    if(typeof window.OfaroFastData.clearCache === 'function') window.OfaroFastData.clearCache();
  }catch(_){}
})();
