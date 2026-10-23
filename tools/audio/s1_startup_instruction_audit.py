#!/usr/bin/env python3
"""Read-only, INCOMPLETE S1 REV01 startup instruction-cost diagnosis.

Input: the caller's original S1 World REV01 ROM, identified before analysis.
Uses the existing Motorola-table decoder in tools/timing/m68k-closure, with
explicit operand shapes, unsigned counters, and observed checksum branch outcome.
No emulator, gameplay trace, state injection, frame conversion, or runtime input
is involved. Valid pinned input exits 2 (INCOMPLETE); invalid input exits 3 (ERROR).
No output file is created. Origin: OpenGGF audio-evidence startup diagnosis,
2026-10-22.

Reference labels: EntryPoint / ClrRAMLoop / SetupValues and CheckSumCheck /
CheckSumOk / GameInit in s1disasm/sonic.asm; PlaySegaSound and its busyloop in
s1disasm/s1.sounddriver.asm. Shipped REV01 / FixBugs=0 is the only CLI identity.
Disassemblies explain the patterns; all analysed instructions and operands come
from the supplied ROM. These are Motorola MC68000 instruction-only costs without
wait states, never authoritative whole-startup timing or publication evidence.
"""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import sys


ROM_SHA1 = '69e102855d4389c3fd1a8f3dc7d193f8eee5fe5b'
ROM_SIZE = 0x80000
SCHEMA = 'openggf.s1-startup-instruction-audit.v1'
MISSING_TERMS = [
    'reset/exception entry and authentic power-on state',
    'bus arbitration and ROM/RAM/Z80 access wait states',
    'VDP FIFO, DMA, register access and display synchronization',
    'DRAM refresh, master-clock phase and clock-domain conversion',
    'interrupt entry/exit and intervening interrupt execution',
    'uncovered startup, prior fade, asset loading and palette work',
    'authentic endpoints and native/production boundary alignment',
    'Z80/DAC execution and final PCM transport/output behavior',
]


def _load_timing():
    path = Path(__file__).resolve().parents[1] / 'timing' / 'm68k-closure' / 'm68k_timing.py'
    name = '_openggf_s1_startup_m68k_timing'
    if name not in sys.modules:
        spec = importlib.util.spec_from_file_location(name, path)
        if spec is None or spec.loader is None:
            raise ValueError('the source-owned Motorola instruction decoder is unavailable')
        module = importlib.util.module_from_spec(spec)
        sys.modules[name] = module
        try:
            spec.loader.exec_module(module)
        except Exception:
            del sys.modules[name]
            raise
    return sys.modules[name]


class AuditError(ValueError):
    """Identity, shape, operand, bounds, or exact instruction-cost failure."""


def unsigned(value, bits, label):
    if type(value) is not int or not 0 <= value < (1 << bits):
        raise AuditError(f'{label} must be an unsigned {bits}-bit integer')
    return value


def _word(rom, address):
    unsigned(address, 32, 'ROM address')
    if address & 1 or address + 2 > len(rom):
        raise AuditError(f'word at {address:#x} is unaligned or outside the supplied ROM')
    return (rom[address] << 8) | rom[address + 1]


def _long(rom, address):
    return (_word(rom, address) << 16) | _word(rom, address + 2)


def _words(rom, address, expected):
    for index, word in enumerate(expected):
        actual = _word(rom, address + index * 2)
        if actual != word:
            raise AuditError(f'instruction/operand shape differs at {address + index * 2:#x}: '
                             f'expected {word:04x}, found {actual:04x}')


def _instruction(rom, pc, opcode, name, size, length=2, *, src='', dst='',
                 kind='seq', target=None, absolute=None):
    _words(rom, pc, (opcode,))
    timing = _load_timing()
    try:
        instruction = timing.Decoder(rom).decode(pc)
    except timing.Unsupported as error:
        raise AuditError(f'unsupported instruction at {pc:#x}: {error}') from error
    # Some decoder forms calculate extension length without reading the operand.
    # A timing shape alone cannot certify a complete instruction in this ROM.
    _word(rom, pc + instruction.length - 2)
    actual = (instruction.name, instruction.size, instruction.length,
              instruction.src, instruction.dst, instruction.kind, instruction.target)
    expected = (name, size, length, src, dst, kind, target)
    if actual != expected or (absolute is not None and instruction.abs_addrs != absolute):
        raise AuditError(f'decoded instruction shape differs at {pc:#x}')
    return instruction


def _exact(cost, pc):
    if cost is None or not cost.exact() or type(cost.lo) is not int or cost.lo < 0:
        raise AuditError(f'instruction cost at {pc:#x} is not exact')
    return cost.lo


def _term(instruction, description, count=1, *, taken=0, fall=0):
    unsigned(count, 64, 'execution count')
    unsigned(taken, 64, 'taken count')
    unsigned(fall, 64, 'fall-through count')
    result = {'pc': f'0x{instruction.pc:06x}', 'instruction': description,
              'executions': count}
    if instruction.kind in ('cond', 'dbcc'):
        if count != taken + fall:
            raise AuditError('branch outcome counts do not equal execution count')
        taken_cycles = _exact(instruction.taken, instruction.pc)
        fall_cycles = _exact(instruction.fall, instruction.pc)
        result.update(taken=taken, fall_through=fall,
                      taken_cycles=taken_cycles, fall_through_cycles=fall_cycles,
                      instruction_only_cycles=taken * taken_cycles + fall * fall_cycles)
    else:
        if taken or fall:
            raise AuditError('nonbranch instruction has branch outcome counts')
        cycles = _exact(instruction.cost, instruction.pc)
        result.update(cycles_per_execution=cycles, instruction_only_cycles=count * cycles)
    return result


def dbf_outcomes(initial_word):
    """DBF tests the decremented low word against FFFF; FFFF starts 65536 bodies."""
    initial_word = unsigned(initial_word, 16, 'DBF initial low word')
    iterations = initial_word + 1
    return {'iterations': iterations, 'taken': iterations - 1, 'fall_through': 1,
            'terminal_low_word': 0xFFFF}


def nested_dbf_outcomes(outer_word, inner_word):
    outer = dbf_outcomes(outer_word)
    inner = dbf_outcomes(inner_word)
    return {'outer': outer, 'inner_per_outer': inner,
            'body_executions': outer['iterations'] * inner['iterations'],
            'inner_taken': outer['iterations'] * inner['taken'],
            'inner_fall_through': outer['iterations']}


def checksum_words(rom, start, inclusive_end):
    """The postincrement ADD reads whole words through the inclusive ROM-end byte."""
    unsigned(start, 32, 'checksum start')
    unsigned(inclusive_end, 32, 'checksum inclusive end')
    if start & 1 or not inclusive_end & 1 or inclusive_end < start or inclusive_end >= len(rom):
        raise AuditError('checksum span is unaligned, reversed, or outside the supplied ROM')
    count = (inclusive_end + 1 - start) // 2
    checksum = sum((rom[pc] << 8) | rom[pc + 1]
                   for pc in range(start, inclusive_end + 1, 2)) & 0xFFFF
    return {'iterations': count, 'checksum_u16': checksum,
            'terminal_a0': inclusive_end + 1}


def _component(name, terms, facts, *, coverage):
    return {'name': name, 'coverage': coverage, 'facts': facts, 'instructions': terms,
            'instruction_only_cycles': sum(term['instruction_only_cycles'] for term in terms)}


def checksum_component(rom):
    # CheckSumCheck (031C..033D): CMP.L a0,d0; BHS is unsigned BCC.
    _words(rom, 0x31C, (0x207C, 0, 0x0200, 0x227C, 0, 0x01A4))
    _words(rom, 0x332, (0x227C, 0, 0x018E))
    if _long(rom, 0x1A0) != 0:
        raise AuditError('the ROM header start is not zero')
    start = _long(rom, 0x31E)
    end_pointer = _long(rom, 0x324)
    inclusive_end = _long(rom, end_pointer)
    if inclusive_end != len(rom) - 1:
        raise AuditError('the ROM header inclusive end does not identify the actual final byte')
    span = checksum_words(rom, start, inclusive_end)
    stored = _word(rom, _long(rom, 0x334))
    if span['checksum_u16'] != stored:
        raise AuditError('computed checksum does not match the actual header checksum')
    count = span['iterations']
    terms = [
        _term(_instruction(rom, 0x31C, 0x207C, 'movea', 'l', 6, src='#', dst='An'),
              'movea.l #$200,a0'),
        _term(_instruction(rom, 0x322, 0x227C, 'movea', 'l', 6, src='#', dst='An'),
              'movea.l #$1a4,a1'),
        _term(_instruction(rom, 0x328, 0x2011, 'move', 'l', src='(An)', dst='Dn'),
              'move.l (a1),d0'),
        _term(_instruction(rom, 0x32A, 0x7200, 'moveq', 'l'), 'moveq #0,d1'),
        _term(_instruction(rom, 0x32C, 0xD258, 'add', 'w', src='(An)+', dst='Dn'),
              'add.w (a0)+,d1', count),
        _term(_instruction(rom, 0x32E, 0xB088, 'cmp', 'l', src='An', dst='Dn'),
              'cmp.l a0,d0', count),
        _term(_instruction(rom, 0x330, 0x64FA, 'bcc', 's', kind='cond', target=0x32C),
              'bhs.s $32c (unsigned)', count, taken=count - 1, fall=1),
        _term(_instruction(rom, 0x332, 0x227C, 'movea', 'l', 6, src='#', dst='An'),
              'movea.l #$18e,a1'),
        _term(_instruction(rom, 0x338, 0xB251, 'cmp', 'w', src='(An)', dst='Dn'),
              'cmp.w (a1),d1'),
        _term(_instruction(rom, 0x33A, 0x6600, 'bne', 'w', 4, kind='cond', target=0x3B8),
              'bne.w CheckSumError', taken=0, fall=1),
    ]
    return _component('coldboot_checksum', terms, {
        'start': start, 'inclusive_end_pointer': end_pointer, 'inclusive_end': inclusive_end,
        **span, 'stored_checksum_u16': stored, 'checksum_branch': 'not_taken',
        'loop_instruction_only_cycles': sum(term['instruction_only_cycles'] for term in terms[4:7]),
    }, coverage='contiguous instructions 0x031c..0x033d; cold checksum path assumed selected')


def entry_ram_component(rom):
    # Locate the register-specific RAM loop within the requested entry range.
    candidates = [pc for pc in range(0x200, 0x2FC, 2)
                  if _word(rom, pc) == 0x2D00 and _word(rom, pc + 2) == 0x51CE]
    if candidates != [0x262]:
        raise AuditError('the unique EntryPoint RAM clear loop is not at the pinned location')
    _words(rom, 0x216, (0x4BFA, 0x007C, 0x4C9D, 0x00E0))
    table = 0x218 + _load_timing().s16(_word(rom, 0x218))
    _words(rom, table, (0x8000, 0x3FFF, 0x0100))
    initial = _word(rom, table + 2)
    outcomes = dbf_outcomes(initial)
    count = outcomes['iterations']
    terms = [
        _term(_instruction(rom, 0x216, 0x4BFA, 'lea', 'l', 4, src='d16(PC)'),
              'lea SetupValues(pc),a5'),
        _term(_instruction(rom, 0x21A, 0x4C9D, 'movem', 'w', 4, src='(An)+'),
              'movem.w (a5)+,d5-d7'),
        _term(_instruction(rom, 0x236, 0x7000, 'moveq', 'l'), 'moveq #0,d0'),
        _term(_instruction(rom, 0x238, 0x2C40, 'movea', 'l', src='Dn', dst='An'),
              'movea.l d0,a6'),
        _term(_instruction(rom, 0x262, 0x2D00, 'move', 'l', src='Dn', dst='-(An)'),
              'move.l d0,-(a6)', count),
        _term(_instruction(rom, 0x264, 0x51CE, 'dbf', 'w', 4, kind='dbcc', target=0x262),
              'dbf d6,$262', count, taken=outcomes['taken'], fall=1),
    ]
    return _component('entry_full_ram_clear', terms, {
        'setup_values_pc': table, 'initial_d6_u16': initial, **outcomes,
        'bytes_cleared': count * 4, 'initial_a6_u32': 0,
        'terminal_a6_u32': (-count * 4) & 0xFFFFFFFF,
        'ram_start_24bit': (-count * 4) & 0xFFFFFF, 'ram_inclusive_end_24bit': 0xFFFFFF,
        'loop_instruction_only_cycles': sum(term['instruction_only_cycles'] for term in terms[4:]),
    }, coverage='noncontiguous counter/base seeds plus 0x0262..0x0267 only; '
                'intervening setup work and cold-path hardware admission are uncounted')


def _postincrement_ram_component(rom, name, setup, absolute_word, initial, base):
    if absolute_word:
        _words(rom, setup, (0x4DF8, base & 0xFFFF))
        lea = _instruction(rom, setup, 0x4DF8, 'lea', 'l', 4, src='abs.W')
    else:
        _words(rom, setup, (0x4DF9, base >> 16, base & 0xFFFF))
        lea = _instruction(rom, setup, 0x4DF9, 'lea', 'l', 6, src='abs.L')
    zero = setup + lea.length
    counter = zero + 2
    body = counter + 4
    _words(rom, counter, (0x3C3C, initial))
    outcomes = dbf_outcomes(_word(rom, counter + 2))
    count = outcomes['iterations']
    terms = [
        _term(lea, f'lea ${base:06x},a6'),
        _term(_instruction(rom, zero, 0x7E00, 'moveq', 'l'), 'moveq #0,d7'),
        _term(_instruction(rom, counter, 0x3C3C, 'move', 'w', 4, src='#', dst='Dn'),
              f'move.w #${initial:04x},d6'),
        _term(_instruction(rom, body, 0x2CC7, 'move', 'l', src='Dn', dst='(An)+'),
              'move.l d7,(a6)+', count),
        _term(_instruction(rom, body + 2, 0x51CE, 'dbf', 'w', 4, kind='dbcc', target=body),
              f'dbf d6,${body:x}', count, taken=outcomes['taken'], fall=1),
    ]
    return _component(name, terms, {
        'initial_d6_u16': initial, **outcomes, 'bytes_cleared': count * 4,
        'ram_start_24bit': base, 'ram_inclusive_end_24bit': base + count * 4 - 1,
        'loop_instruction_only_cycles': sum(term['instruction_only_cycles'] for term in terms[3:]),
    }, coverage=f'contiguous instructions {setup:#06x}..{body + 5:#06x}; '
                'hardware access costs excluded')


def chant_component(rom):
    _words(rom, 0x71FAC, (0x13FC, 0x0088, 0x00A0, 0x1FFF,
                         0x33FC, 0, 0x00A1, 0x1100, 0x323C, 0x0011, 0x303C, 0xFFFF))
    counters = nested_dbf_outcomes(_word(rom, 0x71FBE), _word(rom, 0x71FC2))
    outer = counters['outer']
    body_count = counters['body_executions']
    terms = [
        _term(_instruction(rom, 0x71FAC, 0x13FC, 'move', 'b', 8, src='#', dst='abs.L',
                           absolute=[0xA01FFF]), 'move.b #$88,$a01fff'),
        _term(_instruction(rom, 0x71FB4, 0x33FC, 'move', 'w', 8, src='#', dst='abs.L',
                           absolute=[0xA11100]), 'move.w #0,$a11100 (start Z80)'),
        _term(_instruction(rom, 0x71FBC, 0x323C, 'move', 'w', 4, src='#', dst='Dn'),
              'move.w #$0011,d1'),
        _term(_instruction(rom, 0x71FC0, 0x303C, 'move', 'w', 4, src='#', dst='Dn'),
              'move.w #$ffff,d0', outer['iterations']),
        _term(_instruction(rom, 0x71FC4, 0x4E71, 'nop', ''), 'nop', body_count),
        _term(_instruction(rom, 0x71FC6, 0x51C8, 'dbf', 'w', 4, kind='dbcc', target=0x71FC4),
              'dbf d0,$71fc4', body_count,
              taken=counters['inner_taken'], fall=counters['inner_fall_through']),
        _term(_instruction(rom, 0x71FCA, 0x51C9, 'dbf', 'w', 4, kind='dbcc', target=0x71FC0),
              'dbf d1,$71fc0', outer['iterations'], taken=outer['taken'], fall=1),
        _term(_instruction(rom, 0x71FCE, 0x584F, 'addq', 'w', dst='An'), 'addq.w #4,sp'),
        _term(_instruction(rom, 0x71FD0, 0x4E75, 'rts', '', kind='ret'), 'rts'),
    ]
    return _component('PlaySegaSound_busy_loop', terms, {
        'initial_d1_u16': 0x0011, 'initial_d0_u16': 0xFFFF, **counters,
        'busy_loop_instruction_only_cycles': sum(term['instruction_only_cycles'] for term in terms[2:7]),
        'stack_adjustment_bytes': 4,
    }, coverage='contiguous instructions 0x071fac..0x071fd1; '
                'does not model bus waits, Z80/DAC playback or endpoint scheduling')


def instruction_components(rom):
    """Structural test seam, not an identity bypass for the CLI or runtime."""
    return [entry_ram_component(rom), checksum_component(rom),
            _postincrement_ram_component(rom, 'CheckSumOk_crossreset_ram_clear',
                                         0x33E, True, 0x007F, 0xFFFE00),
            _postincrement_ram_component(rom, 'GameInit_main_ram_clear',
                                         0x364, False, 0x3F7F, 0xFF0000),
            chant_component(rom)]


def audit_rom(rom):
    digest = hashlib.sha1(rom).hexdigest()
    if len(rom) != ROM_SIZE or digest != ROM_SHA1:
        raise AuditError('ROM identity differs from S1 World REV01 (size and SHA-1 required)')
    return {'schema': SCHEMA, 'status': 'INCOMPLETE', 'complete': False,
            'authority': 'offline instruction-only diagnostic; no runtime or publication authority',
            'timing_basis': 'Motorola MC68000 execution tables, no wait states',
            'rom_identity': {'sha1': digest, 'size': len(rom), 'fix_bugs': 0},
            'components': instruction_components(rom), 'missing_terms': MISSING_TERMS.copy(),
            'whole_startup_total': None, 'whole_frame_conversion': None}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--rom', type=Path, required=True,
                        help='original S1 World REV01 ROM; never copied or modified')
    args = parser.parse_args(argv)
    try:
        report = audit_rom(args.rom.read_bytes())
        status = 2
    except (OSError, ValueError) as error:
        report = {'schema': SCHEMA, 'status': 'ERROR', 'complete': False,
                  'authority': 'no runtime or publication authority', 'error': str(error)}
        status = 3
    print(json.dumps(report, indent=2))
    return status


if __name__ == '__main__':
    sys.exit(main())
