#!/usr/bin/env python3
"""Rebuild title wag poses from one indexed glove, never independent anatomy.

Inputs: maintained master/landmarks, ownership contract and approved plane13.
Origin: changing finger-count report, 2026-11-10. Native fingertip-to-wrist
angles measured from palette-correct 8x references; no native art is stored.
Scale2x three times, rotate at 8x, majority-reduce (RotSprite-style).
"""
import argparse,json,math
from collections import Counter
from pathlib import Path
from PIL import Image
from s3k_hand_ownership import points_from_spans

def scale2x(image):
    out=Image.new('P',(image.width*2,image.height*2));out.putpalette(image.getpalette())
    def at(x,y):return image.getpixel((max(0,min(image.width-1,x)),max(0,min(image.height-1,y))))
    for y in range(image.height):
        for x in range(image.width):
            b,d,e,f,h=at(x,y-1),at(x-1,y),at(x,y),at(x+1,y),at(x,y+1)
            values=(d if d==b else e,f if b==f else e,d if d==h else e,f if h==f else e) if b!=h and d!=f else (e,)*4
            for (dx,dy),value in zip(((0,0),(1,0),(0,1),(1,1)),values):out.putpixel((x*2+dx,y*2+dy),value)
    return out

def landmark(point,pivot,degrees):
    a=math.radians(degrees);x,y=point[0]-pivot[0],point[1]-pivot[1]
    return [pivot[0]+x*math.cos(a)-y*math.sin(a),pivot[1]+x*math.sin(a)+y*math.cos(a)]

def derive(master,pivot,degrees):
    if degrees==0:return master.copy()
    enlarged=master
    for _ in range(3):enlarged=scale2x(enlarged)
    enlarged=enlarged.rotate(-degrees,resample=Image.Resampling.NEAREST,center=(pivot[0]*8,pivot[1]*8),fillcolor=0)
    out=master.copy()
    for y in range(master.height):
        for x in range(master.width):
            block=list(enlarged.crop((x*8,y*8,x*8+8,y*8+8)).getdata());counts=Counter(block);maximum=max(counts.values());center=block[4*8+4]
            out.putpixel((x,y),center if counts[center]==maximum else min(k for k,v in counts.items() if v==maximum))
    return out

def rebuild(root):
    spec=json.loads((root/'s3k-hand-anatomy.json').read_text());contract=json.loads((root/'s3k-hand-ownership.json').read_text());master=Image.open(root/'s3k-hand-master.png');plane=Image.open(root/'s3k-plane-13-right.png')
    for selector,angle in spec['relative_angles'].items():
        pose=derive(master,spec['wrist'],angle);frame=Image.new('P',master.size);frame.putpalette(master.getpalette());frame.info['transparency']=0
        for x,y in points_from_spans(contract['ownership_rows']):frame.putpixel((x,y),plane.getpixel((x,y+60)) or 1)
        for y in range(pose.height):
            for x in range(pose.width):
                if pose.getpixel((x,y)):frame.putpixel((x,y),pose.getpixel((x,y)))
        frame.save(root/f's3k-sprite-{selector}.png',bits=4)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--art',type=Path,default=Path(__file__).parent);args=parser.parse_args();rebuild(args.art)
