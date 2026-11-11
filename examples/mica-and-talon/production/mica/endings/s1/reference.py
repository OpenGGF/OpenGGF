#!/usr/bin/env python3
"""Review-only decoder for pinned Nemesis/Enigma ending references.
Inputs: optional S1/S2 disassemblies. Origin: MICA endings lane 2026-11-08.
Never writes native/reference pixels under the art tree.
"""
import importlib.util,json,re
from pathlib import Path
from PIL import Image,ImageDraw
ROOT=Path(__file__).resolve().parents[6];ART=ROOT/'examples/mica-and-talon/art/mica'
def load(p,n):
 s=importlib.util.spec_from_file_location(n,p);m=importlib.util.module_from_spec(s);s.loader.exec_module(m);return m
b=load(ART/'wave3/build.py','b')
def maps(path):
 t=path.read_text();out={}
 for label,chunk in re.findall(r'([.\w]+):\s*spriteHeader(.*?)(?=\n[.\w]+_End)',t,re.S):
  out[label]=[list(map(b.num,a.split(','))) for a in re.findall(r'spritePiece\s+([^;\n]+)',chunk)]
 return out

def render(pieces,raw,pals):
 box=[min(p[0] for p in pieces),min(p[1] for p in pieces),max(p[0]+8*p[2] for p in pieces),max(p[1]+8*p[3] for p in pieces)]
 im=Image.new('RGBA',(box[2]-box[0],box[3]-box[1]))
 for x,y,w,h,t,fx,fy,pl,*_ in reversed(pieces):
  p=b.indexed((w*8,h*8),pals[pl])
  for tx in range(w):
   for ty in range(h):
    data=raw[(t+tx*h+ty)*32:(t+tx*h+ty+1)*32];assert len(data)==32,(t,len(raw));q=b.indexed((8,8),pals[pl]);q.putdata([n for v in data for n in (v>>4,v&15)]);p.paste(q,(tx*8,ty*8))
  if fx:p=p.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
  if fy:p=p.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
  im.alpha_composite(p.convert('RGBA'),(x-box[0],y-box[1]))
 return im

def eni(data):
 length,mask=data[:2];inc=int.from_bytes(data[2:4],'big');common=int.from_bytes(data[4:6],'big');bits=''.join(f'{v:08b}' for v in data[6:]);pos=0
 def read(n):
  nonlocal pos
  v=int(bits[pos:pos+n],2) if n else 0;pos+=n;return v
 def inline():
  flags=0
  for i in range(4,-1,-1):
   if mask&(1<<i):flags|=read(1)<<(11+i)
  return flags|read(length)
 out=[]
 while True:
  if read(1):
   mode=read(2);n=read(4)+1
   if mode==3:
    if n==16:return out
    out += [inline() for _ in range(n)]
   else:
    v=inline();out += [v+j*(0 if mode==0 else 1 if mode==1 else -1) for j in range(n)]
  else:
   mode=read(1);n=read(4)+1;out += [common if mode else inc+j for j in range(n)]
   if not mode:inc+=n

def photos():
 raw=b.nem(ROOT/'docs/s2disasm/art/nemesis/Movie sequence at end of game.nem');pals=[b.pal(ROOT/'docs/s2disasm/art/palettes'/p) for p in ['Ending Photos.bin']*4];out=[]
 for i in range(1,5):
  words=eni((ROOT/f'docs/s2disasm/mappings/misc/End of game sequence frame {i}.eni').read_bytes());assert len(words)==108,len(words)
  out.append(render([[j%12*8,j//12*8,1,1,v&2047,(v>>11)&1,(v>>12)&1,(v>>13)&3,0] for j,v in enumerate(words)],raw,pals))
 return out

def refs(game):
 inv=__import__('yaml').safe_load((ART/'endings/endings-inventory.yaml').read_text());out={}
 if game=='s1':
  mm=maps(ROOT/'docs/s1disasm/_maps/Ending Sequence Sonic.asm');raw=b.nem(ROOT/'docs/s1disasm/artnem/Ending - Sonic.nem');pal=b.pal(ROOT/'docs/s1disasm/palette/Ending.bin')
  for f in inv['frame_sets']['s1_celebration']['frames']:out[f['label']]=render(mm[f['label']],raw,[pal]*4)
 else:
  mm=maps(ROOT/'docs/s2disasm/mappings/sprite/objCF.asm');plane=b.nem(ROOT/'docs/s2disasm/art/nemesis/Small pictures of Tornado in final ending sequence.nem')
  pal=b.pal(ROOT/'docs/s2disasm/art/palettes/Ending Sonic.bin');p2=b.pal(ROOT/'docs/s2disasm/art/palettes/Ending Tails.bin')
  for group,name in [('s2_normal','Small pictures of Sonic and final image of Sonic.nem'),('s2_super','Small pictures of Sonic and final image of Sonic in Super Sonic mode.nem')]:
   raw=b.nem(ROOT/'docs/s2disasm/art/nemesis'/name);raw=bytes(25*32)+raw;data=raw+bytes(0x493*32-len(raw))+plane
   for key,v in inv['frame_sets'].items():
    if key.startswith(group) and 'walk' not in key:
     for f in v['frames']:
      cp=b.pal(ROOT/'docs/s2disasm/art/palettes/Ending Super Sonic.bin') if group=='s2_super' else pal
      out[f['label']]=render(mm[f['label']],data,[cp,p2,cp,cp])
  for i,im in enumerate(photos(),1):out[f'MapEng_Ending{i}']=im
 return out
