#!/usr/bin/env python3
"""Purpose: original MICA ICZ snowboard pixels in the thirteen native mappings.
Inputs: MICA palette, pinned Sonic snowboard maps/DPLC; native art for review only.
Origin: MICA design lane, 2026-11-08. No Java, ROM pixels or generated imagery
in output. Run python3 -I <this file> --review /tmp/mica-art-portrait-20261108.
"""
import argparse, json, math
from pathlib import Path
import importlib.util
from PIL import Image,ImageDraw
HERE=Path(__file__).resolve().parent
s=importlib.util.spec_from_file_location('portrait',HERE.parent/'portrait/build.py');p=importlib.util.module_from_spec(s);s.loader.exec_module(p)
b=p.b;r=p.r;ROOT=p.ROOT
BASE='docs/skdisasm/General/Sprites/Sonic/'

def pose(i):
 im=b.indexed((96,96));d=ImageDraw.Draw(im)
 # Board endpoints measured in the native frame coordinate system. These
 # original cream/jade decks replace the board pixels in the player art too.
 ends={1:((-21,-19),(25,27)),2:((-5,7),(-3,35)),3:((-22,28),(21,-12)),4:((-21,32),(8,-18)),5:((-18,-18),(27,31)),6:((-28,20),(25,20)),7:((-28,22),(27,13)),8:((-28,24),(22,12)),9:((-32,-16),(10,38)),10:((-26,-10),(25,32)),11:((-25,12),(25,23)),12:((-25,9),(23,33))}
 (ax,ay),(bx,by)=ends[i];dx=bx-ax;dy=by-ay;length=math.hypot(dx,dy);nx=-dy/length;ny=dx/length
 def pts(seq):return [(48+round(x),48+round(y)) for x,y in seq]
 def poly(seq,c,outline=1):d.polygon(pts(seq),fill=c,outline=outline)
 def line(seq,c,width=1):d.line(pts(seq),fill=c,width=width)
 # Board thickness is a real silhouette, not copied from native pixels.
 thickness=8 if i<=5 else 3
 poly([(ax,ay),(ax+nx*thickness,ay+ny*thickness),(bx-2+nx*thickness,by+ny*thickness),(bx+2,by-2),(bx,by),(ax+3,ay-1)],7)
 if i<=5:line([(ax+nx*6,ay+ny*6),(bx-3+nx*6,by-3+ny*6)],8,2)
 line([(ax+2,ay+1),(bx-3,by+1)],5)
 # Boots along the top of the board. Pose is authored per mapping rather
 # than rotating an upright bitmap. Strong side-view eye in gameplay frames.
 spec={1:(-5,-32,(-7,-5),(8,10)),2:(-5,-16,(-4,7),(-1,16)),3:(-7,-21,(-5,10),(6,0)),4:(-1,-20,(-3,7),(3,-4)),5:(-4,-19,(-1,4),(6,13)),6:(-3,-15,(-10,14),(7,14)),7:(-3,-15,(-10,16),(8,9)),8:(-3,-15,(-10,18),(8,8)),9:(5,-14,(-18,1),(-8,15)),10:(7,-19,(-12,2),(0,15)),11:(-1,-19,(-9,7),(8,12)),12:(-1,-17,(-9,9),(7,20))}
 hx,hy,boot1,boot2=spec[i]
 # Body joins both knees; cream belly stays visible above the coral boots.
 waist=((boot1[0]+boot2[0])/2,(boot1[1]+boot2[1])/2-8)
 wx,wy=waist
 poly([(hx-6,hy+13),(hx+6,hy+13),(wx+7,wy),(wx+4,wy+6),(wx-5,wy+5),(wx-9,wy)],4)
 poly([(hx,hy+14),(hx+6,hy+13),(wx+4,wy),(wx+2,wy+4),(wx-2,wy+2)],7)
 for x,y in (boot1,boot2):
  poly([(wx,wy),(wx+3,wy+2),(x+3,y-3),(x-2,y-2)],3)
  poly([(x-4,y-3),(x+2,y-4),(x+4,y-1),(x+7,y),(x+6,y+3),(x-5,y+3)],10)
  line([(x-3,y+2),(x+5,y+2)],9);line([(x-2,y-2),(x+2,y-2)],12)
 # Tapered pangolin tail, broad scales (not Sonic spikes).
 poly([(hx-5,hy+12),(wx-8,wy-1),(wx-14,wy+3),(wx-20,wy+1),(wx-14,wy-4),(hx-10,hy+9)],3)
 line([(wx-15,wy),(wx-11,wy-2),(wx-8,wy-1)],6)
 # Original presentation head with two native-pixel eyes and open grin.
 head=p.p.sign('s3k').crop((5,0,40,27)).resize((22,19),Image.Resampling.NEAREST)
 im.paste(head,(48+hx-10,48+hy),head.point(lambda v:255 if v else 0,mode='L'))
 # Outstretched arms follow the frame's balance; gloves remain white.
 for side in (-1,1):
  gx=hx+side*12;gy=hy+16+(2 if i in (7,8) and side==1 else 0)
  line([(hx+side*6,hy+15),(gx,gy)],1,4);line([(hx+side*6,hy+15),(gx,gy)],4,2)
  d.ellipse((48+gx-3,48+gy-2,48+gx+3,48+gy+3),fill=12,outline=1)
  line([(gx,gy+1),(gx+2,gy+1)],13)
 return im

def verify(frames):
 for f in frames:
  im=Image.open(HERE/f['file']);assert im.mode=='P' and im.info['transparency']==0
  assert im.getpalette()[:48]==sum(b.COLORS,[]) and max(im.get_flattened_data())<16
  assert list(im.size)==f['dimensions']
  if not f['pieces']:assert im.getbbox() is None;continue
  tiles={};hx,hy=f['hotspot']
  for x,y,w,h,t,fx,fy,*_ in f['pieces']:
   q=im.crop((x+hx,y+hy,x+hx+w*8,y+hy+h*8))
   if fx:q=q.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
   if fy:q=q.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
   for tx in range(w):
    for ty in range(h):
     n=t+tx*h+ty;v=q.crop((tx*8,ty*8,tx*8+8,ty*8+8)).tobytes()
     assert n not in tiles or tiles[n]==v,(f['id'],n,'tile alias');tiles[n]=v
  assert max(tiles)<f['tile_budget']
 print('snowboard: 13 mappings (12 drawn + empty 0), palette/indices/dimensions/DPLC tile capacity/aliases passed')

def main():
 a=argparse.ArgumentParser();a.add_argument('--review',type=Path);args=a.parse_args()
 mp=BASE+'Map - Sonic Snowboarding.asm';dp=BASE+'DPLC - Sonic Snowboarding.asm'
 maps=r.mapping(mp,'Map_SonicSnowboard_');plcs=r.dplc(dp,'DPLC_SonicSnowboard_');assert len(maps)==len(plcs)==13
 frames=[];refs=[]
 raw=(ROOT/(BASE+'Art/Sonic Snowboarding.unc')).read_bytes() if args.review else None
 for i,((label,pieces),(dl,loads)) in enumerate(zip(maps,plcs)):
  if pieces:out,hot,box=b.crop_mapping(pose(i),pieces)
  else:out=b.indexed((1,1));hot=[0,0];box=(0,0,0,0)
  file=f'mica-{i:02}.png';out.save(HERE/file,bits=4,transparency=0)
  frames.append(dict(id=i,file=file,native_source_label=label,dimensions=list(out.size),native_dimensions=[box[2]-box[0],box[3]-box[1]],hotspot=hot,pieces=pieces,dplc_label=dl,dplc_entries=loads,tile_budget=sum(n for n,t in loads),use='empty sentinel; 1x1 transparent transport' if i==0 else 'native bank coverage; not selected by current ICZ animation scripts' if i<6 else 'live ICZ intro animation'))
  if args.review:refs.append(b.native(pieces,b''.join(raw[t*32:(t+n)*32] for n,t in loads),b.pal(ROOT/(BASE+'Palettes/SonicAndTails.bin'))))
 animations=[dict(id=0,label='byte_39482',frames=[6,7,8],ticks=8,loop_start=2),dict(id=1,label='byte_39488',frames=[9],ticks=32,loop_start=0),dict(id=2,label='byte_3948B',frames=[10],ticks=32,loop_start=0),dict(id=3,label='byte_3948E',frames=[6,6,6,6,6,6,6,7,8,7],ticks=8,loop_start=0),dict(id=4,label='byte_3949A',frames=[11],ticks=32,loop_start=0),dict(id=5,label='byte_3949D',frames=[12],ticks=32,loop_start=0)]
 m=dict(format='openggf-mica-snowboard-1',palette='../palette.yaml',palette_line=0,native_source_label='Map_SonicSnowboard / DPLC_SonicSnowboard / Ani_SonicSnowboard / sub_3968E',mapping_source=mp,dplc_source=dp,animation_source=BASE+'Anim - Sonic Snowboard.asm',native_art_label='ArtUnc_SonicSnowboard',frames=frames,timing=dict(tick_rate=60,animations=animations,angle_table=[4,5,2,1,1,1,1,1,0,1,2,0,1,2,0,4],note='Animate_Sprite timer byte + 1; animation 0 FE,1 holds frame 8; animation 3 loops. Frame changes also select the board DPLC in sub_3968E.'),engine_owners=['Sonic3kICZEvents.spawnSonicSnowboardIntro','IczSnowboardIntroInstance.SONIC_SNOWBOARD_ANIMATION_FRAMES','IczBigSnowPileInstance (post-crash terrain/priority; not frame owner)','Sonic3kConstants.MAP_SONIC_SNOWBOARD_FRAMES = 13'],palette_binding='Install unchanged MICA palette on player line 0. Native snow/board/dust can share player line: engine must audit and split palettes/art; no fixed-blue variants supplied.',separate_board='These player frames include their own deck pixels. Map_Snowboard / DPLC_Snowboard is the separate child board bank and crash fly-away, not these thirteen mappings; remains native.',weak_points='Compact crest and front-view frames 2/4 simplified to three-quarter read. Mapping masks and per-pose support checked in pixel review only; runtime snow occlusion, board handoff and custom palette sharing remain engine lane.')
 (Path(__file__).resolve().parent / 'snowboard.yaml').write_text(json.dumps(m,indent=2)+'\n');verify(frames)
 if args.review:
  old=b.HERE;b.HERE=HERE
  b.review(args.review,'snowboard',frames,refs,[('air',[6,7,8,8,8],8),('balance',[6,6,6,6,6,6,6,7,8,7],8),('slopes',[9,10,11,12],32)])
  b.HERE=old
if __name__=='__main__':main()
