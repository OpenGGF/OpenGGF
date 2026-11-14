#!/usr/bin/env python3
"""Check private owner-ledger ordering, native epoch arithmetic and evidence.

2026-10-23 CD-02. No global time fitting, guest inputs or trace V5 production.
"""
import argparse
from collections import Counter
import json
from pathlib import Path
import struct

from native_owner_schema import FIELDS, SIGNED, KINDS, DOMAINS

RECORD = struct.Struct('<64I')
EPOCH_AFTER = {4: 'scheduler_epoch', 6: 'cd_epoch', 8: 'main_epoch', 11: 'pcm_epoch',
               13: 'reset_epoch', 17: 'gfx_operation'}
PAIRS = {4: 3, 6: 5, 8: 7, 11: 10}


def records(path):
    with path.open('rb') as f:
        while data := f.read(RECORD.size):
            if len(data) != RECORD.size:
                raise ValueError('Truncated event ABI record')
            row = dict(zip(FIELDS, RECORD.unpack(data)))
            for name in SIGNED:
                if row[name] & 0x80000000:
                    row[name] -= 0x100000000
            row['sequence'] = row.pop('sequence_lo') | (row.pop('sequence_hi') << 32)
            yield row


def check_events(path, frames):
    counts, cpu_widths, first = Counter(), Counter(), {}
    epochs = {n: 0 for n in EPOCH_AFTER.values()}
    previous = None
    begins, ends = [], []
    gfx_jobs = {}
    writes = []
    for row in records(path):
        kind = row['kind']
        if kind not in KINDS or row['sequence'] != (previous['sequence'] + 1 if previous else 1):
            raise ValueError('Owner kind/sequence mismatch')
        if not 1 <= row['call'] <= frames or (previous and row['call'] < previous['call']):
            raise ValueError('Owner call ordering mismatch')
        if any(row[n] for n in FIELDS if n.startswith('reserved')):
            raise ValueError('Nonzero reserved ABI fields')
        if kind in (14, 15):
            if row['cpu'] not in (1, 2) or row['width'] not in (1, 2, 4):
                raise ValueError('CPU/width owner identity missing')
            cpu_widths[(row['cpu'], row['width'], KINDS[kind])] += 1
            access = tuple(row[n] for n in ['cpu', 'width', 'address', 'value'])
            if kind == 14:
                writes.append(access)
            elif not writes or writes.pop() != access:
                raise ValueError('Native write owner phases are not nested/preserved')
        elif row['cpu'] != 0:
            raise ValueError('Device event claims an unsupported caller CPU')
        if kind == 16:
            previous_mode, previous_dmna = row['aux1'] & 0xffff, row['aux1'] >> 16
            if not ((previous_mode ^ row['word_mode']) & 7 or previous_dmna != row['dmna']):
                raise ValueError('Word RAM label changed only nonownership bits')
            address, handler, width = row['address'], row['aux0'], row['width']
            # These events are actual byte/word handler legs, including
            # mirrored ports; a surrounding native long stays width 4 only
            # in its CPU access pair. Match the pinned CPU map and decoder.
            if width not in (1, 2) or not (
                    (handler == 1 and address & 0xffff00 == 0xa12000
                     and address & (0x3f if width == 1 else 0x3e) == (3 if width == 1 else 2))
                    or (handler == 2 and address & 0xf0000 == 0xf0000
                        and address & 0x8000 and address & 0x1fe == 2)):
                raise ValueError('Word RAM ownership label is outside its owning register handler')
        if kind in EPOCH_AFTER:
            epochs[EPOCH_AFTER[kind]] += 1
        if any(row[n] != value for n, value in epochs.items()):
            raise ValueError('Owner epoch transition mismatch')
        if kind in PAIRS:
            if previous is None or previous['kind'] != PAIRS[kind] or row['call'] != previous['call']:
                raise ValueError('Rebase/reset pair missing')
            if kind == 6:
                for name in ['s68k_cycles', 'gfx_cycles', 'cdc_cycle0', 'cdc_cycle1']:
                    if (row[name] - previous[name] + row['aux0']) & 0xffffffff:
                        raise ValueError('CD rebase arithmetic differs: ' + name)
            if kind == 8 and (row['m68k_cycles'] - previous['m68k_cycles'] + row['aux0']) & 0xffffffff:
                raise ValueError('Main rebase arithmetic differs')
            if kind == 4 and row['scd_cycles'] != 0 or kind == 11 and row['pcm_cycles'] != 0:
                raise ValueError('Native reset did not reach zero')
        if kind == 1:
            begins.append(row['call'])
        if kind == 2:
            ends.append(row['call'])
        if kind == 17:
            if not row['gfx58'] & 0x8000 or row['trace_offset'] != (row['gfx66'] << 2) & 0x3fff8:
                raise ValueError('Graphics start is not fully initialized')
            gfx_jobs[row['gfx_operation']] = {'start': row}
        if kind in (18, 19):
            if row['gfx58'] & 0x8000 or row['gfx64'] & 0xff:
                raise ValueError('Graphics completion phase has busy/remaining lines')
            job = gfx_jobs.get(row['gfx_operation'])
            if job is None:
                raise ValueError('Graphics completion has no observed launch')
            job['clear' if kind == 18 else 'rendered'] = row
            if kind == 19:
                clear = job.get('clear')
                if clear is None or clear['sequence'] >= row['sequence']:
                    raise ValueError('Graphics final rendering precedes busy clear')
        name = KINDS[kind]
        counts[name] += 1
        first.setdefault(name, row)
        previous = row
    if begins != list(range(1, frames + 1)) or ends != begins:
        raise ValueError('Incomplete native call envelope (possible overflow)')
    if writes:
        raise ValueError('Incomplete native write phase pair')
    if not counts:
        raise ValueError('Empty enabled ledger')
    title = next((v for v in gfx_jobs.values() if v['start']['gfx62'] & 0x1ff == 256
                  and v['start']['gfx64'] & 0xff == 96 and v['start']['trace_offset'] == 0x1c800), None)
    # This descriptor recognition is offline only. It never gates native code.
    return dict(events=sum(counts.values()), kinds=dict(counts),
                cpu_width_phase_counts={'/'.join(map(str, k)): v for k, v in sorted(cpu_widths.items())},
                epochs=epochs, first=first, graphics_operations=len(gfx_jobs), first_title_operation=title)


def analyze(run, frames):
    summary = json.loads((run / 'summary.json').read_text())
    if summary['overflow_fault'] or summary['overflow_dropped']:
        raise ValueError('Overflowed owner ledger cannot establish complete events')
    result = check_events(run / 'events.bin', frames)
    counts = [json.loads(line) for line in (run / 'counts.jsonl').read_text().splitlines()]
    if ([r['frame'] for r in counts] != list(range(1, frames + 1)) or any(r['fault'] or r['dropped'] for r in counts)
            or sum(r['events'] for r in counts) != result['events']):
        raise ValueError('Owner count/fault stream mismatch')
    if not summary['event_restore_equal'] or not summary['invalid_abi_calls_rejected']:
        raise ValueError('Owner restore/API controls failed')
    result.update(schema='soniccd.native-owner-evidence.v1', clock_domains=DOMAINS, frames=frames,
                  scope='private native model evidence; no physical-console/global-clock/V5 claim')
    (run / 'event-summary.json').write_text(json.dumps(result, indent=2) + '\n')
    return result


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--run', type=Path, required=True)
    p.add_argument('--frames', type=int, default=1800)
    args = p.parse_args()
    result = analyze(args.run, args.frames)
    print(json.dumps({k: result[k] for k in ['events', 'kinds', 'graphics_operations']}))


if __name__ == '__main__':
    main()
