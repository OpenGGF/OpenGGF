#!/usr/bin/env python3
"""Bounded native R11A post-frame reference; 2026-10-24 player-reference-r1.
Inputs: pinned scddisasm, existing soniccd producers, official BizHawk 2.11,
user-owned USA CUE/BIOS and new external output. Comparison only; no engine.
R2 (2026-10-24): preflight protects every checkout of the bound producer repo.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
SOURCE_PIN = '1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff'
BIZHAWK_PIN = '427556b5ef3ac437eba754d90c5e7e9096c9a8df'
GPGX_PIN = '051d430d3d1b54625f9900c8f152d7f232e06daf'

def sha(data):
    return hashlib.sha256(data).hexdigest()

def dump(path, value):
    path.write_text(json.dumps(value, indent=2, sort_keys=True)+'\n')

def require(ok, message):
    if not ok:
        raise ValueError(message)

def new_external(output, protected):
    output = Path(output).resolve()
    require(not output.exists(), 'Output already exists')
    for p in protected:
        p = Path(p).resolve()
        require(not (output.is_relative_to(p) or p.is_relative_to(output)), 'Output overlaps protected input')
    return output

def repository_checkouts(producer_directory):
    """Read only the repository owning <checkout>/tools/soniccd, never an ancestor.

    No guessed root is safe if Git is unavailable or its registry is incomplete.
    Reject before inputs/builds instead of falling back to a fixed parent depth.
    """
    try:
        producer = Path(producer_directory).resolve(strict=True)
    except (OSError, RuntimeError) as exc:
        raise ValueError('Cannot bind producer checkout: producer directory unavailable') from exc
    require(producer.is_dir() and producer.name == 'soniccd' and producer.parent.name == 'tools',
            'Cannot bind producer checkout: expected <checkout>/tools/soniccd')
    checkout = producer.parent.parent
    require((checkout / '.git').is_file() or (checkout / '.git').is_dir(),
            'Cannot bind producer checkout: missing checkout-local Git metadata')
    # Inherited Git overrides must not redirect discovery to another repository.
    env = {k:v for k,v in os.environ.items() if not k.startswith('GIT_')}
    env['GIT_OPTIONAL_LOCKS'] = '0'
    def git(*args):
        try:
            return subprocess.run(['git','--no-optional-locks','-C',str(checkout),*args],
                                  env=env,stdout=subprocess.PIPE,stderr=subprocess.PIPE,
                                  check=True,timeout=10).stdout
        except (OSError, subprocess.SubprocessError) as exc:
            raise ValueError('Cannot discover producer repository: read-only Git discovery failed') from exc
    top = Path(os.fsdecode(git('rev-parse','--show-toplevel').removesuffix(b'\n')))
    require(top.is_absolute() and top.resolve() == checkout,
            'Cannot bind producer checkout: Git top level differs from producer checkout')
    data = git('worktree','list','--porcelain','-z')
    require(data.endswith(b'\0\0'), 'Cannot discover producer repository: incomplete worktree registry')
    roots = []
    for record in data[:-2].split(b'\0\0'):
        first = record.split(b'\0',1)[0]
        require(first.startswith(b'worktree '), 'Cannot discover producer repository: malformed worktree registry')
        root = Path(os.fsdecode(first[len(b'worktree '):]))
        require(root.is_absolute(), 'Cannot discover producer repository: nonabsolute worktree path')
        roots.append(root.resolve())  # Includes missing/prunable registrations; no prune/repair.
    require(checkout in roots, 'Cannot discover producer repository: bound checkout is not registered')
    return roots

def check_schedule(scenario):
    n = scenario['frames']
    require(type(n) is int and 1 <= n <= 5760, 'Capture plus 240 replay calls must fit 6000')
    for row in scenario['inputs']:
        require(type(row['first']) is int and type(row['last']) is int and
                1 <= row['first'] <= row['last'] <= n+120, 'Input outside capture/replay bound')
        require(row['button'] in ('Up','Down','Left','Right','A','B','C','Start'), 'Unknown button')
    require(all(type(f) is int and 1 <= f <= n for f in scenario['screenshots']), 'Screenshot outside capture')

def prepare(args):
    sys.path.insert(0, str(args.producer_directory.resolve()))
    import oracle_boot as ob
    import device_observer_run as dev
    import stage_inventory as inv
    source = inv.Source(args.source)
    require(inv.SOURCE_PIN == SOURCE_PIN, 'Source parser pin changed')
    variables = inv.native_variables(source)
    cue = args.cue.resolve(strict=True)
    media = ob.media_identity(cue)
    with inv.RawIso(cue.parent / media['files'][0]['file']) as disc:
        program = inv.Mmd(disc.file('R11A__.MMD;1'))
        extent = disc.files['R11A__.MMD;1']
    inventory = json.loads(args.inventory.read_text())
    release = next(r for r in inventory['releases'] if r['region']=='USA')
    old = next(s for s in release['stages'] if s['stage']=='R11A')
    require(inventory['source_pin']==SOURCE_PIN and sha(program.data)==old['sha256'], 'USA R11A file identity mismatch')
    require(release['cue_sha256']==media['cue_sha256'] and release['data_track_sha256']==media['files'][0]['sha256'], 'USA userdata mismatch')
    require(extent['lba']==old['iso_extent']['lba'] and extent['size']==old['iso_extent']['bytes'], 'ISO extent mismatch')
    plan = inv.r11a(source, program, 'USA')
    data = program.data
    # Independently encode source-owned initialization stores, including native offsets.
    import struct
    init = struct.pack('>HHHHHHHH',0x5428, variables['oRoutine'],0x117c,0x13,variables['oYRadius'],0x117c,9,variables['oXRadius'])
    init_at = inv.unique(data, init, 'ObjSonic_Init native stores')
    map_at = plan['tables']['MapSpr_Sonic']['native_address']
    setmap = struct.pack('>HIH',0x217c,map_at,variables['oMap']) + struct.pack('>HHH',0x317c,0x780,variables['oTile'])
    setmap_at = init_at + inv.unique(data[init_at:init_at+80],setmap,'ObjSonic_Init mapping/tile stores')
    require(init_at < setmap_at < init_at+64, 'Player initialization source order differs')
    # RunObjects addresses the object table; table entries 1 and 2 share ObjSonic.
    candidates=[]
    for at in inv.occurrences(data,bytes.fromhex('43f9')):
        if data[at+6:at+12]==bytes.fromhex('227100fc4e91'):
            table=int.from_bytes(data[at+2:at+6],'big')-0x200000
            if 0 <= table < len(data)-16:
                first=int.from_bytes(data[table:table+4],'big')
                if data[table:table+4]==data[table+4:table+8] and 0x200000<=first<0x240000:
                    candidates.append((at,table,first))
    require(len(candidates)==1, 'RunObjects table encoding not unique')
    run_at, table_at, sonic = candidates[0]
    # Source-bound short code/data windows; no savestate pattern scanning.
    windows=[]
    for name,at,n in [('mmd_header',0,256),('entry_stubs_start',256,128),
                      ('RunObjects_dispatch',run_at-12,32),('ObjectIndex',table_at,32),
                      ('ObjSonic_dispatch',sonic-0x200000,96),('ObjSonic_Init',init_at,80),
                      ('MapSpr_Sonic_prefix',map_at-0x200000,64),
                      ('DPLC_Sonic_prefix',plan['tables']['DPLC_Sonic']['file_offset'],64)]:
        block=inv.bounded(data,at,n)
        windows.append(dict(name=name,offset=at,length=n,sha256=sha(block),prefix_hex=block[:32].hex()))
    fields={}
    for name in ['oID','oSprFlags','oTile','oMap','oX','oXSub','oY','oYSub','oXVel','oYVel',
                 'oPlayerGVel','oYRadius','oXRadius','oPriority','oWidth','oMapFrame','oAnimFrame',
                 'oAnim','oPrevAnim','oAnimTime','oFlags','oRoutine','oRoutine2','oAngle',
                 'oPlayerCharge','oPlayerCtrl','oPlayerJump','oPlayerMoveLock','oPlayerPriAngle',
                 'oPlayerSecAngle','oPlayerStick']:
        fields[name]=variables[name]
    specs={'playerCtrlHold':1,'playerCtrlTap':1,'p1CtrlHold':1,'p1CtrlTap':1,'gameMode':1,
           'levelFrames':2,'levelVIntCounter':4,'zone':1,'act':1,'timeZone':1,'demoMode':2,
           'debugMode':2,'paused':2,'vintRoutine':1,'vintTimer':2,'ctrlLocked':1,
           'cameraX':4,'cameraY':4,'cameraBgX':4,'cameraBgY':4,'sonicLastFrame':1,
           'updateSonicArt':1,'sonicTopSpeed':2,'sonicAcceleration':2,'sonicDeceleration':2,
           'primaryAngle':1,'secondaryAngle':1,'boredTimer':2,'lookMode':1,'sonicRecordIndex':2,
           'rings':2,'timeMinutes':1,'timeSeconds':1,'timeFrames':1}
    globals_={k:dict(offset=variables[k]&0xffff,width=v,native_address=variables[k]&0xffffff) for k,v in specs.items()}
    for path in ['Level/Palmtree Panic/Act 1 Present.asm','Level/Initialization.asm',
                 'Level/Object Functions.asm','Level/Palmtree Panic/Object Index (Act 1 Present).asm',
                 'Level/_Objects/Sonic/Main.asm','Level/Main.asm']:
        source.read(path)
    widths={k:(4 if k=='oMap' else 2 if k in {'oTile','oX','oXSub','oY','oYSub','oXVel','oYVel','oPlayerGVel','oPlayerMoveLock'} else 1) for k in fields}
    cfg=dict(object_field_widths=widths,schema='soniccd-player-postframe-layout-v1',source_pin=SOURCE_PIN,windows=windows,
             object_fields=fields,globals=globals_,player_offset=variables['objPlayerSlot']&0xffff,
             art_buffer_offset=variables['sonicArtBuf']&0xffff,art_buffer_bytes=0x300,
             resident_vram_offset=0xf000,resident_vram_bytes=0x2e0,map_pointer=map_at,
             sonic_dispatch=sonic,mmd=program.header,r11a_sha256=sha(data),iso_extent=old['iso_extent'],
             phase='after_FrameAdvance_and_GetSamplesSync; exact_VInt/player/DPLC_slot_unknown')
    return cfg, dict(media=media,source_pin=SOURCE_PIN,source_inputs=source.used,
                     source_variables=variables,bizhawk_pin=BIZHAWK_PIN,gpgx_pin=GPGX_PIN,
                     inventory_sha256=ob.digest(args.inventory),
                     dependencies_sha256={p.name:ob.digest(p) for p in [args.producer_directory/name for name in
                     ['oracle_boot.py','oracle_probe.cs','device_observer_run.py','stage_inventory.py','catalogue.py','retail_profiles.py']]}),ob,dev

def main():
    p=argparse.ArgumentParser(description=__doc__)
    for n in ['producer-directory','source','inventory','bizhawk-home','cue','firmware-directory','scenario','output']:
        p.add_argument('--'+n,type=Path,required=True)
    p.add_argument('--bios-sha1',required=True)
    p.add_argument('--timeout',type=int,default=180)
    p.add_argument('--modes',default='observed-1,observed-2,control')
    p.add_argument('--compiler',type=Path,default=Path('/usr/lib/mono/msbuild/Current/bin/Roslyn/csc.exe'))
    p.add_argument('--netstandard',type=Path,default=Path('/usr/lib/mono/4.8-api/Facades/netstandard.dll'))
    a=p.parse_args()
    output=new_external(a.output,[*repository_checkouts(a.producer_directory),a.producer_directory,
                                 a.source,a.bizhawk_home,a.cue.parent,a.firmware_directory,Path(__file__).parent])
    scenario=json.loads(a.scenario.read_text());check_schedule(scenario)
    cfg,provenance,ob,dev=prepare(a)
    dll=a.bizhawk_home.resolve()/'dll'; binaries=dev.verify_pins(dll,dev.RUNTIME_PINS)
    require(ob.digest(a.compiler)==ob.COMPILER_SHA256,'Compiler mismatch')
    modes=a.modes.split(',');require(modes and all(m in ('observed-1','observed-2','control') for m in modes) and len(set(modes))==len(modes),'Invalid modes')
    inputs=[Path(__file__),Path(__file__).with_name('player_reference_probe.cs'),a.producer_directory/'oracle_probe.cs']
    inputs_hash={str(f):ob.digest(f) for f in inputs}
    output.mkdir();build=output/'build';build.mkdir();(build/'dll').symlink_to(dll,target_is_directory=True)
    dump(output/'layout.json',cfg);dump(output/'scenario.json',scenario)
    exe=build/'player_reference.exe'
    command=['mono',str(a.compiler),'/nologo','/langversion:9','/deterministic+','/main:PlayerReference',f'/out:{exe}',f'/reference:{a.netstandard}']
    command += [f'/reference:{dll/name}' for name in ob.ASSEMBLIES]+[str(inputs[1]),str(inputs[2])]
    subprocess.run(command,check=True,timeout=30,cwd=build)
    env=os.environ.copy()
    for key in ['DISPLAY','WAYLAND_DISPLAY','PULSE_SERVER','JACK_DEFAULT_SERVER']:
        env.pop(key,None)
    env.update(MONO_PATH=str(dll),LD_LIBRARY_PATH=str(dll),TMPDIR=str(build))
    commands=[]
    for mode in modes:
        cmd=['mono',str(exe),str(a.cue.resolve()),str(a.firmware_directory.resolve()),str(output/mode),
             str(output/'layout.json'),str(output/'scenario.json'),a.bios_sha1,mode]
        commands.append(cmd)
        with (output/(mode+'.log')).open('w') as log:
            proc=subprocess.Popen(cmd,env=env,cwd=build,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
            try:
                result=proc.wait(timeout=a.timeout)
            except BaseException:
                import signal
                os.killpg(proc.pid,signal.SIGTERM)
                try:proc.wait(timeout=5)
                except subprocess.TimeoutExpired:os.killpg(proc.pid,signal.SIGKILL);proc.wait()
                raise
        require(result==0,f'{mode} failed ({result}); inspect bounded log')
        print(mode+' completed',flush=True)
    require(all(ob.digest(f)==inputs_hash[str(f)] for f in inputs),'Producer changed during run')
    dev.verify_pins(dll,dev.RUNTIME_PINS)
    manifest=dict(schema='soniccd-player-reference-v1',comparison_only=True,phase=cfg['phase'],
                  provenance=provenance,runtime_sha256=binaries,producer_sha256=inputs_hash,
                  executable_sha256=ob.digest(exe),compiler_sha256=ob.digest(a.compiler),
                  scenario_sha256=ob.digest(output/'scenario.json'),layout_sha256=ob.digest(output/'layout.json'),
                  modes=modes,commands=commands,display=None,audio_device=None,per_run_call_limit=6000,
                  summaries={m:json.loads((output/m/'summary.json').read_text()) for m in modes})
    dump(output/'manifest.json',manifest)
    print(output/'manifest.json')
if __name__=='__main__':main()
