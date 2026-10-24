#!/usr/bin/env python3
"""Compare source-derived charge events in existing R2 post-frame rows.
Inputs: unchanged R2 checked output for the 2026-10-24 charge-reference task.
External research only. Does not produce inputs, locate actors, or assign phase.
"""
import argparse
import hashlib
import json
from pathlib import Path


def require(ok, message):
    if not ok:
        raise ValueError(message)


def rows(path):
    return [json.loads(line) for line in path.read_text().splitlines()]


def release_kind(previous, cap):
    """ObjSonic increments before dispatch; release need not expose the cap row."""
    require(1 <= previous <= cap, 'Invalid charge before release')
    return 'mature' if min(previous + 1, cap) == cap else 'early'


def episodes(stream, mechanic):
    rolling = mechanic == 'spindash'
    cap, bit, increment = (45, 2, 75) if rolling else (30, 1, 100)
    result, charge, previous, launch_position = [], [], None, None
    for r in stream:
        if not r.get('active_player'):
            require(not charge, 'Charge lost active-player qualification')
            previous = r
            continue
        f, g = r['fields'], r['globals']
        q = f['oPlayerCharge']
        if launch_position is not None:
            require(not q, 'Mature release did not move before next charge')
        if q or charge:
            require(g['p1CtrlHold'] == g['playerCtrlHold'] and g['p1CtrlTap'] == g['playerCtrlTap'], 'Input latch divergence')
        if q:
            require(previous and previous.get('active_player'), 'Missing pre-charge actor')
            require(bool(f['oFlags'] & 4) == rolling and not f['oFlags'] & 2, 'Wrong charge mode')
            require([f['oXRadius'], f['oYRadius']] == ([7,14] if rolling else [9,19]), 'Wrong charge radii')
            require(g['playerCtrlHold'] & bit, 'Charge direction missing')
            if not charge:
                require(q == 1 and previous['fields']['oPlayerCharge'] == 0, 'Missing charge start/zero')
                require(g['playerCtrlTap'] & 0x70 and previous['globals']['playerCtrlHold'] & bit, 'Missing held-before-tap start')
            else:
                before = charge[-1]
                require(q == min(before['fields']['oPlayerCharge'] + 1, cap), 'Charge counter increment/cap differs')
                require(r['x_16_16_unsigned'] == before['x_16_16_unsigned'] and r['y_16_16_unsigned'] == before['y_16_16_unsigned'], 'Charge moved after start')
                require(r['inertia_s16'] == min(before['inertia_s16'] + increment, 2*g['sonicTopSpeed']), 'Charge inertia increment/cap differs')
            charge.append(r)
        elif charge:
            before = charge[-1]
            require(not g['playerCtrlHold'] & bit, 'Charge ended without ordinary release')
            kind = release_kind(before['fields']['oPlayerCharge'], cap)
            if kind == 'early':
                require(all(r[k] == 0 for k in ['inertia_s16','x_velocity_s16','y_velocity_s16']), 'Early cancellation kept velocity')
                require(not f['oFlags'] & 4 and [f['oXRadius'],f['oYRadius']] == [9,19], 'Early cancellation did not restore standing')
            else:
                require(r['inertia_s16'] > 0 and r['x_velocity_s16'] > 0, 'Mature release did not launch')
                launch_position = (before['x_16_16_unsigned'], before['y_16_16_unsigned'])
            result.append(dict(kind=kind, start=charge[0], before_release=before, release=r,
                               maximum_observed_charge=max(x['fields']['oPlayerCharge'] for x in charge),
                               charged_rows=len(charge),
                               first_near_cap=next((x for x in charge if x['fields']['oPlayerCharge']==cap-1),None),
                               first_at_cap=next((x for x in charge if x['fields']['oPlayerCharge']==cap),None)))
            charge = []
        previous = r
        # Stored XY speed is not motion; admit any later full-position displacement.
        if launch_position is not None and (r['x_16_16_unsigned'], r['y_16_16_unsigned']) != launch_position:
            launch_position = None
    require(not charge, 'Unfinished charge at observation bound')
    require(launch_position is None, 'Mature release did not move in observed continuation')
    return result


def validate(root, mechanic):
    scenario = json.loads((root/'scenario.json').read_text())
    summary = json.loads((root/'observed-1/summary.json').read_text())
    capture = rows(root/'observed-1/player.jsonl')
    continuation = [x['player'] for x in rows(root/'observed-1/forward.jsonl')]
    stream = capture + continuation
    active = [r for r in stream if r.get('active_player')]
    for a,b in zip(active,active[1:]):
        require(b['frame'] == a['frame']+1, 'Active observation gap')
        require(b['globals']['levelFrames'] == a['globals']['levelFrames']+1 and
                b['globals']['levelVIntCounter'] == a['globals']['levelVIntCounter']+1, 'Native counter discontinuity')
    found = episodes(stream,mechanic)
    require([x['kind'] for x in found] == ['early','mature'], 'Need early cancellation and mature release')
    full = found[1]
    require(full['first_near_cap'] and full['first_at_cap'], 'Missing explicit near-cap/cap observations')
    checkpoint = summary['checkpoint_poll']
    require(checkpoint['active_player'] and checkpoint['fields']['oPlayerCharge'] > 0, 'Checkpoint is not charging')
    require(full['start']['frame'] <= checkpoint['frame'] < full['first_near_cap']['frame'] < full['release']['frame'] <= scenario['frames']+120, 'Continuation does not span maturity/release')
    return dict(schema='soniccd-charge-reference-events-v1',mechanic=mechanic,comparison_only=True,
                phase=summary['phase'],instruction_phase_known=False,episodes=found,
                checkpoint=checkpoint,final_observed=active[-1],
                evidence_sha256={str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in [root/'observed-1/player.jsonl',root/'observed-1/forward.jsonl',root/'observed-1/replay.jsonl',root/'validation.json']})


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('root',type=Path);p.add_argument('mechanic',choices=['peelout','spindash']);p.add_argument('--output',type=Path,required=True)
    a=p.parse_args();require(not a.output.exists(),'Output exists')
    result=validate(a.root,a.mechanic);a.output.write_text(json.dumps(result,indent=2,sort_keys=True)+'\n')
    print(json.dumps(dict(mechanic=a.mechanic,events=[dict(kind=x['kind'],start=x['start']['frame'],release=x['release']['frame'],max_charge=x['maximum_observed_charge']) for x in result['episodes']],checkpoint_charge=result['checkpoint']['fields']['oPlayerCharge']),indent=2))
if __name__ == '__main__':main()
