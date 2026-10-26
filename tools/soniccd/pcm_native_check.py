#!/usr/bin/env python3
"""Strict CD-06 PCM fixture/control admission. No emulation or value synthesis.

2026-10-23. Checks all declared RAM, channels, selectors, output samples and
padding; comparisons have no masks/tolerances. These are private source-model
fixtures, not hardware timing, resampling, driver or full-game parity evidence.
"""
import argparse
from collections import Counter
import gzip
import hashlib
import json
from pathlib import Path
import struct

from pcm_native_common import (BLOCK_BYTES, STATE_BYTES, RAM_BYTES, SAMPLE_BYTES, RUNTIME_PINS,
    SOURCE_PROVENANCE, TOOLCHAIN_PROVENANCE, USA_MEDIA, USA_BIOS, FROZEN_PINS, UNALTERED_CORE,
    COMPILER_PIN, require_hash, sha)

SCHEMA = 'soniccd.pcm-native-capture.v1'
SUMMARY_FIELDS = ('ordinal', 'call', 'length', 'enabled', 'status', 'index', 'bank',
                  'cycles_pre', 'cycles_post', 'left_pre', 'right_pre', 'left_post', 'right_post',
                  'nonzero_samples', 'loop_markers', 'volume')
SOURCE_PINS = {'bizhawk': '427556b5ef3ac437eba754d90c5e7e9096c9a8df',
               'gpgx': '051d430d3d1b54625f9900c8f152d7f232e06daf',
               'musl': '2063abc4e16c84218757b1db10d3cdf9f36ef3f8'}


def signed(value):
    return value if value < 0x80000000 else value - 0x100000000


def uncompress(path, bound):
    with gzip.open(path, 'rb') as f:
        data = f.read(bound + 1)
    if len(data) > bound:
        raise ValueError('Compressed payload exceeds declared bound')
    return data


def state(data):
    if len(data) != STATE_BYTES:
        raise ValueError('Incomplete PCM state')
    words = struct.unpack('<56I', data)
    channels = [list(words[n:n+6]) for n in range(0, 48, 6)]
    for addr, start, loop, frequency, env, pan in channels:
        if start & ((1 << 19) - 1) or start > 255 << 19 or loop > 65535 or frequency > 65535 or env > 255 or pan > 255:
            raise ValueError('Non-native PCM channel field')
    enabled, mask, index, bank, left, right, cycles, volume = words[48:]
    left, right = signed(left), signed(right)
    if enabled not in (0,128) or mask > 255 or index > 7 or bank > 15 or volume != 100 or not (-32768 <= left <= 32767 and -32768 <= right <= 32767):
        raise ValueError('Non-native PCM chip/output field')
    return dict(channels=channels, enabled=enabled, status=mask, index=index, bank=bank,
                output=[left,right], cycles=cycles, volume=volume)


def decode_block(data):
    if len(data) != BLOCK_BYTES:
        raise ValueError('Incomplete or trailing PCM block bytes')
    h = struct.unpack_from('<16I', data)
    if (h[0] != 0x424d4350 or h[1] != 1 or h[2] < 1 or h[3] < 1 or not 1 <= h[4] <= 4096
            or h[5:11] != (224,65536,32,h[4],1,1) or any(h[11:])):
        raise ValueError('PCM block ABI/extent/phase mismatch')
    pre, post = state(data[64:288]), state(data[288:512])
    ram_pre, ram_post = data[512:66048], data[66048:131584]
    if ram_pre != ram_post:
        raise ValueError('PCM RAM changed inside pure pcm_run')
    for field in ['enabled','status','index','bank','volume']:
        if pre[field] != post[field]:
            raise ValueError('PCM register changed inside pcm_run: ' + field)
    for index,(a,b) in enumerate(zip(pre['channels'], post['channels'])):
        if a[1:] != b[1:]:
            raise ValueError('PCM channel register changed inside pcm_run')
        if (not pre['enabled'] or not pre['status'] & (1 << index)) and a[0] != b[0]:
            raise ValueError('Inactive PCM channel cursor changed inside pcm_run')
    if post['cycles'] != (pre['cycles'] + 1536*h[4]) & 0xffffffff:
        raise ValueError('PCM service counter differs from explicit length')
    raw = list(struct.iter_unpack('<IiiiiIII', data[131584:131584+h[4]*32]))
    if any(data[131584+h[4]*32:]):
        raise ValueError('PCM unused output storage is nonzero')
    previous = pre['output']
    for n, (i,l,r,dl,dr,submitted,loops,reserved) in enumerate(raw):
        expected_submit = bool(pre['enabled']) or (n == 0 and any(previous))
        if (i != n or not -32768 <= l <= 32767 or not -32768 <= r <= 32767
                or dl != l-previous[0] or dr != r-previous[1] or submitted != int(expected_submit)
                or loops > 255 or loops & ~pre['status'] or (not pre['enabled'] and (l or r or loops)) or reserved):
            raise ValueError('PCM full sample/delta extent invalid at ' + str(n))
        previous = [l,r]
    if previous != post['output']:
        raise ValueError('PCM final sample/output mismatch')
    return dict(ordinal=h[2], call=h[3], length=h[4], pre=pre, post=post,
                ram_pre=ram_pre, ram_post=ram_post, raw=raw)


def device_state(native):
    """The complete finite Java device extent; cycles/volume are separately
    validated native service/host metadata, never silently masked chip fields."""
    return dict(channels=[[a,st >> 19,ls,fd,env,pan] for a,st,ls,fd,env,pan in native['channels']],
                chipEnabled=bool(native['enabled']), enabledChannelMask=native['status'],
                selectedChannel=native['index'], selectedBank=native['bank'],
                leftOutput=native['output'][0], rightOutput=native['output'][1])


def compare_device_result(block, actual):
    """Exact future-consumer boundary. Reject missing/extra keys and compare
    every declared device scalar/channel, all 65536 RAM bytes and every step."""
    expected = dict(state=device_state(block['post']), waveRam=block['ram_post'],
                    steps=[[l,r,dl,dr,bool(sub)] for _,l,r,dl,dr,sub,_,_ in block['raw']])
    def equal(a,b):
        if type(a) is not type(b):
            return False
        if isinstance(a,dict):
            return set(a)==set(b) and all(equal(a[k],b[k]) for k in a)
        if isinstance(a,list):
            return len(a)==len(b) and all(equal(x,y) for x,y in zip(a,b))
        return a==b
    if not equal(actual,expected):
        raise ValueError('PCM exact device/RAM/output comparison differs')


def summaries(data, frames):
    if len(data) % 64 or len(data) > 3600*4096*64:
        raise ValueError('PCM summary extent invalid')
    rows, previous_call = [], 0
    for n, words in enumerate(struct.iter_unpack('<16I', data), 1):
        row = dict(zip(SUMMARY_FIELDS, words))
        for key in ['left_pre','right_pre','left_post','right_post']:
            row[key] = signed(row[key])
        if (row['ordinal'] != n or not previous_call <= row['call'] <= frames or row['call'] < 1
                or row['enabled'] not in (0,128) or row['status'] > 255 or row['index'] > 7 or row['bank'] > 15
                or row['volume'] != 100 or row['nonzero_samples'] > row['length'] or row['loop_markers'] > 8*row['length']
                or row['cycles_post'] != (row['cycles_pre']+1536*row['length']) & 0xffffffff
                or any(not -32768 <= row[k] <= 32767 for k in ['left_pre','right_pre','left_post','right_post'])):
            raise ValueError('PCM summary order/field/clock mismatch')
        previous_call = row['call']; rows.append(row)
    return rows


def verify_run(run, frames, inputs, selected, mode):
    s = json.loads((run / 'summary.json').read_text())
    if (s['frames'] != frames or s['inputs'] != inputs or s['selected'] != selected or s['mode'] != mode
            or not s['is_mega_cd'] or not s['restore_120_calls_equal'] or not s['observation_savestate_unchanged']
            or not s['event_restore_equal'] or s['total_frame_advance_calls'] != frames+240 or frames+240 > 3600
            or s['display'] is not None or s['audio_device'] is not None):
        raise ValueError('PCM native scenario/restore control failed')
    for name, extent in [('av.jsonl',range(1,frames+1)),('continuation.jsonl',range(frames+1,frames+121))]:
        rows = [json.loads(line) for line in (run / name).read_text().splitlines()]
        if [r['frame'] for r in rows] != list(extent):
            raise ValueError('PCM AV frame extent/order differs')
        for r in rows:
            expected = sorted({p['button'] for p in inputs if p['first'] <= r['frame'] <= p['last']})
            if r['buttons'] != expected:
                raise ValueError('PCM authored controller input differs')
    checkpoint = uncompress(run / 'checkpoint.state.gz', 16*1024*1024)
    if len(checkpoint) != s['checkpoint_bytes'] or hashlib.sha256(checkpoint).hexdigest() != s['checkpoint_sha256']:
        raise ValueError('PCM unmasked checkpoint identity differs')
    calls = uncompress(run / 'calls.bin.gz', 3600*4096*64)
    if mode == 'overflow':
        if s['overflow_fault'] != 1 or s['overflow_dropped'] < 1:
            raise ValueError('PCM overflow control not exercised')
        return s, []
    if s['overflow_fault'] or s['overflow_dropped']:
        raise ValueError('PCM observer overflow: incomplete evidence')
    if mode != 'control' and not s['invalid_abi_calls_rejected']:
        raise ValueError('PCM invalid ABI rejection missing')
    if mode in ('control','disabled'):
        if calls or list(run.glob('block-*.bin.gz')):
            raise ValueError('Disabled/control PCM observer emitted evidence')
        return s, []
    rows = summaries(calls, frames)
    counts = [json.loads(line) for line in (run / 'counts.jsonl').read_text().splitlines()]
    by_call = Counter(r['call'] for r in rows)
    if ([r['frame'] for r in counts] != list(range(1,frames+1))
            or any(r['calls'] != by_call[r['frame']] or r['fault'] or r['dropped'] for r in counts)):
        raise ValueError('PCM per-call summary accounting differs')
    blocks = []
    if sorted(p.name for p in run.glob('block-*.bin.gz')) != [f'block-{n:08d}.bin.gz' for n in selected]:
        raise ValueError('PCM selected fixture inventory incomplete/extra')
    for ordinal in selected:
        b = decode_block(uncompress(run / f'block-{ordinal:08d}.bin.gz', BLOCK_BYTES))
        if ordinal > len(rows):
            raise ValueError('PCM selected ordinal absent')
        r = rows[ordinal-1]
        if (b['ordinal'] != ordinal or b['call'] != r['call'] or b['length'] != r['length']
                or b['pre']['output'] != [r['left_pre'],r['right_pre']]
                or b['post']['output'] != [r['left_post'],r['right_post']]
                or any(b['pre'][k] != r[k] for k in ['enabled','status','index','bank','volume'])
                or b['pre']['cycles'] != r['cycles_pre'] or b['post']['cycles'] != r['cycles_post']
                or sum(bool(v[1] or v[2]) for v in b['raw']) != r['nonzero_samples']
                or sum(v[6].bit_count() for v in b['raw']) != r['loop_markers']):
            raise ValueError('PCM selected block differs from observed call summary')
        blocks.append(b)
    for prefix in ['forward-','replay-']:
        summaries(uncompress(run / (prefix+'calls.bin.gz'), 120*4096*64),120)
    if (run / 'forward-calls.bin.gz').read_bytes() != (run / 'replay-calls.bin.gz').read_bytes():
        raise ValueError('PCM 120-call restored native observations differ')
    return s, blocks


def compare_runs(left, right):
    for name in ['av.jsonl','views.jsonl','continuation.jsonl','final.ppm']:
        if (left / name).read_bytes() != (right / name).read_bytes():
            raise ValueError('PCM independent native behavior differs: ' + name)
    pictures=sorted(p.name for p in left.glob('*.ppm'))
    if pictures != sorted(p.name for p in right.glob('*.ppm')) or any((left / n).read_bytes() != (right / n).read_bytes() for n in pictures):
        raise ValueError('PCM independent route pictures differ')
    a,b = [json.loads((p / 'summary.json').read_text()) for p in [left,right]]
    for field in ['checkpoint_view','final_view','fps_num','fps_den','settings','sync_settings','firmware']:
        if a[field] != b[field]:
            raise ValueError('PCM independent native view/config differs: ' + field)


def validate_capture(root, require_controls=True):
    root=Path(root)
    m=json.loads((root / 'manifest.json').read_text())
    if m['schema'] != SCHEMA or m['runtime_sha256'] != RUNTIME_PINS or m['compiler_sha256'] != COMPILER_PIN:
        raise ValueError('PCM producer/runtime schema or identity differs')
    if (json.loads((root / 'runtime-pins.json').read_text()) != RUNTIME_PINS
            or json.loads((root / 'inputs.json').read_text()) != m['inputs']):
        raise ValueError('PCM actual runtime/input control files differ')
    if (m['source_pins'] != SOURCE_PINS or m['source_provenance_sha256'] != SOURCE_PROVENANCE
            or m['toolchain_provenance_sha256'] != TOOLCHAIN_PROVENANCE or m['bios_sha1'] != USA_BIOS):
        raise ValueError('PCM source/toolchain/BIOS pins differ')
    media=dict(m['media']); fingerprint=media.pop('source_fingerprint')
    if (fingerprint != USA_MEDIA or hashlib.sha256(json.dumps(media,sort_keys=True,separators=(',',':')).encode()).hexdigest() != fingerprint):
        raise ValueError('PCM media pins differ')
    required_producers={'pcm_native_probe.cs','pcm_native_run.py','pcm_native_check.py','pcm_native_common.py',
                        'pcm_native_build.py','pcm_native_patch.py','pcm_native_core.patch','pcm_native_observer.c',
                        'pcm_native_observer.h','oracle_boot.py','oracle_probe.cs','native_owner_build.py'}
    if set(m['producer_sha256']) != required_producers:
        raise ValueError('PCM producer inventory incomplete/extra')
    for name, expected in m['producer_sha256'].items():
        require_hash(root / 'producer' / name, expected)
        if name in FROZEN_PINS and expected != FROZEN_PINS[name]:
            raise ValueError('PCM frozen producer pin differs')
    build=m['instrumented_identity']
    if (build['patch_sha256'] != m['producer_sha256']['pcm_native_core.patch'] or build['source_pins'] != SOURCE_PINS
            or build['source_provenance_sha256'] != SOURCE_PROVENANCE or build['toolchain_provenance_sha256'] != TOOLCHAIN_PROVENANCE
            or build['recipe_sha256'] != FROZEN_PINS['native_owner_build.py']):
        raise ValueError('PCM source-built core provenance differs')
    if build['pcm_recipe_sha256'] != m['producer_sha256']['pcm_native_build.py']:
        raise ValueError('PCM build recipe differs from producer snapshot')
    for name in ['gpgx.wbx','gpgx.wbx.zst','input-inventory.json','abi.json']:
        key={'gpgx.wbx':'raw_sha256','gpgx.wbx.zst':'compressed_sha256','input-inventory.json':'input_inventory_sha256','abi.json':None}[name]
        expected=build[key] if key else m['abi_sha256']
        require_hash(Path(m['instrumented_build']) / name, expected)
    abi=json.loads((Path(m['instrumented_build']) / 'abi.json').read_text())
    if (abi != m['abi'] or abi['raw_sha256'] != build['raw_sha256'] or abi['block_bytes'] != BLOCK_BYTES
            or abi['state_bytes'] != STATE_BYTES or abi['ram_bytes'] != RAM_BYTES or abi['sample_bytes'] != SAMPLE_BYTES
            or not abi['invisible_address'] <= abi['observer_address'] < abi['observer_address']+abi['observer_bytes'] <= abi['invisible_address']+abi['invisible_bytes']):
        raise ValueError('PCM binary ABI/layout/invisible extent differs')
    if require_controls and not m['controls_complete']:
        raise ValueError('PCM independent controls are incomplete')
    results={}
    for label, mode in m['modes'].items():
        selected=[] if mode=='overflow' else m['selected']
        actual_files={p.name for p in (root / label).iterdir() if p.is_file()}
        if actual_files != set(m['streams'][label]):
            raise ValueError('PCM run output inventory incomplete/extra')
        for name, record in m['streams'][label].items():
            if Path(name).name != name:
                raise ValueError('PCM output path escapes run')
            path=root / label / name
            if path.stat().st_size != record['bytes']:
                raise ValueError('PCM output byte extent differs')
            require_hash(path,record['sha256'])
        s,blocks=verify_run(root / label,m['frames'],m['inputs'],selected,mode)
        expected_core=RUNTIME_PINS['gpgx.wbx.zst'] if label.startswith('official') else UNALTERED_CORE if label.startswith('source') else build['compressed_sha256']
        if s != m['summaries'][label] or s['core_sha256'] != expected_core:
            raise ValueError('PCM executed native core/summary differs')
        require_hash(root / 'build' / label / 'pcm_native.exe',m['executable_sha256'])
        require_hash(root / 'build' / label / 'dll/gpgx.wbx.zst',expected_core)
        for name,pin in RUNTIME_PINS.items():
            if name != 'gpgx.wbx.zst':
                require_hash(root / 'build' / label / 'dll' / name,pin)
        results[label]=blocks
    if m['controls_complete']:
        required={'official-1','official-2','source-1','source-2','disabled-1','disabled-2','enabled-1','enabled-2','overflow'}
        if set(m['modes']) != required:
            raise ValueError('PCM control inventory differs')
        for label in required:
            compare_runs(root / 'official-1', root / label)
        for group in [('official-1','official-2'),('source-1','source-2'),('disabled-1','disabled-2','enabled-1','enabled-2','overflow')]:
            reference=uncompress(root / group[0] / 'checkpoint.state.gz',16*1024*1024)
            if any(reference != uncompress(root / n / 'checkpoint.state.gz',16*1024*1024) for n in group[1:]):
                raise ValueError('PCM full unmasked same-binary checkpoint differs')
        for name in ['calls.bin.gz','forward-calls.bin.gz','replay-calls.bin.gz']+[f'block-{n:08d}.bin.gz' for n in m['selected']]:
            if (root / 'enabled-1' / name).read_bytes() != (root / 'enabled-2' / name).read_bytes():
                raise ValueError('PCM enabled repeated native bytes differ')
        if not all(m['negative_controls'].values()):
            raise ValueError('PCM real identity/layout negative controls missing')
    return results


if __name__ == '__main__':
    p=argparse.ArgumentParser(description=__doc__); p.add_argument('capture',type=Path)
    a=p.parse_args(); result=validate_capture(a.capture)
    print(json.dumps({'controls':'exact AV/public views/120-call restore; unmasked same-binary states',
                      'fixtures':len(result['enabled-1']), 'java_comparison':False},indent=2))
