#!/usr/bin/env python3
"""Validate completed owner-ledger controls from immutable producer snapshots.

CD-02 2026-10-23. A helper's subsequent local path edit cannot change a native
process already compiled from producer/. No raw state bytes are masked.
"""
import argparse
import json
from pathlib import Path

from native_owner_build import sha
from native_owner_run import compare, verify_run


def check_controls(root):
    labels = ['official-1', 'official-2', 'source-1', 'source-2']
    summaries = {n: json.loads((root / n / 'summary.json').read_text()) for n in labels}
    count = summaries['official-1']['frames']
    starts = summaries['official-1']['input_start_frames']
    for label in labels:
        verify_run(root / label, count, starts)
        compare(root / labels[0], root / label)
    state_repetition = {a.split('-')[0]: summaries[a]['checkpoint_sha256'] == summaries[b]['checkpoint_sha256']
                        for a, b in [('official-1', 'official-2'), ('source-1', 'source-2')]}
    producers = {p.name: sha(p) for p in (root / 'producer').iterdir() if p.is_file()}
    if not all(n in producers for n in ['oracle_probe.cs', 'native_owner_probe.cs', 'native_owner_run.py']):
        raise ValueError('Native compiled producer snapshot missing')
    binaries = {label: sha(root / 'build' / label / 'dll/gpgx.wbx.zst') for label in labels}
    if any(binaries[n] != summaries[n]['core_sha256'] for n in labels):
        raise ValueError('Executed core identity differs')
    return dict(schema='soniccd.native-owner-verified-controls.v1', frames=count,
                start_frames=starts, summaries=summaries, producer_sha256=producers,
                core_sha256=binaries, all_av_ram_register_restore_controls_equal=True,
                cold_checkpoint_raw_hashes_equal=state_repetition,
                cross_binary_raw_states_compared=False,
                note='Independent validation of completed native outputs and archived compiled producers; no state masks.')


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--controls', type=Path, required=True)
    args = p.parse_args()
    result = check_controls(args.controls)
    target = args.controls / 'verified-controls.json'
    target.write_text(json.dumps(result, indent=2) + '\n')
    print(target)


if __name__ == '__main__':
    main()
