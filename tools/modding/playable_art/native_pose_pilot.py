#!/usr/bin/env python3
"""Purpose: reduce GPT Image pose edits using the existing shared-palette pipeline;
produce disposable four-column pose reviews and paired animation GIFs.
Inputs: /tmp native_pose.py exports, per-frame GPT PNGs/prompts, current MICA handoff.
Origin: W7 native-pose pilot, 2026-11-08; generated MICA only enters the repo.
Raw images remain review material; final native pixel rows make rebuilds lossless.
"""
import argparse
import hashlib
import json
from pathlib import Path
from PIL import Image, ImageDraw
import importlib.util

ROOT=Path(__file__).resolve().parents[3]
ART=ROOT/'examples/mica-and-talon/art/mica'
PILOT=ART/'native-pose-pilot'
NEAREST=Image.Resampling.NEAREST

def module(name):
    spec=importlib.util.spec_from_file_location(name,Path(__file__).with_name(name+'.py'))
    m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m

def current(key, manifest):
    if key in ('s3k-hud','s2-sign'):
        return Image.open(ART/'presentation'/f'{key}.png').convert('RGBA'), [8,8] if key=='s3k-hud' else [24,16]
    f=next(f for f in manifest['frames'] if f['id']==key)
    return Image.open(ART/f['file']).convert('RGBA'), f['hotspot']

def canvas(im,hot):
    out=Image.new('RGBA',(80,80));out.alpha_composite(im,(40-hot[0],40-hot[1]));return out

def build(review):
    review=review.resolve()
    if not review.is_relative_to(Path('/tmp')):raise ValueError('Review must stay in /tmp')
    refs=json.loads((review/'native/references.json').read_text())
    prompts=json.loads((review/'prompts.json').read_text())
    expected={f'{g}-{i:02}' for g,n in [('wait',6),('walk',8),('run',4),('spring',2),('roll',4)] for i in range(n)} | {'s3k-hud','s2-sign'}
    if {f['id'] for f in refs} != expected: raise ValueError('Pilot requires all 26 native references')
    for key in expected:
        if not (review/'raw'/f'{key}.png').exists() or key not in prompts:
            raise ValueError(f'Missing raw image or exact prompt: {key}')
    pipeline=module('pipeline');colors=json.loads((ART/'palette.yaml').read_text())['colors']
    old=json.loads((ART/'frames.yaml').read_text());PILOT.mkdir(exist_ok=True)
    recipe=dict(palette='../palette.yaml',frames=[],scripts=old['scripts'])
    new={};previous={}
    metadata=[]
    for f in refs:
        key=f['id'];raw=review/'raw'/f'{key}.png'
        clean=pipeline.largest_component(pipeline.remove_background(Image.open(raw)))
        reduced=pipeline.outline(pipeline.indexed(clean.resize(tuple(f['size']),Image.Resampling.LANCZOS),colors))
        # Light cleanup uses the pipeline's connected-alpha and inside-ink passes.
        # Explicit eye edits belong in patches below; never replace a GPT pose.
        patches=json.loads((PILOT/'patches.json').read_text()) if (PILOT/'patches.json').exists() else {}
        for x,y,index in patches.get(key,[]):reduced.putpixel((x,y),index)
        reduced=pipeline.outline(reduced)
        rows=[''.join(format(reduced.getpixel((x,y)),'x') for x in range(reduced.width)) for y in range(reduced.height)]
        recipe['frames'].append(dict(id=key,animation=key.split('-')[0].upper(),order=int(key.split('-')[-1]) if key.split('-')[-1].isdigit() else 0,
          native_pixels=dict(rows=rows,anchor=f['hotspot']),notes='GPT Image native-pose pilot; not runtime integrated'))
        im,hot=current(key,old)
        new[key]=canvas(reduced.convert('RGBA'),f['hotspot']);previous[key]=canvas(im,hot)
        metadata.append(dict(**f,raw_sha256=hashlib.sha256(raw.read_bytes()).hexdigest(),prompt=prompts[key],
          reduction='largest alpha component; Lanczos to native tight bbox; fixed palette; inside outline; patches.json'))
        page=Image.new('RGB',(320,100),'#929292');d=ImageDraw.Draw(page)
        native=Image.open(review/'native'/f'{key}.png').convert('RGBA')
        raw_preview=clean.resize(tuple(f['size']),Image.Resampling.LANCZOS)
        for col,(img,anchor,label) in enumerate([(native,f['hotspot'],'native'),(raw_preview,f['hotspot'],'raw GPT'),(reduced.convert('RGBA'),f['hotspot'],'reduced'),(im,hot,'current')]):
            tile=canvas(img,anchor);page.paste(tile,(col*80,0),tile);d.text((col*80,82),label,fill='black')
        d.text((0,91),key,fill='black')
        for scale in (1,4):page.resize((320*scale,100*scale),NEAREST).save(review/f'{key}-{scale}x.png')
    (PILOT/'recipe.json').write_text(json.dumps(recipe,indent=2)+'\n')
    (PILOT/'provenance.json').write_text(json.dumps(dict(tool='built-in GPT Image (latest exposed; model/version and billing not returned)',
      concept='../../../concept/mica.png',source_pins=json.loads((review/'native/source-pins.json').read_text()),frames=metadata),indent=2)+'\n')
    pipeline.build(PILOT/'recipe.json')
    for scale in (1,4):
        sheet=Image.new('RGB',(320*scale,100*scale*len(refs)))
        for i,f in enumerate(refs):sheet.paste(Image.open(review/f"{f['id']}-{scale}x.png"),(0,i*100*scale))
        sheet.save(review/f'contact-{scale}x.png')
    for group in ('wait','walk','run','spring','roll','s3k','s2'):
        fs=[f for f in refs if f['id'].startswith(group+'-')]
        for scale in (1,4):
            sheet=Image.new('RGB',(320*scale,100*scale*len(fs)))
            for i,f in enumerate(fs):sheet.paste(Image.open(review/f"{f['id']}-{scale}x.png"),(0,i*100*scale))
            sheet.save(review/f'{group}-review-{scale}x.png')
    for group in ('WAIT','WALK','RUN'):
        if group=='WAIT':keys=old['scripts']['WAIT']['frames'];ticks=old['scripts']['WAIT']['durations']
        else:
            keys=[f['id'] for f in refs if f['id'].startswith(group.lower()+'-')]
            ticks=[1]*len(keys) # fastest native speed-driven preview, not a fixed animation period
        pages=[]
        for key in keys:
            p=Image.new('RGB',(160,96),'#929292');d=ImageDraw.Draw(p)
            for x,im,label in ((0,new[key],'pilot'),(80,previous[key],'current')):
                p.paste(im,(x,0),im);d.text((x,82),label,fill='black')
            pages.append(p.resize((640,384),NEAREST))
        # Cumulative centisecond rounding avoids slowing every 1-tick frame to 20ms.
        time=0;durations=[]
        for t in ticks:
            before=round(time*100/60);time+=t;durations.append((round(time*100/60)-before)*10)
        pages[0].save(review/f'{group}-paired.gif',save_all=True,append_images=pages[1:],duration=durations,loop=0,disposal=2)
    print('PASS: 26 pilot reductions and four-column reviews; WAIT authored script; WALK/RUN native fastest tick')

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--review',type=Path,required=True)
    build(p.parse_args().review)
