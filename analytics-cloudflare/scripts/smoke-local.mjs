import {spawn} from 'node:child_process';
import assert from 'node:assert/strict';
const child=spawn(process.execPath,['node_modules/wrangler/bin/wrangler.js','dev','--local','--port','8787','--ip','127.0.0.1'],{env:process.env,stdio:['ignore','pipe','pipe']});
let output='';const ready=new Promise((resolve,reject)=>{const timeout=setTimeout(()=>reject(Error('No local runtime: '+output.slice(-1500))),30000);child.stdout.on('data',d=>{output+=d;if(output.includes('Ready on')){clearTimeout(timeout);resolve();}});child.stderr.on('data',d=>output+=d);child.on('exit',code=>{clearTimeout(timeout);reject(Error('Runtime exited '+code+': '+output));});});
try{
 await ready;
 const base='http://127.0.0.1:8787';
 for(const path of ['/','/carta.html','/menu-dia.html','/gestion/','/sorteo/']){const r=await fetch(base+path,{redirect:'manual'});assert.equal(r.status,200,path);assert.match(await r.text(),/__ofaro\/client.js/);}
 for(const path of ['/admin','/admin/','/admin.html','/%61dmin.html','/__ofaro/admin','/__ofaro/admin.js','/__ofaro/api/admin/summary'])assert.equal((await fetch(base+path,{redirect:'manual'})).status,403,path);
 for(const path of ['/analytics-cloudflare/src/worker.js','/apps-script.gs','/public/admin.html','/public/index.html'])assert.equal((await fetch(base+path,{redirect:'manual'})).status,404,path);
 const cid=crypto.randomUUID(),sid=crypto.randomUUID();const post=(path,body)=>fetch(base+path,{method:'POST',headers:{origin:base,'content-type':'application/json'},body:JSON.stringify(body)});
 assert.equal((await post('/__ofaro/api/event',{cid,sid,event:'page',page:'/'})).status,403);
 assert.equal((await post('/__ofaro/api/consent',{cid,choice:'accept'})).status,200);
 assert.equal((await post('/__ofaro/api/event',{cid,sid,event:'page',page:'/'})).status,200);
 assert.equal((await post('/__ofaro/api/consent',{cid,choice:'withdraw'})).status,200);
 assert.equal((await post('/__ofaro/api/event',{cid,sid,event:'page',page:'/'})).status,403);
 console.log('Worker real local: 5 páginas, 7 rutas privadas, 4 rutas internas y ciclo consentimiento/evento/retirada verificados.');
}finally{child.kill('SIGTERM');}
