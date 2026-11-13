#!/usr/bin/env python3
"""Purpose: reduce fresh native-pose S3K roll-in generations into existing plane slots.
Inputs: five-column/two-row small sheet, two-column giant sheet, native RGBA exports.
Origin: optional owner roll-in enhancement, 2026-11-10. Native pixels never packaged.
"""
import argparse,json,math,hashlib
from pathlib import Path
from PIL import Image

def reduce(image, palette):
    result=Image.new('P',image.size)
    result.putpalette(palette)
    pixels=[]
    for i,(red,green,blue,alpha) in enumerate(image.getdata()):
        if alpha<128:
            pixels.append(0)
            continue
        rgb=(red,green,blue)
        nearest=sorted(range(2,8),key=lambda k:sum((rgb[c]-palette[k*3+c])**2 for c in range(3)))[:2]
        first,second=nearest
        a=palette[first*3:first*3+3];b=palette[second*3:second*3+3]
        axis=[v-u for u,v in zip(a,b)]
        weight=max(0,min(1,sum((v-u)*d for v,u,d in zip(rgb,a,axis))/max(1,sum(d*d for d in axis))))
        x,y=i%image.width,i//image.width
        threshold=((.125,.625),(.875,.375))[y%2][x%2]
        pixels.append(second if weight>threshold else first)
    result.putdata(pixels);result.info['transparency']=0
    return result

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--generation',type=Path,required=True)
    parser.add_argument('--small-generation',type=Path,required=True)
    parser.add_argument('--native',type=Path,required=True)
    parser.add_argument('--output',type=Path,default=Path(__file__).parent)
    args=parser.parse_args()
    large_source=Image.open(args.generation).convert('RGBA')
    small_source=Image.open(args.small_generation).convert('RGBA')
    palette=Image.open(args.output/'s3k-plane-13-left.png').getpalette()
    measurements=[]
    for column,frame in enumerate(range(2,8)):
        # Independent top-row take A; no edit chain or pixel recolour of native art.
        source=small_source if frame<7 else large_source
        columns=5 if frame<7 else 2
        selected_column=column if frame<7 else 0
        height=source.height//2 if frame<7 else source.height
        box=(round(selected_column*source.width/columns),0,round((selected_column+1)*source.width/columns),height)
        image=source.crop(box)
        image.putdata([(r,g,b,0 if r>150 and b>150 and g<110 else 255) for r,g,b,a in image.getdata()])
        # Remove cell-neighbour fragments and isolated keying speckles only.
        pending={(x,y) for y in range(image.height) for x in range(image.width) if image.getpixel((x,y))[3]}
        largest=set()
        while pending:
            seed=pending.pop();component={seed};todo=[seed]
            while todo:
                x,y=todo.pop()
                for point in [(x-1,y),(x+1,y),(x,y-1),(x,y+1)]:
                    if point in pending:
                        pending.remove(point);component.add(point);todo.append(point)
            if len(component)>len(largest):largest=component
        image.putdata([(r,g,b,a if (i%image.width,i//image.width) in largest else 0) for i,(r,g,b,a) in enumerate(image.getdata())])
        image=image.crop(image.getbbox())
        native=Image.open(args.native/f's3k-mascot-{frame}.png').convert('RGBA')
        if frame==7:
            # The old reference cropped all ownership at row80, leaving native
            # blue echoes in transparent SEGA letter gaps. Isolate the complete
            # native contribution against the preceding logo-only lower plane.
            composition=Image.open(args.native/'s3k-plane-7.png').convert('RGBA')
            logo=Image.open(args.native/'s3k-plane-6.png').convert('RGBA')
            mask=bytearray((args.output/'s3k-plane-7.mask').read_bytes())
            native=Image.new('RGBA',(320,224))
            for y in range(224):
                for x in range(320):
                    if y>=80 and composition.getpixel((x,y))[3] and not logo.getpixel((x,y))[3]:
                        mask[y*320+x]=1
                    if mask[y*320+x]:native.putpixel((x,y),composition.getpixel((x,y)))
            (args.output/'s3k-plane-7.mask').write_bytes(mask)
        bounds=native.getbbox()
        image=image.resize((bounds[2]-bounds[0],bounds[3]-bounds[1]),Image.Resampling.LANCZOS)
        indexed=reduce(image,palette)
        canvas=Image.new('P',(320,224));canvas.putpalette(palette);canvas.info['transparency']=0
        canvas.paste(indexed,bounds[:2])
        if frame==7:
            # The native effect is precomposed behind the SEGA glyphs in its
            # plane mapping, not a sprite over the letters. Preserve that pass.
            for y in range(80,224):
                for x in range(320):
                    if logo.getpixel((x,y))[3]:canvas.putpixel((x,y),0)
        for half,rect in [('left',(0,0,160,224)),('right',(160,0,320,224))]:
            canvas.crop(rect).save(args.output/f's3k-plane-{frame}-{half}.png',bits=4)
        native_area=sum(p[3]>0 for p in native.getdata())
        authored_area=sum(p>0 for p in canvas.getdata())
        measurements.append({'selector':frame,'bounds':bounds,'native_area':native_area,'authored_area':authored_area,'size_ratio':math.sqrt(authored_area/native_area)})
    print(json.dumps({'generation_sha256':hashlib.sha256(args.generation.read_bytes()).hexdigest(),'frames':measurements,'size_ratio':math.sqrt(sum(r['authored_area'] for r in measurements)/sum(r['native_area'] for r in measurements))},indent=2))
if __name__=='__main__':main()
