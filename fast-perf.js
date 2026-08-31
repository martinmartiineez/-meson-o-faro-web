(function(){
  const fast = window.OfaroFastData;
  if(!fast) return;

  ['loadCarta','loadMenu'].forEach(name=>{
    if(typeof fast[name] !== 'function') return;
    const original = fast[name].bind(fast);
    let inFlight = null;

    fast[name] = function(force){
      if(force === true) return original(true);
      if(inFlight) return inFlight;
      inFlight = Promise.resolve(original(false)).finally(()=>{ inFlight = null; });
      return inFlight;
    };
  });
})();
