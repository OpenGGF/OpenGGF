#!/usr/bin/env python3
"""Validate bounded native player comparisons; player-reference-r1, 2026-10-24.
Input: an external player_reference.py output. Never supplies gameplay inputs.
"""
import argparse
import hashlib
import json
from pathlib import Path
from player_reference import require, check_schedule, dump


def digest(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()


def rows(p):
    return [json.loads(s) for s in p.read_text().splitlines()]


def field_check(r, cfg):
    require(r['phase']==cfg['phase'],'Incorrect phase label')
    if not r['accepted']:
        require(not r.get('active_player',False),'Unknown identity admitted as active player')
        return
    require(r['identity_window_sha256']=={w['name']:w['sha256'] for w in cfg['windows']},'Loaded program window mismatch')
    b=bytes.fromhex(r['player_raw_hex']);require(len(b)==64,'Player slot extent')
    f=r['fields']
    words={'oTile','oX','oXSub','oY','oYSub','oXVel','oYVel','oPlayerGVel','oPlayerMoveLock'}
    for name,at in cfg['object_fields'].items():
        n=4 if name=='oMap' else 2 if name in words else 1
        require(f[name]==int.from_bytes(b[at:at+n],'big'),f'Logical memory endian/field mismatch: {name}')
    require(f['oID']==1 and f['oMap']==cfg['map_pointer'],'Unverified player actor')
    for field,raw in [('centre_x','oX'),('centre_y','oY'),('x_velocity_s16','oXVel'),('y_velocity_s16','oYVel'),('inertia_s16','oPlayerGVel')]:
        v=f[raw];require(r[field]==(v-65536 if v>=32768 else v),f'Signed native field mismatch: {field}')
    require(r['x_16_16_unsigned']==f['oX']*65536+f['oXSub'],'X fraction lost')
    require(r['y_16_16_unsigned']==f['oY']*65536+f['oYSub'],'Y fraction lost')
    g=r['globals']
    active=(f['oRoutine']==2 and g['paused']==0 and g['demoMode']==0 and g['debugMode']==0 and g['ctrlLocked']==0)
    require(r['active_player']==active,'Active player qualification mismatch')


def check_av(av, scenario, start, count):
    require([r['frame'] for r in av]==list(range(start,start+count)),'Missing/reordered AV calls')
    for r in av:
        inputs=sorted({'P1 '+x['button'] for x in scenario['inputs'] if x['first']<=r['frame']<=x['last']})
        require(r['input']==inputs,'Controller schedule mismatch')
        require(r['audio_samples']>0,'No synthesized samples')
        require(all(len(r[k])==64 for k in ['audio_sha256','video_sha256']),'Missing AV hash')


def summary_check(s, cfg, scenario, mode):
    n=scenario['frames']
    require(s['frames']==n and s['total_frameadvance_calls']==n+240 and n+240<=6000,'Wrong call bound/count')
    require(s['restore_120_equal'] and s['observation_savestate_unchanged'],'Restore or read-effect failure')
    require(s['deterministic'] and s['is_mega_cd'],'Wrong native core mode')
    require(s['mode']==mode and s['phase']==cfg['phase'],'Mode/phase mismatch')
    require(s['raw_final_states_equal']==(s['final_state_sha256']==s['replay_final_state_sha256']),'False raw-state equality label')


def check_run(p,cfg,scenario,mode):
    n=scenario['frames'];s=json.loads((p/'summary.json').read_text())
    summary_check(s,cfg,scenario,mode)
    require(digest(p/'checkpoint.state')==s['checkpoint_sha256'],'Checkpoint hash mismatch')
    av=rows(p/'av.jsonl');check_av(av,scenario,1,n)
    forward=rows(p/'forward.jsonl');replay=rows(p/'replay.jsonl')
    require(forward==replay,'120-call stream replay mismatch')
    require(digest(p/'forward.jsonl')==s['continuation_sha256'],'Continuation hash mismatch')
    check_av([r['av'] for r in forward],scenario,n+1,120)
    active=[]
    if mode!='control':
        obs=rows(p/'player.jsonl')
        require([r['frame'] for r in obs]==list(range(1,n+1)),'Missing/reordered player calls')
        for r in obs:field_check(r,cfg)
        for r in forward:field_check(r['player'],cfg)
        field_check(s['checkpoint_poll'],cfg)
        require(s['checkpoint_poll']['active_player'],'Checkpoint is not an active unpaused player')
        active=[r for r in obs if r.get('active_player')]
        require(active,'No active player')
    else:
        require(not (p/'player.jsonl').exists() and all(r['player'] is None for r in forward),'Control includes player observer')
    raw=s['raw_final_states_equal']
    require(raw==(s['final_state_sha256']==s['replay_final_state_sha256']),'False raw-state equality label')
    if not raw:
        require(s['raw_final_different_bytes']>0 and s['raw_final_different_offsets_first_64'],'Private state mismatch unaccounted')
        for file,key in [('forward-end.state','final_state_sha256'),('replay-end.state','replay_final_state_sha256')]:
            require(digest(p/file)==s[key],'Raw mismatch artifact hash differs')
    return s,av,forward,active


def validate(root):
    cfg=json.loads((root/'layout.json').read_text());scenario=json.loads((root/'scenario.json').read_text())
    check_schedule(scenario)
    m=json.loads((root/'manifest.json').read_text())
    require(m['layout_sha256']==digest(root/'layout.json') and m['scenario_sha256']==digest(root/'scenario.json'),'Input metadata drift')
    require(m['comparison_only'] and m['per_run_call_limit']==6000,'Authority/bound mismatch')
    require(set(m['modes'])=={'observed-1','observed-2','control'},'Two cold repetitions and control required')
    checked={mode:check_run(root/mode,cfg,scenario,mode) for mode in m['modes']}
    a,b,c=[checked[x] for x in ('observed-1','observed-2','control')]
    for filename in ['player.jsonl','av.jsonl','forward.jsonl','replay.jsonl']:
        require(digest(root/'observed-1'/filename)==digest(root/'observed-2'/filename),'Cold repetitions differ: '+filename)
    require(a[1]==c[1],'Observer/control capture AV differs')
    require([r['av'] for r in a[2]]==[r['av'] for r in c[2]],'Observer/control replay AV differs')
    for f in scenario['screenshots']:
        name=f'postframe-{f:05d}.ppm'
        require(digest(root/'observed-1'/name)==digest(root/'observed-2'/name)==digest(root/'control'/name),'Screenshot difference')
    states={mode:s[0]['checkpoint_sha256'] for mode,s in checked.items()}
    checkpoint_bytes_equal=all((root/'observed-1'/'checkpoint.state').read_bytes()==(root/mode/'checkpoint.state').read_bytes() for mode in checked)
    active=a[3];accepted=[r for r in rows(root/'observed-1'/'player.jsonl') if r['accepted']]
    g=lambda r:r['globals']
    result=dict(schema='soniccd-player-reference-validation-v1',comparison_only=True,
                call_count_each=scenario['frames']+240,cold_player_and_av_equal=True,observer_control_av_equal=True,
                restored_120_player_and_av_equal=True,read_observations_preserve_checkpoint=True,
                checkpoint_sha256=states,checkpoint_bytes_equal=checkpoint_bytes_equal,
                raw_restored_final_equal={mode:s[0]['raw_final_states_equal'] for mode,s in checked.items()},
                accepted_rows=len(accepted),active_rows=len(active),first_accepted_call=accepted[0]['frame'],
                first_active_call=active[0]['frame'],phase=cfg['phase'],
                level_frames_range=[g(active[0])['levelFrames'],g(active[-1])['levelFrames']],
                level_vint_range=[g(active[0])['levelVIntCounter'],g(active[-1])['levelVIntCounter']],
                x_range=[min(r['centre_x'] for r in active),max(r['centre_x'] for r in active)],
                y_range=[min(r['centre_y'] for r in active),max(r['centre_y'] for r in active)],
                native_air_rows=sum(bool(r['fields']['oFlags']&2) for r in active),
                native_rolling_rows=sum(bool(r['fields']['oFlags']&4) for r in active),
                negative_velocity_rows=sum(r['x_velocity_s16']<0 for r in active),
                positive_velocity_rows=sum(r['x_velocity_s16']>0 for r in active),
                charged_rows=sum(r['fields']['oPlayerCharge']>0 for r in active),
                evidence_sha256={mode:{p.name:digest(p) for p in (root/mode).iterdir() if p.is_file()} for mode in checked})
    return result


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('root',type=Path);p.add_argument('--output',type=Path)
    a=p.parse_args();result=validate(a.root)
    if a.output:
        require(not a.output.exists(),'Validation output exists');dump(a.output,result)
    print(json.dumps({k:v for k,v in result.items() if k!='evidence_sha256'},indent=2))
if __name__=='__main__':main()
