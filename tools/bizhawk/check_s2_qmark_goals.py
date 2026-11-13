#!/usr/bin/env python3
"""Purpose: bind eight monitor outcome witnesses to a cold, read-only GPGX movie.
Inputs: capture_every_frame.lua output and frozen planner BK2; no input authoring.
Origin: S2 competition question-mark visual routes, 2026-11-09.
"""
import argparse
import csv
import hashlib
import json
from pathlib import Path
import zipfile

ROM = '8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9'

def rows(path):
    with path.open() as source:
        return [{k: int(v) for k, v in row.items()} for row in csv.DictReader(source)]

def analyse(frames, observations, content):
    if len(frames) != len(observations) or any(a['frame'] != i+1 or b['frame'] != i+1
            for i, (a,b) in enumerate(zip(frames, observations))):
        raise ValueError('cold contiguous paired observations required')
    icon = next(r for r in observations if r['content'])
    broken = next(r for r in observations if r['shell_routine'] == 6)
    lag = next((r['frame'] for r in frames if r['mode'] == 12 and r['lfc'] > 0 and r['lag']), None)
    init = icon['frame']
    if icon['content'] != content or content != (icon['lfc'] & 7)+1:
        raise ValueError('icon is not the requested native counter-phase outcome')
    if (icon['parent'] != 0xb000 or icon['icon_x'] != 960 or icon['items'] != 0
            or icon['p1_timer'] != 1 or icon['p2_timer'] != 1
            or frames[init-1]['mode'] != 12 or frames[init-1]['two_player'] != 1
            or broken['frame'] >= init or lag is not None and lag <= init):
        raise ValueError('default-options racing P1 break before native lag required')
    owner = 'p2' if content == 2 else 'p1'
    def awarded(r):
        if r[owner+'_monitors'] != 1: return False
        if content in (1,2): return r[owner+'_lives'] == icon[owner+'_lives']+1
        if content == 3: return r['p1_routine'] == 4 and r['p1_rings'] == 0 and icon['p1_rings'] > 0
        if content == 4: return r['p1_rings'] == icon['p1_rings']+10
        if content in (5,6,7): return bool(r['p1_power'] & {5:4,6:1,7:2}[content])
        return (abs(icon['p1x']-icon['p2x']) > 500 and abs(r['p1x']-icon['p2x']) < 48
                and abs(r['p2x']-icon['p1x']) < 48)
    award = next(r for r in observations[init:] if awarded(r))
    if frames[-1]['frame']-award['frame'] < 240 or frames[-1]['frame'] != broken['frame']+400:
        raise ValueError('break+400 capture and at least four seconds after award required')
    return dict(passed=True,coverage=[f'qmark-content-{content}-init',f'qmark-content-{content}-award',
                'p1-break-before-native-lag','four-second-outcome-hold'],missing=[],
                goalFrames=dict(breakFrame=broken['frame'],iconInit=init,award=award['frame']),
                levelFrameAtInit=icon['lfc'],acceptedComparisonBoundary=lag)

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--capture',type=Path,required=True);p.add_argument('--movie',type=Path,required=True)
    p.add_argument('--content',type=int,choices=range(1,9),required=True);p.add_argument('--out',type=Path,required=True)
    a=p.parse_args();host=json.loads((a.capture/'host.json').read_text());digest=hashlib.sha256(a.movie.read_bytes()).hexdigest()
    if (host.get('execution_status') != 'completed' or host.get('exit_code') != 0 or host.get('failures')
            or host.get('fixture_state') or host.get('bk2_sha256_after','').lower() != digest
            or host['rom_sha1'].upper() != ROM or host['bk2_sha256'].lower() != digest):
        raise ValueError('matching successful cold host receipt required')
    with zipfile.ZipFile(a.movie) as z:
        inputs=[r for r in z.read('Input Log.txt').decode().splitlines() if r.startswith('|')]
    if len(inputs)<360 or any(set(r)-set('|.') for r in inputs[-360:]):
        raise ValueError('frozen movie must end with 360 neutral rows')
    frames=rows(a.capture/'frames.csv');observations=rows(a.capture/'monitors.csv')
    if len(frames)<len(inputs): raise ValueError('capture must cover the whole movie')
    result=analyse(frames,observations,a.content)
    result.update(format='openggf-native-goals/1',romSha1=ROM,movieSha256=digest,nativeRows=len(inputs),
            harness='BizHawk2.11/GPGX cold read-only qmark outcomes',capture=str(a.capture.resolve()),
            observationsSha256=hashlib.sha256((a.capture/'monitors.csv').read_bytes()).hexdigest())
    a.out.write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
if __name__=='__main__': main()
