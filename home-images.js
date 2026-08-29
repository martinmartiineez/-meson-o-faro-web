(function(){
  function norm(value){
    return String(value || '').normalize('NFD').replace(/[\u0300-\u036f]/g,'').trim().toLowerCase();
  }

  function active(item){
    return item && item.url && !['no','false','0'].includes(norm(item.activa));
  }

  function applyImages(data){
    const images = Array.isArray(data && data.imagenes) ? data.imagenes.filter(active) : [];
    if(!images.length) return;

    const ordered = images.slice().sort((a,b)=>(Number(a.orden)||0)-(Number(b.orden)||0));

    const presentation = ordered.find(item => norm(item.seccion) === 'presentacion');
    const aboutImage = document.getElementById('aboutImage');
    if(presentation && aboutImage){
      aboutImage.src = presentation.url;
      aboutImage.alt = presentation.alt || presentation.nombre || 'Mesón O Faro';
    }

    const galleryItems = ordered.filter(item => norm(item.seccion) === 'galeria');
    const gallery = document.getElementById('homeGallery');
    if(gallery && galleryItems.length){
      gallery.replaceChildren();
      galleryItems.forEach(item => {
        const img = document.createElement('img');
        img.src = item.url;
        img.alt = item.alt || item.nombre || 'Mesón O Faro';
        img.loading = 'lazy';
        gallery.appendChild(img);
      });
    }
  }

  async function init(){
    if(!window.OfaroData || typeof window.OfaroData.loadPublic !== 'function') return;
    try{
      const data = await window.OfaroData.loadPublic();
      applyImages(data);
    }catch(err){
      console.warn('O Faro: no se pudieron cargar las imágenes dinámicas.', err);
    }
  }

  if(document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
})();
