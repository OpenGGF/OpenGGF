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
 if colors and isinstance(colors[0][0], list):
  # Explicit banks: preserve mapping palette selection instead of flattening.
  out=Image.new('RGBA',(96,96))
  for p in reversed(pieces):
   line=p[7] if len(p)>7 else 0
   tile=native([p],data,colors[line]).convert('RGBA')
   out.alpha_composite(tile)
  return out
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

def back(phase,angle=0,ball=False,yscale=1,yoffset=0):
 """Pixel-coordinate polygons, never resample a bitmap. Stride has four poses."""
 im=indexed((96,96));d=ImageDraw.Draw(im);c=math.cos(math.radians(angle));s=math.sin(math.radians(angle))
 def pts(p):return [(48+round(x*c-y*s)-(5 if angle else 0),48+round((x*s+y*c)*yscale)+yoffset-(2 if angle==45 else 0)) for x,y in p]
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
  for step,i in enumerate(indices):
   page=Image.new('RGB',(202,96),'#969696');im=Image.open(HERE/frames[i]['file']).convert('RGBA');left=Image.new('RGBA',(96,96));left.alpha_composite(im,(48-frames[i]['hotspot'][0],48-frames[i]['hotspot'][1]));r=refs[i].convert('RGBA')
   if name=='hurt':
    fx,fy=[(1,0),(0,0),(0,0),(0,0),(0,1),(0,1),(1,1),(1,0)][step]
    if fx:left=left.transpose(Image.Transpose.FLIP_LEFT_RIGHT);r=r.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
    if fy:left=left.transpose(Image.Transpose.FLIP_TOP_BOTTOM);r=r.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
   page.paste(left,(0,0),left);page.paste(r,(106,0),r);pages.append(page.resize((808,384),Image.Resampling.NEAREST))
  pages[0].save(dest/f'{key}-{name}.gif',save_all=True,append_images=pages[1:],duration=round(ticks*1000/60),loop=0,disposal=2)

def s2(dest):
 text=(ROOT/'docs/s2disasm/mappings/sprite/obj09.asm').read_text();asm=(ROOT/'docs/s2disasm/s2.asm').read_text()
 labels=re.findall(r'mappingsTableEntry.w\s+(\w+)',text);frames=[];refs=[]
 raw=nem(ROOT/'docs/s2disasm/art/nemesis/Sonic and Tails animation frames in special stage.nem') if dest else None
 fixed=pal(ROOT/'docs/s2disasm/art/palettes/Special Stage Main.bin',1)
 # Keep shared palette untouched; optional fixed-line approximation is explicit.
 lut=[0,1,2,3,4,5,6,14,15,15,12,13,8,9,14,1]
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
 sequences=[('upright',list(range(4)),4),('diagonal',list(range(4,12)),4),('horizontal',list(range(12,16)),4),('ball',[16,17],2),('hurt',[4,0,4,12,4,0,4,12],4)]
 surface=dict(surface='half_pipe',game='s2',native_source_label='Obj09 / Obj09_MapUnc_34212 / Obj09_MapRUnc_345FA / off_341E4',source='docs/s2disasm/s2.asm',mapping_source='docs/s2disasm/mappings/sprite/obj09.asm',native_art_label='ArtNem_SpecialSonicAndTails',palette_line=1,palette='../palette.yaml',fixed_palette=dict(source='docs/s2disasm/art/palettes/Special Stage Main.bin',line=1,colors=fixed,index_lut=lut,note='Semantic fixed-line quantization maps jade ramps to blue and preserves red boots/white gloves; native Sonic line has no green. Shared-palette originals require a character palette binding; fixed variants are layout-compatible but not colour-faithful.'),timing=dict(source='byte_341EE / byte_341F4 / byte_341FE / byte_34204 / byte_34208 / SSHurt_Animation',run_ticks=4,ball_ticks=2,hurt='SSHurt_Animation uses angle + timer in 8-unit steps and render flips; no dedicated hurt art',hurt_flips=[[1,0],[0,0],[0,0],[0,0],[0,1],[0,1],[1,1],[1,0]]),sequences=[dict(name=n,frames=f,ticks=t) for n,f,t in sequences],frames=frames)
 if dest:
  review(dest,'s2-halfpipe',frames,refs,sequences)
  review(dest,'s2-halfpipe-fixed',[dict(f,file=f['fixed_palette_file']) for f in frames],refs,sequences)
 return surface

def blue(dest):
 path='docs/skdisasm/General/Sprites/Sonic/Map - SStage Sonic.asm'
 text=(ROOT/path).read_text()
 maps=re.findall(r'dc.w (word_\w+)-Map_SStageSonic_',text)
 plcs=re.findall(r'dc.w (word_\w+)-PLC_SStageSonic_',text)
 fixed=pal(ROOT/'docs/skdisasm/General/Special Stage/Palettes/Main.bin')
 lut=[0,11,11,10,9,8,15,14,13,13,2,4,1,15,14,5]
 raw=(ROOT/'docs/skdisasm/General/Sprites/Sonic/Art/SStage Sonic.unc').read_bytes() if dest else None
 frames=[];refs=[]
 def block(label):return text.split(label+':')[1].split('\nword_')[0]
 for i,(label,plc) in enumerate(zip(maps,plcs)):
  pieces=[]
  for args in re.findall(r'dc.b([^\n]+)',block(label)):
   y,size,hi,lo,xhi,xlo=map(num,args.split(','));x=(xhi<<8)|xlo;t=(hi<<8)|lo
   pieces.append([x-65536 if x>=32768 else x,y-256 if y>=128 else y,(size>>2)+1,(size&3)+1,t&2047,int(bool(t&2048)),int(bool(t&4096)),(t>>13)&3,1])
  vals=[num(v) for v in re.findall(r'dc.w\s+(\$[0-9A-F]+|\d+)',block(plc))];loads=[[(v>>12)+1,v&4095] for v in vals[1:]];assert len(loads)==vals[0]
  if not pieces:
   out=indexed((1,1));hot=[0,0]
  else:
   if i<9:
    im=back([0,0,0,2,4,6,2,4,6][i],yscale=.72 if i==1 else .88,yoffset=-15 if i==1 else -17)
    # Native lower pieces alternate a long support boot and a raised boot.
    # Explicit coordinates keep soles continuous inside the narrow 8/16px pieces.
    d=ImageDraw.Draw(im);d.rectangle((0,38,95,95),fill=0)
    def poly(points,fill,outline=1):d.polygon([(48+x,48+y) for x,y in points],fill=fill,outline=outline)
    if i in (1,2):boots=[(-10,-4,0),(2,-5,0)]
    elif i in (3,4):boots=[(-11,3,0),(2,-10,0)]
    elif i==5:boots=[(-9,3,1),(2,-10,0)]
    elif i in (6,7):boots=[(4,3,0),(-9,-10,0)]
    else:boots=[(1,3,1),(-9,-10,0)]
    poly([(-7,-12),(7,-12),(6,-5),(0,-1),(-6,-5)],3)
    for x,y,narrow in boots:
     w=6 if narrow else 8
     poly([(x+3,-11),(x+w-1,-10),(x+w-3,-3),(x+w-1,y+2),(x+1,y+2),(x,-3)],4)
     poly([(x,y),(x+w-2,y),(x+w,y+3),(x+w,y+7),(x,y+7)],10)
     d.line([(48+x+1,48+y+6),(48+x+w-1,48+y+6)],fill=9)
     d.line([(48+x+1,48+y+1),(48+x+w-3,48+y+1)],fill=12)
    poly([(-3,-10),(3,-10),(2,-5),(0,0),(-2,-4)],4,2)
    d.line([(46,40),(48,42),(50,40)],fill=6)
   else:
    im=indexed((96,96));d=ImageDraw.Draw(im)
    d.ellipse((33,11,62,41),fill=3,outline=1)
    for y in range(13+(i-9)*2,39,6):
     for x in range(35 if (y//6)%2 else 39,60,8):
      d.polygon([(x,y),(x+3,y-1),(x+6,y+1),(x+5,y+4),(x+2,y+5),(x,y+3)],fill=4,outline=2);d.line([(x+1,y),(x+3,y),(x+5,y+1)],fill=6)
    # Preserve the circular ink contour; scales stay within the curl.
    for y in range(96):
     for x in range(96):
      r=((x-47.5)/15)**2+((y-26)/15.5)**2
      if r>1:im.putpixel((x,y),0)
      elif r>.85:im.putpixel((x,y),1)
   out,hot,_=crop_mapping(im,pieces)
  file=f's3k-blue-sphere-{i:02}.png';out.save(HERE/file,bits=4,transparency=0)
  q=indexed(out.size,fixed);q.putdata([lut[v] for v in out.get_flattened_data()]);q.save(HERE/f's3k-blue-sphere-{i:02}-fixed.png',bits=4,transparency=0)
  frames.append(dict(id=i,file=file,dimensions=list(out.size),hotspot=hot,native_source_label=label,pieces=pieces,dplc_label=plc,dplc_entries=loads,tile_budget=sum(n for n,t in loads),fixed_palette_file=f's3k-blue-sphere-{i:02}-fixed.png'))
  if dest:refs.append(native(pieces,b''.join(raw[t*32:(t+n)*32] for n,t in loads),fixed))
 sequences=[('run',[2,6,7,8,7,6,2,3,4,5,4,3],5),('jump',[9,11,10,11],5),('stationary',[1],20)]
 surface=dict(surface='blue_sphere',game='s3k',native_source_label='Map_SStageSonic / PLC_SStageSonic / ArtUnc_SStageSonic / byte_91E8 / byte_91F6',source='docs/skdisasm/sonic3k.asm',mapping_source=path,palette='../palette.yaml',palette_line=0,fixed_palette=dict(source='docs/skdisasm/General/Special Stage/Palettes/Main.bin',line=0,colors=fixed,index_lut=lut,note='Semantic fixed-line quantization maps jade to blue, preserves red boots/white gloves; shared-palette originals require a character palette binding.'),frame_count_correction='Pinned S3K has 12 mapping entries (0 blank, 1 standing, 2-8 run, 9-11 curl), not 24. The run sequence has 12 steps and jumping another 12 steps, reusing these entries. Turning rotates the stage via Special_stage_angle; no additional player turn art.',timing=dict(source='loc_907E / loc_90A8 / byte_91E8 / byte_91F6 / sub_9580',run='anim_frame_timer += Special_stage_velocity >> 5; wrap 12; preview ticks illustrative',jump='same phase timer; stationary jump uses Level_frame_counter & 3'),sequences=[dict(name=n,frames=f,preview_ticks=t) for n,f,t in sequences],frames=frames)
 if dest:
  review(dest,'s3k-blue-sphere',frames,refs,sequences)
  review(dest,'s3k-blue-sphere-fixed',[dict(f,file=f['fixed_palette_file']) for f in frames],refs,sequences)
 return surface

def s1_audit(dest):
 maptext=(ROOT/'docs/s1disasm/_maps/Sonic.asm').read_text();plctext=(ROOT/'docs/s1disasm/_maps/Sonic - Dynamic Gfx Script.asm').read_text()
 raw=(ROOT/'docs/s1disasm/artunc/Sonic.unc').read_bytes() if dest else None
 fixed=pal(ROOT/'docs/s1disasm/palette/Special Stage.bin')
 core=[f for f in json.loads((ART/'frames.yaml').read_text())['frames'] if f['animation']=='ROLL']
 native_frames=[];comparisons=[];refs=[]
 for i in range(5):
  key=f'Roll{i+1}'
  block=maptext.split('MS_'+key+':')[1].split('MS_'+key+'_End')[0]
  pieces=[list(map(num,a.split(','))) for a in re.findall(r'spritePiece\s+([^\n;]+)',block)]
  block=plctext.split('SonPLC_'+key+':')[1].split('SonPLC_'+key+'_End')[0]
  loads=[list(map(num,a)) for a in re.findall(r'dplcEntry\s+([^,]+),\s*([^\s;]+)',block)]
  native_frames.append(dict(id=46+i,native_source_label='MS_'+key,dplc_label='SonPLC_'+key,pieces=pieces,dplc_entries=loads,tile_budget=sum(n for n,t in loads)))
  f=core[i%4];comparisons.append(dict(id=f['id'],file='../'+f['file'],hotspot=f['hotspot']))
  if dest:refs.append(native(pieces,b''.join(raw[t*32:(t+n)*32] for n,t in loads),fixed))
 if dest:review(dest,'s1-maze-reuse',comparisons,refs,[('slow-roll',list(range(5)),5),('fast-roll',[0,1,4,2,3,4],3)])
 return dict(surface='rotating_maze',game='s1',native_source_label='SonicSS_Main / SonicSS_Control / SonicSS_Display / Sonic_Animate / Sonic_LoadGfx / Map_Sonic',source='docs/s1disasm/_incObj/09 Sonic in Special Stage.asm',mapping_source='docs/s1disasm/_maps/Sonic.asm',animation_source='docs/s1disasm/_anim/Sonic.asm',palette_line=0,palette='../palette.yaml',native_palette_source='docs/s1disasm/palette/Special Stage.bin',frames=[],native_frames=native_frames,reuse=[dict(file='../'+f['file'],hotspot=f['hotspot'],id=f['id']) for f in core],timing=dict(source='Sonic_Animate.rolljump / SonAni_Roll / SonAni_Roll2',slow_native_frames=[46,47,48,49,50],fast_native_frames=[46,47,50,48,49,50],ticks='1 + (max(0, 0x400 - abs(inertia)) >> 8), with native word arithmetic; consult owning routine for state'),audit='No dedicated maze art: normal mapping, DPLC loader and id_Roll. No additional MICA drawing needed. Existing four canonical ROLL frames reused. Native has five distinct roll mappings; GIF repeats MICA phase 0 for the fifth comparison and does not certify W2 native/canonical remapping. Stage rotates, not the playable image. Shared MICA palette must bind to player line; fixed native palette colour conversion remains W4 if the stage does not admit a custom player line.')

def main():
 p=argparse.ArgumentParser();p.add_argument('--review',type=Path);a=p.parse_args()
 manifest=dict(format='openggf-wave3-art-handoff-1',palette='../palette.yaml',tick_rate=60,notes='Original hand-authored native-coordinate pixel source. Not GGFP or runtime certification.',surfaces=[s2(a.review),blue(a.review),s1_audit(a.review)])
 (Path(__file__).resolve().parent / 'wave3.yaml').write_text(json.dumps(manifest,indent=2)+'\n')
if __name__=='__main__':main()
