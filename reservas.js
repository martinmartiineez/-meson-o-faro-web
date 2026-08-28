(function(){
  const trigger = document.getElementById('reserveTrigger');
  const wrap = document.getElementById('reserveFormWrap');
  const form = document.getElementById('reserveForm');
  const status = document.getElementById('reserveStatus');
  if(!trigger || !wrap || !form) return;

  const dateInput = form.elements.fecha;
  const today = new Date();
  const yyyy = today.getFullYear();
  const mm = String(today.getMonth()+1).padStart(2,'0');
  const dd = String(today.getDate()).padStart(2,'0');
  dateInput.min = yyyy+'-'+mm+'-'+dd;

  trigger.addEventListener('click', function(){
    const hidden = wrap.hidden;
    wrap.hidden = !hidden;
    trigger.setAttribute('aria-expanded', String(hidden));
    trigger.textContent = hidden ? 'Cerrar formulario' : 'Reservar mesa';
    if(hidden) setTimeout(function(){ form.elements.nombre.focus(); }, 50);
  });

  form.addEventListener('submit', async function(e){
    e.preventDefault();
    status.className = 'reserve-status';
    status.textContent = '';
    const submit = form.querySelector('button[type="submit"]');

    const payload = {
      nombre: form.elements.nombre.value.trim(),
      telefono: form.elements.telefono.value.trim(),
      correo: form.elements.correo.value.trim(),
      fecha: form.elements.fecha.value,
      hora: form.elements.hora.value,
      personas: Number(form.elements.personas.value),
      observaciones: form.elements.observaciones.value.trim()
    };

    if(!window.OfaroData || !OfaroData.apiUrl){
      status.className = 'reserve-status error';
      status.textContent = 'El sistema de reservas está preparado, pero falta activar la conexión con Google antes de recibir solicitudes reales.';
      return;
    }

    submit.disabled = true;
    submit.textContent = 'Enviando…';
    try{
      const result = await OfaroData.apiPost('reserve', payload);
      status.className = 'reserve-status success';
      status.textContent = result.message || 'Solicitud enviada correctamente.';
      form.reset();
      dateInput.min = yyyy+'-'+mm+'-'+dd;
    }catch(err){
      status.className = 'reserve-status error';
      status.textContent = err && err.message ? err.message : 'No se pudo enviar la solicitud.';
    }finally{
      submit.disabled = false;
      submit.textContent = 'Solicitar reserva';
    }
  });
})();
