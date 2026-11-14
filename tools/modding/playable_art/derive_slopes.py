#!/usr/bin/env python3
"""Purpose: derive indexed slope cycles from corrected flat art, without native pixels.
Inputs: a flat indexed frame, its hotspot, the angle and the support distance to keep.
Origin: owner slope correction, 2026-11-08. Python + Pillow; no Java or ROM needed.
SAnim_WalkRun (docs/s2disasm/s2.asm:38453-38502) selects offsets 0/2/4/6,
with 8 WALK or 4 RUN phases per set. Native run mappings $35-$38 are exact
quarter turns of $2D-$30; $39-$3C are quarter turns of $31-$34.
Thus up45/steep/inverted are +45/+90/+135 visual counterclockwise, before
engine facing/vertical flips. Scale2x x3, nearest rotate and reduce is a
RotSprite-style indexed rotation: never interpolate palette or alpha values.

Two reductions exist. "mode" (the default, and every shipped 45 degree frame since
2026-11-12) first lifts the flat's silhouette contour, so no stale outline is
rotated into the body, keeps the most common index of each 8x8 block of the
rotated master, lets the caller run its material cleanup (`finish`), and only
then draws the 1 px ink silhouette. `ring` names the indices that form the flat's
contour: ink for normal sheets, plus the contour slot of a powered bank, whose
caller moves the new ink back onto that slot. "nearest" keeps one sample of each
block; it is the superseded 2026-11-08 reduction, kept selectable for comparison.

examples/mica-and-talon/production/slope_sets.py derives every shipped set
(TALON's normal WALK/RUN through production/talon/walk_cleanup.py).
"""
import math
from collections import Counter
from PIL import Image
from pipeline import outline

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


def lift_ring(im, ring=(1,)):
    """Give each silhouette contour pixel the material beside it, so the outline is redrawn after a turn."""
    out = im.copy()
    px, dst = im.load(), out.load()
    def at(x, y):
        return px[x,y] if 0 <= x < im.width and 0 <= y < im.height else 0
    def body(v):
        return v and v not in ring
    for y in range(im.height):
        for x in range(im.width):
            if px[x,y] not in ring or all(at(x+a,y+b) for a,b in ((-1,0),(1,0),(0,-1),(0,1))):
                continue
            near = [at(x+a,y+b) for a,b in ((-1,0),(1,0),(0,-1),(0,1)) if body(at(x+a,y+b))]
            if not near:
                near = [at(x+a,y+b) for a in (-1,0,1) for b in (-1,0,1) if body(at(x+a,y+b))]
            if near:  # an all-contour limb keeps its index
                counts = Counter(near); top = max(counts.values())
                dst[x,y] = min(k for k,n in counts.items() if n == top)
    return out


def mode_reduce(big, size, factor=8):
    """Most common index of each block; a block that is mostly empty stays empty. Ties take the lower index."""
    out = Image.new('P', size)
    out.putpalette(big.getpalette())
    px, dst = big.load(), out.load()
    for y in range(size[1]):
        for x in range(size[0]):
            counts = Counter(px[x*factor+i,y*factor+j] for i in range(factor) for j in range(factor))
            if counts[0]*2 > factor*factor:
                continue
            del counts[0]
            top = max(counts.values())
            dst[x,y] = min(k for k,n in counts.items() if n == top)
    return out


def rotate(im, angle, method='mode', finish=None, ring=(1,)):
    """finish (mode only) takes and returns the 45 degree image before it is inked; quarter turns never see it."""
    if method not in ('nearest', 'mode'):
        raise ValueError('Unknown reduction: ' + str(method))
    if angle == 90:
        return im.transpose(Image.Transpose.ROTATE_90)
    if angle == 135:
        return rotate(im,45,method,finish,ring).transpose(Image.Transpose.ROTATE_90)
    if angle != 45:
        raise ValueError('Expected 45, 90 or 135')
    big = lift_ring(im, ring) if method == 'mode' else im
    for _ in range(3):
        big = scale2x(big)
    big = big.rotate(45, resample=Image.Resampling.NEAREST,
                     center=(big.width/2,big.height/2), fillcolor=0)
    # No smoothing, dithering or learned palette. Inside-ink cleanup only.
    if method == 'nearest':
        return outline(big.resize(im.size, Image.Resampling.NEAREST))
    small = mode_reduce(big, im.size)
    return outline(finish(small) if finish else small)


def derive(flat, hotspot, angle, support, method='mode', finish=None, ring=(1,)):
    canvas = Image.new('P',(128,128)); canvas.putpalette(flat.getpalette())
    canvas.paste(flat,(64-hotspot[0],64-hotspot[1]))
    rotated = rotate(canvas,angle,method,finish,ring)
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
