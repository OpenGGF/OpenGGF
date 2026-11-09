#!/usr/bin/env python3
"""Purpose: reduce/review MICA whole-cycle experiment 2 without touching live art.
Inputs: --work external scratch with references, prompts, attempts, selection.json.
Origin: owner-requested leg-depth correction experiment, 2026-11-08.
Native references and regenerable preview images stay outside Git.
"""
import argparse, hashlib, html, importlib.util, json, shutil, subprocess
from pathlib import Path
from PIL import Image, ImageDraw
ap=argparse.ArgumentParser(description=__doc__)
ap.add_argument('--work',required=True,type=Path)
a=ap.parse_args(); R=a.work.resolve(); O=Path(__file__).resolve().parent
ROOT=Path.cwd(); A=ROOT/'examples/mica-and-talon/art/mica'
spec=importlib.util.spec_from_file_location('pipeline',ROOT/'tools/modding/playable_art/pipeline.py')
p=importlib.util.module_from_spec(spec);spec.loader.exec_module(p)
refs=json.loads((R/'references.json').read_text()); prompts=json.loads((R/'prompts.json').read_text()); sel=json.loads((R/'selection.json').read_text())
table=json.loads((O/'leg-layering.json').read_text()); colors=json.loads((A/'palette.yaml').read_text())['colors'];shutil.copy2(A/'palette.yaml',O/'palette.yaml')
ticks={'walk':5,'run':3,'roll':1,'spindash':1}; counts={'walk':6,'run':3,'roll':3,'spindash':3}
recipe={'palette':'palette.yaml','frames':[]}
prov={'tool':'Built-in image_gen.imagegen; exact model version unavailable. One still whole-cycle strip per call.', 'base':subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),'source_pins':{'s2disasm':subprocess.check_output(['git','rev-parse','HEAD:docs/s2disasm'],text=True).strip()},'cleanup':'Existing pipeline magenta removal (50), largest component, Lanczos fit to native box, fixed palette, inside ink. No repainting. Repeated mapping aliases share first drawing.','groups':{},'frames':[]}
reduced={}
for g,rr in refs.items():
 n=len(rr)
 for attempt in range(1,counts[g]+1):
  src=Image.open(R/g/f'attempt-{attempt}.png').convert('RGBA')
  for i,f in enumerate(rr):
   bounds=[i*src.width//n,0,(i+1)*src.width//n,src.height]
   clean=p.largest_component(p.remove_background(src.crop(bounds),[255,0,255],tolerance=50))
   reduced[g,attempt,i]=p.outline(p.indexed(clean.resize(f['size'],Image.Resampling.LANCZOS),colors))
 seen={}; decisions=sel[g]['frames']
 prov['groups'][g]={'attempt_count':counts[g],'attempt_prompts':prompts[g],'attempt_sha256':[hashlib.sha256((R/g/f'attempt-{i}.png').read_bytes()).hexdigest() for i in range(1,counts[g]+1)],'selection':sel[g],'ticks_per_step':ticks[g]}
 for i,f in enumerate(rr):
  key=f'{g}-{i:02}';attempt=decisions[i];im=reduced[g,attempt,i]
  alias=seen.get(f['mapping'])
  if alias: rows=next(x for x in recipe['frames'] if x['id']==alias)['native_pixels']['rows']
  else:
   rows=[''.join(format(im.getpixel((x,y)),'x') for x in range(im.width)) for y in range(im.height)];seen[f['mapping']]=key
  recipe['frames'].append(dict(id=key,animation=g.upper(),order=i,ticks=ticks[g],notes='Experiment 2 only; no live adoption.',native_pixels={'rows':rows,'anchor':f['hotspot']}))
  src=Image.open(R/g/f'attempt-{attempt}.png');bounds=[i*src.width//n,0,(i+1)*src.width//n,src.height]
  prov['frames'].append(dict(id=key,reference=f,attempt=attempt,source_cell=bounds,alias_of=alias,layering=table.get(g,[None]*n)[i]))
(O/'recipe.json').write_text(json.dumps(recipe,indent=2)+'\n');(O/'provenance.json').write_text(json.dumps(prov,indent=2)+'\n');p.build(O/'recipe.json')
manifest={f['id']:f for f in json.loads((O/'frames.yaml').read_text())['frames']}
N=Image.Resampling.NEAREST

def tile(im,hot):
 out=Image.new('RGBA',(80,80));out.alpha_composite(im.convert('RGBA'),(40-hot[0],40-hot[1]));return out

page='''<!doctype html><html><meta charset="utf-8"><title>MICA cycle experiment 2</title><style>body{background:#20272c;color:#f0eee6;font:16px system-ui;margin:28px}img{max-width:100%;image-rendering:pixelated}table{border-collapse:collapse}td,th{border:1px solid #65767b;padding:8px;text-align:left}p{max-width:1100px;line-height:1.5}h2{color:#7ee4bd}</style><h1>MICA / cycle experiment 2</h1><p>Native S2 at left; selected original MICA at right. Live frames untouched. WALK 6 additional strips; RUN/ROLL/SPINDASH 3 each. Near leg stays camera-side, lit and in front; far leg stays shaded behind, even when it moves forward. Frames numbered from zero. No native pixels committed.</p><p>Timing at 60Hz: WALK inertia $0400 = 5 ticks; RUN $0600 = 3; ROLL $0600 = 1; SPINDASH script = 1. GIF durations round cumulatively to centiseconds. These are selected native speeds, not universal periods. Slow GIFs use 150ms per step. Fitting to native boxes can squeeze the tail/hood. No Java, GGFP or runtime certification.</p>'''
for g,rr in refs.items():
 n=len(rr);pages=[];sheet=Image.new('RGB',(200*n,270),'#aaa');d=ImageDraw.Draw(sheet)
 for i,f in enumerate(rr):
  cur=manifest[f'{g}-{i:02}'];im=Image.open(O/cur['file']);native=Image.open(R/g/f'native-{i:02}.png')
  pair=Image.new('RGB',(160,96),'#aaa');pd=ImageDraw.Draw(pair)
  for j,(pic,hot) in enumerate(((native,f['hotspot']),(im,cur['hotspot']))):
   t=tile(pic,hot);pair.paste(t,(j*80,0),t);pd.text((j*80+2,82),['Native S2','MICA exp2'][j],fill='black')
  pages.append(pair.resize((640,384),N))
  t=tile(native,f['hotspot']);sheet.paste(t,(i*200+5,20),t)
  t=tile(im,cur['hotspot']);sheet.paste(t,(i*200+105,20),t)
  d.text((i*200+4,4),f'{i:02} native / MICA  a{sel[g]["frames"][i]}',fill='black')
  if g in table:
   ftable=table[g][i]
   for y,k in enumerate(('forward','near_lit_front','far_shaded_behind')):d.text((i*200+3,110+y*32),ftable[k],fill='black')
  else:d.text((i*200+3,110),f'Native mapping ${f["mapping"]:02X}',fill='black')
  d.text((i*200+3,220),sel[g]['frame_review'][i],fill='black')
 sheet.save(R/g/'annotated.png')
 # Every reduced attempt, native-sized with chosen cells outlined.
 attempts=Image.new('RGB',(n*100,counts[g]*115),'#aaa');ad=ImageDraw.Draw(attempts)
 for attempt in range(1,counts[g]+1):
  for i,f in enumerate(rr):
   t=tile(reduced[g,attempt,i],f['hotspot']);x=i*100;y=(attempt-1)*115;attempts.paste(t,(x,y+15),t)
   chosen=sel[g]['frames'][i]==attempt
   ad.text((x+2,y+2),f'a{attempt} f{i:02}'+(' CHOSEN' if chosen else ''),fill='black')
   if chosen:ad.rectangle((x,y,x+98,y+112),outline='#007347',width=3)
 attempts.resize((attempts.width*2,attempts.height*2),N).save(R/g/'all-attempts.png')
 elapsed=0;dur=[]
 for _ in pages:
  before=round(elapsed*100/60);elapsed+=ticks[g];dur.append((round(elapsed*100/60)-before)*10)
 for name,duration in [('paired',dur),('slow',150)]:pages[0].save(R/g/f'{name}.gif',save_all=True,append_images=pages[1:],duration=duration,loop=0,disposal=2)
 page+=f'<h2>{g.upper()}</h2><p>{html.escape(sel[g]["review"])}</p><img src="{g}/paired.gif"><img src="{g}/slow.gif"><h3>Native and selected phases / depth annotations</h3><img src="{g}/annotated.png"><h3>All reduced attempts / chosen cells outlined</h3><img src="{g}/all-attempts.png">'
 if g in table:
  page+='<table><tr><th>Frame</th><th>Forward</th><th>Near: lit/front</th><th>Far: shaded/behind</th><th>Review</th></tr>'
  for i,f in enumerate(table[g]):page+='<tr>'+''.join(f'<td>{html.escape(str(v))}</td>' for v in [i,f['forward'],f['near_lit_front'],f['far_shaded_behind'],sel[g]['frame_review'][i]])+'</tr>'
  page+='</table>'
 for i in range(1,counts[g]+1):
  raw=Image.open(R/g/f'attempt-{i}.png');raw.thumbnail((1200,450));raw.save(R/g/f'attempt-{i}-preview.png')
  page+=f'<details><summary>Raw attempt {i}</summary><a href="{g}/attempt-{i}.png">Full original</a><img src="{g}/attempt-{i}-preview.png"></details>'
page+='</html>'; (R/'review.html').write_text(page)
print('Review:',R/'review.html')
