(() => {
  'use strict';

  const API_URL = 'https://script.google.com/macros/s/AKfycbwyICNMM0CHeSFQqOaO4d6g_d84vougY6OivfrMi6G5DIIVy7Y1qK_v2tBsZKmnQ2njkQ/exec';
  const STORAGE_KEY = 'ofaro_gestion_key';
  const STORAGE_TERMINAL = 'ofaro_gestion_terminal';
  const APP_VERSION = 'web-1.0.0';

  const app = document.getElementById('app');
  const bottomNav = document.getElementById('bottomNav');
  const topSubtitle = document.getElementById('topSubtitle');
  const homeButton = document.getElementById('homeButton');
  const modal = document.getElementById('modal');
  const modalContent = document.getElementById('modalContent');
  const toastEl = document.getElementById('toast');
  const printRoot = document.getElementById('printRoot');

  const state = {
    key: localStorage.getItem(STORAGE_KEY) || '',
    terminal: localStorage.getItem(STORAGE_TERMINAL) || 'iPhone O Faro',
    route: 'home',
    currentReservation: null,
    currentTicket: null,
    ticketImageData: '',
    ticketImagePosition: 'top',
    scanner: null
  };

  const esc = value => String(value ?? '').replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
  const val = value => String(value ?? '').trim();
  const todayIso = () => {
    const d = new Date();
    const y = d.getFullYear();
    const m = String(d.getMonth()+1).padStart(2,'0');
    const day = String(d.getDate()).padStart(2,'0');
    return `${y}-${m}-${day}`;
  };

  function toast(message){
    toastEl.textContent = message;
    toastEl.hidden = false;
    clearTimeout(toast._timer);
    toast._timer = setTimeout(()=>{ toastEl.hidden = true; }, 2600);
  }

  function loader(text='Cargando…'){
    return `<div class="loader"><span class="dot"></span><span class="dot"></span><span class="dot"></span><strong>${esc(text)}</strong></div>`;
  }

  function errorBox(message){
    return `<div class="notice error">${esc(message)}</div>`;
  }

  async function post(action, payload={}){
    if(!state.key) throw new Error('Falta la clave de gestión.');
    const body = Object.assign({action,key:state.key,terminal:state.terminal,appVersion:APP_VERSION}, payload || {});
    const response = await fetch(API_URL, {
      method:'POST',
      body:JSON.stringify(body),
      cache:'no-store',
      redirect:'follow'
    });
    const text = await response.text();
    let data;
    try { data = JSON.parse(text); }
    catch(_){ throw new Error('La API no devolvió una respuesta válida.'); }
    if(!response.ok || !data || data.ok === false) throw new Error((data && data.error) || `Error HTTP ${response.status}`);
    return data;
  }

  async function testConnection(key = state.key, terminal = state.terminal){
    const body = {action:'appPing',key,terminal,appVersion:APP_VERSION};
    const response = await fetch(API_URL,{method:'POST',body:JSON.stringify(body),cache:'no-store',redirect:'follow'});
    const data = await response.json();
    if(!response.ok || !data || data.ok === false) throw new Error((data && data.error) || 'No se pudo conectar.');
    return data;
  }

  function setRoute(route){
    state.route = route;
    [...bottomNav.querySelectorAll('.nav-item')].forEach(btn=>btn.classList.toggle('is-active',btn.dataset.route===route));
    homeButton.hidden = route === 'home' || !state.key;
    if(route === 'home') renderHome();
    else if(route === 'reservations') renderReservations();
    else if(route === 'web') renderWebSections();
    else if(route === 'participations') renderParticipations();
    else if(route === 'tickets') renderTickets();
    else if(route === 'history') renderHistory();
    else if(route === 'settings') renderSettings();
  }

  function page(title, html){
    topSubtitle.textContent = title;
    app.innerHTML = html;
    window.scrollTo({top:0,behavior:'instant'});
  }

  function openModal(html){
    modalContent.innerHTML = html;
    modal.hidden = false;
    document.body.style.overflow = 'hidden';
  }

  async function closeModal(){
    if(state.scanner){
      try{ await state.scanner.stop(); }catch(_){}
      try{ state.scanner.clear(); }catch(_){}
      state.scanner = null;
    }
    modal.hidden = true;
    modalContent.innerHTML = '';
    document.body.style.overflow = '';
  }

  function modalHeader(kicker,title){
    return `<div class="modal-title-row"><div><div class="kicker">${esc(kicker)}</div><h2>${esc(title)}</h2></div><button class="modal-close" data-close-modal type="button">×</button></div>`;
  }

  function requireAuth(){
    if(state.key) return true;
    renderLogin();
    return false;
  }

  function renderLogin(){
    bottomNav.hidden = true;
    homeButton.hidden = true;
    page('Acceso privado', `
      <section class="hero">
        <div class="kicker">O Faro · Gestión</div>
        <h1>Panel interno</h1>
        <p class="lead">Gestiona reservas, carta, menú, premios y tickets desde tu iPhone. La clave queda guardada únicamente en este dispositivo.</p>
      </section>
      <section class="card">
        <label class="field"><span>Clave app gestión</span><input id="loginKey" type="password" autocomplete="current-password" placeholder="Pega la clave de la app"></label>
        <label class="field"><span>Nombre de este terminal</span><input id="loginTerminal" value="${esc(state.terminal)}" autocomplete="off"></label>
        <button id="loginButton" class="btn" type="button">CONECTAR</button>
        <div id="loginStatus" style="margin-top:10px"></div>
      </section>
      <p class="install-note">Después puedes instalarla desde Safari: <strong>Compartir → Añadir a pantalla de inicio</strong>.</p>
    `);
    document.getElementById('loginButton').addEventListener('click', async () => {
      const button = document.getElementById('loginButton');
      const status = document.getElementById('loginStatus');
      const key = val(document.getElementById('loginKey').value);
      const terminal = val(document.getElementById('loginTerminal').value) || 'iPhone O Faro';
      if(!key){ status.innerHTML = errorBox('Introduce la clave de gestión.'); return; }
      button.disabled = true;
      status.innerHTML = loader('Comprobando acceso…');
      try{
        const res = await testConnection(key,terminal);
        state.key = key;
        state.terminal = terminal;
        localStorage.setItem(STORAGE_KEY,key);
        localStorage.setItem(STORAGE_TERMINAL,terminal);
        bottomNav.hidden = false;
        toast(res.message || 'Conexión correcta');
        setRoute('home');
      }catch(err){
        status.innerHTML = errorBox(err.message);
        button.disabled = false;
      }
    });
  }

  async function renderHome(){
    if(!requireAuth()) return;
    bottomNav.hidden = false;
    page('Gestión', `
      <section class="hero">
        <div class="kicker">Panel interno</div>
        <h1>¿Qué necesitas?</h1>
        <p class="lead" id="homeStatus">Conectando con O Faro…</p>
      </section>
      <div class="grid two">
        <button class="module-card dark" data-go="reservations"><strong>Reservas</strong><small id="homeReservationText">Consultar y gestionar</small></button>
        <button class="module-card" data-go="web"><strong>Gestión web</strong><small>Carta, menú, avisos, horarios…</small></button>
        <button class="module-card" data-go="participations"><strong>Participaciones</strong><small>Generar, validar y canjear</small></button>
        <button class="module-card" data-go="tickets"><strong>Tickets</strong><small>Previsualizar, QR e imágenes</small></button>
        <button class="module-card" data-go="history"><strong>Historial</strong><small>Movimientos recientes</small></button>
        <button class="module-card" data-go="settings"><strong>Ajustes</strong><small>Acceso e instalación</small></button>
      </div>
    `);
    app.querySelectorAll('[data-go]').forEach(btn=>btn.addEventListener('click',()=>setRoute(btn.dataset.go)));
    try{
      const [ping,reservations] = await Promise.all([
        post('appPing'),
        post('reservationList',{date:todayIso(),limit:100,includeClosed:true})
      ]);
      document.getElementById('homeStatus').textContent = `${ping.message || 'API operativa'} · ${state.terminal}`;
      const items = reservations.items || [];
      const pending = items.filter(r=>/^pendiente$/i.test(r.state)).length;
      document.getElementById('homeReservationText').textContent = `${items.length} hoy · ${pending} pendientes`;
    }catch(err){
      document.getElementById('homeStatus').textContent = `Sin conexión: ${err.message}`;
    }
  }

  async function renderReservations(date = todayIso()){
    if(!requireAuth()) return;
    page('Reservas', `
      <div class="section-head"><div><div class="kicker">Agenda</div><h2>Reservas</h2></div><button id="newReservation" class="btn" style="width:auto;min-height:44px">+ NUEVA</button></div>
      <div class="card">
        <div class="search-row">
          <label class="field"><span>Fecha</span><input id="reservationDate" type="date" value="${esc(date)}"></label>
          <button id="loadReservations" class="btn secondary" type="button">VER</button>
        </div>
      </div>
      <div id="reservationSummary" style="margin:14px 2px 10px">${loader('Cargando reservas…')}</div>
      <div id="reservationList"></div>
    `);
    document.getElementById('newReservation').addEventListener('click',()=>openNewReservation());
    document.getElementById('loadReservations').addEventListener('click',()=>renderReservations(document.getElementById('reservationDate').value || todayIso()));
    try{
      const res = await post('reservationList',{date,limit:200,includeClosed:true});
      const items = res.items || [];
      const pending = items.filter(r=>/^pendiente$/i.test(r.state)).length;
      document.getElementById('reservationSummary').innerHTML = `<span class="status-pill">${items.length} reservas</span> <span class="status-pill warn">${pending} pendientes</span> <span class="status-pill">${Number(res.totalPeople||0)} personas</span>`;
      const list = document.getElementById('reservationList');
      if(!items.length){
        list.innerHTML = `<div class="empty">No hay reservas para esta fecha.</div>`;
        return;
      }
      list.innerHTML = items.map(r=>reservationCard(r)).join('');
      list.querySelectorAll('[data-reservation]').forEach(btn=>btn.addEventListener('click',()=>{
        const item = items.find(r=>r.id===btn.dataset.reservation);
        if(item) openReservation(item);
      }));
    }catch(err){
      document.getElementById('reservationList').innerHTML = errorBox(err.message);
    }
  }

  function reservationCard(r){
    const stateClass = /^confirmada$/i.test(r.state)?'ok':(/^denegada|cancelada$/i.test(r.state)?'bad':(/^pendiente$/i.test(r.state)?'warn':''));
    return `<article class="card">
      <div class="card-head"><div><div class="card-title">${esc(r.time || '--:--')} · ${esc(r.name)}</div><div class="card-meta">${Number(r.people||0)} personas${r.table?` · Mesa ${esc(r.table)}`:''}${r.zone && r.zone!=='Sin asignar'?` · ${esc(r.zone)}`:''}</div></div><span class="status-pill ${stateClass}">${esc(r.state || 'Pendiente')}</span></div>
      ${r.notes?`<div class="card-note">${esc(r.notes)}</div>`:''}
      <div class="card-meta" style="margin-top:9px">Servicio: <strong>${esc(r.serviceState || 'Pendiente')}</strong>${r.customerEmailState?` · Correo: ${esc(r.customerEmailState)}`:''}</div>
      <div class="actions"><button class="btn secondary" data-reservation="${esc(r.id)}">ABRIR</button></div>
    </article>`;
  }

  function openReservation(r){
    state.currentReservation = r;
    const phoneDigits = val(r.phone).replace(/[^+\d]/g,'');
    const waDigits = phoneDigits.replace(/^\+/,'');
    openModal(`${modalHeader('Reserva',`${r.time || ''} · ${r.name || ''}`)}
      <div class="card">
        <div class="card-meta">${esc(r.date)} · ${Number(r.people||0)} personas</div>
        <div class="card-title" style="margin-top:4px">${esc(r.name)}</div>
        <div class="card-meta">${esc(r.phone || 'Sin teléfono')} · ${esc(r.email || 'Sin correo')}</div>
        <div class="card-meta">Mesa: ${esc(r.table || '—')} · Zona: ${esc(r.zone || 'Sin asignar')}</div>
        ${r.notes?`<div class="card-note">${esc(r.notes)}</div>`:''}
        <div style="margin-top:12px"><span class="status-pill">${esc(r.state||'Pendiente')}</span> <span class="status-pill">${esc(r.serviceState||'Pendiente')}</span></div>
      </div>
      <div class="actions">
        ${phoneDigits?`<a class="btn secondary" href="tel:${esc(phoneDigits)}" style="text-decoration:none;text-align:center">LLAMAR</a>`:''}
        ${waDigits?`<a class="btn secondary" href="https://wa.me/${esc(waDigits)}" target="_blank" rel="noopener" style="text-decoration:none;text-align:center">WHATSAPP</a>`:''}
        <button class="btn secondary" id="editReservation">EDITAR</button>
        <button class="btn secondary" id="previewReservation">TICKET</button>
      </div>
      <div class="section-head"><h3>Estado de reserva</h3></div>
      <div class="grid two">
        <button class="btn ok" data-res-state="Confirmada">CONFIRMAR</button>
        <button class="btn danger" data-res-state="Denegada">DENEGAR</button>
        <button class="btn secondary" data-res-state="Pendiente">PENDIENTE</button>
        <button class="btn danger" data-res-state="Cancelada">CANCELAR</button>
      </div>
      <label class="field"><span>Estado del servicio</span><select id="serviceState">
        ${['Pendiente','Llegó','Sentada','En servicio','Completada','No se presentó'].map(s=>`<option ${s===(r.serviceState||'Pendiente')?'selected':''}>${esc(s)}</option>`).join('')}
      </select></label>
      <button id="saveServiceState" class="btn secondary" type="button">GUARDAR ESTADO DE SERVICIO</button>
      ${(r.email && /^(confirmada|denegada)$/i.test(r.state||''))?`<button id="resendReservationEmail" class="btn secondary" type="button">REENVIAR CORREO AL CLIENTE</button>`:''}
      <div id="reservationActionStatus" style="margin-top:10px"></div>
    `);
    document.getElementById('editReservation').addEventListener('click',()=>openReservationEditor(r));
    document.getElementById('previewReservation').addEventListener('click',()=>openTicketPreview(reservationTicket(r)));
    modalContent.querySelectorAll('[data-res-state]').forEach(btn=>btn.addEventListener('click',()=>changeReservationState(r,btn.dataset.resState)));
    document.getElementById('saveServiceState').addEventListener('click',()=>saveReservationService(r,document.getElementById('serviceState').value));
    const resend = document.getElementById('resendReservationEmail');
    if(resend) resend.addEventListener('click',()=>reservationAction(r,{state:r.state,sendEmail:true,forceEmail:true},'Correo reenviado'));
  }

  async function changeReservationState(r,newState){
    let sendEmail = false;
    if((newState==='Confirmada' || newState==='Denegada') && r.email){
      sendEmail = confirm(`¿Quieres enviar al cliente el correo de ${newState==='Confirmada'?'confirmación':'denegación'}?\n\nAceptar = enviar correo\nCancelar = cambiar estado sin correo`);
    }
    if(!confirm(`¿Cambiar la reserva de ${r.name} a “${newState}”?`)) return;
    await reservationAction(r,{state:newState,sendEmail},`Reserva: ${newState}`);
  }

  async function saveReservationService(r,serviceState){
    if(!confirm(`¿Cambiar el estado del servicio a “${serviceState}”?`)) return;
    await reservationAction(r,{serviceState},`Servicio: ${serviceState}`);
  }

  async function reservationAction(r,payload,successText){
    const status = document.getElementById('reservationActionStatus');
    if(status) status.innerHTML = loader('Guardando…');
    try{
      await post('reservationAction',Object.assign({id:r.id},payload));
      toast(successText || 'Reserva actualizada');
      await closeModal();
      renderReservations(r.date || todayIso());
    }catch(err){
      if(status) status.innerHTML = errorBox(err.message);
      else alert(err.message);
    }
  }

  function openReservationEditor(r){
    openModal(`${modalHeader('Editar reserva',r.name || 'Reserva')}
      <label class="field"><span>Nombre</span><input id="erName" value="${esc(r.name)}"></label>
      <div class="inline-fields"><label class="field"><span>Fecha</span><input id="erDate" type="date" value="${esc(r.date)}"></label><label class="field"><span>Hora</span><input id="erTime" type="time" value="${esc(r.time)}"></label></div>
      <div class="inline-fields"><label class="field"><span>Personas</span><input id="erPeople" type="number" min="1" max="30" value="${Number(r.people||1)}"></label><label class="field"><span>Mesa</span><input id="erTable" value="${esc(r.table)}"></label></div>
      <label class="field"><span>Zona</span><select id="erZone">${['Sin asignar','Interior','Terraza'].map(z=>`<option ${z===(r.zone||'Sin asignar')?'selected':''}>${z}</option>`).join('')}</select></label>
      <label class="field"><span>Teléfono</span><input id="erPhone" type="tel" value="${esc(r.phone)}"></label>
      <label class="field"><span>Correo</span><input id="erEmail" type="email" value="${esc(r.email)}"></label>
      <label class="field"><span>Observaciones</span><textarea id="erNotes">${esc(r.notes)}</textarea></label>
      <button id="saveReservationEdit" class="btn" type="button">GUARDAR CAMBIOS</button>
      <div id="reservationEditStatus" style="margin-top:10px"></div>
    `);
    document.getElementById('saveReservationEdit').addEventListener('click',async()=>{
      const button = document.getElementById('saveReservationEdit');
      const status=document.getElementById('reservationEditStatus');
      const targetDate = val(document.getElementById('erDate').value) || r.date || todayIso();
      button.disabled=true;
      status.innerHTML=loader('Guardando…');
      try{
        await post('reservationFullUpdate',{
          id:r.id,
          name:val(document.getElementById('erName').value),
          date:targetDate,
          time:val(document.getElementById('erTime').value),
          people:Number(document.getElementById('erPeople').value)||1,
          table:val(document.getElementById('erTable').value),
          zone:val(document.getElementById('erZone').value),
          phone:val(document.getElementById('erPhone').value),
          email:val(document.getElementById('erEmail').value),
          notes:val(document.getElementById('erNotes').value)
        });
        toast('Reserva actualizada');
        await closeModal();
        renderReservations(targetDate);
      }catch(err){
        button.disabled=false;
        status.innerHTML=errorBox(err.message);
      }
    });
  }

  function openNewReservation(){
    openModal(`${modalHeader('Reservas','Nueva reserva')}
      <label class="field"><span>Nombre *</span><input id="nrName"></label>
      <div class="inline-fields"><label class="field"><span>Fecha *</span><input id="nrDate" type="date" value="${todayIso()}"></label><label class="field"><span>Hora *</span><input id="nrTime" type="time" value="14:00"></label></div>
      <div class="inline-fields"><label class="field"><span>Personas *</span><input id="nrPeople" type="number" min="1" max="30" value="2"></label><label class="field"><span>Mesa</span><input id="nrTable"></label></div>
      <label class="field"><span>Zona</span><select id="nrZone"><option>Sin asignar</option><option>Interior</option><option>Terraza</option></select></label>
      <label class="field"><span>Teléfono *</span><input id="nrPhone" type="tel"></label>
      <label class="field"><span>Correo</span><input id="nrEmail" type="email"></label>
      <label class="field"><span>Estado inicial</span><select id="nrState"><option>Confirmada</option><option>Pendiente</option></select></label>
      <label class="field"><span>Observaciones</span><textarea id="nrNotes"></textarea></label>
      <button id="createReservationButton" class="btn" type="button">GUARDAR RESERVA</button>
      <div id="newReservationStatus" style="margin-top:10px"></div>
    `);
    document.getElementById('createReservationButton').addEventListener('click',async()=>{
      const status=document.getElementById('newReservationStatus');
      const button=document.getElementById('createReservationButton');
      const body={
        nombre:val(document.getElementById('nrName').value),
        telefono:val(document.getElementById('nrPhone').value),
        correo:val(document.getElementById('nrEmail').value),
        fecha:val(document.getElementById('nrDate').value),
        hora:val(document.getElementById('nrTime').value),
        personas:Number(document.getElementById('nrPeople').value)||1,
        mesa:val(document.getElementById('nrTable').value),
        zona:val(document.getElementById('nrZone').value),
        observaciones:val(document.getElementById('nrNotes').value)
      };
      if(!body.nombre||!body.telefono||!body.fecha||!body.hora){
        status.innerHTML=errorBox('Nombre, teléfono, fecha y hora son obligatorios.');
        return;
      }
      button.disabled=true;
      status.innerHTML=loader('Guardando…');
      try{
        const res=await post('reservationCreate',body);
        const wanted=val(document.getElementById('nrState').value);
        if(wanted==='Pendiente') await post('reservationAction',{id:res.id,state:'Pendiente'});
        toast('Reserva creada');
        await closeModal();
        renderReservations(body.fecha);
      }catch(err){
        button.disabled=false;
        status.innerHTML=errorBox(err.message);
      }
    });
  }

  function reservationTicket(r){
    const body = [
      `${r.date || ''}  ${r.time || ''}`,
      '',
      String(r.name || '').toUpperCase(),
      `${Number(r.people||0)} PERSONAS`,
      r.table ? `Mesa: ${r.table}` : '',
      r.zone && r.zone!=='Sin asignar' ? `Zona: ${r.zone}` : '',
      r.phone ? `Tel: ${r.phone}` : '',
      r.notes ? `\nOBSERVACIONES\n${r.notes}` : '',
      `\n${r.id || ''}`
    ].filter(Boolean).join('\n');
    return {title:'MESÓN O FARO',subtitle:'RESERVA',body,qr:'',code:r.id||'',type:'Reserva'};
  }

  async function renderWebSections(){
    if(!requireAuth()) return;
    page('Gestión web', `<section class="hero"><div class="kicker">Contenido conectado</div><h1>Gestión web</h1><p class="lead">Los cambios se guardan en Google Sheets y alimentan la web pública.</p></section><div id="webSections">${loader('Cargando apartados…')}</div>`);
    try{
      const res=await post('webSections');
      const items=res.items||[];
      document.getElementById('webSections').innerHTML=`<div class="grid two">${items.map(s=>`<button class="module-card" data-web-section="${esc(s.key)}"><strong>${esc(s.title)}</strong><small>${esc(s.description||'')}</small></button>`).join('')}</div>`;
      app.querySelectorAll('[data-web-section]').forEach(btn=>btn.addEventListener('click',()=>renderWebSection(btn.dataset.webSection)));
    }catch(err){
      document.getElementById('webSections').innerHTML=errorBox(err.message);
    }
  }

  async function renderWebSection(section){
    page('Gestión web', `<div class="section-head"><button id="backWeb" class="link-button">‹ APARTADOS</button></div><div id="webSectionContent">${loader('Cargando datos…')}</div>`);
    document.getElementById('backWeb').addEventListener('click',renderWebSections);
    try{
      const res=await post('webSectionRows',{section});
      const rows=res.rows||[];
      const fields=res.fields||[];
      const content=document.getElementById('webSectionContent');
      content.innerHTML=`<section class="hero"><div class="kicker">${esc(res.title)}</div><h1>${esc(res.title)}</h1><p class="lead">${rows.length} registros</p></section>
        ${res.allowAdd?`<button id="addWebRow" class="btn" type="button">+ AÑADIR</button>`:''}
        <div id="webRowList" style="margin-top:12px">${rows.length?rows.map((row,i)=>webRowCard(row,fields,res.idKey,i)).join(''):`<div class="empty">No hay registros.</div>`}</div>`;
      const openEditor=(row,index)=>openWebRowEditor(section,res,row,fields,index);
      content.querySelectorAll('[data-web-row]').forEach(btn=>btn.addEventListener('click',()=>openEditor(rows[Number(btn.dataset.webRow)],Number(btn.dataset.webRow))));
      const add=document.getElementById('addWebRow');
      if(add) add.addEventListener('click',()=>openEditor({},-1));
    }catch(err){
      document.getElementById('webSectionContent').innerHTML=errorBox(err.message);
    }
  }

  function webRowCard(row,fields,idKey,index){
    const displayFields=fields.filter(f=>f.key!==idKey && val(row[f.key])).slice(0,3);
    const titleField=displayFields[0] || fields.find(f=>f.key!==idKey);
    const title=titleField ? row[titleField.key] : row[idKey];
    const meta=displayFields.slice(1).map(f=>`${f.label}: ${row[f.key]}`).join(' · ');
    return `<article class="card"><div class="card-head"><div><div class="card-title">${esc(title || row[idKey] || 'Nuevo registro')}</div><div class="card-meta">${esc(meta || row[idKey] || '')}</div></div><button class="btn secondary" style="width:auto;min-height:40px" data-web-row="${index}">EDITAR</button></div></article>`;
  }

  function openWebRowEditor(section,res,row,fields,index){
    const isNew=index<0;
    const inputs=fields.map(f=>{
      const id=`wf_${f.key}`;
      const value=row[f.key] ?? '';
      if(f.readOnly && isNew && f.key===res.idKey) return '';
      if(f.multiline) return `<label class="field"><span>${esc(f.label)}</span><textarea id="${esc(id)}" ${f.readOnly?'readonly':''}>${esc(value)}</textarea></label>`;
      return `<label class="field"><span>${esc(f.label)}</span><input id="${esc(id)}" ${f.type==='number'?'type="number" step="any"':''} value="${esc(value)}" ${f.readOnly?'readonly':''}></label>`;
    }).join('');
    openModal(`${modalHeader(isNew?'Nuevo registro':res.title,isNew?'Añadir':(row[res.idKey]||'Editar'))}${inputs}
      <button id="saveWebRow" class="btn" type="button">GUARDAR CAMBIOS</button>
      ${(!isNew && res.allowDelete)?`<button id="deleteWebRow" class="btn danger" type="button">ELIMINAR</button>`:''}
      <div id="webRowStatus" style="margin-top:10px"></div>`);
    document.getElementById('saveWebRow').addEventListener('click',async()=>{
      const values={};
      fields.forEach(f=>{
        const input=document.getElementById(`wf_${f.key}`);
        if(input) values[f.key]=input.value;
      });
      if(!isNew && row[res.idKey]) values[res.idKey]=row[res.idKey];
      const status=document.getElementById('webRowStatus');
      status.innerHTML=loader('Guardando…');
      try{
        await post('webSectionSave',{section,values});
        toast('Cambios guardados');
        await closeModal();
        renderWebSection(section);
      }catch(err){
        status.innerHTML=errorBox(err.message);
      }
    });
    const del=document.getElementById('deleteWebRow');
    if(del) del.addEventListener('click',async()=>{
      if(!confirm(`¿Eliminar definitivamente “${row[res.idKey]}”?`)) return;
      if(section==='legal' && !confirm('Es un texto legal. ¿Confirmas de nuevo que quieres eliminarlo?')) return;
      const status=document.getElementById('webRowStatus');
      status.innerHTML=loader('Eliminando…');
      try{
        await post('webSectionDelete',{section,id:row[res.idKey]});
        toast('Registro eliminado');
        await closeModal();
        renderWebSection(section);
      }catch(err){
        status.innerHTML=errorBox(err.message);
      }
    });
  }

  function renderParticipations(){
    if(!requireAuth()) return;
    page('Participaciones', `
      <section class="hero"><div class="kicker">Promociones</div><h1>Participaciones</h1><p class="lead">Genera tickets, escanea QR y canjea premios desde el iPhone.</p></section>
      <section class="card">
        <h2>Generar</h2>
        <label class="field"><span>Cantidad</span><input id="participationQty" type="number" min="1" max="20" value="1"></label>
        <button id="generateParticipation" class="btn" type="button">GENERAR</button>
        <div id="participationGenerateStatus" style="margin-top:10px"></div>
        <div id="generatedParticipationList"></div>
      </section>
      <section class="card">
        <h2>Validar y canjear</h2>
        <button id="openScanner" class="btn" type="button">ESCANEAR QR</button>
        <label class="field"><span>O escribe el código</span><input id="manualParticipation" placeholder="OF-XXXXX-XXXXX"></label>
        <button id="validateParticipation" class="btn secondary" type="button">VALIDAR CÓDIGO</button>
        <div id="participationValidation" style="margin-top:10px"></div>
      </section>
    `);
    document.getElementById('generateParticipation').addEventListener('click',generateParticipations);
    document.getElementById('openScanner').addEventListener('click',openParticipationScanner);
    document.getElementById('validateParticipation').addEventListener('click',()=>validateParticipation(val(document.getElementById('manualParticipation').value)));
  }

  async function generateParticipations(){
    const qty=Math.max(1,Math.min(20,Number(document.getElementById('participationQty').value)||1));
    const button=document.getElementById('generateParticipation');
    const status=document.getElementById('participationGenerateStatus');
    const list=document.getElementById('generatedParticipationList');
    button.disabled=true;
    status.innerHTML=loader(`Generando 0 de ${qty}…`);
    list.innerHTML='';
    const made=[];
    try{
      for(let i=0;i<qty;i++){
        const res=await post('participationCreate',{origin:'WebApp iPhone'});
        made.push(res);
        status.innerHTML=loader(`Generando ${i+1} de ${qty}…`);
      }
      status.innerHTML=`<div class="notice success">Generados ${made.length} códigos.</div>`;
      list.innerHTML=made.map((p,i)=>`<article class="card" style="margin-top:10px"><div class="card-title">${esc(p.code)}</div><div class="card-meta">${esc(p.createdAt||'')}</div><div class="actions"><button class="btn secondary" data-preview-part="${i}">PREVISUALIZAR</button></div></article>`).join('');
      list.querySelectorAll('[data-preview-part]').forEach(btn=>btn.addEventListener('click',()=>{
        const p=made[Number(btn.dataset.previewPart)];
        openTicketPreview({title:'MESÓN O FARO',subtitle:'PARTICIPACIÓN',body:`${p.code}\n${p.createdAt||''}\n\nEscanea este QR en O Faro\npara validar tu participación.\nConserva este ticket.`,qr:p.qrPayload||`OFARO:${p.code}`,code:p.code,type:'Participación'});
      }));
    }catch(err){
      status.innerHTML=errorBox(err.message);
    }finally{
      button.disabled=false;
    }
  }

  function openParticipationScanner(){
    openModal(`${modalHeader('Participaciones','Escanear QR')}<div class="scanner"><div id="reader"></div></div><div id="scannerStatus" class="notice">Autoriza el uso de la cámara cuando Safari lo solicite.</div><label class="field"><span>También puedes elegir una foto del QR</span><input id="qrImageInput" type="file" accept="image/*"></label>`);
    const status=document.getElementById('scannerStatus');
    if(typeof Html5Qrcode === 'undefined'){
      status.className='notice error';
      status.textContent='El lector QR todavía no ha cargado. Comprueba la conexión a Internet.';
      return;
    }
    state.scanner=new Html5Qrcode('reader');
    state.scanner.start({facingMode:'environment'},{fps:10,qrbox:{width:230,height:230}},async decoded=>{
      const code=decoded;
      try{ await state.scanner.stop(); }catch(_){}
      state.scanner=null;
      await closeModal();
      setRoute('participations');
      setTimeout(()=>{
        const input=document.getElementById('manualParticipation');
        if(input) input.value=code;
        validateParticipation(code);
      },50);
    },()=>{}).catch(()=>{
      status.className='notice error';
      status.textContent='No se pudo abrir la cámara. Puedes elegir una foto o introducir el código manualmente.';
    });
    document.getElementById('qrImageInput').addEventListener('change',async e=>{
      const file=e.target.files && e.target.files[0];
      if(!file||!state.scanner) return;
      try{
        try{ await state.scanner.stop(); }catch(_){}
        const decoded=await state.scanner.scanFile(file,true);
        state.scanner=null;
        await closeModal();
        setRoute('participations');
        setTimeout(()=>{
          const input=document.getElementById('manualParticipation');
          if(input) input.value=decoded;
          validateParticipation(decoded);
        },50);
      }catch(err){
        status.className='notice error';
        status.textContent='No pude leer un QR en esa imagen.';
      }
    });
  }

  async function validateParticipation(code){
    const out=document.getElementById('participationValidation');
    if(!out) return;
    if(!code){
      out.innerHTML=errorBox('Introduce o escanea un código.');
      return;
    }
    out.innerHTML=loader('Validando…');
    try{
      const res=await post('participationValidate',{code});
      const klass=res.state==='Canjeada'?'bad':(res.canRedeem?'ok':'warn');
      out.innerHTML=`<div class="card"><div class="card-head"><div><div class="card-title">${esc(res.prize || 'Sin premio')}</div><div class="card-meta">${esc(res.code)} · ${esc(res.state)}</div></div><span class="status-pill ${klass}">${esc(res.state)}</span></div>
        ${res.redeemedAt?`<div class="card-note">Canjeado: ${esc(res.redeemedAt)} · ${esc(res.redeemedBy||'')}</div>`:''}
        ${res.canRedeem?`<button id="redeemParticipation" class="btn ok" type="button">CANJEAR PREMIO</button>`:''}</div>`;
      const redeem=document.getElementById('redeemParticipation');
      if(redeem) redeem.addEventListener('click',async()=>{
        if(!confirm(`¿Entregar “${res.prize}” y marcar ${res.code} como canjeado? Esta acción no se puede deshacer.`)) return;
        redeem.disabled=true;
        try{
          const done=await post('participationRedeem',{code:res.code});
          toast(`Canjeado: ${done.prize}`);
          validateParticipation(res.code);
        }catch(err){
          alert(err.message);
          redeem.disabled=false;
        }
      });
    }catch(err){
      out.innerHTML=errorBox(err.message);
    }
  }

  async function renderTickets(){
    if(!requireAuth()) return;
    page('Tickets', `
      <section class="hero"><div class="kicker">Previsualización</div><h1>Tickets</h1><p class="lead">Puedes ver cómo quedarán sin estar conectado a la térmica. En iPhone, “Imprimir / PDF” abre el sistema de impresión de iOS.</p></section>
      <section class="card"><h2>Impresión libre</h2><label class="field"><span>Título</span><input id="freeTitle" placeholder="MESÓN O FARO"></label><label class="field"><span>Texto</span><textarea id="freeBody" placeholder="Escribe aquí…"></textarea></label><label class="field"><span>QR opcional</span><input id="freeQr" placeholder="URL o texto"></label><button id="previewFreeTicket" class="btn">PREVISUALIZAR</button></section>
      <section class="card"><h2>Imagen</h2><p class="lead">Crea un ticket solo con una imagen o úsala arriba/debajo de cualquier ticket.</p><button id="previewImageTicket" class="btn secondary">PREVISUALIZAR IMAGEN</button></section>
      <div class="section-head"><h2>QR rápidos</h2></div><div id="qrTicketList">${loader('Cargando QR…')}</div>
      <div class="section-head"><h2>Plantillas</h2></div><div id="templateTicketList">${loader('Cargando plantillas…')}</div>
    `);
    document.getElementById('previewFreeTicket').addEventListener('click',()=>openTicketPreview({title:val(document.getElementById('freeTitle').value)||'MESÓN O FARO',subtitle:'',body:val(document.getElementById('freeBody').value),qr:val(document.getElementById('freeQr').value),code:'',type:'Libre'}));
    document.getElementById('previewImageTicket').addEventListener('click',()=>openTicketPreview({title:'MESÓN O FARO',subtitle:'IMAGEN',body:'',qr:'',code:'',type:'Imagen'}));
    try{
      const [qRes,tRes]=await Promise.all([post('qrList'),post('templateList')]);
      const qs=qRes.items||[];
      const ts=tRes.items||[];
      document.getElementById('qrTicketList').innerHTML=qs.length?qs.map((q,i)=>`<article class="card"><div class="card-title">${esc(q.name)}</div><div class="card-meta">${esc(q.ticketText||q.content)}</div><div class="actions"><button class="btn secondary" data-qr-ticket="${i}">PREVISUALIZAR</button></div></article>`).join(''):`<div class="empty">No hay QR activos.</div>`;
      document.querySelectorAll('[data-qr-ticket]').forEach(btn=>btn.addEventListener('click',()=>{
        const q=qs[Number(btn.dataset.qrTicket)];
        openTicketPreview({title:'MESÓN O FARO',subtitle:'',body:q.ticketText||q.name,qr:q.content,code:'',type:'QR'});
      }));
      document.getElementById('templateTicketList').innerHTML=ts.length?ts.map((t,i)=>`<article class="card"><div class="card-title">${esc(t.name)}</div><div class="card-meta">${esc(t.type)} · ${esc(t.text||'')}</div><div class="actions"><button class="btn secondary" data-template-ticket="${i}">PREVISUALIZAR</button></div></article>`).join(''):`<div class="empty">No hay plantillas activas.</div>`;
      document.querySelectorAll('[data-template-ticket]').forEach(btn=>btn.addEventListener('click',()=>{
        const t=ts[Number(btn.dataset.templateTicket)];
        const q=t.qr||{};
        openTicketPreview({title:t.title||'MESÓN O FARO',subtitle:'',body:t.text||'',qr:q.content||'',code:'',type:'Plantilla'});
      }));
    }catch(err){
      document.getElementById('qrTicketList').innerHTML=errorBox(err.message);
      document.getElementById('templateTicketList').innerHTML=errorBox(err.message);
    }
  }

  function openTicketPreview(ticket){
    state.currentTicket=ticket;
    state.ticketImageData='';
    state.ticketImagePosition='top';
    openModal(`${modalHeader('Ticket','Previsualización')}
      <div class="ticket-wrap"><div id="ticketPreview" class="ticket"></div></div>
      <div class="preview-controls card">
        <label class="field"><span>Imagen opcional</span><input id="ticketImage" type="file" accept="image/*"></label>
        <img id="ticketImageMini" class="image-preview-mini" alt="Imagen seleccionada">
        <label class="field"><span>Posición de la imagen</span><select id="ticketImagePosition"><option value="top">Arriba del ticket</option><option value="bottom">Debajo del ticket</option></select></label>
        <button id="clearTicketImage" class="btn soft" type="button">QUITAR IMAGEN</button>
      </div>
      <button id="printTicket" class="btn" type="button">IMPRIMIR / AIRPRINT / PDF</button>
      <div class="notice" style="margin-top:10px">La previsualización funciona sin impresora. iOS permite imprimir con AirPrint o guardar/compartir como PDF desde la hoja de impresión.</div>`);
    renderTicketPreview();
    document.getElementById('ticketImage').addEventListener('change',e=>{
      const file=e.target.files && e.target.files[0];
      if(!file) return;
      const reader=new FileReader();
      reader.onload=()=>{
        state.ticketImageData=String(reader.result||'');
        const mini=document.getElementById('ticketImageMini');
        mini.src=state.ticketImageData;
        mini.classList.add('has-image');
        renderTicketPreview();
      };
      reader.readAsDataURL(file);
    });
    document.getElementById('ticketImagePosition').addEventListener('change',e=>{
      state.ticketImagePosition=e.target.value;
      renderTicketPreview();
    });
    document.getElementById('clearTicketImage').addEventListener('click',()=>{
      state.ticketImageData='';
      document.getElementById('ticketImage').value='';
      document.getElementById('ticketImageMini').classList.remove('has-image');
      renderTicketPreview();
    });
    document.getElementById('printTicket').addEventListener('click',printCurrentTicket);
  }

  function ticketHtml(ticket,includeImage=true){
    const image = includeImage && state.ticketImageData ? `<img class="ticket-image" src="${esc(state.ticketImageData)}" alt="">` : '';
    const topImage = state.ticketImagePosition==='top' ? image : '';
    const bottomImage = state.ticketImagePosition==='bottom' ? image : '';
    const body=esc(ticket.body||'').replace(/\n/g,'<br>');
    return `${topImage}<h3>${esc(ticket.title||'MESÓN O FARO')}</h3>${ticket.subtitle?`<div class="ticket-sub">${esc(ticket.subtitle)}</div>`:''}<div class="ticket-rule"></div>${body?`<div class="ticket-body">${body}</div>`:''}${ticket.code?`<div class="ticket-code">${esc(ticket.code)}</div>`:''}${ticket.qr?`<div class="qr-box" data-qr="${esc(ticket.qr)}"></div>`:''}${bottomImage}<div class="ticket-rule"></div><div class="ticket-center">MESÓN O FARO</div>`;
  }

  function renderTicketPreview(){
    const el=document.getElementById('ticketPreview');
    if(!el||!state.currentTicket) return;
    el.innerHTML=ticketHtml(state.currentTicket,true);
    renderQrElements(el);
  }

  function renderQrElements(root){
    root.querySelectorAll('[data-qr]').forEach(node=>{
      if(typeof QRCode==='undefined'){
        node.textContent='[QR]';
        return;
      }
      node.innerHTML='';
      try{
        new QRCode(node,{text:node.dataset.qr,width:210,height:210,correctLevel:QRCode.CorrectLevel.M});
      }catch(_){
        node.textContent='[QR]';
      }
    });
  }

  function printCurrentTicket(){
    if(!state.currentTicket) return;
    let includeImage=false;
    if(state.ticketImageData){
      includeImage=confirm(`Has seleccionado una imagen para colocar ${state.ticketImagePosition==='top'?'ARRIBA':'DEBAJO'} del ticket.\n\n¿Quieres incluirla en esta impresión?`);
    }else if(!confirm('No has seleccionado ninguna imagen. ¿Quieres continuar e imprimir el ticket sin imagen?')){
      return;
    }
    printRoot.innerHTML=`<div class="ticket-wrap"><div class="ticket">${ticketHtml(state.currentTicket,includeImage)}</div></div>`;
    renderQrElements(printRoot);
    printRoot.setAttribute('aria-hidden','false');
    setTimeout(()=>{
      window.print();
      setTimeout(()=>{
        printRoot.innerHTML='';
        printRoot.setAttribute('aria-hidden','true');
      },800);
    },180);
  }

  async function renderHistory(){
    if(!requireAuth()) return;
    page('Historial', `<section class="hero"><div class="kicker">Actividad</div><h1>Historial</h1></section><div id="historyList">${loader('Cargando movimientos…')}</div>`);
    try{
      const res=await post('historyList',{limit:100});
      const items=res.items||[];
      document.getElementById('historyList').innerHTML=items.length?items.map(h=>`<article class="card"><div class="card-title">${esc(h.type||'Acción')} · ${esc(h.action||'')}</div><div class="card-meta">${esc(h.date||'')} · ${esc(h.reference||'')}</div>${h.detail?`<div class="card-note">${esc(h.detail)}</div>`:''}<div class="card-meta">${esc(h.terminal||'')} ${h.state?`· ${esc(h.state)}`:''}</div></article>`).join(''):`<div class="empty">No hay movimientos todavía.</div>`;
    }catch(err){
      document.getElementById('historyList').innerHTML=errorBox(err.message);
    }
  }

  function renderSettings(){
    if(!requireAuth()) return;
    page('Ajustes', `<section class="hero"><div class="kicker">Este dispositivo</div><h1>Ajustes</h1><p class="lead">La clave se almacena en el navegador de este iPhone y no forma parte del código público.</p></section>
      <section class="card"><label class="field"><span>Nombre del terminal</span><input id="settingsTerminal" value="${esc(state.terminal)}"></label><label class="field"><span>Clave app gestión</span><input id="settingsKey" type="password" value="${esc(state.key)}"></label><label class="field"><span>Endpoint</span><input value="${esc(API_URL)}" readonly></label><button id="saveSettings" class="btn">GUARDAR</button><button id="testSettings" class="btn secondary">PROBAR CONEXIÓN</button><button id="logoutSettings" class="btn danger">CERRAR SESIÓN EN ESTE IPHONE</button><div id="settingsStatus" style="margin-top:10px"></div></section>
      <section class="card"><h2>Instalar como app</h2><p class="lead">En Safari pulsa <strong>Compartir</strong> y después <strong>Añadir a pantalla de inicio</strong>. Se abrirá a pantalla completa como una aplicación.</p></section>
      <section class="card"><h2>Impresión en iPhone</h2><p class="lead">La webapp puede previsualizar todos los tickets y abrir AirPrint/PDF. La conexión ESC/POS directa al puerto 9100 sigue requiriendo la APK Android.</p></section>`);
    document.getElementById('saveSettings').addEventListener('click',()=>{
      state.terminal=val(document.getElementById('settingsTerminal').value)||'iPhone O Faro';
      state.key=val(document.getElementById('settingsKey').value);
      localStorage.setItem(STORAGE_TERMINAL,state.terminal);
      localStorage.setItem(STORAGE_KEY,state.key);
      toast('Ajustes guardados');
    });
    document.getElementById('testSettings').addEventListener('click',async()=>{
      const status=document.getElementById('settingsStatus');
      status.innerHTML=loader('Probando…');
      const key=val(document.getElementById('settingsKey').value);
      const terminal=val(document.getElementById('settingsTerminal').value)||'iPhone O Faro';
      try{
        const res=await testConnection(key,terminal);
        status.innerHTML=`<div class="notice success">${esc(res.message||'Conexión correcta')}</div>`;
      }catch(err){
        status.innerHTML=errorBox(err.message);
      }
    });
    document.getElementById('logoutSettings').addEventListener('click',()=>{
      if(!confirm('¿Eliminar la clave guardada de este iPhone?')) return;
      localStorage.removeItem(STORAGE_KEY);
      state.key='';
      bottomNav.hidden=true;
      renderLogin();
    });
  }

  bottomNav.addEventListener('click',event=>{
    const btn=event.target.closest('[data-route]');
    if(btn) setRoute(btn.dataset.route);
  });
  homeButton.addEventListener('click',()=>setRoute('home'));
  modal.addEventListener('click',event=>{
    if(event.target.closest('[data-close-modal]')) closeModal();
  });
  document.addEventListener('keydown',event=>{
    if(event.key==='Escape' && !modal.hidden) closeModal();
  });

  if('serviceWorker' in navigator){
    window.addEventListener('load',()=>navigator.serviceWorker.register('./sw.js').catch(()=>{}));
  }

  if(state.key){
    bottomNav.hidden=false;
    testConnection().then(()=>setRoute('home')).catch(()=>renderLogin());
  }else{
    renderLogin();
  }
})();
