(function(){
  const fallbackById = {
    C002:'Huevo, Pescado',
    C004:'Gluten, Leche, Huevo',
    C005:'Gluten, Leche, Huevo',
    C007:'Huevo',
    C009:'Huevo',
    C010:'Crustáceos',
    C011:'Leche',
    C012:'Leche',
    C014:'Leche',
    C015:'Leche',
    C016:'Gluten, Huevo',
    C017:'Pescado, Gluten',
    C018:'Moluscos',
    C019:'Crustáceos',
    C020:'Crustáceos',
    C021:'Moluscos',
    C022:'Pescado',
    C023:'Moluscos, Gluten',
    C024:'Moluscos',
    C025:'Moluscos, Gluten',
    C026:'Moluscos, Gluten',
    C027:'Moluscos'
  };

  if(!window.OfaroFastData || typeof window.OfaroFastData.loadCarta !== 'function') return;

  const originalLoadCarta = window.OfaroFastData.loadCarta.bind(window.OfaroFastData);

  window.OfaroFastData.loadCarta = async function(force){
    const data = await originalLoadCarta(force);
    if(data && Array.isArray(data.carta)){
      data.carta = data.carta.map(function(item){
        if(!item) return item;
        const current = String(item.alergenos == null ? '' : item.alergenos).trim();
        if(current) return item;
        const fallback = fallbackById[String(item.id || '').trim()] || '';
        return Object.assign({}, item, {alergenos:fallback});
      });
    }
    return data;
  };

  try{
    if(window.OfaroFastData && typeof window.OfaroFastData.clearCache === 'function'){
      window.OfaroFastData.clearCache();
    }
  }catch(_){}
})();
