#!/usr/bin/env python3
"""Purpose: derive indexed slope cycles from corrected flat art, without native pixels.
Inputs: MICA recipe/manifest and native support distances in native-pose-core.json.
Origin: owner slope correction, 2026-11-08. Python + Pillow; no Java or ROM needed.
SAnim_WalkRun (docs/s2disasm/s2.asm:38453-38502) selects offsets 0/2/4/6,
with 8 WALK or 4 RUN phases per set. Native run mappings $35-$38 are exact
quarter turns of $2D-$30; $39-$3C are quarter turns of $31-$34.
Thus up45/steep/inverted are +45/+90/+135 visual counterclockwise, before
engine facing/vertical flips. Scale2x x3, nearest rotate and decimate is a
RotSprite-style indexed rotation: never interpolate palette or alpha values.
"""
import argparse
import json
import math
from pathlib import Path
from PIL import Image
from pipeline import outline, build

ANGLES = {'up45':45, 'steep':90, 'inverted':135}


def scale2x(im):
    """EPX/Scale2x: expand corner colours only where adjacent edges agree."""
    out = Image.new('P', (im.width*2, im.height*2))
    out.putpalette(im.getpalette())
    px, dst = im.load(), out.load()
    for y in range(im.height):
        for x in range(im.width):
            e = px[x,y]
            b = px[x,max(0,y-1)]; d = px[max(0,x-1),y]
            f = px[min(im.width-1,x+1),y]; h = px[x,min(im.height-1,y+1)]
            vals = (d if d==b else e, f if b==f else e,
                    d if d==h else e, f if h==f else e) if b!=h and d!=f else (e,)*4
            for (dx,dy), value in zip(((0,0),(1,0),(0,1),(1,1)), vals):
                dst[x*2+dx,y*2+dy] = value
    return out


def rotate(im, angle):
    if angle == 90:
        return im.transpose(Image.Transpose.ROTATE_90)
    if angle == 135:
        return rotate(im,45).transpose(Image.Transpose.ROTATE_90)
    if angle != 45:
        raise ValueError('Expected 45, 90 or 135')
    big = im
    for _ in range(3):
        big = scale2x(big)
    big = big.rotate(45, resample=Image.Resampling.NEAREST,
                     center=(big.width/2,big.height/2), fillcolor=0)
    # No smoothing, dithering or learned palette. Inside-ink cleanup only.
    return outline(big.resize(im.size, Image.Resampling.NEAREST))


def derive(flat, hotspot, angle, support):
    canvas = Image.new('P',(128,128)); canvas.putpalette(flat.getpalette())
    canvas.paste(flat,(64-hotspot[0],64-hotspot[1]))
    rotated = rotate(canvas,angle)
    sn, cs = math.sin(math.radians(angle)), math.cos(math.radians(angle))
    def distance(x,y): return (x-64)*sn+(y-64)*cs
    actual = max(distance(x,y) for y in range(128) for x in range(128) if rotated.getpixel((x,y)))
    # Integer translation along the normal; no sole stretching or contour clipping.
    delta = support-actual
    steps = math.floor(delta/(sn if abs(sn)>0.5 else cs)+1e-8)
    dx,dy = (steps,0) if abs(sn)>0.5 else (0,steps)
    moved = Image.new('P',canvas.size);moved.putpalette(flat.getpalette())
    moved.paste(rotated,(dx,dy))
    box = moved.getbbox()
    if box is None or min(box[:2])==0 or max(box[2:])==128:
        raise ValueError('Slope exceeds work canvas')
    return moved.crop(box), [64-box[0],64-box[1]]


def regenerate(root):
    recipe=json.loads((root/'recipe.json').read_text())
    manifest=json.loads((root/'frames.yaml').read_text())
    meta=json.loads((root/'native-pose-core.json').read_text())
    flats={f['id']:f for f in manifest['frames'] if f['angle_set']=='flat'}
    for f in recipe['frames']:
        if f['animation'] not in ('WALK','RUN') or f['angle_set']=='flat': continue
        key=f['id']; group, slope, phase=key.split('-');base=f'{group}-{phase}'
        origin=flats[base];source=meta['frames'][key];angle=ANGLES[slope]
        with Image.open(root/origin['file']) as flat:
            im,hot=derive(flat,origin['hotspot'],angle,source['native_support'])
        for field in list(f):
            if field not in ('id','animation','order','angle_set','ticks'): del f[field]
        f['notes']=f'Deterministic Scale2x slope from {base}; +{angle} degrees CCW, native support, integer contact alignment.'
        f['native_pixels']={'rows':[''.join(format(im.getpixel((x,y)),'x') for x in range(im.width)) for y in range(im.height)],'anchor':hot}
        source.update(geometry_reference={'size':list(im.size),'hotspot':hot},native_size=list(im.size),hotspot=hot,
                      owner_fix=f['notes'],derived_from=base,rotation_degrees=angle)
    (root/'recipe.json').write_text(json.dumps(recipe,indent=2)+'\n')
    (root/'native-pose-core.json').write_text(json.dumps(meta,indent=2)+'\n')
    build(root/'recipe.json')


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('root',type=Path)
    regenerate(parser.parse_args().root)
