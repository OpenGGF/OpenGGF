#!/usr/bin/env python3
"""Read-only CD-06 Sound Test route evidence checks (2026-10-23).

Inputs: a frozen pcm_native_run.py nine-control capture and its pinned USA data
track. Origin: bounded native-input Sound Test research at OpenGGF 5c0f2eae2.
No emulation, controller generation, state loading, writes, or output synthesis.
JSON stdout adds active continuation/block and retail-program identity evidence
to the frozen capture validator. Commercial payloads stay external.
"""
import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path

from pcm_native_check import (BLOCK_BYTES, decode_block, summaries, uncompress,
                              validate_capture)
from pcm_native_common import sha
from pcm_native_run import parse_inputs
from stage_inventory import RawIso
import stage_inventory

INPUTS = ('600:Start,900:Start,1200:Start,1800-1801:Down,1810-1811:Down,'
          '1820-1821:Down,1830-1831:Left,1840-1841:Right,1850-1851:A,'
          '2100-2107:Right,2140-2219:Down,2250-2251:A')
SELECTED = [7605, 11459, 13341, 19938, 44351, 62330, 89261, 92597]
SOSEL_SHA256 = '27dfb7822347e8c07a1645039f76c4094209f5542ca171603ba72bda1aa35eaf'


def require(condition, message):
    if not condition:
        raise ValueError(message)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def program_identity(run, mmd):
    """Locate RAM by its complete public-domain hash, not an assumed state offset.

    Native storage is word-swapped. Exact code windows then tie the observed PC
    and loaded start to the retail file. Mutable program variables are not used
    as a code signature. No state bytes are changed or loaded into a core.
    """
    info = json.loads((run / 'summary.json').read_text())['checkpoint_view']
    checkpoint = uncompress(run / 'checkpoint.state.gz', 16 * 1024 * 1024)
    origin = int.from_bytes(mmd[2:6], 'big')
    prefix = mmd[256:384]
    swapped = bytearray(prefix)
    swapped[0::2], swapped[1::2] = prefix[1::2], prefix[0::2]
    matches, offset = [], 0
    while True:
        hit = checkpoint.find(swapped, offset)
        if hit < 0:
            break
        offset = hit + 1
        base = hit - (origin & 65535)
        raw = checkpoint[base:base + 65536] if base >= 0 else b''
        if len(raw) != 65536:
            continue
        ram = bytearray(raw)
        ram[0::2], ram[1::2] = raw[1::2], raw[0::2]
        if digest(ram) == info['domains']['68K RAM']:
            matches.append((base, bytes(ram)))
    require(len(matches) == 1, 'Cannot uniquely bind complete checkpoint 68K RAM')
    base, ram = matches[0]
    pc = info['registers']['M68K PC']
    require(origin <= pc < origin + len(mmd) - 256 - 32,
            'Checkpoint PC is outside retail Sound Test program')
    code = mmd[256 + pc - origin:256 + pc - origin + 32]
    require(ram[pc & 65535:(pc & 65535) + 32] == code,
            'Checkpoint instruction window differs from retail Sound Test')
    return dict(origin=origin, pc=pc, ram_state_offset=base,
                complete_ram_sha256=digest(ram), start_window_bytes=128,
                start_window_sha256=digest(prefix), pc_window_bytes=32,
                pc_window_sha256=digest(code), checkpoint_sha256=digest(checkpoint))


def stream_facts(rows):
    active = [r for r in rows if r['enabled'] and r['status']]
    return dict(calls=len(rows), active_calls=len(active),
                nonzero_calls=sum(bool(r['nonzero_samples']) for r in rows),
                nonzero_samples=sum(r['nonzero_samples'] for r in rows),
                loop_markers=sum(r['loop_markers'] for r in rows),
                active_call_labels=sorted({r['call'] for r in active}),
                masks=dict(sorted(Counter(r['status'] for r in active).items())),
                max_length=max((r['length'] for r in rows), default=0))


def block_facts(path):
    payload = uncompress(path, BLOCK_BYTES)
    b = decode_block(payload)
    require(b['pre']['enabled'] and b['pre']['status'] and
            any(l or r for _, l, r, *_ in b['raw']), 'Selected block is inactive/silent')
    channels = [dict(channel=i, cursor=c[0], start=c[1], loop=c[2],
                     frequency=c[3], env=c[4], pan=c[5])
                for i, c in enumerate(b['pre']['channels'])
                if b['pre']['status'] & (1 << i)]
    return dict(file=path.name, compressed_sha256=sha(path),
                full_payload_sha256=digest(payload), ordinal=b['ordinal'],
                call=b['call'], length=b['length'], mask=b['pre']['status'],
                nonzero_samples=sum(bool(v[1] or v[2]) for v in b['raw']),
                loops=[dict(sample=v[0], mask=v[6]) for v in b['raw'] if v[6]],
                clipped_samples=sum(abs(v[1]) == 32768 or v[1] == 32767 or
                                    abs(v[2]) == 32768 or v[2] == 32767 for v in b['raw']),
                active_channels=channels, pre=b['pre'], post=b['post'])


def check(capture, track):
    blocks = validate_capture(capture)
    m = json.loads((capture / 'manifest.json').read_text())
    require(m['frames'] == 3000 and m['inputs'] == parse_inputs(INPUTS, 3000) and
            m['selected'] == SELECTED, 'Different route/interval/ordinal selection')
    record = m['media']['files'][0]
    require(track.name == record['file'] and track.stat().st_size == record['bytes']
            and sha(track) == record['sha256'], 'Data track is not capture-pinned USA media')
    with RawIso(track) as disc:
        mmd = disc.file('SOSEL_.MMD;1')
        iso_record = disc.files['SOSEL_.MMD;1']
    require(digest(mmd) == SOSEL_SHA256, 'Different retail Sound Test program')
    identities = {label: program_identity(capture / label, mmd) for label in m['modes']}
    enabled = capture / 'enabled-1'
    rows = summaries(uncompress(enabled / 'calls.bin.gz', 3600 * 4096 * 64), 3000)
    music = [r for r in rows if r['call'] > 2251 and r['enabled'] and r['status']]
    require(music and music[-1]['call'] == 3000, 'No active music checkpoint')
    main = [block_facts(enabled / f'block-{n:08d}.bin.gz') for n in SELECTED]
    require(all(b['call'] >= music[0]['call'] for b in main), 'Startup block selected')
    continuation = summaries(uncompress(enabled / 'forward-calls.bin.gz', 120 * 4096 * 64), 120)
    facts = stream_facts(continuation)
    require(facts['active_call_labels'] == list(range(1, 121)) and
            facts['active_calls'] == facts['calls'], 'Continuation is not active throughout')
    expected = [n for n in SELECTED if n <= len(continuation)]
    require(expected, 'No captured continuation blocks')
    for label in ['enabled-1', 'enabled-2']:
        for prefix in ['forward-', 'replay-']:
            require(sorted(p.name for p in (capture / label).glob(prefix + 'block-*.bin.gz')) ==
                    [prefix + f'block-{n:08d}.bin.gz' for n in expected],
                    'Incomplete/extra continuation block inventory')
    restored = []
    for n in expected:
        name = f'block-{n:08d}.bin.gz'
        reference = enabled / ('forward-' + name)
        fact = block_facts(reference)
        for label in ['enabled-1', 'enabled-2']:
            for prefix in ['forward-', 'replay-']:
                path = capture / label / (prefix + name)
                require(path.read_bytes() == reference.read_bytes(),
                        'Repeated/restored complete active block differs')
        row = continuation[n - 1]
        require((fact['ordinal'], fact['call'], fact['length'], fact['mask'], fact['nonzero_samples']) ==
                (n, row['call'], row['length'], row['status'], row['nonzero_samples']),
                'Continuation block differs from integer summary stream')
        require(sum(v['mask'].bit_count() for v in fact['loops']) == row['loop_markers'],
                'Continuation block loop markers differ from integer summary stream')
        restored.append(fact)
    require(len(blocks['enabled-1']) == 8, 'Incomplete main fixture set')
    return dict(schema='soniccd.pcm-soundtest-route-evidence.v1',
                capture=str(capture), manifest_sha256=sha(capture / 'manifest.json'),
                research_checker_sha256=sha(Path(__file__)),
                raw_iso_reader_sha256=sha(Path(stage_inventory.__file__)),
                route_inputs=INPUTS, selected_ordinals=SELECTED, frames=3000,
                total_frame_advance_calls_per_successful_process=3240,
                classification='retail Sound Test music preview; no in-level/full-game claim',
                source_program=dict(file='SOSEL_.MMD;1', sha256=digest(mmd),
                                    iso_record=iso_record, checkpoints=identities),
                full_stream=stream_facts(rows), music_stream=stream_facts(music),
                active_restore_120=facts, main_blocks=main, restored_blocks=restored,
                controls=list(m['modes']), negative_controls=m['negative_controls'])


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('capture', type=Path)
    parser.add_argument('--data-track', type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(check(args.capture.resolve(), args.data_track.resolve()), indent=2))
