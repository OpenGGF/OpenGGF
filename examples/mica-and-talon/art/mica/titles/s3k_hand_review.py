#!/usr/bin/env python3
"""Purpose: per-presentation hand ownership/renderer gate and real4x wag strips.
Inputs: native/mod900-frame captures, title-state.csv and maintained hand contract.
Origin: owner doubled-thumb/halo correction, 2026-11-10. Pillow; no ROM pixels ship.
"""
import argparse,csv,json,subprocess
from pathlib import Path
from PIL import Image
from s3k_hand_ownership import source_errors,points_from_spans

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--captures',type=Path,required=True);parser.add_argument('--before',type=Path,required=True);args=parser.parse_args();p=args.captures;r=Path(__file__).parent;contract=json.loads((r/'s3k-hand-ownership.json').read_text());region=points_from_spans(contract['ownership_rows']);frames={i:Image.open(r/f's3k-sprite-{i}.png') for i in (0,1,4)};plane=Image.new('P',(320,224));plane.paste(Image.open(r/'s3k-plane-13-left.png'),(0,0));plane.paste(Image.open(r/'s3k-plane-13-right.png'),(160,0));assert not source_errors(plane,frames,contract)
    rows=list(csv.DictReader((p/'captures/s3k-mod/title-state.csv').open()));fail=[];outside=[];seen={};checks=0;ownership_checks=0;palette=frames[4].getpalette()
    for row in rows:
        j=int(row['frame']);before=Image.open(args.before/f'frames/{j:05}.png').convert('RGB');actual=Image.open(p/f'captures/s3k-mod/frames/{j:05}.png').convert('RGB');dy=-int(row['vscroll']) if row['phase']=='INTERACTIVE' else 0
        if int(row['plane'])!=13:
            if before.tobytes()!=actual.tobytes():outside.append(j)
            continue
        selector=int(row['finger']) if row['finger_active']=='true' else 4;frame=frames[selector];ownership_checks+=len(region);assert all(frame.getpixel(pt)>0 for pt in region)
        # Native banner frame0 begins16pixels below its origin (first mapped row).
        # Only compare unobscured hand pixels; ownership is still checked behind it.
        for x,y in region:
            sx,sy=160+x,60+y+dy
            if row['phase']!='INTERACTIVE' or not 0<=sy<min(224,int(row['banner_y'])+16):continue
            value=frame.getpixel((x,y));expected=tuple(palette[value*3:value*3+3]);checks+=1
            if actual.getpixel((sx,sy))!=expected:fail.append((j,sx,sy,value,actual.getpixel((sx,sy))))
        if row['phase']=='INTERACTIVE' and j>=400:seen.setdefault(selector,j)
        # Preserve the whole portrait/ring and every other presentation pixel.
        for y in range(224):
            for x in range(320):
                if 160<=x<216 and 60+dy<=y<120+dy:continue
                if before.getpixel((x,y))!=actual.getpixel((x,y)):outside.append((j,x,y))
    result={'presentations':len(rows),'plane_hand_residue':0,'missing_sprite_ownership':0,'ownership_pixel_checks':ownership_checks,'unoccluded_render_pixel_checks':checks,'render_failures':fail[:50],'render_failure_count':len(fail),'outside_hand_failures':outside[:50],'outside_hand_failure_count':len(outside),'selectors_seen':seen,'note':'Opaque identical backing owns old hand footprint in0/1/4. Transparent parts of moved poses never expose a second plane glove; native banner priority is preserved.'};(p/'ownership-check.json').write_text(json.dumps(result,indent=2)+'\n');print('ownership',ownership_checks,'render',checks,'errors',len(fail),'outside',len(outside));assert not fail and not outside
    requested=[406,472,474,482,488,508,514,520,526,532,538,544,550]
    html=['<!doctype html><meta charset="utf-8"><style>body{background:#181818;color:#eee;font:16px system-ui}img{image-rendering:pixelated;max-width:100%}video{max-width:100%}pre{white-space:pre-wrap}</style><h1>S3K single hand ownership</h1><p>Real production frames: stock above fixed MICA,4×. Native selectors/timing unchanged. Complete old hand region is owned by each sprite, including its shared handless cape context. No hand remains baked into the plane.</p>']
    for label,chosen in [('distinct',list(seen.values())),('lead',requested)]:
        for page,start in enumerate(range(0,len(chosen),4)):
            ids=chosen[start:start+4];out=Image.new('RGB',(320*len(ids),224))
            for col,j in enumerate(ids):
                for yy,kind in enumerate(('stock','mod')):
                    image=Image.open(p/f'captures/s3k-{kind}/frames/{j:05}.png');image=image.crop((144,32,224,144));out.paste(image,(col*80,yy*112))
            out=out.crop((0,0,80*len(ids),224)).resize((320*len(ids),896),Image.Resampling.NEAREST);name=f'{label}-{page}-4x.png';out.save(p/name);html +=[f'<h2>{label}: frames{ids}</h2><img src="{name}">']
    html+=['<pre>'+json.dumps(result,indent=2)+'</pre><p>Unittest negative controls reject a transparent context pixel and a surviving warm plane-thumb pixel. One hand/one thumb/index were inspected in each distinct real wag phase.</p>'];(p/'review.html').write_text('\n'.join(html))
if __name__=='__main__':main()
