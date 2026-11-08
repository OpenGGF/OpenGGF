#!/usr/bin/env python3
"""Original MICA ending pixels; native-coordinate polygons, fixed indexed palette.
Inputs: MICA palette, inventory and pinned mapping layouts; refs only for review.
Origin: design lane, 2026-11-08. No Java or generated image dependency.
"""
import argparse,importlib.util,json,math,hashlib
from pathlib import Path
from PIL import Image,ImageDraw
HERE=Path(__file__).resolve().parent
s=importlib.util.spec_from_file_location('reference',HERE/'reference.py');r=importlib.util.module_from_spec(s);s.loader.exec_module(r)
ROOT=r.ROOT;ART=r.ART;COLORS=json.loads((ART/'palette.yaml').read_text())['colors']

def indexed(size):return r.b.indexed(size)

def head(im,x,y,w,h,look=False,front=False):
 d=ImageDraw.Draw(im)
 def pts(p):return [(x+round(a*w/40),y+round(b*h/40)) for a,b in p]
 def poly(p,c,o=1):d.polygon(pts(p),fill=c,outline=o)
 def line(p,c):d.line(pts(p),fill=c)
 poly([(4,36),(0,23),(2,12),(9,4),(19,0),(29,2),(35,9),(37,22),(32,34),(23,39),(13,40)],3)
 for p in [[(9,6),(17,2),(22,4),(21,10),(14,13),(7,11)],[(22,4),(29,5),(34,10),(31,15),(24,14),(20,10)],[(4,14),(12,11),(18,14),(16,21),(9,24),(2,20)],[(15,16),(23,13),(29,18),(26,24),(18,26),(13,23)],[(3,25),(10,23),(16,26),(14,33),(8,36),(3,32)],[(13,29),(20,26),(25,30),(23,36),(17,39),(12,36)]]:
  poly(p,4,2);line(p[:3],6)
 poly([(18,17),(16,10),(19,7),(23,9),(25,17)],1)
 poly([(19,14),(19,10),(21,11),(23,16)],14,14)
 poly([(25,17),(31,15),(35,19),(36,24),(40,26),(39,31),(32,35),(26,36),(19,32),(18,25),(20,20)],7)
 line([(20,31),(26,34),(32,34),(38,30)],8)
 poly([(21,18),(25,17),(28,21),(28,28),(23,28),(20,24)],12)
 poly([(31,19),(33,20),(35,24),(34,27),(31,27)],12)
 line([(25,20 if not look else 18),(26,25 if not look else 23)],15)
 line([(33,21 if not look else 19),(33,25 if not look else 23)],15)
 line([(21,18),(19,16)],1);line([(23,18),(21,16)],1)
 poly([(25,13),(29,13),(32,17),(29,22),(27,18)],4,2);line([(26,14),(29,15)],6)
 line([(38,26),(39,27)],1)
 poly([(25,30),(34,30),(31,34),(28,34)],1)
 line([(26,30),(32,30)],12);line([(28,33),(30,33)],14)

def glove(im,x,y,w,h,peace=False):
 d=ImageDraw.Draw(im)
 def pts(p):return [(x+round(a*w/20),y+round(b*h/24)) for a,b in p]
 p=([(3,23),(1,17),(4,13),(0,4),(2,1),(5,2),(9,12),(11,1),(14,0),(16,3),(14,13),(18,13),(20,17),(17,22),(11,24)] if peace else [(2,6),(7,2),(14,1),(18,5),(20,13),(17,20),(10,23),(4,20),(0,13)])
 d.polygon(pts(p),fill=12,outline=1)
 for p in [[(5,14),(10,16),(16,14)],[(4,19),(9,21),(15,20)],[(10,16),(12,18)]]:d.line(pts(p),fill=13)

def boot(im,x,y,w,h):
 d=ImageDraw.Draw(im)
 p=[(x,y),(x+w*.6,y),(x+w-1,y+h*.45),(x+w-1,y+h-1),(x,y+h-1),(x-1,y+h*.5)]
 d.polygon([(round(a),round(b)) for a,b in p],fill=10,outline=1)
 d.line((x+1,y+h-2,x+w-2,y+h-2),fill=9)
 d.line((x+1,y+1,x+max(1,w//2),y+1),fill=12)
 if w>7:d.line((x+2,y+3,x+max(2,w//2),y+3),fill=4)

def body(im,x,y,w,h):
 d=ImageDraw.Draw(im)
 d.ellipse((x,y,x+w,y+h),fill=4,outline=1)
 d.ellipse((x+w//3,y+1,x+w-2,y+h-2),fill=7,outline=1)
 for yy in range(y+max(3,h//4),y+h-2,max(3,h//4)):d.line((x+w//3+1,yy,x+w-3,yy+1),fill=8)

def limb(im,a,b,width,c=4):
 d=ImageDraw.Draw(im);d.line([a,b],fill=1,width=width+2);d.line([a,b],fill=c,width=width)

def standing(kind=0,size=(32,40)):
 im=indexed(size);d=ImageDraw.Draw(im)
 # Native narrow crown; tail curls beneath the shared upper-body piece.
 d.polygon([(13,22),(20,27),(14,32),(3,35),(8,30),(11,27)],fill=4,outline=1)
 d.line([(7,32),(12,31),(16,28)],fill=6)
 body(im,13,22,12,12)
 for x in (13,23):limb(im,(x,31),(x-1,35),3)
 boot(im,9,34,10,6);boot(im,21,34,10,6)
 head(im,9,0,22,24,kind==2)
 if kind in (0,1):
  limb(im,(14,25),(12,28),3);limb(im,(24,25),(22,28),3)
  glove(im,8,25,8,8);glove(im,17,25,8,8)
  if kind==1:
   for x,y in [(3,24),(5,21),(3,29),(28,23),(29,29),(7,26)]:d.point((x,y),fill=12)
 elif kind==2:
  limb(im,(14,25),(8,28),3);glove(im,1,24,9,10,True);glove(im,24,26,7,8)
 else:
  limb(im,(14,25),(6,29),3);limb(im,(24,25),(28,29),3)
  glove(im,0,26,9,9);glove(im,24,26,8,9)
 if kind==4:im=im.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
 return im

def leap(size,hero=False,super=False):
 # Re-draw vector coordinates at each native size; no filtered/scaled bitmap.
 w,h=size;im=indexed(size);d=ImageDraw.Draw(im)
 def at(a,b):return (round(a*w/100),round(b*h/100))
 def poly(p,c):d.polygon([at(a,b) for a,b in p],fill=c,outline=1)
 poly([(38,54),(48,68),(30,82),(2,96),(11,82),(30,69)],4)
 for a,b in [(13,85),(22,78),(31,71)]:d.line([at(a,b),at(a+6,b+3),at(a+10,b-3)],fill=6)
 body(im,*at(35,48),max(8,round(w*.26)),max(10,round(h*.27)))
 for a,b,c,e in [(43,70,28,87),(55,70,60,87)]:limb(im,at(a,b),at(c,e),max(2,round(w*.05)))
 boot(im,*at(15,85),max(6,round(w*.22)),max(4,round(h*.12)))
 boot(im,*at(56,86),max(5,round(w*.15)),max(4,round(h*.12)))
 if hero:
  limb(im,at(39,55),at(15,69),max(3,round(w*.06)));glove(im,*at(1,65),max(6,round(w*.23)),max(6,round(h*.23)))
  limb(im,at(58,54),at(77,50),max(3,round(w*.06)));glove(im,*at(68,39),max(8,round(w*.30)),max(8,round(h*.29)))
 else:
  limb(im,at(38,54),at(18,30),max(2,round(w*.05)));glove(im,*at(3,7),max(6,round(w*.27)),max(8,round(h*.31)),True)
  limb(im,at(60,55),at(77,57),max(2,round(w*.05)));glove(im,*at(71,49),max(6,round(w*.23)),max(6,round(h*.18)))
 head(im,*at(28,3),max(14,round(w*.45)),max(15,round(h*.48)))
 if hero and not super:
  # Hero leap's foreground peace glove, native large perspective.
  glove(im,*at(65,16),max(8,round(w*.34)),max(10,round(h*.49)),True)
 return im

def front_takeoff():
 im=indexed((32,40));d=ImageDraw.Draw(im)
 body(im,10,22,11,10)
 d.ellipse((5,0,26,25),fill=3,outline=1)
 for y in (3,8,13):
  for x in (8,15):
   d.polygon([(x,y),(x+3,y-2),(x+7,y),(x+5,y+4),(x+1,y+4)],fill=4,outline=2)
   d.line((x+1,y,x+4,y-1),fill=6)
 d.polygon([(10,12),(15,16),(21,12),(25,19),(23,23),(16,26),(8,23),(6,19)],fill=7,outline=1)
 for x in (9,18):
  d.ellipse((x,11,x+4,19),fill=12,outline=1);d.line((x+2,13,x+2,17),fill=15)
 d.polygon([(11,21),(20,21),(18,24),(13,24)],fill=1);d.line((12,21,19,21),fill=12)
 limb(im,(10,27),(4,28),2);glove(im,0,23,8,9)
 limb(im,(21,27),(27,28),2);glove(im,24,23,8,9)
 boot(im,8,33,9,7);boot(im,17,33,9,7)
 # Equal upper halves, including the complete paired eyes and grin.
 for y in range(24):
  for x in range(16):im.putpixel((31-x,y),im.getpixel((x,y)))
 return im

def s1_enlarged(last):
 if not last:
  im=indexed((48,72));d=ImageDraw.Draw(im)
  d.polygon([(19,37),(27,43),(18,59),(5,64),(11,52)],fill=4,outline=1)
  body(im,12,34,17,20);limb(im,(18,52),(17,64),4);limb(im,(25,52),(27,62),4)
  boot(im,8,62,9,9);boot(im,17,59,7,10)
  limb(im,(16,39),(5,42),4);glove(im,0,36,13,13)
  limb(im,(28,39),(36,37),4);glove(im,27,29,15,16,True)
  head(im,0,0,40,39)
  return im
 im=indexed((176,136));d=ImageDraw.Draw(im)
 d.polygon([(57,79),(71,90),(47,111),(7,131),(25,106),(43,92)],fill=4,outline=1)
 for x,y in [(23,112),(34,103),(45,94)]:d.line([(x,y),(x+7,y+4),(x+14,y-5)],fill=6)
 limb(im,(61,94),(35,117),9);limb(im,(72,94),(66,118),8)
 boot(im,4,116,33,17);boot(im,57,118,19,17)
 body(im,49,65,34,35)
 limb(im,(50,74),(32,76),8);glove(im,24,65,24,23)
 limb(im,(79,75),(119,66),10)
 head(im,24,0,81,77)
 glove(im,104,27,70,72,True)
 return im

def mask_layout(im,pieces,bounds):
 x,y,w,h=bounds;mask=Image.new('1',(w,h));d=ImageDraw.Draw(mask)
 for px,py,pw,ph,*_ in pieces:d.rectangle((px-x,py-y,px-x+pw*8-1,py-y+ph*8-1),fill=1)
 out=indexed(im.size);out.paste(im,mask=mask)
 old=out.copy()
 for yy in range(h):
  for xx in range(w):
   if old.getpixel((xx,yy)) and any(not(0<=xx+dx<w and 0<=yy+dy<h) or not old.getpixel((xx+dx,yy+dy)) for dx,dy in ((1,0),(-1,0),(0,1),(0,-1))):out.putpixel((xx,yy),1)
 return out

def review(dest,key,frames,refs,folder,colors=None):
 dest.mkdir(parents=True,exist_ok=True)
 for scale in (1,4):
  cellw=max(max(f['dimensions'][0] for f in frames)+10,115);cellh=max(max(f['dimensions'][1] for f in frames)+30,100)
  sheet=Image.new('RGB',(cellw*2,cellh*len(frames)),'#969696');d=ImageDraw.Draw(sheet)
  for n,f in enumerate(frames):
   im=Image.open(folder/f['file'])
   if colors:im.putpalette(sum(colors,[])+[0]*(768-48))
   im=im.convert('RGBA');ref=refs[f['native_source_label']]
   sheet.paste(im,(0,n*cellh),im);sheet.paste(ref,(cellw,n*cellh),ref)
   d.text((1,n*cellh+cellh-27),str(f['id'])+' MICA',fill='black');d.text((cellw,n*cellh+cellh-27),'native Sonic',fill='black')
   d.text((1,n*cellh+cellh-14),f['native_source_label'],fill='black')
  sheet.resize((sheet.width*scale,sheet.height*scale),Image.Resampling.NEAREST).save(dest/f'{key}-{scale}x.png')

def verify(folder,m):
 for f in m['frames']:
  im=Image.open(folder/f['file']);assert im.mode=='P' and list(im.size)==f['dimensions'];assert im.info['transparency']==0
  colors=json.loads((ART/'presentation/gilded.yaml').read_text())['cycles']['s2']['frames'][0] if f.get('form')=='gilded' else COLORS
  assert im.getpalette()[:48]==sum(colors,[]);assert max(im.get_flattened_data())<16;assert im.getbbox()
  if 'pieces' in f:
   assert mask_layout(im,f['pieces'],f['bounds_px']).tobytes()==im.tobytes(),f['id']
 print(folder.name,len(m['frames']),'indexed palette, alpha, dimensions and piece coverage PASS')

def main():
 p=argparse.ArgumentParser();p.add_argument('--review',type=Path);a=p.parse_args()
 inv=__import__('yaml').safe_load((ART/'endings/endings-inventory.yaml').read_text());group=inv['frame_sets']['s1_celebration'];mm=r.maps(ROOT/'docs/s1disasm/_maps/Ending Sequence Sonic.asm');frames=[]
 for f in group['frames']:
  i=f['index'];x,y,w,h=f['bounds_px'];pieces=mm[f['label']]
  im=standing(i) if i<5 else front_takeoff() if i==5 else s1_enlarged(i==7)
  if i==5:
   # Native repeats a mirrored head/arm half; author symmetrical face-on takeoff.
   for yy in range(24):
    for xx in range(16):im.putpixel((31-xx,yy),im.getpixel((xx,yy)))
  im=mask_layout(im,pieces,f['bounds_px']);file=f'ending-{i:02}.png';im.save(HERE/file,bits=4,transparency=0)
  frames.append(dict(id=i,file=file,native_source_label=f['label'],dimensions=[w,h],bounds_px=f['bounds_px'],hotspot=[-x,-y],palette_line=0,pieces=pieces))
 m=dict(format='openggf-ending-art-handoff-1',game='s1',native_source_label='Nem_EndSonic / Map_ESon / Ani_ESon',palette='../../palette.yaml',palette_line=0,tick_rate=60,palette_binding='Install unchanged MICA line 0. Audit sharing with emeralds, flowers, logo; do not recolour MICA to Sonic blue.',layout='Native piece coordinates and flips; independent per-selector banks required (shared native source tiles are not an instruction to alias MICA frames).',timing=[s['animation_timing'] for s in inv['sequences'] if s['id'] in ('s1-good-celebration','s1-bad-celebration')],animations=[dict(name='hold',frames=[1,0,1,0,1,0,1,0,1,0,1,2],ticks=4),dict(name='confused',frames=[3,4,3,4,3,4,3],ticks=6),dict(name='leap',frames=[5,5,5,6,7],ticks=4,hold_last=True)],frames=frames)
 (HERE/'s1.yaml').write_text(json.dumps(m,indent=2)+'\n');verify(HERE,m)
 if a.review:review(a.review,'s1',frames,r.refs('s1'),HERE)
if __name__=='__main__':main()
