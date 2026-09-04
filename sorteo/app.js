(() => {
'use strict';
const API='https://script.google.com/macros/s/AKfycbxPSc2Ep3IkZ7lDV1tkm3dnBWgMV0QVrRfx50WJJJXN-q1xELZSWrfdJt3lTxaAXG2miA/exec';
const app=document.getElementById('app');
const token=(new URLSearchParams(location.search).get('t')||'').trim();
let data=null,result=null,playing=false,playPromise=null;
const esc=v=>String(v??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));

async function post(action,payload={}){
  const c=new AbortController(),tm=setTimeout(()=>c.abort(),12000);
  try{
    const r=await fetch(API,{method:'POST',cache:'no-store',redirect:'follow',signal:c.signal,body:JSON.stringify({action,...payload})});
    const t=await r.text();let j;
    try{j=JSON.parse(t);}catch(_){throw new Error('No se pudo conectar con la promoción.');}
    if(!r.ok||j?.ok===false)throw new Error(j?.error||'Promoción no disponible');
    return j;
  }finally{clearTimeout(tm);}
}

function error(msg){app.innerHTML=`<div class="error"><div class="box"><div class="eyebrow">O FARO</div><h1>No podemos abrir esta promoción</h1><p>${esc(msg)}</p></div></div>`;}

async function init(){
  if(!token)return error('El QR no contiene una invitación válida.');
  try{
    data=await post('promoPublicGet',{token});
    if(data.used&&data.result){result=data.result;renderResult();}
    else renderGame();
  }catch(e){error(e.message);}
}

function shell(inner){
  const c=data?.campaign||{};
  return `<div class="shell"><div class="brand">O FARO</div><section class="hero"><div class="eyebrow">${esc(c.type||'Promoción')}</div><h1>${esc(c.name||'Sorteo')}</h1><p>${esc(c.description||'Descubre si hoy tienes premio.')}</p></section><section class="game-card">${inner}</section></div>`;
}

function renderGame(){
  const c=data.campaign;let inner='';
  if(c.type==='Ruleta')inner=wheelHtml(c);else if(c.type==='Rasca')inner=scratchHtml(c);else inner=revealHtml(c);
  app.innerHTML=shell(inner);
  if(c.type==='Ruleta')bindWheel(c);else if(c.type==='Rasca')bindScratch(c);else bindReveal(c);
}

function polar(cx,cy,r,deg){const a=(deg-90)*Math.PI/180;return {x:cx+r*Math.cos(a),y:cy+r*Math.sin(a)};}
function arcPath(cx,cy,r,a0,a1){const p0=polar(cx,cy,r,a1),p1=polar(cx,cy,r,a0),large=a1-a0>180?1:0;return `M ${cx} ${cy} L ${p0.x} ${p0.y} A ${r} ${r} 0 ${large} 0 ${p1.x} ${p1.y} Z`;}

function wheelSvg(c){
  const segs=c.segments||[];let total=segs.reduce((n,s)=>n+(Number(s.percentage)||0),0)||segs.length||1;let a=0;
  const colors=['#f2b705','#111111','#c84b31','#1c6c5c','#6b4b8a','#e6d8bb','#2f5fa8','#d56d96'];let out='';
  segs.forEach((s,i)=>{
    const pct=(Number(s.percentage)||0)/total,span=pct*360,a0=a,a1=a+span,mid=(a0+a1)/2;a=a1;
    const fill=colors[i%colors.length],dark=['#111111','#c84b31','#1c6c5c','#6b4b8a','#2f5fa8'].includes(fill),rad=span<26?123:span<55?112:102,p=polar(160,160,rad,mid);
    let rot=mid;if(rot>90&&rot<270)rot+=180;
    const label=String(s.label||'').toUpperCase(),words=label.split(/\s+/),line1=words.slice(0,Math.ceil(words.length/2)).join(' '),line2=words.slice(Math.ceil(words.length/2)).join(' '),fs=span<22?8:span<38?10:12;
    out+=`<path d="${arcPath(160,160,150,a0,a1)}" fill="${fill}" stroke="#fff" stroke-width="2"/><g transform="translate(${p.x} ${p.y}) rotate(${rot})"><text class="wheel-label" fill="${dark?'#fff':'#111'}" font-size="${fs}"><tspan x="0" y="${line2?-4:0}">${esc(line1)}</tspan>${line2?`<tspan x="0" y="9">${esc(line2)}</tspan>`:''}</text></g>`;
  });
  return `<svg viewBox="0 0 320 320" class="wheel-spin" id="wheelSpin">${out}<circle cx="160" cy="160" r="150" fill="none" stroke="#111" stroke-width="7"/></svg>`;
}

function wheelHtml(c){return `<div class="wheel-stage"><div class="wheel-pointer"></div>${wheelSvg(c)}<div class="wheel-ring"></div><div class="wheel-center">O FARO</div></div><button class="primary" id="playBtn">GIRAR RULETA</button><p class="hint">El resultado se calcula de forma segura al girar.</p>`;}

function bindWheel(c){
  const btn=document.getElementById('playBtn');
  btn.onclick=async()=>{
    if(playing||result)return;playing=true;btn.disabled=true;btn.textContent='GIRANDO…';
    try{
      const r=await playOnce();result=r.result;
      const segs=c.segments||[];let total=segs.reduce((n,s)=>n+(Number(s.percentage)||0),0)||100,before=0,target=segs.findIndex(s=>Number(s.order)===Number(result.segmentOrder));
      if(target<0)target=Math.max(0,Number(result.wheelTargetIndex)||0);for(let i=0;i<target;i++)before+=(Number(segs[i]?.percentage)||0)/total*360;
      const span=(Number(segs[target]?.percentage)||0)/total*360,center=before+span/2,deg=360*7-center,wheel=document.getElementById('wheelSpin');
      if(wheel)wheel.style.transform=`rotate(${deg}deg)`;
      setTimeout(renderResult,5400);
    }catch(e){playing=false;btn.disabled=false;btn.textContent='GIRAR RULETA';alert(e.message);}
  };
}

function scratchHtml(c){return `<div class="scratch-wrap"><div class="scratch-under" id="scratchUnder"><small>TU RESULTADO</small><strong id="scratchResult">TOCA PARA EMPEZAR</strong></div><canvas id="scratchCanvas" class="scratch-canvas"></canvas></div><p class="scratch-help">Toca la zona gris. Cuando el resultado esté listo, rasca para descubrirlo.</p><div class="prize-cloud">${(c.possiblePrizes||[]).slice(0,6).map(p=>`<span>${esc(p.name)}</span>`).join('')}</div>`;}

function bindScratch(){
  const canvas=document.getElementById('scratchCanvas'),wrap=canvas?.parentElement;
  if(!canvas||!wrap)return;
  let ctx,down=false,moves=0,last=null,finished=false;

  function setStatus(text){const el=document.getElementById('scratchResult');if(el)el.textContent=text;}
  function applyResult(){
    if(!result)return false;
    setStatus(result.won?(result.prize||'PREMIO'):'SIN PREMIO');
    const under=document.getElementById('scratchUnder');
    if(under&&result.won)under.style.background='radial-gradient(circle at 50% 25%,#245c49 0,#10261f 70%)';
    return true;
  }
  function size(){
    if(!canvas.isConnected||finished)return;
    const r=wrap.getBoundingClientRect(),d=Math.min(2,devicePixelRatio||1);canvas.width=Math.round(r.width*d);canvas.height=Math.round(r.height*d);ctx=canvas.getContext('2d');ctx.scale(d,d);ctx.fillStyle='#b9b9b5';ctx.fillRect(0,0,r.width,r.height);ctx.fillStyle='#888';ctx.font='900 24px -apple-system,sans-serif';ctx.textAlign='center';ctx.fillText(result?'RASCA AQUÍ':'TOCA PARA JUGAR',r.width/2,r.height/2+8);ctx.globalCompositeOperation='destination-out';
  }
  size();window.addEventListener('resize',size,{once:true});

  async function start(e){
    e.preventDefault();
    if(finished||playing)return;
    if(!result){
      playing=true;setStatus('COMPROBANDO…');
      try{
        const r=await playOnce();result=r.result;
        if(!canvas.isConnected)return;
        applyResult();size();
      }catch(err){setStatus('TOCA PARA REINTENTAR');alert(err.message);}
      finally{playing=false;}
      return;
    }
    down=true;erase(e);
  }

  function pos(e){const r=canvas.getBoundingClientRect(),p=e.touches?.[0]||e;return{x:p.clientX-r.left,y:p.clientY-r.top};}
  function erase(e){
    if(!down||!ctx||finished||!result)return;
    const p=pos(e);ctx.beginPath();if(last){ctx.moveTo(last.x,last.y);ctx.lineTo(p.x,p.y);ctx.lineWidth=34;ctx.lineCap='round';ctx.stroke();}ctx.arc(p.x,p.y,18,0,Math.PI*2);ctx.fill();last=p;moves++;
    if(moves>42){finished=true;down=false;canvas.style.pointerEvents='none';canvas.style.opacity='0';setTimeout(()=>{if(canvas.isConnected)canvas.remove();setTimeout(renderResult,650);},420);}
  }
  function end(){down=false;last=null;}
  canvas.addEventListener('pointerdown',start);canvas.addEventListener('pointermove',e=>{if(down)erase(e)});window.addEventListener('pointerup',end);
}

function revealHtml(c){const icon=c.type==='Código premiado'?'#':'?';return `<div class="reveal-card"><div class="reveal-icon">${icon}</div><h2>${c.type==='Código premiado'?'¿Código premiado?':'¿Qué te habrá tocado?'}</h2><div class="prize-cloud">${(c.possiblePrizes||[]).slice(0,6).map(p=>`<span>${esc(p.name)}</span>`).join('')}</div></div><button class="primary" id="revealBtn" style="margin-top:18px">DESCUBRIR</button>`;}

function bindReveal(){
  const btn=document.getElementById('revealBtn');
  btn.onclick=async()=>{
    if(playing||result)return;playing=true;btn.disabled=true;btn.textContent='ABRIENDO…';
    try{const r=await playOnce();result=r.result;setTimeout(renderResult,500);}catch(err){playing=false;btn.disabled=false;btn.textContent='DESCUBRIR';alert(err.message);}
  };
}

async function playOnce(){
  if(result)return {ok:true,result};
  if(playPromise)return playPromise;
  playPromise=(async()=>{
    try{
      const r=await post('promoPublicPlay',{token,ua:navigator.userAgent.slice(0,220)});
      if(!r?.result)throw new Error('El servidor no devolvió el resultado de la promoción.');
      result=r.result;return r;
    }catch(firstError){
      try{
        const latest=await post('promoPublicGet',{token});data=latest;
        if(latest?.used&&latest?.result){result=latest.result;return {ok:true,result,recovered:true};}
      }catch(_){/* conserva el error original */}
      throw firstError;
    }finally{playPromise=null;}
  })();
  return playPromise;
}

function renderResult(){
  if(!result)return;
  playing=false;
  if(result.won)confetti();
  app.innerHTML=shell(`<div class="result ${result.won?'win':'lose'}"><span class="result-badge">${result.won?'PREMIO':'RESULTADO'}</span><h2>${esc(result.won?result.prize:'Esta vez no')}</h2><p>${esc(result.message||'')}</p>${result.won?`<div class="result-code">${esc(result.code)}</div><p class="result-note">Enseña este código al personal para canjear tu premio.</p>`:''}</div>`);
}

function confetti(){const colors=['#f2b705','#111','#c84b31','#1c6c5c','#6b4b8a'];for(let i=0;i<42;i++){const e=document.createElement('i');e.className='confetti';e.style.left=Math.random()*100+'vw';e.style.background=colors[i%colors.length];e.style.setProperty('--x',(Math.random()*180-90)+'px');e.style.animationDelay=Math.random()*.8+'s';document.body.appendChild(e);setTimeout(()=>e.remove(),3800);}}

init();
})();
