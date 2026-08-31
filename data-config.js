window.OFARO_CONFIG = {
  apiUrl: "https://script.google.com/macros/s/AKfycbwyICNMM0CHeSFQqOaO4d6g_d84vougY6OivfrMi6G5DIIVy7Y1qK_v2tBsZKmnQ2njkQ/exec",
  spreadsheetId: "1I852Llhr3Nj2LuR1TESXwYZ54hlPNQj30GU8GU5uSaI",
  sheetUrl: "https://docs.google.com/spreadsheets/d/1I852Llhr3Nj2LuR1TESXwYZ54hlPNQj30GU8GU5uSaI/edit",
  sheets: {
    carta: "Carta",
    menu: "Menu del dia",
    config: "Configuracion"
  }
};

(function(){
  function loadCompliance(){
    if(document.querySelector('script[data-ofaro-compliance]')) return;
    const path = (location.pathname.split('/').pop() || 'index.html').toLowerCase();
    const script = document.createElement('script');
    script.src = path === 'carta.html'
      ? 'carta-compliance.js?v=20260901-perf3'
      : 'compliance.js?v=20260831-legal3';
    script.defer = true;
    script.dataset.ofaroCompliance = '1';
    document.head.appendChild(script);
  }

  if(document.readyState === 'loading') document.addEventListener('DOMContentLoaded',loadCompliance,{once:true});
  else loadCompliance();
})();
