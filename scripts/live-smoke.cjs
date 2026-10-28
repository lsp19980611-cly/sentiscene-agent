if(process.env.RUN_LIVE_TESTS!=='1')throw new Error('Set RUN_LIVE_TESTS=1 to authorize calls that consume model credits.');
const {chromium}=require('../frontend/node_modules/playwright');
const fs=require('node:fs'),assert=require('node:assert/strict');
(async()=>{
 const browser=await chromium.launch({...(process.env.CHROME_PATH?{executablePath:process.env.CHROME_PATH}:{}),headless:true,args:['--autoplay-policy=no-user-gesture-required']});
 const page=await browser.newPage({viewport:{width:1440,height:1000}});const events=[],errors=[];
 page.on('pageerror',e=>errors.push(e.message));
 page.on('websocket',socket=>socket.on('framereceived',frame=>{try{const m=JSON.parse(frame.payload.toString());events.push({type:m.type,turnId:m.turnId,index:m.index,text:m.text,warning:m.warning,message:m.message,audioBytes:m.audio?Math.floor(m.audio.length*3/4):0,action:m.action});}catch{}}));
 await page.addInitScript(()=>{window.testAudio=[];const Native=window.Audio;window.Audio=class extends Native{constructor(src){super(src);window.testAudio.push(this);}};window.testSockets=[];const WS=window.WebSocket;window.WebSocket=class extends WS{constructor(...args){super(...args);window.testSockets.push(this);}};});
 await page.goto(process.env.APP_URL||'http://127.0.0.1:8080');await page.getByText('百炼 · LIVE').waitFor();await page.getByRole('button',{name:'你在等谁？',exact:true}).click();
 await page.waitForFunction(()=>window.testAudio.some(a=>!a.paused&&a.currentTime>.1),{},{timeout:60000});
 console.log('PASS Java -> Bailian DashScope chat + TTS -> browser audio');
 await page.screenshot({path:require('node:path').join(__dirname,'../verification/live-desktop.png'),fullPage:true});
 const payload=fs.readFileSync(require('node:path').join(__dirname,'../frontend/public/audio/hello.wav'));
 // Feed a known PCM phrase through the same client WebSocket recording messages.
 let offset=12,pcm;while(offset<payload.length){const id=payload.toString('ascii',offset,offset+4),len=payload.readUInt32LE(offset+4);if(id==='data'){pcm=payload.subarray(offset+8,offset+8+len);break;}offset+=8+len+(len%2);}
 assert(pcm); // This fixture is 22050 Hz; resample to the client wire contract.
 const rate=payload.readUInt32LE(24),resampled=Buffer.alloc(Math.floor(pcm.length/2*16000/rate)*2);
 for(let i=0;i<resampled.length/2;i++)resampled.writeInt16LE(pcm.readInt16LE(Math.min(pcm.length/2-1,Math.floor(i*rate/16000))*2),i*2);
 // Click interruption is tested on the UI, then send a real ASR request on a fresh dedicated socket.
 await page.getByRole('button',{name:'可以看看你的照片吗？',exact:true}).click();assert(await page.evaluate(()=>window.testAudio[0].paused));
 const asr=await page.evaluate(async data=>await new Promise((resolve,reject)=>{
   const w=new WebSocket(`ws://${location.host}/ws`);const received=[];const timer=setTimeout(()=>{w.close();reject(new Error('Live ASR timeout'));},80000);
   w.onopen=()=>{w.send(JSON.stringify({type:'begin',turnId:1,voice:true}));for(let i=0;i<data.length;i+=32000)w.send(JSON.stringify({type:'audio',turnId:1,data:btoa(String.fromCharCode(...data.slice(i,i+32000)))}));w.send(JSON.stringify({type:'end',turnId:1}));};
   w.onmessage=e=>{const m=JSON.parse(e.data);if(m.type==='error'){clearTimeout(timer);w.close();reject(new Error(m.message));}if(m.type==='transcript')received.push(m.text);if(m.type==='segment'){clearTimeout(timer);w.send(JSON.stringify({type:'cancel',turnId:1}));w.close();resolve({transcripts:received,audioBytes:m.audio?.length||0,text:m.text,warning:m.warning});}};
 }),Array.from(resampled));
 assert(asr.transcripts.join('').includes('摄影师'));assert(asr.audioBytes>1000);console.log('PASS PCM -> Java -> real Bailian DashScope ASR -> dialogue -> TTS',JSON.stringify(asr));
 assert.equal(errors.length,0);fs.writeFileSync(require('node:path').join(__dirname,'../verification/live-tests.json'),JSON.stringify({date:new Date().toISOString(),provider:'Bailian DashScope',checks:['Chat and TTS via Java play in Chrome','Text interruption pauses prior audio','Actual PCM fixture recognized via Java and generates audible reply'],asr,events,errors},null,2));
 await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});
