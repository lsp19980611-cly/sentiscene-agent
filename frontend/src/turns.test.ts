import {describe,it,expect} from 'vitest';
import {TurnGate,pcm16} from './turns';
describe('turn isolation',()=>{
 it('rejects late audio, subtitles and actions after repeated interruption',()=>{const g=new TurnGate();const old=g.next();g.next();const current=g.next();expect(g.accepts(old)).toBe(false);expect(g.accepts(current-1)).toBe(false);expect(g.accepts(current)).toBe(true);});
 it('invalidates async microphone permission on cancellation',()=>{const g=new TurnGate();const recording=g.next();g.next();expect(g.accepts(recording)).toBe(false);});
});
describe('wire PCM',()=>{
 it('downsamples 48k mono into 16k little endian signed audio',()=>{const b=pcm16(new Float32Array([1,1,1,-1,-1,-1]),48000);const v=new DataView(b.buffer);expect(b.length).toBe(4);expect(v.getInt16(0,true)).toBe(32767);expect(v.getInt16(2,true)).toBe(-32768);});
 it('clamps audio to avoid integer overflow',()=>{const b=pcm16(new Float32Array([4,-3]),16000);const v=new DataView(b.buffer);expect(v.getInt16(0,true)).toBe(32767);expect(v.getInt16(2,true)).toBe(-32768);});
});
