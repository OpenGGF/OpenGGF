#!/usr/bin/env python3
"""Original native-pixel S2 ending panels and selectors.
Inputs: shared MICA palette, inventory, native piece coordinates. No Java.
Origin: design lane 2026-11-08; native decoded pixels are review-only.
Use --group normal/super/float in delivery order, or all to regenerate.
"""
import argparse,importlib.util,json,re,math
from pathlib import Path
from PIL import Image,ImageDraw
HERE=Path(__file__).resolve().parent
s=importlib.util.spec_from_file_location('art',HERE.parent/'s1/build.py');a=importlib.util.module_from_spec(s);s.loader.exec_module(a)
r=a.r;ROOT=a.ROOT;ART=a.ART

def rider(size,phase):
 # Explicit tiny views; the plane remains the independently ROM-owned layer.
 w,h=size;im=a.indexed(size);d=ImageDraw.Draw(im)
 if w<=8:
  d.polygon([(2,0),(5,0),(7,4),(6,8),(3,9),(1,6)],fill=4,outline=1)
  d.rectangle((4,3,6,6),fill=7);d.point((5,3),fill=12)
  d.rectangle((2,9,5,12),fill=4);d.line((2,13,6,13),fill=10);d.line((3,10,6,10),fill=12)
 else:
  a.body(im,max(1,w//3),h-10,max(5,w//3),7)
  a.head(im,1,0,min(w-2,13),min(h-7,14),phase==4)
  if phase==0:a.glove(im,w-5,9,5,7,True)
  a.boot(im,2,h-4,max(4,w//2),4)
 return im

def normal_leap(i,size):
 if i<14:
  im=a.leap(size);return im
 im=a.indexed(size)
 # MICA's raised lead glove, sweeping scales and projecting trailing boots.
 d=ImageDraw.Draw(im);d.polygon([(29,50),(42,60),(30,78),(4,76),(20,64)],fill=4,outline=1)
 for x,y in [(12,73),(21,67),(29,61)]:d.line([(x,y),(x+5,y+2),(x+8,y-3)],fill=6)
 a.limb(im,(39,66),(50,82),5);a.limb(im,(47,65),(64,93),6)
 a.boot(im,40,80,16,10);a.boot(im,61,92,17,11)
 a.body(im,28,46,25,23)
 a.limb(im,(34,50),(12,24),6);a.glove(im,1,6,24,28,True)
 a.limb(im,(50,55),(62,59),5);a.glove(im,56,49,15,15)
 a.head(im,16,10,45,43)
 return im

def super_flight(i,size):
 w,h=size;im=a.indexed(size);d=ImageDraw.Draw(im)
 if i==23:
  d.polygon([(41,50),(57,62),(27,75),(4,88),(17,65)],fill=4,outline=1)
  for x,y in [(17,70),(26,64),(35,58)]:d.line([(x,y),(x+7,y+2),(x+10,y-4)],fill=6)
  a.body(im,39,45,29,29)
  a.limb(im,(47,67),(36,81),7);a.limb(im,(58,67),(66,83),7)
  a.boot(im,27,79,17,12);a.boot(im,61,82,19,12)
  a.limb(im,(42,56),(16,76),7);a.glove(im,0,73,26,22)
  a.limb(im,(63,55),(88,51),8);a.glove(im,77,36,33,30)
  a.head(im,24,16,49,43,True)
  return im
 if i in (21,22):
  rows=[
   '0000011111000000',
   '0000145654100000',
   '0001454455410000',
   '0001446744410000',
   '00017c77c7710000',
   '00017f77f7710000',
   '0001777777710000',
   '000017cc77100000',
   '0110011111100110',
   '1cc1147774411cc1',
   '1cc1447874441cc1',
   '0110147774100110',
   '0000014441000000',
   '00001a101a100000',
   '00001a101a100000',
   '0000111011100000']
  im.putdata([int(v,16) for row in rows for v in row])
  if i==22:
   # Raised hands and separated boot endpoints as she opens toward the viewer.
   for xx,yy in [(0,7),(1,6),(14,7),(14,6)]:im.putpixel((xx,yy),12)
   im.putpixel((7,3),5);im.putpixel((8,3),6)
  return im
 # Five hand-placed distant flight angles, not scaled copies of hero pose.
 if i==18:
  d.polygon([(1,13),(9,6),(16,2),(21,4),(23,7),(17,10),(13,11),(8,15)],fill=4,outline=1)
  a.head(im,11,0,11,10);a.glove(im,8,8,5,5);a.boot(im,1,12,6,4)
 else:
  phase=i-19
  hx=[7,5,3,5][phase];hy=[0,1,0,1][phase]
  d.polygon([(hx,7),(hx+4,9),(8,13),(1,15),(3,11)],fill=4,outline=1)
  a.body(im,5,7,6,5);a.head(im,hx,hy,9,9)
  a.glove(im,[3,2,0,1][phase],[8,6,7,9][phase],5,5)
  a.boot(im,[2,5,8,7][phase],12,5,4)
 return im

def floating(phase,size):
 w,h=size;im=a.indexed(size);d=ImageDraw.Draw(im)
 if phase in (0,2):
  # Opposed side views. Keep the cheek inside the 24x32 central piece;
  # the narrow leading piece carries only the extended white glove.
  d.polygon([(18,17),(24,21),(15,27),(3,29),(8,23)],fill=4,outline=1)
  d.line([(8,26),(12,25),(16,21)],fill=6)
  a.body(im,13,13,15,12)
  a.limb(im,(17,19),(7,14),3);a.boot(im,0,8,11,9)
  a.limb(im,(19,23),(12,25),3);a.boot(im,6,23,12,8)
  a.limb(im,(28,15),(40,8),3);a.glove(im,40,2,7,11)
  a.head(im,19,0,20,23,True);a.glove(im,13,16,8,8)
  return im.transpose(Image.Transpose.FLIP_LEFT_RIGHT) if phase==2 else im
 if phase==1:
  # Front-facing spread arms, paired eye whites, grin and hanging belly.
  d.ellipse((13,0,34,24),fill=3,outline=1)
  for yy in (3,8):
   for xx in (16,23):
    d.polygon([(xx,yy),(xx+3,yy-2),(xx+7,yy),(xx+5,yy+4),(xx+1,yy+4)],fill=4,outline=2)
    d.line((xx+1,yy,xx+4,yy-1),fill=6)
  d.polygon([(17,12),(23,16),(29,12),(33,18),(31,23),(24,25),(16,23),(14,18)],fill=7,outline=1)
  for xx in (17,26):
   d.ellipse((xx,11,xx+4,19),fill=12,outline=1);d.line((xx+2,13,xx+2,17),fill=15)
  d.polygon([(19,21),(28,21),(26,24),(21,24)],fill=1);d.line((20,21,27,21),fill=12)
  a.body(im,18,24,11,7)
  a.limb(im,(17,22),(5,11),3);a.glove(im,0,5,8,10)
  a.limb(im,(30,22),(42,11),3);a.glove(im,40,5,8,10)
  # Native mapping aliases left and right halves, so draw the matching half.
  for yy in range(h):
   for xx in range(24):im.putpixel((47-xx,yy),im.getpixel((xx,yy)))
  return im
 # Rear three-quarter opposed views: three jade scale rows, tapered tail,
 # coral heels and a forward-reaching white glove. No misleading front face.
 a.limb(im,(18,19),(31,16),3);a.boot(im,29,12,10,10)
 a.limb(im,(18,23),(25,27),3);a.boot(im,21,24,10,8)
 d.ellipse((9,0,29,26),fill=3,outline=1)
 for row,yy in enumerate((3,9,15,21)):
  for xx in ((12,19) if row%2==0 else (10,17,23)):
   d.polygon([(xx,yy),(xx+3,yy-2),(xx+6,yy),(xx+5,yy+4),(xx+1,yy+5)],fill=4,outline=2)
   d.line((xx+1,yy,xx+4,yy-1),fill=6)
 d.polygon([(15,22),(23,23),(24,28),(16,31),(11,30)],fill=4,outline=1)
 d.line((16,25,21,27),fill=6)
 a.limb(im,(12,15),(5,8),3);a.glove(im,0,2,8,11)
 return im.transpose(Image.Transpose.FLIP_LEFT_RIGHT) if phase==4 else im

def float_refs(inv):
 mm=r.maps(ROOT/'docs/s2disasm/mappings/sprite/Sonic.asm');dp=(ROOT/'docs/s2disasm/mappings/spriteDPLC/Sonic.asm').read_text();raw=(ROOT/"docs/s2disasm/art/uncompressed/Sonic's art.bin").read_bytes();pal=r.b.pal(ROOT/'docs/s2disasm/art/palettes/Ending Sonic.bin');out={}
 for f in inv['frame_sets']['s2_float']['frames']:
  chunk=dp.split(f".frame{f['index']}:",1)[1].split('_End',1)[0];loads=[list(map(r.b.num,v)) for v in re.findall(r'dplcEntry\s+([^,]+),\s*([^\s;]+)',chunk)]
  out[f['label']]=r.render(mm[f['label']],b''.join(raw[t*32:(t+n)*32] for n,t in loads),[pal]*4)
 return out

def generate(group,inv):
 frames=[]
 if group=='photos':
  raise ValueError('S2 photos are native in phase 1 and sidekick-owned in phase 2')
 else:
  keys={'normal':['s2_normal_turn','s2_normal_leap'],'super':['s2_super_depart','s2_super_hero'],'float':['s2_float']}[group]
  mm=r.maps(ROOT/('docs/s2disasm/mappings/sprite/Sonic.asm' if group=='float' else 'docs/s2disasm/mappings/sprite/objCF.asm'))
  for key in keys:
   for f in inv['frame_sets'][key]['frames']:
    i=f['index'];x,y,w,h=f['bounds_px'];pieces=mm[f['label']];char=[p for p in pieces if p[7]==1] if key=='s2_normal_turn' else pieces
    if key=='s2_normal_turn':
     cx=min(p[0] for p in char);cy=min(p[1] for p in char);cw=max(p[0]+p[2]*8 for p in char)-cx;ch=max(p[1]+p[3]*8 for p in char)-cy
     im=a.indexed((w,h))
     if i in (9,10):
      pp=char[0];im.paste(rider((8,16),i-7),(pp[0]-x,pp[1]-y))
      pp=char[1];a.glove(im,pp[0]-x+1,pp[1]-y+1,6,6)
     else:im.paste(rider((cw,ch),i-7),(cx-x,cy-y))
    elif group=='normal':im=normal_leap(i,(w,h))
    elif group=='super':im=super_flight(i,(w,h))
    else:im=floating(i-84,(w,h))
    im=a.mask_layout(im,char,f['bounds_px'])
    if group=='super':im.putpalette(sum(json.loads((ART/'presentation/gilded.yaml').read_text())['cycles']['s2']['frames'][0],[])+[0]*720)
    file=f'{group}-{i:02x}.png';im=a.native_pose(im,file,HERE);im.save(HERE/file,bits=4,transparency=0)
    frames.append(dict(id=i,file=file,native_source_label=f['label'],native_art_label=inv['frame_sets'][key]['native_art'],dimensions=[w,h],bounds_px=f['bounds_px'],hotspot=[-x,-y],palette_line=1 if key=='s2_normal_turn' else 0,pieces=char,native_composite_pieces=pieces if key=='s2_normal_turn' else None,group=key,**({'form':'gilded'} if group=='super' else {})))
 if group=='float':
  dp=(ROOT/'docs/s2disasm/mappings/spriteDPLC/Sonic.asm').read_text()
  for f in frames:
   label=f".frame{f['id']}";chunk=dp.split(label+':',1)[1].split('_End',1)[0]
   loads=[list(map(r.b.num,v)) for v in re.findall(r'dplcEntry\s+([^,]+),\s*([^\s;]+)',chunk)]
   f.update(dplc_label='MapRUnc_Sonic'+label,dplc_entries=loads,tile_budget=sum(n for n,t in loads),timing=dict(native_source_label='SonAni_Float2',ticks=8,order=f['id']-84))
 return frames

def main():
 p=argparse.ArgumentParser();p.add_argument('--group',choices=['photos','normal','super','float','all'],default='all');p.add_argument('--review',type=Path);args=p.parse_args();inv=__import__('yaml').safe_load((ART/'endings/endings-inventory.yaml').read_text())
 dest=HERE/'s2.yaml';m=json.loads(dest.read_text()) if dest.exists() else dict(format='openggf-ending-art-handoff-1',game='s2',native_source_label='ArtNem_EndingPics / ArtNem_EndingSonic / ArtNem_EndingSuperSonic / SonAni_Float2',palette='../../palette.yaml',gilded='../../presentation/gilded.yaml',tick_rate=60,palette_binding='Keep all MICA indices: normal leap/Float2 on line 0; Tornado rider line 1 shared with native Tails pilot requires engine palette arbitration or per-piece reassignment. Plane line 0 remains ROM-owned; PNGs for 7..11 are character-only transparent overlays. Super stores unchanged indices with the first Gilded s2 palette for static viewing; install the full s2 cycle on line 0. All four photos remain native ROM art with their native palette.',layout='Native piece coordinates, dimensions, flips and priorities. Reallocate original tiles into per-frame banks; do not retain inter-frame source aliases. Full composite bounds include blank plane area in character overlays.',timing={s['id']:s['animation_timing'] for s in inv['sequences'] if s['game']=='s2'},groups={})
 m['photo_cast_note']='Native monochrome ROM photos; TALON sidekick overrides deferred to phase 2.'
 m['palette_binding']=m['palette_binding'].replace('Super stores unchanged indices; install Gilded s2 cycle on line 0.', 'Super stores unchanged indices with the first Gilded s2 palette for static viewing; install the full s2 cycle on line 0.')
 for group in (['normal','super','float'] if args.group=='all' else [args.group]):
  fs=generate(group,inv);m['groups'][group]=[f['id'] for f in fs];updates={f['id']:f for f in fs};old=m.get('frames',[]);m['frames']=[updates.pop(f['id'],f) for f in old]+list(updates.values());a.verify(HERE,dict(frames=fs))
  if args.review:
   args.review.mkdir(parents=True,exist_ok=True)
   refs=float_refs(inv) if group=='float' else r.refs('s2')
   # Compare transparent rider overlays against native composite with plane visible.
   if group=='normal':
    raw=r.b.nem(ROOT/'docs/s2disasm/art/nemesis/Small pictures of Tornado in final ending sequence.nem');pal=r.b.pal(ROOT/'docs/s2disasm/art/palettes/Ending Sonic.bin')
    for f in fs:
     if f['id']<12:
      plane=[p[:] for p in f['native_composite_pieces'] if p[7]==0]
      for pp in plane:pp[4]-=0x493
      refplane=r.render(plane,raw,[pal]*4);canvas=Image.new('RGBA',tuple(f['dimensions']));bx=min(pp[0] for pp in plane)-f['bounds_px'][0];by=min(pp[1] for pp in plane)-f['bounds_px'][1];canvas.alpha_composite(refplane,(bx,by));overlay=Image.open(HERE/f['file']).convert('RGBA');canvas.alpha_composite(overlay);canvas.save(args.review/f'normal-{f["id"]:02x}-composite.png')
   colors=None
   if group=='super':colors=json.loads((ART/'presentation/gilded.yaml').read_text())['cycles']['s2']['frames'][0]
   reviewfs=[dict(f,file=str(args.review/f'normal-{f["id"]:02x}-composite.png')) if group=='normal' and f['id']<12 else f for f in fs]
   a.review(args.review,'s2-'+group,reviewfs,refs,HERE,colors)
 dest.write_text(json.dumps(m,indent=2)+'\n')
if __name__=='__main__':main()
