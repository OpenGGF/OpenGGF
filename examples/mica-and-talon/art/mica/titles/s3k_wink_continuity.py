#!/usr/bin/env python3
"""Check whole-lash ownership and ink continuity, including the alpha boundary.
Inputs: indexed plane13/wink2/3 and maintained semantic lash landmarks/footprint.
Origin: owner split-eyelashes report, 2026-11-10. The whole old lash group is
inside the patch; any remaining ink crossing the boundary must equal the plane
on both sides. Eight-connected ink paths join the lid root to all three tips.
"""
import json
from pathlib import Path
from PIL import Image
from s3k_hand_ownership import points_from_spans

def neighbours(point):
    x,y=point
    return [(x+dx,y+dy) for dy in (-1,0,1) for dx in (-1,0,1) if dx or dy]

def errors(plane,frames,contract):
    failures=[];ox,oy=contract['origin'];owned=points_from_spans(contract['ownership_rows']);ink=set(contract['ink_indices']);old=points_from_spans(contract['old_lash_rows'])
    for selector,frame in frames.items():
        if list(frame.size)!=contract['size']:failures.append(('size',selector));continue
        if frame.getpalette()!=plane.getpalette():failures.append(('palette mismatch',selector))
        if any(frame.getpixel((x,y)) and (x,y) not in owned for y in range(frame.height) for x in range(frame.width)):failures.append(('unexpected ownership',selector))
        for x,y in owned:
            if not frame.getpixel((x,y)):failures.append(('unowned',selector,x,y))
        def at(pt):
            x,y=pt
            return frame.getpixel(pt) if 0<=x<frame.width and 0<=y<frame.height and frame.getpixel(pt) else plane.getpixel((x+ox,y+oy))
        # Original lash footprint has a complete one-pixel opaque surround.
        for pt in old:
            if any(n not in owned for n in [pt]+neighbours(pt)):failures.append(('old lash at boundary',selector,*pt))
        for pt in owned:
            for other in neighbours(pt):
                if other in owned:continue
                pair=[pt,other]
                if any(at(p) in ink or plane.getpixel((p[0]+ox,p[1]+oy)) in ink for p in pair):
                    palette=plane.getpalette();rgb=lambda value:tuple(palette[value*3:value*3+3])
                    # Transparent0 and opaque title-black1 share RGB; context
                    # deliberately owns black without exposing underlying art.
                    if any(rgb(at(p))!=rgb(plane.getpixel((p[0]+ox,p[1]+oy))) for p in pair):failures.append(('ink seam',selector,*pt))
        landmarks=contract['states'][str(selector)];root=tuple(landmarks['root']);box=contract['lash_roi'];pending=[root];seen=set()
        while pending:
            point=pending.pop();x,y=point
            if point in seen or not(box[0]<=x<box[2] and box[1]<=y<box[3]) or at(point) not in ink:continue
            seen.add(point);pending.extend(neighbours(point))
        for name,point in [('root',root)]+[(f'tip{i}',tuple(pt)) for i,pt in enumerate(landmarks['tips'])]:
            if point not in seen:failures.append(('disconnected lash',selector,name,*point))
    return failures

def inputs(root):
    left=Image.open(root/'s3k-plane-13-left.png');plane=Image.new('P',(320,224));plane.putpalette(left.getpalette());plane.paste(left,(0,0));plane.paste(Image.open(root/'s3k-plane-13-right.png'),(160,0))
    return plane,{i:Image.open(root/f's3k-sprite-{i}.png') for i in (2,3)},json.loads((root/'s3k-wink-ownership.json').read_text())

if __name__=='__main__':
    result=errors(*inputs(Path(__file__).parent));print('wink continuity failures:',len(result));assert not result,result[:20]
