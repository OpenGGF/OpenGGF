#!/usr/bin/env python3
"""Purpose: native-strength title blur A/B candidates and visible-kernel measurements.
Inputs: scratch native plane exports, approved sharp MICA planes, optional GPT sheets.
Origin: owner rejection of roll-in pass1, 2026-11-10. NumPy/Pillow, offline only.
Flattened art cannot uniquely recover a 3D shutter. Landmark spans/copy counts are
visible estimates; sampled dither coverage and edge fractions are measured pixels.
No runtime gameplay or timing consumes these measurements.
"""
import argparse,json,math
from pathlib import Path
import numpy as np
from PIL import Image,ImageFilter,ImageDraw

# Native-pixel visible spans, read against nearest8x exports. These are art recipe
# measurements, not an inferred hardware or 3D renderer implementation.
MEASURE={
2:dict(vector=[2,-4],copies=2,spacing=3,sharp_estimate=[.1,.25]),
3:dict(vector=[4,-6],copies=3,spacing=3.6,sharp_estimate=[.1,.2]),
4:dict(vector=[6,-8],copies=3,spacing=5,sharp_estimate=[.1,.2]),
5:dict(vector=[7,-8],copies=3,spacing=5.3,sharp_estimate=[.1,.2]),
6:dict(vector=[9,-12],copies=3,spacing=7.5,sharp_estimate=[.05,.15]),
7:dict(vector=[-22,48],copies=3,spacing=26.4,sharp_estimate=[.05,.15]),
8:dict(vector=[0,28],copies=3,spacing=14,sharp_estimate=[0,.1],landmarks=[[235,122],[235,136],[237,149]],note='Brow ridges; radial zoom makes glove smear40–55px, not one global translation.'),
9:dict(vector=[-67,43],copies=4,spacing=26.5,sharp_estimate=[0,.1],landmarks=[[84,132],[63,145],[39,160],[17,175]],note='Left glove echo centres; right glove moves outward/down: spatially varying 3D motion.'),
10:dict(vector=[12,24],copies=3,spacing=13.4,sharp_estimate=[.05,.15],landmarks=[[122,173],[128,185],[134,197]],note='Pale boot-edge bands; hands have20–30px lateral spread; no readable pupil.'),
}

def quantize(rgb,palette,allowed=None):
    colors=np.array(palette[:48],dtype=np.float32).reshape(16,3)
    slots=np.array(allowed if allowed is not None else list(range(1,16)))
    delta=((rgb[:,:,None,:]-colors[slots][None,None,:,:])**2).sum(axis=3)
    order=slots[np.argsort(delta,axis=2)[:,:,:2]]
    first=colors[order[:,:,0]];second=colors[order[:,:,1]];axis=second-first
    weight=np.clip(((rgb-first)*axis).sum(axis=2)/np.maximum((axis*axis).sum(axis=2),1),0,1)
    # Fixed4x4 Bayer: palette shade occupancy encodes coverage after black matting.
    b=np.array([[0,2],[3,1]],dtype=np.float32)
    for _ in range(1):b=np.block([[4*b,4*b+2],[4*b+3,4*b+1]])
    threshold=np.tile((b+.5)/16,(math.ceil(rgb.shape[0]/4),math.ceil(rgb.shape[1]/4)))[:rgb.shape[0],:rgb.shape[1]]
    indices=np.where(weight>threshold,order[:,:,1],order[:,:,0]).astype(np.uint8)
    indices[(np.max(rgb,axis=2)<4)|(indices==1)]=0
    image=Image.fromarray(indices,'P');image.putpalette(palette);image.info['transparency']=0
    return image

def transform(image,scale,angle,dx,dy,centre):
    a=math.cos(math.radians(angle))/scale;b=math.sin(math.radians(angle))/scale
    cx,cy=centre
    # Inverse destination->source affine, preserving native screen registration.
    return image.transform((320,224),Image.Transform.AFFINE,(a,b,cx-a*(cx+dx)-b*(cy+dy),-b,a,cy+b*(cx+dx)-a*(cy+dy)),Image.Resampling.BICUBIC)

def exposure(source,samples,palette,allowed=None):
    total=np.zeros((224,320,3),np.float32)
    for scale,angle,dx,dy,weight,centre in samples:
        layer=np.array(transform(source,scale,angle,dx,dy,centre),dtype=np.float32)/255
        color=np.where(layer[:,:,:3]<=.04045,layer[:,:,:3]/12.92,((layer[:,:,:3]+.055)/1.055)**2.4)
        total+=weight*color*layer[:,:,3,None]
    rgb=np.where(total<=.0031308,total*12.92,1.055*np.power(np.maximum(total,0),1/2.4)-.055)*255
    return quantize(np.clip(rgb,0,255),palette,allowed)

def deterministic(frame,sharp,palette,native):
    samples=[];image=sharp.convert('RGBA').filter(ImageFilter.GaussianBlur(.65 if frame>=8 else .25))
    if frame<=7:
        boxes={2:(8,10,159,48),3:(10,14,163,40),4:(13,15,170,30),5:(17,18,178,17),6:(26,26,184,9),7:(84,84,170,0)}
        width,height,x,y=boxes[frame];crop=image.crop(image.getbbox()).resize((width,height),Image.Resampling.LANCZOS)
        image=Image.new('RGBA',(320,224));image.paste(crop,(x,y));vx,vy=MEASURE[frame]['vector']
        peaks=[(0,.50),(.5,.32),(1,.18)]
        for position,weight in peaks:
            for jitter in [-.10,-.05,0,.05,.10]:
                t=max(0,min(1,position+jitter));samples.append((1,0,vx*t,vy*t,weight/5,(160,112)))
    else:
        if frame==8:
            peaks=[(.85,0,0,0,.25),(1,0,0,0,.45),(1.15,0,0,0,.30)];centre=(160,290)
        elif frame==9:
            peaks=[(.80,-8,0,-10,.15),(.95,-3,0,0,.25),(1.10,2,0,10,.35),(1.25,7,0,20,.25)];centre=(160,0)
        else:
            peaks=[(.90,-3,-12,-5,.20),(.95,-1.5,-6,-2.5,.20),(1,0,0,0,.20),(1.05,1.5,6,2.5,.20),(1.10,3,12,5,.20)];centre=(160,80)
        for scale,angle,dx,dy,weight in peaks:
            for jitter in [-1,-.5,0,.5,1]:samples.append((scale+jitter*.025,angle+jitter,dx,dy,weight/5,centre))
    result=exposure(image,samples,palette,list(range(1,8))+[11,12,13] if frame<=7 else None)
    if frame==7:
        logo=np.array(Image.open(native/'s3k-plane-6.png').convert('RGBA'))[:,:,3]>0
        pixels=np.array(result);pixels[80:][logo[80:]]=0
        result=Image.fromarray(pixels,'P');result.putpalette(palette);result.info['transparency']=0
    return result,samples

def edge_fraction(image):
    array=np.array(image.convert('RGBA'));rgb=array[:,:,:3].astype(float)
    opaque=np.array(image.convert('RGBA').getchannel('A').filter(ImageFilter.MinFilter(3)))>0
    edge=np.maximum(np.max(np.abs(rgb-np.roll(rgb,1,axis=0)),axis=2),np.max(np.abs(rgb-np.roll(rgb,1,axis=1)),axis=2))
    return float(((edge>64)&opaque).sum()/max(1,opaque.sum()))

def native_reference(frame,native):
    if frame!=7:return Image.open(native/f's3k-mascot-{frame}.png').convert('RGBA')
    image=Image.open(native/'s3k-plane-7.png').convert('RGBA')
    logo=Image.open(native/'s3k-plane-6.png').convert('RGBA')
    for y in range(80,224):
        for x in range(320):
            if logo.getpixel((x,y))[3]:image.putpixel((x,y),(0,0,0,0))
    return image

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--scratch',type=Path,required=True);parser.add_argument('--native',type=Path,required=True);args=parser.parse_args();p=args.scratch;n=args.native
    palette=Image.open(p/'sharp/13-left.png').getpalette();dest=p/'deterministic';dest.mkdir(exist_ok=True);rows=[]
    for frame in range(2,11):
        sharp=Image.open(p/f'sharp/{frame}.png');native=native_reference(frame,n)
        result,kernel=deterministic(frame,sharp,palette,n);result.save(dest/f'{frame}.png',bits=4)
        native.resize((960,672),Image.Resampling.NEAREST).save(p/f'native-{frame}-3x.png');result.convert('RGBA').resize((960,672),Image.Resampling.NEAREST).save(dest/f'{frame}-3x.png')
        row=dict(selector=frame,visible=MEASURE[frame],smear_length_px=math.hypot(*MEASURE[frame]['vector']),kernel=kernel,native_interior_hard_edge_fraction=edge_fraction(native),sharp_mica_interior_hard_edge_fraction=edge_fraction(sharp),blur_mica_interior_hard_edge_fraction=edge_fraction(result))
        # Actual native8x8 band samples: mean colour coverage and fraction of
        # foreground pixels above half the local native peak (a dither proxy,
        # NOT uniquely recovered physical opacity).
        bb=native.convert('RGBA').getbbox();crops=[]
        for fraction in [.25,.5,.75]:
            x=round(bb[0]+(bb[2]-bb[0])*fraction);y=round(bb[1]+(bb[3]-bb[1])*fraction);crop=np.array(native.convert('RGBA').crop((x-4,y-4,x+4,y+4)));v=crop[:,:,:3].max(axis=2);crops.append(dict(centre=[x,y],opaque_density=float((crop[:,:,3]>0).mean()),bright_density=float((v>=128).mean())))
        row['native_8x8_band_densities']=crops;rows.append(row)
    (p/'measurements.json').write_text(json.dumps(dict(limit='Visible landmarks and dither samples do not uniquely determine the original3D shutter kernel. Sharp estimates are visual ranges; hard-edge fractions are separate measured raster proxies.',planes=rows),indent=2)+'\n')
if __name__=='__main__':main()
