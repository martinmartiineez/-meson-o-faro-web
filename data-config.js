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
  if(document.querySelector('script[data-ofaro-compliance]')) return;
  const script = document.createElement('script');
  script.src = 'compliance.js?v=20260831-legal3';
  script.defer = true;
  script.dataset.ofaroCompliance = '1';
  document.head.appendChild(script);
})();
