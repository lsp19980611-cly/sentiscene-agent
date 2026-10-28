class Recorder extends AudioWorkletProcessor {
  constructor(){ super(); this.buffer=[]; }
  process(inputs){
    const input=inputs[0]?.[0];
    if(input){this.buffer.push(...input); if(this.buffer.length>=4096){const chunk=new Float32Array(this.buffer);this.port.postMessage(chunk,[chunk.buffer]);this.buffer=[];}}
    return true;
  }
}
registerProcessor('recorder',Recorder);
