#!/usr/bin/env python3
"""Purpose: compare heavy roll-in to stock/integration and gate approved later pixels.
Inputs:900 presentation captures in s3k-stock/baseline/mod, observed title-state.csv,
raw native title exports. Origin: owner heavy-blur review, 2026-11-10.
Writes review media only to scratch; never changes gameplay from comparison data.
"""
import argparse,csv,json,math,hashlib,subprocess
from pathlib import Path
from PIL import Image

def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--scratch',type=Path,required=True);parser.add_argument('--native',type=Path,required=True);args=parser.parse_args();p=args.scratch;n=args.native
 rows=list(csv.DictReader((p/'captures/s3k-mod/title-state.csv').open()));allowed={int(r['frame']) for r in rows if 2<=int(r['plane'])<=10};after=[int(r['frame']) for r in rows if int(r['plane'])>=11];ring=[int(r['frame']) for r in rows if int(r['plane'])==13];fail=[];changed=[]
 for row in rows:
  j=int(row['frame']);a=(p/f'captures/s3k-baseline/frames/{j:05}.png').read_bytes();b=(p/f'captures/s3k-mod/frames/{j:05}.png').read_bytes()
  if a!=b:
   changed.append(j)
   if j not in allowed:fail.append(j)
 logo=Image.open(n/'s3k-plane-6.png').convert('RGBA');glyph_fail=[];blue_fail=[];glyph_checks=0
 for row in rows:
  if int(row['plane'])!=7:continue
  j=int(row['frame']);stock=Image.open(p/f'captures/s3k-stock/frames/{j:05}.png').convert('RGB');mod=Image.open(p/f'captures/s3k-mod/frames/{j:05}.png').convert('RGB')
  for y in range(224):
   for x in range(320):
    if y>=80 and logo.getpixel((x,y))[3]:
     glyph_checks+=1
     if stock.getpixel((x,y))!=mod.getpixel((x,y)):glyph_fail.append((j,x,y))
    elif 140<=x<260 and y<140:
     rr,gg,bb=mod.getpixel((x,y))
     if bb>max(rr,gg)+20:blue_fail.append((j,x,y))
 result=dict(baseline='5278d637cd',frames=len(rows),allowed_selectors=list(range(2,11)),changed_frames=changed,outside_rollin_byte_mismatches=fail,settling_and_later_range=[min(after),max(after)],settling_and_later_count=len(after),ring_and_later_range=[min(ring),max(ring)],ring_and_later_count=len(ring),SEGA_checks=glyph_checks,SEGA_failures=glyph_fail,residual_blue_failures=blue_fail)
 (p/'preservation.json').write_text(json.dumps(result,indent=2)+'\n');assert not fail and not glyph_fail and not blue_fail,result
 # Requested comparison: stock, integration baseline, selected candidate.
 for scale in [1,3]:
  out=Image.new('RGB',(1280*scale,672*scale))
  for col,j in enumerate([254,267,279,289]):
   for row,kind in enumerate(['stock','baseline','mod']):out.paste(Image.open(p/f'captures/s3k-{kind}/frames/{j:05}.png').resize((320*scale,224*scale),Image.Resampling.NEAREST),(col*320*scale,row*224*scale))
  out.save(p/f'3way-{scale}x.png')
 measure=json.loads((p/'measurements.json').read_text());selection=json.loads((p/'selection.json').read_text());sizes=[]
 for f in range(2,11):
  native=Image.open(n/f's3k-mascot-{f}.png').convert('RGBA')
  if f==7:native=Image.open(p/'native7-complete.png').convert('RGBA')
  chosen=Image.open(p/selection[str(f)]/f'{f}.png').convert('RGBA');na=sum(v[3]>0 for v in native.getdata());aa=sum(v[3]>0 for v in chosen.getdata());sizes.append(dict(selector=f,native=na,authored=aa,ratio=math.sqrt(aa/na),method=selection[str(f)]))
 size=dict(frames=sizes,total_ratio=math.sqrt(sum(r['authored'] for r in sizes)/sum(r['native'] for r in sizes)),limit='Raw visible silhouette area includes ghost footprints. Approved sharp source anatomy stays fixed; large ratios reflect shutter coverage, not rescaling to a bounding box.')
 (p/'size-check.json').write_text(json.dumps(size,indent=2)+'\n')
 html=['<!doctype html><meta charset="utf-8"><style>body{font:16px system-ui;background:#181818;color:#eee}img{image-rendering:pixelated;max-width:100%}video{max-width:100%}td,th{border:1px solid #777;padding:7px}pre{white-space:pre-wrap}</style><h1>S3K heavy motion-blur retry</h1><p>Rows: stock, integration5278 baseline, new. Columns:254,267,279,289. These are real production capture PNGs, not asset review renders. The three close-ups are engine selectors8/9/10. Settling11/12 and ring13 remain untouched.</p><img src="3way-1x.png"><img src="3way-3x.png"><video controls loop src="comparison-60hz.mp4"></video><h2>Native measurements</h2><p>Visible copy/landmark estimates and dither-density patch measurements do not uniquely recover a3D shutter kernel. Sharp-area ranges are visual estimates; raster edge/detail metrics are separate diagnostics.</p><table><tr><th>Selector<th>Smear vector px<th>Length<th>Visible copies<th>Spacing px<th>Estimated sharp fraction<th>Sampled light occupancy<th>Choice']
 for row in measure['planes']:
  f=row['selector'];v=row['visible'];bands=row.get('native_corresponding_material_bands',[]);html.append(f"<tr><td>{f}<td>{v['vector']}<td>{row['smear_length_px']:.1f}<td>{v['copies']}<td>{v['spacing']}<td>{v['sharp_estimate']}<td>{[b['half_primary_bright_density'] for b in bands]}<td>{selection[str(f)]}")
 html+=['</table><p>Vectors vary spatially on the close-ups: brow/hand/boot landmarks define the table, while zoom/turn shutter samples transfer those displacements to MICA’s approved features. Do not interpret one vector as a uniform convolution over the entire3D subject.</p>']
 for f in range(2,11):
  html +=[f'<h2>Selector{f}: unlabelled native / GPT / deterministic at1× and3×</h2><img src="candidates-{f}-1x.png"><img src="candidates-{f}-3x.png"><img src="band-samples-{f}.png">']
 html +=['<h2>Preservation checks</h2><pre>'+json.dumps(result,indent=2)+'</pre><h2>Size checks</h2><pre>'+json.dumps(size,indent=2)+'</pre><p>Limits: 2D shutter approximates native3D pose-dependent blur. Existing15-opaque-colour private palette adds visible ordered dithering. No host code, timing, API or palette changes. The film is360 frames at60Hz; these checks do not certify hardware timing parity.</p>'];(p/'review.html').write_text('\n'.join(html))
 subprocess.run(['ffmpeg','-y','-loglevel','error','-framerate','60','-i',str(p/'captures/s3k-stock/frames/%05d.png'),'-framerate','60','-i',str(p/'captures/s3k-baseline/frames/%05d.png'),'-framerate','60','-i',str(p/'captures/s3k-mod/frames/%05d.png'),'-filter_complex','hstack=inputs=3','-frames:v','360','-c:v','libx264rgb','-crf','0','-preset','fast',str(p/'comparison-60hz.mp4')],check=True)
 print(json.dumps(result,indent=2))
if __name__=='__main__':main()
