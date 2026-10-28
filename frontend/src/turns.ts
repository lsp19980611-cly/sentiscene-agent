export class TurnGate {
  current = 0;
  next() { return ++this.current; }
  accepts(id: number) { return id === this.current; }
}
export type Segment = { turnId:number; index:number; text:string; emotion:string; action:string; scene:string; audio?:string; audioUrl?:string; warning?:string; mediaFailed?:boolean };
export function pcm16(samples:Float32Array, rate:number):Uint8Array {
  const ratio=rate/16000, length=Math.floor(samples.length/ratio), bytes=new Uint8Array(length*2), view=new DataView(bytes.buffer);
  for(let i=0;i<length;i++) {
    const start=Math.floor(i*ratio), end=Math.max(start+1,Math.floor((i+1)*ratio)); let sum=0;
    for(let k=start;k<end && k<samples.length;k++) sum+=samples[k];
    const value=Math.max(-1,Math.min(1,sum/(end-start)));
    view.setInt16(i*2, value<0?value*32768:value*32767,true);
  }
  return bytes;
}
