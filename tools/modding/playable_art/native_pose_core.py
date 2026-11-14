#!/usr/bin/env python3
"""Purpose: reduce original GPT Image core-pose redraws and retain native anchors.
Inputs: native_pose.py --core /tmp exports, per-frame raw GPT PNGs and prompts,
previous-manifest.json plus previous/ frame snapshots, approved pilot review.
Origin: owner-directed MICA W7 native-pose core lane, 2026-11-08.
Only original MICA rows/provenance enter Git. Ordinary pipeline.py rebuilds do
not require these disposable native/raw inputs. Capture snapshots before edits.
Use --review /tmp/<review> [--pilot-review /tmp/<pilot>] wait walk ...
"""
import argparse,json,sys,importlib.util,hashlib,shutil,subprocess
from pathlib import Path
from PIL import Image,ImageDraw
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--review',type=Path,required=True)
parser.add_argument('--pilot-review',type=Path,default=Path('/tmp/mica-native-pose-20261108'))
parser.add_argument('groups',nargs='+')
args=parser.parse_args()
ROOT=Path(__file__).resolve().parents[3];A=ROOT/'examples/mica-and-talon/art/mica';R=args.review.resolve();N=Image.Resampling.NEAREST
if not R.is_relative_to(Path('/tmp')) or not args.pilot_review.resolve().is_relative_to(Path('/tmp')):
 raise ValueError('Native/raw reviews must stay under /tmp')
s=importlib.util.spec_from_file_location('pipeline',ROOT/'tools/modding/playable_art/pipeline.py');p=importlib.util.module_from_spec(s);s.loader.exec_module(p)
old=json.loads((R/'previous-manifest.json').read_text());refs={f['id']:f for f in json.loads((R/'native/references.json').read_text())};colors=json.loads((A/'palette.yaml').read_text())['colors']
for i in range(4):
 refs[f'stop-{i:02}']=dict(refs[f'skid-{i:02}'],id=f'stop-{i:02}')
prov=A/'native-pose-core.json'
def canvas(im,hot):
 out=Image.new('RGBA',(80,80));out.alpha_composite(im.convert('RGBA'),(40-hot[0],40-hot[1]));return out

def apply(group):
 recipe=json.loads((A/'recipe.json').read_text());meta=json.loads(prov.read_text()) if prov.exists() else {'method':'Built-in GPT Image native-pose redraw; latest exposed model; exact version/billing unavailable. Native boxes and hotspots per owner correction; shared palette indices unchanged.','frames':{}}
 for f in old['frames']:
  key=f['id']
  if f['animation']!=group.upper() or f['angle_set']!='flat':continue
  target=Image.open(R/'previous'/f'{key}.png');geometry=refs['roll-02'] if key=='homing_dive-02' else refs[key];size=tuple(geometry['size']);old_hot=f['hotspot'];f=dict(f,hotspot=geometry['hotspot'])
  if (R/'raw'/f'{key}.png').exists():
   raw=R/'raw'/f'{key}.png';clean=p.largest_component(p.remove_background(Image.open(raw)));im=p.outline(p.indexed(clean.resize(size,Image.Resampling.LANCZOS),colors));prompt=json.loads((R/'prompts.json').read_text())[key]
  else:
   raw=args.pilot_review/'raw'/f'{key}.png';clean=p.largest_component(p.remove_background(Image.open(raw)));im=Image.open(A/'native-pose-pilot/frames'/f'{key}.png').resize(size,N);im=p.outline(im);prompt=next(x['prompt'] for x in json.loads((A/'native-pose-pilot/provenance.json').read_text())['frames'] if x['id']==key)
  # Threshold may shed outer alpha: retain full authored compatibility box.
  if im.getbbox()!=(0,0,*size):im=im.crop(im.getbbox()).resize(size,N);im=p.outline(im)
  if group=='roll':
   im=Image.open(A/'native-pose-pilot/frames/roll-00.png').rotate(-90*int(key[-2:]),resample=N,expand=True).resize(size,N)
   im=p.outline(im)
  if key=='spring-01':
   head=Image.open(A/'frames/spring-00.png')
   im.paste(head.crop((0,0,head.width,25)),(0,0));im=p.outline(im)
  if key=='homing_dive-00':
   # Tuck the small leading muzzle cluster behind the generated jade shell.
   for y in range(im.height//2,im.height):
    for x in range(im.width//2,im.width):
     if im.getpixel((x,y)) in (7,8,9,12,13,15):im.putpixel((x,y),4 if (x+y)%4 else 3)
   im=p.outline(im)
  if group=='wait' and key!='wait-00':
   head=Image.open(A/'frames/wait-00.png')
   # Shared generated head stabilizes hood/eye scale; body keeps native fidget.
   im.paste(head.crop((0,0,head.width,27)).resize((size[0],27),N),(0,0))
   if key=='wait-02':
    for y in range(10,14):
     for x in range(max(0,size[0]-12),size[0]-6):
      if im.getpixel((x,y)) in (12,13,15):im.putpixel((x,y),9 if y!=12 else 1)
   im=p.outline(im)
  im.save(A/f['file'],transparency=0,optimize=True,bits=4)
  rf=next(x for x in recipe['frames'] if x['id']==key)
  rf.clear();rf.update(id=key,animation=f['animation'],order=f['order'],angle_set=f['angle_set'],ticks=f['duration_ticks'],notes=f['notes'],native_pixels={'rows':[''.join(format(im.getpixel((x,y)),'x') for x in range(im.width)) for y in range(im.height)],'anchor':f['hotspot']})
  meta['frames'][key]={'prompt':prompt,'raw_sha256':hashlib.sha256(raw.read_bytes()).hexdigest(),'reference':refs[key],'geometry_reference':geometry,'native_size':list(size),'previous_size':list(target.size),'previous_hotspot':old_hot,'hotspot':f['hotspot'],'source':'approved pilot adoption' if raw.parent!=R/'raw' else 'new built-in GPT Image','cleanup':('shared generated head/hood; blink eye ink' if group=='wait' else 'accepted pilot roll-00 rotated in exact quarter turns with expanded bounds' if group=='roll' else 'spring-derived downward shell in native roll envelope for cycle consistency' if key=='homing_dive-02' else 'leading muzzle tucked behind jade shell' if key=='homing_dive-00' else 'shared spring-00 generated head/hood; lower launch phase retained' if key=='spring-01' else 'connected alpha; shared palette; inside ink outline')}
  page=Image.new('RGB',(320,100),'#929292');d=ImageDraw.Draw(page)
  native=Image.open(R/'native'/f'{key}.png')
  for col,(img,hot,label) in enumerate([(native,refs[key]['hotspot'],'native 8x ref'),(clean.resize(size,Image.Resampling.LANCZOS),f['hotspot'],'raw GPT'),(im,f['hotspot'],'reduced'),(target,old_hot,'previous')]):
   tile=canvas(img,hot);page.paste(tile,(col*80,0),tile);d.text((col*80,82),label,fill='black')
  d.text((0,91),key,fill='black')
  for scale in (1,4):page.resize((320*scale,100*scale),N).save(R/f'{key}-{scale}x.png')
 meta['source_pins']=json.loads((R/'native/source-pins.json').read_text())
 meta['concept']='examples/mica-and-talon/concept/mica.png'
 for entry in meta['frames'].values():
  if 'native_size' in entry:
   before=entry['previous_size'];after=entry['native_size']
   entry['material_box_change']=max(abs(a-b) for a,b in zip(before,after))>=4 or ((before[0]>before[1]) != (after[0]>after[1]))
 (A/'recipe.json').write_text(json.dumps(recipe,indent=2)+'\n');prov.write_text(json.dumps(meta,indent=2)+'\n')
 meta['method']='Built-in GPT Image native-pose redraw; native boxes/hotspots per owner correction, fixed shared palette; exact model/billing unavailable.'
 prov.write_text(json.dumps(meta,indent=2)+'\n')
 # STOP is an exact artwork alias, matching the original handoff.
 if group=='skid':
  for i in range(4):
   src=f'skid-{i:02}';dst=f'stop-{i:02}';shutil.copy2(A/'frames'/f'{src}.png',A/'frames'/f'{dst}.png');rf=next(x for x in recipe['frames'] if x['id']==dst);sf=next(x for x in recipe['frames'] if x['id']==src);rf.clear();rf.update(sf,id=dst,animation='STOP');meta['frames'][dst]={'alias':src}
   page=Image.open(R/f'{src}-1x.png');d=ImageDraw.Draw(page);d.rectangle((0,91,80,99),fill='#929292');d.text((0,91),dst,fill='black')
   for scale in (1,4):page.resize((320*scale,100*scale),N).save(R/f'{dst}-{scale}x.png')
  (A/'recipe.json').write_text(json.dumps(recipe,indent=2)+'\n');prov.write_text(json.dumps(meta,indent=2)+'\n')
 manifest=json.loads((A/'frames.yaml').read_text())
 for f in manifest['frames']:
  if f['id'] in meta['frames']:
   source=meta['frames'][f['id']];source=meta['frames'][source['alias']] if 'alias' in source else source
   f['hotspot']=source['hotspot']
 (A/'frames.yaml').write_text(json.dumps(manifest,indent=2)+'\n')
 subprocess.run(['python3',str(ROOT/'tools/modding/playable_art/assemble.py'),str(A),str(ROOT/'examples/mica-and-talon/src/main/mod')],check=True)
 review(group)
 if group=='skid':review('stop')
def review(group):
 fs=[dict(f,hotspot=(refs['roll-02'] if f['id']=='homing_dive-02' else refs[f['id']])['hotspot']) for f in old['frames'] if f['animation']==group.upper() and f['angle_set']=='flat']
 for scale in (1,4):
  sheet=Image.new('RGB',(320*scale,100*scale*len(fs)))
  for i,f in enumerate(fs):sheet.paste(Image.open(R/f"{f['id']}-{scale}x.png"),(0,i*100*scale))
  sheet.save(R/f'{group}-review-{scale}x.png')
 packed=json.loads((ROOT/'examples/mica-and-talon/src/main/mod/mica-sheet.yaml').read_text())['animations'][group.upper()];keys=[old['frames'][i]['id'] for i in packed['frames']];ticks=packed.get('durations',[1]*len(keys));pages=[];time=0;dur=[]
 for key,t in zip(keys,ticks):
  f=next(f for f in fs if f['id']==key);page=Image.new('RGB',(160,96),'#929292');d=ImageDraw.Draw(page)
  for x,path,label in [(0,A/'frames'/f'{key}.png','new'),(80,R/'previous'/f'{key}.png','previous')]:
   hot=f['hotspot'] if label=='new' else next(x['hotspot'] for x in old['frames'] if x['id']==key);im=canvas(Image.open(path),hot);page.paste(im,(x,0),im);d.text((x,82),label,fill='black')
  pages.append(page.resize((640,384),N));before=round(time*100/60);time+=t;dur.append((round(time*100/60)-before)*10)
 pages[0].save(R/f'{group}-paired.gif',save_all=True,append_images=pages[1:],duration=dur,loop=0,disposal=2)
if __name__=='__main__':
 for g in args.groups:apply(g)
