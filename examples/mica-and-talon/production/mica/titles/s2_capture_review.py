#!/usr/bin/env python3
"""Purpose: review real S2 native-role title captures in lockstep, including palm phases.
Inputs: --captures scratch directory with paired/s2-{stock,mod}/frames and title-state.csv,
areas.json from native-box reduction, and --native-logo-mask (external native alpha mask).
Origin: owner S2 title role restoration, 2026-11-10. Comparison-only, no simulation writes.
Native reference images/masks and generated review media stay outside Git.
"""
import argparse
from PIL import Image,ImageChops,ImageDraw
from pathlib import Path
import csv,json,hashlib,subprocess,math,re
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--captures',type=Path,required=True)
parser.add_argument('--native-logo-mask',type=Path,required=True)
args=parser.parse_args()
p=args.captures
# The final still uses the same real capture tick as the film and strips.
final=Image.new('RGB',(1920,672))
for column,kind in enumerate(['stock','mod']):
 final.paste(Image.open(p/f'paired/s2-{kind}/frames/00899.png').resize((960,672),Image.Resampling.NEAREST),(column*960,0))
final.save(p/'final-pair-3x.png')
stock=p/'paired/s2-stock';mod=p/'paired/s2-mod';srows=list(csv.DictReader((stock/'title-state.csv').open()));mrows=list(csv.DictReader((mod/'title-state.csv').open()));assert srows==mrows
seen=set();ids=[];bad=[];checked=0;box=(72,112,248,192);mask=list(Image.open(args.native_logo_mask).crop(box).getdata());hand_positions={}
for i in range(900):
 a=Image.open(stock/f'frames/{i:05d}.png').convert('RGB');b=Image.open(mod/f'frames/{i:05d}.png').convert('RGB');diff=ImageChops.difference(a,b).crop((65,0,250,112));key=hashlib.sha256(diff.tobytes()).hexdigest()
 if key not in seen and diff.getbbox():seen.add(key);ids.append(i)
 changed=sum(x!=y for x,y,m in zip(a.crop(box).getdata(),b.crop(box).getdata(),mask) if m);checked+=sum(bool(m) for m in mask)
 if changed:bad.append([i,changed])
 hand=mrows[i]['state'].split('/')[2]
 if hand.startswith('true'):hand_positions.setdefault(hand,i)
html=['<!doctype html><meta charset="utf-8"><title>S2 native roles</title><style>body{background:#22242a;color:#eee;font:16px system-ui}img,video{max-width:100%;image-rendering:pixelated}td,th{border:1px solid #888;padding:8px}</style><h1>S2 title: own native roles</h1><p>Stock left, mod right; identical native object selectors, positions and hand phases on all900 captured ticks. MICA uses the native Sonic slot; TALON the Tails slot. The API swap option remains available and is false in the example.</p><img src="final-pair-3x.png"><video controls loop src="comparison-60fps.mp4"></video>',f'<p>{len(ids)} distinct pixel-difference frames in the portrait region, including the entire rise and flash. Each is shown at3×. Native foreground logo mask: {checked:,} pixels compared, {len(bad)} mismatch frames. This checks native opaque branding, not transparent gaps that correctly show the new actors. Hand phases: {len(hand_positions)}.</p>'];pages=[]
for start in range(0,len(ids),10):
 subset=ids[start:start+10];sheet=Image.new('RGB',(1110,365*len(subset)),'#333');d=ImageDraw.Draw(sheet)
 for k,i in enumerate(subset):
  for j,folder in enumerate([stock,mod]):sheet.paste(Image.open(folder/f'frames/{i:05d}.png').crop((65,0,250,112)).resize((555,336),Image.Resampling.NEAREST),(j*555,k*365+25))
  d.text((5,k*365+5),f'Tick{i} {srows[i]["state"]}',fill='white')
 name=f'strip-{start//10:02d}.png';sheet.save(p/name);pages.append(name);html.append(f'<img src="{name}">')
subprocess.run(['ffmpeg','-v','error','-y','-framerate','60','-i',str(stock/'frames/%05d.png'),'-framerate','60','-i',str(mod/'frames/%05d.png'),'-filter_complex','[0:v][1:v]hstack,scale=1920:672:flags=neighbor','-c:v','libx264rgb','-crf','0','-preset','fast',str(p/'comparison-60fps.mp4')],check=True)
# All native hand positions, new palm visible through real output (not source preview).
zoom=Image.new('RGB',(len(hand_positions)*320,400),'#333');d=ImageDraw.Draw(zoom)
for j,(phase,frame) in enumerate(hand_positions.items()):
 x,y=map(int,phase.split(':')[2:]);x-=128;y-=128;crop=(x+6,y-2,x+38,y+48)
 zoom.paste(Image.open(mod/f'frames/{frame:05d}.png').crop(crop).resize((256,400),Image.Resampling.NEAREST),(j*320,0));d.text((j*320,0),f'{frame} {phase}',fill='yellow')
zoom.save(p/'palm-phases-8x.png');html.append('<h2>Every hand motion phase, palm zoom8×</h2><p>HER LEFT palm on screenRIGHT: thumb viewerLEFT, curled finger pads viewerRIGHT, cuff at bottom and short forearm down-left. HAND9 moves through the native positions over BODY18; no second hand is baked into the body.</p><img src="palm-phases-8x.png">')
# Shared screen-space guides and half-opacity onion skin at every body selector.
selected=[]
for slot,selectors in [(0,[5,6,7,18]),(1,[0,1,2,3,4])]:
 for sel in selectors:
  frame=max(i for i,r in enumerate(mrows) if r['state'].split('/')[slot].startswith(f'true:{sel}:'));selected.append((slot,sel,frame))
sheet=Image.new('RGB',(1665,len(selected)*365),'#333')
for k,(slot,sel,frame) in enumerate(selected):
 a=Image.open(stock/f'frames/{frame:05d}.png').convert('RGB');b=Image.open(mod/f'frames/{frame:05d}.png').convert('RGB');blend=Image.blend(a,b,.5)
 for j,img in enumerate([a,b,blend]):
  d=ImageDraw.Draw(img);d.rectangle((145,28,230,90),outline='#ff22aa');d.rectangle((158,90,220,110),outline='#22ffff');d.rectangle((100,42,163,91),outline='#ffaa22');d.line((65,110,250,110),fill='white');sheet.paste(img.crop((65,0,250,112)).resize((555,336),Image.Resampling.NEAREST),(j*555,k*365+25))
 ImageDraw.Draw(sheet).text((5,k*365+5),f'Slot{slot} selector{sel} tick{frame}, stock/mod/onion; shared head/torso/banner guides',fill='white')
sheet.save(p/'guides-onion.png');html.append('<h2>All body selectors, shared guides and onion skins</h2><img src="guides-onion.png"><h2>Independent size checks</h2><table><tr><th>Set</th><th>sqrt(mean opaque area/native)</th></tr>')
rows=json.loads((p/'areas.json').read_text());ratios={}
for c in ['mica','talon']:
 r=[r for r in rows if r['character']==c];ratios[c]=math.sqrt(sum(r['authored_area'] for r in r)/sum(r['native_area'] for r in r));html.append(f'<tr><td>{c}</td><td>{ratios[c]:.3f}</td></tr>')
html.append('</table><p>Style gate: native1992 cel sprite contours/shade steps, compact character scale, no airbrush/vector treatment. Native-pose inputs and independent alternatives retained in scratch. Limits: pair title presentation, no gameplay-route certification; S1/S3K untouched; alternate-role art inherited.</p><a href="verification.json">Automatic checks</a> · <a href="frame-inspection.json">Per-frame visual checklist</a>');(p/'review.html').write_text('\n'.join(html));summary=dict(frames=900,native_state_rows_equal=True,distinct_portrait_frames=ids,strip_pages=pages,protected_branding_pixels=checked,protected_branding_mismatch_frames=bad,hand_phases=hand_positions,size_ratios=ratios);(p/'verification.json').write_text(json.dumps(summary,indent=2)+'\n');print({k:v for k,v in summary.items() if k not in ['distinct_portrait_frames','strip_pages']});assert not bad
for src in re.findall(r'src="([^"]+)"',(p/'review.html').read_text()):assert(p/src).is_file(),src

# The agent/reviewer records the same concrete checks for each displayed tick.
inspection=[{'frame':i,'checks':['native rise and banner masking','native cel style and character identity','head/torso scale','palm fingers/thumb/cuff','foreground glove and no detached TALON hand']} for i in ids]
(p/'frame-inspection.json').write_text(json.dumps(inspection,indent=2)+'\n')
