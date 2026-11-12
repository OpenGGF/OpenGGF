#!/usr/bin/env python3
"""Purpose: export native Tails presentation references for original-character art review.
Inputs: optional pinned S1/S2/S3K disassemblies, existing reference decoders, Pillow.
Origin: TALON presentation lane, 2026-11-10. Decoded pixels are comparison-only;
write them to an owned scratch directory, never an asset/resource or Git path.
The original-character mod and runtime never call this development-only probe.
"""
import importlib.util,json
from pathlib import Path
from PIL import Image
R=Path(__file__).resolve().parents[3]
import argparse
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--output',type=Path,required=True,help='owned disposable directory; native art must never be committed')
O=parser.parse_args().output.expanduser().resolve()
if O.is_relative_to(R): parser.error("native reference output must be outside the repository")
O.mkdir(parents=True,exist_ok=True)
s=importlib.util.spec_from_file_location('b',R/'examples/mica-and-talon/production/mica/wave3/build.py');b=importlib.util.module_from_spec(s);s.loader.exec_module(b)
pal=b.pal(R/'docs/skdisasm/General/Sprites/Sonic/Palettes/SonicAndTails.bin')
raw=b.nem(R/'docs/skdisasm/General/Sprites/HUD Icon/Tails Life Icon.nem')
im=b.native([[0,0,2,2,0,0,0]],raw,pal).crop((48,48,64,64));im.save(O/'tails-hud.png');im.resize((128,128),Image.Resampling.NEAREST).save(O/'tails-hud-8x.png')
import re
s=importlib.util.spec_from_file_location('r',R/'examples/mica-and-talon/production/mica/wave3/round5.py');r=importlib.util.module_from_spec(s);s.loader.exec_module(r)
def save_group(name,frames):
 records=[];sheet=Image.new('RGBA',(64*4,64*((len(frames)+3)//4)))
 for j,(label,im,hot) in enumerate(frames):
  im.save(O/f'{name}-{j:02}-native.png');sheet.alpha_composite(im.convert("RGBA"),((j%4)*64+32-hot[0],(j//4)*64+48-hot[1]));records.append(dict(id=j,label=label,dimensions=im.size,hotspot=hot))
 sheet.resize((sheet.width*8,sheet.height*8),Image.Resampling.NEAREST).save(O/f'{name}-native-8x.png');(O/f'{name}.json').write_text(json.dumps(records,indent=2))
text=(R/'docs/s2disasm/mappings/sprite/obj10.asm').read_text();asm=(R/'docs/s2disasm/s2.asm').read_text();raw=b.nem(R/'docs/s2disasm/art/nemesis/Sonic and Tails animation frames in special stage.nem');frames=[]
for i,label in enumerate(re.findall(r'mappingsTableEntry.w\s+(\w+)',text)):
 block=text.split(label+':')[1].split(label+'_End')[0];ps=[list(map(b.num,a.split(','))) for a in re.findall(r'spritePiece\s+([^\n;]+)',block)]
 plc=asm.split(f'.tails_{i}:')[1].split(f'.tails_{i}_End')[0];loads=[list(map(b.num,a)) for a in re.findall(r'dplcEntry\s+([^,]+),\s*([^\s;]+)',plc)]
 base=0x183 if i<4 else 0x1c0 if i<12 else 0x264 if i<16 else 0x29e;data=b''.join(raw[(base+t)*32:(base+t+n)*32] for n,t in loads)
 im=b.native(ps,data,b.pal(R/'docs/s2disasm/art/palettes/Special Stage Main.bin',2));cut,hot,_=b.crop_mapping(im,ps);cut.putpalette(im.getpalette());frames.append((label,cut,hot))
save_group('halfpipe',frames)
mp='docs/skdisasm/General/Sprites/Tails/Map - SStage Tails.asm';maps=r.mapping(mp,'Map_SStageTails_');plcs=r.dplc(mp,'PLC_SStageTails_');raw=(R/'docs/skdisasm/General/Sprites/Tails/Art/SStage Tails.unc').read_bytes();frames=[]
for i,((label,ps),(_,loads)) in enumerate(zip(maps,plcs)):
 im=b.native(ps,b''.join(raw[t*32:(t+n)*32] for n,t in loads),b.pal(R/'docs/skdisasm/General/Special Stage/Palettes/Main.bin',1));cut,hot,_=b.crop_mapping(im,ps) if ps else (b.indexed((8,8)),[0,0],None);cut.putpalette(im.getpalette());frames.append((label,cut,hot))
save_group('bluesphere',frames)
# S2 alternate is native Tails; head-only crop excludes backing for pose transformation.
raw=b.nem(R/'docs/s2disasm/art/nemesis/Signpost.nem');ps=[[0,0,1,4,0x3a,0,0],[8,0,4,4,0x3e,0,0],[40,0,1,4,0x3a,1,0]]
im=b.native(ps,raw,b.pal(R/'docs/s2disasm/art/palettes/SonicAndTails.bin')).crop((48,48,96,80));im.resize((384,256),Image.Resampling.NEAREST).save(O/'sign-native-8x.png')
s=importlib.util.spec_from_file_location('c',R/'examples/mica-and-talon/production/mica/continue/build.py');c=importlib.util.module_from_spec(s);s.loader.exec_module(c)
s=importlib.util.spec_from_file_location('p',R/'examples/mica-and-talon/production/mica/portrait/build.py');p=importlib.util.module_from_spec(s);s.loader.exec_module(p)
frames=[]
for bank,i,(label,ps),data,loads,pl in c.sources('s1')[0]:
 colors=c.sources('s1')[1];im=b.native(ps,data,colors).convert('RGBA');box=(48+min(q[0] for q in ps),48+min(q[1] for q in ps),48+max(q[0]+q[2]*8 for q in ps),48+max(q[1]+q[3]*8 for q in ps));frames.append((f'{bank}/{i}',im.crop(box),(48-box[0],48-box[1])))
save_group('continue-s1',frames)
frames=[];colors=b.pal(R/'docs/s2disasm/art/palettes/SonicAndTails.bin');maps=c.macros('docs/s2disasm/mappings/sprite/objDA.asm')
for bank,ids,path in [('nag',[2,3],'Tails on continue screen.nem'),('icon',[4,5],'Tails continue.nem')]:
 for i in ids:
  label,ps=maps[i];im=b.native(ps,b.nem(R/'docs/s2disasm/art/nemesis'/path),colors);cut,hot,_=b.crop_mapping(im,ps);cut.putpalette(im.getpalette());frames.append((f'{bank}/{i}',cut,hot))
raw=(R/"docs/s2disasm/art/uncompressed/Tails's art.bin").read_bytes();maps=c.macros('docs/s2disasm/mappings/sprite/Tails.asm');plcs=c.macroplc('docs/s2disasm/mappings/spriteDPLC/Tails.asm')
for i in list(range(0xe,0x16))+list(range(0x2e,0x32)):
 label,ps=maps[i];loads=plcs[i][1];im=b.native(ps,b''.join(raw[t*32:(t+n)*32] for n,t in loads),colors);cut,hot,_=b.crop_mapping(im,ps);cut.putpalette(im.getpalette());frames.append((f'player/{i}',cut,hot))
save_group('continue-s2',frames)
frames=[];colors=b.pal(R/'docs/skdisasm/General/Continue/Palettes/Palette.bin');mp=r.mapping('docs/skdisasm/General/Continue/Map - Player Sprites.asm','Map_ContinueSprites_');ic=r.mapping('docs/skdisasm/General/Continue/Map - Player Icons.asm','Map_ContinueIcons_')
for bank,ids,maps,path in [('team-idle',[5,6],mp,'Player Sprites.nem'),('icon',[2,3],ic,'Player Icons.nem')]:
 for i in ids:
  label,ps=maps[i];im=b.native(ps,b.nem(R/'docs/skdisasm/General/Continue/Nemesis Art'/path),colors);cut,hot,_=b.crop_mapping(im,ps);cut.putpalette(im.getpalette());frames.append((f'{bank}/{i}',cut,hot))
raw=(R/'docs/skdisasm/General/Sprites/Tails/Art/Tails.unc').read_bytes();maps=r.mapping('docs/skdisasm/General/Sprites/Tails/Map - Tails.asm','Map_Tails_');plcs=r.dplc('docs/skdisasm/General/Sprites/Tails/DPLC - Tails.asm','PLC_Tails_')
for i in [0xad,0x21,0x22,0x23,0x24]:
 label,ps=maps[i];loads=plcs[i][1];im=b.native(ps,b''.join(raw[t*32:(t+n)*32] for n,t in loads),colors);cut,hot,_=b.crop_mapping(im,ps);cut.putpalette(im.getpalette());frames.append((f'player/{i}',cut,hot))
save_group('continue-s3k',frames)
raw=p.kos(R/'docs/skdisasm/General/Save Menu/Kosinski Art/Misc.kos');label,ps=r.mapping('docs/skdisasm/General/Save Menu/Map - Save Screen General.asm','Map_SaveScreen_')[6];im=b.native(ps,raw,b.pal(R/'docs/skdisasm/General/Save Menu/Palettes/Chars.bin'));cut,hot,_=b.crop_mapping(im,ps);cut.putpalette(im.getpalette());save_group('portrait',[(label,cut,hot)])
raw=b.nem(R/'docs/s1disasm/artnem/HUD - Life Counter Icon.nem');ps=[[0,0,4,2,4,0,0]];im=b.native(ps,raw,b.pal(R/'docs/s1disasm/palette/Sonic.bin')).crop((48,48,80,56));im.resize((512,128),Image.Resampling.NEAREST).save(O/'s1-name-native-16x.png')
label,ps=r.mapping('docs/skdisasm/General/Blue Sphere/Map - Tails Pose.asm','Map_BSTailsPose_')[0];raw=b.nem(R/'docs/skdisasm/General/Blue Sphere/Nemesis Art/Tails Pose.nem');im=b.native(ps,raw,b.pal(R/'docs/skdisasm/General/Sprites/Sonic/Palettes/SonicAndTails.bin'));cut,hot,_=b.crop_mapping(im,ps);cut.putpalette(im.getpalette());save_group('blue-result',[(label,cut,hot)])
mp='docs/skdisasm/General/Sprites/Tails/Map - Tails.asm';dp='docs/skdisasm/General/Sprites/Tails/DPLC - Tails.asm';maps=r.mapping(mp,'Map_Tails_');plcs=r.dplc(dp,'PLC_Tails_');raw=(R/'docs/skdisasm/General/Sprites/Tails/Art/Tails.unc').read_bytes();frames=[]
for i in range(0x96,0x9b):
 label,ps=maps[i];loads=plcs[i][1];im=b.native(ps,b''.join(raw[t*32:(t+n)*32] for n,t in loads),pal);cut,hot,_=b.crop_mapping(im,ps);cut.putpalette(im.getpalette());frames.append((label,cut,hot))
save_group('slots',frames)
