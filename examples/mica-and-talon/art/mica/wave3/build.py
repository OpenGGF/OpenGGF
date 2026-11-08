#!/usr/bin/env python3
"""Purpose: hand-place MICA wave-3 pixels; decode pinned native art for review only.
Inputs: unchanged MICA palette, S2 obj09 mapping/DPLC/animations and Nemesis art.
Origin: MICA art lane round 4, 2026-11-08. No Java, generated imagery or ROM pixels
enter the original output. Run with python3 -I <this file> --review /tmp/....
"""
import argparse, importlib.util, json, math, re
from pathlib import Path
from PIL import Image, ImageDraw
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[4]
ART=HERE.parent
COLORS=json.loads((ART/'palette.yaml').read_text())['colors']
def num(v):
 v=v.strip();return -num(v[1:]) if v.startswith('-') else int(v.replace('$','0x'),0) if '$' in v else int(v)
def indexed(size,colors=COLORS):
 im=Image.new('P',size);im.putpalette(sum(colors,[])+[0]*(768-48));im.info['transparency']=0;return im
def pal(path,line=0):
 raw=path.read_bytes()[line*32:(line+1)*32]
 return [[round(((int.from_bytes(raw[i:i+2],'big')>>s)&14)*255/14) for s in (0,4,8)] for i in range(0,32,2)]
def nem(path):
 spec=importlib.util.spec_from_file_location('nem',ROOT/'tools/timing/s3k-instruction-ledgers/vint/nem/nem_reference.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
 return b''.join(r.to_bytes(4,'big') for r in m.decode(path.read_bytes()+bytes(8),0))
def native(pieces,data,colors):
 im=indexed((96,96),colors)
 for x,y,w,h,t,fx,fy,*_ in pieces:
  p=indexed((w*8,h*8),colors)
  for tx in range(w):
   for ty in range(h):
    raw=data[(t+tx*h+ty)*32:(t+tx*h+ty+1)*32];assert len(raw)==32
    tile=indexed((8,8),colors);tile.putdata([n for b in raw for n in (b>>4,b&15)]);p.paste(tile,(tx*8,ty*8))
  if fx:p=p.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
  if fy:p=p.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
  im.paste(p,(48+x,48+y),p.point(lambda i:255 if i else 0, mode='L'))
 return im

def back(phase,angle=0,ball=False):
 """Pixel-coordinate polygons, never resample a bitmap. Stride has four poses."""
 im=indexed((96,96));d=ImageDraw.Draw(im);c=math.cos(math.radians(angle));s=math.sin(math.radians(angle))
 def pts(p):return [(48+round(x*c-y*s)-(5 if angle else 0),48+round(x*s+y*c)-(2 if angle==45 else 0)) for x,y in p]
 def poly(p,fill,outline=1):d.polygon(pts(p),fill=fill,outline=outline)
 def line(p,fill,width=1):d.line(pts(p),fill=fill,width=width)
 if ball:
  # Symmetric so the native mirrored-half mapping can represent it exactly.
  for y in range(-19,20):
   for x in range(-16,16):
    if ((x+.5)/15)**2+(y/19)**2<=1:im.putpixel((48+x,48+y),2)
  for y in range(-16,18,6):
   for x in (-11,-3,5):
    poly([(x,y),(x+4,y-2),(x+8,y),(x+6,y+4),(x+2,y+5)],4+phase%2,2)
    line([(x+1,y),(x+4,y-1),(x+6,y)],6)
  # Enforce mirrored tile pairs, leaving transparent corners.
  for y in range(96):
   for x in range(48):im.putpixel((95-x,y),im.getpixel((x,y)))
  for y in range(96):
   for x in range(96):
    if ((x-47.5)/15)**2+((y-48)/19)**2>1:im.putpixel((x,y),0)
  # One-pixel ink boundary inside the oval.
  old=im.copy()
  for y in range(1,95):
   for x in range(1,95):
    if old.getpixel((x,y)) and any(not old.getpixel((x+dx,y+dy)) for dx,dy in ((1,0),(-1,0),(0,1),(0,-1))):im.putpixel((x,y),1)
  return im
 # Four independently placed support/lift extremes; coral boots outside the tail.
 lift=[(0,-6),(-2,-3),(-4,2),(-5,1),(-6,0),(-3,-2),(2,-4),(1,-5)][phase%8]
 for side,dy in ((-1,lift[0]),(1,lift[1])):
  x=side*7
  poly([(x-3,9),(x+3,9),(x+4,19+dy),(x-4,19+dy)],3)
  poly([(x-5,17+dy),(x+4,16+dy),(x+6,21+dy),(x+4,25+dy),(x-6,25+dy)],10)
  line([(x-5,24+dy),(x+4,24+dy)],9)
  line([(x-3,18+dy),(x+2,18+dy)],12)
  line([(x-2,20+dy),(x+1,21+dy)],5)
 # Swinging arms/gloves frame the back, white silhouette remains clear at 1x.
 for side,dy in ((-1,lift[1]//2),(1,lift[0]//2)):
  x=side*11
  poly([(x-2,-8),(x+3,-7),(x+4,3+dy),(x-3,5+dy)],4)
  poly([(x-3,dy),(x+3,dy-1),(x+5,3+dy),(x+3,8+dy),(x-3,7+dy),(x-4,4+dy)],12)
  line([(x-2,6+dy),(x+2,6+dy)],13)
 poly([(-8,-7),(7,-7),(10,5),(6,14),(0,19),(-7,13),(-10,4)],3)
 for y in (-3,3,8):
  for x in (-6,1):
   poly([(x,y),(x+3,y-1),(x+6,y+1),(x+3,y+4),(x,y+3)],4,2)
   line([(x+1,y),(x+3,y),(x+4,y+1)],5)
 # Tapered hanging tail, clearly separated from boots.
 poly([(-4,7),(4,7),(5,14),(2,20),(-1,25),(-4,18),(-6,12)],4,2)
 for y in (10,15,20):line([(-3,y),(0,y+2),(3 if y<20 else 0,y)],6)
 # Rounded scale hood, original pangolin outline rather than Sonic quills.
 poly([(-11,-12),(-12,-20),(-8,-26),(-2,-28),(5,-27),(10,-23),(12,-16),(10,-9),(5,-5),(-4,-5)],3)
 for row,y in enumerate((-24,-18,-12)):
  for x in ((-7,1) if row%2==0 else (-9,-1,7)):
   poly([(x,y),(x+4,y-2),(x+7,y+1),(x+5,y+5),(x+1,y+5),(x-1,y+2)],4,2)
   line([(x,y),(x+3,y-1),(x+5,y)],6)
 # Cream cheek and a single friendly eye only on leaning three-quarter poses.
 if angle:
  poly([(8,-21),(12,-18),(15,-16),(15,-13),(10,-11),(8,-15)],7)
  poly([(10,-19),(12,-18),(12,-15),(10,-15)],12)
  line([(12,-18),(12,-16)],1)
 return im

def crop_mapping(im,pieces):
 box=(min(p[0] for p in pieces),min(p[1] for p in pieces),max(p[0]+p[2]*8 for p in pieces),max(p[1]+p[3]*8 for p in pieces))
 mask=Image.new('1',im.size);d=ImageDraw.Draw(mask)
 for x,y,w,h,*_ in pieces:d.rectangle((48+x,48+y,47+x+w*8,47+y+h*8),fill=1)
 out=indexed(im.size);out.paste(im,mask=mask)
 return out.crop(tuple(v+48 for v in box)),[-box[0],-box[1]],box

def review(dest,key,frames,refs,sequences):
 dest.mkdir(parents=True,exist_ok=True)
 for scale in (1,4):
  sheet=Image.new('RGB',(210*scale,116*len(frames)*scale),'#969696')
  for n,(f,ref) in enumerate(zip(frames,refs)):
   page=Image.new('RGB',(210,116),'#969696');d=ImageDraw.Draw(page)
   mica=Image.open(HERE/f['file']).convert('RGBA');page.paste(mica,(48-f['hotspot'][0],48-f['hotspot'][1]),mica)
   rgba=ref.convert('RGBA');page.paste(rgba,(106,0),rgba)
   d.text((2,96),f'{n:02} MICA',fill='black');d.text((110,96),'native Sonic',fill='black')
   sheet.paste(page.resize((210*scale,116*scale),Image.Resampling.NEAREST),(0,n*116*scale))
  sheet.save(dest/f'{key}-{scale}x.png')
 for name,indices,ticks in sequences:
  pages=[]
  for i in indices:
   page=Image.new('RGB',(202,96),'#969696');im=Image.open(HERE/frames[i]['file']).convert('RGBA');page.paste(im,(48-frames[i]['hotspot'][0],48-frames[i]['hotspot'][1]),im);r=refs[i].convert('RGBA');page.paste(r,(106,0),r);pages.append(page.resize((808,384),Image.Resampling.NEAREST))
  pages[0].save(dest/f'{key}-{name}.gif',save_all=True,append_images=pages[1:],duration=round(ticks*1000/60),loop=0,disposal=2)

def s2(dest):
 text=(ROOT/'docs/s2disasm/mappings/sprite/obj09.asm').read_text();asm=(ROOT/'docs/s2disasm/s2.asm').read_text()
 labels=re.findall(r'mappingsTableEntry.w\s+(\w+)',text);frames=[];refs=[]
 raw=nem(ROOT/'docs/s2disasm/art/nemesis/Sonic and Tails animation frames in special stage.nem') if dest else None
 fixed=pal(ROOT/'docs/s2disasm/art/palettes/Special Stage Main.bin',1)
 # Keep shared palette untouched; optional fixed-line approximation is explicit.
 lut=[0]+[min(range(1,16),key=lambda j:sum((COLORS[i][k]-fixed[j][k])**2 for k in range(3))) for i in range(1,16)]
 for i,label in enumerate(labels):
  block=text.split(label+':')[1].split(label+'_End')[0];pieces=[list(map(num,a.split(','))) for a in re.findall(r'spritePiece\s+([^\n;]+)',block)]
  plc=asm.split(f'.sonic_{i}:')[1].split(f'.sonic_{i}_End')[0];loads=[list(map(num,a)) for a in re.findall(r'dplcEntry\s+([^,]+),\s*([^\s;]+)',plc)]
  angle=0 if i<4 or i>=16 else 45 if i<12 else 90
  im=back((i-4) if 4<=i<12 else (i%4)*2,angle,i>=16);out,hot,box=crop_mapping(im,pieces)
  file=f's2-halfpipe-{i:02}.png';out.save(HERE/file,bits=4,transparency=0)
  q=indexed(out.size,fixed);q.putdata([lut[v] for v in out.get_flattened_data()]);q.save(HERE/f's2-halfpipe-{i:02}-fixed.png',bits=4,transparency=0)
  frames.append(dict(id=i,file=file,dimensions=list(out.size),hotspot=hot,native_source_label=label,pieces=pieces,dplc_label=f'Obj09_MapRUnc_345FA.sonic_{i}',dplc_entries=loads,tile_budget=sum(n for n,t in loads),fixed_palette_file=f's2-halfpipe-{i:02}-fixed.png'))
  if dest:
   base=0 if i<4 else 0x58 if i<12 else 0x124 if i<16 else 0x171
   data=b''.join(raw[(base+t)*32:(base+t+n)*32] for n,t in loads);refs.append(native(pieces,data,fixed))
 sequences=[('upright',list(range(4)),4),('diagonal',list(range(4,12)),4),('horizontal',list(range(12,16)),4),('ball',[16,17],2),('hurt',[0,4,12,4,0,4,12,4],4)]
 surface=dict(surface='half_pipe',game='s2',native_source_label='Obj09 / Obj09_MapUnc_34212 / Obj09_MapRUnc_345FA / off_341E4',source='docs/s2disasm/s2.asm',mapping_source='docs/s2disasm/mappings/sprite/obj09.asm',native_art_label='ArtNem_SpecialSonicAndTails',palette_line=1,palette='../palette.yaml',fixed_palette=dict(source='docs/s2disasm/art/palettes/Special Stage Main.bin',line=1,colors=fixed,index_lut=lut,note='Nearest RGB approximation loses jade and cream: native Sonic line has no green. Shared-palette originals require a character palette binding; fixed variants are layout-compatible but not colour-faithful.'),timing=dict(source='byte_341EE / byte_341F4 / byte_341FE / byte_34204 / byte_34208 / SSHurt_Animation',run_ticks=4,ball_ticks=2,hurt='SSHurt_Animation uses angle + timer in 8-unit steps and render flips; no dedicated hurt art'),sequences=[dict(name=n,frames=f,ticks=t) for n,f,t in sequences],frames=frames)
 if dest:review(dest,'s2-halfpipe',frames,refs,sequences)
 return surface

def main():
 p=argparse.ArgumentParser();p.add_argument('--review',type=Path);a=p.parse_args()
 manifest=dict(format='openggf-wave3-art-handoff-1',palette='../palette.yaml',tick_rate=60,notes='Original hand-authored native-coordinate pixel source. Not GGFP or runtime certification.',surfaces=[s2(a.review)])
 (HERE/'wave3.yaml').write_text(json.dumps(manifest,indent=2)+'\n')
if __name__=='__main__':main()
