import { useEffect, useRef, useState } from 'react';
import { ArrowUp, ArrowUpRight, AudioLines, ChevronDown, CloudRain, Headphones, Mic, RotateCcw, Settings2, Volume2, VolumeX, X } from 'lucide-react';
import Scene from './Scene';
import {useContinuousVoice} from './useContinuousVoice';
import { TurnGate, pcm16, type Segment } from './turns';

const labels:Record<string,string>={idle:'在这里，等你开口',listening:'正在听你说',thinking:'正在整理思绪',speaking:'正在轻声讲述'};
export default function App(){
 const [state,setState]=useState('idle'),[emotion,setEmotion]=useState('calm'),[action,setAction]=useState('none'),[clear,setClear]=useState(false);
 const [connected,setConnected]=useState(false),[mode,setMode]=useState('mock'),[text,setText]=useState(''),[subtitle,setSubtitle]=useState('雨还没停。要不要坐下来，聊一会儿？');
 const [warning,setWarning]=useState(''),[muted,setMuted]=useState(false),[entered,setEntered]=useState(false),[settings,setSettings]=useState(false),[fault,setFault]=useState(''),[failed,setFailed]=useState(false),[transcript,setTranscript]=useState(''),[chapter,setChapter]=useState(0);
 const gate=useRef(new TurnGate()), ws=useRef<WebSocket|null>(null), queue=useRef<Segment[]>([]), busy=useRef(false), done=useRef(false);
 const audio=useRef<HTMLAudioElement|null>(null), stopWait=useRef<(()=>void)|null>(null), mutedRef=useRef(false), voiceTurn=useRef<number|null>(null), lastInput=useRef('你好，Mira');
 const send=(data:object)=>{if(ws.current?.readyState===WebSocket.OPEN)ws.current.send(JSON.stringify(data));};
 const voice=useContinuousVoice({
  onStart:()=>{const id=invalidate();voiceTurn.current=id;setEntered(true);setWarning('');setSubtitle('');setState('listening');send({type:'begin',turnId:id,voice:true,fault});},
  onEnd:samples=>{const id=voiceTurn.current;voiceTurn.current=null;if(id===null||!gate.current.accepts(id))return;
   const bytes=pcm16(samples,16000);for(let i=0;i<bytes.length;i+=16000){let data='';for(const b of bytes.subarray(i,i+16000))data+=String.fromCharCode(b);send({type:'audio',turnId:id,data:btoa(data)});}
   send({type:'end',turnId:id});setState('thinking');},
  onError:message=>{invalidate();setState('idle');setWarning(message);}
 });
 function stopCapture(){voice.stop();voiceTurn.current=null;}
 async function toggleVoice(){
  if(voice.active.current){stopCapture();invalidate();setState('idle');return;}
  invalidate();setEntered(true);setWarning('');setState('listening');await voice.start();
 }
 function stopPlayback(){
   const a=audio.current; if(a){a.pause();a.removeAttribute('src');a.load();audio.current=null;}
   stopWait.current?.();stopWait.current=null;queue.current=[];busy.current=false;done.current=false;
 }
 function invalidate(){const old=gate.current.current;const id=gate.current.next();send({type:'cancel',turnId:old});voiceTurn.current=null;stopPlayback();setAction('none');return id;}
 function finish(id:number){if(gate.current.accepts(id)&&done.current&&!busy.current&&!queue.current.length){setState(voice.active.current?'listening':'idle');send({type:'complete',turnId:id});}}
 async function play(s:Segment){
   const id=s.turnId;if(!gate.current.accepts(id))return;
   setState('speaking');setEmotion(s.emotion);if(s.action!=='none')setAction(s.action);if(s.scene!=='keep')setClear(s.scene==='clear');
   if(s.action==='photo')setChapter(2);else if(s.emotion==='worried')setChapter(c=>Math.max(1,c));else if(s.scene==='clear')setChapter(3);
   if(s.warning)setWarning(s.warning);setFailed(!!s.mediaFailed);setSubtitle(s.text);
   const url=s.audio?URL.createObjectURL(new Blob([Uint8Array.from(atob(s.audio),c=>c.charCodeAt(0))],{type:'audio/mpeg'})):s.audioUrl;
   let cancelled=false;
   try{
    if(url){
      await new Promise<void>((resolve,reject)=>{
        const a=new Audio(url);audio.current=a;a.muted=mutedRef.current;let settled=false;
        const timeout=setTimeout(()=>end(new Error('音频加载超时')),15000);
        const end=(error?:Error)=>{if(settled)return;settled=true;clearTimeout(timeout);a.onended=null;a.onerror=null;a.onplaying=null;a.ontimeupdate=null;stopWait.current=null;error?reject(error):resolve();};
        stopWait.current=()=>{cancelled=true;end();};
        a.onended=()=>end();a.onerror=()=>end(new Error('音频加载失败'));
        a.onplaying=()=>{clearTimeout(timeout);};
        a.ontimeupdate=()=>{if(gate.current.accepts(id)&&Number.isFinite(a.duration)&&a.duration>0){const count=Math.max(8,Math.ceil((a.currentTime/a.duration)*s.text.length));setSubtitle(s.text.slice(0,count));}};
        a.play().catch(e=>end(e));
      });
    }else await textDelay(s.text.length);
   }catch{
      if(gate.current.accepts(id)){setWarning('声音暂时无法播放，已保留完整字幕。可继续对话重试。');setFailed(true);setSubtitle(s.text);await textDelay(s.text.length);}
   }finally{if(s.audio&&url)URL.revokeObjectURL(url);}
   if(!cancelled&&gate.current.accepts(id)){setSubtitle(s.text);send({type:'played',turnId:id,index:s.index});}
 }
 function textDelay(length:number){return new Promise<void>(resolve=>{const timer=setTimeout(()=>{stopWait.current=null;resolve();},Math.min(12000,Math.max(3500,length*110)));stopWait.current=()=>{clearTimeout(timer);resolve();};});}
 async function pump(id:number){
   if(busy.current)return;busy.current=true;
   while(gate.current.accepts(id)&&queue.current.length){const s=queue.current.shift()!;await play(s);}
   if(gate.current.accepts(id)){busy.current=false;finish(id);}
 }
 function connect(){
   ws.current?.close();const socket=new WebSocket(`${location.protocol==='https:'?'wss':'ws'}://${location.host}/ws`);ws.current=socket;
   socket.onopen=()=>{if(ws.current===socket){setConnected(true);setWarning('');}};
   socket.onmessage=event=>{
     if(ws.current!==socket)return;const m=JSON.parse(event.data);
     if(m.type==='hello'){setMode(m.mode);return;}
     if(!gate.current.accepts(m.turnId))return;
     if(m.type==='state')setState(m.state);
     if(m.type==='transcript'){setTranscript(`${m.simulated?'模拟识别：':''}${m.text}`);}
     if(m.type==='segment'){queue.current.push(m);void pump(m.turnId);}
     if(m.type==='done'){done.current=true;finish(m.turnId);}
     if(m.type==='error'){invalidate();setState(voice.active.current?'listening':'idle');setWarning(m.message);}
   };
   socket.onclose=()=>{if(ws.current===socket){setConnected(false);stopCapture();invalidate();setState('idle');setWarning('连接已断开。重新连接后将开始新的会话。');}};
   socket.onerror=()=>{if(ws.current===socket)setWarning('无法连接服务，请确认后端已启动。');};
 }
 useEffect(()=>{connect();const onHidden=()=>{if(document.hidden){stopCapture();invalidate();setState('idle');}};document.addEventListener('visibilitychange',onHidden);return()=>{document.removeEventListener('visibilitychange',onHidden);const s=ws.current;ws.current=null;s?.close();gate.current.next();stopCapture();stopPlayback();};},[]);
 function begin(input:string){
   if(!connected||!input.trim())return;const id=invalidate();setWarning('');setFailed(false);setEntered(true);setTranscript(input);setSubtitle('');setState('thinking');lastInput.current=input;
   send({type:'begin',turnId:id,text:input,fault});setText('');
 }
 function toggleMute(){mutedRef.current=!mutedRef.current;setMuted(mutedRef.current);if(audio.current)audio.current.muted=mutedRef.current;}
 return <main className="app">
  <Scene state={state} emotion={emotion} action={action} clear={clear} failed={failed}/>
  <header><a className="brand" href="/" aria-label="雨停之前首页"><span className="brand-mark">雨</span><span>雨停之前<small>AFTER THE RAIN</small></span></a><div className="top-tools"><span className="mode"><i className={connected?'online':''}/>{connected?(mode==='mock'?'离线剧情 · MOCK':'百炼 · LIVE'):'未连接'}</span><button className="icon" onClick={toggleMute} aria-label={muted?'开启声音':'关闭声音'}>{muted?<VolumeX size={19}/>:<Volume2 size={19}/>}</button><button className="icon" onClick={()=>setSettings(!settings)} aria-label="体验设置"><Settings2 size={19}/></button></div></header>
  <aside className="scene-note"><span>CHAPTER 01</span><h1>一场雨，<br/>一次偶然的停留。</h1><p>城市的灯渐渐熄了。<br/>还有一杯咖啡，一个未说完的故事。</p><div className="location"><CloudRain size={15}/> 雨巷咖啡馆 <b>·</b> {clear?'雨后微光':'23:47'}</div></aside>
  <div className="character-tag"><span className="little-star">✧</span><div>Mira<small>旅行摄影师 · 26 岁</small></div></div>
  <aside className="chapter-track" aria-label="剧情进度">{['偶然相遇','未寄出的信','光的方向','雨停以后'].map((s,i)=><div key={s} className={chapter>=i?'reached':''}><i/>{s}</div>)}</aside>
  <section className="conversation" aria-label="互动区">
    <div className={`status ${state}`}><span className="status-bars"><i/><i/><i/><i/></span>{labels[state]}</div>
    <div className="subtitle" aria-live="polite"><span className="speaker">MIRA</span><p>{subtitle|| (state==='listening'?'我在听，慢慢说。':state==='thinking'?'稍等，让我想一想……':'')}</p></div>
    {transcript&&<div className="transcript" title={transcript}>{transcript}</div>}
    {warning&&<div className="warning" role="alert"><span>{warning}</span><button onClick={()=>connected?begin(lastInput.current):connect()}>{connected?'重试':'重新连接'} <RotateCcw size={12}/></button></div>}
    {!entered&&<button className="enter" onClick={()=>begin('你好，Mira')} disabled={!connected}>坐一会儿 <ArrowUpRight size={18}/></button>}
    <div className="suggestions">{['你在等谁？','可以看看你的照片吗？','我陪你等雨停。'].map(s=><button key={s} disabled={!connected} onClick={()=>begin(s)}>{s}<ArrowUpRight size={12}/></button>)}</div>
    <form className="composer" onSubmit={e=>{e.preventDefault();begin(text);}}><input aria-label="和 Mira 说点什么" value={text} maxLength={1000} onChange={e=>setText(e.target.value)} placeholder="和 Mira 说点什么…" disabled={!connected}/><button className="send" type="submit" disabled={!connected||!text.trim()} aria-label="发送文字"><ArrowUp size={19}/></button><span className="divider"/><button type="button" className={`mic ${voice.enabled?'recording':''}`} disabled={!connected} aria-label={voice.enabled?'结束连续对话':'开始连续对话'} onClick={()=>void toggleVoice()}><Mic size={18}/><span>{voice.loading?'加载中…':voice.enabled?'结束对话':'连续对话'}</span></button></form>
    <footer><span><Headphones size={12}/> 戴上耳机，让故事靠近一点</span><span>{voice.enabled?'麦克风已开启 · 停顿后自动发送':'点一次麦克风，就可以一直聊'}</span></footer>
  </section>
  {settings&&<div className="settings"><div><h2>体验设置</h2><button className="icon" onClick={()=>setSettings(false)} aria-label="关闭设置"><X size={18}/></button></div><p>{mode==='mock'?'当前使用本地剧情和录制音频。麦克风可录音，识别结果固定模拟为”给我看看旅行照片”，不是真实语音识别。':'当前通过 Java 服务端调用百炼。点击一次开启连续语音，停顿后自动发送，再次开口可以打断回应；点击结束关闭麦克风。'}</p><button className="setting-button" onClick={()=>{invalidate();setState(voice.active.current?'listening':'idle');setSubtitle('我们慢慢聊。');}}>停止当前回应 <AudioLines size={16}/></button>{mode==='mock'&&<label>下一轮故障演示<select value={fault} onChange={e=>setFault(e.target.value)}><option value="">正常体验</option><option value="timeout">模型超时</option><option value="media">媒体失败，静态降级</option><option value="late">延迟响应（用于打断测试）</option></select></label>}<button className="setting-button" onClick={()=>{stopCapture();invalidate();setChapter(0);setClear(false);setEmotion('calm');setEntered(false);setTranscript('');setSubtitle('雨还没停。要不要坐下来，聊一会儿？');setState('idle');connect();}}>重新开始 <RotateCcw size={15}/></button><small>原创虚构角色 · AI 互动体验</small><ChevronDown size={14}/></div>}
 </main>;
}
