#!/usr/bin/env python3
"""Presentation-only SSZ arena prototype (2026-09-24 arena-static task).
Inputs: verified 800x224/60fps gameplay MP4s; outputs: comparison page and movies.
Requires NumPy and ffmpeg. No ROM loading, gameplay mutation, or engine integration.
Noise uses independent deterministic fields; no spatial scrolling or tape band.
"""
import argparse
import hashlib
import json
import shutil
import subprocess
from pathlib import Path
import numpy as np

WIDTH, HEIGHT, FPS = 800, 224, 60
LEFT, RIGHT = 240, 560  # centred original 320px camera rectangle, demo only


def smooth(a, b, value):
    t = np.clip((value-a)/(b-a), 0, 1)
    return t*t*(3-2*t)


def compose(rgb, frame):
    seconds = frame/FPS
    envelope = smooth(.35, 1.1, seconds)  # hold through hits; no unlock occurs in these clips
    y, x = np.mgrid[:HEIGHT, :WIDTH]
    outside = np.maximum(LEFT-x, x-(RIGHT-1))
    opacity = smooth(0, 12, outside)*envelope
    # The first prototype translated y by tick*13, so the same noise field
    # scrolled upward. Independent seeded fields retain reproducibility without
    # moving texture coordinates or consuming the engine's gameplay RNG.
    tick = frame//5  # 12Hz refresh
    grain = np.random.default_rng(np.random.SeedSequence([20260924, tick])).random((HEIGHT, WIDTH))
    luma = 13+grain*35
    noise = luma[..., None]*np.array([.94, .98, 1.06])
    result = np.clip(rgb*(1-opacity[..., None])+noise*opacity[..., None], 0, 255).astype(np.uint8)
    # The prototype must never touch any active arena or HUD pixel.
    assert np.array_equal(result[:, LEFT:RIGHT], rgb[:, LEFT:RIGHT])
    return result


def render(source, destination):
    decoder = subprocess.Popen(['ffmpeg','-v','error','-i',str(source),'-vf',
        'scale=800:224:flags=neighbor,fps=60','-f','rawvideo','-pix_fmt','rgb24','-'], stdout=subprocess.PIPE)
    encoder = subprocess.Popen(['ffmpeg','-v','error','-y','-f','rawvideo','-pix_fmt','rgb24',
        '-s','800x224','-r','60','-i','-','-vf','scale=2400:672:flags=neighbor',
        '-an','-c:v','libx264','-preset','fast','-crf','16','-pix_fmt','yuv420p',
        '-movflags','+faststart',str(destination)], stdin=subprocess.PIPE)
    frame = 0
    try:
        while True:
            data = decoder.stdout.read(WIDTH*HEIGHT*3)
            if not data: break
            if len(data) != WIDTH*HEIGHT*3: raise ValueError('Partial decoded frame')
            rgb = np.frombuffer(data,dtype=np.uint8).reshape(HEIGHT,WIDTH,3)
            output = compose(rgb,frame)
            encoder.stdin.write(output.tobytes())
            if frame == 150:
                subprocess.run(['ffmpeg','-v','error','-y','-f','rawvideo','-pix_fmt','rgb24',
                    '-s','800x224','-i','-','-frames:v','1',str(destination.with_suffix('.png'))],
                    input=output.tobytes(),check=True)
            frame += 1
    finally:
        decoder.stdout.close(); encoder.stdin.close()
    if decoder.wait() or encoder.wait(): raise RuntimeError('ffmpeg failed')
    if frame != 300: raise ValueError(f'Expected verified five-second source, got {frame} frames')
    subprocess.run(['ffmpeg','-v','error','-i',str(destination),'-f','null','-'],check=True)
    return frame


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ghz',type=Path,required=True)
    parser.add_argument('--mtz',type=Path,required=True)
    parser.add_argument('--out',type=Path,required=True)
    args=parser.parse_args(); args.out.mkdir(parents=True,exist_ok=True)
    manifest={'prototype':True,'arena':[LEFT,RIGHT],'nativeWidth':320,
              'viewportWidth':WIDTH,'fps':FPS,'sources':{},'checks':[]}
    for name,source in [('ghz',args.ghz),('mtz',args.mtz)]:
        source=source.resolve()
        if not source.is_file(): raise FileNotFoundError(source)
        shutil.copyfile(source,args.out/f'{name}-original.mp4')
        frames=render(source,args.out/f'{name}-static.mp4')
        manifest['sources'][name]={'path':str(source),'sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'frames':frames}
    # Entry, sustained mask and active viewport invariants independent of video content.
    sample=np.full((HEIGHT,WIDTH,3),173,dtype=np.uint8)
    assert np.array_equal(compose(sample,0),sample)
    assert not np.array_equal(compose(sample,299)[:,:LEFT-12],sample[:,:LEFT-12])
    active=compose(sample,150)
    assert np.array_equal(active[:,LEFT:RIGHT],sample[:,LEFT:RIGHT])
    assert not np.array_equal(active[:,:LEFT-12],sample[:,:LEFT-12])
    # Detect the original 13px upward translation as well as stationary grain.
    next_tick=compose(sample,155)
    for shift in (0, 13):
        old=active[shift:HEIGHT, :LEFT-12, 0].astype(float).ravel()
        new=next_tick[:HEIGHT-shift, :LEFT-12, 0].astype(float).ravel()
        assert abs(np.corrcoef(old,new)[0,1]) < .03
    assert np.array_equal(active,compose(sample,150))
    manifest['checks']=['600 composited frames preserve the exact active rectangle before encoding',
        'clear entry and mask held through final frame','both final movies fully decoded','opaque outer wings at full envelope',
        'successive noise fields decorrelated at zero and 13px vertical offset', 'deterministic noise replay']
    (args.out/'provenance.json').write_text(json.dumps(manifest,indent=2)+'\n')
    shutil.copyfile(Path(__file__).with_suffix('.html'),args.out/'index.html')
    print(args.out/'index.html')

if __name__=='__main__': main()
