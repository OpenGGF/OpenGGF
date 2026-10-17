#!/usr/bin/env python3
# Origin: kos46 S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), round R4 (2026-10-16); CCR-source (MOVEA) correction in R6 (v3 patch).
# Promoted with the kos46 instruction-closure patches (v3 9b76415d..., successor 81f3097a...) on 2026-10-17 at base 00f8ab78a.
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""Proof-based narrowing of operand-dependent and branch-outcome cycle intervals for frame_closure.py.

Purpose: some 68000 costs depend on a value the execution histogram does not record (Scc outcome, DBcc exit kind,
dynamic bit number, an unforced branch). Each rule here narrows such a cost only when ROM instruction semantics plus
the bucket's own execution counts prove the value; every precondition is checked and a failed check leaves the
interval unchanged and records why. No rule uses timing, residuals or tolerances, so a rule cannot be fitted to close.
Rules:
  scc_flag_pair       generic: Scc Dn immediately followed by Bcc on the same or complementary condition. Scc leaves
                      the CCR unchanged, so its outcome equals the Bcc outcome the flow solver already proved.
  dbf_counter_bit     generic: BSET/BCLR/BCHG Dr,Dn inside a single-entry `moveq #imm,Dr` ... `dbf Dr` loop with no
                      other write of Dr: Dr stays in [0, imm], so imm < 16 proves the bit-number < 16 time.
  nonnegative_counter spec-declared, tool-verified: a register initialised once by `moveq #imm,Dr` and only decreased
                      within the executed static closure of a routine, and proven non-negative by an unconditionally
                      reached later sign test whose `bmi` is never taken, is non-negative at every DBcc whose flags
                      come from it; `dbmi` exits are therefore count expiries.
  z_exit_accounting   spec-declared, tool-verified: a routine whose only exits are listed Z-determined exits, called
                      only from sites that immediately branch on Z, has its exit mix fixed by the callers' counts.
Origin: OpenGGF S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), 2026-10-16.
"""

COMPLEMENT = {'hi': 'ls', 'ls': 'hi', 'cc': 'cs', 'cs': 'cc', 'ne': 'eq', 'eq': 'ne', 'vc': 'vs', 'vs': 'vc',
              'pl': 'mi', 'mi': 'pl', 'ge': 'lt', 'lt': 'ge', 'gt': 'le', 'le': 'gt'}


def data_writes(op):
    """Data registers an opcode writes, or None when this decoder does not know (callers then fail closed)."""
    top, mode, reg, rx = op >> 12, (op >> 3) & 7, op & 7, (op >> 9) & 7
    if top in (1, 2, 3):
        return {rx} if (op >> 6) & 7 == 0 else set()
    if top == 7:
        return {rx}
    if top == 5:
        if (op >> 6) & 3 == 3:
            return {reg} if mode in (0, 1) else set()          # Scc Dn, DBcc Dn
        return {reg} if mode == 0 else set()                     # ADDQ/SUBQ
    if top == 6:
        return set()
    if top == 0xE:
        return set() if (op >> 6) & 3 == 3 else {reg}
    if top == 0:
        if op & 0x100 or (op & 0xF00) == 0x800:                  # bit operations (and MOVEP)
            if op & 0x100 and mode == 1:
                return {rx} if not op & 0x80 else set()
            return set() if (op >> 6) & 3 == 0 else ({reg} if mode == 0 else set())
        sel = (op >> 9) & 7
        if sel == 6 or (mode == 7 and reg == 4):                 # CMPI, to CCR/SR
            return set()
        return {reg} if mode == 0 else set()
    if top == 4:
        if (op & 0xFFB8) == 0x4880 or (op & 0xFFF8) == 0x4840:   # EXT, SWAP
            return {reg}
        if (op & 0xFF80) == 0x4C80:                              # MOVEM memory -> registers (mask not decoded)
            return None
        if (op & 0xFF80) == 0x4880:                              # MOVEM registers -> memory
            return set()
        if (op & 0xF900) == 0x4000 and (op >> 6) & 3 != 3:       # NEGX/CLR/NEG/NOT
            return {reg} if mode == 0 else set()
        if (op & 0xFF00) == 0x4A00:                              # TST / TAS
            return {reg} if (op >> 6) & 3 == 3 and mode == 0 else set()
        if (op & 0xFFC0) == 0x40C0:
            return {reg} if mode == 0 else set()
        if (op & 0xF1C0) == 0x41C0 or (op & 0xFFC0) in (0x4E80, 0x4EC0, 0x4840, 0x44C0,
                                                                                   0x46C0):
            return set()
        if op in (0x4E71, 0x4E73, 0x4E75, 0x4E77) or (op & 0xFFF0) in (0x4E50, 0x4E60):
            return set()
        return None
    if top in (8, 9, 0xB, 0xC, 0xD):
        opm = (op >> 6) & 7
        if top == 0xB:
            return {reg} if 4 <= opm <= 6 and mode == 0 else set()   # EOR Dn,Dn; CMP/CMPA/CMPM write none
        if opm in (3, 7):
            return {rx} if top in (8, 0xC) else set()            # DIV/MUL write Dn; ADDA/SUBA write An
        if opm < 3:
            return {rx}
        if mode == 0:
            return {rx, reg} if (top == 0xC and opm == 5) else {rx}  # EXG Dn,Dn; ADDX/SUBX/ABCD/SBCD Dn
        if mode == 1:
            return {rx} if (top == 0xC and opm == 6) else set()  # EXG Dn,An
        return set()
    return None


def _sets_nz_from_word_dn(op, r):
    """True only for `tst.w Dr` or `move.w Dr,Dn`; MOVEA (destination mode 1) does not change the CCR, and other
    forms are not accepted as a proven source of N/Z for Dr."""
    if op == 0x4A40 | r:
        return True
    return op >> 12 == 3 and (op >> 3) & 7 == 0 and op & 7 == r and (op >> 6) & 7 == 0


def _move_sets_nz(op):
    """MOVE.B/W/L to a non-address destination sets N and Z from the moved value; MOVEA leaves the CCR unchanged."""
    return op >> 12 in (1, 2, 3) and (op >> 6) & 7 != 1


def _word(rom, a):
    return (rom[a] << 8) | rom[a + 1]


def _static_closure(start, ins, stop=()):
    """Executed PCs reachable from `start` along static fall-through, branch, jump and call edges; RTS/RTE end a path.
    Returns (pcs, indirect) where indirect lists executed indirect transfers met on the way."""
    seen, todo, indirect = set(), [start], []
    while todo:
        pc = todo.pop()
        if pc in seen or pc not in ins or pc in stop:
            continue
        seen.add(pc)
        i = ins[pc]
        nxt = pc + i.length
        if i.kind == 'seq':
            todo.append(nxt)
        elif i.kind in ('cond', 'dbcc'):
            todo += [nxt, i.target]
        elif i.kind == 'jump':
            todo.append(i.target)
        elif i.kind == 'call':
            todo += [i.target, nxt]
        elif i.kind in ('ijump', 'icall'):
            indirect.append(pc)
            if i.kind == 'icall':
                todo.append(nxt)
    return seen, indirect


def _exact(br, pc):
    lo, hi = br[pc]
    return lo if lo == hi else None


def scc_flag_pair(ctx):
    out = {}
    for pc, i in ctx.ins.items():
        cc = i.name[1:] if i.name[:1] == 's' else None
        if i.kind != 'seq' or cc not in COMPLEMENT or i.dst != 'Dn':
            continue
        q = ctx.ins.get(pc + i.length)
        why = None
        if q is None or q.kind != 'cond' or q.name[1:] not in (cc, COMPLEMENT[cc]):
            why = 'next instruction is not a Bcc on the same condition'
        elif ctx.counts[pc + i.length] != ctx.counts[pc] or ctx.deficits.get(pc + i.length):
            why = 'Bcc has another predecessor'
        elif _exact(ctx.branches, q.pc) is None:
            why = 'Bcc outcome itself unresolved'
        if why:
            ctx.notes.append('scc_flag_pair %06X: %s' % (pc, why))
            continue
        taken = _exact(ctx.branches, q.pc)
        true = taken if q.name[1:] == cc else ctx.counts[pc] - taken
        lo, hi = ctx.cost(i)
        out[pc] = true * hi + (ctx.counts[pc] - true) * lo           # GPGX: Scc Dn true costs base + 2
        ctx.notes.append('scc_flag_pair %06X: %d of %d true' % (pc, true, ctx.counts[pc]))
    return out


def dbf_counter_bit(ctx):
    out = {}
    for pc, i in ctx.ins.items():
        if i.name not in ('bset', 'bclr', 'bchg') or i.dst != 'Dn' or i.src == '#':
            continue
        op = _word(ctx.rom, pc)
        r = (op >> 9) & 7
        loops = [d for d, j in ctx.ins.items() if j.name == 'dbf' and (_word(ctx.rom, d) & 7) == r
                 and j.target <= pc < d]
        why = None
        if len(loops) != 1:
            why = 'not inside exactly one executed dbf loop on the bit register'
        else:
            d = loops[0]
            top = ctx.ins[d].target
            init = max((p for p in ctx.ins if p < top), default=None)
            iop = _word(ctx.rom, init) if init is not None else 0
            if init is None or init + ctx.ins[init].length != top or iop >> 12 != 7 or (iop >> 9) & 7 != r:
                why = 'loop is not entered from moveq #imm,Dr'
            elif not 0 <= (iop & 0xFF) < 16 or iop & 0x80:
                why = 'moveq immediate is not in [0, 15]'
            elif _exact(ctx.branches, d) is None or \
                    ctx.counts[top] != ctx.counts[init] + _exact(ctx.branches, d) or ctx.landings.get(top):
                why = 'loop head has another entry'
            else:
                body = [p for p in ctx.ins if top <= p < d]
                for p in body:
                    w = data_writes(_word(ctx.rom, p))
                    j = ctx.ins[p]
                    if w is None or r in w:
                        why = 'Dr written or unknown write at %06X' % p
                    elif j.kind in ('call', 'icall', 'ijump') or (j.kind in ('cond', 'jump') and
                                                                  not top <= j.target <= d):
                        why = 'loop body leaves the loop at %06X' % p
                    if why:
                        break
        if why:
            ctx.notes.append('dbf_counter_bit %06X: %s' % (pc, why))
            continue
        lo, hi = ctx.cost(i)
        out[pc] = ctx.counts[pc] * lo                                  # bit number < 16: no +2
        ctx.notes.append('dbf_counter_bit %06X: bit register in [0, %d]' % (pc, iop & 0xFF))
    return out


def nonnegative_counter(ctx, fact):
    r = fact['register']
    entry, init, test = (int(fact[k], 16) for k in ('entry_pc', 'init_pc', 'sign_test_branch_pc'))
    out = {}

    def fail(why):
        ctx.notes.append('nonnegative_counter d%d: %s' % (r, why))
        return {}
    if any(ctx.counts.get(p, 0) != 1 for p in (entry, init, test)):
        return fail('entry, init and sign test must each execute exactly once')
    iop = _word(ctx.rom, init)
    if iop >> 12 != 7 or (iop >> 9) & 7 != r or iop & 0x80:
        return fail('init is not moveq #non-negative,Dr')
    t = ctx.ins.get(test)
    if t is None or t.name != 'bmi' or _exact(ctx.branches, test) != 0:
        return fail('sign test is not a never-taken bmi')
    prev = max((p for p in ctx.ins if p < test), default=None)
    pop = _word(ctx.rom, prev) if prev is not None else 0
    if prev + ctx.ins[prev].length != test or not _sets_nz_from_word_dn(pop, r):
        return fail('sign test flags do not come from move.w Dr,Dn or tst.w Dr')
    if ctx.counts[prev] != ctx.counts[test] or ctx.deficits.get(test) or ctx.landings.get(test):
        return fail('sign test has another predecessor')
    pre, ind0 = _static_closure(entry, ctx.ins, stop=(init,))
    whole, ind = _static_closure(entry, ctx.ins)
    post, _ = _static_closure(test, ctx.ins)
    if ind:
        return fail('indirect transfer inside the routine closure at %s' % ['%06X' % p for p in ind])
    for p in whole:
        if p != entry and ctx.landings.get(p):
            return fail('dynamic landing inside the closure at %06X' % p)
    for p, j in ctx.ins.items():
        if p not in whole and j.kind in ('cond', 'dbcc', 'jump', 'call') and j.target in whole and j.target != entry:
            return fail('closure entered from %06X outside it' % p)
        if p not in whole and p + j.length in whole and j.kind in ('seq', 'cond', 'dbcc', 'call', 'icall') \
                and p + j.length != entry:
            return fail('closure entered by fall-through from %06X' % p)
    dec_total = 0
    for p in whole - post:
        w = data_writes(_word(ctx.rom, p))
        if w is None:
            return fail('unknown register writes at %06X' % p)
        if r in w and p != init:
            op = _word(ctx.rom, p)
            if op >> 12 == 5 and op & 0x100 and (op >> 3) & 7 == 0 and (op >> 6) & 3 in (1, 2) and p not in pre:
                dec_total += ctx.counts[p] * (((op >> 9) & 7) or 8)
            else:
                return fail('Dr written by a non-decrement at %06X' % p)
    if dec_total > (iop & 0x7F) + 32768:
        return fail('decrements could wrap the word')
    for p in whole - post - pre:
        j = ctx.ins[p]
        if j.kind != 'dbcc' or j.name != 'dbmi':
            continue
        q = max((x for x in ctx.ins if x < p), default=None)
        qop = _word(ctx.rom, q) if q is not None else 0
        flags_from_r = q is not None and q + ctx.ins[q].length == p and ctx.counts[q] == ctx.counts[p] and (
            qop == 0x4A40 | r or (qop >> 12 == 5 and qop & 0x100 and (qop >> 3) & 7 == 0 and qop & 7 == r
                                  and (qop >> 6) & 3 == 1)) and not ctx.deficits.get(p) and not ctx.landings.get(p)
        if not flags_from_r:
            ctx.notes.append('nonnegative_counter d%d: dbmi %06X flags not proven from Dr' % (r, p))
            continue
        k = ctx.cost(j)
        taken = _exact(ctx.branches, p)
        if taken is None:
            ctx.notes.append('nonnegative_counter d%d: dbmi %06X loop count unresolved' % (r, p))
            continue
        out[p] = taken * k['taken'][0] + (ctx.counts[p] - taken) * k['fall'][1]   # every exit is a count expiry
        ctx.notes.append('nonnegative_counter d%d: dbmi %06X exits are expiries' % (r, p))
    return out


def z_exit_accounting(ctx, fact):
    """Returns (pins, totals): pinned branch intervals for the solver and exact totals for DBcc exits."""
    ret = int(fact['return_pc'], 16)
    entries = [int(e, 16) for e in fact['entry_pcs']]
    bexit = int(fact['z0_branch_exit_pc'], 16)
    dexit = int(fact['dbcc_exit_pc'], 16)

    def fail(why):
        ctx.notes.append('z_exit_accounting %06X: %s' % (entries[0], why))
        return {}, {}
    if ctx.counts.get(ret, 0) == 0:
        return fail('routine not executed')
    b, d = ctx.ins.get(bexit), ctx.ins.get(dexit)
    if ctx.ins.get(ret) is None or ctx.ins[ret].name != 'rts':
        return fail('return pc is not rts')
    if b is None and ctx.counts.get(bexit, 0) == 0 and _word(ctx.rom, bexit) >> 8 == 0x6B:
        exits = [dexit]                       # the bmi exit did not execute in this bucket
    elif b is None or b.name != 'bmi' or b.target != ret:
        return fail('Z0 exit is not a bmi to the return')
    else:
        exits = [bexit, dexit]
        prev = max(p for p in ctx.ins if p < bexit)
        pop = _word(ctx.rom, prev)
        if prev + ctx.ins[prev].length != bexit or not _move_sets_nz(pop) or ctx.counts[prev] != ctx.counts[bexit] \
                or ctx.deficits.get(bexit) or ctx.landings.get(bexit):
            return fail('bmi flags do not come from the immediately preceding move')
    if d is None or d.name not in ('dbeq',) or dexit + d.length != ret:
        return fail('DBcc exit is not a dbeq falling into the return')
    inflow = [p for p, j in ctx.ins.items() if (p + j.length == ret and j.kind in ('seq', 'cond', 'dbcc', 'call'))
              or (j.kind in ('cond', 'dbcc', 'jump', 'call') and j.target == ret)]
    if sorted(inflow) != sorted(exits) or ctx.landings.get(ret):
        return fail('return has other predecessors %s' % ['%06X' % p for p in inflow])
    z0 = z1 = calls = 0
    for p, j in ctx.ins.items():
        if j.kind == 'call' and j.target in entries:
            q = ctx.ins.get(p + j.length)
            if q is None or q.name not in ('bne', 'beq') or ctx.counts[p + j.length] != ctx.counts[p]:
                return fail('call at %06X is not followed by bne/beq with equal count' % p)
            taken = _exact(ctx.branches, q.pc)
            if taken is None:
                return fail('Z consumer %06X unresolved' % q.pc)
            zero = taken if q.name == 'bne' else ctx.counts[q.pc] - taken
            z0 += zero
            z1 += ctx.counts[q.pc] - zero
            calls += ctx.counts[p]
        elif j.kind in ('jump', 'cond', 'dbcc') and j.target in entries and not entries[0] <= p <= ret:
            return fail('entry reached by a jump from %06X' % p)
    for e in entries:
        if ctx.landings.get(e):
            return fail('entry %06X has dynamic landings' % e)
    if calls != ctx.counts[ret]:
        return fail('calls %d != returns %d' % (calls, ctx.counts[ret]))
    # Z1 returns are exactly the dbeq exits taken on equality; Z0 returns are bmi exits plus count expiries.
    found = z1
    pins = {bexit: [0, z0]} if bexit in exits else {}
    if z0 == 0 and bexit in exits:
        pins[bexit] = [0, 0]
    ctx.notes.append('z_exit_accounting %06X: %d Z=1 returns (dbeq found), %d Z=0 returns' % (entries[0], z1, z0))
    return pins, {'found': found, 'z0': z0, 'dbcc': dexit, 'bmi': bexit}


class Context:
    def __init__(self, rom, counts, solved, cost):
        self.rom, self.counts = rom, counts
        self.ins, self.branches = solved['ins'], solved['branches']
        self.landings, self.deficits = solved['landings'], solved['deficits']
        self.cost = cost
        self.notes = []


def resolve(rom, counts, solve, cost, facts):
    """Run every rule. `solve(pins)` re-runs the flow solver with pinned branch intervals. Returns
    (solved, totals, notes): totals maps pc -> exact bucket cycles that replace that pc's interval."""
    solved = solve({})
    ctx = Context(rom, counts, solved, cost)
    pins, zinfo = {}, []
    for f in facts:
        if f['kind'] == 'z_exit_accounting':
            p, info = z_exit_accounting(ctx, f)
            pins.update(p)
            if info:
                zinfo.append(info)
    if pins:
        solved = solve(pins)
        notes = ctx.notes
        ctx = Context(rom, counts, solved, cost)
        ctx.notes = notes
    totals = {}
    for info in zinfo:
        d = info['dbcc']
        taken = _exact(ctx.branches, d)
        bmi_taken = _exact(ctx.branches, info['bmi']) if info['bmi'] in ctx.branches else 0
        if taken is None or bmi_taken is None:
            ctx.notes.append('z_exit_accounting: outcomes still open after pinning')
            continue
        fall = counts[d] - taken
        expired = fall - info['found']
        if expired < 0 or expired + bmi_taken != info['z0']:
            ctx.notes.append('z_exit_accounting: caller counts inconsistent with exits')
            continue
        k = cost(ctx.ins[d])
        totals[d] = taken * k['taken'][0] + info['found'] * k['fall'][0] + expired * k['fall'][1]
    totals.update(scc_flag_pair(ctx))
    totals.update(dbf_counter_bit(ctx))
    for f in facts:
        if f['kind'] == 'nonnegative_counter':
            totals.update(nonnegative_counter(ctx, f))
        elif f['kind'] != 'z_exit_accounting':
            ctx.notes.append('unknown refinement kind %r ignored' % f['kind'])
    return solved, totals, ctx.notes
