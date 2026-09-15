#!/usr/bin/env python3
"""Measure paired FBZ boundary fixture evidence; never issues checkpoint PASS.

Origin: 2026-09-14 FBZ completion. Inputs: native fixture directory, engine
fixture directory, new external JSON output. Compare actual final framebuffers,
CRAM/uploaded palette and retained Plane-B descriptors. Report the fixed gameplay region
explicitly; never shift pixels or choose a frame by its image similarity.
"""
import argparse
import hashlib
import json
from pathlib import Path
from PIL import Image


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest().upper()


def descriptor_words(data):
    if len(data) != 8192:
        raise ValueError('Expected 64x32 RGBA descriptor ring')
    result = []
    for i in range(0, len(data), 4):
        lo, flags, _, alpha = data[i:i+4]
        if alpha < 128:
            raise ValueError('Invalid retained descriptor')
        result.append(lo | ((flags & 7) << 8) | ((flags & 24) << 10)
                      | ((flags & 96) << 6) | ((flags & 128) << 8))
    return result


def measure(native, engine, name):
    np, ep = native/(name+'.png'), engine/(name+'.png')
    with Image.open(np) as source:
        if source.size != (348,240):
            raise ValueError('Expected verified native border geometry')
        ni = source.convert('RGB').crop((14,8,334,232))
    with Image.open(ep) as source:
        if source.size != (320,224):
            raise ValueError('Expected native-width engine crop')
        ei = source.convert('RGB')
    mismatch = []
    for y in range(224):
        for x in range(320):
            if tuple(round(c/34) for c in ni.getpixel((x,y))) != tuple(round(c*7/255) for c in ei.getpixel((x,y))):
                mismatch.append((x,y))
    # Fixed review region; no actor exclusion or image-dependent mask.
    gameplay = [(x,y) for x,y in mismatch if 64 <= y < 208]
    vram = (native/(name+'.vram')).read_bytes()
    nw = [int.from_bytes(vram[i:i+2],'big') for i in range(0xe000,0xf000,2)]
    ring = engine/(name+'-background-ring.rgba')
    ew = descriptor_words(ring.read_bytes())
    bad_cells = [i for i,(a,b) in enumerate(zip(nw,ew)) if a!=b]
    cram=(native/(name+'.cram')).read_bytes()
    nrgb=[((int.from_bytes(cram[i:i+2],'big')>>1)&7,
           (int.from_bytes(cram[i:i+2],'big')>>5)&7,
           (int.from_bytes(cram[i:i+2],'big')>>9)&7) for i in range(0,128,2)]
    palette=(engine/(name+'-palette.rgba')).read_bytes()
    ergb=[tuple(round(c*7/255) for c in palette[i:i+3]) for i in range(0,256,4)]
    state=json.loads((engine/(name+'.json')).read_text())
    return {'sample':name,'native_png_sha256':sha(np),'engine_png_sha256':sha(ep),
            'native_vram_sha256':sha(native/(name+'.vram')),'engine_ring_sha256':sha(ring),
            'whole_frame_mismatches':len(mismatch),'gameplay_region':[0,64,320,144],
            'gameplay_region_mismatches':len(gameplay),'actor_exclusions':[],
            'palette_mismatched_indices':[i for i in range(64) if nrgb[i]!=ergb[i]],
            'plane_b_mismatched_cells':bad_cells,'plane_b_mismatched_rows':sorted({i//64 for i in bad_cells}),
            'engine_state':{k:state[k] for k in ('player_x','player_y','camera_x','camera_y',
              'foreground_vscroll','background_vscroll','level_frame_counter','sidekicks')}}


def sprite_publication(native):
    """Observe $FFF800 CPU preparation versus $F800 VDP publication.

    Only consecutive emulator frames are compared. Duplicate samples (fixture
    writes without execution) and gaps are counted, never treated as a delay.
    Entire $280-byte tables are compared; no player/active-entry selection.
    """
    rows = []
    prior = None
    duplicate_samples = gaps = 0
    for line in (native / 'fixture.jsonl').read_text().splitlines():
        state = json.loads(line)
        if state.get('kind') != 'sample':
            continue
        name = f"{state['phase']}-{state['index']:02d}"
        prepared_path = native / (name + '-prepared-sat.bin')
        vram_path = native / (name + '.vram')
        prepared = prepared_path.read_bytes()
        vram = vram_path.read_bytes()
        if len(prepared) != 0x280 or len(vram) != 0x10000:
            raise ValueError('Expected complete CPU SAT and VRAM readbacks')
        displayed = vram[0xF800:0xFA80]
        frame = state['native_frame']
        row = {'sample': name, 'native_frame': frame,
               'prepared_sha256': sha(prepared_path), 'vram_sha256': sha(vram_path),
               'current_cpu_mismatched_bytes': sum(a != b for a, b in zip(prepared, displayed))}
        if prior is not None:
            delta = frame - prior[0]
            if delta < 0:
                raise ValueError('Native sample frames must not move backwards')
            if delta == 1:
                row['previous_cpu_mismatched_bytes'] = sum(a != b for a, b in zip(prior[1], displayed))
            elif delta == 0:
                duplicate_samples += 1
            else:
                gaps += 1
        rows.append(row)
        prior = frame, prepared
    if not rows:
        raise ValueError('No native sample observations')
    comparisons = [r for r in rows if 'previous_cpu_mismatched_bytes' in r]
    if not comparisons:
        raise ValueError('No consecutive native frames to compare')
    return {'status': 'native-publication-measurement-not-engine-acceptance',
            'table_bytes': 0x280, 'sample_count': len(rows),
            'consecutive_pairs': len(comparisons), 'duplicate_samples': duplicate_samples,
            'sample_gaps': gaps,
            'previous_cpu_mismatch_pairs': sum(r['previous_cpu_mismatched_bytes'] != 0 for r in comparisons),
            'current_cpu_mismatch_samples': sum(r['current_cpu_mismatched_bytes'] != 0 for r in rows),
            'samples': rows}


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('native',type=Path);p.add_argument('engine',type=Path);p.add_argument('output',type=Path)
    p.add_argument('--sprite-publication', action='store_true', help='compare native CPU and VDP SAT boundaries')
    a=p.parse_args()
    result={'status':'measurements-only-independent-review-required',
            'pixel_rule':'native channel/34 versus round(engine channel*7/255), same coordinates; no translation',
            'samples':[measure(a.native,a.engine,name) for name in ('forward-after-01','after-01')]}
    if a.sprite_publication:
        result['native_sprite_publication'] = sprite_publication(a.native)
    with a.output.open('x') as f:json.dump(result,f,indent=2)
    print(json.dumps(result))


if __name__=='__main__':main()
