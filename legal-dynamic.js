(function(){
  const file = (location.pathname.split('/').pop() || '').toLowerCase();
  const pageMap = {
    'aviso-legal.html':'aviso-legal',
    'privacidad.html':'privacidad',
    'cookies.html':'cookies',
    'accesibilidad.html':'accesibilidad',
    'bases-promociones.html':'promociones'
  };
  const page = pageMap[file];
  if(!page) return;

  const root = document.getElementById('contenido');
  if(!root) return;

  const text = value => String(value == null ? '' : value).trim();
  const active = value => {
    if(typeof value === 'boolean') return value;
    return !/^(no|false|0)$/i.test(text(value));
  };

  function normalize(items){
    return (Array.isArray(items) ? items : [])
      .map(item=>({
        id:text(item && item.id),
        pagina:text(item && item.pagina).toLowerCase(),
        titulo:text(item && item.titulo),
        texto:text(item && item.texto),
        activo:active(item && item.activo),
        orden:Number(item && item.orden) || 0
      }))
      .filter(item=>item.activo && item.pagina === page && item.titulo)
      .sort((a,b)=>a.orden-b.orden);
  }

  function appendText(container,value){
    const blocks = text(value).split(/\n\s*\n/).map(v=>v.trim()).filter(Boolean);
    blocks.forEach(block=>{
      const lines = block.split(/\n/).map(v=>v.trim()).filter(Boolean);
      const bullets = lines.length && lines.every(line=>/^[•*-]\s+/.test(line));
      if(bullets){
        const ul = document.createElement('ul');
        lines.forEach(line=>{
          const li = document.createElement('li');
          li.textContent = line.replace(/^[•*-]\s+/, '');
          ul.appendChild(li);
        });
        container.appendChild(ul);
        return;
      }
      lines.forEach(line=>{
        const p = document.createElement('p');
        p.textContent = line;
        container.appendChild(p);
      });
    });
  }

  function legalLinks(){
    const section = document.createElement('section');
    section.className = 'legal-card';
    const links = document.createElement('div');
    links.className = 'legal-links';
    [
      ['Aviso legal','aviso-legal.html'],
      ['Privacidad','privacidad.html'],
      ['Cookies y almacenamiento','cookies.html'],
      ['Accesibilidad','accesibilidad.html'],
      ['Promociones','bases-promociones.html']
    ].forEach(([label,href])=>{
      if(href === file) return;
      const a = document.createElement('a');
      a.href = href;
      a.textContent = label;
      links.appendChild(a);
    });
    section.appendChild(links);
    return section;
  }

  function render(items){
    if(!items.length) return;
    const fragment = document.createDocumentFragment();
    items.forEach(item=>{
      const section = document.createElement('section');
      section.className = 'legal-card';
      section.dataset.legalId = item.id;
      const h2 = document.createElement('h2');
      h2.textContent = item.titulo;
      section.appendChild(h2);
      appendText(section,item.texto);
      fragment.appendChild(section);
    });
    fragment.appendChild(legalLinks());
    root.replaceChildren(fragment);
  }

  async function init(){
    if(!window.OfaroData || typeof window.OfaroData.loadPublic !== 'function') return;
    try{
      const data = await window.OfaroData.loadPublic();
      render(normalize(data && (data.legales || data.textosLegales)));
    }catch(err){
      console.warn('O Faro: no se pudieron cargar los textos legales editables.',err);
    }
  }

  if(document.readyState === 'loading') document.addEventListener('DOMContentLoaded',init,{once:true});
  else init();
})();
