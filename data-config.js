window.OFARO_CONFIG = {
  apiUrl: "https://script.google.com/macros/s/AKfycbwyICNMM0CHeSFQqOaO4d6g_d84vougY6OivfrMi6G5DIIVy7Y1qK_v2tBsZKmnQ2njkQ/exec",
  spreadsheetId: "1I852Llhr3Nj2LuR1TESXwYZ54hlPNQj30GU8GU5uSaI",
  sheetUrl: "https://docs.google.com/spreadsheets/d/1I852Llhr3Nj2LuR1TESXwYZ54hlPNQj30GU8GU5uSaI/edit",
  sheets: {
    carta: "Carta",
    menu: "Menu del dia",
    config: "Configuracion"
  },
  contact: {
    phoneDisplay: "981 465 035",
    phoneHref: "+34981465035",
    whatsappUrl: "https://wa.me/message/SKWRIQJCLXXMF1?src=qr",
    email: "ofaromeson@gmail.com"
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

(function(){
  function initContactPanel(){
    const cfg = (window.OFARO_CONFIG && window.OFARO_CONFIG.contact) || {};
    const phoneDisplay = String(cfg.phoneDisplay || '981 465 035');
    const phoneHref = String(cfg.phoneHref || '+34981465035');
    const whatsappUrl = String(cfg.whatsappUrl || 'https://wa.me/message/SKWRIQJCLXXMF1?src=qr');
    const email = String(cfg.email || 'ofaromeson@gmail.com');

    if(!document.getElementById('ofaro-contact-style')){
      const style = document.createElement('style');
      style.id = 'ofaro-contact-style';
      style.textContent = `
        .ofaro-contact-overlay[hidden]{display:none!important}
        .ofaro-contact-overlay{position:fixed;inset:0;z-index:10050;display:flex;align-items:flex-end;justify-content:center;padding:18px;background:rgba(0,0,0,.48);backdrop-filter:blur(8px);-webkit-backdrop-filter:blur(8px)}
        .ofaro-contact-panel{width:min(100%,520px);background:#fff;color:#111;border:1px solid #ddd8cf;border-radius:30px;padding:24px 20px calc(24px + env(safe-area-inset-bottom));box-shadow:0 22px 70px rgba(0,0,0,.28)}
        .ofaro-contact-head{display:flex;align-items:flex-start;justify-content:space-between;gap:18px;margin-bottom:18px}
        .ofaro-contact-kicker{font-size:.72rem;font-weight:800;letter-spacing:.15em;text-transform:uppercase;color:#85817a;margin-bottom:6px}
        .ofaro-contact-title{font-family:Georgia,'Times New Roman',serif;font-size:2rem;line-height:1;margin:0}
        .ofaro-contact-close{appearance:none;border:0;background:#111;color:#fff;width:44px;height:44px;border-radius:50%;font-size:1.45rem;line-height:1;display:grid;place-items:center;cursor:pointer;flex:0 0 auto}
        .ofaro-contact-list{display:grid;gap:10px}
        .ofaro-contact-option{display:flex;align-items:center;justify-content:space-between;gap:14px;text-decoration:none;color:#111;background:#f5f3ef;border:1px solid #e5e1da;border-radius:20px;padding:16px 17px;min-height:74px}
        .ofaro-contact-option:hover,.ofaro-contact-option:focus-visible{background:#ece9e3;outline:none}
        .ofaro-contact-option-text{min-width:0}
        .ofaro-contact-label{display:block;font-size:.71rem;font-weight:800;letter-spacing:.12em;text-transform:uppercase;color:#77736d;margin-bottom:4px}
        .ofaro-contact-value{display:block;font-size:1.08rem;font-weight:800;line-height:1.25;overflow-wrap:anywhere}
        .ofaro-contact-arrow{font-size:1.3rem;font-weight:800;flex:0 0 auto}
        @media (min-width:700px){
          .ofaro-contact-overlay{align-items:center}
          .ofaro-contact-panel{border-radius:30px;padding-bottom:24px}
        }
      `;
      document.head.appendChild(style);
    }

    if(document.getElementById('ofaroContactOverlay')) return;

    const overlay = document.createElement('div');
    overlay.id = 'ofaroContactOverlay';
    overlay.className = 'ofaro-contact-overlay';
    overlay.hidden = true;
    overlay.innerHTML = `
      <section class="ofaro-contact-panel" role="dialog" aria-modal="true" aria-labelledby="ofaroContactTitle">
        <div class="ofaro-contact-head">
          <div>
            <div class="ofaro-contact-kicker">Mesón O Faro</div>
            <h2 class="ofaro-contact-title" id="ofaroContactTitle">Contacto</h2>
          </div>
          <button class="ofaro-contact-close" type="button" aria-label="Cerrar contacto">×</button>
        </div>
        <div class="ofaro-contact-list">
          <a class="ofaro-contact-option" href="tel:${phoneHref}">
            <span class="ofaro-contact-option-text"><span class="ofaro-contact-label">Teléfono</span><span class="ofaro-contact-value">${phoneDisplay}</span></span>
            <span class="ofaro-contact-arrow" aria-hidden="true">→</span>
          </a>
          <a class="ofaro-contact-option" href="${whatsappUrl}" target="_blank" rel="noopener noreferrer">
            <span class="ofaro-contact-option-text"><span class="ofaro-contact-label">WhatsApp</span><span class="ofaro-contact-value">Escribir por WhatsApp</span></span>
            <span class="ofaro-contact-arrow" aria-hidden="true">→</span>
          </a>
          <a class="ofaro-contact-option" href="mailto:${email}">
            <span class="ofaro-contact-option-text"><span class="ofaro-contact-label">Correo electrónico</span><span class="ofaro-contact-value">${email}</span></span>
            <span class="ofaro-contact-arrow" aria-hidden="true">→</span>
          </a>
        </div>
      </section>
    `;
    document.body.appendChild(overlay);

    const closeButton = overlay.querySelector('.ofaro-contact-close');
    let previousFocus = null;
    let previousOverflow = '';

    function openPanel(trigger){
      previousFocus = trigger || document.activeElement;
      previousOverflow = document.body.style.overflow;
      overlay.hidden = false;
      document.body.style.overflow = 'hidden';
      requestAnimationFrame(()=>closeButton.focus());
    }

    function closePanel(){
      if(overlay.hidden) return;
      overlay.hidden = true;
      document.body.style.overflow = previousOverflow;
      if(previousFocus && typeof previousFocus.focus === 'function') previousFocus.focus();
    }

    document.addEventListener('click',event=>{
      const trigger = event.target.closest('a[href="#contacto"],a[href="index.html#contacto"],[data-contact-trigger]');
      if(!trigger) return;
      event.preventDefault();
      openPanel(trigger);
    });

    closeButton.addEventListener('click',closePanel);
    overlay.addEventListener('click',event=>{ if(event.target === overlay) closePanel(); });
    document.addEventListener('keydown',event=>{ if(event.key === 'Escape' && !overlay.hidden) closePanel(); });
  }

  if(document.readyState === 'loading') document.addEventListener('DOMContentLoaded',initContactPanel,{once:true});
  else initContactPanel();
})();
