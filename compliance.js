(function(){
  const path = (location.pathname.split('/').pop() || 'index.html').toLowerCase();
  if(path === 'admin.html') return;

  if(!document.querySelector('link[data-ofaro-legal]')){
    const link = document.createElement('link');
    link.rel = 'stylesheet';
    link.href = 'legal.css?v=20260831-legal2';
    link.dataset.ofaroLegal = '1';
    document.head.appendChild(link);
  }

  function legalLinks(){
    return '<div class="site-legal" aria-label="Información legal"><a href="aviso-legal.html">Aviso legal</a><a href="privacidad.html">Privacidad</a><a href="cookies.html">Cookies y almacenamiento</a><a href="accesibilidad.html">Accesibilidad</a><a href="bases-promociones.html">Promociones</a></div>';
  }

  function ensureSkipLink(){
    if(document.querySelector('.skip-link')) return;
    const main = document.querySelector('main');
    if(!main) return;
    if(!main.id) main.id = 'contenido-principal';
    const a = document.createElement('a');
    a.className = 'skip-link';
    a.href = '#' + main.id;
    a.textContent = 'Saltar al contenido';
    document.body.insertBefore(a,document.body.firstChild);
  }

  function ensureFooter(){
    if(document.body.classList.contains('legal-page')) return;
    let footer = document.querySelector('body > footer');
    if(!footer){
      footer = document.createElement('footer');
      const bottom = document.querySelector('.bottom');
      if(bottom) document.body.insertBefore(footer,bottom);
      else document.body.appendChild(footer);
    }
    let inner = footer.querySelector('.footer-inner');
    if(!inner){
      inner = document.createElement('div');
      inner.className = 'footer-inner';
      footer.appendChild(inner);
    }
    inner.innerHTML = '<div class="footer-title">Mesón<br>O Faro.</div><div class="muted">Calle María, 53 · 15402 Ferrol<br><a href="tel:+34981465035">981 465 035</a> · <a href="mailto:ofaromeson@gmail.com">ofaromeson@gmail.com</a><br>NIF X7560307T · Hojas de reclamaciones disponibles en el establecimiento.</div>' + legalLinks();
  }

  function setupReservationPrivacy(){
    const form = document.getElementById('reserveForm');
    if(!form) return;
    const observations = form.elements && form.elements.observaciones;
    if(observations){
      observations.placeholder = 'Trona, celebración u otras observaciones…';
      if(!document.getElementById('reserveHealthWarning')){
        const warning = document.createElement('p');
        warning.id = 'reserveHealthWarning';
        warning.className = 'form-data-warning';
        warning.innerHTML = 'No incluyas alergias, intolerancias, diagnósticos ni otros datos de salud aquí. Para comunicar una necesidad relacionada con alergias o accesibilidad, contacta con el mesón en el <a href="tel:+34981465035">981 465 035</a>.';
        observations.insertAdjacentElement('afterend',warning);
      }
    }
    if(!document.getElementById('reservePrivacyInfo')){
      const submit = form.querySelector('button[type="submit"]');
      if(submit){
        const info = document.createElement('p');
        info.id = 'reservePrivacyInfo';
        info.className = 'privacy-first-layer';
        info.innerHTML = '<strong>Protección de datos:</strong> Mesón O Faro tratará los datos para gestionar tu solicitud de reserva. La base jurídica es la aplicación de medidas precontractuales solicitadas por ti. Consulta conservación, derechos y demás información en la <a href="privacidad.html">Política de privacidad</a>.';
        submit.insertAdjacentElement('beforebegin',info);
      }
    }
  }

  function setupCartaLegal(){
    const note = document.getElementById('menuFooterNote');
    if(!note) return;
    const apply = function(){
      note.innerHTML = '<strong>Información de la carta:</strong> IVA incluido · servicio en terraza: suplemento de 0,20 € por producto.';
      if(!document.getElementById('allergenGeneralNote')){
        const allergen = document.createElement('div');
        allergen.id = 'allergenGeneralNote';
        allergen.className = 'allergen-note';
        allergen.innerHTML = '<strong>Alérgenos e intolerancias:</strong> disponemos de información sobre los 14 alérgenos de declaración obligatoria. Consulta al personal antes de realizar tu pedido. La composición puede variar por cambios de producto o proveedor.';
        note.insertAdjacentElement('afterend',allergen);
      }
    };
    apply();
    const observer = new MutationObserver(apply);
    observer.observe(note,{childList:true,subtree:true,characterData:true});
    setTimeout(()=>observer.disconnect(),8000);
  }

  function setupMenuDayLegal(){
    const grid = document.querySelector('.menu-price-grid');
    if(!grid || document.getElementById('menuDayLegalNote')) return;
    const note = document.createElement('div');
    note.id = 'menuDayLegalNote';
    note.className = 'allergen-note';
    note.innerHTML = '<strong>Información del menú:</strong> precios con IVA incluido. En terraza se aplica un suplemento de 0,20 € por producto. La composición concreta de cada modalidad puede variar; consulta en el establecimiento qué incluye el menú publicado ese día y la información de alérgenos antes de pedir.';
    grid.insertAdjacentElement('afterend',note);
  }

  function init(){
    ensureSkipLink();
    ensureFooter();
    if(path === 'index.html' || path === '') setupReservationPrivacy();
    if(path === 'carta.html') setupCartaLegal();
    if(path === 'menu-dia.html') setupMenuDayLegal();
  }

  if(document.readyState === 'loading') document.addEventListener('DOMContentLoaded',init,{once:true});
  else init();
})();
