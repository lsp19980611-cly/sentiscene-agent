import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import {readFileSync} from 'node:fs';
// ORT dynamically imports its runtime; bypass Vite's public-module transform in dev.
const localRuntime={name:'local-vad-runtime',configureServer(server:import('vite').ViteDevServer){
 server.middlewares.use((req,res,next)=>{
  if(req.url?.split('?')[0]!=='/vad/ort-wasm-simd-threaded.mjs')return next();
  res.setHeader('Content-Type','text/javascript');
  res.end(readFileSync(new URL('./public/vad/ort-wasm-simd-threaded.mjs',import.meta.url)));
 });
}};
export default defineConfig({plugins:[react(),localRuntime],server:{proxy:{'/api':'http://127.0.0.1:8080','/ws':{target:'ws://127.0.0.1:8080',ws:true},'/videos':'http://127.0.0.1:8080','/audio':'http://127.0.0.1:8080'}}});
