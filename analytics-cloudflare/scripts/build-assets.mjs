import { readdir, mkdir, copyFile, cp, rm } from 'node:fs/promises';
import { join, resolve, extname } from 'node:path';
import { fileURLToPath } from 'node:url';

const base=resolve(fileURLToPath(new URL('../..',import.meta.url)));
const output=join(base,'analytics-cloudflare','dist','public');
const allowed=new Set(['.html','.css','.js','.svg','.png','.jpg','.jpeg','.webp','.ico','.json','.webmanifest','.woff','.woff2','.avif']);
const subdirs=['gestion','sorteo'];

await rm(output,{recursive:true,force:true});
await mkdir(output,{recursive:true});
const entries=await readdir(base,{withFileTypes:true});
for(const entry of entries){
  if(!entry.isFile())continue;
  if(!allowed.has(extname(entry.name).toLowerCase()))continue;
  // Public entry points and front-end assets only. Never package .gs, GitHub or analytics source.
  if(entry.name==='package.json'||entry.name.startsWith('.'))continue;
  await copyFile(join(base,entry.name),join(output,entry.name));
}
for(const dir of subdirs){
  await cp(join(base,dir),join(output,dir),{
    recursive:true,
    filter:path=>{
      const filename=path.split('/').pop();
      if(filename===dir)return true;
      return !filename.startsWith('.') && allowed.has(extname(filename).toLowerCase());
    }
  });
}
console.log('Mesón O Faro: archivos públicos preparados para Cloudflare Workers');
