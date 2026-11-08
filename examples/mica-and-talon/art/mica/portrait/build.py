#!/usr/bin/env python3
"""Purpose: MICA save portrait native-pixel source and disposable native comparison.
Inputs: unchanged shared palette, pinned save mappings/Misc.kos (review only).
Origin: MICA design lane, 2026-11-08. Run from repository root; no Java.
"""
import argparse, importlib.util, json
from pathlib import Path
from PIL import Image, ImageDraw
HERE=Path(__file__).resolve().parent
ART=HERE.parent
ROOT=HERE.parents[4]
def load(name,path):
 s=importlib.util.spec_from_file_location(name,path);m=importlib.util.module_from_spec(s);s.loader.exec_module(m);return m
r=load('round5',ART/'wave3/round5.py');b=r.b
p=load('presentation',ROOT/'tools/modding/playable_art/presentation.py')

def kos(path):
 data=path.read_bytes();pos=0;bits=0;left=0;out=bytearray()
 def byte():
  nonlocal pos
  v=data[pos];pos+=1;return v
 def refill():
  nonlocal bits,left
  bits=byte()|byte()<<8;left=16
 def bit():
  nonlocal bits,left
  v=bits&1;bits>>=1;left-=1
  if left==0:refill()
  return v
 refill()
 while True:
  if bit():out.append(byte());continue
  if bit():
   lo=byte();hi=byte();distance=8192-(((hi&248)<<5)|lo);count=hi&7
   if count:count+=2
   else:
    count=byte()+1
    if count==1:break
    if count==2:continue
  else:
   count=2+2*bit()+bit();distance=256-byte()
  for _ in range(count):out.append(out[-distance] if distance<=len(out) else 0)
 return bytes(out)

def portrait():
 # Native bounds (-18,-40)..(14,0), engine centre at 48,48.
 im=b.indexed((96,96));d=ImageDraw.Draw(im)
 d.polygon([(31,36),(30,30),(35,26),(40,28),(43,37),(38,40)],fill=3,outline=1)
 d.line([(31,33),(35,30),(39,32)],fill=6)
 d.polygon([(38,28),(50,27),(55,34),(55,41),(43,43),(36,37)],fill=4,outline=1)
 d.polygon([(43,28),(49,28),(53,33),(52,39),(47,42),(42,38)],fill=7,outline=1)
 for y in (33,36,39):d.line([(44,y),(51,y)],fill=8)
 head=p.sign('s3k').crop((5,0,40,27)).resize((29,25),Image.Resampling.NEAREST).transpose(Image.Transpose.FLIP_LEFT_RIGHT)
 im.paste(head,(31,8),head.point(lambda v:255 if v else 0,mode='L'))
 d.ellipse((32,31,40,38),fill=12,outline=1);d.line([(34,35),(38,36)],fill=13)
 d.polygon([(53,33),(55,29),(56,24),(58,23),(60,25),(59,29),(61,30),(61,35),(58,38),(54,37)],fill=12,outline=1)
 d.line([(57,31),(60,31)],fill=13);d.line([(56,34),(59,34)],fill=13)
 for x in (39,51):
  d.polygon([(x,39),(x+7,39),(x+8,42),(x+10,43),(x+10,47),(x,47)],fill=10,outline=1)
  d.line([(x+1,46),(x+9,46)],fill=9)
  d.line([(x+2,41),(x+6,41)],fill=12)
  d.line([(x+3,43),(x+7,43)],fill=5)
 return im

def sheets(dest,im,sonic,tails):
 dest.mkdir(parents=True,exist_ok=True)
 def place(canvas,sprite,x,y):
  rgba=sprite.convert('RGBA');canvas.paste(rgba,(x,y),rgba)
 # Whole 96px canvases retain the native mapping origin at (48,48).
 rows=[]
 for name,sprite in [('MICA',im),('native Sonic',sonic)]:
  row=Image.new('RGB',(240,96),'#969696');place(row,sprite,0,0);ImageDraw.Draw(row).text((105,35),name,fill='black');rows.append(row)
 # Compose mapping offsets exactly: main x -14, Tails x 3 (bounds -25..23),
 # both bottom 0; Tails mirrored inside its own mapping bounds -25..15.
 for team in (False,True):
  row=Image.new('RGB',(240,96),'#969696');place(row,im,-14 if team else -12,0)
  if team:
   # Native Tails bounds (-25,-40)..(15,0), mirrored about -5.
   t=tails.crop((23,8,63,48)).transpose(Image.Transpose.FLIP_LEFT_RIGHT)
   place(row,t,48+3-25,8)
  ImageDraw.Draw(row).text((105,35),'MICA + Tails' if team else 'MICA solo',fill='black');rows.append(row)
 sheet=Image.new('RGB',(240,96*len(rows)))
 for i,row in enumerate(rows):sheet.paste(row,(0,96*i))
 for scale in (1,4):sheet.resize((sheet.width*scale,sheet.height*scale),Image.Resampling.NEAREST).save(dest/f'portrait-{scale}x.png')

def main():
 a=argparse.ArgumentParser();a.add_argument('--review',type=Path);args=a.parse_args()
 mp='docs/skdisasm/General/Save Menu/Map - Save Screen General.asm'
 maps=r.mapping(mp,'Map_SaveScreen_');label,pieces=maps[5]
 im=portrait();out,hot,box=b.crop_mapping(im,pieces)
 out.save(HERE/'mica.png',bits=4,transparency=0)
 f=dict(id=5,file='mica.png',native_source_label=label,dimensions=list(out.size),hotspot=hot,pieces=pieces)
 manifest=dict(format='openggf-mica-portrait-1',palette='../palette.yaml',palette_line=1,native_source_label='Map_SaveScreen / word_CF5C / ArtKos_SaveScreenMisc',mapping_source=mp,native_art='General/Save Menu/Kosinski Art/Misc.kos',timing='Static; selection animation belongs to save slot, not portrait',frames=[f],composition=dict(solo=dict(x=-12,y=0,mirror=False),with_tails=dict(main_x=-14,main_y=0,tails_x=3,tails_y=0,tails_mirror=True,overlap=10,draw_order=['MICA','Tails'])),palette_binding='Reserve a separate MICA palette line and rebind her pieces. Native Sonic/Tails share line 1 with other menu art. Do not overwrite stock Tails or quantize jade to blue.',engine_limit='Composer currently maps unknown codes to stock Sonic frame 5. Engine lane must register this art and palette; previews simulate the measured layout, not runtime integration.',weak_points='Compact boots and glove fingers simplify at 1x; team overlap hides part of right glove. No runtime background or selection certification.')
 (HERE/'portrait.yaml').write_text(json.dumps(manifest,indent=2)+'\n')
 assert out.mode=='P' and out.getpalette()[:48]==sum(b.COLORS,[]) and max(out.get_flattened_data())<16
 if args.review:
  raw=kos(ROOT/'docs/skdisasm/General/Save Menu/Kosinski Art/Misc.kos');colors=b.pal(ROOT/'docs/skdisasm/General/Save Menu/Palettes/Chars.bin')
  # Original cropped output placed back at its mapping origin.
  original=b.indexed((96,96));original.paste(out,(48-hot[0],48-hot[1]))
  sheets(args.review,original,b.native(pieces,raw,colors),b.native(maps[6][1],raw,colors))
 print('portrait: 1 frame, native bounds',box,'palette/index checks passed')
if __name__=='__main__':main()
