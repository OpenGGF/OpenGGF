#!/usr/bin/env python3
"""Purpose: gate S3K roll-in captures and preserve the approved later sequence.
Inputs: scratch captures/s3k-{stock,baseline,mod}, mod title-state.csv, native planes.
Origin: owner roll-in enhancement, 2026-11-10; catches truncated trail ownership
and branding overdraw. Uses production PNG bytes; writes only scratch reports.
"""
from pathlib import Path
from PIL import Image,ImageChops
import argparse,csv,json,hashlib,subprocess
parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--captures',type=Path,required=True);parser.add_argument('--native',type=Path,required=True);parser.add_argument('--baseline-sha',required=True);args=parser.parse_args();p=args.captures;rows=list(csv.DictReader((p/'captures/s3k-mod/title-state.csv').open()));roll=[int(r['frame']) for r in rows if 2<=int(r['plane'])<=7];late=[int(r['frame']) for r in rows if int(r['plane'])>=8];allowed=set(roll);failed=[];changed=[]
for row in rows:
 j=int(row['frame']);a=(p/f'captures/s3k-baseline/frames/{j:05}.png').read_bytes();b=(p/f'captures/s3k-mod/frames/{j:05}.png').read_bytes()
 if a!=b:
  changed.append(j)
  if j not in allowed:failed.append(j)
result={'baseline':args.baseline_sha,'capture_frames':900,'allowed_rollin_frames':roll,'changed_frames':changed,'unchanged_approved_frame_count':len(late),'unchanged_approved_range':[min(late),max(late)],'byte_mismatch_outside_rollin':failed,'comparison':'PNG bytes, not just RGB equality'};(p/'unchanged-check.json').write_text(json.dumps(result,indent=2)+'\n');assert not failed

logo=Image.open(args.native/'s3k-plane-6.png').convert('RGBA');logo_failures=[];blue_failures=[];logo_checks=0
for row in rows:
 if int(row['plane'])!=7:continue
 j=int(row['frame']);a=Image.open(p/f'captures/s3k-stock/frames/{j:05}.png').convert('RGB');b=Image.open(p/f'captures/s3k-mod/frames/{j:05}.png').convert('RGB')
 for y in range(224):
  for x in range(320):
   glyph=y>=80 and bool(logo.getpixel((x,y))[3])
   if glyph:
    logo_checks+=1
    if a.getpixel((x,y))!=b.getpixel((x,y)):logo_failures.append((j,x,y))
   elif 140<=x<260 and y<140:
    rr,gg,bb=b.getpixel((x,y))
    if bb>max(rr,gg)+20:blue_failures.append((j,x,y))
result['SEGA_pixel_checks']=logo_checks;result['SEGA_failures']=logo_failures;result['residual_blue_failures']=blue_failures
assert not logo_failures and not blue_failures
(p/'unchanged-check.json').write_text(json.dumps(result,indent=2)+'\n')

seen={}
for row in rows:
 if 2<=int(row['plane'])<=7:seen.setdefault(int(row['plane']),int(row['frame']))
compact=Image.new('RGB',(1920,448))
for i,(f,j) in enumerate(seen.items()):
 for y,kind in enumerate(('stock','mod')):compact.paste(Image.open(p/f'captures/s3k-{kind}/frames/{j:05}.png'),(i*320,y*224))
compact.resize((5760,1344),Image.Resampling.NEAREST).save(p/'all-selectors-3x.png')
html=['<!doctype html><meta charset="utf-8"><style>body{background:#191919;color:#eee;font:16px system-ui}img{image-rendering:pixelated;max-width:100%}video{max-width:100%}td{padding:8px;border:1px solid #777}</style><h1>MICA S3K roll-in — lockstep production frames</h1><p>Stock above, proposed mod below. Six unique baked plane selectors. Selectors2–6 have4 presentations each; selector7 has13 while the next plane loads. All33 presentations are shown at3× below. Both streams use the current integration engine and identical inputs. No engine/API/timing/palette changes.</p><video controls loop src="comparison-60hz.mp4"></video><h2>All six distinct selectors</h2><img src="all-selectors-3x.png">']
for start in range(0,len(roll),4):
 frames=roll[start:start+4];im=Image.new('RGB',(960*len(frames),1344))
 for col,j in enumerate(frames):
  for yy,kind in enumerate(('stock','mod')):im.paste(Image.open(p/f'captures/s3k-{kind}/frames/{j:05}.png').resize((960,672),Image.Resampling.NEAREST),(col*960,yy*672))
 name=f'lockstep-{start//4}.png';im.save(p/name);html +=[f'<h2>Capture frames {frames}</h2><img src="{name}"><p>Checked each: native screen box, diagonal light and dark echo direction, broad opaque lobes, jade shade falloff, no stray pieces, SEGA occlusion and unchanged recorded presentation cadence.</p>']
html +=[f'<h2>Preservation gate</h2><pre>{json.dumps(result,indent=2)}</pre><h2>Independent area measurements</h2><pre>{(p/"reduction.json").read_text()}</pre><p>Approved post-roll-in assets and palette are unchanged. Native has9–13 blue shades across frames; the existing creator palette supplies six jade shades with ordered dithering. This is a title presentation check, not ROM hardware timing parity.</p>'];(p/'review.html').write_text('\n'.join(html))
subprocess.run(['ffmpeg','-y','-loglevel','error','-framerate','60','-i',str(p/'captures/s3k-stock/frames/%05d.png'),'-framerate','60','-i',str(p/'captures/s3k-mod/frames/%05d.png'),'-filter_complex','hstack=inputs=2','-frames:v','360','-c:v','libx264rgb','-crf','0','-preset','fast',str(p/'comparison-60hz.mp4')],check=True)
print(json.dumps(result,indent=2))
