#!/usr/bin/env python3
"""Purpose: reproducible S3K title portrait/eye/glove reduction, no native RGB assets.
Inputs: selected fresh GPT Image half-sheet, fresh two-row patch sheet, scratch output.
Origin: owner S3K title revision, 2026-11-10. Run from repository root.
"""
from pathlib import Path
from PIL import Image,ImageDraw
import argparse,json
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--body',type=Path,required=True)
parser.add_argument('--patches',type=Path,required=True)
parser.add_argument('--scratch',type=Path,required=True)
args=parser.parse_args();p=args.scratch;p.mkdir(parents=True,exist_ok=True)
r=Path('examples/mica-and-talon/art/mica/titles')
cg=json.loads((r/'s3k-palette.json').read_text())['colors']
cg[14]=[219,109,109];cg[15]=[255,146,146]
def cut(a,rect):
 a=a.crop(rect).convert('RGBA');a.putdata([(rr,g,b,0 if rr>170 and b>140 and g<110 else 255) for rr,g,b,_ in a.getdata()]);return a.crop(a.getbbox())
def quant(a,colors,dither=False):
 cache={};out=[]
 for idx,(rr,g,b,alpha) in enumerate(a.getdata()):
  if alpha<128:out.append(0);continue
  rgb=(rr,g,b)
  if rgb not in cache:
   near=sorted(range(1,16),key=lambda k:sum((v-col)**2 for v,col in zip(rgb,colors[k])))[:2]
   axis=[v-u for u,v in zip(colors[near[0]],colors[near[1]])];weight=max(0,min(1,sum((v-u)*d for v,u,d in zip(rgb,colors[near[0]],axis))/max(1,sum(d*d for d in axis))))
   cache[rgb]=(*near,weight)
  k,j,weight=cache[rgb];x=idx%a.width;y=idx//a.width;threshold=((.125,.625),(.875,.375))[y%2][x%2];out.append(j if dither and weight>threshold else k)
 q=Image.new('P',a.size);q.putdata(out);q.putpalette(sum(colors,[])+[0]*720);q.info['transparency']=0;return q
a=cut(Image.open(args.body),(0,0,Image.open(args.body).width,Image.open(args.body).height));a=a.resize((143,138),Image.Resampling.LANCZOS);q=quant(a,cg,True);full=Image.new('P',(320,224));full.putpalette(q.getpalette());full.info['transparency']=0;full.paste(q,(86,4));full.save(p/'full.png');full.convert('RGBA').resize((1280,896),Image.Resampling.NEAREST).save(p/'full-4x.png');print('area',sum(v>0 for v in full.getdata()))
# Restrict pink to pink material; cream shadows must not quantize into the ear ramp.
# Requantize all art against a material-constrained distance palette.
def quant_material(a):
 out=quant(a,cg,True)
 safe=cg[:14]+[cg[9],cg[10]]
 cream=quant(a,safe,True)
 for y in range(a.height):
  for x in range(a.width):
   rr,g,b,alpha=a.getpixel((x,y))
   if out.getpixel((x,y)) in (14,15) and not (rr>g*1.3 and b>g*.8):out.putpixel((x,y),[9,10][cream.getpixel((x,y))-14] if cream.getpixel((x,y))>=14 else cream.getpixel((x,y)))
 return out
q=quant_material(a);full.paste(q,(86,4));full.save(p/'full.png')
# All planes share the corrected local ear ramp; prior approved approach poses retained.
for f in r.glob('s3k-*.png'):
 im=Image.open(f);pal=im.getpalette();pal[42:48]=cg[14]+cg[15];im.putpalette(pal);im.save(f,bits=4)
(r/'s3k-palette.json').write_text(json.dumps({'colors':cg,'style':'Baked 3D; private static title palette. Coral-pink ear ramp14/15, cream rim8..10; no native cycle writes this row.'},indent=2)+'\n')
# Glove ownership is anatomical connected greys, expanded only to adjoining ink.
owned={(x,y) for y in range(62,119) for x in range(162,213) if full.getpixel((x,y)) in (11,12,13)}
for x,y in list(owned):
 for xx,yy in [(x-1,y),(x+1,y),(x,y-1),(x,y+1)]:
  if full.getpixel((xx,yy))==1:owned.add((xx,yy))
rest=full.crop((160,60,216,120));rest.paste(0,(0,0,56,60))
for x,y in owned:rest.putpixel((x-160,y-60),full.getpixel((x,y)));full.putpixel((x,y),0)
rest.info['transparency']=0;rest.save(r/'s3k-sprite-4.png',bits=4)
src=Image.open(args.patches)
# Fresh takeA hand patches: fixed fist/wrist registration and native-sized envelopes.
for i in (0,1):
 im=cut(src,(round(i*src.width/4),0,round((i+1)*src.width/4),src.height//2));im=quant_material(im.resize((39,49),Image.Resampling.LANCZOS));canvas=rest.copy();canvas.paste(0,(0,0,56,60));canvas.paste(im,(5,5));canvas.info['transparency']=0;canvas.save(r/f's3k-sprite-{i}.png',bits=4)
# Eye ownership derives only from the new plane's own eye-white/pupil connected region.
portrait=Image.open(p/'full.png');eye_mask=Image.new('1',(320,224));ImageDraw.Draw(eye_mask).polygon([(111,77),(116,64),(127,53),(141,47),(155,46),(159,52),(156,67),(148,78),(128,83)],fill=1)
for i in (2,3):
 patch=portrait.crop((108,40,164,88));im=cut(src,(round(i*src.width/4),0,round((i+1)*src.width/4),src.height//2));im=quant_material(im.resize((50,43),Image.Resampling.LANCZOS))
 for yy in range(43):
  for xx in range(50):
   x,y=111+xx,41+yy
   if eye_mask.getpixel((x,y)) and im.getpixel((xx,yy)):
    patch.putpixel((x-108,y-40),im.getpixel((xx,yy)))
 patch.info['transparency']=0;patch.save(r/f's3k-sprite-{i}.png',bits=4)
for half,b in [('left',(0,0,160,224)),('right',(160,0,320,224))]:full.crop(b).save(r/f's3k-plane-13-{half}.png',bits=4)
f=Path('examples/mica-and-talon/src/main/java/mica/TitlePresentation.java');s=f.read_text();s=s.replace('character.equals("mica") ? 40 : 24,character.equals("mica") ? 29 : 36','character.equals("mica") ? 40 : 24,character.equals("mica") ? 32 : 36').replace('character.equals("mica") ? -8 : -16,character.equals("mica") ? 32 : 40','character.equals("mica") ? 12 : -16,character.equals("mica") ? 32 : 40');f.write_text(s)
