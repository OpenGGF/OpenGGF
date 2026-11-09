#!/usr/bin/env python3
"""Purpose: slice whole-cycle MICA strips, reduce with shared pipeline, and review.
Inputs: --work scratch folder containing prompts.json, references.json and attempts;
run from repo root, requires Pillow and optional S2 disassembly for reference inputs.
Origin: owner-directed whole-cycle vs per-frame experiment, 2026-11-08.
Only original MICA indexed rows/frames/provenance go in Git; native/raw remain scratch.
"""
import argparse,hashlib,importlib.util,json,shutil,subprocess
from pathlib import Path
from PIL import Image,ImageDraw
ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('--work',type=Path,required=True);args=ap.parse_args()
ROOT=Path.cwd();R=args.work.resolve();O=R/'results';O.mkdir(exist_ok=True)
A=ROOT/'examples/mica-and-talon/art/mica';N=Image.Resampling.NEAREST
spec=importlib.util.spec_from_file_location('p',ROOT/'tools/modding/playable_art/pipeline.py');p=importlib.util.module_from_spec(spec);spec.loader.exec_module(p)
colors=json.loads((A/'palette.yaml').read_text())['colors'];shutil.copy2(A/'palette.yaml',O/'palette.yaml')
refs=json.loads((R/'references.json').read_text());prompts=json.loads((R/'prompts.json').read_text());old=json.loads((A/'frames.yaml').read_text());old={f['id']:f for f in old['frames']}
selection={'walk':2,'run':3,'roll':3,'spindash':3};ticks={'walk':5,'run':3,'roll':1,'spindash':1}
notes={
'walk':'Attempt 2: steady head/hood/tail. Arm swing changes; near/far boots swap shading in phases 3/7, but phases 4/8 remain too alike. Foot alternation is partially readable, not cleanly solved. Attempt 1 had weaker near/far differentiation; 3 retained similar limb ambiguity.',
'run':'Attempt 3: crispest pixels, stable hood and tail; four boot arcs form a coherent wheel. Phases 2/4 remain very similar; white boot-cuff accents persist despite prompt. Attempt 1 has broken transparent/background artifacts; 2 softer and more illustrative.',
'roll':'Attempt 3: four readable quarter-turn tucked poses with a jade blur. Blur orientation drift is normalized by aliasing the first blur; no redraw or rotation of spin poses. Attempt 1 has finer noise, 2 softer clusters. Shape/face still varies slightly between quarter turns.',
'spindash':'Attempt 3: closed shell throughout, closer to native reference than the seated face/boots in attempts 1/2. Five scale patterns vary, but the spin is subtle and looks like texture shimmer. Shared interleave aliases the first shell. Not a strong sense of charging acceleration.'}
recipe={'palette':'palette.yaml','frames':[]};provenance={'tool':'Built-in image_gen.imagegen; single image_url result; no animated-output, sequence-output or model/version selector exposed. Used one horizontal strip per attempt, not an animated model output. Exact model/version unavailable.','base':subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),'source_pins':{'s2disasm':subprocess.check_output(['git','rev-parse','HEAD:docs/s2disasm'],text=True).strip()},'concept':'examples/mica-and-talon/concept/mica.png','cleanup':'Edge-connected magenta key removal tolerance 50; largest connected component; Lanczos fit to native tight box; fixed 15 opaque colours; inside ink outline. Shared native blur/interleave mappings reuse first reduced occurrence. No limb redrawing, bitmap rotation, or head transplant. Geometry fitting is anisotropic and may affect proportions.','groups':{},'frames':[]}
for g,records in refs.items():
 attempt=selection[g];raw=R/g/f'attempt-{attempt}.png';source=Image.open(raw).convert('RGBA');count=len(records);seen={};seq=[];provenance['groups'][g]={'selected_attempt':attempt,'attempt_prompts':prompts[g],'attempt_sha256':[hashlib.sha256((R/g/f'attempt-{i}.png').read_bytes()).hexdigest() for i in (1,2,3)],'raw_file':str(raw).replace(str(Path.home()), '$HOME'),'review':notes[g],'ticks_per_step':ticks[g],'inertia_hex':{'walk':'0400','run':'0600','roll':'0600','spindash':None}[g]}
 for i,f in enumerate(records):
  key=f'{g}-{i:02}';bounds=[i*source.width//count,0,(i+1)*source.width//count,source.height]
  if f['mapping'] not in seen:
   cell=p.remove_background(source.crop(bounds),[255,0,255],tolerance=50)
   clean=p.largest_component(cell);im=p.outline(p.indexed(clean.resize(f['size'],Image.Resampling.LANCZOS),colors));seen[f['mapping']]=key
   rows=[''.join(format(im.getpixel((x,y)),'x') for x in range(im.width)) for y in range(im.height)]
   recipe['frames'].append(dict(id=key,animation=g.upper(),order=i,ticks=ticks[g],notes='Whole-cycle strip experiment; not live art.',native_pixels={'rows':rows,'anchor':f['hotspot']}))
  else:
   first=next(x for x in recipe['frames'] if x['id']==seen[f['mapping']]);recipe['frames'].append(dict(first,id=key,order=i))
  seq.append(key);provenance['frames'].append(dict(id=key,reference=f,source_cell=bounds,alias_of=seen[f['mapping']] if seen[f['mapping']]!=key else None))
(O/'recipe.json').write_text(json.dumps(recipe,indent=2)+'\n');(O/'provenance.json').write_text(json.dumps(provenance,indent=2)+'\n');p.build(O/'recipe.json')
# Disposable engine-centre-aligned previews: native, cycle, optional previous art.
manifest=json.loads((O/'frames.yaml').read_text());mf={f['id']:f for f in manifest['frames']}
def draw_frame(path,hot):
 tile=Image.new('RGBA',(80,80));tile.alpha_composite(Image.open(path).convert('RGBA'),(40-hot[0],40-hot[1]));return tile
for g,records in refs.items():
 pages=[];sheet=Image.new('RGB',(80*len(records),260),'#929292');d=ImageDraw.Draw(sheet)
 for i,ref in enumerate(records):
  key=f'{g}-{i:02}';current=mf[key];pairs=[(R/g/f'native-{i:02}.png',ref['hotspot'],'Native S2'),(O/current['file'],current['hotspot'],'Cycle MICA')]
  if g in ('walk','run'):pairs.append((A/old[key]['file'],old[key]['hotspot'],'Earlier per-frame'))
  page=Image.new('RGB',(80*len(pairs),96),'#929292');pd=ImageDraw.Draw(page)
  for j,(path,hot,label) in enumerate(pairs):
   tile=draw_frame(path,hot);page.paste(tile,(j*80,0),tile);pd.text((j*80+2,82),label,fill='black');sheet.paste(tile,(i*80,j*80),tile)
  d.text((i*80+2,242),f'{i+1}: ${ref["mapping"]:02X}',fill='black');pages.append(page.resize((page.width*4,384),N))
 sheet.save(R/g/'contact-1x.png');sheet.resize((sheet.width*4,1040),N).save(R/g/'contact-4x.png')
 elapsed=0;dur=[]
 for _ in pages:
  before=round(elapsed*100/60);elapsed+=ticks[g];dur.append((round(elapsed*100/60)-before)*10)
 pages[0].save(R/g/'paired.gif',save_all=True,append_images=pages[1:],duration=dur,loop=0,disposal=2)
 pages[0].save(R/g/'slow.gif',save_all=True,append_images=pages[1:],duration=150,loop=0,disposal=2)
html='''<!doctype html><html><head><meta charset="utf-8"><title>MICA whole-cycle experiment</title><style>body{font:16px system-ui;background:#171c22;color:#e9efed;margin:28px;max-width:1300px}h1,h2{color:#7fe0bd}figure{margin:0}img{max-width:100%;image-rendering:pixelated;background:#929292}.g{display:grid;grid-template-columns:1fr 1fr;gap:16px}figcaption,p{line-height:1.5;color:#c9d2d0}a{color:#8fe5cb}details{margin:16px 0}summary{cursor:pointer}</style></head><body><h1>MICA / whole-cycle strips</h1><p>Single-image tool: three attempts per animation. No animated output or exact model version exposed. Selected WALK 2, RUN 3, ROLL 3, SPINDASH 3. Native S2 at left, whole-cycle MICA next; WALK/RUN also show the earlier per-frame branch art at right. All comparison assets remain outside Git; live art unchanged.</p><p>Steady native timing at 60 Hz: WALK inertia $0400 = 5 ticks/step; RUN $0600 = 3; ROLL $0600 = 1; SPINDASH = 1. GIF centiseconds rounded cumulatively (10/20 ms for one tick). These are native speed choices, not a universal movement period. Slow views are explicitly 150 ms/step. Sources: SonAni_Walk/Run/Roll/Spindash and SAnim_WalkRun/SAnim_Roll, pinned S2 disassembly.</p><p>Reduction fits each alpha crop to its native tight box, preserving the native hotspot. This can squeeze the larger MICA hood/tail; indexed sprites are candidates for comparison, not integrated game art or animation-parity certification.</p>'''
for g in refs:
 html+=f'<h2>{g.upper()}</h2><p>{notes[g]}</p><div class="g"><figure><img src="{g}/paired.gif"><figcaption>Native timing; shared engine-centre anchor.</figcaption></figure><figure><img src="{g}/slow.gif"><figcaption>Slow inspection: 150 ms per step.</figcaption></figure></div><details><summary>1× / 4× phase sheets (native / cycle / earlier)</summary><img src="{g}/contact-1x.png"><img src="{g}/contact-4x.png"></details>'
 for i in (selection[g],*([x for x in (1,2,3) if x!=selection[g]])):
  html+=f'<details {"open" if i==selection[g] else ""}><summary>Raw strip attempt {i} {"— SELECTED" if i==selection[g] else "— rejected"}</summary><img src="{g}/attempt-{i}.png"></details>'
html+='<p>Whole-strip generation stabilizes identity, head scale and palette relative to the earlier per-frame WALK/RUN art, but does not guarantee gait phase semantics. WALK near/far alternation remains ambiguous; RUN has a coherent wheel but similar opposite phases; ROLL is the clearest result; SPINDASH remains weak texture motion. This experiment cannot establish superiority over the other lane’s unfinished regeneration.</p></body></html>'
(R/'review.html').write_text(html)
print('Review:',R/'review.html')
