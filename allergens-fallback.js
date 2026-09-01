(function(){
  if(!window.OfaroFastData || typeof window.OfaroFastData.loadCarta !== 'function') return;

  const cfg = window.OFARO_CONFIG || {};
  const apiUrl = String(cfg.apiUrl || '').trim();
  const originalLoadCarta = window.OfaroFastData.loadCarta.bind(window.OfaroFastData);

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
      return {
        carta:data.carta.map(item=>Object.assign({},item,{
          alergenos:String(item && item.alergenos == null ? '' : item.alergenos).trim()
        })),
        config:data.config || {incrementoTerraza:0.20},
        source:'api-live'
      };
    } finally {
      clearTimeout(timer);
    }
  }

  window.OfaroFastData.loadCarta = async function(force){
    try{
      return await loadLive();
    }catch(_){
      const data = await originalLoadCarta(force);
      if(data && Array.isArray(data.carta)){
        data.carta = data.carta.map(item=>Object.assign({},item,{
          alergenos:String(item && item.alergenos == null ? '' : item.alergenos).trim()
        }));
      }
      return data;
    }
  };

  try{
    if(typeof window.OfaroFastData.clearCache === 'function') window.OfaroFastData.clearCache();
  }catch(_){}
})();
