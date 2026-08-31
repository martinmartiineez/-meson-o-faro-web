(function(){
  const text = value => String(value == null ? '' : value).trim();

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

  function ensureLegalCss(){
    if(document.querySelector('link[data-ofaro-legal]')) return;
    const link = document.createElement('link');
    link.rel = 'stylesheet';
    link.href = 'legal.css?v=20260831-legal3';
    link.dataset.ofaroLegal = '1';
    document.head.appendChild(link);
  }

  function ensureFooter(){
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
    if(inner.dataset.cartaLegalReady === '1') return;
    inner.dataset.cartaLegalReady = '1';
    inner.innerHTML = '<div class="footer-title">Mesón<br>O Faro.</div><div class="muted">Mesón O Faro · NIF X7560307T<br>Calle María, 53 · 15402 Ferrol<br><a href="tel:+34981465035">981 465 035</a> · <a href="mailto:ofaromeson@gmail.com">ofaromeson@gmail.com</a><br>Hojas de reclamaciones disponibles en el establecimiento.</div><div class="site-legal" aria-label="Información legal"><a href="aviso-legal.html">Aviso legal</a><a href="privacidad.html">Privacidad</a><a href="cookies.html">Cookies y almacenamiento</a><a href="accesibilidad.html">Accesibilidad</a><a href="bases-promociones.html">Promociones</a></div>';
  }

  function ensureCartaNotes(){
    const note = document.getElementById('menuFooterNote');
    if(!note) return;
    const wanted = '<strong>Información de la carta:</strong> IVA incluido · servicio en terraza: suplemento de 0,20 € por producto.';
    if(note.innerHTML !== wanted) note.innerHTML = wanted;

    if(!document.getElementById('allergenGeneralNote')){
      const allergen = document.createElement('div');
      allergen.id = 'allergenGeneralNote';
      allergen.className = 'allergen-note';
      allergen.innerHTML = '<strong>Alérgenos e intolerancias:</strong> disponemos de información sobre los 14 alérgenos de declaración obligatoria. Consulta al personal antes de realizar tu pedido. La composición puede variar por cambios de producto o proveedor.';
      note.insertAdjacentElement('afterend',allergen);
    }
  }

  function init(){
    ensureLegalCss();
    ensureSkipLink();
    ensureCartaNotes();
    ensureFooter();
  }

  if(document.readyState === 'loading') document.addEventListener('DOMContentLoaded',init,{once:true});
  else init();
})();
