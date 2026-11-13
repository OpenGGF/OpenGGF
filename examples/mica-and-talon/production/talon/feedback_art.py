#!/usr/bin/env python3
"""Rebuild/check selected feedback art, without source generations or native pixels.
Purpose: preserve exact approved indexed selections and hotspots for the feedback lane.
Inputs: production/{mica/powered,talon}/{feedback,powered-concepts}-generation.json selected rows/palettes.
Origin: MICA/TALON owner-feedback round, 2026-11-11. Pillow; no ROM or Java needed.
Run from the repository root; --check compares without writing.
"""
import argparse,json
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parents[4]
ART=ROOT/'examples/mica-and-talon/art'
PROD=ROOT/'examples/mica-and-talon/production'
def main():
 ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('--check',action='store_true');a=ap.parse_args();count=0
 for record in [PROD/'mica/powered/feedback-generation.json',PROD/'talon/feedback-generation.json',
                PROD/'mica/powered/powered-concepts-generation.json',PROD/'talon/powered-concepts-generation.json']:
  data=json.loads(record.read_text())
  for f in data['frames']:
   rows=f['selected_pixels']['rows'];im=Image.new('P',(len(rows[0]),len(rows)));im.putpalette(sum(f['palette'],[])+[0]*(768-48));im.putdata([int(v,16)for row in rows for v in row]);path=ART/f['file']
   if a.check:
    with Image.open(path)as actual:
     if actual.mode!='P' or actual.size!=im.size or list(actual.get_flattened_data())!=list(im.get_flattened_data()) or actual.getpalette()[:48]!=im.getpalette()[:48]:raise SystemExit('Stale selected pixels: '+str(path))
   else:path.parent.mkdir(parents=True,exist_ok=True);im.save(path,transparency=0,bits=4)
   count+=1
 print(f'{count} selected indexed frames '+('match' if a.check else 'rebuilt'))
if __name__=='__main__':main()
