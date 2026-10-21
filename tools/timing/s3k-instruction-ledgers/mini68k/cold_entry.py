#!/usr/bin/env python3
"""Independent diagnostic S3K cold entry; optionally retire the first VDP status read.

Origin: cold-entry-semantic-control-20261021, base8c78e776. Inputs: original ROM,
explicit fixed profile, pinned GPGX object store and TraceChaser703 object store.
The optional qualified native report is EXPECTED OUTPUT ONLY, read after execution.
No engine/native/runtime writes, capture, readiness authority or research-default changes.
Sources: EntryPoint/SetupValues (skdisasm044fa46); GPGX051d430d genesis.c gen_reset,
io_ctrl.c io_reset, system.c system_reset/frame_gen, m68kcpu.c reset/run,
m68kcpu.h immediate/operand reads and m68kops.h MOVEM/branch/ANDI/refresh.
First-status opt-in (task first-vdp-r3/base876037b4) additionally derives VDP reset,
first system_frame_gen(0) admission and status predicates from those pinned sources,
vdp_render.c and BizHawk427 managed/native frame wiring. No later device execution.
"""
import argparse
import copy
from dataclasses import dataclass, field
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parents[1] / 'm68k-closure'))
import mini68k  # noqa: E402
import gpgx_cycles  # noqa: E402
import m68k_timing  # noqa: E402

GPGX = gpgx_cycles.PINNED_COMMIT
GPGX_TREE = '1bb96ca74d660d383e70d9cd56b88906a0773519'
BIZHAWK = '427556b5ef3ac437eba754d90c5e7e9096c9a8df'
TRACECHASER = '703bd92d32634ce5ebac60625b48c60ef5500427'
TRACECHASER_TREE = 'eb61ea653b70b9cab90786014f7deaeb9c2abd6a'
ROM_SHA1 = 'cfbf98c36c776677290a872547ac47c53d2761d6'
HARNESS_SHA256 = 'dad8d30f427978166fcc048dafc8a8d41e6dfce9615a664f33b159cd2f6c44a3'
BUILD_SHA256 = 'ba477ca4acafc26d5ac463c765ee7744ff0e474d91c432fe6412afe60c64e84f'
CORE_SHA256 = 'b665a2dd91df065f410841c6caa5fb6d322ad6025e02750aae4d14d2f3a83543'
RAW_SHA256 = 'b9ace4f67f675d645c476a69ccf2c394a8bbbefb4d417a7e7f0d54289b7ab3cd'
M = mini68k.M
PERIOD = 128 * 7
SOURCE_FILES = ('core/genesis.c', 'core/system.c', 'core/system.h', 'core/mem68k.c',
                'core/io_ctrl.c', 'core/vdp_ctrl.c', 'core/cart_hw/sram.c',
                'core/cart_hw/md_cart.c', 'core/input_hw/input.h',
                'core/m68k/m68kcpu.c', 'core/m68k/m68kcpu.h',
                'core/m68k/m68kops.h', 'core/m68k/m68kconf.h')
PROFILE_NAME = 'gpgx703-md-usa-ntsc-cold'


class Refused(ValueError):
    pass


class DeviceBoundary(mini68k.Unsupported):
    def __init__(self, pc, opcode, address, width):
        self.pc, self.opcode, self.address, self.width = pc, opcode, address, width
        super().__init__('unsupported device read at %06X: opcode%04X bus%06X width%d'
                         % (pc, opcode, address, width))


@dataclass(frozen=True)
class Profile:
    name: str
    hardware: int = 0x80
    region: int = 0x80
    pal: int = 0
    lines: int = 262
    bios: int = 0
    ports: tuple = (1, 1)
    devices: tuple = (0, 0)
    save_provider: bool = False
    neutral: bool = True

    def require(self):
        fixed = Profile(PROFILE_NAME)
        if (self != fixed or json.dumps(vars(self), sort_keys=True)
                != json.dumps(vars(fixed), sort_keys=True)):
            raise Refused('unsupported cold profile/input/initialization')


def digest(data):
    return hashlib.sha256(data).hexdigest()


def _git(repo, *args):
    try:
        return subprocess.run(['git', '-C', str(repo), *args], check=True,
                              stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout
    except (OSError, subprocess.CalledProcessError) as error:
        raise Refused('source object unavailable: ' + str(repo)) from error


def require_repository(repo, commit, tree):
    if (_git(repo, 'rev-parse', commit + '^{commit}').decode().strip() != commit
            or _git(repo, 'rev-parse', commit + '^{tree}').decode().strip() != tree):
        raise Refused('source commit/tree differs')


def strict_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise Refused('duplicate JSON field: ' + key)
        result[key] = value
    return result


class Sources:
    """Only authenticated source objects enter execution; no observed state enters here."""
    def __init__(self, gpgx_repo, tracechaser_repo):
        require_repository(gpgx_repo, GPGX, GPGX_TREE)
        require_repository(tracechaser_repo, TRACECHASER, TRACECHASER_TREE)
        self.files = {p: _git(gpgx_repo, 'show', GPGX + ':' + p) for p in SOURCE_FILES}
        self.table = tuple(gpgx_cycles.load_table(str(gpgx_repo)))
        header = self.files['core/m68k/m68kcpu.h'].decode()
        self.reset_cycles = int(re.search(
            r'm68ki_exception_cycle_table\[256\]\s*=\s*\{\s*(\d+)\*MUL', header)[1])
        self.line_mclk = int(re.search(r'#define MCYCLES_PER_LINE\s+(\d+)',
                                      self.files['core/system.h'].decode())[1])
        if self.reset_cycles != 40 or self.line_mclk != 3420:
            raise Refused('reset source constants differ')
        self.manifest = {p: digest(b) for p, b in self.files.items()}
        source = 'bizhawk-headless/native/gpgx-audio-observer/'
        self.lock_bytes = _git(tracechaser_repo, 'show', TRACECHASER + ':' + source + 'source-lock.json')
        self.lock = json.loads(self.lock_bytes, object_pairs_hook=strict_object)
        self.patches = []
        for number, name in enumerate(('buffer-z80-audio-events', 's3k-audio-parity-events',
                                      's3k-chip-pcm-events', 'm68k-stall-counters',
                                      'm68k-cpu-ledger-v2', 'constructor-reset-prefix'), 1):
            filename = '%04d-%s.patch' % (number, name)
            self.patches.append({'file': filename, 'sha256': digest(_git(
                tracechaser_repo, 'show', TRACECHASER + ':' + source + filename))})
        # Authenticate constructor/profile wiring too, even though the pure machine does not call it.
        self.manifest['TraceChaser:GpgxHost.cs'] = digest(_git(tracechaser_repo, 'show',
            TRACECHASER + ':bizhawk-headless/src/Core/GpgxHost.cs'))
        self.manifest['TraceChaser:ResetPrefixRunner.cs'] = digest(_git(tracechaser_repo, 'show',
            TRACECHASER + ':bizhawk-headless/src/Recording/ResetPrefixRunner.cs'))
        self.fingerprint = digest(json.dumps(self.manifest, sort_keys=True).encode())

    def cycles(self, rom, pc, outcome):
        instruction = m68k_timing.Decoder(rom).decode(pc)
        op = int.from_bytes(rom[pc:pc + 2], 'big')
        value = gpgx_cycles.cost(self.table, instruction, op,
                                lambda a: int.from_bytes(rom[a:a + 2], 'big'))
        if isinstance(value, dict):
            value = value[outcome]
        if value[0] != value[1]:
            raise Refused('unresolved instruction cycle interval')
        return self.table[op], value[0] - self.table[op]


@dataclass(frozen=True)
class Continuation:
    rom_sha256: str
    source: str
    profile: Profile
    state: dict


class ColdCPU(mini68k.CPU):
    """Narrow forms and source clock adapter. Existing mini68k defaults remain unchanged.

    phase separates native fetch, refresh and execution so pure continuations can be
    copied on either side of refresh. Decode/prefetch reads never masquerade as logical
    operand observations. The cursor is the native PC (extensions consumed before reads),
    while mini68k.pc remains its instruction PC until CPU.step finishes.
    """
    def __init__(self, rom, sources, profile):
        profile.require()
        if len(rom) != 0x400000 or hashlib.sha1(rom).hexdigest() != ROM_SHA1:
            raise Refused('original locked-on 4MiB ROM identity differs')
        super().__init__(bytes(rom))
        self.sources, self.profile = sources, profile
        self.rom_sha256 = digest(self.rom)
        # m68k is fresh BSS; not_z_flag=0 represents Z=1, all other CCR bits clear.
        self.d, self.a = [0] * 8, [0] * 8
        self.x = self.n = self.v = self.c = 0
        self.z = 1
        self.pc = self.cursor = self.sr_upper = self.ir = self.fetch_pc = 0
        self.pref_addr = self.pref_data = self.refresh = self.irq = 0
        self.phase = 'boundary'
        self.extension = self.opcode_pending = False
        self.events = []
        self.io = [0] * 16  # io_reset occurs after gen_reset, not before its snapshots.
        self.raw = ((profile.lines - 192 + 159 - 27 * profile.pal) * sources.line_mclk + 1004) // 7 * 7
        self.z80_cycles = self.raw // 15 * 15
        self.emit(1)
        self.sr_upper = 0x2700
        self.pref_addr = 0x1000
        self.a[7] = self.imm32()
        self.pc = self.imm32()
        self.cursor = self.pc
        charge = sources.reset_cycles * 7
        self.raw += charge
        self.refresh = (self.raw // PERIOD + 1) * PERIOD
        self.emit(2, aux=charge)
        # io_reset MD/non-MCD branch; input systems/types from fixed GpgxHost profile.
        self.io = [profile.region | (profile.bios & 1) | 0x20, 0, 0, 0, 0, 0, 0,
                   0xFF, 0, 0, 0xFF, 0, 0, 0xFB, 0, 0]
        # sram_init's combined-ROM branch, not an observed flag or a user save.
        if b'SONIC & KNUCKLES' not in rom[0x150:0x180] or rom[0x1B0:0x1B2] == b'RA':
            raise Refused('unsupported SRAM/cart header producer')
        self.sram_flags = 1  # on=1, detected=custom=0; fresh SRAM fill=FF; never accessed here.
        self.emit(3, address=profile.ports[0] | profile.ports[1] << 8,
                  value=profile.devices[0] | profile.devices[1] << 8, width=self.sram_flags)

    @property
    def sr(self):
        return self.sr_upper | self.x << 4 | self.n << 3 | self.z << 2 | self.v << 1 | self.c

    def emit(self, kind, address=0, value=0, width=0, aux=0):
        if len(self.events) >= 128:
            raise Refused('diagnostic capacity exhausted; no order loss accepted')
        p = self.profile
        self.events.append(tuple([kind, len(self.events), self.cursor, self.raw, self.refresh,
            self.sr, self.irq, self.z80_cycles, 0, 0, address & 0xFFFFFF, value, width, 1]
            + self.d + self.a + [self.ir, p.hardware, p.region, p.pal, p.lines, self.io[0],
            self.io[4] | self.io[5] << 8 | self.io[6] << 16, p.bios, len(self.rom),
            self.fetch_pc if kind >= 4 else aux]))

    def rom_word(self, address):
        if address & 1 or not 0 <= address <= len(self.rom) - 2:
            raise mini68k.Unsupported('unsupported instruction/prefetch address')
        return int.from_bytes(self.rom[address:address + 2], 'big')

    def imm16(self):
        # m68kcpu.h:784..810, enabled prefetch; these are not observed operand reads.
        if self.cursor != self.pref_addr:
            self.pref_addr, self.pref_data = self.cursor, self.rom_word(self.cursor)
        value = self.pref_data
        self.cursor += 2
        self.pref_addr, self.pref_data = self.cursor, self.rom_word(self.cursor)
        return value

    def imm32(self):
        # m68kcpu.h:813..832: no intermediate prefetch is a logical data read.
        if self.cursor != self.pref_addr:
            self.pref_addr, self.pref_data = self.cursor, self.rom_word(self.cursor)
        value = self.pref_data << 16
        self.cursor += 2
        value |= self.rom_word(self.cursor)
        self.cursor += 2
        self.pref_addr, self.pref_data = self.cursor, self.rom_word(self.cursor)
        return value

    def word(self, address):
        if self.opcode_pending and address == self.pc:
            self.opcode_pending = False
            return self.ir
        if address != self.cursor:
            raise mini68k.Unsupported('nonsequential extension read')
        return self.imm16()

    def ea(self, mode, reg, size, ext):
        self.extension = True
        try:
            return super().ea(mode, reg, size, ext)
        finally:
            self.extension = False

    def require_data(self, address, size):
        width, address = mini68k.SZ[size][0], address & 0xFFFFFF
        if width > 1 and address & 1:
            raise mini68k.Unsupported('unaligned operand/address-error producer')
        if address < len(self.rom) and address + width <= len(self.rom):
            return
        if (address, size) not in ((0xA10008, 'l'), (0xA1000C, 'w'), (0xA10001, 'b')):
            raise mini68k.Unsupported('unsupported operand producer bus%06X/%s' % (address, size))

    def io_word(self, address):
        offset = address >> 1 & 15
        if offset in (1, 2, 3):
            raise mini68k.Unsupported('controller callback/input is unsupported')
        value = self.io[offset]
        return value << 8 | value  # ctrl_io_read_word duplicates byte latches across lanes.

    def read(self, address, size):
        if self.extension:
            if address != self.cursor or size != 'l':
                raise mini68k.Unsupported('unsupported extension producer')
            return self.imm32()
        self.require_data(address, size)
        address &= 0xFFFFFF
        width = mini68k.SZ[size][0]
        if address < len(self.rom):
            value = int.from_bytes(self.rom[address:address + width], 'big')
        elif size == 'l':
            value = self.io_word(address) << 16 | self.io_word(address + 2)
        elif size == 'w':
            value = self.io_word(address)
        else:
            value = self.io[address >> 1 & 15]
        self.emit(6, address, value, width)
        return value

    def write(self, address, size, value):
        raise mini68k.Unsupported('cold-entry writes are unsupported')

    def check_form(self, op):
        mode, reg = op >> 3 & 7, op & 7
        if op >> 12 == 3 and op >> 6 & 7 == 0 and mode == 2:
            address = self.a[reg] & 0xFFFFFF
            if 0xC00000 <= address < 0xC00020:
                raise DeviceBoundary(self.pc, op, address, 2)
        lea = op & 0xF1C0 == 0x41C0 and mode == 7 and reg in (0, 2)
        tst = op & 0xFF00 == 0x4A00 and op >> 6 & 3 in (1, 2) and mode == 7 and reg == 1
        branch = op >> 12 == 6 and op >> 8 & 15 in (6, 7) and op & 255 != 0
        movem = op & 0xFFF8 in (0x4C98, 0x4CD8)
        move = op >> 12 == 1 and op >> 6 & 7 == 0 and mode == 5
        andi = op & 0xFFF8 == 0x0200
        if not (lea or tst or branch or movem or move or andi):
            raise mini68k.Unsupported('cold form %04X at %06X' % (op, self.pc))
        # Reject unknown input/device producers before the fetch/transition.
        if tst:
            self.require_data(int.from_bytes(self.rom[self.pc + 2:self.pc + 6], 'big'),
                              'w' if op >> 6 & 3 == 1 else 'l')
        if move:
            self.require_data(self.a[reg] + mini68k.sx(self.rom_word(self.pc + 2), 'w'), 'b')
        if movem:
            width = 4 if op & 0x40 else 2
            count = self.rom_word(self.pc + 2).bit_count()
            start = self.a[reg] & 0xFFFFFF
            if start & 1 or start + count * width + 2 > len(self.rom):
                raise mini68k.Unsupported('MOVEM ROM/extra-read producer unsupported')

    def step(self, open_calls, open_terms):
        op = self.ir
        if op & 0xFFF8 in (0x4C98, 0x4CD8):
            self.word(self.pc)  # same opcode consumption contract as mini68k.CPU.step
            mask = self.word(self.pc + 2)
            size, reg = ('l' if op & 0x40 else 'w'), op & 7
            address = self.a[reg]
            for index in range(16):
                if mask & 1 << index:
                    value = self.read(address, size)  # snapshot precedes register update.
                    (self.d if index < 8 else self.a)[index & 7] = mini68k.sx(value, size) & M
                    address = (address + mini68k.SZ[size][0]) & M
            self.a[reg] = address  # source AY update precedes the mandatory extra read.
            self.read(address, 'w')
            self.pc += 4
            return 'seq'
        if op & 0xFFF8 == 0x0200:
            self.word(self.pc)
            value = self.d[op & 7] & (self.word(self.pc + 2) | 0xFFFFFF00)
            self.d[op & 7] = value & M
            self.nz(value, 'b')
            self.v = self.c = 0
            self.pc += 4
            return 'seq'
        return super().step(open_calls, open_terms)

    def advance_phase(self):
        if self.irq:
            raise mini68k.Unsupported('external IRQ producer is unsupported')
        if self.phase == 'boundary':
            op = self.rom_word(self.pc)
            self.check_form(op)
            self.ir, self.fetch_pc = op, self.pc
            self.emit(4)
            if self.imm16() != op:
                raise Refused('prefetch differs from authenticated opcode')
            self.phase = 'refresh'
        elif self.phase == 'refresh':
            # Native common refresh occurs after fetch and before execution.
            if self.raw >= self.refresh:
                self.refresh = self.raw + PERIOD
                self.raw += 2 * 7
            self.phase = 'execute'
        elif self.phase == 'execute':
            pc = self.pc
            self.opcode_pending = True
            outcome = self.step({}, {})
            if self.opcode_pending or self.extension:
                raise Refused('incomplete decode continuation')
            base, dynamic = self.sources.cycles(self.rom, pc, outcome)
            self.raw += dynamic * 7
            # Exact m68kops.h:3..5,15439..15440: extend old deadline, once, before base.
            if self.ir & 0xFFF8 == 0x4CD8 and self.raw >= self.refresh:
                self.refresh += PERIOD
            self.raw += base * 7
            self.cursor = self.pc
            self.emit(5)
            self.phase = 'boundary'
        else:
            raise Refused('unknown continuation phase')

    def snapshot(self):
        state = copy.deepcopy({k: v for k, v in self.__dict__.items() if k not in ('rom', 'sources')})
        return Continuation(self.rom_sha256, self.sources.fingerprint, self.profile, state)

    def restore(self, continuation):
        if (continuation.rom_sha256 != self.rom_sha256 or continuation.source != self.sources.fingerprint
                or continuation.profile != self.profile):
            raise Refused('continuation source/ROM/profile differs')
        rom, sources = self.rom, self.sources
        self.__dict__.clear()
        self.__dict__.update(copy.deepcopy(continuation.state), rom=rom, sources=sources)


def generate(rom, sources, profile):
    """Execute independently. Deliberately has no expected-report input."""
    cpu = ColdCPU(rom, sources, profile)
    while True:
        try:
            cpu.advance_phase()
        except DeviceBoundary as boundary:
            return cpu, boundary


class FirstVDPSources(Sources):
    """Additional source admission is opt-in; the default source manifest is unchanged."""
    def __init__(self, gpgx_repo, tracechaser_repo, bizhawk_repo):
        super().__init__(gpgx_repo, tracechaser_repo)
        require_repository(bizhawk_repo, BIZHAWK, '7281227ed2f3b89c0962b2792b28539e35361c6b')
        for path in ('core/vdp_render.c', 'core/input_hw/input.c', 'core/loadrom.c', 'core/macros.h'):
            self.files[path] = _git(gpgx_repo, 'show', GPGX + ':' + path)
            self.manifest[path] = digest(self.files[path])
        managed = 'src/BizHawk.Emulation.Cores/Consoles/Sega/gpgx64/'
        paths = [managed + name for name in ('GPGX.cs', 'GPGX.IEmulator.cs', 'GPGX.ISettable.cs',
                 'GPGX.IDebuggable.cs', 'GPGXControlConverter.cs')]
        for path in paths + ['waterbox/gpgx/cinterface/cinterface.c', 'waterbox/gpgx/Makefile']:
            self.manifest['BizHawk:' + path] = digest(_git(bizhawk_repo, 'show', BIZHAWK + ':' + path))
        vdp = self.files['core/vdp_ctrl.c'].decode()
        self.status_timings = tuple(int(re.search(r'#define\s+' + name + r'\s+\((\d+)\)', vdp)[1])
            for name in ('VINT_H32_MCYCLE', 'HBLANK_H32_START_MCYCLE', 'HBLANK_H32_END_MCYCLE'))
        self.fingerprint = digest(json.dumps(self.manifest, sort_keys=True).encode())


@dataclass(frozen=True)
class FirstFrameProfile:
    # GpgxHost703 settings/Advance -> BizHawk427 FrameAdvance -> gpgx_advance.
    overscan: int = 3
    managed_render: bool = False
    native_skip: int = 0  # render=false skips managed UpdateVideo, never system_frame_gen.
    memory_callbacks: bool = False
    input_devices: tuple = (0, 0xFF, 0xFF, 0xFF, 0, 0xFF, 0xFF, 0xFF)  # PAD3B / NO_DEVICE

    def require(self):
        if (self != FirstFrameProfile() or json.dumps(vars(self), sort_keys=True)
                != json.dumps(vars(FirstFrameProfile()), sort_keys=True)):
            raise Refused('unsupported first-frame profile/render/input/callback path')


@dataclass
class FirstVDP:
    """Source-only device/scheduler state, not extra native-observer fields.

    vdp_reset explicitly clears registers/control/DMA/IRQ state, then sets reg10=FF.
    Unaccessed VRAM/pixels are outside this adapter. No absent report field supplies state.
    """
    reg: list = field(default_factory=lambda: [0] * 0x20)
    addr: int = 0
    addr_latch: int = 0
    code: int = 0
    pending: int = 0
    hint_pending: int = 0
    vint_pending: int = 0
    dma_length: int = 0
    dma_end: int = 0
    odd_frame: int = 0
    interlaced: int = 0
    im2: int = 0
    fifo_idx: int = 0
    fifo_cycles: list = field(default_factory=lambda: [0] * 4)
    status: int = 0
    width: int = 256
    height: int = 192
    viewport_x: int = 0
    viewport_y: int = 0
    hvc_latch: int = 0x10000
    v_counter: int = 192
    h_counter: int = 0xFF
    vint_cycle: int = 0
    hblank_start: int = 0
    hblank_end: int = 0
    z80_irq: bool = False
    mcycles: int = 0
    line_end: int = 0
    first_frame: bool = False
    admitted: bool = False
    last_sample: int = 0

    @classmethod
    def reset(cls, sources, profile, frame):
        frame.require()
        device = cls(status=profile.pal, viewport_x=(frame.overscan & 2) * 7,
                     viewport_y=(frame.overscan & 1) * 24 * (profile.pal + 1))
        device.reg[10] = 0xFF  # vdp_ctrl.c:419..420; overrides memset, not an assumption.
        device.vint_cycle, device.hblank_start, device.hblank_end = sources.status_timings
        return device

    def admit_first_frame(self, raw, sources, profile, frame):
        """system.c:351..676, only until the first m68k_run whose target exceeds raw.

        Earlier m68k_run calls return immediately (m68kcpu.c:265). MD display-off
        render_line still blanks/remaps, but cannot parse sprites or alter status.
        PAD3B/NO_DEVICE input_refresh is inert, Z80 is held reset, SVP is absent.
        """
        frame.require()
        if self.first_frame or self.v_counter != self.height or self.h_counter != 0xFF:
            raise mini68k.Unsupported('unsupported initial VDP/frame interrupt state')
        self.first_frame = True
        self.mcycles = 0
        self.fifo_cycles = [0] * 4
        self.status = (self.status & 0xFEED) | 0x08
        if self.interlaced or self.dma_length or self.reg[1] & 0x40:
            raise mini68k.Unsupported('unsupported frame DMA/interlace/display producer')

        def run():
            self.line_end = self.mcycles + sources.line_mclk
            self.admitted = raw < self.line_end
            return self.admitted

        if run():  # initial overscan line, no initial VINT because VC == viewport.h.
            return
        self.z80_irq = False
        self.mcycles += sources.line_mclk
        for line in range(self.height + 1, profile.lines - 1):
            self.v_counter = line
            if run():
                return
            self.mcycles += sources.line_mclk
        self.v_counter = profile.lines - 1
        self.h_counter = self.reg[10]
        self.status &= ~0x08
        if run():
            return
        self.mcycles += sources.line_mclk
        for line in range(self.height):
            self.v_counter = line
            if self.h_counter == 0:
                self.h_counter = self.reg[10]
                self.hint_pending = 0x10
                if self.reg[0] & 0x10:
                    raise mini68k.Unsupported('unsupported first-frame HINT producer')
            else:
                self.h_counter -= 1
            if run():
                return
            self.mcycles += sources.line_mclk
        raise mini68k.Unsupported('CPU not admitted before unsupported frame-end rebase')

    def require_window(self, raw):
        if not self.admitted or not self.mcycles <= raw < self.line_end:
            raise mini68k.Unsupported('unsupported scheduler handoff beyond first admitted line')

    def control_read(self, raw, base_mclk):
        # vdp_ctrl.c:1201..1272. Only this local sample advances; CPU raw is untouched.
        cycles = raw + base_mclk
        self.last_sample = cycles
        if self.status & 2 and not self.dma_length and cycles >= self.dma_end:
            self.status &= 0xFFFD
        value = self.status
        self.pending = 0
        self.status &= 0xFF9F
        if cycles >= self.fifo_cycles[(self.fifo_idx + 3) & 3]:
            value |= 0x200
        elif cycles < self.fifo_cycles[self.fifo_idx]:
            value |= 0x100
        if not self.reg[1] & 0x40:
            value |= 8
        relative = cycles - self.mcycles
        if self.v_counter == self.height and relative >= self.vint_cycle and not self.z80_irq:
            value |= 0x80
        if self.hblank_start <= relative < self.hblank_end:
            value |= 4
        return value


class FirstVDPCPU(ColdCPU):
    """Opt-in single device read. No device write or subsequent instruction is admitted."""
    def __init__(self, rom, sources, profile, frame=None):
        if type(sources) is not FirstVDPSources:
            raise Refused('first status requires additional authenticated source objects')
        self.frame_profile = FirstFrameProfile() if frame is None else frame
        if type(self.frame_profile) is not FirstFrameProfile:
            raise Refused('unknown first-frame profile type')
        self.frame_profile.require()
        super().__init__(rom, sources, profile)
        self.vdp = FirstVDP.reset(sources, profile, self.frame_profile)
        self.vdp.admit_first_frame(self.raw, sources, profile, self.frame_profile)
        self.status_read = False

    def check_form(self, op):
        if op == 0x3014:  # m68k_op_move_16_d_ai; actual CPU.step owns D0 and CCR.
            if self.a[4] & 0xFFFFFF != 0xC00004 or self.status_read:
                raise mini68k.Unsupported('unsupported first-status operand/continuation')
            return
        super().check_form(op)

    def read(self, address, size):
        if self.extension or (address & 0xFFFFFF, size) != (0xC00004, 'w'):
            return super().read(address, size)
        if self.phase != 'execute' or self.ir != 0x3014 or self.status_read:
            raise mini68k.Unsupported('status read outside supported MOVE.W instruction')
        base, dynamic = self.sources.cycles(self.rom, self.pc, 'seq')
        if dynamic:
            raise Refused('unresolved status instruction dynamic timing')
        self.vdp.require_window(self.raw + base * 7)
        low = self.vdp.control_read(self.raw, base * 7) & 0x3FF
        # mem68k.c:1426..1430 uses direct ROM map at *advanced PC*, not IR/pref_data.
        # LSB_FIRST loadrom byteswap + uint16 dereference equals original big-endian word.
        value = low | (self.rom_word(self.cursor) & 0xFC00)
        self.status_read = True
        self.emit(6, address, value, 2)  # pending/status effects already applied; D0/CCR still old.
        return value

    def advance_phase(self):
        if self.phase == 'first_status_retired':
            raise mini68k.Unsupported('first VDP status read retired; later execution unsupported')
        self.frame_profile.require()
        self.vdp.require_window(self.raw)
        executing = self.phase == 'execute'
        super().advance_phase()
        self.vdp.require_window(self.raw)
        if executing and self.status_read:
            self.phase = 'first_status_retired'

    def restore(self, continuation):
        if continuation.state.get('frame_profile') != self.frame_profile:
            raise Refused('continuation first-frame profile differs')
        super().restore(continuation)


def generate_first_vdp(rom, sources, profile, frame=None):
    """No expected input. Stop only after the supported status instruction actually retires."""
    cpu = FirstVDPCPU(rom, sources, profile, frame)
    while cpu.phase != 'first_status_retired':
        cpu.advance_phase()
    return cpu


PROFILE_REPORT = {'hardware': 'Mega Drive cartridge', 'region_setting': 'Autodetect',
    'expected_region': 'USA NTSC', 'bios': 'disabled; NoFirmwareProvider',
    'controllers': 'two normal three-button ports',
    'sram': 'fresh native constructor initialization; no user save read or write',
    'input': 'all buttons neutral', 'video': 'ordinary FrameAdvance render=false',
    'sound': 'ordinary FrameAdvance sound=false', 'fm_chip': 'MAME_YM2612', 'filter': 'None',
    'sync_settings': 'GpgxHost.CreateGhz1SyncSettings'}


def _load_expected_with_digest(path):
    """Retain identity of the same bounded immutable buffer passed to strict JSON parsing."""
    with Path(path).open('rb') as stream:
        raw = stream.read(65537)
    if len(raw) > 65536:
        raise Refused('expected report exceeds bounded size')
    return json.loads(raw, object_pairs_hook=strict_object), digest(raw)


def load_expected(path):
    return _load_expected_with_digest(path)[0]


def validate_expected(report, sources):
    """Validate only comparison data. Never returns initialization/execution inputs."""
    exact = {'schema': 'openggf.gpgx-reset-prefix-diagnostic.v1', 'execution_status': 'completed',
        'observer': 'on', 'ordinary_neutral_frames': 1, 'reset_prefix_completed': True,
        'observation_stop': 'first_vdp_status_read_retirement', 'comparison_only': True,
        'knownness': 'reference-emulator initialized; not physical-console power-on certainty',
        'profile': PROFILE_REPORT, 'record_words': 40, 'rom_sha1': ROM_SHA1.upper(), 'rom_bytes': 0x400000,
        'installed_core_sha256': CORE_SHA256, 'installed_core_bytes': 438011,
        'installed_decompressed_core_verified': False, 'harness_executable_sha256': HARNESS_SHA256,
        'bizhawk_managed_version': '2.11.0.0', 'managed_cores_sha256': sources.lock['stock']['cores_sha256'],
        'managed_cores_module_version_id': '9404fb97-fb75-4ebb-9153-5264f4ff6a38',
        'managed_source_lock_sha256': digest(sources.lock_bytes), 'native_build_identity_sha256': BUILD_SHA256,
        'managed_constructor_verification': 'source-lock stock byte pin and loaded module checked before Open, after constructor/observations and before publication',
        'limits': 'Prefix ends at first VDP status retirement. No title timing, AIZ, SSZ, full CPU or native heap claim.'}
    other = {'native_initial', 'record_count', 'records', 'native_build_identity', 'managed_cores_loaded_path',
             'savestate_sha256', 'savestate_bytes', 'ram_register_video_audio_checkpoint_sha256', 'checkpoint_bytes'}
    if type(report) is not dict or set(report) != set(exact) | other:
        raise Refused('expected schema field inventory differs')
    for key, value in exact.items():
        if type(report[key]) is not type(value) or report[key] != value:
            raise Refused('expected identity/status/profile differs: ' + key)
    build = report['native_build_identity']
    if (type(build) is not dict or build.get('schema') != 'openggf.gpgx-reset-prefix-observer-build.v1'
            or build.get('series') != 'reset-prefix' or build.get('patches') != sources.patches
            or build.get('source') != {'bizhawk_commit': BIZHAWK, 'gpgx_commit': GPGX,
                'gpgx_tree': GPGX_TREE, 'musl_commit': '2063abc4e16c84218757b1db10d3cdf9f36ef3f8',
                'verified': 'git object ids'}
            or build.get('compressed_sha256') != CORE_SHA256 or build.get('decompressed_sha256') != RAW_SHA256
            or build.get('reset_prefix_abi') != {'version': 1, 'record_bytes': 160, 'capacity': 128}):
        raise Refused('expected native source/build identity differs')
    # Check every other build field too; this is the qualified703 build identity,
    # not a fitted timing/stream hash. Canonical comparison preserves JSON scalar types.
    qualified_build = {'compressed_size': 438011, 'decompressed_size': 43875360,
        'build_id': 'd20f8f9242ff9785', 'invisible_section_bytes': 4105496,
        'selftest_runner': 'reset-prefix-run.sh',
        'native_selftest_log_sha256': 'ba9ff14877cd954d6bdc5de4cdf6f9be91c00cb5a60b3d7b6af478d05cd9f788',
        'stall_abi': {'versions': [1, 2], 'snapshot_size': 80, 'v2_layout': 'slot 12, accept 32, bank 176, frame 40'},
        'toolchain': {'clang_sha256': 'bb6556bdcdeb00dca0c758da9966a9982542a23ddcaffa784a2de9344ede3fc0',
            'ld_lld_sha256': 'f8d0601bf957a1b063e29c3c43613a5b76482f6c14664b9fcac4d596871e14df',
            'sysroot_file_digest_sha256': 'cc9c53a8e10a7c637c0c63b95471d025b04631ee64eab22eb7d787ebcf05d1e0',
            'zstd_sha256': '2448569ed7664f1cebb2c108cfd670d8f38ff167b0dd0006043310bc63244885', 'zstd_threads': 1}}
    checked = {'schema', 'series', 'patches', 'source', 'compressed_sha256', 'decompressed_sha256', 'reset_prefix_abi'}
    if set(build) != checked | set(qualified_build) or any(build[k] != v for k, v in qualified_build.items()):
        raise Refused('expected qualified build detail differs')
    typed_build = dict(qualified_build, schema='openggf.gpgx-reset-prefix-observer-build.v1',
        series='reset-prefix', patches=sources.patches,
        source={'bizhawk_commit': BIZHAWK, 'gpgx_commit': GPGX, 'gpgx_tree': GPGX_TREE,
                'musl_commit': '2063abc4e16c84218757b1db10d3cdf9f36ef3f8', 'verified': 'git object ids'},
        compressed_sha256=CORE_SHA256, decompressed_sha256=RAW_SHA256,
        reset_prefix_abi={'version': 1, 'record_bytes': 160, 'capacity': 128})
    if json.dumps(build, sort_keys=True) != json.dumps(typed_build, sort_keys=True):
        raise Refused('expected build field type differs')
    path = report['managed_cores_loaded_path']
    if type(path) is not str or len(path) > 4096 or not Path(path).is_absolute() or Path(path).name != 'BizHawk.Emulation.Cores.dll':
        raise Refused('expected managed loaded path is invalid')
    for name in ('savestate_sha256', 'ram_register_video_audio_checkpoint_sha256'):
        if type(report[name]) is not str or not re.fullmatch('[0-9a-f]{64}', report[name]):
            raise Refused('expected checkpoint identity invalid')
    for name in ('savestate_bytes', 'checkpoint_bytes'):
        if type(report[name]) is not int or not 0 < report[name] <= 64 * 1024 * 1024:
            raise Refused('expected checkpoint size invalid')
    rows = report['records']
    if type(rows) is not list or not 3 <= len(rows) <= 128 or type(report['record_count']) is not int or report['record_count'] != len(rows):
        raise Refused('expected ordered record capacity/count differs')
    active = False
    for index, row in enumerate(rows):
        if type(row) is not list or len(row) != 40 or any(type(v) is not int or not 0 <= v <= M for v in row):
            raise Refused('expected record word layout/range differs')
        if row[1] != index or row[13] != 1:
            raise Refused('expected record order/knownness differs')
        kind = row[0]
        if index < 3:
            if kind != index + 1:
                raise Refused('expected constructor snapshot order differs')
        elif kind == 4 and not active:
            active = True
        elif kind == 5 and active:
            active = False
        elif kind != 6 or not active:
            raise Refused('expected fetch/read/retire order differs')
    if active:
        raise Refused('expected incomplete instruction')


def compare(cpu, boundary, report):
    validate_expected(report, cpu.sources)
    # The source-derived endpoint is an assertion after reaching an unsupported producer,
    # never a count, timestamp, frame or expected-record stop condition.
    if (boundary.pc, boundary.opcode, boundary.address, boundary.width) != (0x238, 0x3014, 0xC00004, 2):
        raise Refused('unsupported boundary differs from ROM cold entry')
    if len(cpu.events) != 38:
        raise Refused('source-derived cold prefix record assertion differs')
    rows = report['records']
    if len(rows) != len(cpu.events) + 3:
        raise Refused('expected missing/extra prefix or device suffix')
    for index, actual in enumerate(cpu.events):
        for word, (got, expected) in enumerate(zip(actual, rows[index])):
            if got != expected:
                raise Refused('conformance differs at record%d word%d: pure%08X expected%08X'
                              % (index, word, got, expected))
    tail = rows[len(cpu.events):]
    if ([r[0] for r in tail] != [4, 6, 5] or tail[0][2] != boundary.pc
            or tail[0][30] != boundary.opcode or tail[1][10] != boundary.address
            or tail[1][12] != boundary.width or any(r[39] != boundary.pc for r in tail)):
        raise Refused('expected unsupported device suffix/order differs')
    reset, init = cpu.events[1], cpu.events[2]
    initial = dict(zip(('reset_enter_raw_mclk', 'reset_complete_raw_mclk', 'reset_cost_mclk',
        'refresh_deadline_mclk', 'reset_pc', 'reset_a7', 'reset_sr', 'reset_irq', 'z80_cycles',
        'z80_state', 'z80_bank', 'system_hw', 'region_code', 'vdp_pal', 'lines_per_frame',
        'hw_version', 'io_control_latches', 'bios', 'rom_size', 'controller_port_systems',
        'controller_devices', 'sram_flags'), (cpu.events[0][3], reset[3], reset[39], reset[4],
        reset[2], reset[29], reset[5], reset[6], reset[7], reset[8], reset[9], init[31],
        init[32], init[33], init[34], init[35], init[36], init[37], init[38], init[10], init[11], init[12])))
    if (type(report['native_initial']) is not dict or report['native_initial'] != initial
            or any(type(v) is not int for v in report['native_initial'].values())):
        raise Refused('expected named initial snapshot differs')
    return {'status': 'PASS', 'comparison_only': True, 'claimed_records': len(cpu.events),
        'claimed_words_per_record': 40, 'retired_instructions': sum(r[0] == 5 for r in cpu.events),
        'logical_reads': sum(r[0] == 6 for r in cpu.events),
        'unsupported': 'three device suffix records: only boundary/order checked; device value, post-read flags/clocks/registers not claimed',
        'boundary': str(boundary), 'production_readiness': False}


def compare_first_vdp(cpu, report):
    if type(cpu) is not FirstVDPCPU or cpu.phase != 'first_status_retired' or not cpu.status_read:
        raise Refused('first status retirement was not generated')
    # Reuse the original strict admission/initial snapshot/default prefix comparison.
    prefix = copy.copy(cpu)
    prefix.events = cpu.events[:38]
    result = compare(prefix, DeviceBoundary(0x238, 0x3014, 0xC00004, 2), report)
    if len(cpu.events) != 41 or len(report['records']) != len(cpu.events):
        raise Refused('source-derived first-status record assertion differs')
    retired = sum(row[0] == 5 for row in cpu.events)
    reads = sum(row[0] == 6 for row in cpu.events)
    if (retired, reads) != (12, 14):
        raise Refused('source-derived first-status instruction/read assertion differs')
    for index in range(38, len(cpu.events)):
        for word, (got, expected) in enumerate(zip(cpu.events[index], report['records'][index])):
            if got != expected:
                raise Refused('conformance differs at record%d word%d: pure%08X expected%08X'
                              % (index, word, got, expected))
    result.update(claimed_records=len(cpu.events), retired_instructions=retired, logical_reads=reads,
        boundary='first VDP status read retired; next instruction not fetched',
        unsupported='later device/CPU execution; internal VDP/scheduler fields are source-unit qualified only')
    return result


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('rom', 'gpgx-git', 'tracechaser', 'expected'):
        parser.add_argument('--' + name, required=True, type=Path)
    parser.add_argument('--profile', required=True, choices=(PROFILE_NAME,))
    parser.add_argument('--first-vdp-status', action='store_true',
                        help='opt in to independent first status-read retirement (41 claimed records)')
    parser.add_argument('--bizhawk-git', type=Path, help='BizHawk427 object store; required only for opt-in')
    args = parser.parse_args(argv)
    try:
        if args.first_vdp_status:
            if args.bizhawk_git is None:
                raise Refused('--first-vdp-status requires --bizhawk-git')
            sources = FirstVDPSources(args.gpgx_git, args.tracechaser, args.bizhawk_git)
            cpu = generate_first_vdp(args.rom.read_bytes(), sources, Profile(args.profile))
        else:
            if args.bizhawk_git is not None:
                raise Refused('--bizhawk-git requires --first-vdp-status')
            sources = Sources(args.gpgx_git, args.tracechaser)
            cpu, boundary = generate(args.rom.read_bytes(), sources, Profile(args.profile))
        # Expected data is opened only AFTER the entire independently generated continuation.
        expected, expected_sha256 = _load_expected_with_digest(args.expected)
        result = compare_first_vdp(cpu, expected) if args.first_vdp_status else compare(cpu, boundary, expected)
        result.update(rom_sha1=ROM_SHA1, source_manifest=sources.manifest,
                      native_expected_sha256=expected_sha256)
        print(json.dumps(result, sort_keys=True))
        return 0
    except (Refused, mini68k.Unsupported, OSError, ValueError, KeyError) as error:
        print('REFUSED: ' + str(error), file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
