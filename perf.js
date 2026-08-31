(function(){
  const data = window.OfaroData;
  if(!data || typeof data.loadPublic !== 'function') return;

  const original = data.loadPublic.bind(data);
  const CACHE_KEY = 'ofaro-public-shared-v1';
  const CACHE_MS = 30000;
  let memory = null;
  let memoryTime = 0;
  let inFlight = null;

  function readCache(){
    try{
      const raw = sessionStorage.getItem(CACHE_KEY);
      if(!raw) return null;
      const parsed = JSON.parse(raw);
      if(!parsed || !parsed.time || !parsed.data) return null;
      if(Date.now() - Number(parsed.time) > CACHE_MS) return null;
      return parsed;
    }catch(_){ return null; }
  }

  function writeCache(value){
    try{
      sessionStorage.setItem(CACHE_KEY, JSON.stringify({time:Date.now(),data:value}));
    }catch(_){}
  }

  data.loadPublic = function(force){
    if(force === true){
      memory = null;
      memoryTime = 0;
      try{ sessionStorage.removeItem(CACHE_KEY); }catch(_){}
    }else{
      if(memory && Date.now() - memoryTime <= CACHE_MS) return Promise.resolve(memory);
      const stored = readCache();
      if(stored){
        memory = stored.data;
        memoryTime = Number(stored.time) || Date.now();
        return Promise.resolve(memory);
      }
      if(inFlight) return inFlight;
    }

    inFlight = Promise.resolve()
      .then(()=>original())
      .then(value=>{
        memory = value;
        memoryTime = Date.now();
        writeCache(value);
        return value;
      })
      .finally(()=>{ inFlight = null; });

    return inFlight;
  };
})();
