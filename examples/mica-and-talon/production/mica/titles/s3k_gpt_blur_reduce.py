#!/usr/bin/env python3
"""Purpose: reduce fresh GPT heavy-blur references and compare to deterministic art.
Inputs: scratch gpt-small.png/gpt-7..10.png and approved sharp planes; native exports.
Origin: owner heavy-blur retry, 2026-11-10. NumPy/Pillow; all outputs stay scratch.
The generated8/10 sheets include an unwanted Sonic half: only the MICA half is used.
"""
from pathlib import Path
from PIL import Image
import argparse,numpy as np
from s3k_heavy_blur import quantize,native_reference
parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--scratch',type=Path,required=True);parser.add_argument('--native',type=Path,required=True);args=parser.parse_args();p=args.scratch;n=args.native;pal=Image.open(p/'sharp/13-left.png').getpalette();(p/'gpt').mkdir(exist_ok=True)
def crop_subject(a):
 rgb=np.array(a.convert('RGB'));active=rgb.max(axis=2)>12
 pending={(int(x),int(y)) for y,x in np.argwhere(active)};largest=set()
 while pending:
  seed=pending.pop();component={seed};todo=[seed]
  while todo:
   x,y=todo.pop()
   for pt in ((x-1,y),(x+1,y),(x,y-1),(x,y+1)):
    if pt in pending:pending.remove(pt);component.add(pt);todo.append(pt)
  if len(component)>len(largest):largest=component
 mask=np.zeros(active.shape,np.uint8)
 for x,y in largest:mask[y,x]=255
 return a.crop(Image.fromarray(mask).getbbox())
for f in range(2,11):
 if f<=6:
  src=Image.open(p/'gpt-small.png').convert('RGB');c=f-2;im=src.crop((round(c*src.width/5),0,round((c+1)*src.width/5),src.height//2));im=crop_subject(im);native=Image.open(n/f's3k-mascot-{f}.png');box=native.getbbox()
  if f==2:box=(158,43,170,59) # one-pixel glint padding; native centre remains(164,51)
 elif f==7:
  src=Image.open(p/'gpt-7.png').convert('RGB');im=crop_subject(src.crop((0,0,src.width//2,src.height)));box=(148,0,254,132)
 elif f in (8,9):
  src=Image.open(p/f'gpt-{f}.png').convert('RGB');im=src.crop((src.width//2 if f==8 else 0,0,src.width if f==8 else src.width//2,src.height));box=(0,0,320,224)
 else:
  src=Image.open(p/'gpt-10.png').convert('RGB');im=crop_subject(src.crop((src.width//2,0,src.width,src.height)));box=(69,0,251,224)
 im=im.resize((box[2]-box[0],box[3]-box[1]),Image.Resampling.LANCZOS);canvas=Image.new('RGB',(320,224));canvas.paste(im,box[:2]);q=quantize(np.array(canvas,dtype=float),pal,list(range(1,8))+[11,12,13] if f<=7 else None)
 if f==7:
  logo=np.array(Image.open(n/'s3k-plane-6.png').convert('RGBA'))[:,:,3]>0;pixels=np.array(q);pixels[80:][logo[80:]]=0;q=Image.fromarray(pixels,'P');q.putpalette(pal);q.info['transparency']=0
 q.save(p/f'gpt/{f}.png',bits=4)
 # Actual reduction candidates, unlabelled native/GPT/deterministic at1x and3x.
 native=native_reference(f,n)
 out=Image.new('RGB',(960,224));out.paste(native,(0,0),native);out.paste(q.convert('RGBA'),(320,0),q.convert('RGBA'));b=Image.open(p/f'deterministic/{f}.png').convert('RGBA');out.paste(b,(640,0),b);out.save(p/f'candidates-{f}-1x.png');out.resize((2880,672),Image.Resampling.NEAREST).save(p/f'candidates-{f}-3x.png')
