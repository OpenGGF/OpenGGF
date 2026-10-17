#!/usr/bin/env python3
"""Genesis Plus GX 68000 base-cycle table reader with pinned source identity.

Purpose: read `core/m68k/m68ki_cycles.h` from an explicit Genesis-Plus-GX git object store at an explicit commit,
verify the commit and the file's blob id, and expose GPGX's per-opcode base cycles together with the dynamic
`USE_CYCLES` rules of core/m68k/m68kops.h and m68kcpu.h (Bcc not taken -2 (.s) / +2 (.w); DBcc loop -2 /
expired +2; Scc Dn true +2; MOVEM +4/+8 per register; shifts +2 per count; Dn bit number >= 16 +2;
MULU/MULS 38..70 and DIVU 76..140 / DIVS 120..158 added to the base).
Inputs: repository path and commit (caller-supplied; nothing is fetched). Origin: OpenGGF S3K ending Kos
CPU-budget research, 2026-10-16. The BizHawk 2.11 pin is GPGX 051d430d3d1b54625f9900c8f152d7f232e06daf.
"""
import re
import subprocess

PINNED_COMMIT = '051d430d3d1b54625f9900c8f152d7f232e06daf'
PINNED_CYCLES_BLOB = '6b18bb209c1fcb1bf4e78d0c1e747da01a164542'
CYCLES_PATH = 'core/m68k/m68ki_cycles.h'
CPU_HEADER_PATH = 'core/m68k/m68kcpu.h'
PINNED_CPU_HEADER_BLOB = 'da20b21499ffc6c7fbdf49ac804ea96de2ad94d2'


_CONDITIONS = ('t', 'f', 'hi', 'ls', 'cc', 'cs', 'ne', 'eq', 'vc', 'vs', 'pl', 'mi', 'ge', 'lt', 'gt', 'le')


class IdentityError(Exception):
    pass


def _git(repo, *args):
    return subprocess.run(['git', '-C', repo, *args], check=True, capture_output=True).stdout


def load_table(repo, commit=PINNED_COMMIT, blob=PINNED_CYCLES_BLOB):
    try:
        full = _git(repo, 'rev-parse', '--verify', commit + '^{commit}').decode().strip()
        got_blob = _git(repo, 'rev-parse', '%s:%s' % (commit, CYCLES_PATH)).decode().strip()
    except subprocess.CalledProcessError as e:
        raise IdentityError('GPGX source %s@%s unavailable: %s' % (repo, commit, e.stderr.decode().strip()))
    if full != commit:
        raise IdentityError('GPGX commit %s resolved to %s' % (commit, full))
    if got_blob != blob:
        raise IdentityError('GPGX %s blob %s, expected %s' % (CYCLES_PATH, got_blob, blob))
    txt = _git(repo, 'show', '%s:%s' % (commit, CYCLES_PATH)).decode()
    body = txt[txt.index('m68ki_cycles[0x10000]'):]
    body = body[body.index('{') + 1: body.index('};')]
    vals = [int(a) for a in re.findall(r'(\d+)\s*\*\s*7', body)]
    if len(vals) != 0x10000:
        raise IdentityError('parsed %d cycle entries, expected 65536' % len(vals))
    return vals


def load_interrupt_cycles(repo, commit=PINNED_COMMIT, blob=PINNED_CPU_HEADER_BLOB):
    """Interrupt acceptance cost table. GPGX charges m68ki_cycle_interrupts[(cycles / MUL) % 10] when it takes an
    interrupt (m68kcpu.h m68ki_exception_interrupt), i.e. the autovector exception plus E-clock synchronisation,
    not the exception-table value 44. Returns the ten per-phase costs in 68000 cycles."""
    try:
        got_blob = _git(repo, 'rev-parse', '%s:%s' % (commit, CPU_HEADER_PATH)).decode().strip()
    except subprocess.CalledProcessError as e:
        raise IdentityError('GPGX source %s@%s unavailable: %s' % (repo, commit, e.stderr.decode().strip()))
    if got_blob != blob:
        raise IdentityError('GPGX %s blob %s, expected %s' % (CPU_HEADER_PATH, got_blob, blob))
    txt = _git(repo, 'show', '%s:%s' % (commit, CPU_HEADER_PATH)).decode()
    m = re.search(r'm68ki_cycle_interrupts\[10\]\s*=\s*\{([^}]*)\}', txt)
    vals = [int(v) for v in re.findall(r'(\d+)\s*\*\s*MUL', m.group(1))] if m else []
    if len(vals) != 10 or 'USE_CYCLES(m68ki_cycle_interrupts[(m68ki_cpu.cycles / MUL) % 10])' not in txt:
        raise IdentityError('GPGX interrupt cycle table not found as expected')
    return vals


def cost(table, ins, op, word_at):
    """GPGX cost of one execution: (lo, hi) for sequential kinds, or {'taken': (lo, hi), 'fall': (lo, hi)}."""
    b = table[op]
    n = ins.name
    if ins.kind == 'cond':
        return {'taken': (b, b), 'fall': (b - 2, b - 2) if ins.size == 's' else (b + 2, b + 2)}
    if ins.kind == 'dbcc':
        fall = (b + 2, b + 2) if n == 'dbf' else ((b, b) if n == 'dbt' else (b, b + 2))
        return {'taken': (b - 2, b - 2), 'fall': fall}
    if n == 'movem':
        k = bin(word_at(ins.pc + 2)).count('1')
        return (b + (8 if ins.size == 'l' else 4) * k,) * 2
    if n in ('asl', 'asr', 'lsl', 'lsr', 'roxl', 'roxr', 'rol', 'ror'):
        if (op >> 6) & 3 == 3:
            return (b, b)
        if op & 0x20:
            return (b, b + 2 * 63)
        return (b + 2 * (((op >> 9) & 7) or 8),) * 2
    if n in ('bset', 'bclr', 'bchg') and ins.dst == 'Dn':
        if ins.src == '#':
            return (b + (2 if (word_at(ins.pc + 2) & 31) >= 16 else 0),) * 2
        return (b, b + 2)
    if ins.kind == 'seq' and n[:1] == 's' and n[1:] in _CONDITIONS and ins.dst == 'Dn' and n[1:] not in ('t', 'f'):
        return (b, b + 2)
    if n in ('mulu', 'muls'):
        return (b + 38, b + 70)
    if n == 'divu':
        return (b + 76, b + 140)
    if n == 'divs':
        return (b + 120, b + 158)
    return (b, b)
