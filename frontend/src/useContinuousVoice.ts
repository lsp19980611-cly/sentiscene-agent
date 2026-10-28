import {useEffect,useRef,useState} from 'react';
import type {MicVAD} from '@ricky0123/vad-web';

type Callbacks={onStart:()=>void;onEnd:(audio:Float32Array)=>void;onError:(message:string)=>void};
/** A session owns one microphone. Turn cancellation never reacquires it. */
export function useContinuousVoice(callbacks:Callbacks){
 const latest=useRef(callbacks);latest.current=callbacks;
 const [enabled,setEnabled]=useState(false),[loading,setLoading]=useState(false);
 const active=useRef(false),epoch=useRef(0),vad=useRef<MicVAD|null>(null);
 const stream=useRef<MediaStream|null>(null),limit=useRef<ReturnType<typeof setTimeout>|undefined>(undefined);
 function stop(){
  active.current=false;epoch.current++;clearTimeout(limit.current);
  stream.current?.getTracks().forEach(track=>track.stop());stream.current=null;
  const instance=vad.current;vad.current=null;void instance?.destroy().catch(()=>{});
  setEnabled(false);setLoading(false);
 }
 async function start(){
  if(active.current)return;
  active.current=true;const generation=++epoch.current;setEnabled(true);setLoading(true);
  const current=()=>active.current&&generation===epoch.current;
  try{
   if(!navigator.mediaDevices?.getUserMedia)throw new Error('HTTPS required');
   const mic=await navigator.mediaDevices.getUserMedia({audio:{echoCancellation:true,noiseSuppression:true,autoGainControl:true,channelCount:1}});
   if(!current()){mic.getTracks().forEach(track=>track.stop());return;}stream.current=mic;
   const {MicVAD}=await import('@ricky0123/vad-web');
   if(!current())return;
   const instance=await MicVAD.new({model:'v5',startOnLoad:false,baseAssetPath:'/vad/',onnxWASMBasePath:'/vad/',
    ortConfig:ort=>{ort.env.wasm.numThreads=1;},getStream:async()=>mic,
    redemptionMs:850,preSpeechPadMs:300,minSpeechMs:200,positiveSpeechThreshold:.65,negativeSpeechThreshold:.4,
    onSpeechRealStart:()=>{if(!current())return;latest.current.onStart();clearTimeout(limit.current);
     limit.current=setTimeout(()=>{if(current()){stop();latest.current.onError('这段话有点长，请重新开启麦克风，分成短句说。');}},28000);},
    onSpeechEnd:samples=>{if(current()){clearTimeout(limit.current);latest.current.onEnd(samples);}},
   });
   if(!current()){await instance.destroy();return;}vad.current=instance;
   await instance.start();if(current())setLoading(false);
  }catch{if(current()){stop();latest.current.onError('无法使用麦克风或加载语音检测，请检查权限和连接，或直接输入文字。');}}
 }
 useEffect(()=>()=>stop(),[]);
 return {enabled,loading,active,start,stop};
}
