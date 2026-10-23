#!/usr/bin/env python3
# Purpose: preserve an actual-ROM CPU oracle for S2's four-slice track renderer.
# Inputs: original S2 World REV01 ROM, mapping ID, incoming orientation and alternate PNT.
# Origin: 2026-10-22 s2_chain_pacing task, implementation milestone 44c1d674004c.
"""Execute SSTrack_Draw, observing CPU costs and writes, without device timing.

The local adapter extends s2_load_costs.S2CPU, leaving the shared S3K interpreters
and Motorola timing decoder unchanged. ROM code and lookup tables supply every
native branch and mapping word. A separate high-level bit reader checks raw PNT
contents; it is not the Java renderer and does not certify rendered pixels.

Each call draws seven 128-word rows. Three ROM cursors and six saved words survive
calls 0..2; call 3 clears the six words. Unflipped calls write VDP DATA words.
Flipped calls write $700 bytes of private PNT RAM: the FOLLOWING Vint_S2SS transfers
that buffer, before incrementing drawing index. This tool does not execute VInt or
DMA. The private VRAM model only applies commands/writes with autoincrement 2.

Instruction costs come from m68k_timing.Decoder (Motorola MC68000 section 8,
without wait states). Write observations carry their instruction's CPU prefix
cost, NOT an intra-instruction bus-cycle timestamp. FIFO waits, DMA, interrupts,
device phase, refresh, Z80 arbitration and the other main-loop work remain open.
No engine consumes the output; it provides no gameplay/readiness/timing authority.

Native entry $5604, continuation $56D2, return $5D58, orientation $6C0A;
mapping directory $643E, animation directory $63DC, UNC/RLE LUTs $651E/$69E6.
Original routine: docs/s2disasm/s2.asm SSTrack_Draw / SSTrackDraw_return /
SSTrackSetOrientation; Vint_S2SS owns the separate flipped-buffer transfer.
"""
from __future__ import annotations

import argparse
from dataclasses import asdict, dataclass
import hashlib
import json
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
import s2_load_costs as load  # noqa: E402

CostError = load.CostError
ENTRY, CONTINUE, SENTINEL = 0x5604, 0x56D2, load.SENTINEL
CODE_RANGES = ((0x5604, 0x63DC), (0x6C0A, 0x6C9C))
MAPPING_TABLE, ANIMATION_TABLE, ANIMATION_LENGTHS = 0x643E, 0x63DC, 0x55FE
MAPPING_END, FRAME_COUNT = 0xDCA38, 56
UNC_LUT, RLE_LUT, PART2 = 0x651E, 0x69E6, 64
PNT_BUFFER, PNT_BYTES, ROW_WORDS, SLICE_ROWS = 0xFFD000, 0x700, 128, 7
CURSORS = (0xFFDB00, 0xFFDB04, 0xFFDB3A)
REG_BUFFER = 0xFFDB3E
SEGMENT, ANIM_FRAME, ALT_PNT, DRAW_INDEX = 0xFFDB0A, 0xFFDB0B, 0xFFDB0C, 0xFFDB0D
ORIENTATION, ALT_SCROLL, MAPPING_FRAME = 0xFFDB0E, 0xFFDB0F, 0xFFDB10
LAST_SCROLL, PLAYER_TIMER, LAYOUT_POINTER = 0xFFDB11, 0xFFDB21, 0xFFDB8E


@dataclass(frozen=True)
class Write:
    """An ordered write; cpu_prefix is the instruction START, not bus time."""
    pc: int
    cpu_prefix: int
    address: int
    width: int
    value: int
    destination: str


@dataclass(frozen=True)
class Snapshot:
    ram: bytes
    data_registers: tuple[int, ...]
    address_registers: tuple[int, ...]
    flags: tuple[int, ...]
    pc: int
    sr_mask: int
    vram: bytes
    vdp_address: int | None


class TrackCPU(load.S2CPU):
    """Only the exact additional execution forms used by SSTrack_Draw."""
    def __init__(self, rom, ram=None):
        super().__init__(rom, ram)
        self.writes = []
        self.vram = bytearray(0x10000)
        self.vdp_address = None
        self.observing = False
        self.cpu_prefix = 0

    def write(self, address, size, value):
        address &= 0xFFFFFF
        width, mask = load.mini68k.SZ[size]
        value &= mask
        destination = None
        if address in (0xC00000, 0xC00004):
            if size not in ("w", "l"):
                raise load.mini68k.Unsupported("byte VDP port write")
            if address == 0xC00004:
                # Draw uses a full VRAM-write command, never half commands or
                # VDP-register writes. Other device paths are deliberately open.
                code = ((value >> 30) & 3) | ((value >> 2) & 0x3C)
                if size != "l" or code != 1:
                    raise load.mini68k.Unsupported("non-track VDP control command")
                self.vdp_address = ((value >> 16) & 0x3FFF) | ((value & 3) << 14)
                destination = "vdp_control"
            else:
                if self.vdp_address is None:
                    raise load.mini68k.Unsupported("VDP DATA before VRAM command")
                for shift in range((width - 1) * 8, -1, -8):
                    self.vram[self.vdp_address] = (value >> shift) & 0xFF
                    self.vdp_address = (self.vdp_address + 1) & 0xFFFF
                destination = "vdp_data"
            # Bypass nem_forms' longword-only observer, not instruction logic.
            load.mini68k.CPU.write(self, address, size, value)
        else:
            super().write(address, size, value)
            if PNT_BUFFER <= address < PNT_BUFFER + PNT_BYTES:
                if address + width > PNT_BUFFER + PNT_BYTES:
                    raise load.mini68k.Unsupported("write crosses PNT buffer extent")
                destination = "pnt_buffer"
        if self.observing and destination:
            self.writes.append(Write(self.pc, self.cpu_prefix, address, width, value, destination))

    def capture(self):
        return Snapshot(bytes(self.ram), tuple(self.d), tuple(self.a),
                        (self.x, self.n, self.z, self.v, self.c), self.pc,
                        getattr(self, "sr_mask", 3), bytes(self.vram), self.vdp_address)

    def restore(self, state: Snapshot):
        if len(state.ram) != 0x10000 or len(state.vram) != 0x10000:
            raise CostError("invalid private RAM/VRAM snapshot extent")
        self.ram[:] = state.ram
        self.d[:], self.a[:] = state.data_registers, state.address_registers
        self.x, self.n, self.z, self.v, self.c = state.flags
        self.pc, self.sr_mask = state.pc, state.sr_mask
        self.vram[:], self.vdp_address = state.vram, state.vdp_address
        self.writes.clear()
        self.observing = False

    def step(self, open_calls, open_terms):
        pc, op = self.pc, self.word(self.pc)
        if op == 0x4C9B:  # MOVEM.W (A3)+,D2-D7: word loads SIGN EXTEND
            if self.word(pc + 2) != 0x00FC:
                raise load.mini68k.Unsupported("track MOVEM restore mask")
            for register in range(2, 8):
                self.d[register] = load.mini68k.sx(self.read(self.a[3], "w"), "w") & load.mini68k.M
                self.a[3] = (self.a[3] + 2) & load.mini68k.M
            self.pc += 4
            return "seq"
        if op == 0x48A3:  # MOVEM.W D2-D7,-(A3): reverse register order
            if self.word(pc + 2) != 0x3F00:
                raise load.mini68k.Unsupported("track MOVEM save mask")
            for register in range(7, 1, -1):
                self.a[3] = (self.a[3] - 2) & load.mini68k.M
                self.write(self.a[3], "w", self.d[register])
            self.pc += 4
            return "seq"
        if op == 0x4EFB:  # JMP d8(PC,D0.W); extension PC is the base
            if self.word(pc + 2) & 0xFF00:
                raise load.mini68k.Unsupported("track JMP index is not D0.W")
            location, _ = self.ea(7, 3, "l", pc + 2)
            self.pc = location[1] & 0xFFFFFF
            return "seq"
        if op in (0x50F8, 0x51F8):  # ST/SF (xxx).W; condition flags unchanged
            location, following = self.ea(7, 0, "b", pc + 2)
            self.put(location, "b", 0xFF if op == 0x50F8 else 0)
            self.pc = following
            return "seq"
        if op >> 12 == 0xE and (op >> 6) & 3 == 0 and not op & 0x20 and (op >> 3) & 3 == 3:
            register, count, left = op & 7, ((op >> 9) & 7) or 8, bool(op & 0x100)
            if register not in (2, 4) or count not in ((1, 2, 3) if left else (1, 2, 3, 4)):
                return super().step(open_calls, open_terms)
            value = self.d[register] & 0xFF
            for _ in range(count):
                if left:
                    self.c = value >> 7
                    value = ((value << 1) | self.c) & 0xFF
                else:
                    self.c = value & 1
                    value = (value >> 1) | (self.c << 7)
            self.put(("d", register), "b", value)
            self.nz(value, "b")
            self.v = 0  # ROL/ROR preserve X
            self.pc += 2
            return "seq"
        return super().step(open_calls, open_terms)


@dataclass(frozen=True)
class DrawState:
    drawing_index: int
    mapping_frame: int
    segment: int
    animation_frame: int
    cursors: tuple[int, ...]
    saved_words: tuple[int, ...]
    orientation: int
    alternate_pnt: int
    alternate_scroll: int
    last_alternate_scroll: int
    data_registers: tuple[int, ...]
    address_registers: tuple[int, ...]
    flags: tuple[int, ...]


@dataclass(frozen=True)
class Slice:
    cpu_cycles: int
    instructions: int
    writes: tuple[Write, ...]
    post_state: DrawState
    pnt_words: tuple[int, ...]


def post_state(cpu):
    byte = lambda address: cpu.read(address, "b")
    return DrawState(byte(DRAW_INDEX), byte(MAPPING_FRAME), byte(SEGMENT), byte(ANIM_FRAME),
                     tuple(cpu.read(address, "l") for address in CURSORS),
                     tuple(cpu.read(REG_BUFFER + index * 2, "w") for index in range(6)),
                     byte(ORIENTATION), byte(ALT_PNT), byte(ALT_SCROLL), byte(LAST_SCROLL),
                     tuple(cpu.d), tuple(cpu.a), (cpu.x, cpu.n, cpu.z, cpu.v, cpu.c))


class TrackLedger(load.Ledger):
    def run_slice(self, cpu, drawing_index, limit=100_000):
        if not 0 <= drawing_index <= 0xFF:
            raise CostError("drawing index must be a native unsigned byte")
        cpu.observing = False
        cpu.write(DRAW_INDEX, "b", drawing_index)
        cpu.pc, cpu.a[7] = ENTRY, 0xFFFE00
        cpu.write(cpu.a[7], "l", SENTINEL)
        cpu.writes.clear()
        cycles = instructions = 0
        cpu.observing = True
        try:
            while cpu.pc != SENTINEL:
                pc = cpu.pc
                if not any(start <= pc < end for start, end in CODE_RANGES):
                    raise CostError(f"track path left priced ranges at {pc:06X}")
                if instructions >= limit:
                    raise CostError(f"track instruction limit {limit} reached at {pc:06X}")
                try:
                    if pc not in self.decoded:
                        self.decoded[pc] = self.decoder.decode(pc)
                    instruction = self.decoded[pc]
                    cpu.cpu_prefix = cycles
                    outcome = cpu.step({}, {})
                except (load.mini68k.Unsupported, load.m68k_timing.Unsupported, IndexError) as exc:
                    raise CostError(f"unsupported track instruction/path at {pc:06X}: {exc}") from exc
                cost = (instruction.taken if outcome == "taken" else instruction.fall
                        if outcome in ("fall", "expired") else instruction.cost)
                if cost is None or not cost.exact():
                    raise CostError(f"unresolved track instruction cost at {pc:06X}: {cost}")
                cycles += cost.lo
                instructions += 1
        finally:
            cpu.observing = False
        state = post_state(cpu)
        words = ()
        if drawing_index < 4:
            writes = [write for write in cpu.writes if write.destination in ("pnt_buffer", "vdp_data")]
            if len(writes) != PNT_BYTES // 2 or any(write.width != 2 for write in writes):
                raise CostError("native slice did not write exactly $700 bytes of words")
            if state.orientation:
                if any(write.destination != "pnt_buffer" for write in writes):
                    raise CostError("flipped native slice wrote VDP DATA directly")
                words = tuple(cpu.read(PNT_BUFFER + offset, "w") for offset in range(0, PNT_BYTES, 2))
            else:
                if any(write.destination != "vdp_data" for write in writes):
                    raise CostError("unflipped native slice wrote the PNT buffer")
                words = tuple(write.value for write in writes)
            if drawing_index == 3 and any(state.saved_words):
                raise CostError("last native slice did not clear saved decoder words")
        elif cpu.writes:
            raise CostError("orientation-only native call produced track writes")
        return Slice(cycles, instructions, tuple(cpu.writes), state, words)


def mapping_bounds(rom, mapping):
    if not 0 <= mapping < FRAME_COUNT:
        raise CostError("mapping ID must be 0..55")
    get_long = lambda address: int.from_bytes(rom[address:address + 4], "big")
    source = get_long(MAPPING_TABLE + mapping * 4)
    end = get_long(MAPPING_TABLE + (mapping + 1) * 4) if mapping + 1 < FRAME_COUNT else MAPPING_END
    if not 0xCA904 <= source < end <= MAPPING_END:
        raise CostError("mapping directory extent is outside original track data")
    return source, end


def animation_position(rom, mapping):
    for kind in range(5):
        offset = int.from_bytes(rom[ANIMATION_TABLE + kind * 2:ANIMATION_TABLE + kind * 2 + 2], "big")
        sequence = rom[ANIMATION_TABLE + offset:ANIMATION_TABLE + offset + rom[ANIMATION_LENGTHS + kind]]
        if mapping in sequence:
            return kind, sequence.index(mapping)
    raise CostError("mapping is not selected by an original ROM animation")


def prepare(rom, mapping, flipped=False, alternate_pnt=False):
    load.verify_rom(rom)
    mapping_bounds(rom, mapping)
    if not isinstance(flipped, bool) or not isinstance(alternate_pnt, bool):
        raise CostError("orientation and alternate PNT must be booleans")
    kind, position = animation_position(rom, mapping)
    cpu = TrackCPU(rom)
    # This is an explicitly constructed component input, not an actual stage
    # route. Both adjacent layout bytes select the same native geometry, since
    # the last animation position can advance the segment before orientation.
    cpu.write(LAYOUT_POINTER, "l", 0xFF8000)
    cpu.write(0xFF8000, "b", kind | (0x80 if flipped else 0))
    cpu.write(0xFF8001, "b", kind | (0x80 if flipped else 0))
    cpu.write(ANIM_FRAME, "b", position)
    cpu.write(ORIENTATION, "b", 0xFF if flipped else 0)
    cpu.write(ALT_PNT, "b", int(alternate_pnt))
    return cpu


class Bits:
    """Independent MSB-first format reader, not the native register algorithm."""
    def __init__(self, data):
        self.data, self.position = data, 0

    def take(self, count):
        if self.position + count > len(self.data) * 8:
            raise CostError("reference bitstream exhausted")
        value = 0
        for _ in range(count):
            value = value * 2 + ((self.data[self.position // 8] >> (7 - self.position % 8)) & 1)
            self.position += 1
        return value


def decode_reference(rom, mapping, flipped=False):
    """Check canonical raw PNT words; deliberately not Java's 32-strip layout."""
    source, end = mapping_bounds(rom, mapping)
    streams, position = [], source
    for _ in range(3):
        if position + 4 > end:
            raise CostError("reference segment header outside mapping")
        length = int.from_bytes(rom[position + 2:position + 4], "big")
        start, position = position + 4, position + 4 + length
        if position > end:
            raise CostError("reference segment length outside mapping")
        streams.append(Bits(rom[start:position]))
    flags, unc, rle = streams
    rows, row, unc_flags = [], [], []
    while len(rows) < 28 * ROW_WORDS:
        if flags.take(1):
            index = unc.take(10) + PART2 if unc.take(1) else unc.take(6)
            address = UNC_LUT + index * 2
            if address + 2 > RLE_LUT:
                raise CostError("reference UNC index exceeds ROM LUT")
            row.append(int.from_bytes(rom[address:address + 2], "big") | 0x6000)
            unc_flags.append(True)
        else:
            extended = rle.take(1)
            index = rle.take(7 if extended else 6)
            if extended and index == 127:
                if len(row) != ROW_WORDS:
                    raise CostError(f"reference row has {len(row)} words, expected 128")
                if flipped:
                    row = [word ^ (0x800 if is_unc else 0)
                           for word, is_unc in zip(reversed(row), reversed(unc_flags))]
                rows.extend(row)
                row, unc_flags = [], []
                # rows holds words, not row arrays.
                if len(rows) == 28 * ROW_WORDS:
                    return tuple(rows)
                continue
            index += PART2 if extended else 0
            address = RLE_LUT + index * 4
            if address + 4 > 0x6BFE:
                raise CostError("reference RLE index exceeds ROM LUT")
            pattern = int.from_bytes(rom[address:address + 2], "big") | 0xE000
            count = int.from_bytes(rom[address + 2:address + 4], "big") + 1
            row.extend([pattern] * count)
            unc_flags.extend([False] * count)
        if len(row) > ROW_WORDS:
            raise CostError("reference run crosses a native row")
    raise CostError("reference did not decode all 28 rows")


def analyze(rom, mapping=0, flipped=False, alternate_pnt=False):
    cpu, ledger = prepare(rom, mapping, flipped, alternate_pnt), TrackLedger(rom)
    slices = tuple(ledger.run_slice(cpu, index) for index in range(4))
    words = tuple(word for part in slices for word in part.pnt_words)
    if words != decode_reference(rom, mapping, flipped):
        raise CostError("native words disagree with independent raw-PNT reference")
    return slices


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--rom", required=True, type=Path)
    parser.add_argument("--mapping", type=lambda value: int(value, 0), default=0)
    parser.add_argument("--flipped", action="store_true")
    parser.add_argument("--alternate-pnt", action="store_true")
    parser.add_argument("--json", action="store_true", help="include ordered writes and critical post-state")
    args = parser.parse_args(argv)
    try:
        if not args.rom.is_absolute():
            raise CostError("ROM path must be absolute")
        slices = analyze(args.rom.read_bytes(), args.mapping, args.flipped, args.alternate_pnt)
    except (CostError, OSError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2
    if args.json:
        result = {"evidence": "CPU_ONLY_TRACK_COMPONENT", "runtime_authority": False,
                  "rom_sha1": load.ROM_SHA1, "mapping": args.mapping,
                  "flipped": args.flipped, "alternate_pnt": args.alternate_pnt,
                  "slices": [{"cpu_cycles": part.cpu_cycles, "instructions": part.instructions,
                              "writes": [asdict(write) for write in part.writes],
                              "post_state": asdict(part.post_state)} for part in slices]}
        print(json.dumps(result, indent=2))
    else:
        print(f"CPU_ONLY_TRACK_COMPONENT: mapping {args.mapping}, flipped={args.flipped}, alternate_pnt={args.alternate_pnt}")
        for index, part in enumerate(slices):
            data = sum(write.width for write in part.writes if write.destination == "vdp_data")
            pnt = sum(write.width for write in part.writes if write.destination == "pnt_buffer")
            digest = hashlib.sha256(b"".join(word.to_bytes(2, "big") for word in part.pnt_words)).hexdigest()[:16]
            print(f"  slice{index}: {part.cpu_cycles} CPU clocks / {part.instructions} instructions; VDP DATA {data} bytes, PNT RAM {pnt} bytes; PNT sha256 {digest}")
        print("Four persistent slices match the independent raw-PNT format reader.")
        print("CPU and ordered writes only; no FIFO/DMA/interrupt/device or complete-frame timing claim.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
