import { DASHBOARD_HTML, DASHBOARD_JS } from './dashboard.js';
import { CONSENT_JS } from './consent.js';

const PREFIX = '/__ofaro';
const DAY = 86400000;
const DEFAULTS = {security_days:7, tracking_days:180};
const BROWSER_PAGES = new Set(['/','/index.html','/carta.html','/menu-dia.html','/aviso-legal.html','/privacidad.html','/cookies.html','/accesibilidad.html','/bases-promociones.html']);
const text = (value, max=200) => String(value == null ? '' : value).slice(0,max);
const json = (data,status=200) => new Response(JSON.stringify(data),{status,headers:{'content-type':'application/json; charset=utf-8','cache-control':'no-store','x-content-type-options':'nosniff'}});
const safeError = (msg,status=400) => json({ok:false,error:msg},status);
const cleanPath = path => (/^\/[a-z0-9/_\-.]{0,130}$/i.test(path) ? path : '/');
const isoDate = value => /^\d{4}-\d{2}-\d{2}$/.test(value||'') ? value : '';
const today = () => new Date().toISOString().slice(0,10);
const now = () => new Date().toISOString();
const dateOffset = days => new Date(Date.now()+days*DAY).toISOString();
function clientIp(request){
  // This Worker must only run on the Cloudflare edge, not behind a user-controlled proxy.
  const value = request.headers.get('CF-Connecting-IP') || '';
  return value.length <= 45 && /^[0-9a-fA-F:.]+$/.test(value) ? value : '';
}
function uaParts(raw){
  const ua=text(raw,512);
  const device=/mobile|iphone|android/i.test(ua)?'Móvil':/ipad|tablet/i.test(ua)?'Tableta':'Escritorio';
  const browser=/Edg\//.test(ua)?'Edge':/Firefox\//.test(ua)?'Firefox':/Chrome\//.test(ua)?'Chrome':/Safari\//.test(ua)?'Safari':'Otro';
  const os=/iPhone|iPad|Mac OS X/i.test(ua)?'Apple':/Android/i.test(ua)?'Android':/Windows/i.test(ua)?'Windows':/Linux/i.test(ua)?'Linux':'Otro';
  return {device,browser,os};
}
function trafficSource(value,origin){
  try{
    if(!value) return 'Directo/desconocido';
    const u=new URL(value);
    if(u.origin===origin) return 'Interno';
    if(/(^|\.)google\.|(^|\.)bing\.|(^|\.)duckduckgo\./i.test(u.hostname)) return 'Buscadores';
    if(/(^|\.)instagram\.|(^|\.)facebook\.|(^|\.)tiktok\.|(^|\.)whatsapp\./i.test(u.hostname)) return 'Redes sociales';
    return 'Otras referencias';
  }catch(_){return 'Directo/desconocido';}
}
async function signIp(ip,env){
  if(!ip || !env.LOG_HMAC_SECRET) return '';
  const key=await crypto.subtle.importKey('raw',new TextEncoder().encode(env.LOG_HMAC_SECRET),{name:'HMAC',hash:'SHA-256'},false,['sign']);
  const bytes=await crypto.subtle.sign('HMAC',key,new TextEncoder().encode(today()+'|'+ip));
  return Array.from(new Uint8Array(bytes)).map(b=>b.toString(16).padStart(2,'0')).join('');
}
async function setting(env,key){
  const row=await env.DB.prepare('SELECT value FROM settings WHERE key = ?').bind(key).first();
  const num=Number(row && row.value);
  const fallback=DEFAULTS[key] || 7;
  return Number.isInteger(num) ? Math.max(1,Math.min(key==='security_days'?30:365,num)) : fallback;
}
async function incident(env,req,kind,severity,status,message){
  if(!env.DB) return;
  const ip=clientIp(req),info=uaParts(req.headers.get('User-Agent')||'');
  const p=new URL(req.url).pathname;
  const cf=req.cf || {};
  await env.DB.prepare('INSERT INTO security_events (at,ip,path,status,kind,severity,country,city,browser,os,device,details) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)')
    .bind(now(),ip,cleanPath(p),status,kind,severity,text(cf.country||'',2),text(cf.city||'',64),info.browser,info.os,info.device,text(message,180)).run();
  if(env.ALERT_WEBHOOK_URL && severity==='alta'){
    try{await fetch(env.ALERT_WEBHOOK_URL,{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({app:'Mesón O Faro',kind,severity,path:cleanPath(p),at:now(),country:cf.country||'',notice:'Se ha omitido la IP en la alerta externa'})});}catch(_){}
  }
}
async function isBlocked(env,req){
  const ip=clientIp(req);
  if(!env.DB || !ip) return false;
  const row=await env.DB.prepare('SELECT action FROM ip_rules WHERE ip=? AND (expires_at IS NULL OR expires_at>?)').bind(ip,now()).first();
  return row && row.action==='block';
}
async function rateCheck(env,req,limit){
  const ip=clientIp(req);
  if(!env.DB || !ip || !env.LOG_HMAC_SECRET) return false;
  const minute=now().slice(0,16),fingerprint=await signIp(ip,env);
  const key=minute+'|'+fingerprint;
  await env.DB.prepare('INSERT INTO request_counters (k,count,at) VALUES (?,1,?) ON CONFLICT(k) DO UPDATE SET count=count+1').bind(key,now()).run();
  const row=await env.DB.prepare('SELECT count FROM request_counters WHERE k=?').bind(key).first();
  return Number(row && row.count)>limit;
}
function jwtDecode(part){
  const input=part.replace(/-/g,'+').replace(/_/g,'/');
  return Uint8Array.from(atob(input.padEnd(Math.ceil(input.length/4)*4,'=')),c=>c.charCodeAt(0));
}
let keyCache=null, keyCacheAt=0;
async function adminIdentity(req,env){
  const team=text(env.ACCESS_TEAM_DOMAIN||'',200).replace(/^https?:\/\//,'').replace(/\/$/,'');
  if(!team || !env.ACCESS_AUD || !env.ADMIN_EMAILS) return null;
  let token=req.headers.get('Cf-Access-Jwt-Assertion')||'';
  if(!token){const match=(req.headers.get('Cookie')||'').match(/(?:^|;\s*)CF_Authorization=([^;]+)/);token=match?decodeURIComponent(match[1]):'';}
  if(!token || token.length>8000) return null;
  try{
    const parts=token.split('.');
    if(parts.length!==3) return null;
    const header=JSON.parse(new TextDecoder().decode(jwtDecode(parts[0])));
    const data=JSON.parse(new TextDecoder().decode(jwtDecode(parts[1])));
    if(header.alg!=='RS256'||!header.kid) return null;
    const stamp=Math.floor(Date.now()/1000);
    const issuer='https://'+team;
    const audiences=Array.isArray(data.aud)?data.aud:[data.aud];
    const permitted=env.ADMIN_EMAILS.split(',').map(v=>v.trim().toLowerCase()).filter(Boolean);
    if(data.iss!==issuer || !audiences.includes(env.ACCESS_AUD) || !data.exp || data.exp<=stamp || (data.nbf&&data.nbf>stamp) || !permitted.includes(String(data.email||'').toLowerCase())) return null;
    if(!keyCache || Date.now()-keyCacheAt>3600000){
      const response=await fetch(issuer+'/cdn-cgi/access/certs');
      if(!response.ok) return null;
      const result=await response.json();
      keyCache=result.keys||[];
      keyCacheAt=Date.now();
    }
    const jwk=keyCache.find(k=>k.kid===header.kid);
    if(!jwk) return null;
    const key=await crypto.subtle.importKey('jwk',jwk,{name:'RSASSA-PKCS1-v1_5',hash:'SHA-256'},false,['verify']);
    const signed=parts[0]+'.'+parts[1];
    const verified=await crypto.subtle.verify('RSASSA-PKCS1-v1_5',key,jwtDecode(parts[2]),new TextEncoder().encode(signed));
    return verified ? {email:data.email} : null;
  }catch(_){return null;}
}
async function readBody(req){
  if(req.method!=='POST') throw new Error('Método no permitido');
  if(Number(req.headers.get('content-length')||0)>6000) throw new Error('Solicitud demasiado grande');
  const txt=await req.text();
  if(txt.length>6000) throw new Error('Solicitud demasiado grande');
  return JSON.parse(txt);
}
function validUuid(value){return /^[a-f0-9-]{36}$/i.test(String(value||''));}
async function consentEndpoint(req,env){
  if(await rateCheck(env,req,30)) return safeError('Demasiadas solicitudes',429);
  let b;try{b=await readBody(req);}catch(_){return safeError('Solicitud incorrecta');}
  if(!validUuid(b.cid) || !['accept','withdraw'].includes(b.choice)) return safeError('Consentimiento incorrecto');
  if(b.choice==='withdraw'){
    await env.DB.batch([
      env.DB.prepare('DELETE FROM visit_events WHERE cid=?').bind(b.cid),
      env.DB.prepare('DELETE FROM consents WHERE cid=?').bind(b.cid)
    ]);
    return json({ok:true,withdrawn:true});
  }
  await env.DB.prepare('INSERT INTO consents (cid,policy_version,accepted_at,expires_at) VALUES (?,?,?,?) ON CONFLICT(cid) DO UPDATE SET policy_version=excluded.policy_version,accepted_at=excluded.accepted_at,expires_at=excluded.expires_at').bind(b.cid,'2026-10',now(),dateOffset(180)).run();
  return json({ok:true});
}
async function eventEndpoint(req,env){
  if(await rateCheck(env,req,120)) return safeError('Demasiadas solicitudes',429);
  let b;try{b=await readBody(req);}catch(_){return safeError('Datos incorrectos');}
  if(!validUuid(b.cid)||!validUuid(b.sid)|| !['page','click','leave'].includes(b.event)) return safeError('Evento incorrecto');
  const consent=await env.DB.prepare('SELECT cid FROM consents WHERE cid=? AND expires_at>?').bind(b.cid,now()).first();
  if(!consent) return safeError('Sin consentimiento válido',403);
  const path=cleanPath(text(b.page,130)),info=uaParts(req.headers.get('User-Agent'));
  const country=text(req.cf?.country||'',2);
  const duration=b.event==='leave' ? Math.max(0,Math.min(1800,Number(b.seconds)||0)) : 0;
  const source=trafficSource(text(b.referrer,240),new URL(req.url).origin);
  await env.DB.prepare('INSERT INTO visit_events (at,cid,sid,page,event,element,seconds,country,device,browser,os,source) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)')
    .bind(now(),b.cid,b.sid,path,b.event,text(b.element||'',45),duration,country,info.device,info.browser,info.os,source).run();
  return json({ok:true});
}
async function pageCounter(req,res,env){
  if(!env.DB || req.method!=='GET') return;
  const url=new URL(req.url),path=cleanPath(url.pathname);
  const html=res.headers.get('content-type')||'';
  if(!html.includes('text/html') || !BROWSER_PAGES.has(path) || res.status!==200) return;
  const country=text(req.cf?.country||'XX',2);
  await env.DB.prepare('INSERT INTO page_hits (day,page,country,hits) VALUES (?,?,?,1) ON CONFLICT(day,page,country) DO UPDATE SET hits=hits+1')
    .bind(today(),path,country).run();
}
function dateRange(url){
  const end=isoDate(url.searchParams.get('to'))||today();
  const start=isoDate(url.searchParams.get('from'))||dateOffset(-29).slice(0,10);
  if(start>end || ((Date.parse(end)-Date.parse(start))/DAY)>366) throw new Error('Rango de fechas no válido (máximo 366 días)');
  return [start,end];
}
async function adminApi(req,env,path){
  const url=new URL(req.url);
  let start,end;try{[start,end]=dateRange(url);}catch(e){return safeError(e.message);}
  if(req.method==='GET'){
    if(path==='summary'){
      const views=await env.DB.prepare('SELECT COALESCE(SUM(hits),0) total FROM page_hits WHERE day BETWEEN ? AND ?').bind(start,end).first();
      const uniques=await env.DB.prepare("SELECT COUNT(DISTINCT cid) n,COUNT(DISTINCT sid) sessions FROM visit_events WHERE event='page' AND substr(at,1,10) BETWEEN ? AND ?").bind(start,end).first();
      const recent=await env.DB.prepare('SELECT at,kind,severity,path,status,country FROM security_events WHERE substr(at,1,10) BETWEEN ? AND ? ORDER BY at DESC LIMIT 15').bind(start,end).all();
      return json({ok:true,period:{from:start,to:end},hits:views.total,consenting_uniques:uniques.n,consenting_sessions:uniques.sessions,incidents:recent.results});
    }
    if(path==='traffic'){
      const daily=await env.DB.prepare('SELECT day,SUM(hits) hits FROM page_hits WHERE day BETWEEN ? AND ? GROUP BY day ORDER BY day').bind(start,end).all();
      const pages=await env.DB.prepare('SELECT page,SUM(hits) hits FROM page_hits WHERE day BETWEEN ? AND ? GROUP BY page ORDER BY hits DESC LIMIT 30').bind(start,end).all();
      const countries=await env.DB.prepare('SELECT country,SUM(hits) hits FROM page_hits WHERE day BETWEEN ? AND ? GROUP BY country ORDER BY hits DESC LIMIT 30').bind(start,end).all();
      const devices=await env.DB.prepare("SELECT device,browser,os,source,COUNT(*) n FROM visit_events WHERE event='page' AND substr(at,1,10) BETWEEN ? AND ? GROUP BY device,browser,os,source ORDER BY n DESC LIMIT 50").bind(start,end).all();
      return json({ok:true,daily:daily.results,pages:pages.results,countries:countries.results,devices:devices.results});
    }
    if(path==='visits'){
      const data=await env.DB.prepare('SELECT at,substr(cid,1,8) pseudonym,substr(sid,1,8) session,page,event,element,seconds,country,device,browser,os,source FROM visit_events WHERE substr(at,1,10) BETWEEN ? AND ? ORDER BY at DESC LIMIT 300').bind(start,end).all();
      return json({ok:true,rows:data.results,notice:'Solo visitantes con consentimiento. Identificadores truncados.'});
    }
    if(path==='security'){
      const events=await env.DB.prepare('SELECT id,at,ip,path,status,kind,severity,country,city,browser,os,device,details FROM security_events WHERE substr(at,1,10) BETWEEN ? AND ? ORDER BY at DESC LIMIT 300').bind(start,end).all();
      const rules=await env.DB.prepare('SELECT ip,action,reason,created_at,expires_at FROM ip_rules ORDER BY created_at DESC LIMIT 200').all();
      return json({ok:true,events:events.results,rules:rules.results});
    }
    if(path==='settings'){
      return json({ok:true,security_days:await setting(env,'security_days'),tracking_days:await setting(env,'tracking_days'),webhook_active:!!env.ALERT_WEBHOOK_URL});
    }
    return safeError('No existe ese módulo',404);
  }
  let body;try{body=await readBody(req);}catch(_){return safeError('Solicitud inválida');}
  if(path==='rules'){
    const ip=text(body.ip,45);
    if(!ip || !/^[0-9a-fA-F:.]+$/.test(ip)) return safeError('Dirección IP no válida');
    if(body.action==='remove'){
      await env.DB.prepare('DELETE FROM ip_rules WHERE ip=?').bind(ip).run();
      return json({ok:true});
    }
    if(!['allow','block'].includes(body.action)) return safeError('Acción incorrecta');
    const minutes=Math.max(5,Math.min(43200,Math.floor(Number(body.minutes)||60)));
    await env.DB.prepare('INSERT INTO ip_rules (ip,action,reason,created_at,expires_at) VALUES (?,?,?,?,?) ON CONFLICT(ip) DO UPDATE SET action=excluded.action,reason=excluded.reason,created_at=excluded.created_at,expires_at=excluded.expires_at')
      .bind(ip,body.action,text(body.reason||'Regla manual',120),now(),dateOffset(minutes/1440)).run();
    return json({ok:true});
  }
  if(path==='settings'){
    const a=Number(body.security_days),b=Number(body.tracking_days);
    if(!Number.isInteger(a)||a<1||a>30||!Number.isInteger(b)||b<1||b>365) return safeError('Conservación fuera de límites');
    await env.DB.batch([
      env.DB.prepare("INSERT INTO settings(key,value) VALUES ('security_days',?) ON CONFLICT(key) DO UPDATE SET value=excluded.value").bind(String(a)),
      env.DB.prepare("INSERT INTO settings(key,value) VALUES ('tracking_days',?) ON CONFLICT(key) DO UPDATE SET value=excluded.value").bind(String(b))
    ]);
    return json({ok:true});
  }
  if(path==='purge'){
    await purge(env);
    return json({ok:true,finished:now()});
  }
  return safeError('Acción no admitida',404);
}
async function purge(env){
  if(!env.DB) return;
  const security=dateOffset(-(await setting(env,'security_days'))),tracking=dateOffset(-(await setting(env,'tracking_days')));
  await env.DB.batch([
    env.DB.prepare('DELETE FROM security_events WHERE at < ?').bind(security),
    env.DB.prepare('DELETE FROM visit_events WHERE at < ?').bind(tracking),
    env.DB.prepare('DELETE FROM consents WHERE expires_at < ?').bind(now()),
    env.DB.prepare('DELETE FROM request_counters WHERE at < ?').bind(dateOffset(-2)),
    env.DB.prepare('DELETE FROM ip_rules WHERE expires_at IS NOT NULL AND expires_at < ?').bind(now())
  ]);
}
function protectHeaders(response,isHtml=false){
  const h=new Headers(response.headers);
  h.set('X-Content-Type-Options','nosniff');
  h.set('Referrer-Policy','strict-origin-when-cross-origin');
  h.set('Permissions-Policy','geolocation=(),microphone=(),camera=()');
  if(isHtml){h.set('Content-Security-Policy',"default-src 'none'; script-src 'self'; style-src 'unsafe-inline'; connect-src 'self'; img-src 'self' data:; base-uri 'none'; frame-ancestors 'none'; form-action 'self'");h.set('Cache-Control','no-store');}
  return new Response(response.body,{status:response.status,statusText:response.statusText,headers:h});
}
async function publicPage(req,env,ctx){
  const response=await env.ASSETS.fetch(req);
  ctx.waitUntil(pageCounter(req,response,env).catch(()=>{}));
  if(!response.headers.get('content-type')?.includes('text/html') || response.status!==200) return response;
  const body=await response.text();
  // Defer third-party Google Maps until an explicit user click.
  const mapSafe=body.replace(/src="(https:\/\/www\.google\.com\/maps[^"]*)"/g,'data-ofaro-map-src="$1"');
  const modified=mapSafe.replace(/<\/body>/i,'<script defer src="/__ofaro/client.js"></script></body>');
  const headers=new Headers(response.headers);
  headers.delete('Content-Length');headers.delete('Content-Encoding');headers.set('Content-Type','text/html; charset=utf-8');
  return new Response(modified,{status:200,headers});
}
export default {
  async fetch(req,env,ctx){
    const u=new URL(req.url),path=u.pathname;
    if(!env.ASSETS || !env.DB) return safeError('Infraestructura no configurada',503);
    if(path===PREFIX+'/client.js') return new Response(CONSENT_JS,{headers:{'content-type':'application/javascript; charset=utf-8','cache-control':'public, max-age=3600','x-content-type-options':'nosniff'}});
    if(path===PREFIX+'/api/consent'||path===PREFIX+'/api/event'){
      if(req.method!=='POST') return safeError('Método no admitido',405);
      return path.endsWith('/consent')?consentEndpoint(req,env):eventEndpoint(req,env);
    }
    if(path.startsWith(PREFIX+'/admin')||path.startsWith(PREFIX+'/api/admin/')){
      if(await isBlocked(env,req)) {ctx.waitUntil(incident(env,req,'ip_blocked','media',403,'Acceso bloqueado'));return safeError('Acceso denegado',403);}
      const identity=await adminIdentity(req,env);
      if(!identity){ctx.waitUntil(incident(env,req,'auth_denied','media',403,'Credenciales Access incorrectas o ausentes'));return safeError('Acceso privado. Autenticación obligatoria.',403);}
      if(path===PREFIX+'/admin' && req.method==='GET') return protectHeaders(new Response(DASHBOARD_HTML,{headers:{'content-type':'text/html; charset=utf-8'}}),true);
      if(path===PREFIX+'/admin.js' && req.method==='GET') return new Response(DASHBOARD_JS,{headers:{'content-type':'application/javascript; charset=utf-8','cache-control':'no-store','x-content-type-options':'nosniff'}});
      if(path.startsWith(PREFIX+'/api/admin/')) return adminApi(req,env,path.slice((PREFIX+'/api/admin/').length));
      return safeError('Recurso no encontrado',404);
    }
    if(path.startsWith(PREFIX)) return safeError('Recurso no encontrado',404);
    if(await isBlocked(env,req)){ctx.waitUntil(incident(env,req,'ip_blocked','media',403,'Bloqueo manual'));return new Response('Acceso temporalmente restringido',{status:403});}
    if(/\/(?:\.env|\.git|wp-admin|phpmyadmin|\.well-known\/security\.txt|config\.php)/i.test(path)){
      ctx.waitUntil(incident(env,req,'suspicious_path','media',404,'Ruta potencialmente automatizada'));
      return new Response('Not Found',{status:404});
    }
    return publicPage(req,env,ctx);
  },
  async scheduled(event,env,ctx){ctx.waitUntil(purge(env));}
};
