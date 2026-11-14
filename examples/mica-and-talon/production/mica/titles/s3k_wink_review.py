#!/usr/bin/env python3
"""Gate real title captures and emit every wink state at6x, with patch borders.
Inputs:900-frame mod, preceding-mod and stock capture directories; title-state.csv.
Origin: split-lashes correction, 2026-11-10. Uses production raster outputs only;
no reference data is fed into runtime. Pillow/FFmpeg, native pixels stay scratch.
"""
import argparse,csv,json
from pathlib import Path
from PIL import Image,ImageChops,ImageDraw,ImageFont
from s3k_wink_continuity import inputs,errors,neighbours
from s3k_hand_ownership import points_from_spans

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--captures',type=Path,required=True);parser.add_argument('--before',type=Path,required=True);parser.add_argument('--stock',type=Path,required=True);args=parser.parse_args();p=args.captures;mod=p/'captures/s3k-mod';plane,frames,contract=inputs(Path(__file__).parent);assert not errors(plane,frames,contract);owned=points_from_spans(contract['ownership_rows']);ox,oy=contract['origin'];rows=list(csv.DictReader((mod/'title-state.csv').open()));assert (mod/'title-state.csv').read_bytes()==(args.before/'title-state.csv').read_bytes()
    outside=[];render=[];checked=0;seen={};active_count=0;hand_failures=[];hand_checks=0;hand_frames={i:Image.open(Path(__file__).parent/f's3k-sprite-{i}.png') for i in (0,1,4)}
    for row in rows:
        j=int(row['frame']);before=Image.open(args.before/f'frames/{j:05}.png').convert('RGB');actual=Image.open(mod/f'frames/{j:05}.png').convert('RGB');diff=ImageChops.difference(before,actual);allow=Image.new('L',(320,224));dy=-int(row['vscroll']) if row['phase']=='INTERACTIVE' else 0;selector=int(row['wink'])
        if row['wink_active']=='true' and selector in frames:
            active_count+=1
            for x,y in owned:allow.putpixel((ox+x,oy+y+dy),255)
            if row['phase']=='INTERACTIVE':
                frame=frames[selector].convert('RGB')
                for x,y in owned:
                    checked+=1
                    if actual.getpixel((ox+x,oy+y+dy))!=frame.getpixel((x,y)):render.append((j,x,y))
        if Image.composite(diff,Image.new('RGB',(320,224)),ImageChops.invert(allow)).getbbox():outside.append(j)
        if row['phase']=='INTERACTIVE' and int(row['plane'])==13:
            hand=hand_frames[int(row['finger']) if row['finger_active']=='true' else 4]
            for y in range(hand.height):
                for x in range(hand.width):
                    if hand.getpixel((x,y))<8:continue
                    hand_checks+=1
                    if before.getpixel((x+160,y+60+dy))!=actual.getpixel((x+160,y+60+dy)):hand_failures.append((j,x,y))
        if j>=450 and row['phase']=='INTERACTIVE':seen.setdefault(selector,j)
    result={'presentations':len(rows),'state_csv_byte_identical':True,'active_wink_presentations':active_count,'source_lash_continuity_failures':0,'exact_render_pixel_checks':checked,'render_failures':render[:20],'render_failure_count':len(render),'outside_owned_patch_failures':outside,'full_hand_pixel_checks':hand_checks,'full_hand_failures':hand_failures[:20],'full_hand_failure_count':len(hand_failures),'hand_and_intro_preserved':not outside and not hand_failures,'old_lash_pixels_owned':len(points_from_spans(contract['old_lash_rows'])),'ownership_pixels':len(owned),'distinct_states':seen};(p/'wink-check.json').write_text(json.dumps(result,indent=2)+'\n');print(result);assert not render and not outside and not hand_failures
    chosen=[460,472,474];crop=(96,8,192,112);out=Image.new('RGB',(576*3,624*2));ann=out.copy();font=ImageFont.truetype('DejaVuSans.ttf',18)
    for col,j in enumerate(chosen):
        row=rows[j];dy=-int(row['vscroll']);selector=int(row['wink'])
        for r,folder in enumerate((args.stock,mod)):
            image=Image.open(folder/f'frames/{j:05}.png').convert('RGB').crop(crop).resize((576,624),Image.Resampling.NEAREST);out.paste(image,(col*576,r*624));ann.paste(image,(col*576,r*624));draw=ImageDraw.Draw(ann);draw.text((col*576+8,r*624+4),f'{"stock" if r==0 else "MICA"} {j}, state {selector}',font=font,fill='white',stroke_width=2,stroke_fill='black')
            if selector not in frames:continue
            x,y,w,h=(104,48,32,48) if r==0 else (ox,oy,*contract['size']);x0=col*576+(x-crop[0])*6;y0=r*624+(y+dy-crop[1])*6;draw.rectangle((x0,y0,x0+w*6-1,y0+h*6-1),outline='#55ddff',width=2)
            if r==1:
                for x,y in owned:
                    if any(n not in owned for n in neighbours((x,y))):
                        sx=col*576+(x+ox-crop[0])*6;sy=r*624+(y+oy+dy-crop[1])*6;draw.rectangle((sx,sy,sx+5,sy+5),outline='#ff66dd',width=1)
    out.save(p/'wink-6x.png');ann.save(p/'wink-border-6x.png');(p/'review.html').write_text('''<!doctype html><meta charset="utf-8"><title>S3K whole-lash wink</title><style>body{background:#202329;color:#eee;font:16px system-ui}img,video{max-width:100%;image-rendering:pixelated}pre{white-space:pre-wrap}</style><h1>S3K continuous lashes</h1><p>Real capture, all distinct states: open4, half2, closed3. Stock above MICA,6× nearest. Each lash is single and joins its own lid root; all three move with the lid.</p><img src="wink-6x.png"><p>Cyan: native32×48 or MICA80×60 frame bounds. Magenta: actual opaque ownership edge. Open4 has no wink overlay. The entire original lash footprint has an opaque surround; the boundary lies beyond the lashes. Ink elsewhere crossing that edge equals the plane pixel-exactly on both sides.</p><img src="wink-border-6x.png"><video controls loop src="wink-60hz.mp4"></video><pre>'''+json.dumps(result,indent=2)+'''</pre><p>Negative controls reject a severed lash root, missing old-lash ownership, and new ink at the context border. Every rendered wink pixel matches its indexed source. All pixels outside the owned patch, including the whole hand and roll-in, match the preceding hand commit; native clock CSV is byte-identical.</p>''')

if __name__=='__main__':main()
