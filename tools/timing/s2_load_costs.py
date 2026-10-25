#!/usr/bin/env python3
# Purpose: preserve ROM-derived S2 synchronous-load CPU cost probes for recurring seam analysis.
# Inputs: original S2 World REV01 ROM, ROM zone/zero-based act, BG camera and NTSC/PAL region.
# Origin: 2026-10-22 s2_chain_pacing task, implementation milestone 44c1d674004c.
"""CPU-only micro-execution of selected Sonic 2 level-load components.

Runs actual REV01 instructions on private mini68k RAM; the supplied ROM is read
only. Sources come from the ROM's tables, never disassembly files or traces.
Supported paths are one-player Sonic+Tails in EHZ, CPZ and ARZ, acts 0/1.
The priced components are five KosDec calls, LoadAnimatedBlocks, DrawInitialBG,
RingsManager_Setup, InitPlayers, and EHZ's no-water WaterEffects branch.

This is NOT the complete post-LoadZoneTiles envelope. Caller/trampoline costs,
PLC/palette loading, object placement and dispatch, VInt/HInt acceptance/body,
FIFO/DMA stalls, refresh, Z80 arbitration and starting phase remain unresolved.
CPU frame equivalents are fractional unit conversions, not elapsed frame counts,
runtime costs, readiness input or gameplay timing. No engine consumes this tool.

Instruction costs use m68k_timing.Decoder's Motorola MC68000 section 8 tables.
The local S2CPU adds only the forms listed in its step method; existing S3K
interpreters and the shared decoder remain unchanged. Unsupported execution or
operand-dependent cost raises instead of supplying a default or zero charge.

Example (absolute ROM path; act is zero based)::

    python3 -B tools/timing/s2_load_costs.py --rom /path/to/original.gen \
        --zone 0 --act 0 --bg-x 0 --bg-y 0 --region ntsc
"""
from __future__ import annotations

import argparse
from collections import Counter
from dataclasses import dataclass, field
import hashlib
import json
from pathlib import Path
import sys
import zlib

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE / "s3k-instruction-ledgers" / "mini68k"))
sys.path.insert(0, str(HERE / "m68k-closure"))
import mini68k  # noqa: E402
import palette_forms  # noqa: E402
import m68k_timing  # noqa: E402

ROM_SHA1 = "8bca5dcef1af3e00098666fd892dc1c2a76333f9"
ROM_CRC32 = 0x7B905383
ZONES = {0: "EHZ", 13: "CPZ", 15: "ARZ"}
SCANLINES = {"ntsc": 262, "pal": 313}
SENTINEL = 0xFFFFF0
KOS = 0x193A
KOS_RANGE = ((KOS, 0x19DC),)
RINGS = 0x172A4
RINGS_RANGE = ((RINGS, 0x1736A),)
DRAW_BG = 0xE300
DRAW_RANGE = ((0xDF8A, 0xE396),)
ANIMATED_BLOCKS = 0x402D4
ANIMATED_RANGE = ((ANIMATED_BLOCKS, 0x40338),)
INIT_PLAYERS = 0x446E
INIT_RANGE = ((INIT_PLAYERS, 0x44E4),)
WATER_EFFECTS = 0x450E
WATER_RANGE = ((WATER_EFFECTS, 0x4584),)
CURRENT_ZONE_ACT = 0xFFFE10
BG_CAMERA = 0xFFEE08
LEVEL_ART_POINTERS = 0x42594
ANIM_PAT_MAPS = 0x40350
OFF_COL_P, OFF_COL_S = 0x49E8, 0x4A2C
OFF_RINGS = 0xE4300


class CostError(ValueError):
    """An invalid source, unsupported native path or incomplete CPU model."""


class S2CPU(palette_forms.CPU):
    """The frozen mini68k chain plus the exact forms this S2 slice executes."""

    def step(self, open_calls, open_terms):
        pc = self.pc
        op = self.word(pc)
        if op == 0x40C6:  # MOVE SR,D6 (KosDec saves flags across DBF/refill)
            ccr = self.x * 16 | self.n * 8 | self.z * 4 | self.v * 2 | self.c
            self.put(("d", 6), "w", getattr(self, "sr_mask", 3) << 8 | 0x2000 | ccr)
            self.pc += 2
            return "seq"
        if op == 0x44C6:  # MOVE D6,CCR: upper register bits do not enter SR
            ccr = self.d[6]
            self.x, self.n, self.z, self.v, self.c = (
                (ccr >> bit) & 1 for bit in (4, 3, 2, 1, 0))
            self.pc += 2
            return "seq"
        if op == 0xE353:  # ROXL.W #1,D3 (short Kosinski repeat length)
            value = self.d[3] & 0xFFFF
            result = ((value << 1) | self.x) & 0xFFFF
            self.c = self.x = value >> 15
            self.put(("d", 3), "w", result)
            self.nz(result, "w")
            self.v = 0
            self.pc += 2
            return "seq"
        if op in (0xE218, 0xE958):  # ROR.B #1,D0 / ROL.W #4,D0 (ring tables)
            size, count = ("b", 1) if op == 0xE218 else ("w", 4)
            width, mask = mini68k.SZ[size][0] * 8, mini68k.SZ[size][1]
            value = self.d[0] & mask
            for _ in range(count):
                if op == 0xE218:
                    self.c = value & 1
                    value = (value >> 1) | (self.c << (width - 1))
                else:
                    self.c = value >> (width - 1)
                    value = ((value << 1) | self.c) & mask
            self.put(("d", 0), size, value)
            self.nz(value, size)
            self.v = 0  # ROR/ROL leave X unchanged
            self.pc += 2
            return "seq"
        if op == 0x0478:  # SUBI.W #imm,(xxx).W (InitPlayers' Tails X offset)
            loc, after = self.ea(7, 0, "w", pc + 4)
            self.arith(loc, "w", self.get(loc, "w"), self.word(pc + 2), add=False)
            self.pc = after
            return "seq"
        if op in (0x48E7, 0x4CDF):  # MOVEM.L D4-D5[/D6],-(SP) / (SP)+
            mask = self.word(pc + 2)
            allowed = (0x0C00, 0x0E00) if op == 0x48E7 else (0x0030, 0x0070)
            if mask not in allowed:
                raise mini68k.Unsupported(f"S2 MOVEM mask {mask:04X} at {pc:06X}")
            for bit in range(16):
                if not (mask >> bit) & 1:
                    continue
                register = 15 - bit if op == 0x48E7 else bit
                if op == 0x48E7:
                    self.a[7] = (self.a[7] - 4) & mini68k.M
                    self.write(self.a[7], "l", self.d[register])
                else:
                    self.d[register] = self.read(self.a[7], "l")
                    self.a[7] = (self.a[7] + 4) & mini68k.M
            self.pc += 4
            return "seq"
        if op in (0x0A80, 0x0A83):  # EORI.L #imm,D0/D3 (BG tile flips)
            register = op & 7
            value = self.d[register] ^ self.read(pc + 2, "l")
            self.put(("d", register), "l", value)
            self.nz(value, "l")
            self.c = self.v = 0
            self.pc += 6
            return "seq"
        if op == 0x0810:  # BTST #imm,(A0): memory bit numbers wrap modulo 8
            bit = self.word(pc + 2) & 7
            self.z = int(not ((self.read(self.a[0], "b") >> bit) & 1))
            self.pc += 4
            return "seq"
        return super().step(open_calls, open_terms)


@dataclass
class Execution:
    cycles: int = 0
    instructions: int = 0
    counts: Counter = field(default_factory=Counter)
    vdp_data_bytes: int = 0
    vdp_control_writes: int = 0
    interrupt_masked_rows: int = 0


class Ledger:
    def __init__(self, rom: bytes):
        verify_rom(rom)
        self.rom = rom
        self.decoder = m68k_timing.Decoder(rom)
        self.decoded = {}

    def run(self, cpu: S2CPU, entry: int, ranges, limit=1_000_000) -> Execution:
        cpu.pc = entry
        cpu.a[7] = 0xFFFE00
        cpu.write(cpu.a[7], "l", SENTINEL)
        cpu.data_writes.clear()
        cpu.control_writes.clear()
        result = Execution()
        while cpu.pc != SENTINEL:
            pc = cpu.pc
            if not any(start <= pc < end for start, end in ranges):
                raise CostError(f"native path left priced ranges at {pc:06X}")
            if result.instructions >= limit:
                raise CostError(f"native instruction limit {limit} reached at {pc:06X}")
            try:
                if pc not in self.decoded:
                    self.decoded[pc] = self.decoder.decode(pc)
                instruction = self.decoded[pc]
                outcome = cpu.step({}, {})
            except (mini68k.Unsupported, m68k_timing.Unsupported, IndexError) as exc:
                raise CostError(f"unsupported native instruction/path at {pc:06X}: {exc}") from exc
            cost = (instruction.taken if outcome == "taken" else instruction.fall
                    if outcome in ("fall", "expired") else instruction.cost)
            if cost is None or not cost.exact():
                raise CostError(f"unresolved operand/branch cost at {pc:06X}: {cost}")
            result.cycles += cost.lo
            result.instructions += 1
            result.counts[pc] += 1
            if self.rom[pc:pc + 4] == bytes.fromhex("46fc2700"):
                result.interrupt_masked_rows += 1
        result.vdp_data_bytes = len(cpu.data_writes) * 4
        result.vdp_control_writes = len(cpu.control_writes)
        return result

    def kos(self, cpu, source, destination):
        if not 0 <= source < len(self.rom):
            raise CostError(f"Kosinski source {source:#x} is outside the ROM")
        if not 0xFF0000 <= destination < 0xFFDE00:
            raise CostError(f"Kosinski destination {destination:#x} is outside load RAM")
        cpu.a[0], cpu.a[1] = source, destination
        result = self.run(cpu, KOS, KOS_RANGE)
        consumed, produced = cpu.a[0] - source, cpu.a[1] - destination
        if consumed <= 0 or produced <= 0 or cpu.a[1] > 0xFFDE00:
            raise CostError("Kosinski stream has an invalid source/output extent")
        return result, consumed, produced


def verify_rom(rom: bytes):
    if (zlib.crc32(rom) != ROM_CRC32 or hashlib.sha1(rom).hexdigest() != ROM_SHA1):
        raise CostError("requires original Sonic 2 World REV01 ROM (CRC32 7B905383)")


def validate_inputs(zone, act, bg_x, bg_y, region):
    if zone not in ZONES:
        raise CostError(f"unsupported zone {zone}: priced paths are EHZ=0, CPZ=13, ARZ=15")
    if act not in (0, 1):
        raise CostError("act must be zero based: 0 or 1")
    if not 0 <= bg_x <= 0xFFFF or not 0 <= bg_y <= 0xFFFF:
        raise CostError("BG camera inputs must be unsigned native words (0..65535)")
    if region not in SCANLINES:
        raise CostError("region must be ntsc or pal")


def frame_equivalents(cycles, region):
    if region not in SCANLINES:
        raise CostError("region must be ntsc or pal")
    return cycles * 7 / (3420 * SCANLINES[region])


def analyze(rom: bytes, zone=0, act=0, bg_x=0, bg_y=0, region="ntsc"):
    validate_inputs(zone, act, bg_x, bg_y, region)
    ledger = Ledger(rom)
    cpu = S2CPU(rom)
    cpu.write(CURRENT_ZONE_ACT, "w", zone << 8 | act)
    read_word = lambda address: int.from_bytes(rom[address:address + 2], "big")
    read_long = lambda address: int.from_bytes(rom[address:address + 4], "big")
    directory = LEVEL_ART_POINTERS + zone * 12
    # loadLevelLayout $E462 loads this table address from the original ROM.
    layout_table = read_long(0xE46E)
    sources = (
        # Retail ARZ decodes $1900 bytes: $100 beyond Block_Table into
        # TempArray_LayerDef. Keep the native spill rather than rejecting or
        # truncating it. The guard ends after the two adjacent $200-byte
        # scratch buffers, before Object_Display_Lists at $AC00.
        ("block_maps", read_long(directory + 4) & 0xFFFFFF, 0xFF9000, 0x1C00),
        ("chunk_maps", read_long(directory + 8) & 0xFFFFFF, 0xFF0000, 0x8000),
        ("level_layout", layout_table + read_word(layout_table + zone * 4 + act * 2),
         0xFF8000, 0x1000),
        ("primary_collision", read_long(OFF_COL_P + zone * 4), 0xFFD600, 0x300),
        ("secondary_collision", read_long(OFF_COL_S + zone * 4), 0xFFD900, 0x300),
    )
    components = []

    def append(name, result, **details):
        component = {"name": name, "cpu_cycles": result.cycles,
                     "instructions": result.instructions,
                     "cpu_frame_equivalents": frame_equivalents(result.cycles, region)}
        component.update(details)
        components.append(component)

    for name, source, destination, maximum in sources:
        result, consumed, produced = ledger.kos(cpu, source, destination)
        if produced > maximum:
            raise CostError(f"{name} output {produced} exceeds its native buffer {maximum}")
        append(name, result, entry_pc=f"0x{KOS:X}", source=f"0x{source:X}",
               input_bytes=consumed, output_bytes=produced)
        if name == "block_maps":
            components[-1].update(nominal_buffer_bytes=0x1800,
                                  scratch_spill_bytes=max(0, produced - 0x1800))

    result = ledger.run(cpu, ANIMATED_BLOCKS, ANIMATED_RANGE)
    apm = ANIM_PAT_MAPS + read_word(ANIM_PAT_MAPS + zone * 2)
    words = read_word(apm + 2) + 1 if read_word(apm) else 0
    append("animated_blocks", result, entry_pc=f"0x{ANIMATED_BLOCKS:X}",
           source=f"0x{apm:X}", copied_words=words)
    cpu.write(BG_CAMERA, "w", bg_x)
    cpu.write(BG_CAMERA + 4, "w", bg_y)
    result = ledger.run(cpu, DRAW_BG, DRAW_RANGE)
    append("initial_background", result, entry_pc=f"0x{DRAW_BG:X}",
           camera_x=bg_x, camera_y=bg_y, block_calls=result.counts[0xE0D4],
           vdp_data_bytes=result.vdp_data_bytes,
           vdp_control_writes=result.vdp_control_writes,
           interrupt_masked_rows=result.interrupt_masked_rows)

    result = ledger.run(cpu, RINGS, RINGS_RANGE)
    ring_source = OFF_RINGS + read_word(OFF_RINGS + zone * 4 + act * 2)
    append("ring_setup", result, entry_pc=f"0x{RINGS:X}",
           source=f"0x{ring_source:X}", expanded_rings=cpu.d[5] & 0xFFFF)
    # Only EHZ's no-water branch is closed from the inputs accepted here.
    # CPZ/ARZ WaterEffects additionally needs water-owner RAM, not a zero seed.
    if zone == 0:
        append("water_effects_no_water", ledger.run(cpu, WATER_EFFECTS, WATER_RANGE),
               entry_pc=f"0x{WATER_EFFECTS:X}")
    append("init_players_sonic_tails", ledger.run(cpu, INIT_PLAYERS, INIT_RANGE),
           entry_pc=f"0x{INIT_PLAYERS:X}")

    total = sum(component["cpu_cycles"] for component in components)
    unresolved = [
        "caller/trampoline, PLC/palette, remaining setup and object-placement work",
        "first RunObjects, BuildSprites and AniArt_Load dispatch/publication",
        "VInt/HInt acceptance, handler work and interrupt phase (SR masks defer service)",
        "per-burst VDP FIFO waits, DMA freezes and starting VDP phase",
        "refresh and Z80 bus arbitration/latency",
    ]
    if zone != 0:
        unresolved.append("WaterEffects needs explicit water-owner initialization RAM")
    return {"evidence": "CPU_ONLY_PARTIAL_ENVELOPE", "runtime_authority": False,
            "rom_sha1": ROM_SHA1, "zone": zone, "zone_name": ZONES[zone], "act": act,
            "player_mode": "one-player Sonic+Tails", "region": region,
            "components": components, "selected_cpu_cycles": total,
            "selected_cpu_frame_equivalents": frame_equivalents(total, region),
            "unresolved": unresolved}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--rom", required=True, type=Path, help="absolute original REV01 ROM path")
    parser.add_argument("--zone", type=lambda value: int(value, 0), default=0,
                        help="ROM zone id: EHZ=0, CPZ=13, ARZ=15")
    parser.add_argument("--act", type=int, default=0, help="zero-based act: 0 or 1")
    parser.add_argument("--bg-x", type=lambda value: int(value, 0), default=0)
    parser.add_argument("--bg-y", type=lambda value: int(value, 0), default=0)
    parser.add_argument("--region", choices=tuple(SCANLINES), default="ntsc")
    parser.add_argument("--json", action="store_true", help="print bounded structured component summary")
    args = parser.parse_args(argv)
    try:
        validate_inputs(args.zone, args.act, args.bg_x, args.bg_y, args.region)
        if not args.rom.is_absolute():
            raise CostError("ROM path must be absolute")
        result = analyze(args.rom.read_bytes(), args.zone, args.act, args.bg_x, args.bg_y, args.region)
    except (OSError, CostError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2
    if args.json:
        print(json.dumps(result, indent=2))
    else:
        print(f"{result['evidence']}: {result['zone_name']} act {args.act} / {args.region}")
        for component in result["components"]:
            print(f"  {component['name']}: {component['cpu_cycles']} CPU clocks, "
                  f"{component['cpu_frame_equivalents']:.6f} frame equivalents")
        print(f"Selected sum: {result['selected_cpu_cycles']} CPU clocks, "
              f"{result['selected_cpu_frame_equivalents']:.6f} frame equivalents")
        print("Unresolved; no elapsed-frame or gameplay-timing claim:")
        for term in result["unresolved"]:
            print(f"  {term}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
