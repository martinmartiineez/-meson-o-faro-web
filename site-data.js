(function(){
  const cfg = window.OFARO_CONFIG || {};
  const apiUrl = (cfg.apiUrl || '').trim();

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
    config: {
      precioMenu: 12,
      incrementoTerraza: 0.20,
      direccion: 'Calle María, 53 · Ferrol'
    }
  };

  function clone(value){ return JSON.parse(JSON.stringify(value)); }

  async function loadPublic(){
    if(!apiUrl) return clone(fallback);
    try{
      const res = await fetch(apiUrl + '?action=public&_=' + Date.now(), {cache:'no-store'});
      if(!res.ok) throw new Error('HTTP ' + res.status);
      const data = await res.json();
      if(!data || !Array.isArray(data.carta) || !Array.isArray(data.menu)) throw new Error('Formato inválido');
      return data;
    }catch(err){
      console.warn('O Faro: se usa la copia local porque la API no respondió.', err);
      return clone(fallback);
    }
  }

  async function apiPost(action, payload, token){
    if(!apiUrl) throw new Error('La API todavía no está conectada.');
    const res = await fetch(apiUrl, {
      method:'POST',
      body: JSON.stringify(Object.assign({action:action, token:token || ''}, payload || {})),
      cache:'no-store'
    });
    const data = await res.json();
    if(!res.ok || !data || data.ok === false) throw new Error((data && data.error) || 'No se pudo completar la operación.');
    return data;
  }

  window.OfaroData = {
    apiUrl,
    sheetUrl: cfg.sheetUrl || '',
    fallback,
    loadPublic,
    apiPost
  };
})();
