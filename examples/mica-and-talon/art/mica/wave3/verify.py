#!/usr/bin/env python3
"""Verify wave3 indexed PNGs, native pieces, tile aliasing and deterministic rebuild.
Purpose/inputs/origin: W7 round 4 handoff contract, 2026-11-08; no Java required.
"""
import hashlib,json,runpy,subprocess,sys
from pathlib import Path
from PIL import Image
HERE=Path(__file__).resolve().parent
m=runpy.run_path(str(HERE/'build.py'));manifest=json.loads((HERE/'wave3.yaml').read_text())
files=list(HERE.glob('*.png'))+[HERE/'wave3.yaml'];before={p:hashlib.sha256(p.read_bytes()).hexdigest() for p in files}
count=0
for surface in manifest['surfaces']:
 for f in surface.get('frames',[]):
  if not f.get('file'):continue
  im=Image.open(HERE/f['file']);assert im.mode=='P' and list(im.size)==f['dimensions'];assert im.info['transparency']==0
  assert im.getpalette()[:48]==sum(m['COLORS'],[]);assert max(im.get_flattened_data())<=15
  tiles={};hx,hy=f['hotspot']
  for x,y,w,h,t,fx,fy,*_ in f['pieces']:
   piece=im.crop((x+hx,y+hy,x+hx+w*8,y+hy+h*8))
   if fx:piece=piece.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
   if fy:piece=piece.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
   for tx in range(w):
    for ty in range(h):
     idx=t+tx*h+ty;data=piece.crop((tx*8,ty*8,tx*8+8,ty*8+8)).tobytes()
     assert idx not in tiles or tiles[idx]==data,(f['id'],'native shared tile mismatch',idx)
     tiles[idx]=data
  assert max(tiles,default=-1)<f['tile_budget']
  if 'fixed_palette_file' in f:
   q=Image.open(HERE/f['fixed_palette_file']);assert q.getpalette()[:48]==sum(surface['fixed_palette']['colors'],[])
   assert list(q.get_flattened_data())==[surface['fixed_palette']['index_lut'][i] for i in im.get_flattened_data()]
  count+=1
subprocess.run([sys.executable,'-I',str(HERE/'build.py')],check=True)
assert all(hashlib.sha256(p.read_bytes()).hexdigest()==h for p,h in before.items())
print(f'PASS: {count} frames; palette, alpha, dimensions, piece tile aliases/budgets, fixed quantization, deterministic rebuild. No runtime certification.')
