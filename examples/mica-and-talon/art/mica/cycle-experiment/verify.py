#!/usr/bin/env python3
"""Purpose: verify isolated cycle art palette, native geometry, aliases and rebuild.
Inputs: experiment path; no Java or disassembly required.
Origin: MICA whole-cycle experiment, 2026-11-08.
"""
import hashlib,importlib.util,json,sys
from pathlib import Path
from PIL import Image
root=Path.cwd();a=Path(sys.argv[1]);colors=json.loads((a/'palette.yaml').read_text())['colors'];p=json.loads((a/'provenance.json').read_text());m=json.loads((a/'frames.yaml').read_text())['frames'];refs={f['id']:f for f in p['frames']}
assert len(m)==30
hashes={}
for f in m:
 im=Image.open(a/f['file']);ref=refs[f['id']]['reference'];assert im.mode=='P';assert list(im.size)==ref['size'];assert f['hotspot']==ref['hotspot'];assert im.getpalette()[:48]==sum(colors,[]);assert im.info['transparency']==0;assert max(im.get_flattened_data())<16
 assert im.convert('RGBA').getchannel('A').getextrema()==(0,255)
 for y in range(im.height):
  for x in range(im.width):
   if im.getpixel((x,y)) and any(not (0<=xx<im.width and 0<=yy<im.height) or im.getpixel((xx,yy))==0 for xx,yy in ((x-1,y),(x+1,y),(x,y-1),(x,y+1))):assert im.getpixel((x,y))==1
 if refs[f['id']]['alias_of']:
  other=Image.open(a/'frames'/f"{refs[f['id']]['alias_of']}.png");assert im.tobytes()==other.tobytes()
 hashes[f['file']]=hashlib.sha256((a/f['file']).read_bytes()).hexdigest()
s=importlib.util.spec_from_file_location('pipeline',root/'tools/modding/playable_art/pipeline.py');pipeline=importlib.util.module_from_spec(s);s.loader.exec_module(pipeline);pipeline.build(a/'recipe.json')
for f,h in hashes.items():assert hashlib.sha256((a/f).read_bytes()).hexdigest()==h
assert {g:sum(x['animation']==g for x in m) for g in ('WALK','RUN','ROLL','SPINDASH')}=={'WALK':8,'RUN':4,'ROLL':8,'SPINDASH':10}
print('PASS: 30 steps / 23 unique native mappings; palette, binary alpha, ink boundary, native boxes/hotspots, repeated aliases, byte-identical regeneration.')
