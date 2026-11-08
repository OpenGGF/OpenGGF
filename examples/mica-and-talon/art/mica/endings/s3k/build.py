#!/usr/bin/env python3
"""Purpose: original native-pixel MICA S3K ending drawings and layout verification.
Inputs: shared MICA/Gilded palettes and pinned S3K mapping/DPLC references.
Origin: MICA ending art lane, 2026-11-08. Native decoded art is review-only.
Run with -I; no Java build. Review outputs belong outside the repository.
"""
import argparse, importlib.util, json, re
from pathlib import Path
from PIL import Image, ImageDraw
import yaml
HERE=Path(__file__).resolve().parent
ART=HERE.parents[1]
ROOT=ART.parents[3]
spec=importlib.util.spec_from_file_location('round5',ART/'wave3/round5.py')
r=importlib.util.module_from_spec(spec);spec.loader.exec_module(r)
b=r.b
COLORS=b.COLORS
GOLD=json.loads((ART/'presentation/gilded.yaml').read_text())
INV=yaml.safe_load((HERE.parent/'endings-inventory.yaml').read_text())
MAP='docs/skdisasm/General/Sprites/Sonic/Map - Sonic.asm'
DPLC='docs/skdisasm/General/Sprites/Sonic/DPLC - Sonic.asm'
POSE='docs/skdisasm/General/Ending/Map - Sonic Tails Ending Poses.asm'
PLANE='docs/skdisasm/General/Ending/Map - Sonic Plane Ending.asm'
EYE='docs/skdisasm/General/Ending/Map - S3 Ending Graphics.asm'
GROUPS={'reunion':['s3k_normal_pose','s3k_super_pose'],'passengers':['s3k_tiny_passenger'],'trio':['s3k_eyecatch_sonic'],'float2':['s3k_rescue_float'],'ride':['s3k_ride'],'transformation':['s3k_transform']}

def kosm(path):
 """Byte-oriented Kosinski decoder, no timing or hardware model; review only."""
 src=path.read_bytes();expected=int.from_bytes(src[:2],'big');pos=2;out=bytearray()
 while len(out)<expected:
  start=pos;buf=bytearray();desc=0;bits=0
  def byte():
   nonlocal pos
   v=src[pos];pos+=1;return v
  def bit():
   nonlocal desc,bits
   if not bits:desc=byte()|byte()<<8;bits=16
   v=desc&1;desc>>=1;bits-=1
   if not bits:desc=byte()|byte()<<8;bits=16
   return v
  while True:
   if bit():buf.append(byte());continue
   if not bit():
    n=(bit()<<1|bit())+2;off=byte()-256
   else:
    lo=byte();hi=byte();off=((hi&248)<<5|lo)-8192;n=hi&7
    if n:n+=2
    else:
     n=byte()
     if n==0:break
     if n==1:continue
     n+=1
   for _ in range(n):buf.append(buf[off])
  out.extend(buf)
  if len(out)<expected:pos=start+((pos-start+15)//16)*16
 assert len(out)==expected,(path,len(out),expected)
 return bytes(out)

def bounds(p):return [min(x for x,y,w,h,*_ in p),min(y for x,y,w,h,*_ in p),max(x+w*8 for x,y,w,h,*_ in p),max(y+h*8 for x,y,w,h,*_ in p)]
def native(p,data,colors):
 x0,y0,x1,y1=bounds(p);im=b.indexed((x1-x0,y1-y0),colors)
 for x,y,w,h,t,fx,fy,*_ in p:
  tile=b.indexed((w*8,h*8),colors)
  for tx in range(w):
   for ty in range(h):
    raw=data[(t+tx*h+ty)*32:(t+tx*h+ty+1)*32];assert len(raw)==32,(t,len(data))
    z=b.indexed((8,8),colors);z.putdata([n for v in raw for n in (v>>4,v&15)]);tile.paste(z,(tx*8,ty*8))
  if fx:tile=tile.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
  if fy:tile=tile.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
  im.paste(tile,(x-x0,y-y0),tile.point(lambda v:255 if v else 0,mode='L'))
 return im

def definitions():
 maps={}
 for path,base in [(MAP,'Map_Sonic_'),(POSE,'Map_SonicTailsEndPoses_'),(PLANE,'Map_SonicPlaneEnding_'),(EYE,'Map_EndingGraphics_')]:
  maps[path]=dict(r.mapping(path,base))
 # Super mappings live in the same file with their own pointer table.
 maps[MAP].update(dict(r.mapping(MAP,'Map_SuperSonic_')))
 plcs=r.dplc(DPLC,'PLC_Sonic_');frames=[]
 for group,sets in GROUPS.items():
  for key in sets:
   fs=INV['frame_sets'][key];path=POSE if 'pose' in key else PLANE if 'passenger' in key else EYE if 'eyecatch' in key else MAP
   for f in fs['frames']:
    p=maps[path][f['label']];box=bounds(p);x,y,w,h=f['bounds_px'];assert box==[x,y,x+w,y+h],(key,f,box)
    item=dict(id=key.removeprefix('s3k_')+f'-{f["index"]:02x}',group=group,frame_set=key,index=f['index'],native_source_label=f['label'],native_art_label=fs['native_art'],mapping_source=path,dimensions=[w,h],hotspot=[-x,-y],bounds_px=[x,y,w,h],pieces=p,palette_line=fs['palette_line'],relative_piece_palette_lines=f['palette_lines'],palette='../../palette.yaml',form='gilded' if key in ('s3k_super_pose','s3k_transform') else 'normal')
    if path==MAP:
     pl,loads=plcs[f['index']];item.update(dplc_label=pl,dplc_entries=loads,tile_budget=sum(n for n,t in loads))
    frames.append(item)
 return frames

def reference(f):
 key=f['frame_set'];base=ROOT/'docs/skdisasm/General'
 if 'pose' in key:
  name='Super Sonic' if f['form']=='gilded' else 'Sonic'
  raw=kosm(base/f'Ending/KosinskiM Art/{name} Ending Pose.kosm');colors=b.pal(base/f'Ending/Palettes/{name} End Pose.bin')
 elif 'passenger' in key:
  raw=kosm(base/'Ending/KosinskiM Art/Sonic Plane Ending.kosm');colors=b.pal(base/'Sprites/Sonic/Palettes/SonicAndTails.bin')
 elif 'eyecatch' in key:
  raw=b.nem(base/'Ending/Nemesis Art/S3 Ending Graphics.nem');colors=b.pal(base/'Sprites/Sonic/Palettes/SonicAndTails.bin')
 else:
  raw=(base/'Sprites/Sonic/Art/Sonic.unc').read_bytes();raw=b''.join(raw[t*32:(t+n)*32] for n,t in f['dplc_entries']);colors=b.pal(base/'Sprites/Sonic/Palettes/SonicAndTails.bin')
 return native(f['pieces'],raw,colors)

def review(dest,group,frames):
 dest.mkdir(parents=True,exist_ok=True)
 w=320;row=164
 sheet=Image.new('RGB',(w,row*len(frames)),'#9299a1');d=ImageDraw.Draw(sheet)
 for j,f in enumerate(frames):
  im=Image.open(HERE/f['file'])
  if f['form']=='gilded':im.putpalette(sum(GOLD['cycles']['s3k']['frames'][0],[])+[0]*720)
  im=im.convert('RGBA');ref=reference(f).convert('RGBA')
  for x,z in [(6,im),(166,ref)]:sheet.paste(z,(x+(144-z.width)//2,j*row+4+(136-z.height)//2),z)
  d.text((5,j*row+142),f['id'],fill='black');d.text((166,j*row+142),f['native_source_label'],fill='black')
 for scale in (1,4):sheet.resize((w*scale,sheet.height*scale),Image.Resampling.NEAREST).save(dest/f'{group}-{scale}x.png')


def draw(f):
 w,h=f['dimensions'];im=b.indexed((w,h));d=ImageDraw.Draw(im)
 def poly(pts,c=4,o=1):d.polygon([(round(x),round(y)) for x,y in pts],fill=c,outline=o)
 def line(pts,c=1,width=1):d.line([(round(x),round(y)) for x,y in pts],fill=c,width=width)
 def ellipse(box,c,o=1):d.ellipse(tuple(round(v) for v in box),fill=c,outline=o)
 def boot(x,y,s=1):
  poly([(x-3*s,y-3*s),(x+3*s,y-3*s),(x+4*s,y),(x+7*s,y+2*s),(x+6*s,y+6*s),(x-4*s,y+6*s),(x-5*s,y+3*s)],10)
  line([(x-3*s,y+5*s),(x+6*s,y+5*s)],9,max(1,round(s)))
  line([(x-2*s,y),(x+2*s,y)],12,max(1,round(s)))
  line([(x-1*s,y+2*s),(x+3*s,y+3*s)],5,max(1,round(s)))
 def glove(x,y,s=1,point=False):
  ellipse((x-4*s,y-4*s,x+4*s,y+4*s),12)
  line([(x-2*s,y+2*s),(x+2*s,y+2*s)],13,max(1,round(s)))
  if point:
   poly([(x-2*s,y-2*s),(x-3*s,y-8*s),(x-2*s,y-10*s),(x,y-10*s),(x+1*s,y-3*s)],12)
  line([(x,y-2*s),(x+2*s,y-1*s)],13)
 def tail(pts):
  poly(pts,3)
  for a,c in zip(pts[1:-1],pts[2:]):line([a,((a[0]+c[0])/2-1,(a[1]+c[1])/2-2),c],5)
 def head(cx,cy,s=1,face=1,flare=0):
  def pp(pts,c=4,o=1):poly([(cx+face*x*s,cy+y*s) for x,y in pts],c,o)
  def ll(pts,c=1):line([(cx+face*x*s,cy+y*s) for x,y in pts],c,max(1,round(s*.6)))
  pp([(-10,-4),(-10,-10),(-6,-14),(0,-15),(6,-12),(9,-6),(7,2),(0,6),(-8,3)],3)
  # Large overlapping round-edged scale plates; final powered crest fans out.
  for x,y in [(-7,-9),(-1,-12),(4,-9),(-8,-3),(-3,-5),(-6,2)]:
   pp([(x-3,y),(x-2,y-3-flare),(x+1,y-4-flare),(x+4,y-2),(x+4,y+1),(x+1,y+4),(x-2,y+3)],4,2)
   ll([(x-1,y-2-flare),(x+1,y-3-flare),(x+3,y-1)],6)
  pp([(0,-7),(5,-8),(8,-5),(9,-1),(13,1),(13,3),(8,6),(3,6),(-1,2)],7)
  pp([(6,-11),(8,-12),(10,-9),(9,-6),(7,-7)],14)
  pp([(1,-9),(5,-9),(7,-5),(6,0),(2,0),(0,-3)],12)
  pp([(4,-8),(6,-6),(6,-2),(4,-1),(3,-3),(3,-6)],15)
  ll([(4,-7),(4,-6)],12)
  ll([(0,-9),(3,-10),(5,-9)])
  pp([(7,2),(11,2),(9,5),(7,4)],1)
  ll([(8,4),(9,4)],14)
  ll([(12,1),(12,2)])
  pp([(-2,-10),(2,-11),(4,-7),(2,-4),(-1,-6)],4)
  ll([(-1,-9),(1,-9)],6)
 # Tiny passengers are authored pixel clusters, never a reduced portrait.
 if f['group']=='passengers':
  rows=(['0000111111100000','0011444544710000','0144544477c71000','14444247cf771100','14444777ff771a10','014477711771aa10','001112cc112aa110','0000011100111100'] if w==16 else ['00111100','01445710','14477c71','1447cf71','14777771','014c1a10','0011aa10','00011100'])
  im.putdata([int(c,16) for row in rows for c in row]);return im
 if f['group']=='reunion':
  big=f['index']!=0;superfinal=f['index']==2
  if superfinal:
   # Forward flying celebration, face turns toward the incoming scene.
   tail([(w*.45,h*.62),(w*.78,h*.53),(w*.92,h*.63),(w*.69,h*.7),(w*.55,h*.76)])
   poly([(57,68),(83,62),(99,78),(87,99),(61,96),(42,84)],4)
   poly([(53,70),(70,70),(76,83),(63,92),(48,85)],7)
   line([(51,78),(67,82)],8,2)
   poly([(81,86),(106,76),(114,85),(95,103),(79,99)],3)
   boot(109,81,2)
   poly([(60,71),(40,83),(31,105),(22,101),(31,76),(48,62)],4)
   glove(23,108,2.2,True)
   poly([(75,73),(99,73),(107,78),(99,83),(77,83)],7)
   glove(107,77,2)
   head(52,62,2.3,-1,2)
  else:
   s=1.2 if not big else 2.1
   cx=w*.50;cy=h*.30
   tail([(cx-4*s,cy+12*s),(cx-13*s,cy+18*s),(cx-18*s,cy+29*s),(cx-12*s,cy+26*s),(cx+1*s,cy+22*s)])
   poly([(cx-6*s,cy+5*s),(cx+6*s,cy+6*s),(cx+7*s,cy+20*s),(cx,cy+26*s),(cx-9*s,cy+20*s)],4)
   ellipse((cx-4*s,cy+8*s,cx+5*s,cy+22*s),7)
   for y in (12,17):line([(cx-3*s,cy+y*s),(cx+4*s,cy+(y+1)*s)],8,max(1,round(s*.6)))
   poly([(cx-6*s,cy+21*s),(cx-1*s,cy+23*s),(cx-5*s,cy+34*s),(cx-10*s,cy+34*s)],3)
   poly([(cx+2*s,cy+21*s),(cx+7*s,cy+19*s),(cx+5*s,cy+23*s),(cx+1*s,cy+24*s)],4)
   boot(cx-6*s,cy+33*s,s*.8);boot(cx+3*s,cy+23*s,s)
   poly([(cx-6*s,cy+8*s),(cx-12*s,cy+5*s),(cx-15*s,cy-5*s),(cx-11*s,cy-7*s),(cx-7*s,cy+2*s)],4)
   glove(cx-14*s,cy-6*s,s,True)
   poly([(cx+5*s,cy+8*s),(cx+9*s,cy+12*s),(cx+10*s,cy+7*s),(cx+7*s,cy+4*s)],4)
   glove(cx+10*s,cy+8*s,s*.9)
   head(cx,cy,s,1,2 if f['form']=='gilded' else 0)
 elif f['group']=='float2':
  phase=f['index']-200
  # Native Float2 turns in depth (yaw), rather than tumbling in screen space.
  # Independent side, front and back clusters retain full-size head/mitten mass.
  cx=[34,22,20,18,14,22,20,18][phase];cy=12
  if phase in (0,1,3,4):
   face=1 if phase<2 else -1
   rear=cx-face*14
   tail([(cx-face*4,15),(rear,11),(rear-face*6,17),(rear,17),(cx,20)])
   poly([(cx-face*9,12),(cx+face*2,13),(cx+face*3,20),(cx-face*11,20)],4)
   boot(rear,18,.65);boot(rear+face*7,14 if phase in (0,4) else 18,.65)
   head(cx,cy,.78,face)
   glove(cx+face*5,20,.65);glove(cx-face*3,19,.6)
  else:
   back=phase>=5
   for x in (cx-7,cx+6):boot(x,18,.65)
   poly([(cx-6,12),(cx+6,12),(cx+8,20),(cx-8,20)],3)
   ellipse((cx-9,1,cx+9,18),3)
   for row,y in enumerate((4,9,14)):
    for x in ((cx-6,cx) if row%2==0 else (cx-3,cx+3)):
     poly([(x-2,y),(x,y-2),(x+4,y-1),(x+5,y+2),(x+1,y+4),(x-2,y+2)],4,2)
     line([(x,y-1),(x+2,y-1)],6)
   if not back:
    poly([(cx-6,9),(cx,7),(cx+6,9),(cx+7,15),(cx,19),(cx-7,15)],7)
    for x in (cx-4,cx+3):
     ellipse((x-2,7,x+2,13),12)
     line([(x,8),(x,11)],15);d.point((x,8),fill=12)
    poly([(cx-2,15),(cx+3,15),(cx+1,17)],1)
    d.point((cx+1,17),fill=14)
   elif phase!=6:
    side=-1 if phase==5 else 1
    poly([(cx+side*6,8),(cx+side*10,11),(cx+side*9,15),(cx+side*6,16)],7)
   if back:
    glove(cx-11,16,.5);glove(cx+11,16,.5)
   else:
    glove(cx-9,20,.65);glove(cx+9,20,.65)
 elif f['group']=='transformation':
  phase=f['index']-210;cx=w/2;cy=14 if phase else 12
  tail([(cx-3,cy+10),(cx-11,cy+13),(cx-13,cy+20),(cx-7,cy+18),(cx+2,cy+14)])
  poly([(cx-6,cy+4),(cx+5,cy+4),(cx+7,cy+16),(cx,cy+20),(cx-7,cy+16)],4)
  ellipse((cx-3,cy+6,cx+4,cy+16),7)
  for x,dy in [(-5,0),(5,-1)]:
   poly([(cx+x-2,cy+15),(cx+x+2,cy+15),(cx+x+2,cy+23+dy),(cx+x-3,cy+23+dy)],3)
   boot(cx+x,cy+23+dy,.72)
  if phase>=2:
   for side in (-1,1):
    poly([(cx+side*4,cy+7),(cx+side*11,cy+1),(cx+side*12,cy+5),(cx+side*6,cy+12)],4)
    glove(cx+side*11,cy+(1 if phase%2 else 3),.75)
  else:
   for side in (-1,1):glove(cx+side*7,cy+10,.75)
  head(cx,cy,.72 if phase==0 else .88,1,phase)
 else:
  phase=f['index']-1 if f['group']=='trio' else 0;cx=16 if w==32 else 23;cy=13
  tail([(cx-4,cy+9),(cx-12,cy+13),(cx-14,cy+23),(cx-8,cy+20),(cx+1,cy+15)])
  poly([(cx-6,cy+3),(cx+5,cy+4),(cx+6,cy+15),(cx,cy+19),(cx-7,cy+15)],4)
  ellipse((cx-3,cy+5,cx+4,cy+15),7)
  line([(cx-2,cy+10),(cx+3,cy+11)],8)
  for x,dy in [(-4,0),(5,-1 if phase in (1,3) else 0)]:
   poly([(cx+x-2,cy+15),(cx+x+2,cy+15),(cx+x+2,cy+21+dy),(cx+x-2,cy+21+dy)],3)
   boot(cx+x,cy+20+dy,.75)
  glove(cx-7,cy+11,.75)
  glove(cx+7,cy+9,.75)
  head(cx,cy,.9,1)
  if f['group']=='trio' and phase>=2:
   poly([(cx+3,cy+10),(30,23),(30,20),(cx+3,cy+7)],4)
   glove(30,21,.7,True)
 # Keep only pixels addressable by the native pieces. Re-ink the inside edge
 # after layout clipping so no coloured contour is left open.
 mask=Image.new('1',(w,h));md=ImageDraw.Draw(mask);hx,hy=f['hotspot']
 for x,y,tw,th,*_ in f['pieces']:md.rectangle((x+hx,y+hy,x+hx+tw*8-1,y+hy+th*8-1),fill=1)
 for y in range(h):
  for x in range(w):
   if not mask.getpixel((x,y)):im.putpixel((x,y),0)
 old=im.copy()
 for y in range(h):
  for x in range(w):
   if old.getpixel((x,y)) and any(not (0<=x+dx<w and 0<=y+dy<h) or old.getpixel((x+dx,y+dy))==0 for dx,dy in ((0,1),(0,-1),(1,0),(-1,0))):im.putpixel((x,y),1)
 return im

TIMINGS={
 'reunion':dict(source='off_5E328',normal=[dict(index=0,ticks=6),dict(index=1,ticks='hold')],powered=dict(coverage_only=[0,1],final_index=2,slide_pixels_per_tick=8,after_pause_ticks=32)),
 'passengers':dict(source='sub_5FD88',selector='22 + ((V_int_run_count >> 1) & 1)',ticks_per_frame=2),
 'trio':dict(source='AniRaw_6025C / AniRaw_60260',idle=dict(frames=[1,2],ticks=16),celebrate=dict(frames=[1,1,3,4],ticks=8,end='hold 4')),
 'float2':dict(source='AniSonic0F',frames=list(range(200,208)),ticks_per_frame=8,loop=True),
 'ride':dict(source='loc_5D8BC / sub_5FD88 / off_5E328',frames=[186],end='hold during rescue and credits'),
 'transformation':dict(source='AniSonic1F',frames=[210,210,211,211,212,213,214,213,214,213,214,213,214],ticks_per_frame=3,end='change animation 0')}

def verify(frames):
 banks={}
 for f in frames:
  im=Image.open(HERE/f['file']);assert im.mode=='P' and im.info['transparency']==0
  assert im.getpalette()[:48]==sum(COLORS,[]) and max(im.get_flattened_data())<16
  assert list(im.size)==f['dimensions'] and im.getbbox()
  # Native alias consistency within each static bank; dynamic frames have an
  # independently streamed tile address space. Original tile capacity retained.
  bank=f['id'];tiles=banks.setdefault(bank,{})
  hx,hy=f['hotspot'];reconstructed=b.indexed(im.size)
  for x,y,w,h,t,fx,fy,*_ in f['pieces']:
   piece=im.crop((x+hx,y+hy,x+hx+w*8,y+hy+h*8));reconstructed.paste(piece,(x+hx,y+hy))
   if fx:piece=piece.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
   if fy:piece=piece.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
   for tx in range(w):
    for ty in range(h):
     n=t+tx*h+ty;v=piece.crop((tx*8,ty*8,tx*8+8,ty*8+8)).tobytes()
     assert n not in tiles or tiles[n]==v,(f['id'],'tile alias',n)
     tiles[n]=v
  assert reconstructed.tobytes()==im.tobytes(),f['id']
  if 'tile_budget' in f:assert max(tiles)<f['tile_budget']
 print(f'Verified {len(frames)} indexed frames: palette, dimensions, piece coverage, aliases, DPLC capacity')

def main():
 p=argparse.ArgumentParser();p.add_argument('--group',choices=GROUPS);p.add_argument('--review',type=Path);p.add_argument('--verify',action='store_true');p.add_argument('--references-only',action='store_true');a=p.parse_args()
 if a.references_only:
  for f in definitions():
   ref=reference(f);ref.resize((ref.width*4,ref.height*4),Image.Resampling.NEAREST).save(a.review/(f['id']+'-native.png'))
  return
 path=HERE/'s3k.yaml'
 if a.verify:
  verify(json.loads(path.read_text())['frames']);return
 frames=definitions();chosen=[f for f in frames if not a.group or f['group']==a.group]
 for f in chosen:
  f['file']=f['id']+'.png';draw(f).save(HERE/f['file'],bits=4,transparency=0)
 if path.exists():
  old=json.loads(path.read_text())['frames'];chosen= [f for f in old if f['id'] not in {x['id'] for x in chosen}]+chosen
 chosen.sort(key=lambda f:list(GROUPS).index(f['group'])*1000+frames.index(next(x for x in frames if x['id']==f['id'])))
 manifest=dict(format='openggf-ending-art-s3k-1',game='s3k_locked_on',tick_rate=60,piece_format=['x_px','y_px','width_tiles','height_tiles','tile_index','xflip','yflip','relative_palette_line','priority'],palette='../../palette.yaml',art_binding='Per-frame art payloads, native piece geometry. Static mapping tile indices overlap across selectors; compile/rebase per-frame tiles in a replacement bank, do not overwrite all selectors at their source offsets. Super slots 0/1 are conservative coverage only: native Super art interpreted through these maps is not a coherent pose.',palette_binding='Preserve all MICA indices. Reunion object base line 2; other player surfaces line 0. Native piece palette bits are relative (0). Reserve/remap companions and plane/shared line users in engine; do not recolor their tiles or quantize MICA into blue.',gilded=dict(palette='../../presentation/gilded.yaml',cycle='cycles.s3k',png_storage='Normal MICA indexed palette; apply Gilded cycle to indices 2..6 at runtime. Review displays first S3K gold cycle. Transformation uses fade_in then steady cycle.',shared_float2_frames=list(range(200,208))),timing=TIMINGS,frames=chosen)
 path.write_text(json.dumps(manifest,indent=2)+'\n');verify(chosen)
 if a.review:
  for group in GROUPS:
   subset=[f for f in chosen if f['group']==group]
   if subset:review(a.review,group,subset)
if __name__=='__main__':main()
