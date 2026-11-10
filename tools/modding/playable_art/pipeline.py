#!/usr/bin/env python3
"""Purpose: reduce generated pose atlases to shared-palette native playable frames.
Inputs: JSON recipe (also valid YAML), RGBA/keyed atlases, fixed palette, pixel patches.
Origin: 2026-11-08 original-character mod W7, MICA art lane. Requires Pillow.
"""
import argparse
import json
from collections import deque
from pathlib import Path
from PIL import Image, ImageDraw

LEVELS = (0, 36, 73, 109, 146, 182, 219, 255)
NEAREST = Image.Resampling.NEAREST


def remove_background(im, key=None, tolerance=24):
    """Preserve alpha; optional edge flood removes a solid key, not enclosed whites."""
    im = im.convert('RGBA')
    if key is None:
        return im
    px = im.load()
    todo = deque([(x, y) for x in range(im.width) for y in (0, im.height-1)] +
                 [(x, y) for y in range(im.height) for x in (0, im.width-1)])
    seen = set()
    while todo:
        x, y = todo.popleft()
        if (x, y) in seen or not (0 <= x < im.width and 0 <= y < im.height):
            continue
        seen.add((x, y))
        if max(abs(px[x, y][c]-key[c]) for c in range(3)) <= tolerance:
            px[x, y] = (0, 0, 0, 0)
            todo.extend(((x-1,y),(x+1,y),(x,y-1),(x,y+1)))
    return im


def largest_component(im):
    """Discard detached source speckles before resizing; retain a connected character."""
    mask = im.getchannel('A').point(lambda a: 255 if a >= 128 else 0)
    pixels = mask.load()
    remaining = {(x,y) for y in range(im.height) for x in range(im.width) if pixels[x,y]}
    largest = set()
    while remaining:
        seed = remaining.pop()
        component, todo = {seed}, [seed]
        while todo:
            x,y = todo.pop()
            for p in ((x-1,y),(x+1,y),(x,y-1),(x,y+1)):
                if p in remaining:
                    remaining.remove(p)
                    component.add(p)
                    todo.append(p)
        if len(component) > len(largest):
            largest = component
    if not largest:
        raise ValueError('Empty source cell')
    clean = Image.new('L', im.size)
    clean.putdata([255 if (x,y) in largest else 0 for y in range(im.height) for x in range(im.width)])
    im.putalpha(clean)
    return im.crop(clean.getbbox())


def indexed(im, colors):
    out = Image.new('P', im.size, 0)
    out.putpalette(sum(colors, []) + [0] * (768 - len(colors)*3))
    cache = {}
    data = []
    for r,g,b,a in (im.get_flattened_data() if hasattr(im, "get_flattened_data") else im.getdata()):
        if a < 128:
            data.append(0)
            continue
        rgb = (r,g,b)
        if rgb not in cache:
            cache[rgb] = min(range(1,16), key=lambda i: sum((rgb[c]-colors[i][c])**2 for c in range(3)))
        data.append(cache[rgb])
    out.putdata(data)
    out.info['transparency'] = 0
    return out


def outline(im):
    """One native pixel silhouette ink, inside alpha boundary (no size growth)."""
    old = im.copy()
    for y in range(im.height):
        for x in range(im.width):
            if old.getpixel((x,y)) and any(not (0 <= a < im.width and 0 <= b < im.height) or old.getpixel((a,b)) == 0 for a,b in ((x-1,y),(x+1,y),(x,y-1),(x,y+1))):
                im.putpixel((x,y),1)
    return im


def build(recipe_path, review=None):
    recipe_path = Path(recipe_path).resolve()
    root = recipe_path.parent
    recipe = json.loads(recipe_path.read_text())
    palette = json.loads((root / recipe['palette']).read_text())
    colors = palette['colors']
    if len(colors) != 16 or any(len(c)!=3 or any(v not in LEVELS for v in c) for c in colors):
        raise ValueError('Palette requires transparent slot plus 15 Mega Drive RGB triplets')
    if len(set(map(tuple,colors[1:]))) != 15:
        raise ValueError('Opaque colours must be unique')
    (root/'frames').mkdir(exist_ok=True)
    atlases = {}
    records, images = [], {}
    for frame in recipe['frames']:
        source = frame.get('source')
        if 'native_pixels' in frame:
            # Final handoff pixels are authoritative; do not rotate/re-filter them.
            native=frame['native_pixels'];rows=native['rows']
            if not rows or len(set(map(len,rows)))!=1:
                raise ValueError('Native pixel rows must form a nonempty rectangle')
            cut=Image.new('P',(len(rows[0]),len(rows)))
            cut.putpalette(sum(colors,[])+[0]*(768-48))
            cut.putdata([int(v,16) for row in rows for v in row])
            anchor=native['anchor']
            canvas=Image.new('P',(80,80));canvas.putpalette(cut.getpalette())
            canvas.paste(cut,(40-anchor[0],40-anchor[1]))
        elif source:
            if source not in atlases:
                atlases[source] = Image.open(root/source).convert('RGBA')
            atlas = atlases[source]
            cols, rows = frame['grid']
            col, row = frame['cell']
            bounds = (col*atlas.width//cols, row*atlas.height//rows,
                      (col+1)*atlas.width//cols, (row+1)*atlas.height//rows)
            cut = largest_component(remove_background(atlas.crop(bounds), frame.get('background')))
            h = frame['height']
            w = frame.get('width', round(cut.width*h/cut.height))
            cut = cut.resize((w,h), Image.Resampling.LANCZOS)
            cut = outline(indexed(cut, colors))
            # Hotspot is engine centre; standing floor is centre + 19, ball centred.
            anchor = frame.get('anchor', [round(w*.64), h-19])
            if anchor == [-1,-1]:
                anchor = [w//2,h//2]
            canvas = Image.new('P', (80,80), 0)
            canvas.putpalette(cut.getpalette())
            canvas.paste(cut, (40-anchor[0],40-anchor[1]))
        else:
            base = images[frame['derive']]
            canvas = base.rotate(frame.get('angle',0), resample=NEAREST, center=(40,40), fillcolor=0)
            canvas = outline(canvas)
        for x,y,index in frame.get('patches',[]):
            if not (0 <= x < 80 and 0 <= y < 80 and 0 <= index < 16):
                raise ValueError('Invalid native pixel patch')
            canvas.putpixel((x,y),index)
        outline_mode = frame.get('outline_mode', 'ink')
        if outline_mode not in ('ink', 'material'):
            raise ValueError('Unknown outline_mode: ' + str(outline_mode))
        if outline_mode == 'ink':
            canvas = outline(canvas)
        canvas.info['transparency'] = 0
        images[frame['id']] = canvas
        # Tight crop with explicit hotspot; no 80x80 padding shipped.
        bbox = (40, 40, 41, 41) if frame.get('blank') else canvas.getbbox()
        if not bbox or bbox[0] == 0 or bbox[1] == 0 or bbox[2] == 80 or bbox[3] == 80:
            raise ValueError(f"Clipped/empty sprite: {frame['id']}")
        trimmed = canvas.crop(bbox)
        trimmed.save(root/'frames'/f"{frame['id']}.png", transparency=0, optimize=True, bits=4)
        records.append(dict(id=frame['id'], animation=frame['animation'], order=frame['order'],
                            file=f"frames/{frame['id']}.png", hotspot=[40-bbox[0],40-bbox[1]],
                            angle_set=frame.get('angle_set','flat'), duration_ticks=frame.get('ticks',6),
                            notes=frame.get('notes','')))
    (root/'frames.yaml').write_text(json.dumps(dict(format='openggf-playable-art-handoff-1',
        **{k: recipe[k] for k in ('character', 'requiredAnimations') if k in recipe},
        palette=recipe['palette'], facing='right', tick_rate=60, frames=records,
        **({'scripts': recipe['scripts']} if 'scripts' in recipe else {}),
        **({'nativeFrames': recipe['nativeFrames']} if 'nativeFrames' in recipe else {})), indent=2)+'\n')
    if review:
        make_review(images, records, Path(review), colors)
    print(f'Built {len(records)} indexed frames; one shared 15-colour palette + transparent index 0')
    return records


def make_review(images, records, dest, colors):
    dest.mkdir(parents=True, exist_ok=True)
    for scale in (1,4):
        cw,ch = 100*scale,100*scale
        sheet = Image.new('RGB',(cw*8,ch*((len(records)+7)//8)),(146,146,146))
        draw = ImageDraw.Draw(sheet)
        for n,r in enumerate(records):
            im = images[r['id']].convert('RGBA').resize((80*scale,80*scale),NEAREST)
            x,y = n%8*cw,n//8*ch
            sheet.paste(im,(x,y),im)
            draw.text((x,y+80*scale),r['id'],fill=(0,0,0))
        sheet.save(dest/f'contact-{scale}x.png')
    groups = {}
    for r in records:
        key = r['animation']+'-'+r['angle_set']
        groups.setdefault(key,[]).append(r)
    for key, frames in groups.items():
        frames.sort(key=lambda r:r['order'])
        previews=[]
        for r in frames:
            im=images[r['id']].convert('RGBA')
            bg=Image.new('RGBA',im.size,(146,146,146,255))
            bg.alpha_composite(im)
            previews.append(bg.convert('RGB').resize((320,320),NEAREST))
        previews[0].save(dest/f'{key}.gif',save_all=True,append_images=previews[1:],
                         duration=[round(r['duration_ticks']*1000/60) for r in frames],loop=0,disposal=2)


if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('recipe',type=Path)
    parser.add_argument('--review',type=Path)
    args=parser.parse_args()
    build(args.recipe,args.review)
