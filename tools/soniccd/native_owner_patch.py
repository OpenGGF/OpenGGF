#!/usr/bin/env python3
"""Generate the private owner-only patch against exact CD-02 pinned sources.

2026-10-23: reads the full source tree, writes one new native_owner_*.patch.
No source checkout is modified. Replacements fail closed on source changes.
"""
import argparse
import difflib
from pathlib import Path
import re


def generate(root):
    files, originals = {}, {}
    core = 'waterbox/gpgx/Genesis-Plus-GX/core/'

    def read(name):
        if name not in files:
            data = (root / name).read_bytes().decode()
            originals[name] = data
            files[name] = data.replace('\r\n', '\n')
        return files[name]

    def change(name, old, new, count=1):
        data = read(name)
        if data.count(old) != count:
            raise ValueError(f'Source hook cardinality: {name}: {old[:90]!r}: {data.count(old)} != {count}')
        files[name] = data.replace(old, new)

    def include(name):
        change(name, '#include "shared.h"', '#include "shared.h"\n#include "native_owner_ledger.h"')

    def wrap(name, signature, call, before, after):
        # Header/public declaration remains unchanged. The real owner body
        # runs once, with the same arguments. Every early return is enclosed.
        typename, function = signature.split('(', 1)[0].rsplit(' ', 1)
        implementation = function + '_owner_original'
        impl_signature = signature.replace(function + '(', implementation + '(', 1)
        if not impl_signature.startswith('static '):
            impl_signature = 'static ' + impl_signature
        change(name, signature + '\n{', signature + ';\n' + impl_signature + '\n{')
        files[name] += '\n' + signature + '\n{\n' + before + '\n  ' + implementation + '(' + call + ');\n' + after + '\n}\n'

    makefile = 'waterbox/gpgx/Makefile'
    change(makefile, '\tcinterface/cinterface.c \\\n', '\tcinterface/cinterface.c \\\n\tcinterface/native_owner_ledger.c \\\n')
    # New header is placed in the already existing -Icinterface include root.
    for name in ['native_owner_ledger.c', 'native_owner_ledger.h']:
        dest = 'waterbox/gpgx/cinterface/' + name
        files[dest] = Path(__file__).with_name(name).read_text()
        originals[dest] = ''

    interface = 'waterbox/gpgx/cinterface/cinterface.c'
    change(interface, '#include <shared.h>', '#include <shared.h>\n#include "native_owner_ledger.h"')
    change(interface, 'GPGX_EX void gpgx_advance(void)\n{',
           'GPGX_EX void gpgx_advance(void)\n{\n\tnative_owner_mark(OWNER_FRAME_BEGIN, 0);')
    change(interface, '\tnsamples = audio_update(soundbuffer);',
           '\tnsamples = audio_update(soundbuffer);\n\tnative_owner_mark(OWNER_FRAME_END, nsamples);')

    for name, cpu in [('m68kcpu.c', 1), ('s68kcpu.c', 2)]:
        path = core + 'm68k/' + name
        change(path, '#include "m68kcpu.h"', f'#define NATIVE_OWNER_CPU {cpu}\n#include "m68kcpu.h"')
    path = core + 'm68k/m68kcpu.h'
    change(path, '/* ------------------------- Top level read/write ------------------------- */',
           '#include "native_owner_ledger.h"\n\n/* ------------------------- Top level read/write ------------------------- */')
    for bits, width in [(8, 1), (16, 2), (32, 4)]:
        signature = f'INLINE void m68ki_write_{bits}(uint address, uint value)'
        data = read(path)
        begin = data.index(signature + '\n{')
        end = data.index('\n}\n', begin) + 2
        body = data[begin:end]
        position = body.index('  temp = &m68ki_cpu.memory_map[')
        body = body[:position] + f'  native_owner_access(OWNER_WRITE_BEFORE, NATIVE_OWNER_CPU, {width}, address, value, m68ki_cpu.cycles);\n' + body[position:]
        body = body[:-1] + f'  native_owner_access(OWNER_WRITE_AFTER, NATIVE_OWNER_CPU, {width}, address, value, m68ki_cpu.cycles);\n' + body[-1:]
        files[path] = data[:begin] + body + data[end:]

    path = core + 'system.c'
    include(path)
    change(path, '  scd.cycles = 0;', '  native_owner_mark(OWNER_SCHEDULER_BEFORE, 0);\n  scd.cycles = 0;\n  native_owner_mark(OWNER_SCHEDULER_AFTER, 0);')
    # This exact SCD end-of-frame sequence appears only in system_frame_scd.
    change(path, '  scd_end_frame(scd.cycles);\n  input_end_frame(mcycles_vdp);\n  m68k.refresh_cycles -= mcycles_vdp;\n  m68k.cycles -= mcycles_vdp;',
           '  scd_end_frame(scd.cycles);\n  input_end_frame(mcycles_vdp);\n  native_owner_mark(OWNER_MAIN_BEFORE, mcycles_vdp);\n  m68k.refresh_cycles -= mcycles_vdp;\n  m68k.cycles -= mcycles_vdp;\n  native_owner_mark(OWNER_MAIN_AFTER, mcycles_vdp);')

    path = core + 'cd_hw/gfx.c'
    include(path)
    change(path, '  scd.regs[0x58>>1].byte.h = 0x80;',
           '  scd.regs[0x58>>1].byte.h = 0x80;\n  native_owner_mark(OWNER_GFX_START, cycles);')
    change(path, 'void gfx_update(int cycles)\n{', 'void gfx_update(int cycles)\n{\n  int owner_final_lines = 0;')
    change(path, '        scd.regs[0x58>>1].byte.h = 0;',
           '        scd.regs[0x58>>1].byte.h = 0;\n        owner_final_lines = 1;\n        native_owner_mark(OWNER_GFX_BUSY_CLEAR, cycles);')
    change(path, '        gfx.bufferStart += 8;\n      }',
           '        gfx.bufferStart += 8;\n      }\n      if (owner_final_lines) native_owner_mark(OWNER_GFX_RENDERED, cycles);')

    path = core + 'cd_hw/scd.c'
    include(path)
    wrap(path, 'void scd_reset(int hard)', 'hard',
         '  native_owner_mark(OWNER_RESET_BEFORE, hard);', '  native_owner_mark(OWNER_RESET_AFTER, hard);')
    for suffix, width in [('byte', 1), ('word', 2)]:
        wrap(path, f'static void scd_write_{suffix}(unsigned int address, unsigned int data)', 'address, data',
             '  unsigned int mode = scd.regs[0x02>>1].w, dmna = scd.dmna;',
             f'  native_owner_wordram(2, {width}, address, data, mode, dmna);')
    change(path, '      cdd_update();', '      native_owner_mark(OWNER_CDD_BEFORE, s68k_run_cycles);\n      cdd_update();\n      native_owner_mark(OWNER_CDD_AFTER, s68k_run_cycles);')
    change(path, '      if (scd.timer <= 0)\n      {', '      if (scd.timer <= 0)\n      {\n        native_owner_mark(OWNER_TIMER_EXPIRED, s68k_run_cycles);')
    change(path, '          scd.pending |= (1 << 3);', '          scd.pending |= (1 << 3);\n          native_owner_mark(OWNER_TIMER_PENDING, 3);')
    change(path, 'void scd_end_frame(unsigned int cycles)\n{', 'void scd_end_frame(unsigned int cycles)\n{\n  native_owner_mark(OWNER_CD_BEFORE, cycles);')
    change(path, '  cdc.cycles[1] -= cycles;', '  cdc.cycles[1] -= cycles;\n  native_owner_mark(OWNER_CD_AFTER, cycles);')
    wrap(path, 'int scd_68k_irq_ack(int level)', 'level',
         '  int result;\n  native_owner_mark(OWNER_IRQ_BEFORE, level);',
         '  native_owner_mark(OWNER_IRQ_AFTER, level);\n  return result;')
    files[path] = files[path].replace('  scd_68k_irq_ack_owner_original(level);', '  result = scd_68k_irq_ack_owner_original(level);')

    path = core + 'mem68k.c'
    include(path)
    for suffix, width in [('byte', 1), ('word', 2)]:
        wrap(path, f'void ctrl_io_write_{suffix}(unsigned int address, unsigned int data)', 'address, data',
             '  unsigned int mode = scd.regs[0x02>>1].w, dmna = scd.dmna;',
             f'  native_owner_wordram(1, {width}, address, data, mode, dmna);')

    path = core + 'cd_hw/pcm.c'
    include(path)
    change(path, 'void pcm_update(unsigned int samples)\n{', 'void pcm_update(unsigned int samples)\n{\n  native_owner_mark(OWNER_PCM_BEGIN, samples);')
    change(path, '  /* reset PCM master clocks counter */\n  pcm.cycles = 0;',
           '  /* reset PCM master clocks counter */\n  native_owner_mark(OWNER_PCM_BEFORE, samples);\n  pcm.cycles = 0;\n  native_owner_mark(OWNER_PCM_AFTER, samples);')

    path = core + 'cd_hw/cdd.c'
    include(path)
    wrap(path, 'void cdd_process(void)', '', '  native_owner_mark(OWNER_COMMAND_BEFORE, 0);',
         '  native_owner_mark(OWNER_COMMAND_AFTER, 0);')
    change(path, '    cdd.latency--;', '    cdd.latency--;\n    if (!cdd.latency) native_owner_mark(OWNER_LATENCY_EXHAUSTED, 0);')
    change(path, '    cdd.pending = 0;', '    cdd.pending = 0;\n    native_owner_mark(OWNER_SEEK_APPLIED, 0);')

    path = core + 'cd_hw/cdc.c'
    include(path)
    wrap(path, 'void cdc_dma_init(void)', '', '  native_owner_mark(OWNER_CDC_TRIGGER_BEFORE, 0);',
         '  native_owner_mark(OWNER_CDC_TRIGGER_AFTER, 0);')
    change(path, '    /* disable DMA */\n    cdc.dma_w = cdc.halted_dma_w = 0;',
           '    /* disable DMA */\n    cdc.dma_w = cdc.halted_dma_w = 0;\n    native_owner_mark(OWNER_CDC_COMPLETE, cycles);')

    patch = []
    for name in sorted(files):
        old = originals[name]
        new = files[name].replace('\n', '\r\n') if '\r\n' in old else files[name]
        patch.extend(difflib.unified_diff(old.splitlines(keepends=True), new.splitlines(keepends=True),
                                        fromfile='a/' + name, tofile='b/' + name))
    return ''.join(patch)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--source', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    args = p.parse_args()
    if not args.output.name.startswith('native_owner_') or args.output.suffix != '.patch':
        p.error('Require a native_owner_*.patch output')
    args.output.write_bytes(generate(args.source).encode())
    print(args.output)


if __name__ == '__main__':
    main()
