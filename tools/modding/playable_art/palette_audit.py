"""Purpose: compare retained generation references with scene-correct native art.
Inputs: optional pinned disassemblies and historical mica-np-* review folders.
Origin: MICA S2 photo palette investigation, 2026-11-08. No art regeneration.
Output is disposable comparison PNGs/JSON outside the repository; use --output.
"""
import argparse,importlib.util,json,inspect
from pathlib import Path
from PIL import Image,ImageDraw,ImageChops
args=argparse.ArgumentParser(description=__doc__)
args.add_argument('--output',type=Path,required=True)
args.add_argument('--used-root',type=Path,default=Path('/tmp'))
args=args.parse_args()
R=Path(__file__).resolve().parents[3]; A=R/'examples/mica-and-talon/art/mica'; D=args.output.resolve()
if not any(D.is_relative_to(p) for p in (Path('/tmp'),Path.home()/'scratch/agent-tmp')):
 raise ValueError('Audit outputs must stay in disposable /tmp or scratch agent-tmp')
D.mkdir(parents=True,exist_ok=True)
def used(path):return args.used_root/path
def load(p,n):
 s=importlib.util.spec_from_file_location(n,p);m=importlib.util.module_from_spec(s);s.loader.exec_module(m);return m
b=load(A/'wave3/build.py','b');e=load(A/'endings/s1/reference.py','e');c=load(A/'continue/build.py','c');s2=load(A/'endings/s2/build.py','s2');s3=load(A/'endings/s3k/build.py','s3');n=load(R/'tools/modding/playable_art/native_pose.py','n')
results=[]
def review(group,pairs):
 pairs=[(k,Image.open(p).convert('RGBA'),im.convert('RGBA')) for k,p,im in pairs if p.exists()][:4]
 page=Image.new('RGB',(640,max(1,len(pairs))*180),'#929292');d=ImageDraw.Draw(page);changed=[]
 for i,(key,old,new) in enumerate(pairs):
  diff=old.size!=new.size or old.tobytes()!=new.tobytes();changed.append(diff)
  for j,(im,label) in enumerate([(old,'used'),(new,'correct')]):
   im=im.copy();im.thumbnail((280,145),Image.Resampling.NEAREST);scale=min(4,280//im.width,145//im.height);im=im.resize((im.width*scale,im.height*scale),Image.Resampling.NEAREST);page.paste(im,(j*320,i*180),im);d.text((j*320,i*180+148),key+' '+label,fill='black')
 page.save(D/(group+'.png'));results.append(dict(set=group,samples=[k for k,*_ in pairs],changed=changed))
# Freshly decode representative core and slope frames, rather than copying old PNGs.
refs=json.loads((used('mica-np-core-20261108/native')/'references.json').read_text())
review('core',[(f['id'],used('mica-np-core-20261108/native')/(f['id']+'.png'),n.player(f['mapping'])[0]) for f in refs if 'mapping' in f][:4])
refs=json.loads((used('mica-np-slopes-20261108')/'references.json').read_text())
review('slopes',[(f['id'],used('mica-np-slopes-20261108/native')/(f['id']+'.png'),n.player(f['mapping'])[0]) for f in refs][:4])
# Recreate the original sign/HUD decoder setup, stopping before sheet output.
p=load(R/'tools/modding/playable_art/presentation.py','presentation')
setup=inspect.getsource(p.make_review).split('    for scale in (1,4):')[0]+'    return refs,native_pals\n'
exec(setup,p.__dict__);refs,pals=p.make_review(D,{},None);pairs=[]
for key in ['s1-hud','s2-sign','s3k-sign']:
 im=refs[key];game='s3k' if key.startswith('s3k') else key[:2];im.putpalette(sum(pals[game],[])+[0]*720);im.info['transparency']=0
 pairs.append((key,used('mica-np-presentation-20261108/native')/(key+'.png'),im))
portrait=load(A/'portrait/build.py','portrait');maps=portrait.r.mapping('docs/skdisasm/General/Save Menu/Map - Save Screen General.asm','Map_SaveScreen_')
raw=portrait.kos(R/'docs/skdisasm/General/Save Menu/Kosinski Art/Misc.kos')
im=b.native(maps[5][1],raw,n.surface_palette('s3k-portrait')[0]).crop((30,8,62,48))
pairs.append(('portrait',used('mica-np-presentation-20261108/native/portrait.png'),im));review('presentation',pairs)
for game in ('s1','s2','s3k'):
 entries,colors,_,_=c.sources(game);mf=next(g for g in json.loads((A/'continue/continue.yaml').read_text())['games'] if g['game']==game);pairs=[]
 for f,entry in zip(mf['frames'],entries):
  bank,i,(_,ps),data,*_=entry;im=b.native(ps,data,colors).convert('RGBA');hx,hy=f['hotspot'];w,h=f['dimensions'];im=im.crop((48-hx,48-hy,48-hx+w,48-hy+h));key=Path(f['file']).stem
  if len(pairs)<2 or bank=='player' and len(pairs)<4:pairs.append((key,used('mica-np-continue-20261108')/game/'native'/f'{key}.png',im))
 review('continue-'+game,pairs)
for game in ('s1','s2'):
 refs=e.refs(game)
 if game=='s2': refs|=s2.float_refs(__import__('yaml').safe_load((A/'endings/endings-inventory.yaml').read_text()))
 pairs=[]
 for key,im in refs.items():
  if game=='s2' and not key.startswith('MapEng'):continue
  if game=='s1':
   mf=json.loads((A/'endings/s1/s1.yaml').read_text());file=next(f['file'] for f in mf['frames'] if f['native_source_label']==key);key2='s1-'+Path(file).stem
  else:key2='s2-photo-'+key[-1]
  pairs.append((key2,used('mica-np-endings-s1s2-20261108/native')/(key2+'.png'),im))
 review('ending-'+game,pairs)
refs=e.refs('s2')|s2.float_refs(__import__('yaml').safe_load((A/'endings/endings-inventory.yaml').read_text()))
frames2=json.loads((A/'endings/s2/s2.yaml').read_text())['frames']
for group,prefix in [('normal','normal-'),('super','super-'),('float','float-')]:
 review('ending-s2-'+group,[(f['file'],used('mica-np-endings-s1s2-20261108/native')/('s2-'+Path(f['file']).stem+'.png'),refs[f['native_source_label']]) for f in frames2 if f['file'].startswith(prefix)])
frames=json.loads((A/'endings/s3k/s3k.yaml').read_text())['frames']
for group in s3.GROUPS:
 review('ending-s3k-'+group,[(f['id'],used('mica-np-endings-s3k-20261108/native')/(f['id']+'.png'),s3.reference(f)) for f in frames if f['group']==group])
# Stage source banks and load selection from recorded per-frame provenance.
records=json.loads(used('mica-np-stages-20261108/records.json').read_text());surfaces=json.loads((A/'wave3/wave3.yaml').read_text())['surfaces']
for group in ('half_pipe','blue_sphere','slot_machine','snowboard'):
 raw=b.nem(R/'docs/s2disasm/art/nemesis/Sonic and Tails animation frames in special stage.nem') if group=='half_pipe' else (R/('docs/skdisasm/General/Sprites/Sonic/Art/'+{'blue_sphere':'SStage Sonic.unc','slot_machine':'Sonic.unc','snowboard':'Sonic Snowboarding.unc'}[group])).read_bytes()
 colors=next(s['fixed_palette']['colors'] for s in surfaces if s['surface']==group) if group in ('half_pipe','blue_sphere') else b.pal(R/'docs/skdisasm/General/Sprites/Sonic/Palettes/SonicAndTails.bin');pairs=[]
 for f in records:
  if f['group']!=group or not f['pieces']:continue
  base=(0 if f['id']<4 else 0x58 if f['id']<12 else 0x124 if f['id']<16 else 0x171) if group=='half_pipe' else 0;data=b''.join(raw[(base+t)*32:(base+t+count)*32] for count,t in f['dplc_entries']);im=b.native(f['pieces'],data,colors).convert('RGBA');hx,hy=f['hotspot'];w,h=f['dimensions'];im=im.crop((48-hx,48-hy,48-hx+w,48-hy+h));key=f['key'];pairs.append((key,used('mica-np-stages-20261108')/group/'native'/(key+'.png'),im))
 review(group,pairs)
(D/'results.json').write_text(json.dumps(results,indent=2)+'\n');print(json.dumps(results,indent=2))
