import {mkdir,copyFile} from 'node:fs/promises';
const destination=new URL('../public/vad/',import.meta.url);
await mkdir(destination,{recursive:true});
for(const name of ['vad.worklet.bundle.min.js','silero_vad_v5.onnx'])
 await copyFile(new URL(`../node_modules/@ricky0123/vad-web/dist/${name}`,import.meta.url),new URL(name,destination));
for(const name of ['ort-wasm-simd-threaded.mjs','ort-wasm-simd-threaded.wasm'])
 await copyFile(new URL(`../node_modules/onnxruntime-web/dist/${name}`,import.meta.url),new URL(name,destination));
