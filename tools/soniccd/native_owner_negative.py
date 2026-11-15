#!/usr/bin/env python3
"""Actual native identity/layout/overflow rejection controls, CD-02 2026-10-23.

Uses a completed owner-controls producer/runtime, with caller-owned media.
No installed file is replaced. Overflow continues emulation and is rejected
as evidence; its AV and unmasked checkpoint are compared to disabled capture.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess

from native_owner_analyze import analyze
from native_owner_build import ROOT, fresh, sha
from native_owner_run import verify_run, compare


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--controls', type=Path, required=True)
    p.add_argument('--reference-controls', type=Path, required=True)
    p.add_argument('--cue', type=Path, required=True)
    p.add_argument('--firmware-directory', type=Path, required=True)
    p.add_argument('--bios-sha1', required=True)
    p.add_argument('--output', type=Path, required=True)
    args = p.parse_args()
    output = fresh(args.output)
    build = args.controls / 'build/enabled-1'
    exe = build / 'native_owner.exe'
    manifest = json.loads((args.controls / 'manifest.json').read_text())
    for name in ['oracle_probe.cs', 'native_owner_probe.cs']:
        if sha(args.controls / 'producer' / name) != manifest['producer_sha256'][name]:
            raise ValueError('Archived native producer differs')
    actual = sha(build / 'dll/gpgx.wbx.zst')
    if actual != manifest['instrumented_build_identity']['compressed_sha256']:
        raise ValueError('Instrumented core fingerprint differs')
    env = os.environ.copy()
    for key in ['DISPLAY', 'WAYLAND_DISPLAY']:
        env.pop(key, None)
    env.update(MONO_PATH=str(build / 'dll'), LD_LIBRARY_PATH=str(build / 'dll'), TMPDIR=str(output))
    results = {}
    for label, mode, identity, expected in [
        ('wrong-identity', 'enabled', '0' * 64, 'Native owner runtime identity mismatch'),
        ('wrong-layout', 'bad-layout', actual, 'Native owner ABI/layout mismatch before polling'),
        ('disabled', 'disabled', actual, None),
        ('overflow', 'overflow', actual, None),
    ]:
        command = ['mono', str(exe), str(args.cue.resolve()), str(args.firmware_directory.resolve()),
                   str(output / label), '60', '', args.bios_sha1, identity, mode]
        with (output / (label + '.log')).open('w') as f:
            result = subprocess.run(command, env=env, stdout=f, stderr=subprocess.STDOUT, timeout=90)
        if expected:
            if result.returncode == 0 or (output / label).exists() or expected not in (output / (label + '.log')).read_text():
                raise ValueError('Native negative control failed: ' + label)
            results[label] = dict(exit=result.returncode, no_output_created=True, required_error=expected)
        else:
            if result.returncode != 0:
                raise ValueError('Native control process failed: ' + label)
            results[label] = verify_run(output / label, 60, [])
    compare(output / 'disabled', output / 'overflow')
    if (output / 'disabled/checkpoint.state').read_bytes() != (output / 'overflow/checkpoint.state').read_bytes():
        raise ValueError('Overflow changed unmasked savestate bytes')
    # Same released-input boot: check actual AV against the independent official
    # run's calls 1..60 and 61..180, not against a guessed expected output hash.
    reference = [json.loads(s) for s in (args.reference_controls / 'official-1/av.jsonl').read_text().splitlines()]
    for label in ['disabled', 'overflow']:
        capture = [json.loads(s) for s in (output / label / 'av.jsonl').read_text().splitlines()]
        continued = [json.loads(s) for s in (output / label / 'continuation.jsonl').read_text().splitlines()]
        if capture != reference[:60] or continued != reference[60:180]:
            raise ValueError('Negative native process changed official boot AV')
    try:
        analyze(output / 'overflow', 60)
    except ValueError as e:
        if 'Overflowed' not in str(e):
            raise
        results['overflow_evidence_rejected'] = str(e)
    else:
        raise ValueError('Overflow was accepted as complete evidence')
    results.update(schema='soniccd.native-owner-negative-controls.v1',
                   observer_excluded_by_unmasked_state_equality=True, official_av_equal=True,
                   exe_sha256=sha(exe), core_sha256=actual,
                   producer_sha256=manifest['producer_sha256'], command_recipe_sha256=sha(__file__))
    (output / 'summary.json').write_text(json.dumps(results, indent=2) + '\n')
    print(output / 'summary.json')


if __name__ == '__main__':
    main()
