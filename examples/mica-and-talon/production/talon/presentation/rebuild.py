#!/usr/bin/env python3
"""Purpose: rebuild/verify reduced original TALON presentation PNGs without ROM art.
Inputs: native-pose.json (original indexed rows, palettes and native-sized boxes).
Origin: TALON presentation art lane, 2026-11-10; built-in GPT Image native-pose transformation.
"""
from pathlib import Path
import argparse,json,shutil
from PIL import Image
HERE=Path(__file__).resolve().parent

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    source=json.loads((Path(__file__).resolve().parent / 'native-pose.json').read_text());records=source['images']
    for f in records:
        im=Image.new('P',tuple(f['dimensions']));im.putpalette(sum(source['palettes'][f['palette']],[])+[0]*720)
        im.putdata([int(c,16) for row in f['rows'] for c in row]);im.info['transparency']=0
        path=HERE.parent/f['file']
        if args.check:
            actual=Image.open(path)
            assert actual.mode=='P' and actual.size==im.size and actual.info['transparency']==0,path
            assert actual.getpalette()[:48]==im.getpalette()[:48] and actual.tobytes()==im.tobytes(),path
        elif 'alias' in f:
            source_path=HERE.parent/f['alias'];source_image=Image.open(source_path)
            assert source_image.size==im.size and source_image.tobytes()==im.tobytes(),source_path
            shutil.copy2(source_path,path)
        else:im.save(path,bits=4,transparency=0)
    print(f'PASS: {len(records)} original indexed PNGs; dimensions, palettes, pixels and binary transparency')
if __name__=='__main__':main()
