#!/usr/bin/env python3
# Origin: kos46 S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), round R11 (2026-10-17; R12 Nemesis job and R13 H-int composition).
# Promoted into OpenGGF tools/timing on 2026-10-17 at base 00f8ab78a. Promotion changed only import paths and directory names,
# sonic3k.asm line citations (converted to the pinned skdisasm 044fa46) and this header.
# Inputs: ROM bytes, per-VInt semantic state, cost function; composes ../vint, hud/, drawlevel/, nem/, hint/.
# Citations: ROM address and label first; any sonic3k.asm line number refers to skdisasm 044fa46 (see README).
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""Compose the level-frame VInt instruction ledger from its closed parts (kos46 research prototype, 2026-10-17).

VInt / VInt_8 / Set_Kos_Bookmark (vint8_cost), VInt_DrawLevel (drawlevel/), UpdateHUD (hud/) and
Process_Nem_Queue_2 (nem/). A callee's ledger runs only if VInt_8 actually called it. Any remaining open term
(VInt_0, SpecialVInt_xx; HUD open callees raise) leaves `closed` False: the caller must not total it. A nonempty
Nemesis queue given as `NemJob` (ROM art address + patterns already decoded) is closed: fixed part from nem_cost,
decoder part from nem_event_cost (equal to ROM micro-execution on every Offs_PLC art); given as plain `nc.Nem` it stays
open (Nem_PCD_NewRow).
Hardware terms (interrupt acceptance, refresh, Z80 and FIFO waits, DMA freeze) stay separate.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
for sub in ('', 'hud', 'drawlevel', 'nem', 'hint'):
    sys.path.insert(0, os.path.join(HERE, sub))
import vint8_cost as vc  # noqa: E402
import hud_cost as hc  # noqa: E402
import drawlevel_cost as dl  # noqa: E402
import nem_cost as nc  # noqa: E402
import nem_event_cost as ne  # noqa: E402
import hint_cost as hi  # noqa: E402
from dataclasses import dataclass  # noqa: E402


@dataclass(frozen=True)
class NemJob:
    """Queue head being decoded at this VInt: ROM art address, patterns already decoded, and the queue entries."""
    art: int
    done: int
    queue: tuple


class FrameLedger:
    def __init__(self, rom, cost, shift_base=None):
        import m68k_timing
        self.rom = rom
        self.events = ne.NemEventCost(rom, cost, m68k_timing.Decoder(rom), shift_base)
        self.vint = vc.VIntLedger(rom, cost)
        self.hud = hc.HudLedger(rom, cost)
        self.draw = dl.DrawLevelLedger(rom, cost)
        self.nem = nc.NemLedger(rom, cost)
        self.hint = hi.HIntLedger(rom, cost)

    def run(self, state, planes, nem, hud):
        r = self.vint.run(state)
        cycles, counts, terms = r.cycles, dict(r.counts), {}
        parts = {'VInt_DrawLevel': lambda: self.draw.run(planes)[:2],
                 'UpdateHUD': lambda: self.hud.run(hud)[:2],
                 'Process_Nem_Queue_2': lambda: self.nem_part(nem)}
        for name, calls in r.open_terms.items():
            if name not in parts or calls != 1:
                terms[name] = terms.get(name, 0) + calls
                continue
            out = parts[name]()
            cycles += out[0]
            for pc, k in out[1].items():
                counts[pc] = counts.get(pc, 0) + k
            for t, k in (out[2].items() if len(out) > 2 else ()):
                terms[t] = terms.get(t, 0) + k
        return {'cycles': cycles, 'counts': counts, 'open_terms': terms, 'closed': not terms,
                'bookmarked': r.bookmarked}

    def run_hint(self, hstate, nem, hud):
        """One accepted water line interrupt: handler body plus, when VInt_8 deferred them, UpdateHUD and
        Process_Nem_Queue_2 (same ledgers as the VInt path). Acceptance itself is a separate hardware term."""
        h = self.hint.run(hstate)
        cycles, counts, terms = h.cycles, dict(h.counts), {}
        parts = {'UpdateHUD': lambda: self.hud.run(hud)[:2], 'Process_Nem_Queue_2': lambda: self.nem_part(nem)}
        for name, calls in h.open_terms.items():
            out = parts[name]()
            cycles += out[0]
            for pc, k in out[1].items():
                counts[pc] = counts.get(pc, 0) + k
            for t, k in (out[2].items() if len(out) > 2 else ()):
                terms[t] = terms.get(t, 0) + k
        return {'cycles': cycles, 'counts': counts, 'open_terms': terms, 'closed': not terms, 'after': h.after,
                'colors': h.colors}

    def nem_part(self, nem):
        if not isinstance(nem, NemJob):
            return self.nem.run(nem)
        total = (self.rom[nem.art] << 8 | self.rom[nem.art + 1]) & 0x7FFF
        if not nem.queue or nem.queue[0][0] != nem.art:
            raise ValueError('the job art must be the queue head')
        fixed, counts, terms, _ = self.nem.run(nc.Nem(total - nem.done, nem.queue))
        if set(terms) != {'Nem_PCD_NewRow'}:
            raise AssertionError(terms)
        # counts cover the fixed part only; decoder PCs are priced by the event model, not counted
        return fixed + self.events.vint_cycles(nem.art, nem.done), counts, {}
