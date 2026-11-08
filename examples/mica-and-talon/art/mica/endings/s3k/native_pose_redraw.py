#!/usr/bin/env python3
"""Purpose: reduce owner-directed GPT Image native-pose redraws and review them.
Inputs: disposable native 8x references, raw GPT images and exact prompts in /tmp.
Origin: MICA S3K ending redraw lane, 2026-11-08. No native pixels are retained.
"""
import argparse, hashlib, importlib.util, json
from pathlib import Path
from PIL import Image, ImageDraw, ImageChops
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[5]
def module(name,path):
 s=importlib.util.spec_from_file_location(name,path);m=importlib.util.module_from_spec(s);s.loader.exec_module(m);return m

def reduce(review,group):
 review=review.absolute()
 if not review.is_relative_to(Path('/tmp')):raise ValueError('Review inputs must remain in /tmp')
 b=module('ending',HERE/'build.py');p=module('pipeline',ROOT/'tools/modding/playable_art/pipeline.py')
 frames=json.loads((HERE/'s3k.yaml').read_text())['frames']
 prompts={f['id']:f for f in json.loads((review/'prompts.json').read_text())}
 dst=HERE/'native-pose.json';source=json.loads(dst.read_text()) if dst.exists() else dict(tool='Built-in GPT Image; latest exposed (model version not returned)',frames={})
 for f in frames:
  if f['group']!=group:continue
  key=f['id'];raw=review/'raw'/f'{key}.png';clean=p.largest_component(Image.open(raw).convert('RGBA'))
  colors=b.GOLD['cycles']['s3k']['frames'][0] if f['form']=='gilded' else b.COLORS
  reduced=p.indexed(clean.resize(tuple(f['dimensions']),Image.Resampling.LANCZOS),colors)
  # Restrict the drawing to the exact union of native mapping pieces.
  mask=Image.new('1',reduced.size);d=ImageDraw.Draw(mask);hx,hy=f['hotspot']
  for x,y,w,h,*_ in f['pieces']:d.rectangle((x+hx,y+hy,x+hx+w*8-1,y+hy+h*8-1),fill=1)
  # Fit the complete silhouette inside the piece union; never chop off scales,
  # hands or boots to satisfy layout. Prefer the largest fit near canvas centre.
  full=reduced.copy(); found=False
  for percent in range(100,29,-2):
   size=tuple(max(1,round(v*percent/100)) for v in full.size)
   small=p.indexed(clean.resize(size,Image.Resampling.LANCZOS),colors)
   alpha=small.point(lambda v:255 if v else 0,mode='L')
   positions=[(x,y) for y in range(full.height-size[1]+1) for x in range(full.width-size[0]+1)]
   positions.sort(key=lambda xy:(xy[0]-(full.width-size[0])/2)**2+(xy[1]-(full.height-size[1])/2)**2)
   for x,y in positions:
    if ImageChops.subtract(alpha,mask.crop((x,y,x+size[0],y+size[1])).convert('L')).getbbox() is None:
     reduced=b.b.indexed(full.size,colors);reduced.paste(small,(x,y));found=True;break
   if found:break
  if not found:raise ValueError(f'Cannot fit complete sprite: {key}')
  if group!='passengers':p.outline(reduced)
  reduced.save(HERE/f['file'],bits=4,transparency=0)
  if f.get('gilded_file'):
   gold=reduced.copy();gold.putpalette(sum(b.GOLD['cycles']['s3k']['frames'][0],[])+[0]*720);gold.save(HERE/f['gilded_file'],bits=4,transparency=0)
  source['frames'][key]=dict(prompt=prompts[key]['prompt'],raw_sha256=hashlib.sha256(raw.read_bytes()).hexdigest(),rows=[''.join(format(reduced.getpixel((x,y)),'x') for x in range(reduced.width)) for y in range(reduced.height)],reduction='Largest alpha component at threshold 128; Lanczos to manifest canvas; nearest fixed palette; largest complete silhouette fit inside native piece union; inside outline (except tiny passengers).',reference='native_pose.py piece semantics; build.py reference decoder; pinned docs/skdisasm',coverage_note='Native Super selectors 0/1 scrambled; matching normal pose supplied as additional guide.' if key in ('super_pose-00','super_pose-01') else '')
  # Four columns share the unchanged native canvas and engine hotspot.
  cw=max(144,reduced.width+8);ch=max(150,reduced.height+20);sheet=Image.new('RGB',(cw*4,ch),'#929292');d=ImageDraw.Draw(sheet)
  for i,(im,label) in enumerate([(Image.open(review/'native'/f'{key}.png').convert('RGBA'),'native (8x input)'),(clean.resize(reduced.size,Image.Resampling.LANCZOS),'raw GPT'),(reduced.convert('RGBA'),'reduced'),(Image.open(review/'previous'/f['file']).convert('RGBA'),'previous MICA')]):
   sheet.paste(im,(i*cw,0),im);d.text((i*cw,ch-14),label,fill='black')
  for scale in (1,4):sheet.resize((sheet.width*scale,sheet.height*scale),Image.Resampling.NEAREST).save(review/f'{key}-{scale}x.png')
 dst.write_text(json.dumps(source,indent=2)+'\n')
 b.verify([f for f in frames if f['group']==group])
if __name__=='__main__':
 a=argparse.ArgumentParser(description=__doc__);a.add_argument('--review',type=Path,required=True);a.add_argument('--group',required=True);v=a.parse_args();reduce(v.review,v.group)
