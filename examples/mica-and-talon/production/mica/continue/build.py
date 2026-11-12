#!/usr/bin/env python3
"""Purpose: reproduce original MICA continue pixels and disposable native comparisons.
Inputs: unchanged MICA palette; pinned native maps/DPLC/art for layout and reviews.
Origin: MICA continue art lane, 2026-11-08. Native pixels never enter output art.
Run from repository root: python3 -I <this file> s1|s2|s3k --review /tmp/...
"""
import argparse, importlib.util, json, re
from pathlib import Path
from PIL import Image, ImageDraw
HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[4]
def module(name,path):
 s=importlib.util.spec_from_file_location(name,path);m=importlib.util.module_from_spec(s);s.loader.exec_module(m);return m
r=module('round5',HERE.parent/'wave3/round5.py');b=r.b

def macros(path):
 text=(ROOT/path).read_text();labels=re.findall(r'mappingsTableEntry.w\s+([\w.]+)',text);out=[]
 for label in labels:
  body=text.split(label+':',1)[1].split(label+'_End',1)[0]
  ps=[list(map(r.value,a.split(','))) for a in re.findall(r'spritePiece\s+([^\n;]+)',body)]
  out.append((label,ps))
 return out

def macroplc(path):
 text=(ROOT/path).read_text();out=[]
 for label in re.findall(r'mappingsTableEntry.w\s+([\w.]+)',text):
  body=text.split(label+':',1)[1].split(label+'_End',1)[0]
  out.append((label,[list(map(r.value,a)) for a in re.findall(r'dplcEntry\s+([^,]+),\s*([^\s;]+)',body)]))
 return out

def sources(game):
 if game=='s1':
  mp='docs/s1disasm/_maps/Sonic.asm';dp='docs/s1disasm/_maps/Sonic - Dynamic Gfx Script.asm'
  maps=macros(mp);plcs=macroplc(dp);raw=(ROOT/'docs/s1disasm/artunc/Sonic.unc').read_bytes();colors=[b.pal(ROOT/'docs/s1disasm/palette/Special Stage Continue Bonus.bin',line) for line in range(2)]
  dedicated=macros('docs/s1disasm/_maps/Continue Screen.asm');dr=b.nem(ROOT/'docs/s1disasm/artnem/Continue Screen Sonic.nem');ir=b.nem(ROOT/'docs/s1disasm/artnem/Continue Screen Stuff.nem')
  entries=[('floor',i,dedicated[i],dr,[],None) for i in (1,2,3)]
  entries += [('icon',i,dedicated[i],ir,[],None) for i in (6,7)]
  ids=[0x3c,0x3d,0x53,0x3e,0x54]+list(range(6,12))+list(range(0x1e,0x22))
 elif game=='s2':
  mp='docs/s2disasm/mappings/sprite/Sonic.asm';dp='docs/s2disasm/mappings/spriteDPLC/Sonic.asm'
  maps=macros(mp);plcs=macroplc(dp);raw=(ROOT/"docs/s2disasm/art/uncompressed/Sonic's art.bin").read_bytes();colors=b.pal(ROOT/'docs/s2disasm/art/palettes/SonicAndTails.bin')
  dedicated=macros('docs/s2disasm/mappings/sprite/objDA.asm');ir=b.nem(ROOT/'docs/s2disasm/art/nemesis/Sonic continue.nem')
  entries=[('icon',i,dedicated[i],ir,[],None) for i in (4,5)]
  ids=[8,9,7]+list(range(0xd,0x15))+list(range(0x2d,0x31))
 else:
  mp='docs/skdisasm/General/Sprites/Sonic/Map - Sonic.asm';dp='docs/skdisasm/General/Sprites/Sonic/DPLC - Sonic.asm'
  maps=r.mapping(mp,'Map_Sonic_');plcs=r.dplc(dp,'PLC_Sonic_');raw=(ROOT/'docs/skdisasm/General/Sprites/Sonic/Art/Sonic.unc').read_bytes();colors=b.pal(ROOT/'docs/skdisasm/General/Continue/Palettes/Palette.bin')
  dedicated=r.mapping('docs/skdisasm/General/Continue/Map - Player Sprites.asm','Map_ContinueSprites_');dr=b.nem(ROOT/'docs/skdisasm/General/Continue/Nemesis Art/Player Sprites.nem')
  icons=r.mapping('docs/skdisasm/General/Continue/Map - Player Icons.asm','Map_ContinueIcons_');ir=b.nem(ROOT/'docs/skdisasm/General/Continue/Nemesis Art/Player Icons.nem')
  entries=[('team-idle',i,dedicated[i],dr,[],None) for i in (0,1)]+[('icon',i,icons[i],ir,[],None) for i in (0,1)]
  ids=[0x5a,0x59,0x55,0x56,0x57,0xbd,0xbe,0xba]+list(range(0x21,0x25))
 for i in ids:
  label,ps=maps[i];pl,loads=plcs[i];data=b''.join(raw[t*32:(t+n)*32] for n,t in loads)
  entries.append(('player',i,(label,ps),data,loads,pl))
 return entries,colors,mp,dp

def reference_sheet(game,dest):
 entries,colors,_,_=sources(game);page=Image.new('RGB',(600,((len(entries)+5)//6)*112),'#969696');d=ImageDraw.Draw(page)
 for j,(bank,i,(label,ps),data,loads,pl) in enumerate(entries):
  im=b.native(ps,data,colors).convert('RGBA');x=(j%6)*100;y=(j//6)*112;page.paste(im,(x,y),im);d.text((x,y+96),f'{bank} {i:02x}',fill='black')
 dest.mkdir(parents=True,exist_ok=True);page.resize((1200,page.height*2),Image.Resampling.NEAREST).save(dest/f'{game}-native.png')

def draw_pose(game,bank,i,ps):
 im=b.indexed((96,96));d=ImageDraw.Draw(im)
 def poly(points,c=4,o=1):d.polygon(points,fill=c,outline=o)
 def line(points,c=2):d.line(points,fill=c)
 def glove(x,y):
  d.ellipse((x,y,x+6,y+6),fill=12,outline=1);line([(x+2,y+4),(x+4,y+4)],13)
 def boot(x,y,lift=0):
  if lift:
   poly([(x+1,y+9),(x,y+3),(x+3,y),(x+6,y+1),(x+7,y+9)],10);line([(x+5,y+2),(x+6,y+8)],9);line([(x+2,y+3),(x+4,y+3)],12)
  else:
   poly([(x,y),(x+5,y),(x+6,y+3),(x+10,y+5),(x+10,y+8),(x-1,y+8)],10);line([(x,y+7),(x+9,y+7)],9);line([(x+1,y+2),(x+4,y+2)],12);line([(x+3,y+3),(x+6,y+4)],5)
 def head(x,y,face='right',blink=False,small=False,scale=None):
  # Broad overlapping rounded scale plates, projecting cream muzzle, coral ear.
  scale=scale or (.60 if small else 1)
  def P(points,c=4,o=1):poly([(x+round(a*scale),y+round(bb*scale)) for a,bb in points],c,o)
  def L(points,c=2):line([(x+round(a*scale),y+round(bb*scale)) for a,bb in points],c)
  P([(0,9),(2,3),(7,0),(14,0),(19,4),(21,11),(19,20),(11,23),(3,21)],3)
  for pts in [[(2,6),(6,2),(12,1),(13,5),(8,8),(3,9)],[(12,2),(16,2),(19,6),(18,9),(13,8),(10,5)],[(1,11),(5,7),(10,8),(11,12),(7,16),(2,16)],[(2,17),(7,14),(11,16),(10,21),(5,22)],[(10,10),(15,8),(19,11),(18,15),(13,17),(10,14)]]:
   P(pts,4,2);L([pts[0],pts[1],pts[2]],5)
  if face=='back':
   P([(14,14),(19,11),(21,15),(20,20),(15,21)],4,2);return
  P([(13,5),(15,3),(18,5),(17,9),(14,10)],14)
  P([(12,10),(18,9),(20,13),(25,14),(25,17),(20,19),(17,23),(11,21),(10,16)],7)
  if blink:L([(13,13),(16,14),(18,13)],1)
  else:
   P([(13,10),(16,10),(19,14),(18,17),(13,16)],12)
   P([(16,12),(17,12),(18,14),(17,16),(16,15)],15)
   L([(16,12),(16,13)],12);L([(12,10),(14,9),(16,10)],1)
  L([(24,14),(24,15)],1);L([(18,19),(21,20),(23,18)],1)
  P([(10,5),(14,7),(15,11),(12,13),(9,9)],4,2)
  if face=='left':
   # Redraw mirrored head locally; body remains independently authored.
   crop=im.crop((x,y,x+26,y+24)).transpose(Image.Transpose.FLIP_LEFT_RIGHT);im.paste(crop,(x,y))
 if bank=='icon':
  x=min(p[0] for p in ps)+48;y=min(p[1] for p in ps)+48
  head(x,y,small=True)
  poly([(x+5,y+14),(x+11,y+14),(x+12,y+20),(x+5,y+21)],4)
  poly([(x+8,y+15),(x+11,y+15),(x+11,y+18),(x+8,y+19)],7)
  # Two independent tiny soles: the second frame lifts the forward boot.
  for bx,by in [(x+3,y+20),(x+10,y+19-(i%2))]:
   poly([(bx,by),(bx+3,by),(bx+4,by+2),(bx+4,by+3),(bx-1,by+3)],10);line([(bx,by+3),(bx+4,by+3)],9)
  d.rectangle((x+3,y+15,x+5,y+17),fill=12);return im
 if bank=='floor':
  # S1 prone: chin on mittens, one boot gently marks three native tap heights.
  poly([(29,48),(34,43),(45,43),(49,49),(44,56),(31,55)],4)
  poly([(31,48),(27,52),(28,56),(38,55)],4,2)
  boot(29,39,1);head(42,37)
  glove(44,58+(i-1));glove(52,58);return im
 if game=='s2' and i in (8,9):
  # Sit on curled tail; upright toes rock while chin rests on white glove.
  poly([(31,57),(34,51),(44,50),(50,54),(52,60),(44,63),(34,62)],4)
  head(25,37,blink=i==9,scale=.84);glove(35,57);boot(51,56-(i==9),i==9);return im
 xmin=min(p[0] for p in ps)+48;ymin=min(p[1] for p in ps)+48
 xmax=max(p[0]+p[2]*8 for p in ps)+48;ymax=max(p[1]+p[3]*8 for p in ps)+48
 isfloat=game=='s1' and i in (0x3c,0x3d,0x53,0x3e,0x54)
 if isfloat:
  if i==0x3d:
   # Front-facing fall uses native mirrored halves: symmetric cream mask/eyes.
   d.ellipse((36,37,59,57),fill=4,outline=1)
   for x in (37,44,51):poly([(x,40),(x+4,37),(x+7,41),(x+4,45)],5,2)
   poly([(38,46),(43,44),(47,48),(48,48),(52,44),(57,46),(55,55),(48,58),(40,55)],7)
   for x in (41,51):
    d.rectangle((x,47,x+3,52),fill=12);d.line((x+2,48,x+2,51),fill=15)
   d.line((31,43,41,49),fill=3,width=3);d.line((54,49,65,43),fill=3,width=3);glove(26,39);glove(63,39);boot(33,49,1);boot(57,49,1)
  elif i in (0x3c,0x54):
   head(49,37,scale=.77);poly([(38,44),(48,40),(53,46),(48,54),(36,53)],4);boot(29 if i==0x3c else 38,45,1);glove(50,52)
  else:
   head(24,37,'left',scale=.77);poly([(42,44),(50,41),(55,48),(50,54),(40,52)],4);boot(53,45,1);glove(35,51)
  return im
 running=(game=='s1' and i in range(0x1e,0x22)) or (game=='s2' and i in range(0x2d,0x31)) or (game=='s3k' and i in range(0x21,0x25))
 idle=bank=='team-idle' or (game=='s3k' and i in (0x5a,0x59,0x55,0x56,0x57,0xbd,0xbe,0xba))
 phase=i%4
 hx=min(p[0] for p in ps if p[1]+48==ymin)+48;hy=ymin+1
 if idle:
  # Hands clasped, shoulder turn then a determined forward lean.
  back=bank=='team-idle' or i==0x5a
  head(hx,hy,'left' if back or i==0x59 else 'right',blink=i==0xbe,scale=.88)
  poly([(hx+7,hy+22),(hx+16,hy+21),(hx+19,ymax-10),(hx+7,ymax-8)],4)
  if not back:poly([(hx+12,hy+23),(hx+17,hy+22),(hx+18,ymax-13),(hx+12,ymax-11)],7)
  poly([(hx+7,ymax-17),(hx+1,ymax-11),(hx-2,ymax-9),(hx+8,ymax-9)],4,2)
  boot(max(xmin,hx+2),ymax-9-(i%2 if bank=='team-idle' else 0));boot(min(xmax-11,hx+14),ymax-9-(i%2))
  glove(hx+3,hy+23);glove(hx+17,hy+23 if back else hy+25)
  return im
 # Eight walk phases and four run phases, separately placed boot support/lift.
 head(hx+ (1 if running else 0),hy,scale=.88)
 poly([(hx+6,hy+21),(hx+16,hy+20),(hx+19,hy+28),(hx+12,hy+33),(hx+4,hy+28)],4)
 poly([(hx+10,hy+20),(hx+16,hy+20),(hx+18,hy+26),(hx+12,hy+29),(hx+10,hy+25)],7)
 poly([(hx+5,hy+24),(xmin+1,hy+29),(xmin,hy+33),(hx+9,hy+30)],4,2)
 line([(xmin+3,hy+29),(hx+3,hy+29)],6)
 # Place soles in actual lower-piece intervals, including native split-leg gaps.
 def intervals(y):
  return [(48+x,48+x+w*8) for x,yy,w,h,*_ in ps if 48+yy<=y<48+yy+h*8]
 for side in (0,1):
  by=ymax-9-([7,2,0,5][phase] if side==0 else [0,5,7,1][phase])
  spans=[(a,z) for a,z in intervals(by+7) if z-a>=11]
  if not spans:spans=[(xmin,xmax)]
  span=spans[0 if side==0 else -1];a,z=span
  bx=a+1 if side==0 else z-11
  poly([(hx+9,hy+27),(hx+14,hy+27),(bx+6,by+2),(bx+2,by+2)],3)
  boot(bx,by,running and (phase+side)%3==0)
 gx=max(xmin,hx-2);gy=hy+21+[0,3,5,2][phase]
 d.line((hx+7,hy+23,gx+3,gy+3),fill=4,width=3);glove(gx,gy)
 gx=min(xmax-7,hx+19);gy=hy+21+[5,2,0,3][phase]
 d.line((hx+14,hy+23,gx+3,gy+3),fill=4,width=3);glove(gx,gy)
 return im

def sequences(game):
 def seq(name,bank,ids,ticks,**kw):return dict(name=name,frames=[f'{bank}-{i:02x}' for i in ids],ticks=ticks,**kw)
 if game=='s1':return [seq('fall','player',[0x3c,0x3d,0x53,0x3e,0x54],4),seq('waiting','floor',[1,1,1,1,2,2,2,3,3],5),seq('rise','player',[0x3c],4,end='switch to walk'),seq('walk','player',[8,9,10,11,6,7],4,preview_inertia=1280),seq('run','player',range(0x1e,0x22),3,preview_inertia=1536),seq('icons','icon',[6,7],16)]
 if game=='s2':return [seq('waiting','player',[8,9],10),seq('rise','player',[7],4,end='switch to walk'),seq('walk','player',[0xf,0x10,0x11,0x12,0x13,0x14,0xd,0xe],4,preview_inertia=1280),seq('run','player',range(0x2d,0x31),3,preview_inertia=1536),seq('icons','icon',[4,5],16)]
 return [seq('team-wait','team-idle',[0,1],16,clock='V_int_run_count bit 4'),seq('team-rise','player',[0x5a,0x59,0x55,0x56,0x57],7,flips=[False,True,False,False,False]),seq('solo-wait','player',[0xbd,0xbe],12,initial_index=1),seq('solo-rise','player',[0xba],8),seq('run','player',range(0x21,0x25),3),seq('icons','icon',[0,1],16,clock='V_int_run_count bit 4')]

def metadata(game):
 common=dict(game=game,palette='../palette.yaml',palette_line=0,piece_schema=['x','y','width_tiles','height_tiles','tile','flip_x','flip_y','palette_line','priority'],sequences=sequences(game))
 if game=='s1':common.update(native_source_label='ContSonic / CSon_ShowFall / CSon_Animate / CSon_RunRight / Ani_CSon / CSI_ShowMiniSonic',source='docs/s1disasm/_incObj/80, 81 Continue Screen Elements and Sonic.asm',animation_source=['docs/s1disasm/_anim/Continue Screen Sonic.asm','docs/s1disasm/_anim/Sonic.asm'],timing=dict(wait_timer_byte=4,fall_timer_byte=3,fall_dy=4,land_y=416,rise_dy=-8,acceleration=32,movement='At inertia $800: x += 16; otherwise inertia += $20',walk_run='1 + (max(0,$800-abs(inertia)) >> 8); walk below $600, run from $600',countdown='659 initial ticks, timer/60; no countdown-specific character frames'),palette_binding='MICA line 0 also serves native CONTINUE text/countdown and mini icons. Preserve MICA colours; engine must allocate/rebind text/digits separately. Floor oval/stars line 1 remain native.')
 elif game=='s2':common.update(native_source_label='ObjDB / SonAni_Lying / SonAni_LieDown / SonAni_Walk / SonAni_Run / ObjDA',source='docs/s2disasm/s2.asm',timing=dict(wait_timer_byte=9,rise_timer_byte=3,acceleration=32,movement='At inertia $800: x += 16; otherwise inertia += $20',walk_run='1 + (max(0,$800-abs(inertia)) >> 8); walk below $600, run from $600',countdown='658 at provider initialization, timer/60; no countdown-specific character frames'),palette_binding='MICA line 0 is shared with Tails, CONTINUE letters/digits and mini icons. Engine lane must split/rebind other actors and text; do not recolour MICA. This manifest replaces Sonic only; Talon/Tails choreography belongs to its art lane.')
 else:common.update(native_source_label='Obj_Continue_SonicWTails / Obj_Continue_SonicAlone / RawAni_5C622 / byte_5CBC5 / byte_5CBB4',source='docs/skdisasm/sonic3k.asm',timing=dict(team_rise='accepted ages 1..35, five seven-tick entries; first $5A flip byte ignored; age36 still $57; run from age37; movement from age53 at 6px/tick',solo_rise='solo idle continues until age38; $BA age39..46; $21 age47; raw run from age48, 3 ticks, increment-before-fetch',solo_idle='12 ticks, increment-before-fetch selects $BE first',countdown='9 initial, 59 timer; decrement until negative; no countdown-specific actor art'),palette_binding='Continue palette line 0 also serves Tails, Sonic icons and EggRobo. Retain MICA palette; engine lane must reserve/rebind their colours. Punctuation line 2 and Knuckles line 3 remain native.')
 common['review_notes']='Sheets compare native mapping canvases at common hotspots, 1x and 4x. GIF concatenates each sequence once, not a full screen recording; walk/run preview uses the stated fixed inertia, with native timer+1 duration. GIF times quantize to 10ms. Native fall movement/countdown/departure waits remain engine-owned. Weakest: run cycle is restrained, small scales/laces merge at 1x, and shared-palette background validation remains engine lane.'
 common['scope']='Sonic-role normal continue art, including all normal walk/run departure mappings and mini icons. No Talon/Tails, Knuckles/EggRobo replacement. Retained-super FixBugs=0 invalid-script/mismatched-DPLC artifacts are native bugs, not additional authored poses.'
 return common

def review(game,frames,refs,dest):
 dest.mkdir(parents=True,exist_ok=True)
 pages=[]
 for f,ref in zip(frames,refs):
  page=Image.new('RGB',(210,108),'#969696');im=Image.open(HERE/f['file']).convert('RGBA')
  page.paste(im,(48-f['hotspot'][0],48-f['hotspot'][1]),im);rr=ref.convert('RGBA');page.paste(rr,(106,0),rr)
  d=ImageDraw.Draw(page);d.text((1,94),f['key'],fill='black');d.text((108,94),f['native_source_label'][-16:],fill='black');pages.append(page)
 for scale in (1,4):
  cols=4;rows=(len(pages)+cols-1)//cols;sheet=Image.new('RGB',(210*cols,108*rows),'#969696')
  for j,p in enumerate(pages):sheet.paste(p,((j%cols)*210,(j//cols)*108))
  sheet.resize((sheet.width*scale,sheet.height*scale),Image.Resampling.NEAREST).save(dest/f'{game}-{scale}x.png')
 # Exactly one GIF per game: each native sequence once, preserving repeat counts.
 gif=[];dur=[];lookup={f['key']:j for j,f in enumerate(frames)}
 for seq in sequences(game):
  for n,key in enumerate(seq['frames']):
   j=lookup[key];f=frames[j];page=Image.new('RGB',(210,108),'#969696');left=b.indexed((96,96)).convert('RGBA');im=Image.open(HERE/f['file']).convert('RGBA');left.alpha_composite(im,(48-f['hotspot'][0],48-f['hotspot'][1]));right=refs[j].convert('RGBA')
   if seq.get('flips',[False]*len(seq['frames']))[n]:left=left.transpose(Image.Transpose.FLIP_LEFT_RIGHT);right=right.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
   page.paste(left,(0,0),left);page.paste(right,(106,0),right);ImageDraw.Draw(page).text((2,94),seq['name']+'  MICA | native',fill='black')
   gif.append(page.resize((840,432),Image.Resampling.NEAREST));dur.append(round((seq['ticks'] or 3)*1000/60))
 gif[0].save(dest/f'{game}.gif',save_all=True,append_images=gif[1:],duration=dur,loop=0,disposal=2)

def build(game,dest):
 # Accepted GPT redraws supersede the earlier procedural art, game by game.
 source=Path(__file__).resolve().parent / 'native-pose.json'
 if source.exists():
  accepted=json.loads(source.read_text())['frames']
  surface=next(s for s in json.loads((Path(__file__).resolve().parent / 'continue.yaml').read_text())['games'] if s['game']==game)
  if all(f['file'] in accepted for f in surface['frames']):
   surface=module('continue_native_pose',HERE/'native_pose.py').rebuild(game)
   verify(surface)
   if dest:
    entries,colors,_,_=sources(game)
    refs=[b.native(ps,data,colors) for bank,i,(label,ps),data,loads,pl in entries]
    review(game,surface['frames'],refs,dest)
   return
 entries,colors,mp,dp=sources(game);frames=[];refs=[];shared={}
 for bank,i,(label,ps),data,loads,pl in entries:
  im=draw_pose(game,bank,i,ps);out,hot,box=b.crop_mapping(im,ps)
  old=out.copy()
  for y in range(out.height):
   for x in range(out.width):
    if old.getpixel((x,y)) and any(xx<0 or yy<0 or xx>=out.width or yy>=out.height or not old.getpixel((xx,yy)) for xx,yy in ((x-1,y),(x+1,y),(x,y-1),(x,y+1))):out.putpixel((x,y),1)
  # Dedicated banks retain native shared tile identities across mapping frames.
  # Generate each shared tile once. Animation-specific pieces retain their own pixels.
  for x,y,w,h,t,fx,fy,*_ in ps:
   p=out.crop((x+hot[0],y+hot[1],x+hot[0]+w*8,y+hot[1]+h*8))
   if fx:p=p.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
   if fy:p=p.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
   for tx in range(w):
    for ty in range(h):
     tile=t+tx*h+ty;key=(bank if bank!='player' else f'player-{i}',tile);region=(tx*8,ty*8,tx*8+8,ty*8+8)
     if key in shared:p.paste(shared[key],(tx*8,ty*8))
     else:shared[key]=p.crop(region)
   if fy:p=p.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
   if fx:p=p.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
   out.paste(p,(x+hot[0],y+hot[1]))
  file=f'{game}-{bank}-{i:02x}.png';out.save(HERE/file,bits=4,transparency=0)
  f=dict(key=f'{bank}-{i:02x}',id=i,bank=bank,file=file,dimensions=list(out.size),hotspot=hot,native_source_label=label,pieces=ps,palette_line=0)
  f['mapping_source']=mp if bank=='player' else ('docs/s1disasm/_maps/Continue Screen.asm' if game=='s1' else 'docs/s2disasm/mappings/sprite/objDA.asm' if game=='s2' else 'docs/skdisasm/General/Continue/Map - Player Icons.asm' if bank=='icon' else 'docs/skdisasm/General/Continue/Map - Player Sprites.asm')
  if pl:f.update(dplc_label=pl,dplc_entries=loads,tile_budget=sum(n for n,t in loads))
  frames.append(f);refs.append(b.native(ps,data,colors))
 surface=metadata(game);surface.update(mapping_source=mp,dplc_source=dp,frames=frames)
 surface['native_art_labels']=({'floor':'Nem_ContSonic / ArtTile_Continue_Sonic','icon':'Nem_MiniSonic / ArtTile_Mini_Sonic','player':'Art_Sonic / Map_Sonic'} if game=='s1' else {'icon':'ArtNem_MiniSonic','player':'ArtUnc_Sonic / Map_Sonic'} if game=='s2' else {'team-idle':'ArtNem_ContinueSprites / Map_ContinueSprites','icon':'ArtNem_ContinueIcons / Map_ContinueIcons','player':'ArtUnc_Sonic / Map_Sonic'})
 manifest=Path(__file__).resolve().parent / 'continue.yaml';m=json.loads(manifest.read_text()) if manifest.exists() else dict(format='openggf-continue-art-handoff-1',tick_rate=60,palette='../palette.yaml',games=[])
 m['games']=[s for s in m['games'] if s['game']!=game]+[surface];manifest.write_text(json.dumps(m,indent=2)+'\n')
 verify(surface)
 if dest:review(game,frames,refs,dest)

def verify(surface):
 aliases={}
 for f in surface['frames']:
  im=Image.open(HERE/f['file']);assert im.mode=='P' and im.info['transparency']==0;assert im.getpalette()[:48]==sum(b.COLORS,[]);assert list(im.size)==f['dimensions'];assert max(im.get_flattened_data())<16;assert im.getbbox()
  mask=Image.new('L',im.size);d=ImageDraw.Draw(mask);hx,hy=f['hotspot']
  for x,y,w,h,t,fx,fy,*_ in f['pieces']:
   d.rectangle((x+hx,y+hy,x+hx+w*8-1,y+hy+h*8-1),fill=255)
   p=im.crop((x+hx,y+hy,x+hx+w*8,y+hy+h*8))
   if fx:p=p.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
   if fy:p=p.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
   for tx in range(w):
    for ty in range(h):
     tile=t+tx*h+ty;key=(f['bank'] if f['bank']!='player' else f['key'],tile);v=p.crop((tx*8,ty*8,tx*8+8,ty*8+8)).tobytes()
     assert key not in aliases or aliases[key]==v,(f['file'],tile);aliases[key]=v
     if 'tile_budget' in f:assert tile<f['tile_budget']
  assert all(not c or mask.getpixel((j%im.width,j//im.width)) for j,c in enumerate(im.get_flattened_data()))
 print(surface['game'],len(surface['frames']),'indexed palette, native bounds, piece mask, tile aliases and DPLC capacity verified')

if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('game',choices=['s1','s2','s3k']);p.add_argument('--review',type=Path);a=p.parse_args();build(a.game,a.review)
