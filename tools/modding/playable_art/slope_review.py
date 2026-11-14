#!/usr/bin/env python3
"""Purpose: compare each MICA slope pose with decoded native S1 slope art.
Inputs: MICA handoff and optional pinned S1 disassembly art/maps/DPLC/palette.
Origin: W7 art round 3, 2026-11-08; review-only native pixels, never runtime assets.
S1 walk has six phases vs MICA eight: phase labels are comparisons, not parity.
"""
from pathlib import Path
import re,json,argparse
from PIL import Image,ImageDraw
root=Path(__file__).resolve().parents[3]
parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--review',type=Path,required=True)
dest=parser.parse_args().review;dest.mkdir(parents=True,exist_ok=True)
art=root/'examples/mica-and-talon/art/mica'
maptext=(root/'docs/s1disasm/_maps/Sonic.asm').read_text();plctext=(root/'docs/s1disasm/_maps/Sonic - Dynamic Gfx Script.asm').read_text();raw=(root/'docs/s1disasm/artunc/Sonic.unc').read_bytes();pal=(root/'docs/s1disasm/palette/Sonic.bin').read_bytes();palette=sum([[round(((int.from_bytes(pal[i:i+2],'big')>>s)&14)*255/14) for s in (0,4,8)] for i in range(0,32,2)],[])
def num(s):return int(s.replace('$','0x'),0) if '$' in s or s.startswith('-') else int(s)
def native(anim,angle,phase):
 key=('Walk' if anim=='WALK' else 'Run')+str({'up45':2,'steep':3,'inverted':4}[angle])+str(phase%(6 if anim=='WALK' else 4)+1)
 plc=plctext.split('SonPLC_'+key+':')[1].split('SonPLC_'+key+'_End')[0];data=b''
 for count,start in re.findall(r'dplcEntry\s+([^,]+),\s*([^\s;]+)',plc):
  n,t=num(count),num(start);data+=raw[t*32:(t+n)*32]
 out=Image.new('P',(80,80));out.putpalette(palette+[0]*(768-48));out.info['transparency']=0
 block=maptext.split('MS_'+key+':')[1].split('MS_'+key+'_End')[0]
 for args in re.findall(r'spritePiece\s+([^;\n]+)',block):
  x,y,w,h,t,fx,fy,*_=map(num,args.strip().split(','));piece=Image.new('P',(w*8,h*8));piece.putpalette(out.getpalette())
  for tx in range(w):
   for ty in range(h):
    b=data[(t+tx*h+ty)*32:(t+tx*h+ty+1)*32];assert len(b)==32
    tile=Image.new('P',(8,8));tile.putdata([p for v in b for p in (v>>4,v&15)]);piece.paste(tile,(tx*8,ty*8))
  if fx:piece=piece.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
  if fy:piece=piece.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
  mask=Image.new('L',piece.size);mask.putdata([255 if v else 0 for v in piece.get_flattened_data()]);out.paste(piece,(40+x,40+y),mask)
 return out.convert('RGBA')
def review(only=None):
 fs=json.loads((art/'frames.yaml').read_text())['frames']
 for anim in ('WALK','RUN'):
  for angle in ('up45','steep','inverted'):
   if only and angle!=only:continue
   group=[f for f in fs if f['animation']==anim and f['angle_set']==angle];pages=[]
   for scale in (1,4):
    sheet=Image.new('RGB',(160*scale,len(group)*100*scale),'#929292');d=ImageDraw.Draw(sheet)
    for n,f in enumerate(group):
     im=Image.open(art/f['file']).convert('RGBA');mica=Image.new('RGBA',(80,80));mica.alpha_composite(im,(40-f['hotspot'][0],40-f['hotspot'][1]));ref=native(anim,angle,n);page=Image.new('RGB',(160,100),'#929292');pd=ImageDraw.Draw(page)
     for x,v in ((0,mica),(80,ref)):
      page.paste(v,(x,0),v)
     pd.text((0,80),f['id'],fill='black');pd.text((80,90),'S1 phase '+str(n%(6 if anim=='WALK' else 4)+1),fill='black')
     sheet.paste(page.resize((160*scale,100*scale),Image.Resampling.NEAREST),(0,n*100*scale))
     if scale==4:pages.append(page.resize((640,400),Image.Resampling.NEAREST))
    sheet.save(dest/f'{anim}-{angle}-{scale}x.png')
   pages[0].save(dest/f'{anim}-{angle}.gif',save_all=True,append_images=pages[1:],duration=120 if anim=='WALK' else 80,loop=0,disposal=2)
if __name__=='__main__':review()
