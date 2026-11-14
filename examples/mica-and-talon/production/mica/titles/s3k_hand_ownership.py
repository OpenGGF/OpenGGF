#!/usr/bin/env python3
"""Purpose: one complete title glove owner, with native-style context replacement.
Inputs: approved plane13 and sprite0/1/4 snapshots, fresh handless CGI backing.
Origin: owner doubled-thumb/halo report, 2026-11-10. Pillow, no runtime dependency.
Reconstruction is palette-independent anatomical ownership, not grey-only removal.
"""
import argparse,json,hashlib
from pathlib import Path
from PIL import Image,ImageFilter

ORIGIN=(160,60);SIZE=(56,60)
def spans(points):
    result=[]
    for y in sorted({y for x,y in points}):
        xs=sorted(x for x,yy in points if yy==y);start=last=xs[0]
        for x in xs[1:]:
            if x!=last+1:result.append([y,start,last+1]);start=x
            last=x
        result.append([y,start,last+1])
    return result

def points_from_spans(rows):
    return {(x,y) for y,start,end in rows for x in range(start,end)}

def source_errors(plane,sprites,contract):
    errors=[];region=points_from_spans(contract['ownership_rows'])
    for x,y in region:
        if plane.getpixel((x+ORIGIN[0],y+ORIGIN[1]))>=8:errors.append(('plane glove residue',x,y))
        for selector,frame in sprites.items():
            if frame.size!=SIZE or frame.getpixel((x,y))==0:errors.append(('unowned context',selector,x,y))
    return errors

def quant_background(image,palette):
    output=Image.new('P',image.size);output.putpalette(palette);output.info['transparency']=0;data=[]
    for i,(r,g,b) in enumerate(image.convert('RGB').getdata()):
        if max(r,g,b)<8:data.append(0);continue
        rgb=(r,g,b);near=sorted(range(1,7),key=lambda k:sum((rgb[c]-palette[k*3+c])**2 for c in range(3)))[:2];a,z=near;axis=[palette[z*3+c]-palette[a*3+c] for c in range(3)];weight=max(0,min(1,sum((rgb[c]-palette[a*3+c])*axis[c] for c in range(3))/max(1,sum(v*v for v in axis))));threshold=((.125,.625),(.875,.375))[(i//image.width)%2][i%2];data.append(z if weight>threshold else a)
    output.putdata(data);return output

def rebuild(source,background,output):
    left=Image.open(source/'s3k-plane-13-left.png');palette=left.getpalette();plane=Image.new('P',(320,224));plane.putpalette(palette);plane.info['transparency']=0;plane.paste(left,(0,0));plane.paste(Image.open(source/'s3k-plane-13-right.png'),(160,0));original={i:Image.open(source/f's3k-sprite-{i}.png') for i in (0,1,4)}
    full=plane.convert('RGBA');full.alpha_composite(original[4].convert('RGBA'),ORIGIN)
    materials={tuple(palette[k*3:k*3+3]) for k in range(8,14)}
    pending={(x,y) for y in range(60,120) for x in range(162,216) if full.getpixel((x,y))[3] and full.getpixel((x,y))[:3] in materials};groups=[]
    while pending:
        seed=pending.pop();group={seed};stack=[seed]
        while stack:
            x,y=stack.pop()
            for point in [(x-1,y),(x+1,y),(x,y-1),(x,y+1)]:
                if point in pending:pending.remove(point);group.add(point);stack.append(point)
        groups.append(group)
    primary=max(groups,key=len);face=set().union(*(g for g in groups if 50<len(g)<len(primary)))
    mask=Image.new('L',(320,224));guard=mask.copy()
    for point in primary:mask.putpixel(point,255)
    for point in face:guard.putpixel(point,255)
    guard=guard.filter(ImageFilter.MaxFilter(3));near=mask.filter(ImageFilter.MaxFilter(5))
    for group in groups:
        for point in group:
            if near.getpixel(point) and not guard.getpixel(point):mask.putpixel(point,255)
    for y in range(60):
        for x in range(56):
            if original[4].getpixel((x,y)) and not guard.getpixel((x+160,y+60)):mask.putpixel((x+160,y+60),255)
    mask=mask.filter(ImageFilter.MaxFilter(3)).filter(ImageFilter.MinFilter(3));expanded=mask.filter(ImageFilter.MaxFilter(3))
    # Include one native-style material-shadow contour, not a rectangular matte.
    for y in range(60,120):
        for x in range(160,216):
            rgba=full.getpixel((x,y))
            if expanded.getpixel((x,y)) and rgba[3] and rgba[:3] in [tuple(palette[3:6]),tuple(palette[6:9])] and not guard.getpixel((x,y)):mask.putpixel((x,y),255)
    region=mask.filter(ImageFilter.MaxFilter(5));anatomy={(x,y) for y in range(60,120) for x in range(160,216) if mask.getpixel((x,y)) and not guard.getpixel((x,y))};owned={(x,y) for y in range(60,120) for x in range(160,216) if region.getpixel((x,y)) and not guard.getpixel((x,y))}
    raw=Image.open(background).convert('RGB');patch=raw.crop((0,0,raw.width//2,raw.height));visible=patch.point(lambda v:255 if v>12 else 0).convert('L');patch=patch.crop(visible.getbbox()).resize((68,72),Image.Resampling.LANCZOS).crop((0,0,56,60));backing=quant_background(patch,palette)
    for x,y in owned:plane.putpixel((x,y),backing.getpixel((x-160,y-60)))
    frames={}
    for selector in (0,1,4):
        frame=Image.new('P',SIZE);frame.putpalette(palette);frame.info['transparency']=0
        # Every old hand pixel is replaced by the same current backing context;
        # transparent aperture becomes opaque title-local black1, never a halo.
        for x,y in owned:frame.putpixel((x-160,y-60),plane.getpixel((x,y)) or 1)
        if selector==4:
            colors={tuple(palette[k*3:k*3+3]):k for k in range(1,16)}
            for x,y in anatomy:
                rgba=full.getpixel((x,y))
                if rgba[3]:frame.putpixel((x-160,y-60),colors[rgba[:3]])
        else:
            original_frame=original[selector]
            for y in range(60):
                for x in range(56):
                    value=original_frame.getpixel((x,y))
                    if value:frame.putpixel((x,y),value)
        frames[selector]=frame
    local={(x-160,y-60) for x,y in owned};contract={'origin':ORIGIN,'size':SIZE,'ownership_rows':spans(local),'anatomy_rows':spans({(x-160,y-60) for x,y in anatomy}),'ownership_pixels':len(local),'background_sha256':hashlib.sha256(background.read_bytes()).hexdigest(),'native':'Obj_TitleSonicFinger (sonic3k.asm:6239-6258); word_4F96 and word_4FB0 (General/Title/Map - S3 Sonic Anim.asm:7-16); byte_4B36 (General/Title/Anim - S3 Sonic Finger.asm:3-4)','method':'Complete anatomical glove moved from plane to sprite4, including warm lighting/contour. Fresh handless CGI backing fills the entire old hand region. Every sprite0/1/4 owns identical backing context plus exactly one glove. Existing selectors, origins and six-tick timing unchanged.'}
    errors=source_errors(plane,frames,contract);assert not errors,errors[:10]
    # Only the right plane half changes; this operation is independent of blur art.
    plane.crop((160,0,320,224)).save(output/'s3k-plane-13-right.png',bits=4)
    for i,frame in frames.items():frame.save(output/f's3k-sprite-{i}.png',bits=4)
    (Path(__file__).resolve().parent / 's3k-hand-ownership.json').write_text(json.dumps(contract,indent=2)+'\n')
    print('owned',len(local),'anatomy',len(anatomy))

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--source',type=Path,required=True);parser.add_argument('--background',type=Path,required=True);parser.add_argument('--output',type=Path,default=Path(__file__).parent);args=parser.parse_args();rebuild(args.source,args.background,args.output)
if __name__=='__main__':main()
