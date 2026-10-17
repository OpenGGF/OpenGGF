#!/usr/bin/env python3
# Origin: kos46 S3K ending Kos CPU-budget research (known bug "Ending Reunion Kos Service Count"), round R12 (2026-10-17).
# Promoted into OpenGGF tools/timing on 2026-10-17 at base 00f8ab78a. Promotion changed only import paths and directory names,
# sonic3k.asm line citations (converted to the pinned skdisasm 044fa46) and this header.
# Inputs: ROM bytes (art and routines), queue head art address and patterns already decoded, a cost function.
# Citations: ROM address and label first; any sonic3k.asm line number refers to skdisasm 044fa46 (see README).
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""S3K Nemesis queue micro-execution ledger (kos46 research prototype, 2026-10-17).

Executes the shipped routines on a private mini68k model (no gameplay, no engine or emulator memory):
  main loop  Process_Nem_Queue_Init $1780 + Nem_Build_Code_Table $16A8      (LevelLoop, sonic3k.asm:7939)
  VInt       Process_Nem_Queue_2 $17F4 -> Process_Nem_Queue_Main $180C -> Nem_PCD_NewRow $1646 /
             Nem_Process_Compressed_Data $1604 / Nem_PCD_InlineData $1650 / Nem_PCD_WriteRowToVDP[_XOR] $167C/$1686
             -> Process_Nem_Queue_ShiftUp $1872                              (Do_Updates, sonic3k.asm:809)
over the compressed art bytes in the ROM, charging each executed instruction its pinned GPGX 051d430d time (register
shifts: table base + 2 x count, m68kops.h 9614-9623) or Motorola time. Each VInt returns its explicit decoder state
(the RAM Nem_decomp_vars the ROM saves) so the carry between patterns and frames is visible, and the rows written
to the VDP data port so the decode can be checked byte-for-byte against an independent decoder.

Queue semantics are the ROM's: Load_PLC_Raw appends at the first zero source long (sonic3k.asm 2056-2072, no bound
check); Load_PLC_2 / Clear_Nem_Queue zero $80 bytes including Nem_patterns_left and so abort an active decode
(2074-2101); completion shifts 22 longwords with FixBugs = 0, leaving the 16th slot (2250-2273). The 16th slot and
overflow past it are refused here (fail closed), as are art headers whose pattern count is zero.
Hardware terms (VDP FIFO waits on the 8 data writes per pattern, refresh, interrupt acceptance, Z80 waits) stay
separate.
"""
import os
import sys
from dataclasses import dataclass

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..', '..', 'mini68k'))
sys.path.insert(0, os.path.join(HERE, '..', '..', '..', 'm68k-closure'))
import nem_forms  # noqa: E402
import nem_cost  # noqa: E402

INIT, VINT, SENTINEL = 0x1780, 0x17F4, 0xFFFFF0
DECODER = (0x1604, 0x16A8)          # Nem_Process_Compressed_Data .. Nem_PCD_WriteRowToRAM_XOR end
BCT = (0x16A8, 0x16FA)              # Nem_Build_Code_Table
INIT_RANGE = (0x1780, 0x17D8)       # Process_Nem_Queue_Init
PINS = dict(nem_cost.PINS)
PINS.update({0x1604: 0x3E06, 0x160A: 0xEE69, 0x160C: 0x0C01, 0x1610: 0x643E, 0x1628: 0xE145, 0x163A: 0xE848,
             0x163C: 0xE98C, 0x163E: 0x8801, 0x1642: 0x6606, 0x1644: 0x4ED3, 0x1646: 0x7800, 0x1648: 0x7608,
             0x164A: 0x51C8, 0x164E: 0x60B4, 0x1650: 0x5D46, 0x1662: 0xEC69, 0x167A: 0x60BE, 0x167C: 0x2884,
             0x1684: 0x4E75, 0x1686: 0xB982, 0x1688: 0x2882, 0x1690: 0x4E75, 0x16A8: 0x1018, 0x16C6: 0x8E41,
             0x16E4: 0xE368, 0x16EA: 0xE36D, 0x16F4: 0x51CD, 0x16F8: 0x60BA, 0x1780: 0x4AB8, 0x1784: 0x6750,
             0x178A: 0x664A, 0x179E: 0x6A04, 0x17A0: 0xD6FC, 0x17A8: 0x6100, 0x17AE: 0xE145, 0x17B6: 0x21C8,
             0x17BA: 0x21CB, 0x17BE: 0x21C0, 0x17C2: 0x21C0, 0x17C6: 0x21C0, 0x17CA: 0x21C5, 0x17CE: 0x21C6,
             0x17D2: 0x31C2, 0x17D6: 0x4E75})
QUEUE_SLOTS_USABLE = 15


@dataclass(frozen=True)
class DecoderState:
    """Nem_decomp_vars after a VInt (what the ROM saves between frames)."""
    source: int            # Nem_decomp_queue (compressed byte pointer)
    write_routine: int     # Nem_write_routine: $167C plain / $1686 XOR
    repeat_count: int      # Nem_repeat_count (d0): pixels still owed by the current run
    palette_index: int     # Nem_palette_index (d1)
    previous_row: int      # Nem_previous_row (d2, XOR mode)
    data_word: int         # Nem_data_word (d5)
    shift_value: int       # Nem_shift_value (d6)
    patterns_left: int     # Nem_patterns_left
    destination: int       # Nem_decomp_destination (VRAM byte address of the next pattern)


@dataclass
class Step:
    cycles: int
    counts: dict
    rows: list             # longwords written to the VDP data port
    decoder_cycles: int    # part spent at $1604-$16A7
    state: DecoderState


class NemQueueModel:
    def __init__(self, rom, cost, table_base=None):
        """cost(ins, outcome) as in test_vint8_cost; register shifts are re-priced as base + 2 x count, where base is
        table_base(op) for GPGX (m68ki_cycles entry) or the Motorola zero-count time."""
        for pc, op in PINS.items():
            if ((rom[pc] << 8) | rom[pc + 1]) != op:
                raise ValueError('ROM opcode at %06X is not the pinned %04X' % (pc, op))
        self.rom, self.cost, self.table_base = rom, cost, table_base
        w = lambda a: (rom[a] << 8) | rom[a + 1]  # noqa: E731
        self.queue = 0xFFFF0000 | w(0x1874)
        # operands of Process_Nem_Queue_Init's save sequence ($17B6-$17D2)
        self.addr = {k: 0xFFFF0000 | w(pc + 2) for k, pc in (
            ('write_routine', 0x17BA), ('repeat_count', 0x17BE), ('palette_index', 0x17C2),
            ('previous_row', 0x17C6), ('data_word', 0x17CA), ('shift_value', 0x17CE), ('patterns_left', 0x17D2))}
        if 0xFFFF0000 | w(0x17B8) != self.queue:
            raise ValueError('Nem_decomp_queue operand mismatch')
        import m68k_timing
        self.dec = m68k_timing.Decoder(rom)
        self.cpu = nem_forms.CPU(rom)
        self.cpu.a[7] = 0xFFFE00

    # -- queue producers (ROM semantics, applied to the private model)
    def load_plc_raw(self, entries):
        """Load_PLC_Raw: append at the first slot whose source long is zero."""
        slot = 0
        while self.cpu.read(self.queue + 6 * slot, 'l'):
            slot += 1
        if slot + len(entries) > QUEUE_SLOTS_USABLE:
            raise ValueError('PLC would occupy the retained 16th slot or overflow Nem_decomp_queue')
        for k, (src, dst) in enumerate(entries):
            if src == 0:
                raise ValueError('a zero source long is a free slot, not a job')
            self.cpu.write(self.queue + 6 * (slot + k), 'l', src)
            self.cpu.write(self.queue + 6 * (slot + k) + 4, 'w', dst)

    @staticmethod
    def plc_list(rom, addr):
        """plrlistheader/plreq (sonic3k.macros.asm 188-198): word count-1, then (long art, word VRAM byte addr)."""
        n = ((rom[addr] << 8) | rom[addr + 1])
        if n & 0x8000:
            return []
        out = []
        for k in range(n + 1):
            a = addr + 2 + 6 * k
            out.append((int.from_bytes(rom[a:a + 4], 'big'), (rom[a + 4] << 8) | rom[a + 5]))
        return out

    # -- execution
    def charge(self, pc, outcome, shift):
        ins = self.dec.decode(pc)
        if shift is None:
            return self.cost(ins, outcome)
        op = (self.rom[pc] << 8) | self.rom[pc + 1]
        base = self.table_base(op) if self.table_base else ins.cost.lo
        return base + 2 * shift

    def call(self, entry, ranges, limit=2000000):
        cpu = self.cpu
        cpu.a[7] -= 4
        cpu.write(cpu.a[7], 'l', SENTINEL)
        cpu.pc = entry
        cycles, counts, dcyc = 0, {}, 0
        rows0 = len(cpu.data_writes)
        for _ in range(limit):
            if cpu.pc == SENTINEL:
                return cycles, counts, cpu.data_writes[rows0:], dcyc
            pc = cpu.pc
            if not any(a <= pc < b for a, b in ranges):
                raise ValueError('walk left the Nemesis routines at %06X' % pc)
            shift = cpu.shift_count()
            outcome = cpu.step({}, {})
            c = self.charge(pc, outcome, shift)
            counts[pc] = counts.get(pc, 0) + 1
            cycles += c
            if DECODER[0] <= pc < DECODER[1]:
                dcyc += c
        raise RuntimeError('routine at %06X did not return' % entry)

    def main_loop_init(self):
        """Process_Nem_Queue_Init as LevelLoop runs it: returns its cycles (a main-loop term) and whether it armed."""
        before = self.read('patterns_left')
        header = None
        src = self.cpu.read(self.queue, 'l')
        if src and before == 0:
            header = (self.rom[src] << 8) | self.rom[src + 1] if src < len(self.rom) else None
            if header is not None and header & 0x7FFF == 0:
                raise ValueError('art header with zero patterns never arms the decoder (unsupported)')
        cycles, counts, _, _ = self.call(INIT, (INIT_RANGE, BCT))
        return cycles, counts, self.read('patterns_left') != before

    def vint(self):
        cycles, counts, rows, dcyc = self.call(VINT, nem_cost.RANGES + (DECODER,))
        return Step(cycles, counts, rows, dcyc, self.state())

    def read(self, key):
        return self.cpu.read(self.addr[key], 'w' if key == 'patterns_left' else 'l')

    def state(self):
        return DecoderState(self.cpu.read(self.queue, 'l'), self.read('write_routine'), self.read('repeat_count'),
                            self.read('palette_index'), self.read('previous_row'), self.read('data_word'),
                            self.read('shift_value'), self.read('patterns_left'), self.cpu.read(self.queue + 4, 'w'))

    def run_frames(self, frames=100000):
        """LevelLoop order per frame: Process_Nem_Queue_Init (main loop), then VInt_8 -> Process_Nem_Queue_2."""
        out = []
        for _ in range(frames):
            init_cycles, _, armed = self.main_loop_init()
            if self.read('patterns_left') == 0:
                return out
            step = self.vint()
            step.init_cycles = init_cycles if armed else None
            out.append(step)
        raise RuntimeError('queue did not drain')
