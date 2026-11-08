#!/usr/bin/env python3
"""Purpose: export mapped stock Sonic pose references, 8x nearest-neighbour.
Inputs: pinned S2 raw player art/mapping/DPLC; S3K HUD and S2 sign Nemesis art.
Origin: W7 MICA native-pose pilot, owner-directed enlarge/transform/reduce, 2026-11-08.
Native pixels are disposable comparison inputs: output is restricted to /tmp.
Run with python3 -I; no Java, ROM copying or native assets in the repository.
"""
import argparse
import importlib.util
import json
import re
import subprocess
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[3]

def load(path, name):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module

def num(v):
    return int(v.strip().replace('$', '0x'), 0)

def palette(path):
    data = path.read_bytes()
    return [[round(((int.from_bytes(data[i:i+2], 'big') >> s) & 14)*255/14)
             for s in (0, 4, 8)] for i in range(0, 32, 2)]

def piece(data, start, w, h, colors):
    im = Image.new('P', (w*8, h*8))
    im.putpalette(sum(colors, []) + [0]*720)
    im.info['transparency'] = 0
    for x in range(w):
        for y in range(h):
            b = data[(start+x*h+y)*32:(start+x*h+y+1)*32]
            if len(b) != 32: raise ValueError('Missing native tile')
            tile = Image.new('P', (8,8))
            tile.putdata([p for v in b for p in (v >> 4, v & 15)])
            im.paste(tile, (x*8,y*8))
    return im.convert('RGBA')

def player(index):
    base = ROOT/'docs/s2disasm'
    maps = (base/'mappings/sprite/Sonic.asm').read_text()
    labels = re.findall(r'mappingsTableEntry.w\s+(\w+)', maps)
    block = maps.split(labels[index]+':')[1].split(labels[index]+'_End')[0]
    plc = (base/'mappings/spriteDPLC/Sonic.asm').read_text()
    plc = plc.split(f'.frame{index}:')[1].split(f'.frame{index}_End')[0]
    raw = (base/"art/uncompressed/Sonic's art.bin").read_bytes()
    data = b''.join(raw[num(t)*32:(num(t)+num(n))*32]
                    for n,t in re.findall(r'dplcEntry\s+([^,]+),\s*([^\s;]+)', plc))
    colors = palette(base/'art/palettes/SonicAndTails.bin')
    canvas = Image.new('RGBA', (128,128))
    for args in re.findall(r'spritePiece\s+([^;\n]+)', block):
        x,y,w,h,t,fx,fy,*_ = map(num, args.split(','))
        im = piece(data,t,w,h,colors)
        if fx: im = im.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
        if fy: im = im.transpose(Image.Transpose.FLIP_TOP_BOTTOM)
        canvas.alpha_composite(im,(64+x,64+y))
    bbox = canvas.getbbox()
    return canvas.crop(bbox), [64-bbox[0],64-bbox[1]], labels[index]

def references(dest):
    dest = dest.resolve()
    if not dest.is_relative_to(Path('/tmp')): raise ValueError('Native references must stay in /tmp')
    dest.mkdir(parents=True,exist_ok=True)
    oracle = load(ROOT/'tools/timing/s3k-instruction-ledgers/vint/nem/nem_reference.py','nem')
    groups = [('wait', [1,2,3,4,5,6]), ('walk',[15,16,17,18,19,20,13,14]),
              ('run',[45,46,47,48]), ('spring',[91,91]), ('roll',[61,62,63,64])]
    records = []
    for group, indices in groups:
        for i,index in enumerate(indices):
            im,hotspot,label = player(index)
            records.append((f'{group}-{i:02}',im,hotspot,dict(game='S2',mapping=index,label=label)))
    for key, path, pal in [
        ('s3k-hud','docs/skdisasm/General/Sprites/HUD Icon/Sonic Life Icon.nem',
         'docs/skdisasm/General/Sprites/Sonic/Palettes/SonicAndTails.bin'),
        ('s2-sign','docs/s2disasm/art/nemesis/Signpost.nem','docs/s2disasm/art/palettes/SonicAndTails.bin')]:
        raw = (ROOT/path).read_bytes()
        data = b''.join(v.to_bytes(4,'big') for v in oracle.decode(raw+bytes(8),0))
        colors = palette(ROOT/pal)
        if key=='s3k-hud': im=piece(data,0,2,2,colors)
        else:
            im=Image.new('RGBA',(48,32))
            im.alpha_composite(piece(data,0x22,3,4,colors))
            im.alpha_composite(piece(data,0x2e,3,4,colors),(24,0))
        records.append((key,im,[im.width//2,im.height//2],dict(source=path,game=key.split('-')[0])))
    pins = {name: subprocess.check_output(['git','-C',str(ROOT),'rev-parse',f'HEAD:docs/{name}'], text=True).strip()
            for name in ('s2disasm','skdisasm')}
    (dest/'source-pins.json').write_text(json.dumps(pins,indent=2)+'\n')
    manifest=[]
    for key,im,hotspot,source in records:
        im.save(dest/f'{key}.png')
        im.resize((im.width*8,im.height*8),Image.Resampling.NEAREST).save(dest/f'{key}-8x.png')
        manifest.append(dict(id=key,size=list(im.size),hotspot=hotspot,
                             bbox_relative_to_hotspot=[-hotspot[0],-hotspot[1],im.width-hotspot[0],im.height-hotspot[1]],**source))
    (dest/'references.json').write_text(json.dumps(manifest,indent=2)+'\n')
    print(f'Exported {len(manifest)} references to {dest}')

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--output',type=Path,required=True)
    references(p.parse_args().output)
