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

  const metaSections = new Set([
    'aviso-legal|identificación del titular',
    'privacidad|responsable del tratamiento',
    'cookies|almacenamiento técnico',
    'promociones|organizador'
  ]);

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

  function appendLinkedText(container,value){
    const source = text(value);
    const pattern = /([A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,})|(\b(?:\+34\s*)?\d{3}\s\d{3}\s\d{3}\b)/gi;
    let last = 0;
    let match;
    while((match = pattern.exec(source))){
      if(match.index > last) container.appendChild(document.createTextNode(source.slice(last,match.index)));
      const a = document.createElement('a');
      a.textContent = match[0];
      if(match[1]) a.href = 'mailto:' + match[0];
      else a.href = 'tel:' + match[0].replace(/\D/g,'').replace(/^981/,'34981');
      container.appendChild(a);
      last = pattern.lastIndex;
    }
    if(last < source.length) container.appendChild(document.createTextNode(source.slice(last)));
  }

  function appendParagraph(container,value){
    const clean = text(value).replace(/\n+/g,' ').trim();
    if(!clean) return;
    const p = document.createElement('p');

    if(/^No solicitamos datos de salud mediante el formulario web\./i.test(clean)){
      p.className = 'legal-warning';
      const firstSentence = 'No solicitamos datos de salud mediante el formulario web.';
      const strong = document.createElement('strong');
      strong.textContent = firstSentence;
      p.appendChild(strong);
      const rest = clean.slice(firstSentence.length).trim();
      if(rest){
        p.appendChild(document.createTextNode(' '));
        appendLinkedText(p,rest);
      }
    }else{
      if(/^La publicación de estas bases no implica/i.test(clean)) p.className = 'legal-warning';
      appendLinkedText(p,clean);
    }
    container.appendChild(p);
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
          appendLinkedText(li,line.replace(/^[•*-]\s+/,''));
          ul.appendChild(li);
        });
        container.appendChild(ul);
        return;
      }
      appendParagraph(container,lines.join(' '));
    });
  }

  function appendMetaSection(container,value){
    const blocks = text(value).split(/\n\s*\n/).map(v=>v.trim()).filter(Boolean);
    if(!blocks.length) return;

    const rows = blocks[0].split(/\n/).map(v=>v.trim()).filter(Boolean).map(line=>{
      const pos = line.indexOf(':');
      if(pos < 1) return null;
      return {label:line.slice(0,pos).trim(),value:line.slice(pos+1).trim()};
    }).filter(Boolean);

    if(rows.length){
      const meta = document.createElement('div');
      meta.className = 'legal-meta';
      rows.forEach(row=>{
        const card = document.createElement('div');
        const strong = document.createElement('strong');
        strong.textContent = row.label;
        card.appendChild(strong);
        appendLinkedText(card,row.value);
        meta.appendChild(card);
      });
      container.appendChild(meta);
    }else{
      appendParagraph(container,blocks[0]);
    }

    blocks.slice(1).forEach(block=>appendParagraph(container,block));
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

      const key = page + '|' + item.titulo.toLowerCase();
      if(metaSections.has(key)) appendMetaSection(section,item.texto);
      else appendText(section,item.texto);

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
