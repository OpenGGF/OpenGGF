#!/usr/bin/env python3
"""Purpose: sample visible native ghost bands and compare retained source detail.
Inputs: measurements.json, native exports, GPT/deterministic candidate PNGs.
Origin: owner heavy-blur retry, 2026-11-10. Sample rectangles are emitted for review.
Density/brightness are raster measurements, not uniquely recovered physical alpha.
"""
from PIL import Image,ImageDraw,ImageFilter
from pathlib import Path
import argparse,numpy as np,json
from s3k_heavy_blur import native_reference
parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--scratch',type=Path,required=True);parser.add_argument('--native',type=Path,required=True);args=parser.parse_args();p=args.scratch;n=args.native;d=json.loads((p/'measurements.json').read_text());points={2:[(163,52),(166,48),(168,45)],3:[(168,46),(172,40),(175,35)],4:[(176,37),(181,30),(187,23)],5:[(186,26),(193,18),(200,10)],6:[(195,22),(204,12),(214,3)],7:[(214,40),(181,72),(172,105)],8:[(223,174),(222,150),(220,135)],9:[(82,132),(59,148),(36,163),(15,176)],10:[(87,125),(69,133),(50,140)]}
for row in d['planes']:
 f=row['selector'];a=native_reference(f,n)
 rgba=np.array(a,dtype=float);luma=rgba[:,:,:3]@np.array([.2126,.7152,.0722]);radius=2 if f<=6 else 4;primary=points[f][0];x,y=primary;reference=float(luma[max(0,y-radius):y+radius,max(0,x-radius):x+radius].mean());bands=[]
 for x,y in points[f]:
  patch=luma[max(0,y-radius):y+radius,max(0,x-radius):x+radius];bands.append({'centre':[x,y],'patch_size':radius*2,'luma_mean':round(float(patch.mean()),3),'half_primary_bright_density':round(float((patch>=reference*.5).mean()),3),'equivalent_primary_luma':round(float(patch.mean()/max(1,reference)),3)})
 row['native_corresponding_material_bands']=bands
 # Measured retention of original MICA mid-frequency detail at original screen
 # coordinates, after suppressing palette dithering. NOT a native sharp-area claim.
 def detail(im):
  rgb=im.convert('RGB');return np.array(rgb.filter(ImageFilter.GaussianBlur(1.3)),float)-np.array(rgb.filter(ImageFilter.GaussianBlur(4)),float)
 sharp=Image.open(p/f'sharp/{f}.png');h=detail(sharp);row['source_detail_retention']={}
 for kind in ('gpt','deterministic'):
  q=detail(Image.open(p/f'{kind}/{f}.png'));row['source_detail_retention'][kind]=round(float(np.clip((h*q).sum()/max(1,(h*h).sum()),0,1)),3)
 draw=a.convert('RGB').resize((960,672),Image.Resampling.NEAREST);dr=ImageDraw.Draw(draw)
 for i,(x,y) in enumerate(points[f]):dr.rectangle(((x-radius)*3,(y-radius)*3,(x+radius)*3,(y+radius)*3),outline=['yellow','red','magenta','cyan'][i],width=1);dr.text((x*3,y*3),str(i+1),fill='white')
 draw.save(p/f'band-samples-{f}.png')
d['limit']+=' Corresponding-material patches give observed brightness/dither falloff, not unique physical alpha; overlaps and shading can make the sequence non-monotonic. Mid-frequency retention measures old MICA detail, not total perceived sharpness.'
(p/'measurements.json').write_text(json.dumps(d,indent=2)+'\n')
for row in d['planes']:print(row['selector'],[b['equivalent_primary_luma'] for b in row['native_corresponding_material_bands']],row['source_detail_retention'])
