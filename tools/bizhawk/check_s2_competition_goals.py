#!/usr/bin/env python3
"""Purpose: validate cold read-only S2 goal observations, independently of state-comparison lag.
Inputs: native host receipt, frozen BK2, state.csv and goals.csv. Origin: lane P, 2026-11-08.
No input authoring, native execution, gameplay state writes or inferred filler rows.
"""
import argparse
import csv
import hashlib
import json
from pathlib import Path
import zipfile

ROM_SHA1 = "8BCA5DCEF1AF3E00098666FD892DC1C2A76333F9"

def rows(path):
    return [{k: int(v) for k, v in r.items() if v is not None} for r in csv.DictReader(Path(path).open())]

def analyse(state, goals, kind):
    if not state or any(r['frame'] != i+1 for i, r in enumerate(state)):
        raise ValueError('cold contiguous samples from frame 1 required')
    boundary = next((b['frame'] for a,b in zip(state,state[1:])
                     if a['mode']==b['mode']==12 and a['two_player']==b['two_player']==1
                     and a['zoneact']==b['zoneact'] and not a.get('game_paused',0)
                     and not b.get('game_paused',0) and a['lfc']==b['lfc']
                     and (b['vint']-a['vint'])&0xffffffff==1), None)
    found = {}
    if kind in ('slot','slot-flip'):
        if len(goals)!=len(state) or any(g['frame']!=s['frame'] for g,s in zip(goals,state)):
            raise ValueError('one goal observation per physical sample required')
        for g,s in zip(goals,state):
            ordinary=s['mode']==12 and s['two_player']==1 and s['zoneact']>>8==12
            if not ordinary: continue
            if (g['slot_inuse'] and g['slot_visible']==1 and g['slot_player'] in (1,2)
                and 2<=g['slot_object_slot']<128 and 0<=g['slot_screen_x']<320
                and 0<=g['slot_screen_y']<(216 if g['slot_player']==1 else 232)):
                found.setdefault('slot-in-use-in-view',g['frame'])
            top,bottom=(0,216) if g['player']==1 else (216,448)
            if (g['flip_visible']==1 and g['player'] in (1,2) and g['pose']>=0 and g['pose']%4
                and 0<=g['x']<=312 and top<=g['y']<=bottom-16
                and g['x']<=g['pixel_x']<g['x']+8 and g['y']<=g['pixel_y']<g['y']+16
                and g['art_address'] in (0x6600,0xe800)
                and g['art_address']//64<=g['word']%1024<g['art_address']//64+8):
                found.setdefault('resident-nonflat-cell-in-view',g['frame'])
        required=['slot-in-use-in-view'] + (['resident-nonflat-cell-in-view'] if kind=='slot-flip' else [])
    else:
        for s in state:
            if s['mode']==12 and s['zoneact']==0: found.setdefault('ehz-act1-play',s['frame'])
            if s['mode']==12 and s['zoneact']==1: found.setdefault('ehz-act2-play',s['frame'])
            if s['zone_2p']!=0 or s['act1_result']==0xffff or s['act2_result']==0xffff: continue
            if s['mode']==24 and s['results']==1 and s['act_2p']==2:
                found.setdefault('ehz-zone-results',s['frame'])
            wins=[0,0]
            for value in (s['act1_result'],s['act2_result']):
                a,b=value>>8,value&255
                if a>b: wins[0]+=1
                elif b>a: wins[1]+=1
            if (s['mode']==16 and s['results']==3 and s['act_2p']==2
                and wins[0]==wins[1] and s['ss_stage']==1 and s.get('ss_started',0)!=0
                and s.get('p1_id')==9 and s.get('p2_id')==16
                and s['p1_routine']>=2 and s['p2_routine']>=2):
                found.setdefault('tied-act-wins-to-competitive-special-stage',s['frame'])
        required=['ehz-act1-play','ehz-act2-play', 'ehz-zone-results' if kind=='zone-results'
                  else 'tied-act-wins-to-competitive-special-stage']
    missing=[k for k in required if k not in found]
    return {'passed':not missing,'coverage':[k for k in required if k in found],
            'goalFrames':found,'missing':missing,'acceptedComparisonBoundary':boundary}

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--capture',type=Path,required=True);p.add_argument('--movie',type=Path,required=True)
    p.add_argument('--kind',choices=['slot','slot-flip','zone-results','tie-special'],required=True)
    p.add_argument('--out',type=Path,required=True);a=p.parse_args()
    host=json.loads((a.capture/'host.json').read_text())
    digest=hashlib.sha256(a.movie.read_bytes()).hexdigest()
    if (host.get('execution_status')!='completed' or host.get('exit_code')!=0 or host.get('failures')
        or host.get('fixture_state') or host['rom_sha1'].upper()!=ROM_SHA1
        or host['bk2_sha256'].lower()!=digest): raise ValueError('matching successful cold host receipt required')
    state=rows(a.capture/'state.csv')
    with zipfile.ZipFile(a.movie) as z:
        count=sum(line.startswith('|') for line in z.read('Input Log.txt').decode().splitlines())
    if len(state)!=count: raise ValueError('observe the entire exact frozen movie')
    result=analyse(state,rows(a.capture/'goals.csv') if a.kind in ('slot','slot-flip') else [],a.kind)
    result.update(format='openggf-native-goals/1',romSha1=ROM_SHA1,movieSha256=digest,
                  nativeRows=count,harness='BizHawk2.11/GPGX cold read-only required-goals',
                  capture=str(a.capture.resolve()),stateSha256=hashlib.sha256((a.capture/'state.csv').read_bytes()).hexdigest())
    a.out.write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
    return 0 if result['passed'] else 1
if __name__=='__main__':raise SystemExit(main())
