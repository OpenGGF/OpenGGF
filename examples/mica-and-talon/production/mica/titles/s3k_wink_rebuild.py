#!/usr/bin/env python3
"""Reduce fresh native-pose eye generations into whole-lash patches.
Inputs: selected2x2 GPT Image sheet, maintained ownership contract/plane palette.
Origin: split-lashes report, 2026-11-10. Left candidates selected; the two-pixel
context band is copied exactly, not painted. No native decoded art is bundled.
"""
import argparse,json
from pathlib import Path
from PIL import Image,ImageFilter
from s3k_wink_continuity import inputs
from s3k_hand_ownership import points_from_spans

def rebuild(generation,root):
    plane,_,contract=inputs(root);palette=plane.getpalette();ox,oy=contract['origin'];owned=points_from_spans(contract['ownership_rows']);mask=Image.new('L',(320,224))
    for x,y in owned:mask.putpixel((x+ox,y+oy),255)
    inner=mask.filter(ImageFilter.MinFilter(5));source=Image.open(generation).convert('RGB')
    for selector,top,bottom in ((2,0,source.height//2),(3,source.height//2,source.height)):
        generated=source.crop((0,top,source.width//2,bottom)).resize((84,76),Image.Resampling.LANCZOS);frame=Image.new('P',contract['size']);frame.putpalette(palette);frame.info['transparency']=0
        for x,y in owned:
            sx,sy=x+ox,y+oy;value=plane.getpixel((sx,sy)) or 1
            if inner.getpixel((sx,sy)):
                rgb=generated.getpixel((sx-100,sy-24));u,v=sorted(range(1,14),key=lambda k:sum((rgb[c]-palette[k*3+c])**2 for c in range(3)))[:2];axis=[palette[v*3+c]-palette[u*3+c] for c in range(3)];weight=max(0,min(1,sum((rgb[c]-palette[u*3+c])*axis[c] for c in range(3))/max(1,sum(d*d for d in axis))));threshold=((.125,.625),(.875,.375))[sy%2][sx%2];value=v if weight>threshold else u
            frame.putpixel((x,y),value)
        frame.save(root/f's3k-sprite-{selector}.png',bits=4)

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--generation',type=Path,required=True);parser.add_argument('--art',type=Path,default=Path(__file__).parent);args=parser.parse_args();rebuild(args.generation,args.art)
