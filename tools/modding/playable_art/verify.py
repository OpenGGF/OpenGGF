#!/usr/bin/env python3
"""Purpose: verify native frame palette, alpha, geometry and handoff contracts.
Inputs: art root containing frames.yaml, palette.yaml and recipe.json.
Origin: 2026-11-08 original-character mod W7; deliberately avoids Java/ROM builds.
"""
import argparse
import hashlib
import json
from collections import Counter
from pathlib import Path
from PIL import Image
from pipeline import LEVELS, build, remove_background


def verify(root):
    manifest=json.loads((root/'frames.yaml').read_text())
    palette=json.loads((root/'palette.yaml').read_text())['colors']
    expected=sum(palette,[])
    core_path = root/'native-pose-core.json'
    core = json.loads(core_path.read_text())['frames'] if core_path.exists() else {}
    assert all(v in LEVELS for c in palette for v in c)
    ids=set()
    counts=Counter()
    for f in manifest['frames']:
        assert f['id'] not in ids, f['id']
        ids.add(f['id'])
        im=Image.open(root/f['file'])
        assert im.mode=='P' and im.info['transparency']==0, f['id']
        assert im.getpalette()[:48]==expected, f['id']
        data=tuple(im.get_flattened_data() if hasattr(im,'get_flattened_data') else im.getdata())
        assert max(data)<16 and any(data), f['id']
        assert set(im.convert('RGBA').getchannel('A').get_flattened_data() if hasattr(im,'get_flattened_data') else im.convert('RGBA').getchannel('A').getdata()) <= {0,255}
        # Outline must be ink at every exposed native silhouette edge.
        for y in range(im.height):
            for x in range(im.width):
                if im.getpixel((x,y)) and any(not (0<=a<im.width and 0<=b<im.height) or im.getpixel((a,b))==0 for a,b in ((x-1,y),(x+1,y),(x,y-1),(x,y+1))):
                    assert im.getpixel((x,y))==1,(f['id'],x,y)
        provenance = core.get(f['id'], {})
        if 'alias' in provenance:
            provenance = core[provenance['alias']]
        if 'source_asset' in provenance:
            import hashlib
            assert hashlib.sha256((root/provenance['source_asset']).read_bytes()).hexdigest()==provenance['source_sha256'], f['id']
        if 'native_size' in provenance:
            geometry=provenance.get('geometry_reference',provenance['reference'])
            assert list(im.size)==geometry['size'], f['id']
            assert f['hotspot']==geometry['hotspot'], f['id']
        elif f['animation'] in ('WAIT','WALK') and f['angle_set']=='flat':
            assert (im.height in ((39,40) if f['animation']=='WAIT' else (39,))
                    and im.height-f['hotspot'][1]==19),f['id']
        if f['animation']=='ROLL' and 'native_size' not in provenance:
            assert im.size==(29,29) and f['hotspot']==[14,14],f['id']
        counts[f['animation']]+=1
    assert counts['WALK']==32 and counts['RUN']==16
    required={'WAIT','WALK','RUN','ROLL','SPINDASH','PUSH','DUCK','LOOK_UP','SKID','STOP','SPRING','BALANCE','HURT','DEATH','DROWN','GET_AIR','HANG','HOMING_DIVE'}
    assert required==set(counts)-{'NATIVE_POSE'}
    # Flood-fill removal must not erase an enclosed eye-white pixel of same colour.
    test=Image.new('RGBA',(5,5),'white')
    for x,y in ((1,1),(2,1),(3,1),(1,2),(3,2),(1,3),(2,3),(3,3)):
        test.putpixel((x,y),(0,0,0,255))
    keyed=remove_background(test,[255,255,255])
    assert keyed.getpixel((0,0))[3]==0 and keyed.getpixel((2,2))[3]==255
    def hashes():
        return {p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in (root/'frames').glob('*.png')}
    before=hashes()
    build(root/'recipe.json')
    assert before==hashes(),'Recipe did not reproduce committed frames byte-for-byte'
    assert len(before)==len(ids),'Stale frame files'
    print('PASS: palette, binary alpha, ink outline, geometry, core coverage, key removal, deterministic rebuild')
    print(dict(sorted(counts.items())))


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('root',type=Path)
    verify(p.parse_args().root.resolve())
