#!/usr/bin/env python3
"""Purpose: original MICA native-layout art, round 5; pinned ROM art is review-only.
Inputs: MICA palette, disassembly mappings/DPLC, optional native reference binaries.
Origin: original-character art lane, 2026-11-08. No Java build or fixed-palette output.
"""
import argparse, ast, importlib.util, json, re
from pathlib import Path
from PIL import Image, ImageDraw
HERE=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('wave3',HERE/'build.py')
b=importlib.util.module_from_spec(spec);spec.loader.exec_module(b)
ROOT=b.ROOT

def block(text,label):
 return text.split(label+':',1)[1].split('\n'+next(iter(re.findall(r'\n([\w.]+):',text.split(label+':',1)[1])), '\0')+':',1)[0]

def value(s):
 node=ast.parse(re.sub(r"\$([0-9A-Fa-f]+)",r"0x\1",s.strip()),mode="eval").body
 def calc(n):
  if isinstance(n,ast.Constant) and isinstance(n.value,int):return n.value
  if isinstance(n,ast.UnaryOp) and isinstance(n.op,ast.USub):return -calc(n.operand)
  if isinstance(n,ast.BinOp) and isinstance(n.op,(ast.Add,ast.Sub)):return calc(n.left)+(1 if isinstance(n.op,ast.Add) else -1)*calc(n.right)
  raise ValueError(s)
 return calc(node)

def mapping(path,base):
 text=(ROOT/path).read_text();labels=re.findall(r'dc.w\s+(\w+)-'+base,text);out=[]
 for label in labels:
  pieces=[]
  for a in re.findall(r'dc.b([^\n;]+)',block(text,label)):
   y,size,hi,lo,xhi,xlo=map(value,a.split(','));x=xhi*256+xlo;t=hi*256+lo
   pieces.append([x-65536 if x>=32768 else x,y-256 if y>=128 else y,(size>>2)+1,(size&3)+1,t&2047,(t>>11)&1,(t>>12)&1,(t>>13)&3,1])
  out.append((label,pieces))
 return out

def dplc(path,base):
 text=(ROOT/path).read_text();out=[]
 for label in re.findall(r'dc.w\s+(\w+)-'+base,text):
  vals=[b.num(v) for v in re.findall(r'dc.w\s+(\$[0-9A-F]+|\d+)',block(text,label))]
  loads=[[(v>>12)+1,v&4095] for v in vals[1:]]
  assert len(loads)==vals[0],label
  out.append((label,loads))
 return out

def curled(phase):
 # Five distinct hand-pixelled scale phases. One oval and an inward tucked cream
 # cheek/glove/boot keep the pangolin readable while the scale rows travel.
 im=b.indexed((96,96));d=ImageDraw.Draw(im)
 d.ellipse((33,33,62,62),fill=3,outline=1)
 for row,y in enumerate(range(35,61,6)):
  for x in range(31+((row+phase)%2)*4,62,8):
   d.polygon([(x,y),(x+3,y-2),(x+7,y),(x+6,y+4),(x+3,y+5),(x,y+3)],fill=4+(phase%2),outline=2)
   d.line([(x+1,y),(x+3,y-1),(x+5,y)],fill=6)
 # Revealed phases: tucked face, bright eye, mitten and coral boot advance
 # clockwise through the curl. The recurring phase 0 is entirely scale armour.
 if phase:
  x,y={1:(47,43),2:(41,46),3:(40,40),4:(47,39)}[phase]
  d.polygon([(x,y),(x+5,y-2),(x+8,y+1),(x+9,y+5),(x+5,y+8),(x,y+6)],fill=7,outline=1)
  d.rectangle((x+3,y,x+5,y+3),fill=12)
  d.line([(x+5,y+1),(x+5,y+3)],fill=1)
  d.point((x+8,y+4),fill=1)
  bx,by={1:(45,55),2:(35,48),3:(40,35),4:(54,47)}[phase]
  d.rectangle((bx,by,bx+6,by+3),fill=10,outline=1)
  d.line([(bx+1,by+1),(bx+4,by+1)],fill=12)
  gx,gy={1:(44,51),2:(39,43),3:(48,39),4:(51,49)}[phase]
  d.ellipse((gx,gy,gx+4,gy+4),fill=12,outline=1)
 # Keep the oval's continuous inner ink; do not let scales create spikes.
 for y in range(96):
  for x in range(96):
   r=((x-47.5)/15)**2+((y-47.5)/15)**2
   if r>1:im.putpixel((x,y),0)
   elif r>.81:im.putpixel((x,y),1)
 return im

def slot(dest):
 mp='docs/skdisasm/General/Sprites/Sonic/Map - Sonic.asm';dp='docs/skdisasm/General/Sprites/Sonic/DPLC - Sonic.asm'
 maps=mapping(mp,'Map_Sonic_');plcs=dplc(dp,'PLC_Sonic_')
 raw=(ROOT/'docs/skdisasm/General/Sprites/Sonic/Art/Sonic.unc').read_bytes()
 colors=b.pal(ROOT/'docs/skdisasm/General/Sprites/Sonic/Palettes/SonicAndTails.bin')
 frames=[];refs=[]
 for phase,i in enumerate(range(0x96,0x9b)):
  label,pieces=maps[i];pl,loads=plcs[i];im=curled(phase);out,hot,_=b.crop_mapping(im,pieces)
  file=f's3k-slot-{i:02x}.png';out.save(HERE/file,bits=4,transparency=0)
  frames.append(dict(id=i,file=file,dimensions=list(out.size),hotspot=hot,native_source_label=label,pieces=pieces,dplc_label=pl,dplc_entries=loads,tile_budget=sum(n for n,t in loads)))
  refs.append(b.native(pieces,b''.join(raw[t*32:(t+n)*32] for n,t in loads),colors))
 seq=[0,1,0,2,0,3,0,4]
 if dest:b.review(dest,'s3k-slot',frames,refs,[('roll',seq,5)])
 return dict(surface='slot_machine',game='s3k',native_source_label='Obj_Sonic_RotatingSlotBonus / loc_4B97C / sub_4B99E / off_4B9C2 / loc_4B9E8',source='docs/skdisasm/sonic3k.asm',mapping_source=mp,dplc_source=dp,native_art_label='ArtUnc_Sonic',palette='../palette.yaml',palette_line=0,palette_binding='Load MICA palette into player line 0; never quantize jade to native blue. Stage-sharing audit remains engine lane.',audit='No dedicated slot-stage Sonic artwork: native swaps in the normal host Map_Sonic + PLC_Sonic. It forces animation 2 each update. These five native roll mappings are explicit replacements, not the four-frame canonical ROLL remap.',timing=dict(source='AniSonic02 / AniSonic03 / loc_12A2A / loc_12A4C / loc_12A5E / loc_12A68',frames=[0x96+i for i in seq],ticks='1 + (max(0, 0x400 - abs(ground_vel)) >> 8), native word arithmetic',preview_ticks=5),sequences=[dict(name='roll',frames=[0x96+i for i in seq])],frames=frames)

def verify(surface):
 for f in surface['frames']:
  im=Image.open(HERE/f['file']);assert im.mode=='P';assert im.getpalette()[:48]==sum(b.COLORS,[]);assert im.info['transparency']==0;assert list(im.size)==f['dimensions'];assert max(im.get_flattened_data())<16
  # Extract every piece back to tile order and reject incompatible aliases.
  tiles={};hx,hy=f['hotspot']
  for x,y,w,h,t,fx,fy,*_ in f['pieces']:
   p=im.crop((x+hx,y+hy,x+hx+w*8,y+hy+h*8))
   if fx:p=p.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
   if fy:p=p.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
   for tx in range(w):
    for ty in range(h):
     n=t+tx*h+ty;v=p.crop((tx*8,ty*8,tx*8+8,ty*8+8)).tobytes()
     assert n not in tiles or tiles[n]==v,(f['file'],'aliased tile',n)
     tiles[n]=v
  assert not tiles or max(tiles)<f['tile_budget']
 print(surface['surface'],len(surface['frames']),'palette/layout/alias/budget checks passed')

def main():
 p=argparse.ArgumentParser();p.add_argument('--review',type=Path);a=p.parse_args()
 m=json.loads((HERE/'wave3.yaml').read_text());s=slot(a.review);verify(s)
 m['surfaces']=[x for x in m['surfaces'] if x['surface']!=s['surface']]+[s]
 (HERE/'wave3.yaml').write_text(json.dumps(m,indent=2)+'\n')
if __name__=='__main__':main()
