(function(){
  const trigger = document.getElementById('reserveTrigger');
  const wrap = document.getElementById('reserveFormWrap');
  const form = document.getElementById('reserveForm');
  const status = document.getElementById('reserveStatus');
  if(!trigger || !wrap || !form) return;

  const dateInput = form.elements.fecha;
  const originalTimeInput = form.elements.hora;
  const timeSelect = document.createElement('select');
  timeSelect.name = 'hora';
  timeSelect.required = true;
  timeSelect.disabled = true;
  timeSelect.setAttribute('aria-label','Hora de la reserva');
  timeSelect.innerHTML = '<option value="">Selecciona primero una fecha</option>';
  originalTimeInput.replaceWith(timeSelect);

  const WEEKDAYS = ['lunes','martes','miercoles','jueves','viernes','sabado','domingo'];
  const fallbackSchedule = {
    lunes:{activo:true,comida:['11:00','15:45'],cena:['20:00','23:00'],intervalo:15},
    martes:{activo:true,comida:['11:00','15:45'],cena:['20:00','23:00'],intervalo:15},
    miercoles:{activo:true,comida:['11:00','15:45'],cena:['20:00','23:00'],intervalo:15},
    jueves:{activo:true,comida:['11:00','15:45'],cena:null,intervalo:15},
    viernes:{activo:true,comida:['11:00','15:45'],cena:['20:00','23:00'],intervalo:15},
    sabado:{activo:true,comida:['11:00','15:45'],cena:['20:00','23:00'],intervalo:15},
    domingo:{activo:false,comida:null,cena:null,intervalo:15}
  };
  let schedule = fallbackSchedule;
  let scheduleReady = false;

  function norm(value){
    return String(value == null ? '' : value)
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g,'')
      .trim()
      .toLowerCase();
  }

  function yes(value){
    return !['no','false','0',''].includes(norm(value));
  }

  function madridToday(){
    const parts = new Intl.DateTimeFormat('en-CA',{timeZone:'Europe/Madrid',year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(new Date());
    const out = {};
    parts.forEach(p=>{ if(p.type !== 'literal') out[p.type] = p.value; });
    return out.year+'-'+out.month+'-'+out.day;
  }

  dateInput.min = madridToday();

  function parseTime(value){
    const m = String(value || '').trim().match(/^(\d{1,2}):(\d{2})$/);
    if(!m) return null;
    const h = Number(m[1]);
    const min = Number(m[2]);
    if(h < 0 || h > 23 || min < 0 || min > 59) return null;
    return h * 60 + min;
  }

  function formatTime(total){
    const h = Math.floor(total / 60);
    const m = total % 60;
    return String(h).padStart(2,'0')+':'+String(m).padStart(2,'0');
  }

  function slotsBetween(start,end,step){
    const a = parseTime(start);
    const b = parseTime(end);
    const every = Math.max(5,Number(step) || 15);
    if(a == null || b == null || b < a) return [];
    const slots = [];
    for(let value=a; value<=b; value+=every) slots.push(formatTime(value));
    if(slots[slots.length-1] !== formatTime(b)) slots.push(formatTime(b));
    return slots;
  }

  function weekdayForDate(iso){
    if(!/^\d{4}-\d{2}-\d{2}$/.test(String(iso || ''))) return '';
    const d = new Date(iso+'T12:00:00');
    return norm(new Intl.DateTimeFormat('es-ES',{weekday:'long',timeZone:'Europe/Madrid'}).format(d));
  }

  function currentMadridMinutes(){
    const parts = new Intl.DateTimeFormat('es-ES',{timeZone:'Europe/Madrid',hour:'2-digit',minute:'2-digit',hour12:false}).formatToParts(new Date());
    const out = {};
    parts.forEach(p=>{ if(p.type !== 'literal') out[p.type] = p.value; });
    return (Number(out.hour)||0)*60 + (Number(out.minute)||0);
  }

  function allowedSlots(iso){
    const weekday = weekdayForDate(iso);
    const cfg = schedule[weekday];
    if(!cfg || !cfg.activo) return [];
    let slots = [];
    if(cfg.comida) slots = slots.concat(slotsBetween(cfg.comida[0],cfg.comida[1],cfg.intervalo));
    if(cfg.cena) slots = slots.concat(slotsBetween(cfg.cena[0],cfg.cena[1],cfg.intervalo));
    slots = Array.from(new Set(slots)).sort();

    if(iso === madridToday()){
      const now = currentMadridMinutes();
      slots = slots.filter(slot => parseTime(slot) > now);
    }
    return slots;
  }

  function renderTimes(){
    const iso = dateInput.value;
    if(!iso){
      timeSelect.disabled = true;
      timeSelect.innerHTML = '<option value="">Selecciona primero una fecha</option>';
      return;
    }

    const slots = allowedSlots(iso);
    if(!slots.length){
      timeSelect.disabled = true;
      const weekday = weekdayForDate(iso);
      const cfg = schedule[weekday];
      const message = cfg && cfg.activo ? 'No quedan horas disponibles para este día' : 'No aceptamos reservas este día';
      timeSelect.innerHTML = '<option value="">'+message+'</option>';
      return;
    }

    timeSelect.disabled = false;
    timeSelect.innerHTML = '<option value="">Selecciona una hora</option>' + slots.map(slot=>'<option value="'+slot+'">'+slot+'</option>').join('');
  }

  function loadScheduleFromSheet(){
    return new Promise((resolve,reject)=>{
      const cfg = window.OFARO_CONFIG || {};
      const spreadsheetId = String(cfg.spreadsheetId || '').trim();
      if(!spreadsheetId) return reject(new Error('Falta spreadsheetId'));

      const callback = '__ofaro_res_hours_' + Math.random().toString(36).slice(2);
      const script = document.createElement('script');
      let finished = false;
      let timer;

      function done(fn,value){
        if(finished) return;
        finished = true;
        clearTimeout(timer);
        try{ delete window[callback]; }catch(_){ window[callback] = undefined; }
        script.remove();
        fn(value);
      }

      window[callback] = response => {
        if(!response || response.status === 'error' || !response.table){
          return done(reject,new Error('No se pudieron cargar los horarios'));
        }
        const labels = (response.table.cols || []).map((col,i)=>norm(col.label || col.id || ('col'+i)));
        const rows = (response.table.rows || []).map(row=>{
          const obj = {};
          labels.forEach((label,i)=>{
            const cell = row.c && row.c[i];
            obj[label] = cell && cell.v !== null && typeof cell.v !== 'undefined' ? cell.v : '';
          });
          return obj;
        });

        const parsed = {};
        rows.forEach(r=>{
          const day = norm(r['dia']);
          if(!WEEKDAYS.includes(day)) return;
          const foodStart = String(r['primera comida'] || '').trim();
          const foodEnd = String(r['ultima comida'] || '').trim();
          const dinnerStart = String(r['primera cena'] || '').trim();
          const dinnerEnd = String(r['ultima cena'] || '').trim();
          parsed[day] = {
            activo:yes(r['activo']),
            comida:foodStart && foodEnd ? [foodStart,foodEnd] : null,
            cena:dinnerStart && dinnerEnd ? [dinnerStart,dinnerEnd] : null,
            intervalo:Math.max(5,Number(r['intervalo (min)']) || 15)
          };
        });

        if(!Object.keys(parsed).length) return done(reject,new Error('La tabla de horarios está vacía'));
        done(resolve,Object.assign({},fallbackSchedule,parsed));
      };

      script.onerror = ()=>done(reject,new Error('No se pudo leer Horarios reservas'));
      const params = new URLSearchParams({
        sheet:'Horarios reservas',
        headers:'1',
        tq:'select *',
        tqx:'out:json;responseHandler:' + callback,
        _:String(Date.now())
      });
      script.src = 'https://docs.google.com/spreadsheets/d/' + encodeURIComponent(spreadsheetId) + '/gviz/tq?' + params.toString();
      document.head.appendChild(script);
      timer = setTimeout(()=>done(reject,new Error('Tiempo de espera agotado')),4500);
    });
  }

  loadScheduleFromSheet().then(data=>{
    schedule = data;
    scheduleReady = true;
    renderTimes();
  }).catch(err=>{
    console.warn('O Faro: se usa el horario de reservas de respaldo.',err);
    scheduleReady = true;
    renderTimes();
  });

  dateInput.addEventListener('change',renderTimes);

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

    if(!scheduleReady || !allowedSlots(payload.fecha).includes(payload.hora)){
      status.className = 'reserve-status error';
      status.textContent = 'Selecciona una fecha y una hora disponibles.';
      return;
    }

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
      dateInput.min = madridToday();
      renderTimes();
    }catch(err){
      status.className = 'reserve-status error';
      status.textContent = err && err.message ? err.message : 'No se pudo enviar la solicitud.';
    }finally{
      submit.disabled = false;
      submit.textContent = 'Solicitar reserva';
    }
  });
})();
