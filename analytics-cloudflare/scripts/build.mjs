import {readdir,stat,copyFile,mkdir,rm} from 'node:fs/promises';
import {resolve,join,relative,extname,dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
const project=resolve(dirname(fileURLToPath(import.meta.url)),'..');
const root=resolve(project,'../..');
const out=join(project,'dist');
const allowed=new Set(['.html','.js','.css','.svg','.png','.jpg','.jpeg','.webp','.gif','.ico','.webmanifest','.json','.txt','.woff','.woff2']);
const excluded=new Set(['.git','.github','analytics-cloudflare','node_modules','admin.html','README.md','CNAME']);
let copied=0;
await rm(out,{recursive:true,force:true});
await mkdir(out,{recursive:true});
async function visit(dir){
  for(const name of await readdir(dir)){
    if(excluded.has(name)||name.startsWith('.'))continue;
    const source=join(dir,name),rel=relative(root,source),target=join(out,rel);
    const s=await stat(source);
    if(s.isDirectory()){await visit(source);continue;}
    if(!allowed.has(extname(name).toLowerCase()))continue;
    await mkdir(dirname(target),{recursive:true});
    await copyFile(source,target);copied++;
  }
}
await visit(root);
if(copied<10)throw new Error('El artefacto no contiene suficientes archivos de la web.');
console.log('Copia de la web actual: '+copied+' archivos. El antiguo admin.html público queda excluido.');
