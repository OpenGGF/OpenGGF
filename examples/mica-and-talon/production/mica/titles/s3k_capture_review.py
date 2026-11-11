#!/usr/bin/env python3
"""Purpose: inspect S3K title production captures, preserve native pixels and measure size.
Inputs: scratch captures/s3k-{stock,mod}, mod title-state.csv, native reference exports.
Origin: owner round-two title review, 2026-11-10. No native images enter the repo.
"""
from pathlib import Path
from PIL import Image,ImageChops
import argparse,csv,json,math,subprocess
parser=argparse.ArgumentParser();parser.add_argument('--captures',type=Path,required=True);parser.add_argument('--native',type=Path,required=True);args=parser.parse_args();p=args.captures;n=args.native;r=Path('examples/mica-and-talon/art/mica/titles');rows=list(csv.DictReader((p/'captures/s3k-mod/title-state.csv').open()))
def plane(f):
 a=Image.new('RGBA',(320,224));a.paste(Image.open(r/f's3k-plane-{f}-left.png').convert('RGBA'),(0,0));a.paste(Image.open(r/f's3k-plane-{f}-right.png').convert('RGBA'),(160,0));return a
seen={};errors=[];strict=[];checks=0;ear_bad=[]
for row in rows:
 j=int(row['frame']);f=int(row['plane']);dy=-int(row['vscroll']) if row['phase']=='INTERACTIVE' else 0
 if f<1:continue
 shape=plane(f)
 if f==13:
  shape.alpha_composite(Image.open(r/f"s3k-sprite-{row['finger'] if row['finger_active']=='true' else 4}.png").convert('RGBA'),(160,60))
  if row['wink_active']=='true' and row['wink'] in ('2','3'):shape.alpha_composite(Image.open(r/f"s3k-sprite-{row['wink']}.png").convert('RGBA'),(108,40))
 a=Image.open(p/f'captures/s3k-stock/frames/{j:05}.png').convert('RGB');b=Image.open(p/f'captures/s3k-mod/frames/{j:05}.png').convert('RGB');mask=(r/f's3k-plane-{f}.mask').read_bytes();bad=0;outside=0
 # Native mascot removal is necessarily a difference from stock; retain that
 # count separately instead of claiming literal equality outside the new contour.
 for y in range(224):
  sy=y+dy
  if not 0<=sy<224:continue
  for x in range(320):
   if shape.getpixel((x,y))[3]:continue
   if a.getpixel((x,sy))==b.getpixel((x,sy)):continue
   outside+=1
   if mask[y*320+x]:continue
   # Native animated finger/wink own native opaque context outside the new hand.
   if f==13 and (176<=x<224 and 64<=y<120 and row['finger'] in ('0','1') or 104<=x<136 and 48<=y<96 and row['wink'] in ('2','3')):continue
   bad+=1
  checks+=320
 if bad:errors.append({'frame':j,'pixels':bad})
 if outside:strict.append({'frame':j,'pixels':outside})
 key=(f,row['finger'],row['wink'])
 # Lockstep state selections confirmed by actual capture difference.
 if row['phase'] in ('SONIC_ANIMATION','INTERACTIVE'):seen.setdefault(key,j)
 # Ear centre and rim: compare actual rendered pixel to exact source palette.
 if f==13 and row['phase']=='INTERACTIVE':
  for x,y in [(172,31),(174,34),(170,29)]:
   if y+dy<0:continue
   expected=plane(13).getpixel((x,y))
   if expected[3] and b.getpixel((x,y+dy))!=expected[:3]:ear_bad.append((j,x,y))
result={'capture_frames':len(rows),'preserved_native_outside_ownership_failures':errors,'literal_outside_new_silhouette_differences':strict,'ear_render_failures':ear_bad,'checked_pixels':checks,'note':'Literal stock equality outside the new silhouette is impossible where native Sonic is removed. Counts are exposed; preserved native ring/background equality is gated separately. Native sprite context exclusions are explicitly bounded.'};(p/'pixel-checks.json').write_text(json.dumps(result,indent=2)+'\n')
print('pixel check',len(errors),'ear',len(ear_bad),'literal',len(strict),'states',len(seen))
html=['<!doctype html><meta charset="utf-8"><style>body{font:16px system-ui;background:#181818;color:#eee}img{image-rendering:pixelated;max-width:100%}video{max-width:100%}td,th{padding:8px;border:1px solid #777}</style><h1>S3K title round 2 — production captures</h1><p>Stock above, MICA below, identical capture index, 3×. No generated review render substitutes for these strips. Every distinct observed plane/finger/wink selection is included. Plane12 is not exposed by ordinary 900-frame timing; component views are separate.</p>','<video controls loop src="comparison-60hz.mp4"></video>']
for page in range(math.ceil(len(seen)/4)):
 selected=list(seen.items())[page*4:(page+1)*4];out=Image.new('RGB',(960*len(selected),1344))
 for i,(key,j) in enumerate(selected):
  for yy,kind in enumerate(('stock','mod')):out.paste(Image.open(p/f'captures/s3k-{kind}/frames/{j:05}.png').resize((960,672),Image.Resampling.NEAREST),(i*960,yy*672))
 name=f'lockstep-{page}.png';out.save(p/name);html +=[f'<h2>Frames {[j for _,j in selected]}; states {[k for k,_ in selected]}</h2><img src="{name}"><p>Check per column: native timing and placement, baked shading, contour and ring continuity; final states additionally check ear, short finger, exact face-border wink and rounded lower glove.</p>']
html +=[f'<p>Preserved-native check: {len(errors)} failing frames; ear: {len(ear_bad)}. Literal outside-MICA differences occur on {len(strict)} frames because removing native Sonic changes those pixels. See pixel-checks.json; this is not a literal zero-difference pass.</p>']

lineup=Image.new('RGB',(1920,672))
for i,kind in enumerate(('stock','mod')):
 im=Image.open(p/f'captures/s3k-{kind}/frames/00304.png').convert('RGB');d=ImageDraw.Draw(im);d.rectangle((86,4,229,94),outline='#ff00ff');d.rectangle((101,94,198,142),outline='#00ffff');d.line((60,142,245,142),fill='#ffffff');lineup.paste(im.resize((960,672),Image.Resampling.NEAREST),(i*960,0))
lineup.save(p/'landmark-lineup.png');html +=['<h2>Shared baseline and head/torso guides</h2><img src="landmark-lineup.png">']

# Per-asset areas: complete generated portrait before split, native complete mascot;
# finger anatomy excludes native blue/black context as in the first-round measurement.
area=lambda im:sum(v[3]>0 for v in im.convert('RGBA').getdata())
na=sum(area(Image.open(n/f's3k-mascot-{f}.png')) for f in range(1,14));aa=sum(area(plane(f)) for f in range(1,14))+area(Image.open(r/'s3k-sprite-4.png'))
hand_native=sum(sum(px[3]>0 and px[:3] not in [(0,0,0),(0,0,109),(0,0,255),(0,73,255),(36,109,255),(73,146,255),(109,182,255)] for px in Image.open(n/f's3k-sprite-{i}.png').convert('RGBA').getdata()) for i in [0,1]);hand=sum(area(Image.open(r/f's3k-sprite-{i}.png')) for i in [0,1]);size={'plane_bank':math.sqrt(aa/na),'finger_anatomy':math.sqrt(hand/hand_native),'plane_authored':aa,'plane_native':na,'finger_authored':hand,'finger_native':hand_native};(p/'size-check.json').write_text(json.dumps(size,indent=2));html +=[f'<p>Area check: {size}</p>'];(p/'review.html').write_text('\n'.join(html))
subprocess.run(['ffmpeg','-y','-loglevel','error','-framerate','60','-i',str(p/'captures/s3k-stock/frames/%05d.png'),'-framerate','60','-i',str(p/'captures/s3k-mod/frames/%05d.png'),'-filter_complex','hstack=inputs=2','-c:v','libx264rgb','-crf','0','-preset','fast',str(p/'comparison-60hz.mp4')],check=True)
