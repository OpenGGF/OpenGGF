#!/usr/bin/env python3
"""CD-06 2026-10-23: derive a new observer patch from unaltered pinned sources.

Only reads the complete source and new pcm_native_observer files. Never reads
or modifies the independently reviewed owner-ledger patch or installed core.
"""
import argparse
import difflib
import hashlib
from pathlib import Path

PCM = 'waterbox/gpgx/Genesis-Plus-GX/core/cd_hw/pcm.c'
PCM_SHA256 = '64a657104fb7f958601c75587de37cf81ccf1f5d51d7d54f3138462a474ba3a6'


def generate(root):
    files, originals = {}, {}

    def replace(name, old, new):
        if name not in files:
            originals[name] = (root / name).read_bytes().decode()
            files[name] = originals[name].replace('\r\n', '\n')
        if files[name].count(old) != 1:
            raise ValueError('Source hook cardinality: ' + name + ': ' + repr(old[:60]))
        files[name] = files[name].replace(old, new)

    if hashlib.sha256((root / PCM).read_bytes()).hexdigest() != PCM_SHA256:
        raise ValueError('Unaltered pinned pcm.c required')
    replace('waterbox/gpgx/Makefile', '\tcinterface/cinterface.c \\\n',
            '\tcinterface/cinterface.c \\\n\tcinterface/pcm_native_observer.c \\\n')
    interface = 'waterbox/gpgx/cinterface/cinterface.c'
    replace(interface, '#include <shared.h>', '#include <shared.h>\n#include "pcm_native_observer.h"')
    replace(interface, 'GPGX_EX void gpgx_advance(void)\n{',
            'GPGX_EX void gpgx_advance(void)\n{\n\tpcm_observe_frame();')
    replace(PCM, '#include "shared.h"', '#include "shared.h"\n#include "pcm_native_observer.h"')
    replace(PCM, 'void pcm_run(unsigned int length)\n{',
            'void pcm_run(unsigned int length)\n{\n  pcm_observe_begin(length);')
    replace(PCM, '            /* reset WAVE RAM address */',
            '            pcm_observe_loop(j);\n            /* reset WAVE RAM address */')
    replace(PCM, '      /* PCM output mixing level (0-100%) */',
            '      pcm_observe_sample(i, l, r, prev_l, prev_r);\n\n      /* PCM output mixing level (0-100%) */')
    replace(PCM, '    /* check if PCM output was not muted */',
            '    pcm_observe_disabled(length, prev_l, prev_r);\n    /* check if PCM output was not muted */')
    replace(PCM, '  pcm.cycles += length * PCM_SCYCLES_RATIO;',
            '  pcm.cycles += length * PCM_SCYCLES_RATIO;\n  pcm_observe_end();')
    for name in ['pcm_native_observer.c', 'pcm_native_observer.h']:
        path = 'waterbox/gpgx/cinterface/' + name
        originals[path] = ''
        files[path] = Path(__file__).with_name(name).read_text()
    return ''.join(''.join(difflib.unified_diff(originals[n].splitlines(True), files[n].splitlines(True),
                   fromfile='a/' + n, tofile='b/' + n)) for n in sorted(files))


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--source', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    a = p.parse_args()
    if a.output.exists():
        p.error('Require new patch output')
    a.output.write_bytes(generate(a.source).encode())
