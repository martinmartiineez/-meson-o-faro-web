import { readdir, stat, copyFile, mkdir, rm, access } from 'node:fs/promises';
import { resolve, join, relative, extname, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const base = resolve(dirname(fileURLToPath(import.meta.url)),'..');
const root = resolve(base,'..');
const out = join(base,'dist','public');
const allowed = new Set(['.html','.js','.css','.svg','.png','.jpg','.jpeg','.webp','.gif','.ico','.webmanifest','.woff','.woff2','.avif']);
const nested = new Set(['gestion','sorteo']);
const forbidden = new Set(['analytics-cloudflare','.github','.git','node_modules','README.md','CNAME']);
let copied = 0;
await rm(join(base,'dist'),{recursive:true,force:true});
await mkdir(out,{recursive:true});
async function traverse(dir){
  for(const name of await readdir(dir)){
    if(name.startsWith('.')||forbidden.has(name)) continue;
    const file=join(dir,name),rel=relative(root,file);
    const meta=await stat(file);
    if(meta.isDirectory()){
      if(dir===root&&!nested.has(name)) continue;
      await traverse(file);
      continue;
    }
    if(!meta.isFile() || !allowed.has(extname(name).toLowerCase())) continue;
    await mkdir(dirname(join(out,rel)),{recursive:true});
    await copyFile(file,join(out,rel));
    copied++;
  }
}
await traverse(root);
await access(join(out,'index.html'));
if(copied<20)throw Error('Faltan recursos públicos: no se despliega.');
console.log('Compilados '+copied+' recursos sin exponer scripts .gs, migraciones, secretos ni código del Worker.');
